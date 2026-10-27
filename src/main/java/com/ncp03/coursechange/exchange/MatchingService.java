package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.config.MatchingProperties;
import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.ExchangePreferenceRepository;
import com.ncp03.coursechange.domain.ExchangeRequest;
import com.ncp03.coursechange.domain.ExchangeRequestRepository;
import com.ncp03.coursechange.domain.MatchParticipant;
import com.ncp03.coursechange.domain.MatchParticipantRepository;
import com.ncp03.coursechange.domain.MatchSession;
import com.ncp03.coursechange.domain.MatchSessionRepository;
import com.ncp03.coursechange.domain.PenaltyReason;
import com.ncp03.coursechange.domain.RequestStatus;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.student.StudentService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 빠른 매칭 + 교환 확정(트랜잭션).
 *
 * <h2>빠른 매칭이란</h2>
 * 내 요청과 "서로 원하는" 상대 요청을 찾아 매칭 세션을 만든다. 조건은 두 방향 모두다.
 * <ul>
 *   <li>상대가 내놓은 분반이 내 희망 목록에 있다 (내가 원한다)</li>
 *   <li>내가 내놓는 분반이 상대 희망 목록에 있다 (상대도 원한다)</li>
 * </ul>
 * 이것만으로는 부족해서, 후보마다 양쪽의 교환 후 학점·시간표·같은 과목 중복을 다시 검사한다
 * ({@link ExchangeValidator}). 통과하는 첫 후보와 세션을 만든다.
 *
 * <p>후보 정렬 기준: ① 내 희망 순위가 높은 분반 → ② 상대가 더 오래 기다린 요청.
 *
 * <h2>락 전략 (10월 27일 부하테스트용)</h2>
 * {@code app.matching.lock-mode}로 락 없는 버전과 비관적 락 버전을 같은 코드로 전환한다.
 * <ul>
 *   <li>{@code NONE} — 1차. 좌석 교환은 JPA 낙관적 락(@Version)이 막아 주지만, <b>매칭 단계는 막는 장치가 없다.</b>
 *       같은 상대를 노린 요청이 동시에 오면 한 요청이 여러 매칭 세션에 묶이거나, 두 사람이 동시에 동의해도
 *       교환이 실행되지 않고 세션이 PENDING으로 남을 수 있다. 이 이상 현상을 1차 측정에서 숫자로 보여준다.</li>
 *   <li>{@code PESSIMISTIC} — 2차. 요청·좌석 행을 SELECT ... FOR UPDATE로 잠근다.
 *       충돌 시 대기하므로 실패율은 낮고 응답 시간(p95)은 올라간다.</li>
 * </ul>
 * 두 경우 모두 <b>항상 ID 오름차순으로</b> 잠가 교착 상태(deadlock)를 피한다.
 */
@Service
@Transactional(readOnly = true)
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    /** 상대를 못 찾은 이유 코드 (에러가 아니라 정상 응답의 reason 값). */
    static final String NO_PREFERENCES = "NO_PREFERENCES";
    static final String NO_COUNTERPART = "NO_COUNTERPART";
    static final String CANDIDATES_NOT_ELIGIBLE = "CANDIDATES_NOT_ELIGIBLE";

    private final ExchangeRequestRepository requestRepository;
    private final ExchangePreferenceRepository preferenceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final MatchSessionRepository sessionRepository;
    private final MatchParticipantRepository participantRepository;
    private final StudentService studentService;
    private final ExchangeRequestService requestService;
    private final ExchangeValidator validator;
    private final MatchSessionAssembler assembler;
    private final MatchingProperties properties;
    private final EntityManager entityManager;
    private final PenaltyService penaltyService;
    private final PriorityScorer priorityScorer;

    public MatchingService(ExchangeRequestRepository requestRepository,
                           ExchangePreferenceRepository preferenceRepository,
                           EnrollmentRepository enrollmentRepository,
                           MatchSessionRepository sessionRepository,
                           MatchParticipantRepository participantRepository,
                           StudentService studentService,
                           ExchangeRequestService requestService,
                           ExchangeValidator validator,
                           MatchSessionAssembler assembler,
                           MatchingProperties properties,
                           EntityManager entityManager,
                           PenaltyService penaltyService,
                           PriorityScorer priorityScorer) {
        this.requestRepository = requestRepository;
        this.preferenceRepository = preferenceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.studentService = studentService;
        this.requestService = requestService;
        this.validator = validator;
        this.assembler = assembler;
        this.properties = properties;
        this.entityManager = entityManager;
        this.penaltyService = penaltyService;
        this.priorityScorer = priorityScorer;
    }

    // =============================================================== 빠른 매칭

    @Transactional
    public QuickMatchResponse quickMatch(Long studentId, Long requestId) {
        Student me = studentService.getStudent(studentId);
        requestService.requireNotBlocked(me);

        ExchangeRequest myRequest = requestService.loadMyRequest(studentId, requestId);
        if (myRequest.getStatus() != RequestStatus.WAITING) {
            throw new ApiException(ErrorCode.REQUEST_NOT_MODIFIABLE,
                    "WAITING 상태의 요청만 매칭할 수 있습니다. 현재 상태=" + myRequest.getStatus());
        }

        List<Long> myWantedIds = preferenceRepository.findTargetOfferingIdsByRequestId(requestId);
        if (myWantedIds.isEmpty()) {
            return QuickMatchResponse.notMatched(NO_PREFERENCES, 0);
        }
        // 내 희망 순위 (후보 정렬에 쓴다)
        Map<Long, Integer> rankByOfferingId = new HashMap<>();
        for (int i = 0; i < myWantedIds.size(); i++) {
            rankByOfferingId.put(myWantedIds.get(i), i + 1);
        }

        Enrollment myEnrollment = myRequest.getOfferedEnrollment();
        Long myGiveOfferingId = myEnrollment.getOffering().getId();

        List<ExchangeRequest> candidates = requestRepository.findMutualCandidates(
                studentId, myWantedIds, myGiveOfferingId);
        if (candidates.isEmpty()) {
            return QuickMatchResponse.notMatched(NO_COUNTERPART, 0);
        }

        // ① 내 희망 순위 오름차순 → ② 우선순위 점수 내림차순(학년·기다린 시간·학점 여유·감점) → ③ 먼저 등록한 순
        Map<Long, Long> scores = priorityScorer.scoreAll(candidates, LocalDateTime.now());
        List<ExchangeRequest> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((ExchangeRequest r) ->
                        rankByOfferingId.getOrDefault(r.getOfferedEnrollment().getOffering().getId(), Integer.MAX_VALUE))
                .thenComparing((ExchangeRequest r) -> scores.getOrDefault(r.getId(), 0L), Comparator.reverseOrder())
                .thenComparing(ExchangeRequest::getQueuedAt)
                .thenComparing(ExchangeRequest::getId));

        int checked = 0;
        for (ExchangeRequest candidate : sorted) {
            checked++;
            Optional<MatchSession> session = tryPair(me, myRequest, candidate);
            if (session.isPresent()) {
                List<MatchParticipant> participants =
                        participantRepository.findBySessionIdWithAll(session.get().getId());
                return QuickMatchResponse.matched(
                        assembler.toResponse(session.get(), participants, studentId, LocalDateTime.now()), checked);
            }
        }
        return QuickMatchResponse.notMatched(CANDIDATES_NOT_ELIGIBLE, checked);
    }

    /**
     * 후보 1명과 매칭을 시도한다. 양쪽 교환 가능성을 다시 검사하고, 통과하면 세션을 만든다.
     * 조건에 안 맞으면 빈 Optional을 돌려 다음 후보로 넘어간다.
     */
    private Optional<MatchSession> tryPair(Student me, ExchangeRequest myRequest, ExchangeRequest theirRequest) {
        Student partner = theirRequest.getStudent();

        // 패널티로 막힌 상대는 건너뛴다.
        LocalDateTime now = LocalDateTime.now();
        if (partner.getBlockedUntil() != null && partner.getBlockedUntil().isAfter(now)) {
            return Optional.empty();
        }

        Enrollment myEnrollment = myRequest.getOfferedEnrollment();
        Enrollment theirEnrollment = theirRequest.getOfferedEnrollment();
        CourseOffering myGive = myEnrollment.getOffering();
        CourseOffering theirGive = theirEnrollment.getOffering();

        if (myGive.getId().equals(theirGive.getId())) {
            return Optional.empty(); // 같은 분반끼리는 교환 의미가 없다 (DB CHECK도 막는다)
        }

        // PESSIMISTIC: 두 요청 행을 ID 오름차순으로 잠그고 DB의 최신 상태로 새로고침한다.
        // (이미 읽어 둔 엔티티는 잠가도 값이 안 바뀌므로 refresh가 꼭 필요하다.)
        // 새로고침한 상태가 WAITING이 아니면 그사이 다른 매칭에 묶인 것이라 이 후보는 건너뛴다.
        // NONE: 잠그지 않는다. 같은 상대를 노린 요청이 겹치면 한 요청이 두 세션에 묶일 수 있다 (1차 측정에서 보여줄 문제).
        if (properties.isPessimistic()) {
            lockAndRefresh(myRequest, theirRequest);
            if (myRequest.getStatus() != RequestStatus.WAITING
                    || theirRequest.getStatus() != RequestStatus.WAITING) {
                return Optional.empty();
            }
        }

        // 양쪽 교환 가능성 재검사: 내가 theirGive를 받고, 상대가 myGive를 받는다.
        if (validator.check(me, myEnrollment, theirGive).isPresent()) {
            return Optional.empty();
        }
        if (validator.check(partner, theirEnrollment, myGive).isPresent()) {
            return Optional.empty();
        }

        LocalDateTime expiresAt = now.plusSeconds(properties.getAcceptTimeoutSeconds());
        try {
            MatchSession session = sessionRepository.saveAndFlush(MatchSession.open(now, expiresAt));

            // 요청 상태를 MATCHED로. 락 없는 모드에서 동시에 매칭되면 여기서 상태가 안 맞아 예외가 난다.
            myRequest.markMatched();
            theirRequest.markMatched();

            participantRepository.saveAll(List.of(
                    MatchParticipant.of(session, myRequest, theirGive),
                    MatchParticipant.of(session, theirRequest, myGive)));
            requestRepository.flush();
            participantRepository.flush();
            return Optional.of(session);

        } catch (IllegalStateException e) {
            // markMatched()가 거부 → 상대가 방금 다른 세션에 묶였다.
            log.debug("매칭 시도 실패(상태 변경): requestId={}, partnerRequestId={}, 이유={}",
                    myRequest.getId(), theirRequest.getId(), e.getMessage());
            throw new ApiException(ErrorCode.EXCHANGE_CONFLICT,
                    "상대 요청이 방금 다른 매칭에 묶였습니다. 다시 시도해 주세요.");
        } catch (DataIntegrityViolationException | OptimisticLockException
                 | ObjectOptimisticLockingFailureException e) {
            log.debug("매칭 시도 실패(동시성): requestId={}, partnerRequestId={}",
                    myRequest.getId(), theirRequest.getId(), e);
            throw new ApiException(ErrorCode.EXCHANGE_CONFLICT);
        }
    }

    // =============================================================== 대기열 순위

    /** 내 요청의 대기열 순위: 내 1순위 희망 분반을 같이 원하는 대기 요청들을 우선순위 점수로 줄 세운다. */
    public QueuePositionResponse queuePosition(Long studentId, Long requestId) {
        studentService.getStudent(studentId);
        ExchangeRequest mine = requestService.loadMyRequest(studentId, requestId);
        if (mine.getStatus() != RequestStatus.WAITING) {
            return new QueuePositionResponse(requestId, mine.getStatus().name(), null, 0, null);
        }
        List<Long> wanted = preferenceRepository.findTargetOfferingIdsByRequestId(requestId);
        if (wanted.isEmpty()) {
            return new QueuePositionResponse(requestId, mine.getStatus().name(), null, 0, null);
        }
        List<ExchangeRequest> rivals = new ArrayList<>(requestRepository.findWaitingWantingOffering(wanted.get(0)));
        if (rivals.stream().noneMatch(r -> r.getId().equals(requestId))) {
            rivals.add(mine);
        }
        Map<Long, Long> scores = priorityScorer.scoreAll(rivals, LocalDateTime.now());
        rivals.sort(Comparator
                .comparing((ExchangeRequest r) -> scores.getOrDefault(r.getId(), 0L), Comparator.reverseOrder())
                .thenComparing(ExchangeRequest::getQueuedAt)
                .thenComparing(ExchangeRequest::getId));
        int position = 1;
        for (ExchangeRequest r : rivals) {
            if (r.getId().equals(requestId)) {
                break;
            }
            position++;
        }
        return new QueuePositionResponse(requestId, mine.getStatus().name(), position, rivals.size(),
                scores.getOrDefault(requestId, 0L));
    }

    // =============================================================== 세션 조회

    public List<MatchSessionResponse> myPendingSessions(Long studentId) {
        studentService.getStudent(studentId);
        LocalDateTime now = LocalDateTime.now();
        return participantRepository.findPendingByStudentId(studentId).stream()
                .map(p -> assembler.toResponse(p.getSession(), studentId, now))
                .toList();
    }

    public MatchSessionResponse getSession(Long studentId, Long sessionId) {
        studentService.getStudent(studentId);
        MatchSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        participantRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_MY_SESSION));
        return assembler.toResponse(session, studentId, LocalDateTime.now());
    }

    // =============================================================== 동의·거절

    /**
     * 동의. 두 번째 동의가 들어오면 그 자리에서 교환을 실행한다 (한 트랜잭션).
     * 교환 실행이 실패하면 전부 롤백되고 세션은 PENDING으로 남는다.
     */
    @Transactional
    public MatchSessionResponse accept(Long studentId, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        MatchSession session = loadPendingSession(sessionId, now);
        List<MatchParticipant> participants = participantRepository.findBySessionIdWithAll(sessionId);

        MatchParticipant me = findMe(participants, studentId);
        if (me.hasResponded()) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED);
        }

        me.accept(now);
        session.markFirstAccepted(now);

        boolean allAccepted = participants.stream().allMatch(MatchParticipant::hasAccepted);
        if (allAccepted) {
            executeSwap(session, participants, now);
        }
        participantRepository.flush();
        return assembler.toResponse(session, participants, studentId, now);
    }

    /** 거절. 세션을 끝내고 양쪽 요청을 대기열로 되돌린다. */
    @Transactional
    public MatchSessionResponse reject(Long studentId, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        MatchSession session = loadPendingSession(sessionId, now);
        List<MatchParticipant> participants = participantRepository.findBySessionIdWithAll(sessionId);

        MatchParticipant me = findMe(participants, studentId);
        if (me.hasResponded()) {
            throw new ApiException(ErrorCode.ALREADY_RESPONDED);
        }
        me.reject(now);
        session.reject(now, now.plusSeconds(properties.getRematchCooldownSeconds()));
        // 거절은 감점 (귀책 있는 쪽만)
        penaltyService.apply(me.getStudent(), session, PenaltyReason.REJECTED,
                properties.getPenaltyRejectPoints(), now);

        // 거절한 사람은 대기 시간 초기화(귀책), 상대는 유지.
        for (MatchParticipant p : participants) {
            boolean isRejecter = p.getStudent().getId().equals(studentId);
            if (p.getRequest().getStatus() == RequestStatus.MATCHED) {
                p.getRequest().requeue(now, !isRejecter);
            }
        }
        participantRepository.flush();
        return assembler.toResponse(session, participants, studentId, now);
    }

    /** 상대 깨우기. 최대 3회. */
    @Transactional
    public MatchSessionResponse wake(Long studentId, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        MatchSession session = loadPendingSession(sessionId, now);
        List<MatchParticipant> participants = participantRepository.findBySessionIdWithAll(sessionId);
        MatchParticipant me = findMe(participants, studentId);

        if (!me.sendWake()) {
            throw new ApiException(ErrorCode.WAKE_LIMIT_EXCEEDED);
        }
        participantRepository.flush();
        return assembler.toResponse(session, participants, studentId, now);
    }

    // =============================================================== 교환 실행

    /**
     * <b>실제 수강 교환.</b> 두 좌석의 분반을 맞바꾼다. 이 메서드가 호출하는 쪽의 트랜잭션에 참여하므로
     * 도중에 하나라도 실패하면 둘 다 롤백된다 — "두 수강 내역을 한 트랜잭션에서 변경"이라는 요구가 여기서 지켜진다.
     *
     * <p>순서가 중요하다. moveTo를 두 번 연달아 하면 중간 상태에서 UNIQUE(student_id, course_id)를
     * 건드릴 수 있으므로, 양쪽 조건을 먼저 다시 검사하고 나서 바꾼다.
     */
    private void executeSwap(MatchSession session, List<MatchParticipant> participants, LocalDateTime now) {
        if (participants.size() != 2) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "MVP는 세션당 참여자 2명만 지원합니다.");
        }
        MatchParticipant a = participants.get(0);
        MatchParticipant b = participants.get(1);

        Enrollment enrollmentA = a.getOfferedEnrollment();
        Enrollment enrollmentB = b.getOfferedEnrollment();

        if (properties.isPessimistic()) {
            // 좌석 행을 ID 오름차순으로 잠그고 최신 상태로 새로고침한 뒤 아래에서 스냅샷과 비교한다.
            Enrollment first = enrollmentA.getId() <= enrollmentB.getId() ? enrollmentA : enrollmentB;
            Enrollment second = first == enrollmentA ? enrollmentB : enrollmentA;
            entityManager.refresh(first, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(second, LockModeType.PESSIMISTIC_WRITE);
        }

        // 매칭 당시 스냅샷과 지금 좌석이 같은지 확인 (그사이 다른 교환으로 바뀌었을 수 있다)
        if (!enrollmentA.getOffering().getId().equals(a.getGiveOffering().getId())
                || !enrollmentB.getOffering().getId().equals(b.getGiveOffering().getId())) {
            session.invalidate(now);
            throw new ApiException(ErrorCode.EXCHANGE_CONFLICT,
                    "매칭 이후 수강 내역이 바뀌어 교환할 수 없습니다.");
        }

        // 교환 후 조건 재검사 (학점·시간표·같은 과목 중복)
        validator.check(a.getStudent(), enrollmentA, b.getGiveOffering())
                .ifPresent(code -> { throw new ApiException(code); });
        validator.check(b.getStudent(), enrollmentB, a.getGiveOffering())
                .ifPresent(code -> { throw new ApiException(code); });

        try {
            // 좌석 교환. 학생은 그대로 두고 분반(과 course_id)만 맞바꾼다.
            enrollmentA.moveTo(b.getGiveOffering());
            enrollmentB.moveTo(a.getGiveOffering());
            enrollmentRepository.flush();

            a.getRequest().markCompleted();
            b.getRequest().markCompleted();
            session.complete(now);
            requestRepository.flush();

        } catch (DataIntegrityViolationException | OptimisticLockException
                 | ObjectOptimisticLockingFailureException e) {
            // 낙관적 락(version) 충돌 또는 UNIQUE 위반 → 전체 롤백
            log.warn("교환 실행 실패(동시성): sessionId={}", session.getId(), e);
            throw new ApiException(ErrorCode.EXCHANGE_CONFLICT);
        }
    }

    // =============================================================== 만료 처리

    /**
     * 제한 시간이 지난 PENDING 세션을 만료시키고 양쪽 요청을 대기열로 되돌린다.
     * 스케줄러로 주기 실행할 수도 있고, 부하테스트에서는 API로 직접 불러 시점을 통제할 수도 있다.
     *
     * @return 만료 처리한 세션 수
     */
    @Transactional
    public int expireTimedOutSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<MatchSession> expired = sessionRepository.findExpiredPending(now);
        int processed = 0;
        for (MatchSession session : expired) {
            if (properties.isPessimistic()) {
                // 같은 순간 동의가 들어와 이미 끝난 세션이면 건드리지 않는다.
                entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
                if (!session.isPending()) {
                    continue;
                }
            }
            processed++;
            List<MatchParticipant> participants = participantRepository.findBySessionIdWithAll(session.getId());
            session.expire(now, now.plusSeconds(properties.getRematchCooldownSeconds()));
            for (MatchParticipant p : participants) {
                if (p.getRequest().getStatus() == RequestStatus.MATCHED) {
                    // 동의하지 않은 쪽이 귀책 → 대기 시간 초기화. 동의한 쪽은 유지.
                    p.getRequest().requeue(now, p.hasAccepted());
                }
                // 동의하지 않은 쪽(잠수)은 감점
                if (!p.hasAccepted()) {
                    penaltyService.apply(p.getStudent(), session, PenaltyReason.NO_RESPONSE,
                            properties.getPenaltyNoResponsePoints(), now);
                }
            }
        }
        return processed;
    }

    // =============================================================== 내부 공통

    private MatchSession loadPendingSession(Long sessionId, LocalDateTime now) {
        // PESSIMISTIC: 트랜잭션의 첫 쿼리로 세션 행을 잠근다. 두 참여자가 동시에 동의해도 한 명씩 처리되어
        // 서로 상대의 동의를 못 보고 둘 다 "아직 한 명만 동의"로 끝나는 일(교환 미실행)을 막는다.
        MatchSession session = properties.isPessimistic()
                ? entityManager.find(MatchSession.class, sessionId, LockModeType.PESSIMISTIC_WRITE)
                : sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            throw new ApiException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!session.isPending()) {
            throw new ApiException(ErrorCode.SESSION_NOT_PENDING,
                    "이미 끝난 매칭입니다. 상태=" + session.getStatus());
        }
        if (session.isExpiredAt(now)) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        return session;
    }

    private static MatchParticipant findMe(List<MatchParticipant> participants, Long studentId) {
        return participants.stream()
                .filter(p -> p.getStudent().getId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_MY_SESSION));
    }

    /** 교착 상태 방지: 항상 작은 ID부터 잠그고, 잠근 직후 DB의 최신 값으로 엔티티를 덮어쓴다. */
    private void lockAndRefresh(ExchangeRequest a, ExchangeRequest b) {
        ExchangeRequest first = a.getId() <= b.getId() ? a : b;
        ExchangeRequest second = first == a ? b : a;
        entityManager.refresh(first, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(second, LockModeType.PESSIMISTIC_WRITE);
    }
}

package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.config.MatchingProperties;
import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.course.CourseAssembler;
import com.ncp03.coursechange.course.CourseResponse;
import com.ncp03.coursechange.domain.CourseOffering;
import com.ncp03.coursechange.domain.CourseOfferingRepository;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.ExchangePreference;
import com.ncp03.coursechange.domain.ExchangePreferenceRepository;
import com.ncp03.coursechange.domain.ExchangeRequest;
import com.ncp03.coursechange.domain.ExchangeRequestRepository;
import com.ncp03.coursechange.domain.MatchParticipant;
import com.ncp03.coursechange.domain.MatchParticipantRepository;
import com.ncp03.coursechange.domain.RequestStatus;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.student.StudentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 교환 요청(등록·조회·수정·취소).
 *
 * <p>소유권 검증이 핵심이다. 스키마는 exchange_requests.student_id와 offered_enrollment_id를
 * 따로 참조하므로, 다른 학생의 enrollmentId를 보내도 DB 제약만으로는 막히지 않는다.
 * 그래서 등록 시 {@code enrollment.student.id == 헤더의 학생 ID}를 반드시 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class ExchangeRequestService {

    private static final List<RequestStatus> ACTIVE = List.of(RequestStatus.WAITING, RequestStatus.MATCHED);

    private final ExchangeRequestRepository requestRepository;
    private final ExchangePreferenceRepository preferenceRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository offeringRepository;
    private final MatchParticipantRepository participantRepository;
    private final StudentService studentService;
    private final ExchangeValidator validator;
    private final CourseAssembler courseAssembler;
    private final MatchingProperties properties;

    public ExchangeRequestService(ExchangeRequestRepository requestRepository,
                                  ExchangePreferenceRepository preferenceRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  CourseOfferingRepository offeringRepository,
                                  MatchParticipantRepository participantRepository,
                                  StudentService studentService,
                                  ExchangeValidator validator,
                                  CourseAssembler courseAssembler,
                                  MatchingProperties properties) {
        this.requestRepository = requestRepository;
        this.preferenceRepository = preferenceRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.offeringRepository = offeringRepository;
        this.participantRepository = participantRepository;
        this.studentService = studentService;
        this.validator = validator;
        this.courseAssembler = courseAssembler;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ 등록

    @Transactional
    public ExchangeRequest create(Long studentId, Long offeredEnrollmentId, List<Long> wantedOfferingIds) {
        Student student = studentService.getStudent(studentId);
        requireNotBlocked(student);

        Enrollment offered = loadMyEnrollment(studentId, offeredEnrollmentId);
        List<Long> targets = normalizePreferences(wantedOfferingIds);

        // 진행 중 요청 중복 (DB UNIQUE가 최종 방어선이지만, 먼저 409로 알려 준다)
        if (requestRepository.existsActiveByEnrollmentId(offeredEnrollmentId, ACTIVE)) {
            throw new ApiException(ErrorCode.REQUEST_ALREADY_EXISTS);
        }

        // 희망 분반이 애초에 불가능한 것(시간표 겹침·학점 초과 등)이면 등록 단계에서 거른다.
        List<CourseOffering> wanted = loadOfferings(targets);
        for (CourseOffering offering : wanted) {
            validator.validateOrThrow(student, offered, offering);
        }

        ExchangeRequest request;
        try {
            request = requestRepository.saveAndFlush(
                    ExchangeRequest.open(student, offered, LocalDateTime.now()));
        } catch (DataIntegrityViolationException e) {
            // uk_requests_active_enrollment 위반 = 같은 좌석으로 동시에 두 번 등록됨
            throw new ApiException(ErrorCode.REQUEST_ALREADY_EXISTS);
        }

        List<ExchangePreference> preferences = new ArrayList<>();
        for (int i = 0; i < wanted.size(); i++) {
            preferences.add(ExchangePreference.of(request, wanted.get(i), i + 1));
        }
        preferenceRepository.saveAll(preferences);
        return request;
    }

    // --------------------------------------------------------------- 조회

    public MyExchangeRequestsResponse myRequests(Long studentId) {
        studentService.getStudent(studentId);
        List<ExchangeRequest> requests = requestRepository.findAllByStudentIdWithOffering(studentId);
        List<ExchangeRequestResponse> items = toResponses(requests, studentId);
        return new MyExchangeRequestsResponse(studentId, items.size(), items);
    }

    public ExchangeRequestResponse getMyRequest(Long studentId, Long requestId) {
        ExchangeRequest request = loadMyRequest(studentId, requestId);
        return toResponses(List.of(request), studentId).get(0);
    }

    /** 요청 하나를 응답 DTO로. 등록 직후 응답에 쓴다. */
    @Transactional(readOnly = true)
    public ExchangeRequestResponse toResponse(ExchangeRequest request) {
        return toResponses(List.of(request), request.getStudent().getId()).get(0);
    }

    private List<ExchangeRequestResponse> toResponses(List<ExchangeRequest> requests, Long studentId) {
        if (requests.isEmpty()) {
            return List.of();
        }
        List<Long> requestIds = requests.stream().map(ExchangeRequest::getId).toList();

        // 희망 분반을 요청별로 모아 온다 (쿼리 1번)
        Map<Long, List<ExchangePreference>> prefsByRequest = preferenceRepository.findByRequestIds(requestIds).stream()
                .collect(Collectors.groupingBy(p -> p.getRequest().getId(),
                        java.util.LinkedHashMap::new, Collectors.toList()));

        // 분반 정보를 한 번에 조립 (내놓는 분반 + 모든 희망 분반)
        LinkedHashSet<CourseOffering> allOfferings = new LinkedHashSet<>();
        for (ExchangeRequest r : requests) {
            allOfferings.add(r.getOfferedEnrollment().getOffering());
        }
        prefsByRequest.values().forEach(list -> list.forEach(p -> allOfferings.add(p.getTargetOffering())));

        List<CourseOffering> offeringList = List.copyOf(allOfferings);
        List<CourseResponse> courseResponses = courseAssembler.toResponses(offeringList);
        Map<Long, CourseResponse> byOfferingId = new java.util.HashMap<>();
        for (int i = 0; i < offeringList.size(); i++) {
            byOfferingId.put(offeringList.get(i).getId(), courseResponses.get(i));
        }

        // 진행 중 매칭 세션 ID
        Map<Long, Long> sessionByRequest = participantRepository.findPendingByStudentId(studentId).stream()
                .collect(Collectors.toMap(p -> p.getRequest().getId(), p -> p.getSession().getId(),
                        (a, b) -> a));

        return requests.stream()
                .map(r -> new ExchangeRequestResponse(
                        r.getId(),
                        r.getStatus().name(),
                        r.getQueuedAt(),
                        r.getOfferedEnrollment().getId(),
                        byOfferingId.get(r.getOfferedEnrollment().getOffering().getId()),
                        prefsByRequest.getOrDefault(r.getId(), List.of()).stream()
                                .map(p -> new PreferenceResponse(p.getPreferenceRank(),
                                        byOfferingId.get(p.getTargetOffering().getId())))
                                .toList(),
                        sessionByRequest.get(r.getId())))
                .toList();
    }

    // --------------------------------------------------------------- 수정·취소

    /** 희망 분반 목록 교체. WAITING일 때만 가능 (MATCHED면 상대가 기다리는 중이라 못 바꾼다). */
    @Transactional
    public ExchangeRequestResponse replacePreferences(Long studentId, Long requestId, List<Long> wantedOfferingIds) {
        Student student = studentService.getStudent(studentId);
        ExchangeRequest request = loadMyRequest(studentId, requestId);
        if (request.getStatus() != RequestStatus.WAITING) {
            throw new ApiException(ErrorCode.REQUEST_NOT_MODIFIABLE,
                    "상태가 " + request.getStatus() + "라 희망 분반을 바꿀 수 없습니다.");
        }
        List<Long> targets = normalizePreferences(wantedOfferingIds);
        List<CourseOffering> wanted = loadOfferings(targets);
        for (CourseOffering offering : wanted) {
            validator.validateOrThrow(student, request.getOfferedEnrollment(), offering);
        }

        preferenceRepository.deleteByRequestId(requestId);
        List<ExchangePreference> preferences = new ArrayList<>();
        for (int i = 0; i < wanted.size(); i++) {
            preferences.add(ExchangePreference.of(request, wanted.get(i), i + 1));
        }
        preferenceRepository.saveAll(preferences);
        return toResponses(List.of(request), studentId).get(0);
    }

    /**
     * 요청 취소. WAITING은 바로 취소되고, MATCHED는 진행 중인 세션을 무효화한 뒤 취소한다.
     * (상대에게 패널티를 주는 귀책 판정은 이 단계에서 다루지 않는다 — 패널티 기능에서 처리.)
     */
    @Transactional
    public void cancel(Long studentId, Long requestId) {
        studentService.getStudent(studentId);
        ExchangeRequest request = loadMyRequest(studentId, requestId);
        if (!request.getStatus().isActive()) {
            throw new ApiException(ErrorCode.REQUEST_NOT_MODIFIABLE,
                    "상태가 " + request.getStatus() + "라 취소할 수 없습니다.");
        }

        if (request.getStatus() == RequestStatus.MATCHED) {
            LocalDateTime now = LocalDateTime.now();
            participantRepository.findPendingByStudentId(studentId).stream()
                    .filter(p -> p.getRequest().getId().equals(requestId))
                    .map(MatchParticipant::getSession)
                    .findFirst()
                    .ifPresent(session -> {
                        session.invalidate(now);
                        // 상대 요청은 대기열로 되돌린다. 상대 귀책이 아니므로 대기 시간은 유지한다.
                        participantRepository.findBySessionIdWithAll(session.getId()).stream()
                                .map(MatchParticipant::getRequest)
                                .filter(r -> !r.getId().equals(requestId))
                                .filter(r -> r.getStatus() == RequestStatus.MATCHED)
                                .forEach(r -> r.requeue(now, true));
                    });
        }
        request.cancel();
    }

    // --------------------------------------------------------------- 내부 공통

    /** 내 수강 내역인지 확인. 다른 학생 것이면 403. */
    Enrollment loadMyEnrollment(Long studentId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findDetailById(enrollmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.ENROLLMENT_NOT_FOUND));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.NOT_MY_ENROLLMENT);
        }
        return enrollment;
    }

    /** 내 요청인지 확인. 다른 학생 것이면 403. */
    ExchangeRequest loadMyRequest(Long studentId, Long requestId) {
        ExchangeRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.REQUEST_NOT_FOUND));
        if (!request.getStudent().getId().equals(studentId)) {
            throw new ApiException(ErrorCode.NOT_MY_REQUEST);
        }
        return request;
    }

    void requireNotBlocked(Student student) {
        LocalDateTime blockedUntil = student.getBlockedUntil();
        if (blockedUntil != null && blockedUntil.isAfter(LocalDateTime.now())) {
            throw new ApiException(ErrorCode.STUDENT_BLOCKED,
                    blockedUntil + "까지 교환 이용이 제한된 상태입니다.");
        }
    }

    /** 중복 제거 + 개수 제한. 순서(우선순위)는 유지한다. */
    private List<Long> normalizePreferences(List<Long> wantedOfferingIds) {
        if (wantedOfferingIds == null || wantedOfferingIds.isEmpty()) {
            throw new ApiException(ErrorCode.PREFERENCES_REQUIRED);
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(wantedOfferingIds);
        if (unique.size() != wantedOfferingIds.size()) {
            throw new ApiException(ErrorCode.DUPLICATE_PREFERENCE);
        }
        if (unique.size() > properties.getMaxPreferences()) {
            throw new ApiException(ErrorCode.TOO_MANY_PREFERENCES,
                    "희망 분반은 최대 " + properties.getMaxPreferences() + "개까지 보낼 수 있습니다.");
        }
        return List.copyOf(unique);
    }

    private List<CourseOffering> loadOfferings(List<Long> offeringIds) {
        List<CourseOffering> result = new ArrayList<>();
        for (Long id : offeringIds) {
            result.add(offeringRepository.findWithCourseById(id)
                    .orElseThrow(() -> new ApiException(ErrorCode.COURSE_NOT_FOUND,
                            "존재하지 않는 분반입니다. offeringId=" + id)));
        }
        return result;
    }
}

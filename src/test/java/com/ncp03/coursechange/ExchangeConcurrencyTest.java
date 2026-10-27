package com.ncp03.coursechange;

import com.ncp03.coursechange.common.config.MatchingProperties;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.ExchangeRequest;
import com.ncp03.coursechange.domain.ExchangeRequestRepository;
import com.ncp03.coursechange.domain.MatchParticipantRepository;
import com.ncp03.coursechange.domain.MatchSessionRepository;
import com.ncp03.coursechange.domain.RequestStatus;
import com.ncp03.coursechange.exchange.ExchangeRequestService;
import com.ncp03.coursechange.exchange.MatchingService;
import com.ncp03.coursechange.exchange.QuickMatchResponse;
import com.ncp03.coursechange.support.ResettableIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 시험. <b>10월 27일 안건("락 없이 → 1차 테스트 후 락 추가")의 근거가 되는 테스트다.</b>
 *
 * <p>이 테스트는 <b>PESSIMISTIC(락 있음)</b> 모드로 돌린다. 락 없음(NONE)에서는 같은 요청이 여러 매칭 세션에 묶이는
 * 이상 현상이 실제로 생기며, 그것이 1차 부하테스트에서 보여줄 문제다 (좌석 자체는 낙관적 락·UNIQUE가 지켜 준다).
 *
 * <p>검증하는 불변식 (락 있음 기준):
 * <ul>
 *   <li>한 요청이 두 세션에 동시에 묶이지 않는다</li>
 *   <li>한 좌석이 두 번 교환되지 않는다 (학점 합계·수강 과목 수가 보존된다)</li>
 * </ul>
 *
 * <p>락 모드가 바꾸는 것은 <b>실패율</b>이다.
 * NONE이면 EXCHANGE_CONFLICT(409)가 늘고, PESSIMISTIC이면 대기해서 성공률이 올라간다.
 * 부하테스트에서 비교할 지표가 바로 이 숫자다.
 *
 */
@TestPropertySource(properties = "app.matching.lock-mode=PESSIMISTIC")
class ExchangeConcurrencyTest extends ResettableIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ExchangeConcurrencyTest.class);

    @Autowired ExchangeRequestService requestService;
    @Autowired MatchingService matchingService;
    @Autowired ExchangeRequestRepository requestRepository;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired MatchSessionRepository sessionRepository;
    @Autowired MatchParticipantRepository participantRepository;
    @Autowired MatchingProperties properties;

    private long enrollmentIdOf(long studentId, long offeringId) {
        return enrollmentRepository.findAllByStudentIdWithOffering(studentId).stream()
                .filter(e -> e.getOffering().getId().equals(offeringId))
                .map(Enrollment::getId)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "학생 " + studentId + "은 분반 " + offeringId + "을 듣고 있지 않습니다."));
    }

    @Test
    @DisplayName("두 학생이 동시에 서로를 매칭해도 세션은 1개만 생기고 요청이 중복으로 묶이지 않는다")
    void concurrentMutualQuickMatchCreatesAtMostOneSession() throws Exception {
        log.info("현재 락 모드 = {}", properties.getLockMode());

        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        ExchangeRequest b = requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));

        AtomicInteger matchedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger notMatchedCount = new AtomicInteger();

        runSimultaneously(List.of(
                task(() -> matchingService.quickMatch(1L, a.getId()), matchedCount, conflictCount, notMatchedCount),
                task(() -> matchingService.quickMatch(2L, b.getId()), matchedCount, conflictCount, notMatchedCount)));

        log.info("결과: 매칭 성공 {}건, 충돌(409) {}건, 상대 없음 {}건",
                matchedCount.get(), conflictCount.get(), notMatchedCount.get());

        // 불변식 1: 세션은 최대 1개
        assertThat(sessionRepository.findAll()).hasSizeLessThanOrEqualTo(1);
        // 불변식 2: 참여자는 최대 2명 (한 요청이 두 세션에 묶이지 않았다)
        assertThat(participantRepository.findAll()).hasSizeLessThanOrEqualTo(2);
        // 불변식 3: 성공했다면 양쪽 모두 MATCHED, 아니면 양쪽 모두 WAITING (한쪽만 MATCHED인 상태는 없다)
        RequestStatus statusA = requestRepository.findById(a.getId()).orElseThrow().getStatus();
        RequestStatus statusB = requestRepository.findById(b.getId()).orElseThrow().getStatus();
        assertThat(statusA).isEqualTo(statusB);
    }

    @Test
    @DisplayName("같은 요청으로 빠른 매칭을 동시에 10번 호출해도 좌석이 중복 교환되지 않는다")
    void repeatedQuickMatchDoesNotDoubleBook() throws Exception {
        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));

        long creditsBefore1 = enrollmentRepository.sumCreditsByStudentId(1L);
        long creditsBefore2 = enrollmentRepository.sumCreditsByStudentId(2L);
        int countBefore1 = enrollmentRepository.findAllByStudentIdWithOffering(1L).size();

        AtomicInteger matched = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger notMatched = new AtomicInteger();

        List<Callable<Void>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(task(() -> matchingService.quickMatch(1L, a.getId()), matched, conflicts, notMatched));
        }
        runSimultaneously(tasks);

        log.info("10회 동시 호출 결과: 성공 {}건, 충돌 {}건, 상대없음/상태오류 {}건",
                matched.get(), conflicts.get(), notMatched.get());

        // 매칭은 한 번만 성립해야 한다
        assertThat(sessionRepository.findAll()).hasSizeLessThanOrEqualTo(1);
        assertThat(participantRepository.findAll()).hasSizeLessThanOrEqualTo(2);
        // 아직 동의 전이므로 좌석은 그대로
        assertThat(enrollmentRepository.sumCreditsByStudentId(1L)).isEqualTo(creditsBefore1);
        assertThat(enrollmentRepository.sumCreditsByStudentId(2L)).isEqualTo(creditsBefore2);
        assertThat(enrollmentRepository.findAllByStudentIdWithOffering(1L)).hasSize(countBefore1);
    }

    @Test
    @DisplayName("양쪽이 동시에 동의해도 교환은 한 번만 실행된다 (학점·과목 수 보존)")
    void concurrentAcceptExecutesSwapOnce() throws Exception {
        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        ExchangeRequest b = requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));

        QuickMatchResponse match = matchingService.quickMatch(2L, b.getId());
        assertThat(match.matched()).isTrue();
        long sessionId = match.session().sessionId();

        long credits1Before = enrollmentRepository.sumCreditsByStudentId(1L);
        long credits2Before = enrollmentRepository.sumCreditsByStudentId(2L);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();

        runSimultaneously(List.of(
                task(() -> matchingService.accept(1L, sessionId), ok, failed, other),
                task(() -> matchingService.accept(2L, sessionId), ok, failed, other)));

        log.info("동시 동의 결과: 성공 {}건, 충돌 {}건, 기타 {}건", ok.get(), failed.get(), other.get());

        // 학점 합계와 과목 수는 교환 전후로 같아야 한다 (1:1 교환이므로)
        assertThat(enrollmentRepository.sumCreditsByStudentId(1L)).isEqualTo(credits1Before);
        assertThat(enrollmentRepository.sumCreditsByStudentId(2L)).isEqualTo(credits2Before);

        // 두 좌석이 같은 분반을 가리키는 일은 없어야 한다
        List<Long> offerings1 = enrollmentRepository.findAllByStudentIdWithOffering(1L).stream()
                .map(e -> e.getOffering().getId()).toList();
        assertThat(offerings1).doesNotHaveDuplicates();

        // 요청 상태는 양쪽이 같아야 한다 (둘 다 COMPLETED이거나 둘 다 MATCHED)
        assertThat(requestRepository.findById(a.getId()).orElseThrow().getStatus())
                .isEqualTo(requestRepository.findById(b.getId()).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------- 도구

    /** 스레드들을 래치로 묶어 같은 순간에 출발시킨다. */
    private void runSimultaneously(List<Callable<Void>> tasks) throws Exception {
        int n = tasks.size();
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = new java.util.ArrayList<>();
            for (Callable<Void> t : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return t.call();
                }));
            }
            start.countDown();
            for (Future<Void> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 예외를 삼키고 분류만 세는 작업으로 감싼다 (동시성 테스트에서 예외는 정상 결과다). */
    private Callable<Void> task(Runnable action, AtomicInteger success, AtomicInteger conflict, AtomicInteger other) {
        return () -> {
            try {
                action.run();
                success.incrementAndGet();
            } catch (com.ncp03.coursechange.common.error.ApiException e) {
                if (e.getErrorCode() == com.ncp03.coursechange.common.error.ErrorCode.EXCHANGE_CONFLICT) {
                    conflict.incrementAndGet();
                } else {
                    other.incrementAndGet();
                }
            } catch (RuntimeException e) {
                log.debug("동시성 테스트 중 예외", e);
                other.incrementAndGet();
            }
            return null;
        };
    }
}

package com.ncp03.coursechange;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.Enrollment;
import com.ncp03.coursechange.domain.ExchangeRequest;
import com.ncp03.coursechange.domain.ExchangeRequestRepository;
import com.ncp03.coursechange.domain.PenaltyEventRepository;
import com.ncp03.coursechange.domain.RequestStatus;
import com.ncp03.coursechange.domain.StudentRepository;
import com.ncp03.coursechange.exchange.ExchangeRequestService;
import com.ncp03.coursechange.exchange.MatchingService;
import com.ncp03.coursechange.exchange.QuickMatchResponse;
import com.ncp03.coursechange.exchange.QueuePositionResponse;
import com.ncp03.coursechange.support.ResettableIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 패널티(거절·잠수 감점, 누적 시 차단), 만료 자동 처리, 대기열 순위.
 * 차단 기준을 5점으로 낮춰서 잠수 1회(5점)로 차단되는지 본다.
 */
@TestPropertySource(properties = {
        "app.matching.penalty-block-threshold-points=5",
        "app.matching.penalty-reject-points=2",
        "app.matching.penalty-no-response-points=5"})
class PenaltyAndQueueTest extends ResettableIntegrationTest {

    @Autowired ExchangeRequestService requestService;
    @Autowired MatchingService matchingService;
    @Autowired EnrollmentRepository enrollmentRepository;
    @Autowired ExchangeRequestRepository requestRepository;
    @Autowired PenaltyEventRepository penaltyRepository;
    @Autowired StudentRepository studentRepository;
    @Autowired JdbcTemplate jdbc;

    private long enrollmentIdOf(long studentId, long offeringId) {
        return enrollmentRepository.findAllByStudentIdWithOffering(studentId).stream()
                .filter(e -> e.getOffering().getId().equals(offeringId))
                .map(Enrollment::getId)
                .findFirst().orElseThrow();
    }

    private long matchedSession(ExchangeRequest a, ExchangeRequest b) {
        QuickMatchResponse m = matchingService.quickMatch(2L, b.getId());
        assertThat(m.matched()).isTrue();
        return m.session().sessionId();
    }

    @Test
    @DisplayName("거절하면 거절한 쪽만 2점 감점되고, 기준 미만이라 차단은 안 된다")
    void rejectPenalizesOnlyRejecter() {
        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        ExchangeRequest b = requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));
        long sessionId = matchedSession(a, b);

        matchingService.reject(1L, sessionId);

        assertThat(penaltyRepository.findAll()).hasSize(1);
        assertThat(penaltyRepository.findAll().get(0).getDeductionPoints()).isEqualTo(2);
        assertThat(penaltyRepository.findAll().get(0).getStudent().getId()).isEqualTo(1L);
        assertThat(studentRepository.findById(1L).orElseThrow().getBlockedUntil()).isNull();
        assertThat(requestRepository.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.WAITING);
        assertThat(requestRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.WAITING);
    }

    @Test
    @DisplayName("만료: 동의 안 한 쪽(잠수)만 5점 감점·차단, 동의한 쪽은 무감점, 두 요청은 대기열 복귀, 재실행해도 중복 감점 없음")
    void expirePenalizesNoResponse() {
        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        ExchangeRequest b = requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));
        long sessionId = matchedSession(a, b);

        matchingService.accept(1L, sessionId); // 학생 1만 동의, 학생 2는 잠수

        // 제한 시간이 지난 것처럼 시각을 과거로 돌린다.
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("update match_sessions set opened_at = ?, expires_at = ? where session_id = ?",
                now.minusMinutes(10), now.minusMinutes(5), sessionId);

        assertThat(matchingService.expireTimedOutSessions()).isEqualTo(1);
        assertThat(matchingService.expireTimedOutSessions()).isZero(); // 이미 처리됨

        assertThat(penaltyRepository.findAll()).hasSize(1);
        assertThat(penaltyRepository.findAll().get(0).getStudent().getId()).isEqualTo(2L);
        assertThat(penaltyRepository.findAll().get(0).getDeductionPoints()).isEqualTo(5);
        assertThat(studentRepository.findById(2L).orElseThrow().getBlockedUntil()).isAfter(LocalDateTime.now());
        assertThat(studentRepository.findById(1L).orElseThrow().getBlockedUntil()).isNull();
        assertThat(requestRepository.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.WAITING);
        assertThat(requestRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.WAITING);

        // 차단된 학생은 매칭을 요청할 수 없다
        assertThatThrownBy(() -> matchingService.quickMatch(2L, b.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.STUDENT_BLOCKED);
    }

    @Test
    @DisplayName("대기열 순위: 대기 중이면 순위·경쟁자 수·점수, 매칭되면 순위 없음")
    void queuePosition() {
        ExchangeRequest a = requestService.create(1L, enrollmentIdOf(1, 10), List.of(9L));
        ExchangeRequest b = requestService.create(2L, enrollmentIdOf(2, 9), List.of(10L));

        QueuePositionResponse waiting = matchingService.queuePosition(1L, a.getId());
        assertThat(waiting.status()).isEqualTo("WAITING");
        assertThat(waiting.position()).isEqualTo(1);
        assertThat(waiting.competitors()).isEqualTo(1);
        assertThat(waiting.score()).isNotNull();

        matchedSession(a, b);
        QueuePositionResponse matched = matchingService.queuePosition(1L, a.getId());
        assertThat(matched.status()).isEqualTo("MATCHED");
        assertThat(matched.position()).isNull();

        // 남의 요청은 조회할 수 없다
        assertThatThrownBy(() -> matchingService.queuePosition(2L, a.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_MY_REQUEST);
    }
}

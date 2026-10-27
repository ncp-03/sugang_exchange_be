package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.config.MatchingProperties;
import com.ncp03.coursechange.domain.EnrollmentRepository;
import com.ncp03.coursechange.domain.ExchangeRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 대기열 우선순위 점수. 높을수록 먼저 매칭된다.
 *
 * <pre>
 * 점수 = 학년 x 10
 *      + 기다린 분(최대 120분) x 1
 *      + (최대학점 - 현재학점) x 1      // 학점을 덜 채운 사람이 유리
 *      - 최근 24시간 감점 x 5
 * </pre>
 * 가중치는 application.yml(app.matching.score-*)에서 바꾼다.
 * 점수 계산에 필요한 학점·감점은 후보 전체를 쿼리 2번으로 한꺼번에 가져온다 (후보마다 쿼리하지 않는다).
 */
@Component
public class PriorityScorer {

    private final MatchingProperties properties;
    private final EnrollmentRepository enrollmentRepository;
    private final PenaltyService penaltyService;

    public PriorityScorer(MatchingProperties properties, EnrollmentRepository enrollmentRepository,
                          PenaltyService penaltyService) {
        this.properties = properties;
        this.enrollmentRepository = enrollmentRepository;
        this.penaltyService = penaltyService;
    }

    /** 요청 id → 점수. */
    @Transactional(readOnly = true)
    public Map<Long, Long> scoreAll(Collection<ExchangeRequest> requests, LocalDateTime now) {
        Map<Long, Long> scores = new HashMap<>();
        if (requests.isEmpty()) {
            return scores;
        }
        List<Long> studentIds = requests.stream().map(r -> r.getStudent().getId()).distinct().toList();
        Map<Long, Long> credits = new HashMap<>();
        for (Object[] row : enrollmentRepository.sumCreditsByStudentIds(studentIds)) {
            credits.put((Long) row[0], ((Number) row[1]).longValue());
        }
        Map<Long, Long> penalties = penaltyService.recentPoints(studentIds, now);

        for (ExchangeRequest r : requests) {
            Long sid = r.getStudent().getId();
            scores.put(r.getId(), score(r, credits.getOrDefault(sid, 0L), penalties.getOrDefault(sid, 0L), now));
        }
        return scores;
    }

    long score(ExchangeRequest r, long currentCredits, long penaltyPoints, LocalDateTime now) {
        long waitMinutes = Math.max(0, Duration.between(r.getQueuedAt(), now).toMinutes());
        waitMinutes = Math.min(waitMinutes, properties.getScoreWaitCapMinutes());
        long creditSlack = Math.max(0, r.getStudent().getMaxCredits() - currentCredits);
        return (long) r.getStudent().getYearLevel() * properties.getScoreYearWeight()
                + waitMinutes * properties.getScoreWaitWeightPerMinute()
                + creditSlack * properties.getScoreCreditSlackWeight()
                - penaltyPoints * properties.getScorePenaltyWeight();
    }
}

package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.config.MatchingProperties;
import com.ncp03.coursechange.domain.MatchSession;
import com.ncp03.coursechange.domain.PenaltyEvent;
import com.ncp03.coursechange.domain.PenaltyEventRepository;
import com.ncp03.coursechange.domain.PenaltyReason;
import com.ncp03.coursechange.domain.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 패널티(감점) 부과. 호출한 쪽의 트랜잭션에 참여한다 (거절·만료 처리와 같이 커밋/롤백).
 *
 * <p>단계: 감점 기록 → 최근 N시간 누적 감점이 기준 이상이면 일정 시간 매칭 차단(blocked_until).
 * 같은 사건은 idempotency_key(사유:세션:학생)로 한 번만 감점한다.
 */
@Service
@Transactional(readOnly = true)
public class PenaltyService {

    private static final Logger log = LoggerFactory.getLogger(PenaltyService.class);

    private final PenaltyEventRepository repository;
    private final MatchingProperties properties;

    public PenaltyService(PenaltyEventRepository repository, MatchingProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** @return 새로 감점했으면 true, 이미 같은 사건이 기록돼 있으면 false */
    @Transactional
    public boolean apply(Student student, MatchSession session, PenaltyReason reason, int points, LocalDateTime now) {
        String key = reason.name() + ":" + session.getId() + ":" + student.getId();
        if (repository.existsByIdempotencyKey(key)) {
            return false;
        }
        repository.saveAndFlush(PenaltyEvent.of(student, session, reason, points, key, now));

        long total = recentPoints(student.getId(), now);
        if (total >= properties.getPenaltyBlockThresholdPoints()) {
            student.blockUntil(now.plusMinutes(properties.getPenaltyBlockMinutes()));
            log.info("패널티 누적으로 매칭 차단: studentId={}, 누적 {}점, 차단 해제={}",
                    student.getId(), total, student.getBlockedUntil());
        }
        return true;
    }

    public long recentPoints(Long studentId, LocalDateTime now) {
        return repository.sumPointsSince(studentId, now.minusHours(properties.getPenaltyWindowHours()));
    }

    /** 여러 학생의 최근 감점 합 (없으면 맵에 없음 = 0점). */
    public Map<Long, Long> recentPoints(Collection<Long> studentIds, LocalDateTime now) {
        Map<Long, Long> result = new HashMap<>();
        if (studentIds.isEmpty()) {
            return result;
        }
        for (Object[] row : repository.sumPointsSinceByStudentIds(
                studentIds, now.minusHours(properties.getPenaltyWindowHours()))) {
            result.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}

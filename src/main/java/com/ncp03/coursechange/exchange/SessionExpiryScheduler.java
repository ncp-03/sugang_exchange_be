package com.ncp03.coursechange.exchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제한 시간이 지난 매칭 세션을 주기적으로 만료 처리한다 (양쪽 요청은 대기열로 복귀, 잠수한 쪽은 감점).
 * 끄려면 app.matching.expire-scheduler-enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.matching.expire-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SessionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryScheduler.class);

    private final MatchingService matchingService;

    public SessionExpiryScheduler(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @Scheduled(fixedDelayString = "${app.matching.expire-scheduler-interval-ms:5000}",
            initialDelayString = "${app.matching.expire-scheduler-interval-ms:5000}")
    public void run() {
        try {
            int expired = matchingService.expireTimedOutSessions();
            if (expired > 0) {
                log.info("만료 처리한 매칭 세션 {}건", expired);
            }
        } catch (RuntimeException e) {
            // 다음 주기에 다시 시도한다. 스케줄러가 죽으면 안 된다.
            log.warn("만료 세션 처리 중 오류 (다음 주기에 재시도)", e);
        }
    }
}

package com.ncp03.coursechange.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 매칭·교환 동작 설정.
 *
 * <p>lockMode가 핵심이다. 10월 27일 부하테스트는 "락 없는 버전 → 락 추가"를 비교하는 것이므로,
 * 코드를 두 벌 만들지 않고 이 값만 바꿔 같은 코드로 두 번 측정한다.
 *
 * <pre>
 * # 1차 (락 없음)
 * java -jar app.jar --app.matching.lock-mode=NONE
 * # 2차 (비관적 락)
 * java -jar app.jar --app.matching.lock-mode=PESSIMISTIC
 * </pre>
 *
 * <p>NONE이어도 DB 제약(UNIQUE uk_requests_active_enrollment, 낙관적 락 version)은 살아 있어서
 * 데이터가 깨지지는 않는다. 대신 동시 요청이 겹치면 409 EXCHANGE_CONFLICT가 늘어난다.
 * 바로 이 실패율 차이를 측정하는 것이 부하테스트의 목적이다.
 */
@Component
@ConfigurationProperties(prefix = "app.matching")
public class MatchingProperties {

    public enum LockMode {
        /** 락 없음. 1차 측정용. 동시성 충돌은 DB 제약·낙관적 락으로만 걸러진다. */
        NONE,
        /** SELECT ... FOR UPDATE로 요청·좌석 행을 잠근다. 2차 측정용. */
        PESSIMISTIC
    }

    /** 락 전략. 기본값은 NONE (1차 테스트가 먼저이므로). */
    private LockMode lockMode = LockMode.NONE;

    /** 매칭 후 양쪽이 동의해야 하는 제한 시간(초). */
    private int acceptTimeoutSeconds = 120;

    /** 매칭 실패(거절·만료) 후 같은 두 학생이 다시 매칭될 수 없는 시간(초). */
    private int rematchCooldownSeconds = 300;

    /** 요청 1건에 넣을 수 있는 희망 분반 최대 개수. */
    private int maxPreferences = 10;

    public boolean isPessimistic() {
        return lockMode == LockMode.PESSIMISTIC;
    }

    public LockMode getLockMode() { return lockMode; }
    public void setLockMode(LockMode lockMode) { this.lockMode = lockMode; }

    public int getAcceptTimeoutSeconds() { return acceptTimeoutSeconds; }
    public void setAcceptTimeoutSeconds(int acceptTimeoutSeconds) { this.acceptTimeoutSeconds = acceptTimeoutSeconds; }

    public int getRematchCooldownSeconds() { return rematchCooldownSeconds; }
    public void setRematchCooldownSeconds(int rematchCooldownSeconds) {
        this.rematchCooldownSeconds = rematchCooldownSeconds;
    }

    public int getMaxPreferences() { return maxPreferences; }
    public void setMaxPreferences(int maxPreferences) { this.maxPreferences = maxPreferences; }
}

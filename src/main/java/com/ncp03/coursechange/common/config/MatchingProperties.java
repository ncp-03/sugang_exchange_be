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
 * <p>NONE이어도 좌석 교환은 DB 제약(UNIQUE)과 낙관적 락(version)이 지켜서 좌석 데이터는 깨지지 않는다.
 * 다만 매칭 단계에는 막는 장치가 없어서, 동시 요청이 겹치면 한 요청이 여러 세션에 묶이거나
 * 409 EXCHANGE_CONFLICT가 늘어난다. 바로 이 차이를 측정하는 것이 부하테스트의 목적이다.
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

    // ---- 패널티 (회의 결정: 만료 잠수 5점, 거절 1~2점 → 2점, 누적되면 일정 시간 매칭 차단)
    /** 거절 버튼 감점. */
    private int penaltyRejectPoints = 2;
    /** 만료까지 동의하지 않은 쪽(잠수) 감점. */
    private int penaltyNoResponsePoints = 5;
    /** 최근 N시간 누적 감점이 이 값 이상이면 매칭을 막는다. */
    private int penaltyBlockThresholdPoints = 10;
    private int penaltyWindowHours = 24;
    /** 차단 시간(분). */
    private int penaltyBlockMinutes = 60;

    // ---- 만료 자동 처리 스케줄러
    private boolean expireSchedulerEnabled = true;
    private long expireSchedulerIntervalMs = 5000;

    // ---- 우선순위 점수 가중치 (학년·기다린 시간·여유 학점·감점). 점수가 높을수록 먼저 매칭 후보가 된다.
    private int scoreYearWeight = 10;
    private int scoreWaitWeightPerMinute = 1;
    private int scoreWaitCapMinutes = 120;
    private int scoreCreditSlackWeight = 1;
    private int scorePenaltyWeight = 5;

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

    public int getPenaltyRejectPoints() { return penaltyRejectPoints; }
    public void setPenaltyRejectPoints(int v) { this.penaltyRejectPoints = v; }
    public int getPenaltyNoResponsePoints() { return penaltyNoResponsePoints; }
    public void setPenaltyNoResponsePoints(int v) { this.penaltyNoResponsePoints = v; }
    public int getPenaltyBlockThresholdPoints() { return penaltyBlockThresholdPoints; }
    public void setPenaltyBlockThresholdPoints(int v) { this.penaltyBlockThresholdPoints = v; }
    public int getPenaltyWindowHours() { return penaltyWindowHours; }
    public void setPenaltyWindowHours(int v) { this.penaltyWindowHours = v; }
    public int getPenaltyBlockMinutes() { return penaltyBlockMinutes; }
    public void setPenaltyBlockMinutes(int v) { this.penaltyBlockMinutes = v; }
    public boolean isExpireSchedulerEnabled() { return expireSchedulerEnabled; }
    public void setExpireSchedulerEnabled(boolean v) { this.expireSchedulerEnabled = v; }
    public long getExpireSchedulerIntervalMs() { return expireSchedulerIntervalMs; }
    public void setExpireSchedulerIntervalMs(long v) { this.expireSchedulerIntervalMs = v; }
    public int getScoreYearWeight() { return scoreYearWeight; }
    public void setScoreYearWeight(int v) { this.scoreYearWeight = v; }
    public int getScoreWaitWeightPerMinute() { return scoreWaitWeightPerMinute; }
    public void setScoreWaitWeightPerMinute(int v) { this.scoreWaitWeightPerMinute = v; }
    public int getScoreWaitCapMinutes() { return scoreWaitCapMinutes; }
    public void setScoreWaitCapMinutes(int v) { this.scoreWaitCapMinutes = v; }
    public int getScoreCreditSlackWeight() { return scoreCreditSlackWeight; }
    public void setScoreCreditSlackWeight(int v) { this.scoreCreditSlackWeight = v; }
    public int getScorePenaltyWeight() { return scorePenaltyWeight; }
    public void setScorePenaltyWeight(int v) { this.scorePenaltyWeight = v; }

    public int getMaxPreferences() { return maxPreferences; }
    public void setMaxPreferences(int maxPreferences) { this.maxPreferences = maxPreferences; }
}

package com.ncp03.coursechange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 매칭 세션: 두 요청이 맞물려 교환 확정을 기다리는 상태. */
@Entity
@Table(name = "match_sessions")
public class MatchSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "first_accepted_at")
    private LocalDateTime firstAcceptedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "rematch_allowed_at")
    private LocalDateTime rematchAllowedAt;

    protected MatchSession() {
    }

    private MatchSession(LocalDateTime openedAt, LocalDateTime expiresAt) {
        this.status = SessionStatus.PENDING;
        this.openedAt = openedAt;
        this.expiresAt = expiresAt;
    }

    /** 매칭 성립 직후의 세션. expiresAt은 openedAt보다 뒤여야 한다(DB CHECK). */
    public static MatchSession open(LocalDateTime now, LocalDateTime expiresAt) {
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt은 openedAt보다 뒤여야 합니다.");
        }
        return new MatchSession(now, expiresAt);
    }

    /** 첫 동의가 들어왔다. 두 번째 호출은 무시한다(첫 동의 시각만 기록). */
    public void markFirstAccepted(LocalDateTime now) {
        if (this.firstAcceptedAt == null) {
            this.firstAcceptedAt = now;
        }
    }

    /** 양쪽 동의 완료 → 교환 실행까지 끝났다. */
    public void complete(LocalDateTime now) {
        requirePending();
        this.status = SessionStatus.COMPLETED;
        this.finishedAt = now;
    }

    /** 한쪽이 거절했다. */
    public void reject(LocalDateTime now, LocalDateTime rematchAllowedAt) {
        requirePending();
        this.status = SessionStatus.REJECTED;
        this.finishedAt = now;
        this.rematchAllowedAt = rematchAllowedAt;
    }

    /** 제한 시간이 지났다. */
    public void expire(LocalDateTime now, LocalDateTime rematchAllowedAt) {
        requirePending();
        this.status = SessionStatus.EXPIRED;
        this.finishedAt = now;
        this.rematchAllowedAt = rematchAllowedAt;
    }

    /** 전제가 깨져(수강 내역 변경 등) 세션을 무효화한다. */
    public void invalidate(LocalDateTime now) {
        requirePending();
        this.status = SessionStatus.INVALIDATED;
        this.finishedAt = now;
    }

    public boolean isPending() {
        return status == SessionStatus.PENDING;
    }

    public boolean isExpiredAt(LocalDateTime now) {
        return now.isAfter(expiresAt);
    }

    private void requirePending() {
        if (status != SessionStatus.PENDING) {
            throw new IllegalStateException("PENDING 세션만 바꿀 수 있습니다. 현재 상태=" + status);
        }
    }

    public Long getId() { return id; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getOpenedAt() { return openedAt; }
    public LocalDateTime getFirstAcceptedAt() { return firstAcceptedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public LocalDateTime getRematchAllowedAt() { return rematchAllowedAt; }
}

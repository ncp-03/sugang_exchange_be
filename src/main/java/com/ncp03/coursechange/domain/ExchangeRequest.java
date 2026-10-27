package com.ncp03.coursechange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 교환 요청: "이 수강 내역을 내놓고 희망 분반 중 하나를 받고 싶다".
 * DB의 active_enrollment_id 생성 컬럼 + UNIQUE가 같은 수강 내역의 진행 중 요청을 1개로 제한한다
 * (생성 컬럼이라 엔티티에는 매핑하지 않는다).
 */
@Entity
@Table(name = "exchange_requests")
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offered_enrollment_id", nullable = false)
    private Enrollment offeredEnrollment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    @Column(name = "queued_at", nullable = false)
    private LocalDateTime queuedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ExchangeRequest() {
    }

    private ExchangeRequest(Student student, Enrollment offeredEnrollment, LocalDateTime queuedAt) {
        this.student = student;
        this.offeredEnrollment = offeredEnrollment;
        this.status = RequestStatus.WAITING;
        this.queuedAt = queuedAt;
    }

    /** 새 교환 요청을 WAITING(대기열 진입) 상태로 만든다. */
    public static ExchangeRequest open(Student student, Enrollment offeredEnrollment, LocalDateTime now) {
        return new ExchangeRequest(student, offeredEnrollment, now);
    }

    /** 매칭 세션에 묶였다. WAITING일 때만 가능. */
    public void markMatched() {
        requireStatus(RequestStatus.WAITING);
        this.status = RequestStatus.MATCHED;
    }

    /** 교환이 실제로 끝났다. MATCHED일 때만 가능. */
    public void markCompleted() {
        requireStatus(RequestStatus.MATCHED);
        this.status = RequestStatus.COMPLETED;
    }

    /**
     * 매칭이 깨져 대기열로 돌아간다. queued_at을 다시 찍으면 기다린 시간이 초기화되므로,
     * 본인 귀책이 아닐 때는 원래 queued_at을 유지하도록 keepQueuedAt=true로 부른다.
     */
    public void requeue(LocalDateTime now, boolean keepQueuedAt) {
        requireStatus(RequestStatus.MATCHED);
        this.status = RequestStatus.WAITING;
        if (!keepQueuedAt) {
            this.queuedAt = now;
        }
    }

    /** 본인이 취소했다. 진행 중(WAITING·MATCHED)일 때만 가능. */
    public void cancel() {
        if (!status.isActive()) {
            throw new IllegalStateException("진행 중인 요청만 취소할 수 있습니다. 현재 상태=" + status);
        }
        this.status = RequestStatus.CANCELLED;
    }

    private void requireStatus(RequestStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException("상태가 " + expected + "일 때만 가능합니다. 현재 상태=" + status);
        }
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Enrollment getOfferedEnrollment() { return offeredEnrollment; }
    public RequestStatus getStatus() { return status; }
    public LocalDateTime getQueuedAt() { return queuedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

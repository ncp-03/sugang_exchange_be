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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** 감점 이력. idempotency_key UNIQUE로 같은 사건의 중복 감점을 막는다. */
@Entity
@Table(name = "penalty_events")
public class PenaltyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private MatchSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 20)
    private PenaltyReason reasonCode;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "deduction_points", nullable = false)
    private Integer deductionPoints;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PenaltyEvent() {
    }

    public static PenaltyEvent of(Student student, MatchSession session, PenaltyReason reason,
                                  int points, String idempotencyKey, LocalDateTime now) {
        PenaltyEvent e = new PenaltyEvent();
        e.student = student;
        e.session = session;
        e.reasonCode = reason;
        e.deductionPoints = points;
        e.idempotencyKey = idempotencyKey;
        e.createdAt = now;
        return e;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public MatchSession getSession() { return session; }
    public PenaltyReason getReasonCode() { return reasonCode; }
    public Integer getDeductionPoints() { return deductionPoints; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

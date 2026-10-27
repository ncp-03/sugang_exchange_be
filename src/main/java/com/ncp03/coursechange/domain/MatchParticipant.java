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

/** 세션 참여자. 주는·받는 분반은 매칭 당시 스냅샷이다. */
@Entity
@Table(name = "match_participants")
public class MatchParticipant {

    /** 상대 깨우기 최대 횟수 (DB CHECK와 같은 값). */
    public static final int MAX_WAKE_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participant_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private MatchSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private ExchangeRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offered_enrollment_id", nullable = false)
    private Enrollment offeredEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "give_offering_id", nullable = false)
    private CourseOffering giveOffering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receive_offering_id", nullable = false)
    private CourseOffering receiveOffering;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private ResponseStatus responseStatus;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "wake_sent_count", nullable = false)
    private Integer wakeSentCount;

    protected MatchParticipant() {
    }

    private MatchParticipant(MatchSession session, ExchangeRequest request, CourseOffering receiveOffering) {
        this.session = session;
        this.request = request;
        this.student = request.getStudent();
        this.offeredEnrollment = request.getOfferedEnrollment();
        this.giveOffering = request.getOfferedEnrollment().getOffering();
        this.receiveOffering = receiveOffering;
        this.responseStatus = ResponseStatus.PENDING;
        this.wakeSentCount = 0;
    }

    /**
     * 참여자 1명. 주는 분반은 요청의 수강 내역에서, 받는 분반은 상대가 내놓은 분반에서 가져온다.
     * 주는·받는 분반이 같으면 DB CHECK(ck_participants_diff)가 막으므로 미리 거른다.
     */
    public static MatchParticipant of(MatchSession session, ExchangeRequest request, CourseOffering receiveOffering) {
        Long give = request.getOfferedEnrollment().getOffering().getId();
        if (give.equals(receiveOffering.getId())) {
            throw new IllegalArgumentException("주는 분반과 받는 분반이 같습니다. offeringId=" + give);
        }
        return new MatchParticipant(session, request, receiveOffering);
    }

    public void accept(LocalDateTime now) {
        requirePending();
        this.responseStatus = ResponseStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void reject(LocalDateTime now) {
        requirePending();
        this.responseStatus = ResponseStatus.REJECTED;
        this.respondedAt = now;
    }

    /** 상대 깨우기 1회. 한도를 넘으면 false를 돌려주고 아무것도 바꾸지 않는다. */
    public boolean sendWake() {
        if (wakeSentCount >= MAX_WAKE_COUNT) {
            return false;
        }
        this.wakeSentCount = wakeSentCount + 1;
        return true;
    }

    public boolean hasAccepted() {
        return responseStatus == ResponseStatus.ACCEPTED;
    }

    public boolean hasResponded() {
        return responseStatus != ResponseStatus.PENDING;
    }

    private void requirePending() {
        if (hasResponded()) {
            throw new IllegalStateException("이미 응답했습니다. 현재 응답=" + responseStatus);
        }
    }

    public Long getId() { return id; }
    public MatchSession getSession() { return session; }
    public ExchangeRequest getRequest() { return request; }
    public Student getStudent() { return student; }
    public Enrollment getOfferedEnrollment() { return offeredEnrollment; }
    public CourseOffering getGiveOffering() { return giveOffering; }
    public CourseOffering getReceiveOffering() { return receiveOffering; }
    public ResponseStatus getResponseStatus() { return responseStatus; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public Integer getWakeSentCount() { return wakeSentCount; }
}

package com.ncp03.coursechange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 현재 수강 내역 (학생이 차지한 좌석).
 * course_id는 (offering_id, course_id) 복합 FK로 분반과 일치가 보장되고,
 * UNIQUE(student_id, course_id)로 같은 과목 두 분반 동시 수강을 DB가 막는다.
 * 교환 시에는 학생은 그대로 두고 {@link #moveTo(CourseOffering)}로 분반을 바꾼다.
 */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrollment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "enrolled_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime enrolledAt;

    @Version
    @Column(nullable = false)
    private Integer version;

    protected Enrollment() {
    }

    private Enrollment(Student student, CourseOffering offering) {
        this.student = student;
        this.offering = offering;
        this.courseId = offering.getCourse().getId();
    }

    /** 수강신청으로 좌석 1개를 잡는다. enrolled_at은 DB 기본값이 채운다. */
    public static Enrollment of(Student student, CourseOffering offering) {
        return new Enrollment(student, offering);
    }

    /** 교환 성공 시 이 좌석을 다른 분반으로 옮긴다. course_id도 함께 맞춘다. */
    public void moveTo(CourseOffering target) {
        this.offering = target;
        this.courseId = target.getCourse().getId();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public CourseOffering getOffering() { return offering; }
    public Long getCourseId() { return courseId; }
    public LocalDateTime getEnrolledAt() { return enrolledAt; }
    public Integer getVersion() { return version; }
}

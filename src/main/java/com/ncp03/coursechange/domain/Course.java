package com.ncp03.coursechange.domain;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 과목 자체 (예: 프로그래밍언어론). 실제 수강·교환 단위는 {@link CourseOffering}. */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_id")
    private Long id;

    @Column(name = "course_code", nullable = false, length = 20)
    private String courseCode;

    @Column(name = "course_name", nullable = false, length = 100)
    private String courseName;

    @Column(nullable = false, length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_type", nullable = false, length = 20)
    private CourseType courseType;

    /** 대상학년. 화면의 '대상학년' 열과 학년 필터에 쓴다. */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    protected Course() {
    }

    public Long getId() { return id; }
    public String getCourseCode() { return courseCode; }
    public String getCourseName() { return courseName; }
    public String getDepartment() { return department; }
    public CourseType getCourseType() { return courseType; }
        public Integer getTargetYear() { return targetYear; }
}

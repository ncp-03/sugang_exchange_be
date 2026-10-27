package com.ncp03.coursechange.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_id")
    private Long id;

    /** 가상 학번. API 응답에 넣지 않는다. */
    @Column(name = "student_no", nullable = false, length = 20)
    private String studentNo;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false, length = 50)
    private String department;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "year_level", nullable = false)
    private Integer yearLevel;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "max_credits", nullable = false)
    private Integer maxCredits;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    /** SHA2(학번:비밀번호). 로그인 검증에만 쓰고 API 응답에 넣지 않는다. */
    @Column(name = "password_hash", length = 64, insertable = false, updatable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Student() {
    }

    /** 패널티로 매칭 이용을 제한한다. */
    public void blockUntil(LocalDateTime until) { this.blockedUntil = until; }

    public Long getId() { return id; }
    public String getStudentNo() { return studentNo; }
    public String getNickname() { return nickname; }
    public String getDepartment() { return department; }
    public Integer getYearLevel() { return yearLevel; }
    public Integer getMaxCredits() { return maxCredits; }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getBlockedUntil() { return blockedUntil; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

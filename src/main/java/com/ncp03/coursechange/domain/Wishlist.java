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

import java.time.LocalDateTime;

/** 찜(하트·장바구니). 매칭 신청 전 관심 목록이라 학점 상한으로 막지 않는다. */
@Entity
@Table(name = "wishlists")
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "wishlist_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private CourseOffering offering;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 희망 순위. 1이 가장 받고 싶은 분반. 교환 요청을 만들 때 이 순서가 우선순위가 된다. */
    @Column(name = "preference_rank", nullable = false)
    private int preferenceRank;

    protected Wishlist() {
    }

    private Wishlist(Student student, CourseOffering offering, int preferenceRank) {
        this.student = student;
        this.offering = offering;
        this.preferenceRank = preferenceRank;
    }

    /** 찜 1건을 만든다. created_at은 DB 기본값이 채운다. */
    public static Wishlist of(Student student, CourseOffering offering, int preferenceRank) {
        return new Wishlist(student, offering, preferenceRank);
    }

    /** 희망 순위를 바꾼다 (1부터). */
    public void changeRank(int preferenceRank) {
        this.preferenceRank = preferenceRank;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public CourseOffering getOffering() { return offering; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getPreferenceRank() { return preferenceRank; }
}

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

    protected Wishlist() {
    }

    private Wishlist(Student student, CourseOffering offering) {
        this.student = student;
        this.offering = offering;
    }

    /** 찜 1건을 만든다. created_at은 DB 기본값이 채운다. */
    public static Wishlist of(Student student, CourseOffering offering) {
        return new Wishlist(student, offering);
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public CourseOffering getOffering() { return offering; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

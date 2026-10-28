package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    /** 내 찜 목록 (분반·과목 함께 로딩). 희망 순위 순, 순위가 같으면 최근에 찜한 것이 먼저. */
    @Query("""
            select w from Wishlist w
            join fetch w.offering o
            join fetch o.course c
            where w.student.id = :studentId
            order by w.preferenceRank asc, w.createdAt desc, w.id desc
            """)
    List<Wishlist> findAllByStudentIdWithOffering(@Param("studentId") Long studentId);

    Optional<Wishlist> findByStudentIdAndOfferingId(Long studentId, Long offeringId);

    boolean existsByStudentIdAndOfferingId(Long studentId, Long offeringId);

    /** 지금 쓰고 있는 가장 큰 희망 순위. 새 찜은 그 다음 순위를 받는다. */
    @Query("select coalesce(max(w.preferenceRank), 0) from Wishlist w where w.student.id = :studentId")
    int findMaxRankByStudentId(@Param("studentId") Long studentId);
}

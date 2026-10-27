package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ExchangePreferenceRepository extends JpaRepository<ExchangePreference, Long> {

    @Query("""
            select p from ExchangePreference p
            join fetch p.targetOffering o
            join fetch o.course
            where p.request.id = :requestId
            order by p.preferenceRank asc
            """)
    List<ExchangePreference> findByRequestIdOrderByRank(@Param("requestId") Long requestId);

    @Query("""
            select p from ExchangePreference p
            join fetch p.targetOffering o
            join fetch o.course
            where p.request.id in :requestIds
            order by p.request.id asc, p.preferenceRank asc
            """)
    List<ExchangePreference> findByRequestIds(@Param("requestIds") Collection<Long> requestIds);

    /** 희망 분반 ID만. 빠른 매칭에서 IN 조건으로 쓴다. */
    @Query("select p.targetOffering.id from ExchangePreference p where p.request.id = :requestId order by p.preferenceRank asc")
    List<Long> findTargetOfferingIdsByRequestId(@Param("requestId") Long requestId);

    /** 희망 목록 수정 시 기존 행을 지운다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ExchangePreference p where p.request.id = :requestId")
    int deleteByRequestId(@Param("requestId") Long requestId);
}

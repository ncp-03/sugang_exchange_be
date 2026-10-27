package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PenaltyEventRepository extends JpaRepository<PenaltyEvent, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("""
            select coalesce(sum(p.deductionPoints), 0) from PenaltyEvent p
            where p.student.id = :studentId and p.createdAt >= :since
            """)
    long sumPointsSince(@Param("studentId") Long studentId, @Param("since") LocalDateTime since);

    /** 여러 학생의 최근 감점 합. 결과 행: [studentId(Long), points(Long)] */
    @Query("""
            select p.student.id, sum(p.deductionPoints) from PenaltyEvent p
            where p.student.id in :studentIds and p.createdAt >= :since
            group by p.student.id
            """)
    List<Object[]> sumPointsSinceByStudentIds(@Param("studentIds") Collection<Long> studentIds,
                                              @Param("since") LocalDateTime since);
}

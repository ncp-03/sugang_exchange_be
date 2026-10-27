package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchSessionRepository extends JpaRepository<MatchSession, Long> {

    /** 만료 처리 대상: PENDING이면서 제한 시간이 지난 세션. */
    @Query("""
            select s from MatchSession s
            where s.status = com.ncp03.coursechange.domain.SessionStatus.PENDING
              and s.expiresAt < :now
            order by s.expiresAt asc
            """)
    List<MatchSession> findExpiredPending(@Param("now") LocalDateTime now);
}

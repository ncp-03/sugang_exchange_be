package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {

    /** 세션의 참여자 2명 (MVP는 세션당 2명). 학생 ID 순으로 안정적인 순서를 보장한다. */
    @Query("""
            select p from MatchParticipant p
            join fetch p.student
            join fetch p.request r
            join fetch p.offeredEnrollment
            join fetch p.giveOffering g join fetch g.course
            join fetch p.receiveOffering rc join fetch rc.course
            where p.session.id = :sessionId
            order by p.student.id asc
            """)
    List<MatchParticipant> findBySessionIdWithAll(@Param("sessionId") Long sessionId);

    /** 내가 참여 중인 PENDING 세션 (보통 0개 또는 1개). */
    @Query("""
            select p from MatchParticipant p
            join fetch p.session s
            where p.student.id = :studentId
              and s.status = com.ncp03.coursechange.domain.SessionStatus.PENDING
            order by s.openedAt desc
            """)
    List<MatchParticipant> findPendingByStudentId(@Param("studentId") Long studentId);

    Optional<MatchParticipant> findBySessionIdAndStudentId(Long sessionId, Long studentId);

    /** 내가 끝낸 교환 (완료된 세션). 최근 것이 먼저. 화면의 '교환 기록'에 쓴다. */
    @Query("""
            select p from MatchParticipant p
            join fetch p.session s
            join fetch p.giveOffering g join fetch g.course
            join fetch p.receiveOffering rc join fetch rc.course
            where p.student.id = :studentId
              and s.status = com.ncp03.coursechange.domain.SessionStatus.COMPLETED
            order by s.finishedAt desc, s.id desc
            """)
    List<MatchParticipant> findCompletedByStudentId(@Param("studentId") Long studentId);
}

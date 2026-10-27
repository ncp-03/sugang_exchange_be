package com.ncp03.coursechange.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {

    /** 내 교환 요청 전체 (진행 중 먼저, 그다음 최신순). */
    @Query("""
            select r from ExchangeRequest r
            join fetch r.offeredEnrollment e
            join fetch e.offering o
            join fetch o.course
            where r.student.id = :studentId
            order by r.id desc
            """)
    List<ExchangeRequest> findAllByStudentIdWithOffering(@Param("studentId") Long studentId);

    @Query("""
            select r from ExchangeRequest r
            join fetch r.student
            join fetch r.offeredEnrollment e
            join fetch e.offering o
            join fetch o.course
            where r.id = :id
            """)
    Optional<ExchangeRequest> findDetailById(@Param("id") Long id);

    /** 이 수강 내역으로 진행 중인 요청이 이미 있는지. DB UNIQUE가 최종 방어선이고 이건 친절한 400용. */
    @Query("""
            select count(r) > 0 from ExchangeRequest r
            where r.offeredEnrollment.id = :enrollmentId and r.status in :activeStatuses
            """)
    boolean existsActiveByEnrollmentId(@Param("enrollmentId") Long enrollmentId,
                                       @Param("activeStatuses") Collection<RequestStatus> activeStatuses);

    /**
     * 상호 교환 후보 찾기 (빠른 매칭의 핵심 쿼리).
     * 조건: 상대가 내놓은 분반이 내 희망 목록에 있고(= 내가 원하는 것), 내가 내놓는 분반이 상대 희망 목록에 있다(= 상대도 원한다).
     * 같은 과목 다른 분반이든 다른 과목이든 상관없고, 분반 ID가 다르기만 하면 된다.
     * 대기 시간이 긴 요청(queued_at 오름차순)이 먼저 나온다.
     */
    @Query("""
            select r2 from ExchangeRequest r2
            join fetch r2.student
            join fetch r2.offeredEnrollment e2
            join fetch e2.offering o2
            join fetch o2.course
            where r2.status = com.ncp03.coursechange.domain.RequestStatus.WAITING
              and r2.student.id <> :studentId
              and e2.offering.id in :wantedOfferingIds
              and exists (
                    select 1 from ExchangePreference p2
                    where p2.request = r2 and p2.targetOffering.id = :myGiveOfferingId)
            order by r2.queuedAt asc, r2.id asc
            """)
    List<ExchangeRequest> findMutualCandidates(@Param("studentId") Long studentId,
                                               @Param("wantedOfferingIds") Collection<Long> wantedOfferingIds,
                                               @Param("myGiveOfferingId") Long myGiveOfferingId);

    /** 같은 분반을 (희망 목록 안에) 원하는 대기 중 요청들. 대기열 순위 계산용. */
    @Query("""
            select r from ExchangeRequest r
            join fetch r.student
            where r.status = com.ncp03.coursechange.domain.RequestStatus.WAITING
              and exists (
                    select 1 from ExchangePreference p
                    where p.request = r and p.targetOffering.id = :offeringId)
            """)
    List<ExchangeRequest> findWaitingWantingOffering(@Param("offeringId") Long offeringId);

    /**
     * 비관적 락(SELECT ... FOR UPDATE)으로 요청 행을 잠근다.
     * 교착 상태를 피하려고 항상 request_id 오름차순으로 잠근다 — 호출 측에서 ids를 정렬해 넘긴다.
     * app.matching.lock-mode=PESSIMISTIC일 때만 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExchangeRequest r where r.id in :ids order by r.id asc")
    List<ExchangeRequest> lockAllByIdOrdered(@Param("ids") List<Long> ids);
}

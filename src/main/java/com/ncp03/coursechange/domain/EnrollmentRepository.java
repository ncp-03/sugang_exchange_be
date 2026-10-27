package com.ncp03.coursechange.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 수강 내역 1건을 분반·과목까지 함께 가져온다. */
    @Query("""
            select e from Enrollment e
            join fetch e.student
            join fetch e.offering o
            join fetch o.course
            where e.id = :id
            """)
    Optional<Enrollment> findDetailById(@Param("id") Long id);

    /** 같은 과목의 어떤 분반이든 이미 수강 중인지. UNIQUE(student_id, course_id) 위반을 미리 막는다. */
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    /**
     * 바로 그 분반을 수강 중인지.
     * 찜은 "같은 과목 다른 분반"이 핵심 용도이므로 과목이 아니라 분반 단위로 봐야 한다.
     */
    boolean existsByStudentIdAndOfferingId(Long studentId, Long offeringId);

    /**
     * 교환 실행 직전에 두 좌석 행을 비관적 락으로 잠근다.
     * 항상 enrollment_id 오름차순으로 잠가 교착 상태를 피한다 — 호출 측에서 정렬해 넘긴다.
     * app.matching.lock-mode=PESSIMISTIC일 때만 호출한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Enrollment e where e.id in :ids order by e.id asc")
    List<Enrollment> lockAllByIdOrdered(@Param("ids") List<Long> ids);

    /** 학생의 현재 수강 내역 (분반·과목 함께 로딩). */
    @Query("""
            select e from Enrollment e
            join fetch e.offering o
            join fetch o.course c
            where e.student.id = :studentId
            order by c.courseCode asc, o.sectionNo asc
            """)
    List<Enrollment> findAllByStudentIdWithOffering(@Param("studentId") Long studentId);

    /** 분반별 현재 수강 인원. 결과 행: [offeringId(Long), count(Long)] */
    @Query("""
            select e.offering.id, count(e) from Enrollment e
            where e.offering.id in :offeringIds
            group by e.offering.id
            """)
    List<Object[]> countByOfferingIds(@Param("offeringIds") Collection<Long> offeringIds);

    /** 여러 학생의 현재 학점 합. 결과 행: [studentId(Long), credits(Long)] */
    @Query("""
            select e.student.id, sum(o.credits) from Enrollment e join e.offering o
            where e.student.id in :studentIds
            group by e.student.id
            """)
    List<Object[]> sumCreditsByStudentIds(@Param("studentIds") Collection<Long> studentIds);

    @Query("""
            select coalesce(sum(o.credits), 0) from Enrollment e join e.offering o
            where e.student.id = :studentId
            """)
    long sumCreditsByStudentId(@Param("studentId") Long studentId);
}

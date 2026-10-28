package com.ncp03.coursechange.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, Long> {

    /**
     * 강의 검색. 모든 조건은 선택(null이면 무시).
     * keyword는 과목명·학수번호·교수명 부분 일치(대소문자 무시). 호출 측에서 소문자 + %로 감싸서 넘긴다.
     */
    @Query(value = """
            select o from CourseOffering o join fetch o.course c
            where (:keywordPattern is null
                   or lower(c.courseName) like :keywordPattern
                   or lower(c.courseCode) like :keywordPattern
                   or lower(o.professorName) like :keywordPattern)
              and (:department is null or c.department = :department)
              and (:courseType is null or c.courseType = :courseType)
              and (:professorName is null or o.professorName = :professorName)
              and (:targetYear is null or c.targetYear = :targetYear)
              and (:weekday is null or exists (
                     select 1 from ClassTime t where t.offering = o and t.weekday = :weekday))
            order by c.courseCode asc, o.sectionNo asc
            """,
            countQuery = """
            select count(o) from CourseOffering o join o.course c
            where (:keywordPattern is null
                   or lower(c.courseName) like :keywordPattern
                   or lower(c.courseCode) like :keywordPattern
                   or lower(o.professorName) like :keywordPattern)
              and (:department is null or c.department = :department)
              and (:courseType is null or c.courseType = :courseType)
              and (:professorName is null or o.professorName = :professorName)
              and (:targetYear is null or c.targetYear = :targetYear)
              and (:weekday is null or exists (
                     select 1 from ClassTime t where t.offering = o and t.weekday = :weekday))
            """)
    Page<CourseOffering> search(@Param("keywordPattern") String keywordPattern,
                                @Param("department") String department,
                                @Param("courseType") CourseType courseType,
                                @Param("professorName") String professorName,
                                @Param("targetYear") Integer targetYear,
                                @Param("weekday") Integer weekday,
                                Pageable pageable);

    @Query("select o from CourseOffering o join fetch o.course where o.id = :id")
    Optional<CourseOffering> findWithCourseById(@Param("id") Long id);

    @Query("select distinct c.department from Course c order by c.department")
    List<String> findAllDepartments();

    /** 교수 필터 선택지. 분반에 들어 있는 교수명을 가나다순으로 모은다. */
    @Query("select distinct o.professorName from CourseOffering o order by o.professorName")
    List<String> findAllProfessors();

    /** 학년 필터 선택지. 실제로 개설된 과목의 대상학년만 보여 준다. */
    @Query("select distinct c.targetYear from Course c order by c.targetYear")
    List<Integer> findAllTargetYears();
}

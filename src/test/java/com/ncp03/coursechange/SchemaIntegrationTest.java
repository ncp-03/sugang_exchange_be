package com.ncp03.coursechange;

import com.ncp03.coursechange.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flyway 스키마·샘플 데이터·DB 제약 조건 검증. 각 테스트는 끝나면 롤백된다. */
@Transactional
class SchemaIntegrationTest extends IntegrationTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    private Integer count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("Flyway 마이그레이션 V1, V2, V3가 성공하고 테이블 11개가 생긴다")
    void migrationsApplied() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success = 1 order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3");

        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = database() and table_name <> 'flyway_schema_history'
                order by table_name""", String.class);
        assertThat(tables).containsExactlyInAnyOrder(
                "students", "courses", "course_offerings", "class_times", "enrollments",
                "wishlists", "exchange_requests", "exchange_preferences",
                "match_sessions", "match_participants", "penalty_events");
    }

    @Test
    @DisplayName("샘플 데이터 건수")
    void seedCounts() {
        assertThat(count("select count(*) from students")).isEqualTo(10);
        assertThat(count("select count(*) from courses")).isEqualTo(13);
        assertThat(count("select count(*) from course_offerings")).isEqualTo(21);
        assertThat(count("select count(*) from class_times")).isEqualTo(40);
        assertThat(count("select count(*) from enrollments")).isEqualTo(45);
    }

    @Test
    @DisplayName("샘플 데이터: 시간표가 겹치는 학생이 없다")
    void seedHasNoTimetableConflict() {
        Integer conflicts = count("""
                select count(*) from enrollments e1
                join enrollments e2 on e1.student_id = e2.student_id and e1.enrollment_id < e2.enrollment_id
                join class_times t1 on t1.offering_id = e1.offering_id
                join class_times t2 on t2.offering_id = e2.offering_id
                where t1.weekday = t2.weekday and t1.start_time < t2.end_time and t2.start_time < t1.end_time""");
        assertThat(conflicts).isZero();
    }

    @Test
    @DisplayName("샘플 데이터: 학점 상한·정원을 넘는 곳이 없다")
    void seedRespectsCreditsAndCapacity() {
        assertThat(count("""
                select count(*) from (
                  select s.student_id from students s
                  join enrollments e on e.student_id = s.student_id
                  join course_offerings o on o.offering_id = e.offering_id
                  group by s.student_id, s.max_credits having sum(o.credits) > s.max_credits) x""")).isZero();
        assertThat(count("""
                select count(*) from (
                  select o.offering_id from course_offerings o
                  join enrollments e on e.offering_id = o.offering_id
                  group by o.offering_id, o.capacity having count(*) > o.capacity) x""")).isZero();
    }

    @Test
    @DisplayName("수강 내역의 course_id가 분반의 과목과 다르면 거부된다 (복합 FK)")
    void enrollmentCourseMustMatchOffering() {
        // 학생 3은 offering 9(과목 5)를 안 듣는다. course_id를 일부러 1로 넣는다.
        assertThatThrownBy(() -> jdbc.update(
                "insert into enrollments (student_id, offering_id, course_id) values (3, 9, 1)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("같은 과목의 두 분반을 동시에 수강할 수 없다")
    void sameCourseTwiceRejected() {
        // 학생 1은 프로그래밍언어론 02(offering 10)를 듣는다. 01(offering 9)을 추가하면 거부.
        assertThatThrownBy(() -> jdbc.update(
                "insert into enrollments (student_id, offering_id, course_id) values (1, 9, 5)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("수업 시간 CHECK: 요일 1~7, 시작 < 종료")
    void classTimeChecks() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into class_times (offering_id, weekday, start_time, end_time) values (1, 8, '09:00', '10:00')"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into class_times (offering_id, weekday, start_time, end_time) values (1, 5, '11:00', '10:00')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("같은 수강 내역은 진행 중 교환 요청 1개에만 묶이고, 끝난 요청 이력은 여러 개 남길 수 있다")
    void activeRequestPerEnrollmentIsUnique() {
        Long enrollmentId = jdbc.queryForObject(
                "select enrollment_id from enrollments where student_id = 1 and offering_id = 10", Long.class);

        jdbc.update("insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'WAITING')",
                enrollmentId);
        assertThatThrownBy(() -> jdbc.update(
                "insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'WAITING')",
                enrollmentId)).isInstanceOf(DataAccessException.class);

        jdbc.update("update exchange_requests set status = 'CANCELLED' where offered_enrollment_id = ?", enrollmentId);
        jdbc.update("insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'WAITING')",
                enrollmentId);
        jdbc.update("update exchange_requests set status = 'COMPLETED' where offered_enrollment_id = ? and status = 'WAITING'",
                enrollmentId);

        assertThat(count("select count(*) from exchange_requests where offered_enrollment_id = ?", enrollmentId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("상태값 CHECK: 정의되지 않은 상태는 거부된다")
    void statusCheck() {
        Long enrollmentId = jdbc.queryForObject(
                "select enrollment_id from enrollments where student_id = 1 and offering_id = 10", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'DONE')",
                enrollmentId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("깨우기 횟수는 3회를 넘을 수 없고, 같은 사건은 두 번 감점되지 않는다")
    void wakeLimitAndPenaltyIdempotency() {
        Long e1 = jdbc.queryForObject("select enrollment_id from enrollments where student_id = 1 and offering_id = 10", Long.class);
        jdbc.update("insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'MATCHED')", e1);
        Long requestId = jdbc.queryForObject("select max(request_id) from exchange_requests", Long.class);
        jdbc.update("insert into match_sessions (status, expires_at) values ('PENDING', now(6) + interval 5 minute)");
        Long sessionId = jdbc.queryForObject("select max(session_id) from match_sessions", Long.class);
        jdbc.update("""
                insert into match_participants
                  (session_id, request_id, student_id, offered_enrollment_id, give_offering_id, receive_offering_id, wake_sent_count)
                values (?, ?, 1, ?, 10, 9, 3)""", sessionId, requestId, e1);

        assertThatThrownBy(() -> jdbc.update(
                "update match_participants set wake_sent_count = 4 where session_id = ?", sessionId))
                .isInstanceOf(DataAccessException.class);

        jdbc.update("""
                insert into penalty_events (student_id, session_id, reason_code, deduction_points, idempotency_key)
                values (1, ?, 'NO_RESPONSE', 5, ?)""", sessionId, "NO_RESPONSE:" + sessionId + ":1");
        assertThatThrownBy(() -> jdbc.update("""
                insert into penalty_events (student_id, session_id, reason_code, deduction_points, idempotency_key)
                values (1, ?, 'NO_RESPONSE', 5, ?)""", sessionId, "NO_RESPONSE:" + sessionId + ":1"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("교환 방식 확인: 두 좌석의 분반을 맞바꿔도 제약 위반이 없다 (같은 과목 다른 분반)")
    void swapOfferingsWorks() {
        // 학생1: 프언론 02(offering 10), 학생2: 프언론 01(offering 9)
        Long e1 = jdbc.queryForObject("select enrollment_id from enrollments where student_id = 1 and offering_id = 10", Long.class);
        Long e2 = jdbc.queryForObject("select enrollment_id from enrollments where student_id = 2 and offering_id = 9", Long.class);
        jdbc.update("update enrollments set offering_id = 9,  course_id = 5, version = version + 1 where enrollment_id = ?", e1);
        jdbc.update("update enrollments set offering_id = 10, course_id = 5, version = version + 1 where enrollment_id = ?", e2);

        assertThat(count("select count(*) from enrollments where student_id = 1 and offering_id = 9")).isEqualTo(1);
        assertThat(count("select count(*) from enrollments where student_id = 2 and offering_id = 10")).isEqualTo(1);
        assertThat(count("select count(*) from enrollments where offering_id = 9")).isEqualTo(2);
    }
}

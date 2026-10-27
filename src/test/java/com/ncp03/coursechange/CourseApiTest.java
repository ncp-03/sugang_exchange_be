package com.ncp03.coursechange;

import com.ncp03.coursechange.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class CourseApiTest extends IntegrationTestSupport {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("GET /api/courses: 조건 없이 첫 페이지 20개, 학수번호·분반 순 정렬")
    void listDefault() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.content[0].courseCode").value("AIE3010"))
                .andExpect(jsonPath("$.content[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.content[1].sectionNo").value("02"));
    }

    @Test
    @DisplayName("두 번째 페이지에는 남은 1개")
    void secondPage() throws Exception {
        mvc.perform(get("/api/courses").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("과목명 검색: 응답 필드와 정원 마감, 수업 시간 표시")
    void searchByName() throws Exception {
        mvc.perform(get("/api/courses").param("keyword", "프로그래밍언어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].offeringId").value(9))
                .andExpect(jsonPath("$.content[0].courseId").value(5))
                .andExpect(jsonPath("$.content[0].courseCode").value("CSE3050"))
                .andExpect(jsonPath("$.content[0].courseName").value("프로그래밍언어론"))
                .andExpect(jsonPath("$.content[0].sectionNo").value("01"))
                .andExpect(jsonPath("$.content[0].professorName").value("서교수"))
                .andExpect(jsonPath("$.content[0].department").value("컴퓨터공학과"))
                .andExpect(jsonPath("$.content[0].courseType").value("MAJOR_ELECTIVE"))
                .andExpect(jsonPath("$.content[0].courseTypeLabel").value("전공선택"))
                .andExpect(jsonPath("$.content[0].credits").value(3))
                .andExpect(jsonPath("$.content[0].capacity").value(2))
                .andExpect(jsonPath("$.content[0].enrolledCount").value(2))
                .andExpect(jsonPath("$.content[0].full").value(true))
                .andExpect(jsonPath("$.content[0].classroom").value("공학관 501"))
                .andExpect(jsonPath("$.content[0].schedules", hasSize(2)))
                .andExpect(jsonPath("$.content[0].schedules[0].weekday").value(2))
                .andExpect(jsonPath("$.content[0].schedules[0].weekdayLabel").value("화"))
                .andExpect(jsonPath("$.content[0].schedules[0].startTime").value("15:00"))
                .andExpect(jsonPath("$.content[0].schedules[0].endTime").value("16:15"))
                .andExpect(jsonPath("$.content[0].scheduleText").value("화목 15:00-16:15"))
                .andExpect(jsonPath("$.content[0].exchange.wantCount").value(0))
                .andExpect(jsonPath("$.content[0].exchange.offerCount").value(0))
                .andExpect(jsonPath("$.content[1].sectionNo").value("02"))
                .andExpect(jsonPath("$.content[1].full").value(false))
                .andExpect(jsonPath("$.content[1].scheduleText").value("월수 13:30-14:45"));
    }

    @Test
    @DisplayName("교수명·학수번호(대소문자 무시) 검색")
    void searchByProfessorAndCode() throws Exception {
        mvc.perform(get("/api/courses").param("keyword", "서교수"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/courses").param("keyword", "cse305"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/courses").param("keyword", "  "))
                .andExpect(jsonPath("$.totalElements").value(21));
    }

    @Test
    @DisplayName("검색어의 % _ 는 와일드카드가 아니라 글자로 취급")
    void likeWildcardsEscaped() throws Exception {
        mvc.perform(get("/api/courses").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/courses").param("keyword", "_"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("학과·이수구분·요일 필터와 조합")
    void filters() throws Exception {
        mvc.perform(get("/api/courses").param("department", "교양학부"))
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[*].department", everyItem(is("교양학부"))));
        mvc.perform(get("/api/courses").param("courseType", "MAJOR_REQUIRED"))
                .andExpect(jsonPath("$.totalElements").value(6));
        mvc.perform(get("/api/courses").param("weekday", "5"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].courseName", contains("체육과 건강", "글쓰기와 소통")));
        mvc.perform(get("/api/courses").param("department", "컴퓨터공학과").param("weekday", "5"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/courses").param("keyword", "자료").param("weekday", "2"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sectionNo").value("02"));
    }

    @Test
    @DisplayName("잘못된 파라미터는 400 INVALID_PARAMETER")
    void invalidParameters() throws Exception {
        for (String[] p : new String[][]{
                {"courseType", "FOO"}, {"weekday", "6"}, {"weekday", "7"}, {"weekday", "0"}, {"weekday", "abc"},
                {"size", "0"}, {"size", "101"}, {"page", "-1"}}) {
            mvc.perform(get("/api/courses").param(p[0], p[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
    }

    @Test
    @DisplayName("GET /api/courses/{id}: 상세, 없으면 404, 숫자가 아니면 400")
    void detail() throws Exception {
        mvc.perform(get("/api/courses/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offeringId").value(10))
                .andExpect(jsonPath("$.courseName").value("프로그래밍언어론"))
                .andExpect(jsonPath("$.enrolledCount").value(2))
                .andExpect(jsonPath("$.scheduleText").value("월수 13:30-14:45"));
        mvc.perform(get("/api/courses/17"))
                .andExpect(jsonPath("$.credits").value(2))
                .andExpect(jsonPath("$.scheduleText").value("금 10:30-12:15"));
        mvc.perform(get("/api/courses/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        mvc.perform(get("/api/courses/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("교환 현황: 진행 중 요청만 희망자·보유자 수에 포함 (학생 중복 제거)")
    void exchangeStats() throws Exception {
        // 학생1이 프언론 02(offering 10)를 내놓고 01(offering 9)을 희망
        Long e1 = jdbc.queryForObject("select enrollment_id from enrollments where student_id = 1 and offering_id = 10", Long.class);
        jdbc.update("insert into exchange_requests (student_id, offered_enrollment_id, status) values (1, ?, 'WAITING')", e1);
        Long r1 = jdbc.queryForObject("select max(request_id) from exchange_requests", Long.class);
        jdbc.update("insert into exchange_preferences (request_id, target_offering_id, preference_rank) values (?, 9, 1)", r1);

        // 학생10의 프언론 02 요청은 취소됨 → 집계 제외
        Long e10 = jdbc.queryForObject("select enrollment_id from enrollments where student_id = 10 and offering_id = 10", Long.class);
        jdbc.update("insert into exchange_requests (student_id, offered_enrollment_id, status) values (10, ?, 'CANCELLED')", e10);
        Long r10 = jdbc.queryForObject("select max(request_id) from exchange_requests", Long.class);
        jdbc.update("insert into exchange_preferences (request_id, target_offering_id, preference_rank) values (?, 9, 1)", r10);

        mvc.perform(get("/api/courses/9"))
                .andExpect(jsonPath("$.exchange.wantCount").value(1))
                .andExpect(jsonPath("$.exchange.offerCount").value(0));
        mvc.perform(get("/api/courses/10"))
                .andExpect(jsonPath("$.exchange.wantCount").value(0))
                .andExpect(jsonPath("$.exchange.offerCount").value(1));
    }

    @Test
    @DisplayName("GET /api/courses/filters: 드롭다운 선택지")
    void filterOptions() throws Exception {
        mvc.perform(get("/api/courses/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments", contains("교양학부", "소프트웨어융합학과", "인공지능학과", "컴퓨터공학과")))
                .andExpect(jsonPath("$.courseTypes", hasSize(4)))
                .andExpect(jsonPath("$.courseTypes[0].value").value("MAJOR_BASIC"))
                .andExpect(jsonPath("$.courseTypes[0].label").value("전공기초"))
                .andExpect(jsonPath("$.weekdays", hasSize(5)))
                .andExpect(jsonPath("$.weekdays[0].value").value("1"))
                .andExpect(jsonPath("$.weekdays[0].label").value("월"));
    }

    @Test
    @DisplayName("없는 주소는 JSON 404")
    void unknownPath() throws Exception {
        mvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}

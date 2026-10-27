package com.ncp03.coursechange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncp03.coursechange.support.ResettableIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 교환 요청 등록·수정·취소 API.
 *
 * <p>enrollmentId는 AUTO_INCREMENT라 값을 하드코딩하지 않고 {@code /api/enrollments/me}에서 찾아 쓴다.
 *
 * <p>샘플 데이터에서 알아 둘 것
 * <ul>
 *   <li>학생 1: 분반 3·5·7·10·18 수강 (15/19학점). 분반 10 = 프로그래밍언어론 02분반</li>
 *   <li>학생 2: 분반 1·4·6·9 수강 (12/19학점). 분반 9 = 프로그래밍언어론 01분반</li>
 *   <li>분반 3 = 월수 10:30, 분반 16 = 월수 10:30 (시간표 겹침 시험용)</li>
 * </ul>
 */
class ExchangeRequestApiTest extends ResettableIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private static final String STUDENT = "X-Student-Id";

    /** 해당 학생이 그 분반을 듣고 있는 수강 내역 ID를 찾는다. */
    private long enrollmentIdOf(long studentId, long offeringId) throws Exception {
        MvcResult result = mvc.perform(get("/api/enrollments/me").header(STUDENT, String.valueOf(studentId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = om.readTree(result.getResponse().getContentAsString());
        for (JsonNode e : root.path("enrollments")) {
            if (e.path("course").path("offeringId").asLong() == offeringId) {
                return e.path("enrollmentId").asLong();
            }
        }
        throw new AssertionError("학생 " + studentId + "은 분반 " + offeringId + "을 듣고 있지 않습니다.");
    }

    private String body(long enrollmentId, String wantedCsv, boolean autoMatch) {
        return "{\"offeredEnrollmentId\":" + enrollmentId
                + ",\"wantedOfferingIds\":[" + wantedCsv + "]"
                + ",\"autoMatch\":" + autoMatch + "}";
    }

    @Test
    @DisplayName("등록 성공: WAITING 상태로 대기열에 들어가고 희망 분반이 우선순위 순으로 저장된다")
    void createSuccess() throws Exception {
        long enr = enrollmentIdOf(1, 10);

        MvcResult result = mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(enr, "9", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.queuedAt").exists())
                .andExpect(jsonPath("$.offeredEnrollmentId").value(enr))
                .andExpect(jsonPath("$.offeredCourse.offeringId").value(10))
                .andExpect(jsonPath("$.preferences[0].rank").value(1))
                .andExpect(jsonPath("$.preferences[0].course.offeringId").value(9))
                .andExpect(jsonPath("$.matchSessionId").doesNotExist())
                .andReturn();

        long requestId = om.readTree(result.getResponse().getContentAsString()).path("requestId").asLong();

        mvc.perform(get("/api/exchange-requests/me").header(STUDENT, "1"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.requests[0].requestId").value(requestId));

        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("소유권 검증: 다른 학생의 enrollmentId를 보내면 403 NOT_MY_ENROLLMENT")
    void cannotOfferSomeoneElsesEnrollment() throws Exception {
        long othersEnrollment = enrollmentIdOf(2, 9); // 학생 2의 수강 내역

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1") // 학생 1이 보낸다
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(othersEnrollment, "10", false)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MY_ENROLLMENT"));
    }

    @Test
    @DisplayName("같은 수강 내역으로 두 번 등록하면 409 REQUEST_ALREADY_EXISTS")
    void duplicateActiveRequest() throws Exception {
        long enr = enrollmentIdOf(4, 11);

        MvcResult first = mvc.perform(post("/api/exchange-requests").header(STUDENT, "4")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "1", false)))
                .andExpect(status().isCreated()).andReturn();
        long requestId = om.readTree(first.getResponse().getContentAsString()).path("requestId").asLong();

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "4")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "2", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_ALREADY_EXISTS"));

        // 취소하면 다시 등록할 수 있다 (생성 컬럼이 NULL이 되어 UNIQUE가 풀린다)
        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "4"))
                .andExpect(status().isNoContent());
        MvcResult again = mvc.perform(post("/api/exchange-requests").header(STUDENT, "4")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "2", false)))
                .andExpect(status().isCreated()).andReturn();
        long secondId = om.readTree(again.getResponse().getContentAsString()).path("requestId").asLong();
        mvc.perform(delete("/api/exchange-requests/" + secondId).header(STUDENT, "4"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("시간표가 겹치는 분반을 희망하면 409 SCHEDULE_CONFLICT")
    void scheduleConflictRejected() throws Exception {
        // 학생 1은 분반 10(월수 13:30)을 내놓고 분반 16(월수 10:30)을 받으려 한다.
        // 그런데 그대로 두는 분반 3도 월수 10:30이라 겹친다.
        long enr = enrollmentIdOf(1, 10);

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "16", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    @Test
    @DisplayName("같은 과목의 다른 분반을 이미 듣고 있으면 409 SAME_COURSE_ALREADY_HELD")
    void sameCourseAlreadyHeldRejected() throws Exception {
        // 학생 1은 분반 3(운영체제 01)을 내놓고 분반 9(프언 01)를 받으려 한다.
        // 그런데 프언 02분반(10)을 이미 듣고 있으므로 UNIQUE(student_id, course_id)에 걸린다.
        long enr = enrollmentIdOf(1, 3);

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "9", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_COURSE_ALREADY_HELD"));
    }

    @Test
    @DisplayName("내놓는 분반을 희망 분반에 넣으면 400 PREFERENCE_SAME_AS_OFFERED")
    void cannotWantWhatYouOffer() throws Exception {
        long enr = enrollmentIdOf(1, 10);

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "10", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREFERENCE_SAME_AS_OFFERED"));
    }

    @Test
    @DisplayName("입력 검증: 희망 분반 빈 배열 400, 중복 400, 없는 분반 404, 없는 수강 내역 404")
    void inputValidation() throws Exception {
        long enr = enrollmentIdOf(1, 10);

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "9,9", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PREFERENCE"));

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "99999", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));

        mvc.perform(post("/api/exchange-requests").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content(body(99999L, "9", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("희망 분반 수정(PATCH)과 조회 권한: 남의 요청은 403 NOT_MY_REQUEST")
    void updateAndOwnership() throws Exception {
        // 학생 9는 분반 12(월수 13:30)를 내놓고, 시간표가 안 겹치는 분반 10(월수 13:30)·6(월수 15:00)을 원한다.
        // (분반 11은 학생 9가 듣는 분반 7과 월수 12:00으로 겹쳐서 SCHEDULE_CONFLICT가 난다.)
        long enr = enrollmentIdOf(9, 12);
        MvcResult created = mvc.perform(post("/api/exchange-requests").header(STUDENT, "9")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "10", false)))
                .andExpect(status().isCreated()).andReturn();
        long requestId = om.readTree(created.getResponse().getContentAsString()).path("requestId").asLong();

        mvc.perform(patch("/api/exchange-requests/" + requestId).header(STUDENT, "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wantedOfferingIds\":[10,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences[0].course.offeringId").value(10))
                .andExpect(jsonPath("$.preferences[1].course.offeringId").value(6));

        mvc.perform(get("/api/exchange-requests/" + requestId).header(STUDENT, "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MY_REQUEST"));

        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "1"))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "9"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("취소한 요청은 다시 취소할 수 없다 (409 REQUEST_NOT_MODIFIABLE)")
    void cannotCancelTwice() throws Exception {
        long enr = enrollmentIdOf(7, 13);
        MvcResult created = mvc.perform(post("/api/exchange-requests").header(STUDENT, "7")
                        .contentType(MediaType.APPLICATION_JSON).content(body(enr, "14", false)))
                .andExpect(status().isCreated()).andReturn();
        long requestId = om.readTree(created.getResponse().getContentAsString()).path("requestId").asLong();

        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "7"))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/exchange-requests/" + requestId).header(STUDENT, "7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_MODIFIABLE"));

        assertThat(requestId).isPositive();
    }
}

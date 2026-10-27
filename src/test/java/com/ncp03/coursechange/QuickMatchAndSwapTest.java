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
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 빠른 매칭 → 양쪽 동의 → 실제 수강 교환까지의 전체 흐름.
 *
 * <p>시연용 교환 쌍을 쓴다.
 * <ul>
 *   <li>학생 1(졸린고양이): 프로그래밍언어론 <b>02분반(분반 10)</b> 보유 → 01분반을 원함</li>
 *   <li>학생 2(바쁜다람쥐): 프로그래밍언어론 <b>01분반(분반 9)</b> 보유 → 02분반을 원함</li>
 * </ul>
 * 교환 후 학생 1은 분반 9, 학생 2는 분반 10을 갖게 되고 학점 합계는 둘 다 그대로다.
 */
class QuickMatchAndSwapTest extends ResettableIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private static final String STUDENT = "X-Student-Id";

    private long enrollmentIdOf(long studentId, long offeringId) throws Exception {
        JsonNode root = om.readTree(mvc.perform(get("/api/enrollments/me")
                        .header(STUDENT, String.valueOf(studentId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode e : root.path("enrollments")) {
            if (e.path("course").path("offeringId").asLong() == offeringId) {
                return e.path("enrollmentId").asLong();
            }
        }
        throw new AssertionError("학생 " + studentId + "은 분반 " + offeringId + "을 듣고 있지 않습니다.");
    }

    private long register(long studentId, long offeringId, String wantedCsv, boolean autoMatch) throws Exception {
        long enr = enrollmentIdOf(studentId, offeringId);
        MvcResult r = mvc.perform(post("/api/exchange-requests").header(STUDENT, String.valueOf(studentId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredEnrollmentId\":" + enr
                                + ",\"wantedOfferingIds\":[" + wantedCsv + "]"
                                + ",\"autoMatch\":" + autoMatch + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).path("requestId").asLong();
    }

    @Test
    @DisplayName("상대가 없으면 매칭 실패가 아니라 200 + matched=false, reason=NO_COUNTERPART")
    void noCounterpart() throws Exception {
        long requestId = register(1, 10, "9", false);

        mvc.perform(post("/api/exchange-requests/" + requestId + "/quick-match").header(STUDENT, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.reason").value("NO_COUNTERPART"))
                .andExpect(jsonPath("$.session").doesNotExist());
    }

    @Test
    @DisplayName("상호 희망이 맞으면 빠른 매칭으로 세션이 생기고 양쪽 요청이 MATCHED가 된다")
    void quickMatchCreatesSession() throws Exception {
        long requestA = register(1, 10, "9", false);   // 학생1: 02분반 내놓고 01분반 희망
        long requestB = register(2, 9, "10", false);   // 학생2: 01분반 내놓고 02분반 희망

        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.candidatesChecked").value(1))
                .andExpect(jsonPath("$.session.status").value("PENDING"))
                .andExpect(jsonPath("$.session.remainingSeconds").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.session.participants", hasSize(2)))
                .andReturn();

        JsonNode session = om.readTree(matched.getResponse().getContentAsString()).path("session");
        // 참여자는 student_id 순이므로 [0]=학생1, [1]=학생2
        assertThat(session.path("participants").get(0).path("nickname").asText()).isEqualTo("졸린고양이");
        assertThat(session.path("participants").get(0).path("giveCourse").path("offeringId").asLong()).isEqualTo(10);
        assertThat(session.path("participants").get(0).path("receiveCourse").path("offeringId").asLong()).isEqualTo(9);
        assertThat(session.path("participants").get(1).path("me").asBoolean()).isTrue(); // 호출자가 학생2

        mvc.perform(get("/api/exchange-requests/" + requestA).header(STUDENT, "1"))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchSessionId").value(session.path("sessionId").asLong()));

        // 매칭 중에는 희망 분반을 바꿀 수 없다
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/exchange-requests/" + requestA).header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"wantedOfferingIds\":[11]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_MODIFIABLE"));
    }

    @Test
    @DisplayName("등록 시 autoMatch=true면 상대가 이미 대기 중일 때 바로 MATCHED로 등록된다")
    void autoMatchOnCreate() throws Exception {
        register(1, 10, "9", false);

        long enr = enrollmentIdOf(2, 9);
        mvc.perform(post("/api/exchange-requests").header(STUDENT, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredEnrollmentId\":" + enr + ",\"wantedOfferingIds\":[10]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.matchSessionId").exists());
    }

    @Test
    @DisplayName("양쪽 동의 → 한 트랜잭션에서 두 좌석이 맞바뀌고 세션은 COMPLETED, 요청은 COMPLETED")
    void bothAcceptExecutesSwap() throws Exception {
        long requestA = register(1, 10, "9", false);
        long requestB = register(2, 9, "10", false);

        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2"))
                .andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(matched.getResponse().getContentAsString())
                .path("session").path("sessionId").asLong();

        // 첫 동의 → 아직 PENDING
        mvc.perform(post("/api/match-sessions/" + sessionId + "/accept").header(STUDENT, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.firstAcceptedAt").exists());

        // 같은 사람이 두 번 동의하면 409
        mvc.perform(post("/api/match-sessions/" + sessionId + "/accept").header(STUDENT, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_RESPONDED"));

        // 두 번째 동의 → 교환 실행 + COMPLETED
        mvc.perform(post("/api/match-sessions/" + sessionId + "/accept").header(STUDENT, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 좌석이 실제로 바뀌었는지
        mvc.perform(get("/api/enrollments/me").header(STUDENT, "1"))
                .andExpect(jsonPath("$.totalCredits").value(15))          // 학점은 그대로
                .andExpect(jsonPath("$.enrollments[*].course.offeringId",
                        org.hamcrest.Matchers.hasItem(9)))                // 01분반을 받았다
                .andExpect(jsonPath("$.enrollments[*].course.offeringId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(10))));

        mvc.perform(get("/api/enrollments/me").header(STUDENT, "2"))
                .andExpect(jsonPath("$.totalCredits").value(12))
                .andExpect(jsonPath("$.enrollments[*].course.offeringId",
                        org.hamcrest.Matchers.hasItem(10)))
                .andExpect(jsonPath("$.enrollments[*].course.offeringId",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(9))));

        // 요청 상태
        mvc.perform(get("/api/exchange-requests/" + requestA).header(STUDENT, "1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(get("/api/exchange-requests/" + requestB).header(STUDENT, "2"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 끝난 세션에는 더 응답할 수 없다
        mvc.perform(post("/api/match-sessions/" + sessionId + "/accept").header(STUDENT, "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_PENDING"));
    }

    @Test
    @DisplayName("한쪽이 거절하면 세션은 REJECTED, 두 요청은 다시 WAITING으로 돌아간다")
    void rejectRequeuesBoth() throws Exception {
        long requestA = register(1, 10, "9", false);
        long requestB = register(2, 9, "10", false);

        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2"))
                .andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(matched.getResponse().getContentAsString())
                .path("session").path("sessionId").asLong();

        mvc.perform(post("/api/match-sessions/" + sessionId + "/reject").header(STUDENT, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get("/api/exchange-requests/" + requestA).header(STUDENT, "1"))
                .andExpect(jsonPath("$.status").value("WAITING"));
        mvc.perform(get("/api/exchange-requests/" + requestB).header(STUDENT, "2"))
                .andExpect(jsonPath("$.status").value("WAITING"));

        // 좌석은 그대로
        mvc.perform(get("/api/enrollments/me").header(STUDENT, "1"))
                .andExpect(jsonPath("$.enrollments[*].course.offeringId",
                        org.hamcrest.Matchers.hasItem(10)));
    }

    @Test
    @DisplayName("깨우기는 3회까지, 4번째는 409 WAKE_LIMIT_EXCEEDED")
    void wakeLimit() throws Exception {
        register(1, 10, "9", false);
        long requestB = register(2, 9, "10", false);
        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2")).andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(matched.getResponse().getContentAsString())
                .path("session").path("sessionId").asLong();

        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/match-sessions/" + sessionId + "/wake").header(STUDENT, "2"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/match-sessions/" + sessionId + "/wake").header(STUDENT, "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAKE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("세션 권한: 참여자가 아니면 403 NOT_MY_SESSION")
    void sessionOwnership() throws Exception {
        register(1, 10, "9", false);
        long requestB = register(2, 9, "10", false);
        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2")).andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(matched.getResponse().getContentAsString())
                .path("session").path("sessionId").asLong();

        mvc.perform(get("/api/match-sessions/" + sessionId).header(STUDENT, "3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_MY_SESSION"));

        mvc.perform(post("/api/match-sessions/" + sessionId + "/accept").header(STUDENT, "3"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/match-sessions/me").header(STUDENT, "1"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sessionId").value(sessionId));
        mvc.perform(get("/api/match-sessions/me").header(STUDENT, "3"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("매칭 중 요청을 취소하면 세션이 무효화되고 상대는 대기열로 돌아간다")
    void cancelWhileMatched() throws Exception {
        long requestA = register(1, 10, "9", false);
        long requestB = register(2, 9, "10", false);
        MvcResult matched = mvc.perform(post("/api/exchange-requests/" + requestB + "/quick-match")
                        .header(STUDENT, "2")).andExpect(status().isOk()).andReturn();
        long sessionId = om.readTree(matched.getResponse().getContentAsString())
                .path("session").path("sessionId").asLong();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/exchange-requests/" + requestA).header(STUDENT, "1"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/exchange-requests/" + requestB).header(STUDENT, "2"))
                .andExpect(jsonPath("$.status").value("WAITING"));
        mvc.perform(get("/api/match-sessions/" + sessionId).header(STUDENT, "2"))
                .andExpect(jsonPath("$.status").value("INVALIDATED"));
    }
}

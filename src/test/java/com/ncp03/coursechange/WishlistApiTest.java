package com.ncp03.coursechange;

import com.ncp03.coursechange.support.ResettableIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 찜(하트) API.
 *
 * <p>샘플 데이터 기준: 학생 1(졸린고양이)은 분반 3·5·7·10·18을 수강 중이다.
 * 분반 10은 프로그래밍언어론 02분반이고, 분반 9는 같은 과목 01분반이다.
 */
class WishlistApiTest extends ResettableIntegrationTest {

    @Autowired
    MockMvc mvc;

    private static final String STUDENT = "X-Student-Id";

    @Test
    @DisplayName("찜 추가 → 목록 조회 → 삭제 (분반 ID로 토글)")
    void addListRemove() throws Exception {
        // 학생 3은 샘플에 찜이 없다. 분반 9(프언 01)를 찜한다.
        mvc.perform(post("/api/wishlists").header(STUDENT, "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.wishlistId").exists())
                .andExpect(jsonPath("$.alreadyEnrolled").value(false))
                .andExpect(jsonPath("$.course.offeringId").value(9))
                .andExpect(jsonPath("$.course.courseName").value("프로그래밍언어론"))
                .andExpect(jsonPath("$.course.sectionNo").value("01"));

        mvc.perform(get("/api/wishlists/me").header(STUDENT, "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(3))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.wishlists", hasSize(1)))
                .andExpect(jsonPath("$.wishlists[0].course.offeringId").value(9));

        mvc.perform(delete("/api/wishlists/9").header(STUDENT, "3"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/wishlists/me").header(STUDENT, "3"))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("같은 분반을 두 번 찜하면 409 WISHLIST_ALREADY_EXISTS")
    void duplicateWishlist() throws Exception {
        mvc.perform(post("/api/wishlists").header(STUDENT, "4")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":9}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/wishlists").header(STUDENT, "4")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WISHLIST_ALREADY_EXISTS"));

        mvc.perform(delete("/api/wishlists/9").header(STUDENT, "4")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("지금 듣는 '그 분반'은 찜 불가(409)지만, 같은 과목 '다른 분반'은 찜 가능 — 찜의 핵심 용도")
    void sameCourseDifferentSectionIsAllowed() throws Exception {
        // 학생 1은 분반 10(프언 02)을 수강 중이다.
        mvc.perform(post("/api/wishlists").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));

        // 같은 과목 01분반(9)은 교환으로 받고 싶은 대상이므로 찜할 수 있어야 한다.
        mvc.perform(post("/api/wishlists").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":9}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.course.offeringId").value(9));

        mvc.perform(delete("/api/wishlists/9").header(STUDENT, "1")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("찜 단계에서는 학점 상한으로 막지 않는다 — 학생 8(18/18학점)도 찜 가능")
    void noCreditLimitOnWishlist() throws Exception {
        mvc.perform(post("/api/wishlists").header(STUDENT, "8")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":7}"))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/wishlists/7").header(STUDENT, "8")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("오류: 없는 분반 404, 찜 안 한 분반 삭제 404, offeringId 누락 400, 헤더 없음 401")
    void errors() throws Exception {
        mvc.perform(post("/api/wishlists").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":99999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));

        mvc.perform(delete("/api/wishlists/19").header(STUDENT, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WISHLIST_NOT_FOUND"));

        mvc.perform(post("/api/wishlists").header(STUDENT, "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

        mvc.perform(post("/api/wishlists")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":9}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_STUDENT_ID"));
    }

    @Test
    @DisplayName("다른 학생의 찜은 내 목록에 보이지 않는다")
    void wishlistIsPerStudent() throws Exception {
        mvc.perform(post("/api/wishlists").header(STUDENT, "6")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"offeringId\":9}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/wishlists/me").header(STUDENT, "7"))
                .andExpect(jsonPath("$.count").value(0));

        mvc.perform(delete("/api/wishlists/9").header(STUDENT, "6")).andExpect(status().isNoContent());
    }
}

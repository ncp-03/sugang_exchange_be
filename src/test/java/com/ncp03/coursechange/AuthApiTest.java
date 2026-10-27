package com.ncp03.coursechange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncp03.coursechange.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로그인·토큰. 샘플 학생 1의 학번은 2024000001, 초기 비밀번호는 학번과 같다. */
class AuthApiTest extends IntegrationTestSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String login(String studentNo, String password) {
        return "{\"studentNo\":\"" + studentNo + "\",\"password\":\"" + password + "\"}";
    }

    private String loginToken(String studentNo) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(studentNo, studentNo)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).path("accessToken").asText();
    }

    @Test
    @DisplayName("로그인 성공: 토큰과 닉네임이 오고 학번·해시는 내려가지 않는다")
    void loginSuccess() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("2024000001", "2024000001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.nickname").value("졸린고양이"))
                .andExpect(jsonPath("$.studentNo").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("틀린 비밀번호와 없는 학번은 같은 401 INVALID_CREDENTIALS")
    void loginFailure() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("2024000001", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("9999999999", "x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    @DisplayName("토큰으로 내 정보 조회: 토큰의 학생이 곧 나다")
    void bearerTokenIdentifiesStudent() throws Exception {
        String token = loginToken("2024000002");   // 학생 2
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(2));
    }

    @Test
    @DisplayName("토큰이 있으면 X-Student-Id를 보내 남인 척해도 토큰이 우선한다")
    void tokenWinsOverHeader() throws Exception {
        String token = loginToken("2024000002");
        mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + token)
                        .header("X-Student-Id", "1"))
                .andExpect(jsonPath("$.studentId").value(2));
    }

    @Test
    @DisplayName("변조·깨진 토큰은 401 INVALID_TOKEN")
    void tamperedToken() throws Exception {
        String token = loginToken("2024000001");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        for (String bad : new String[]{tampered, "abc", "a.b", "."}) {
            mvc.perform(get("/api/students/me").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
    }
}

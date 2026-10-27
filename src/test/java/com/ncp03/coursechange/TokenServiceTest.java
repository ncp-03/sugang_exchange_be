package com.ncp03.coursechange;

import com.ncp03.coursechange.common.auth.AuthProperties;
import com.ncp03.coursechange.common.auth.TokenService;
import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Docker 없이 도는 순수 단위 테스트. */
class TokenServiceTest {

    private TokenService service(String secret, long ttl) {
        AuthProperties p = new AuthProperties();
        p.setSecret(secret);
        p.setTokenTtlSeconds(ttl);
        return new TokenService(p);
    }

    @Test
    @DisplayName("발급한 토큰은 검증하면 같은 학생 ID가 나온다")
    void roundTrip() {
        TokenService s = service("k", 60);
        assertThat(s.verify(s.issue(42L))).isEqualTo(42L);
    }

    @Test
    @DisplayName("유효 시간이 지나면 INVALID_TOKEN")
    void expires() {
        TokenService s = service("k", 60);
        Instant t0 = Instant.parse("2026-10-27T00:00:00Z");
        String token = s.issue(1L, t0);
        assertThat(s.verify(token, t0.plusSeconds(59))).isEqualTo(1L);
        assertThatThrownBy(() -> s.verify(token, t0.plusSeconds(61)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰, 쓰레기 문자열은 거부한다")
    void rejectsForeignAndGarbage() {
        String foreign = service("other", 60).issue(1L);
        TokenService s = service("k", 60);
        for (String bad : new String[]{foreign, "", "x", "a.b", "...", "@@.@@"}) {
            assertThatThrownBy(() -> s.verify(bad)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    @DisplayName("비밀번호 해시는 V3 SQL(SHA2(학번:비밀번호))과 같은 소문자 16진수 64자")
    void hashFormat() {
        String h = com.ncp03.coursechange.auth.AuthService.hash("2024000001", "2024000001");
        assertThat(h).hasSize(64).matches("[0-9a-f]{64}");
    }
}

package com.ncp03.coursechange.common.auth;

import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * 서명 토큰 발급·검증 (HMAC-SHA256, 외부 라이브러리 없음).
 * 형식: {@code base64url("학생ID:만료epoch초") + "." + base64url(HMAC)}.
 * 서버에 세션을 저장하지 않으므로 로그아웃·강제 만료는 지원하지 않는다 (유효 시간으로만 만료).
 */
@Component
public class TokenService {

    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] key;
    private final long ttlSeconds;

    public TokenService(AuthProperties props) {
        String secret = props.getSecret();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.key = random;
        } else {
            this.key = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.ttlSeconds = props.getTokenTtlSeconds();
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(Long studentId) {
        return issue(studentId, Instant.now());
    }

    public String issue(Long studentId, Instant now) {
        long exp = now.getEpochSecond() + ttlSeconds;
        String payload = ENC.encodeToString((studentId + ":" + exp).getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENC.encodeToString(sign(payload));
    }

    public Long verify(String token) {
        return verify(token, Instant.now());
    }

    /** 유효하면 학생 ID, 아니면 401 INVALID_TOKEN. */
    public Long verify(String token, Instant now) {
        try {
            int dot = token.indexOf('.');
            if (dot < 1 || dot == token.length() - 1) {
                throw invalid();
            }
            String payload = token.substring(0, dot);
            byte[] given = DEC.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(given, sign(payload))) {   // 시간 일정 비교
                throw invalid();
            }
            String[] parts = new String(DEC.decode(payload), StandardCharsets.UTF_8).split(":");
            if (parts.length != 2) {
                throw invalid();
            }
            if (Long.parseLong(parts[1]) < now.getEpochSecond()) {
                throw new ApiException(ErrorCode.INVALID_TOKEN, "토큰이 만료됐습니다. 다시 로그인해 주세요.");
            }
            return Long.parseLong(parts[0]);
        } catch (IllegalArgumentException e) {   // Base64·숫자 파싱 실패
            throw invalid();
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(ErrorCode.INVALID_TOKEN);
    }
}

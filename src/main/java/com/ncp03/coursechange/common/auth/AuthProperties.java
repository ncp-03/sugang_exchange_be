package com.ncp03.coursechange.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 인증 설정 (app.auth.*).
 *
 * <ul>
 *   <li>TOKEN(기본): 로그인으로 받은 {@code Authorization: Bearer <토큰>}만 인정한다.</li>
 *   <li>DEMO: 토큰이 없으면 {@code X-Student-Id} 헤더도 인정한다. 로컬 개발·부하테스트용.
 *       외부에 공개하는 서버에서는 쓰지 않는다.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    public enum Mode { TOKEN, DEMO }

    private Mode mode = Mode.TOKEN;

    /** 토큰 서명 키. 비어 있으면 기동할 때마다 무작위로 만든다(재시작하면 기존 토큰이 모두 무효). */
    private String secret = "";

    /** 토큰 유효 시간(초). 기본 8시간. */
    private long tokenTtlSeconds = 28800;

    public boolean isDemo() { return mode == Mode.DEMO; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public long getTokenTtlSeconds() { return tokenTtlSeconds; }
    public void setTokenTtlSeconds(long tokenTtlSeconds) { this.tokenTtlSeconds = tokenTtlSeconds; }
}

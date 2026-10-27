package com.ncp03.coursechange.common.web;

import com.ncp03.coursechange.common.auth.AuthProperties;
import com.ncp03.coursechange.common.auth.TokenService;
import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 요청을 보낸 학생 ID를 꺼낸다.
 * <ol>
 *   <li>{@code Authorization: Bearer <토큰>}이 있으면 토큰을 검증한다 (틀리면 401 INVALID_TOKEN).</li>
 *   <li>없으면 DEMO 모드에서만 {@code X-Student-Id}를 인정한다. TOKEN 모드(기본)에서는 401 MISSING_TOKEN.</li>
 * </ol>
 */
public class CurrentStudentIdArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_NAME = "X-Student-Id";
    private static final String BEARER = "Bearer ";

    private final TokenService tokenService;
    private final AuthProperties authProperties;

    public CurrentStudentIdArgumentResolver(TokenService tokenService, AuthProperties authProperties) {
        this.tokenService = tokenService;
        this.authProperties = authProperties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentStudentId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Long resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String authorization = webRequest.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return tokenService.verify(authorization.substring(BEARER.length()).trim());
        }
        if (authProperties.isDemo()) {
            return parse(webRequest.getHeader(HEADER_NAME));
        }
        throw new ApiException(ErrorCode.MISSING_TOKEN);
    }

    static Long parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.MISSING_STUDENT_ID);
        }
        try {
            long id = Long.parseLong(raw.trim());
            if (id < 1) {
                throw new ApiException(ErrorCode.INVALID_STUDENT_ID);
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.INVALID_STUDENT_ID);
        }
    }
}

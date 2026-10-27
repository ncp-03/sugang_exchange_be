package com.ncp03.coursechange.common.config;

import com.ncp03.coursechange.common.web.CurrentStudentIdArgumentResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Swagger UI 오른쪽 위 Authorize 버튼에 학생 ID를 넣으면 X-Student-Id 헤더로 전송된다. */
    public static final String STUDENT_ID_SCHEME = "studentId";
    /** POST /api/auth/login 으로 받은 토큰. Authorize 버튼에 토큰 값만 넣으면 된다. */
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("수강신청 교환 마켓 API")
                        .version("v0.1")
                        .description("""
                                가상 학사 시스템 + 강의 교환 서비스 API (팀 3조).
                                로그인 대신 `X-Student-Id` 헤더로 학생을 구분합니다 (샘플 학생 1~10).
                                Authorize 버튼에 학생 ID를 넣으면 '내 정보' API를 바로 호출할 수 있습니다.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .description("로그인(POST /api/auth/login)으로 받은 accessToken"))
                        .addSecuritySchemes(STUDENT_ID_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CurrentStudentIdArgumentResolver.HEADER_NAME)
                                .description("데모용 학생 ID (예: 1)")));
    }

    /** 로그인이 필요한 API는 Bearer 토큰 또는 X-Student-Id(DEMO 모드) 중 하나면 된다 (OR). */
    @Bean
    public OpenApiCustomizer bearerAlternative() {
        return openApi -> openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(op -> {
                    if (op.getSecurity() != null && !op.getSecurity().isEmpty()) {
                        op.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
                    }
                }));
    }
}

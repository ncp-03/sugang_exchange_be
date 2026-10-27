package com.ncp03.coursechange.auth;

import com.ncp03.coursechange.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "학번·비밀번호 로그인 → 토큰")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "로그인",
            description = "학번·비밀번호가 맞으면 토큰을 돌려줍니다. 이후 요청에 `Authorization: Bearer <accessToken>` 헤더를 붙이세요. "
                    + "학번이 없거나 비밀번호가 틀리면 같은 401 INVALID_CREDENTIALS로 응답합니다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공")
    @ApiResponse(responseCode = "400", description = "학번·비밀번호 누락 (INVALID_PARAMETER)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "학번 또는 비밀번호 불일치 (INVALID_CREDENTIALS)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.studentNo(), request.password());
    }
}

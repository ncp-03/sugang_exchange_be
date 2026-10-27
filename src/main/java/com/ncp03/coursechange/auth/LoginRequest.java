package com.ncp03.coursechange.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그인 요청")
public record LoginRequest(
        @Schema(description = "학번 (가상). 샘플 학생: 2024000001 ~", example = "2024000001")
        @NotBlank(message = "학번은 필수입니다.") String studentNo,
        @Schema(description = "비밀번호. 가상 데이터의 초기 비밀번호는 학번과 같다", example = "2024000001")
        @NotBlank(message = "비밀번호는 필수입니다.") String password
) {
}

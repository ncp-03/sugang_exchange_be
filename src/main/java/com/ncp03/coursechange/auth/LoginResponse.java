package com.ncp03.coursechange.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 결과. 이후 요청에 Authorization: Bearer <accessToken> 헤더를 붙인다. 학번은 내려주지 않는다")
public record LoginResponse(
        @Schema(description = "액세스 토큰") String accessToken,
        @Schema(description = "항상 Bearer", example = "Bearer") String tokenType,
        @Schema(description = "유효 시간(초)", example = "28800") long expiresInSeconds,
        @Schema(description = "학생 ID", example = "1") Long studentId,
        @Schema(description = "익명 닉네임", example = "졸린고양이") String nickname
) {
}

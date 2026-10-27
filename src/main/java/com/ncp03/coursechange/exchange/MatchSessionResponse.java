package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "매칭 세션. 양쪽이 제한 시간 안에 동의하면 교환이 실행된다")
public record MatchSessionResponse(
        @Schema(description = "세션 ID", example = "4") Long sessionId,
        @Schema(description = "상태. PENDING / COMPLETED / REJECTED / EXPIRED / INVALIDATED", example = "PENDING")
        String status,
        @Schema(description = "매칭 성립 시각", example = "2026-10-27T15:40:00") LocalDateTime openedAt,
        @Schema(description = "응답 제한 시각", example = "2026-10-27T15:42:00") LocalDateTime expiresAt,
        @Schema(description = "남은 시간(초). 0 이하면 만료", example = "95") long remainingSeconds,
        @Schema(description = "첫 동의 시각. 없으면 null") LocalDateTime firstAcceptedAt,
        @Schema(description = "참여자 2명 (나 포함)") List<MatchParticipantResponse> participants
) {
}

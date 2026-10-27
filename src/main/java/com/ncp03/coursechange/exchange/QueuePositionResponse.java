package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대기열 순위. 내 1순위 희망 분반을 같이 원하는 대기 요청들 사이에서 우선순위 점수로 매긴다")
public record QueuePositionResponse(
        @Schema(description = "요청 ID", example = "7") Long requestId,
        @Schema(description = "요청 상태. WAITING일 때만 순위가 있다", example = "WAITING") String status,
        @Schema(description = "내 순위 (1이 가장 먼저). 대기 중이 아니면 null", example = "3") Integer position,
        @Schema(description = "같은 분반을 원하는 대기 요청 수 (나 포함)", example = "12") int competitors,
        @Schema(description = "내 우선순위 점수. 대기 중이 아니면 null", example = "64") Long score
) {
}

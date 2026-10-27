package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 교환 요청 목록")
public record MyExchangeRequestsResponse(
        @Schema(description = "학생 ID", example = "1") Long studentId,
        @Schema(description = "요청 수", example = "2") int count,
        @Schema(description = "요청 목록 (최근 등록 먼저)") List<ExchangeRequestResponse> requests
) {
}

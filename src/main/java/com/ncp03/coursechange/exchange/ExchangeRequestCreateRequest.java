package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "교환 요청 등록. '이 수강 내역을 내놓고 희망 분반 중 하나를 받고 싶다'")
public record ExchangeRequestCreateRequest(

        @Schema(description = "내놓을 수강 내역 ID. GET /api/enrollments/me 의 enrollmentId", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "offeredEnrollmentId는 필수입니다.")
        @Min(value = 1, message = "offeredEnrollmentId는 1 이상이어야 합니다.")
        Long offeredEnrollmentId,

        @Schema(description = """
                희망 분반 ID 목록. 배열 순서가 우선순위다 (첫 번째가 1순위).
                여러 개는 '이 중 하나'를 뜻한다 (묶음 교환 아님).""",
                example = "[9, 11]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "희망 분반을 1개 이상 보내야 합니다.")
        List<@NotNull @Min(1) Long> wantedOfferingIds,

        @Schema(description = "등록 직후 빠른 매칭까지 바로 시도할지. 기본 true", example = "true")
        Boolean autoMatch
) {
    public boolean autoMatchOrDefault() {
        return autoMatch == null || autoMatch;
    }
}

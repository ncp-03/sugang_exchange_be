package com.ncp03.coursechange.exchange;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "희망 분반 수정 (전체 교체)")
public record ExchangePreferencesUpdateRequest(
        @Schema(description = "희망 분반 ID 목록. 배열 순서가 우선순위", example = "[9, 11]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "희망 분반을 1개 이상 보내야 합니다.")
        List<@NotNull @Min(1) Long> wantedOfferingIds
) {
}

package com.ncp03.coursechange.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "찜 희망 순위 변경. 지금 찜한 분반 전체를 원하는 순서대로 보낸다")
public record WishlistReorderRequest(
        @Schema(description = "분반 ID 목록. 배열 순서가 희망 순위다 (첫 번째가 1순위)",
                example = "[9, 11, 15]", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "분반 ID를 1개 이상 보내야 합니다.")
        List<@NotNull @Min(1) Long> offeringIds
) {
}

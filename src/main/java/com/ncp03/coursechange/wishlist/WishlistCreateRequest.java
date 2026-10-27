package com.ncp03.coursechange.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "찜 추가 요청")
public record WishlistCreateRequest(
        @Schema(description = "찜할 분반 ID", example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "offeringId는 필수입니다.")
        @Min(value = 1, message = "offeringId는 1 이상이어야 합니다.")
        Long offeringId
) {
}

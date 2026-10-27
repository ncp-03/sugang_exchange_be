package com.ncp03.coursechange.wishlist;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "내 찜 목록. 찜 단계에서는 학점 상한으로 제한하지 않는다")
public record MyWishlistsResponse(
        @Schema(description = "학생 ID", example = "1") Long studentId,
        @Schema(description = "찜한 분반 수", example = "3") int count,
        @Schema(description = "찜 목록 (최근 찜한 것 먼저)") List<WishlistItemResponse> wishlists
) {
}

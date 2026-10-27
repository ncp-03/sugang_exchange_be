package com.ncp03.coursechange.wishlist;

import com.ncp03.coursechange.course.CourseResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "찜 1건")
public record WishlistItemResponse(
        @Schema(description = "찜 ID", example = "12") Long wishlistId,
        @Schema(description = "찜한 시각", example = "2026-10-27T15:40:00") LocalDateTime createdAt,
        @Schema(description = "이미 수강 중인 분반인지. true면 교환 희망 대상으로 쓸 수 없다", example = "false")
        boolean alreadyEnrolled,
        @Schema(description = "강의 정보") CourseResponse course
) {
}

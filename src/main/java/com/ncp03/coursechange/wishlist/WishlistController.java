package com.ncp03.coursechange.wishlist;

import com.ncp03.coursechange.common.config.OpenApiConfig;
import com.ncp03.coursechange.common.error.ErrorResponse;
import com.ncp03.coursechange.common.web.CurrentStudentId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "찜", description = "하트·장바구니. 관심 분반을 담아 둔다 (학점 상한으로 막지 않음)")
@RestController
@RequestMapping("/api/wishlists")
public class WishlistController {

    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Operation(summary = "내 찜 목록",
            description = "최근에 찜한 분반이 먼저 나옵니다. alreadyEnrolled가 true면 이미 수강 중이라 교환 희망 대상으로 쓸 수 없습니다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "401", description = "헤더 없음 (MISSING_STUDENT_ID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public MyWishlistsResponse me(@Parameter(hidden = true) @CurrentStudentId Long studentId) {
        return wishlistService.myWishlists(studentId);
    }

    @Operation(summary = "찜 추가",
            description = "같은 분반을 두 번 찜하면 409 WISHLIST_ALREADY_EXISTS, 이미 수강 중인 과목이면 409 ALREADY_ENROLLED.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "201", description = "추가됨")
    @ApiResponse(responseCode = "400", description = "offeringId 누락·형식 오류 (INVALID_PARAMETER)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 분반 (COURSE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "중복 찜 또는 이미 수강 중",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WishlistItemResponse add(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                    @Valid @RequestBody WishlistCreateRequest request) {
        return wishlistService.add(studentId, request.offeringId());
    }

    @Operation(summary = "찜 삭제",
            description = "찜 ID가 아니라 분반 ID(offeringId)로 지웁니다. 프론트가 하트를 토글할 때 편합니다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "404", description = "찜 목록에 없음 (WISHLIST_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{offeringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                       @PathVariable @Min(1) Long offeringId) {
        wishlistService.remove(studentId, offeringId);
    }
}

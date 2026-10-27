package com.ncp03.coursechange.exchange;

import com.ncp03.coursechange.common.config.OpenApiConfig;
import com.ncp03.coursechange.common.error.ErrorResponse;
import com.ncp03.coursechange.common.web.CurrentStudentId;
import com.ncp03.coursechange.domain.ExchangeRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "교환 요청", description = "보유 강의를 내놓고 희망 분반을 받는 요청의 등록·조회·수정·취소, 그리고 빠른 매칭")
@RestController
@RequestMapping("/api/exchange-requests")
public class ExchangeRequestController {

    private final ExchangeRequestService requestService;
    private final MatchingService matchingService;

    public ExchangeRequestController(ExchangeRequestService requestService, MatchingService matchingService) {
        this.requestService = requestService;
        this.matchingService = matchingService;
    }

    @Operation(summary = "교환 요청 등록",
            description = """
                    내 수강 내역 1건을 내놓고 희망 분반 목록을 등록합니다 (대기열 진입).

                    - `offeredEnrollmentId`가 내 수강 내역이 아니면 403 NOT_MY_ENROLLMENT
                    - 같은 수강 내역으로 진행 중인 요청이 이미 있으면 409 REQUEST_ALREADY_EXISTS
                    - 희망 분반 중 하나라도 교환이 불가능하면(학점 초과·시간표 겹침·같은 과목 보유) 409로 거절합니다
                    - `autoMatch`가 true(기본)면 등록 직후 빠른 매칭까지 시도하고, 상대를 찾으면 상태가 MATCHED가 됩니다
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "201", description = "등록됨")
    @ApiResponse(responseCode = "400", description = "희망 분반 누락·중복·개수 초과",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "내 수강 내역 아님 / 패널티 제한",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 수강 내역·분반",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "중복 요청 / 학점 초과 / 시간표 겹침",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExchangeRequestResponse create(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                          @Valid @RequestBody ExchangeRequestCreateRequest body) {
        ExchangeRequest created = requestService.create(
                studentId, body.offeredEnrollmentId(), body.wantedOfferingIds());
        if (body.autoMatchOrDefault()) {
            try {
                matchingService.quickMatch(studentId, created.getId());
            } catch (RuntimeException e) {
                // 등록 자체는 성공했으므로 매칭 실패로 201을 깨지 않는다.
                // 프론트는 응답의 status(WAITING/MATCHED)와 matchSessionId로 판단한다.
            }
        }
        return requestService.getMyRequest(studentId, created.getId());
    }

    @Operation(summary = "내 교환 요청 목록",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/me")
    public MyExchangeRequestsResponse me(@Parameter(hidden = true) @CurrentStudentId Long studentId) {
        return requestService.myRequests(studentId);
    }

    @Operation(summary = "교환 요청 1건 조회",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "내 요청 아님 (NOT_MY_REQUEST)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 요청 (REQUEST_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{requestId}")
    public ExchangeRequestResponse get(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                       @PathVariable @Min(1) Long requestId) {
        return requestService.getMyRequest(studentId, requestId);
    }

    @Operation(summary = "희망 분반 수정",
            description = "희망 분반 목록을 전체 교체합니다. WAITING 상태에서만 가능하고, MATCHED면 409 REQUEST_NOT_MODIFIABLE.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "수정됨")
    @ApiResponse(responseCode = "409", description = "수정할 수 없는 상태",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PatchMapping("/{requestId}")
    public ExchangeRequestResponse updatePreferences(
            @Parameter(hidden = true) @CurrentStudentId Long studentId,
            @PathVariable @Min(1) Long requestId,
            @Valid @RequestBody ExchangePreferencesUpdateRequest body) {
        return requestService.replacePreferences(studentId, requestId, body.wantedOfferingIds());
    }

    @Operation(summary = "교환 요청 취소",
            description = "WAITING이면 바로 취소됩니다. MATCHED면 진행 중인 매칭 세션을 무효화하고 상대 요청을 대기열로 되돌립니다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "204", description = "취소됨")
    @ApiResponse(responseCode = "409", description = "이미 끝난 요청 (REQUEST_NOT_MODIFIABLE)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{requestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                       @PathVariable @Min(1) Long requestId) {
        requestService.cancel(studentId, requestId);
    }

    @Operation(summary = "내 요청의 대기열 순위",
            description = "내 1순위 희망 분반을 같이 원하는 대기 요청들 사이에서 우선순위 점수(학년·기다린 시간·학점 여유·감점)로 순위를 매긴다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "남의 요청",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{requestId}/queue")
    public QueuePositionResponse queue(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                       @PathVariable @Min(1) Long requestId) {
        return matchingService.queuePosition(studentId, requestId);
    }

    @Operation(summary = "빠른 매칭",
            description = """
                    서로 원하는 상대를 찾아 매칭 세션을 만듭니다.

                    상대를 못 찾는 것은 오류가 아니므로 **200 + matched=false**로 응답합니다.
                    - `NO_PREFERENCES` 희망 분반이 없음
                    - `NO_COUNTERPART` 상호 희망 상대가 없음
                    - `CANDIDATES_NOT_ELIGIBLE` 후보는 있었지만 학점·시간표 조건에서 모두 탈락

                    동시에 같은 상대를 노린 요청이 겹치면 409 EXCHANGE_CONFLICT가 날 수 있습니다 (재시도 대상).
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "매칭 성공 또는 상대 없음")
    @ApiResponse(responseCode = "409", description = "동시성 충돌 (EXCHANGE_CONFLICT) 또는 WAITING 아님",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{requestId}/quick-match")
    public QuickMatchResponse quickMatch(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                         @PathVariable @Min(1) Long requestId) {
        return matchingService.quickMatch(studentId, requestId);
    }
}

package com.ncp03.coursechange.exchange;

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
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "매칭 세션", description = "매칭 결과 확인과 동의·거절·깨우기. 양쪽이 동의하면 그 자리에서 교환이 실행된다")
@RestController
@RequestMapping("/api/match-sessions")
public class MatchSessionController {

    private final MatchingService matchingService;

    public MatchSessionController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @Operation(summary = "내 진행 중 매칭",
            description = "응답을 기다리는 세션 목록. 보통 0개 또는 1개입니다. remainingSeconds로 남은 시간을 표시하세요.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @GetMapping("/me")
    public List<MatchSessionResponse> me(@Parameter(hidden = true) @CurrentStudentId Long studentId) {
        return matchingService.myPendingSessions(studentId);
    }

    @Operation(summary = "매칭 세션 상세",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "403", description = "내 세션 아님 (NOT_MY_SESSION)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 세션 (SESSION_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{sessionId}")
    public MatchSessionResponse get(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                    @PathVariable @Min(1) Long sessionId) {
        return matchingService.getSession(studentId, sessionId);
    }

    @Operation(summary = "교환 동의",
            description = """
                    내 응답을 ACCEPTED로 바꿉니다. **두 번째 동의가 들어오면 그 자리에서 교환이 실행됩니다**
                    (두 수강 내역을 한 트랜잭션에서 맞바꿈. 도중 오류가 나면 전부 롤백).

                    응답의 status가 COMPLETED면 교환이 끝났고, PENDING이면 상대 응답을 기다리는 중입니다.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "동의 반영됨 (status로 완료 여부 확인)")
    @ApiResponse(responseCode = "409", description = """
            ALREADY_RESPONDED(이미 응답) / SESSION_EXPIRED(시간 초과) / SESSION_NOT_PENDING(끝난 세션)
            / EXCHANGE_CONFLICT(동시성) / CREDIT_LIMIT_EXCEEDED·SCHEDULE_CONFLICT(그사이 조건이 깨짐)""",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{sessionId}/accept")
    public MatchSessionResponse accept(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                       @PathVariable @Min(1) Long sessionId) {
        return matchingService.accept(studentId, sessionId);
    }

    @Operation(summary = "교환 거절",
            description = "세션을 끝내고 양쪽 요청을 대기열로 되돌립니다. 거절한 쪽은 대기 시간이 초기화됩니다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "거절 반영됨")
    @ApiResponse(responseCode = "409", description = "이미 응답 / 끝난 세션",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{sessionId}/reject")
    public MatchSessionResponse reject(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                       @PathVariable @Min(1) Long sessionId) {
        return matchingService.reject(studentId, sessionId);
    }

    @Operation(summary = "상대 깨우기",
            description = "상대에게 응답을 재촉합니다. 최대 3회, 초과하면 409 WAKE_LIMIT_EXCEEDED.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "깨우기 보냄")
    @ApiResponse(responseCode = "409", description = "횟수 초과 (WAKE_LIMIT_EXCEEDED)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/{sessionId}/wake")
    public MatchSessionResponse wake(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                     @PathVariable @Min(1) Long sessionId) {
        return matchingService.wake(studentId, sessionId);
    }
}

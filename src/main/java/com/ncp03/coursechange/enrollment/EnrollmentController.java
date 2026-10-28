package com.ncp03.coursechange.enrollment;

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

@Tag(name = "수강", description = "수강신청·수강삭제와 내 수강 목록·시간표")
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @Operation(summary = "내 수강 목록",
            description = "X-Student-Id 학생이 지금 수강 중인 분반 목록. 각 항목에 수업 시간이 있어 시간표를 그릴 수 있습니다.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "200", description = "성공")
    @ApiResponse(responseCode = "400", description = "헤더가 숫자가 아님 (INVALID_STUDENT_ID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "헤더 없음 (MISSING_STUDENT_ID)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 학생 (STUDENT_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/me")
    public MyEnrollmentsResponse me(@Parameter(hidden = true) @CurrentStudentId Long studentId) {
        return enrollmentService.myEnrollments(studentId);
    }

    @Operation(summary = "수강신청",
            description = """
                    분반 1개를 신청합니다. 교환과 같은 조건을 검사합니다.

                    - 이미 그 분반을 듣고 있으면 409 ALREADY_ENROLLED
                    - 같은 과목의 다른 분반을 듣고 있으면 409 SAME_COURSE_ALREADY_HELD
                    - 정원이 찼으면 409 COURSE_FULL
                    - 최대 학점을 넘으면 409 CREDIT_LIMIT_EXCEEDED
                    - 시간표가 겹치면 409 SCHEDULE_CONFLICT
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "201", description = "신청됨")
    @ApiResponse(responseCode = "404", description = "없는 분반 (COURSE_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "중복·정원·학점·시간표 조건 불가",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnrollmentItemResponse enroll(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                                         @Valid @RequestBody EnrollmentCreateRequest body) {
        return enrollmentService.enroll(studentId, body.offeringId());
    }

    @Operation(summary = "수강삭제",
            description = "내 수강 내역 1건을 뺍니다. 교환 요청이 진행 중인 좌석은 409 ENROLLMENT_IN_EXCHANGE.",
            security = @SecurityRequirement(name = OpenApiConfig.STUDENT_ID_SCHEME))
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @ApiResponse(responseCode = "403", description = "내 수강 내역이 아님 (NOT_MY_ENROLLMENT)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "없는 수강 내역 (ENROLLMENT_NOT_FOUND)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @DeleteMapping("/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Parameter(hidden = true) @CurrentStudentId Long studentId,
                       @PathVariable @Min(1) Long enrollmentId) {
        enrollmentService.cancel(studentId, enrollmentId);
    }
}

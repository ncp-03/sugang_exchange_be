package com.ncp03.coursechange.auth;

import com.ncp03.coursechange.common.auth.TokenService;
import com.ncp03.coursechange.common.error.ApiException;
import com.ncp03.coursechange.common.error.ErrorCode;
import com.ncp03.coursechange.domain.Student;
import com.ncp03.coursechange.domain.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final StudentRepository studentRepository;
    private final TokenService tokenService;

    public AuthService(StudentRepository studentRepository, TokenService tokenService) {
        this.studentRepository = studentRepository;
        this.tokenService = tokenService;
    }

    /** 학번·비밀번호가 맞으면 토큰 발급. 없는 학번과 틀린 비밀번호는 같은 오류로 응답한다(학번 존재 여부 비노출). */
    public LoginResponse login(String studentNo, String password) {
        Student student = studentRepository.findByStudentNo(studentNo.trim()).orElse(null);
        String expected = student == null ? null : student.getPasswordHash();
        // 학생이 없어도 해시 계산은 하고 비교한다 (응답 시간 차이로 학번 존재 여부가 드러나지 않게)
        String actual = hash(studentNo.trim(), password);
        boolean ok = expected != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                         actual.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return new LoginResponse(tokenService.issue(student.getId()), "Bearer", tokenService.ttlSeconds(),
                student.getId(), student.getNickname());
    }

    /** V3 마이그레이션의 SHA2(CONCAT(student_no, ':', password), 256)와 같은 값 (소문자 16진수). */
    public static String hash(String studentNo, String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((studentNo + ":" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

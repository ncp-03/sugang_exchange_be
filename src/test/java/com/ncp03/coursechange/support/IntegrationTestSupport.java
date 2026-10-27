package com.ncp03.coursechange.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 MySQL 8.0 컨테이너로 돌리는 통합 테스트의 부모 클래스.
 * - Docker가 없으면 이 클래스를 상속한 테스트는 실패하지 않고 "건너뜀(skipped)"으로 표시된다.
 * - 컨테이너는 테스트 전체에서 한 번만 띄운다 (스프링 컨텍스트도 재사용).
 * - Flyway가 V1(스키마), V2(샘플 데이터)를 적용한 상태에서 테스트한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestSupport {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("course_change")
            .withUsername("course_change")
            .withPassword("course_change")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        MYSQL.start(); // 이미 떠 있으면 아무것도 하지 않는다
        registry.add("spring.datasource.url",
                () -> MYSQL.getJdbcUrl() + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // 교환처럼 데이터를 실제로 바꾸는 테스트가 끝난 뒤 DB를 되돌릴 수 있도록 clean을 허용한다.
        // (테스트 컨테이너 전용. 운영 설정에는 영향이 없다.)
        registry.add("spring.flyway.clean-disabled", () -> "false");
        // 기존 테스트는 X-Student-Id 헤더를 쓴다. 토큰 동작은 AuthApiTest가 따로 검증한다.
        registry.add("app.auth.mode", () -> "DEMO");
        // 만료 처리는 테스트가 직접 호출해 시점을 통제한다.
        registry.add("app.matching.expire-scheduler-enabled", () -> "false");
    }
}

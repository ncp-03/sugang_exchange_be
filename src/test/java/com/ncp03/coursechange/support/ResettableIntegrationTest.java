package com.ncp03.coursechange.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 데이터를 실제로 바꾸는 테스트(교환 실행 등)의 부모 클래스.
 *
 * <p>테스트들은 컨테이너·스프링 컨텍스트를 공유하므로, 교환으로 enrollments가 바뀌면
 * 다른 테스트(예: "학생1은 분반 3·5·7·10·18 수강")가 깨진다.
 * 그래서 매 테스트 후 Flyway clean + migrate로 V1·V2 초기 상태로 되돌린다.
 *
 * <p>느리지만(테스트당 1~2초) 확실하다. 읽기 전용 테스트는 {@link IntegrationTestSupport}를 그대로 쓴다.
 */
public abstract class ResettableIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private Flyway flyway;

    @AfterEach
    void resetDatabase() {
        flyway.clean();
        flyway.migrate();
    }
}

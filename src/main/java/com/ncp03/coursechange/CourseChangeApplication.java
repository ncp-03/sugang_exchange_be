package com.ncp03.coursechange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class CourseChangeApplication {

    static {
        // 서버 OS 시간대(UTC 등)와 상관없이 항상 한국 시간으로 동작시킨다.
        // 이게 없으면 UTC 서버(CI)에서 TIME 컬럼이 9시간 어긋나 "월수 13:30"이 "월수 04:30"으로 보인다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CourseChangeApplication.class, args);
    }
}

package com.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 키가 든 application.properties 는 커밋 대상이 아니다. CI 에는 그 파일이 없으므로
// @Value 가 값을 못 찾아 기동이 깨진다. 더미 값과 H2 접속값은 application-test.properties 에 있다.
@SpringBootTest
@ActiveProfiles("test")
class PortfolioApplicationTests {

    @Test
    @DisplayName("스프링 컨텍스트가 뜬다")
    void contextLoads() {
        // 빈 주입 실패·설정 오류·Flyway 마이그레이션 실패는 여기서 터진다. compile 은 못 잡는 자리다
    }
}

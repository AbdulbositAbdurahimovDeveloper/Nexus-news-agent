package org.platform.nexus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        // testda file o'rniga in-memory H2 — repo ichida data/ yaratmaslik uchun
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class NexusApplicationTests {

    @Test
    void contextLoads() {
    }

}

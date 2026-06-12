package io.irn.aipipeline.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("clean")
@RequiredArgsConstructor
public class DevDataCleaner implements ApplicationRunner {

    private final JdbcClient jdbcClient;


    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("Profile 'clean' active — truncating all pipeline tables");
        jdbcClient.sql("""
                TRUNCATE TABLE
                    outbox_events,
                    pipeline_status_log,
                    article_processed,
                    article_raw
                RESTART IDENTITY CASCADE
                """).update();
        log.warn("All pipeline tables truncated");
    }
}

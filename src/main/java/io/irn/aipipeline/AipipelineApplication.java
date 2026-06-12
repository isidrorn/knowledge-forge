package io.irn.aipipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AipipelineApplication {

    static void main(final String[] args) {
        SpringApplication.run(AipipelineApplication.class, args);
    }
}

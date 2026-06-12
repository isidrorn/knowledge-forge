package io.irn.aipipeline.config;

import io.irn.aipipeline.processing.ProcessingProperties;
import io.irn.aipipeline.processing.llm.OllamaProperties;
import io.irn.aipipeline.processing.llm.OpenRouterProperties;
import io.irn.aipipeline.publisher.PublisherProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EntityScan("io.irn.aipipeline.domain")
@EnableJpaRepositories("io.irn.aipipeline.repos")
@EnableTransactionManagement
@EnableAsync
@EnableRetry
@EnableConfigurationProperties({IngestionProperties.class, ProcessingProperties.class, OpenRouterProperties.class, OllamaProperties.class, PublisherProperties.class})
public class DomainConfig {
}

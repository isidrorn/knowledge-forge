package io.irn.aipipeline.processing.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.llm.ollama")
public record OllamaProperties(String model) {}

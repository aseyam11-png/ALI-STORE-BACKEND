package com.store.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    private boolean enabled;
    private String baseUrl = "";
    private String apiKey = "";
    private String model = "";
    private int timeoutMs = 8000;
    private int maxInputChars = 1200;
    private int maxContextProducts = 8;
}

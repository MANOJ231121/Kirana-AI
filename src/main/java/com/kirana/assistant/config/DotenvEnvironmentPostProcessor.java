package com.kirana.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically loads properties from .env into Spring's ConfigurableEnvironment on startup.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        File envFile = new File(".env");
        if (!envFile.exists()) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(Paths.get(".env"));
            Map<String, Object> envProps = new HashMap<>();
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    String val = line.substring(idx + 1).trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    if (!key.isEmpty()) {
                        envProps.put(key, val);
                    }
                }
            }
            if (!envProps.isEmpty()) {
                environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", envProps));
                log.info("Loaded {} environment properties directly from .env file", envProps.size());
            }
        } catch (Exception e) {
            log.warn("Failed to load .env file into Spring Environment: {}", e.getMessage());
        }
    }
}

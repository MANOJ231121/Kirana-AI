package com.kirana.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KiranaAssistantApplication {

    public static void main(String[] args) {
        // Force IPv4-only DNS/connectivity. On Windows machines with broken
        // IPv6 (no working AAAA path) the JDK resolver hangs forever and
        // external APIs like Rime TTS fail with "Failed to resolve ...".
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv4Addresses", "true");
        SpringApplication.run(KiranaAssistantApplication.class, args);
    }
}

package com.anchor.indexer.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication(scanBasePackages = {"com.anchor.indexer", "com.anchor.parser.swap", "com.anchor.parser.service"})
public class IndexerApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(IndexerApplication.class);
        application.setDefaultProperties(Map.of("anchor.scanner.enabled", "true"));
        application.run(args);
    }
}

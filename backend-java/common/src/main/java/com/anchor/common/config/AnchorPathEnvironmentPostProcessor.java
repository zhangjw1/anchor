package com.anchor.common.config;

import com.anchor.common.support.RepoPaths;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作目录不在模块根上时，向上查找 db/migrations，修正 Flyway 路径。
 */
public class AnchorPathEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
    /**
     * 配置的迁移目录已经存在时不改写。
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configured = environment.getProperty("spring.flyway.locations", "");
        if (locationExists(configured)) {
            return;
        }
        Path migrations = RepoPaths.findUp("db/migrations");
        if (migrations == null) {
            return;
        }
        Map<String, Object> values = new HashMap<>();
        values.put("spring.flyway.locations", RepoPaths.flywayLocation(migrations));
        environment.getPropertySources().addFirst(new MapPropertySource("anchor-flyway", values));
    }

    private static boolean locationExists(String configured) {
        if (!configured.startsWith("filesystem:")) {
            return false;
        }
        String raw = configured.substring("filesystem:".length());
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            path = Path.of("").toAbsolutePath().resolve(path).normalize();
        }
        return Files.isDirectory(path);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

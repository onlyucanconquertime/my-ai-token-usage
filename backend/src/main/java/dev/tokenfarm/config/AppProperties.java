package dev.tokenfarm.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Path claudeLogsRoot,
        String timezone,
        int rescanWindowDays,
        int chartWindowDays,
        Path historyFilePath,
        Path svgOutputPath,
        PricingProperties pricing) {
}

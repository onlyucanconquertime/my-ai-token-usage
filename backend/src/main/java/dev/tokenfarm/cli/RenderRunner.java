package dev.tokenfarm.cli;

import dev.tokenfarm.art.TokenChartSvgGenerator;
import dev.tokenfarm.config.AppProperties;
import dev.tokenfarm.history.UsageHistoryStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-shot CLI entry point, invoked by the GitHub Actions workflow (8am/2pm/8pm): renders
 * the trailing window ending today as a pixel-bar-chart SVG from
 * {@code data/usage-history.json} to the path given by {@code app.svg-output-path}.
 * Activate with {@code --spring.profiles.active=render}.
 */
@Component
@Profile("render")
public class RenderRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RenderRunner.class);

    private final AppProperties appProperties;
    private final UsageHistoryStore historyStore;
    private final TokenChartSvgGenerator svgGenerator;

    public RenderRunner(
            AppProperties appProperties,
            UsageHistoryStore historyStore,
            TokenChartSvgGenerator svgGenerator) {
        this.appProperties = appProperties;
        this.historyStore = historyStore;
        this.svgGenerator = svgGenerator;
    }

    @Override
    public void run(String... args) {
        Path outputPath = appProperties.svgOutputPath();
        if (outputPath == null || outputPath.toString().isBlank()) {
            throw new IllegalStateException("app.svg-output-path must be set when running with --spring.profiles.active=render");
        }

        LocalDate today = LocalDate.now(ZoneId.of(appProperties.timezone()));
        var history = historyStore.load(appProperties.historyFilePath());
        String svg = svgGenerator.generate(today, appProperties.chartWindowDays(), history);

        try {
            Path parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, svg, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write SVG to " + outputPath, e);
        }

        log.info("Rendered {}-day window ending {} to {}", appProperties.chartWindowDays(), today, outputPath);
    }
}

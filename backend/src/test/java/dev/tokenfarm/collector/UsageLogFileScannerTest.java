package dev.tokenfarm.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageLogFileScannerTest {

    private final UsageLogFileScanner scanner = new UsageLogFileScanner();

    @Test
    void parsesUsageFromAssistantLinesAndSkipsOthers(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, String.join("\n",
                assistantLine("uuid-1", "2026-09-14T10:48:02.460Z", 10, 20, 5, 30),
                "{\"type\":\"user\",\"uuid\":\"uuid-user\",\"timestamp\":\"2026-09-14T10:49:00.000Z\"}",
                "{\"type\":\"assistant\",\"uuid\":\"uuid-no-usage\",\"timestamp\":\"2026-09-14T10:50:00.000Z\",\"message\":{}}",
                "not even json",
                ""));

        List<UsageEventDto> events = scanner.scanRecent(tempDir, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(events).hasSize(1);
        UsageEventDto event = events.get(0);
        assertThat(event.uuid()).isEqualTo("uuid-1");
        assertThat(event.inputTokens()).isEqualTo(10);
        assertThat(event.cacheCreationTokens()).isEqualTo(20);
        assertThat(event.cacheReadTokens()).isEqualTo(5);
        assertThat(event.outputTokens()).isEqualTo(30);
        assertThat(event.totalTokens()).isEqualTo(65);
    }

    @Test
    void dedupesRepeatedUuidsWithinARun(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("session.jsonl");
        Files.writeString(file, String.join("\n",
                assistantLine("uuid-1", "2026-09-14T10:48:02.460Z", 1, 0, 0, 1),
                assistantLine("uuid-1", "2026-09-14T10:48:02.460Z", 1, 0, 0, 1)));

        List<UsageEventDto> events = scanner.scanRecent(tempDir, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(events).hasSize(1);
    }

    @Test
    void skipsFilesOlderThanTheRescanWindow(@TempDir Path tempDir) throws IOException {
        Path oldFile = tempDir.resolve("old-session.jsonl");
        Files.writeString(oldFile, assistantLine("uuid-old", "2020-01-01T00:00:00.000Z", 1, 0, 0, 1));
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        List<UsageEventDto> events = scanner.scanRecent(tempDir, Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(events).isEmpty();
    }

    @Test
    void returnsEmptyListWhenRootDoesNotExist(@TempDir Path tempDir) {
        List<UsageEventDto> events = scanner.scanRecent(tempDir.resolve("missing"), Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(events).isEmpty();
    }

    private String assistantLine(String uuid, String timestamp, long input, long cacheCreation, long cacheRead, long output) {
        return String.format(
                "{\"type\":\"assistant\",\"uuid\":\"%s\",\"timestamp\":\"%s\",\"message\":{\"model\":\"claude-sonnet-5\",\"usage\":{"
                        + "\"input_tokens\":%d,\"cache_creation_input_tokens\":%d,\"cache_read_input_tokens\":%d,\"output_tokens\":%d}}}",
                uuid, timestamp, input, cacheCreation, cacheRead, output);
    }
}

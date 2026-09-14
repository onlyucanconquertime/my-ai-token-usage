package dev.tokenfarm.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Walks the local Claude Code log tree ({@code ~/.claude/projects/**}/*.jsonl}) and parses
 * assistant-message usage events. Only files modified since a given instant are read, so
 * repeated runs (e.g. after every Claude Code session) stay fast regardless of how much
 * history has accumulated.
 */
@Component
public class UsageLogFileScanner {

    private static final Logger log = LoggerFactory.getLogger(UsageLogFileScanner.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses every {@code *.jsonl} file under {@code root} modified on or after {@code since},
     * deduplicating messages by uuid within this call.
     */
    public List<UsageEventDto> scanRecent(Path root, Instant since) {
        if (!Files.isDirectory(root)) {
            log.warn("Claude logs root {} does not exist; treating as no usage", root);
            return List.of();
        }

        Map<String, UsageEventDto> byUuid = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .filter(p -> modifiedSince(p, since))
                    .forEach(p -> parseFile(p, byUuid));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk Claude logs root " + root, e);
        }
        return List.copyOf(byUuid.values());
    }

    private boolean modifiedSince(Path path, Instant since) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isAfter(since);
        } catch (IOException e) {
            log.warn("Could not read mtime for {}, skipping", path, e);
            return false;
        }
    }

    private void parseFile(Path path, Map<String, UsageEventDto> byUuid) {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                parseLine(line).ifPresent(event -> byUuid.put(event.uuid(), event));
            }
        } catch (IOException e) {
            log.warn("Failed to read {}, skipping", path, e);
        }
    }

    private java.util.Optional<UsageEventDto> parseLine(String line) {
        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (IOException e) {
            log.debug("Skipping malformed JSONL line: {}", e.getMessage());
            return java.util.Optional.empty();
        }

        if (!"assistant".equals(node.path("type").asText(null))) {
            return java.util.Optional.empty();
        }
        JsonNode usage = node.path("message").path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return java.util.Optional.empty();
        }
        String uuid = node.path("uuid").asText(null);
        String timestampText = node.path("timestamp").asText(null);
        if (uuid == null || timestampText == null) {
            return java.util.Optional.empty();
        }

        Instant timestamp;
        try {
            timestamp = Instant.parse(timestampText);
        } catch (java.time.format.DateTimeParseException e) {
            log.debug("Skipping line with unparsable timestamp {}", timestampText);
            return java.util.Optional.empty();
        }

        String model = node.path("message").path("model").asText(null);
        return java.util.Optional.of(new UsageEventDto(
                uuid,
                timestamp,
                model,
                usage.path("input_tokens").asLong(0),
                usage.path("cache_creation_input_tokens").asLong(0),
                usage.path("cache_read_input_tokens").asLong(0),
                usage.path("output_tokens").asLong(0)));
    }
}

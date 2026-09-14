package dev.tokenfarm.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Reads and writes {@code data/usage-history.json}: a flat, human-readable map of
 * ISO date -> {@link DayUsage}. This file is the single source of truth for the app's
 * history; it's small enough (~365 rows/year) that a database would be overkill.
 */
@Component
public class UsageHistoryStore {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public Map<LocalDate, DayUsage> load(Path path) {
        if (!Files.exists(path)) {
            return new TreeMap<>();
        }
        try {
            Map<String, DayUsage> raw = objectMapper.readValue(
                    Files.readAllBytes(path),
                    objectMapper.getTypeFactory().constructMapType(TreeMap.class, String.class, DayUsage.class));
            Map<LocalDate, DayUsage> result = new TreeMap<>();
            raw.forEach((dateText, usage) -> result.put(LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE), usage));
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read usage history at " + path, e);
        }
    }

    /** Overwrites only the given dates in the stored history, leaving all others untouched. */
    public Map<LocalDate, DayUsage> merge(Path path, Map<LocalDate, DayUsage> updates) {
        Map<LocalDate, DayUsage> merged = load(path);
        merged.putAll(updates);
        write(path, merged);
        return merged;
    }

    public void write(Path path, Map<LocalDate, DayUsage> history) {
        Map<String, DayUsage> serializable = new TreeMap<>();
        history.forEach((date, usage) -> serializable.put(date.format(DateTimeFormatter.ISO_LOCAL_DATE), usage));
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(path.toFile(), serializable);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write usage history at " + path, e);
        }
    }
}

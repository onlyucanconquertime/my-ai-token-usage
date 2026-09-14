package dev.tokenfarm.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UsageHistoryStoreTest {

    private final UsageHistoryStore store = new UsageHistoryStore();

    @Test
    void loadReturnsEmptyMapWhenFileMissing(@TempDir Path tempDir) {
        Map<LocalDate, DayUsage> loaded = store.load(tempDir.resolve("missing.json"));

        assertThat(loaded).isEmpty();
    }

    @Test
    void writeThenLoadRoundTrips(@TempDir Path tempDir) {
        Path file = tempDir.resolve("usage-history.json");
        Map<LocalDate, DayUsage> history = Map.of(
                LocalDate.of(2026, 9, 14), new DayUsage(1000, 0.05));

        store.write(file, history);
        Map<LocalDate, DayUsage> loaded = store.load(file);

        assertThat(loaded).containsExactlyEntriesOf(history);
    }

    @Test
    void mergeOverwritesOnlyGivenDatesAndKeepsTheRest(@TempDir Path tempDir) {
        Path file = tempDir.resolve("usage-history.json");
        store.write(file, Map.of(
                LocalDate.of(2026, 9, 1), new DayUsage(100, 0.01),
                LocalDate.of(2026, 9, 2), new DayUsage(200, 0.02)));

        Map<LocalDate, DayUsage> merged = store.merge(file, Map.of(
                LocalDate.of(2026, 9, 2), new DayUsage(999, 0.99),
                LocalDate.of(2026, 9, 3), new DayUsage(300, 0.03)));

        assertThat(merged).isEqualTo(Map.of(
                LocalDate.of(2026, 9, 1), new DayUsage(100, 0.01),
                LocalDate.of(2026, 9, 2), new DayUsage(999, 0.99),
                LocalDate.of(2026, 9, 3), new DayUsage(300, 0.03)));
    }
}

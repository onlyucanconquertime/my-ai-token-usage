package dev.tokenfarm.aggregation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tokenfarm.art.UsageTier;
import dev.tokenfarm.history.DayUsage;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TierAssignmentServiceTest {

    private final TierAssignmentService service = new TierAssignmentService();
    private final LocalDate start = LocalDate.of(2026, 9, 1);
    private final LocalDate today = LocalDate.of(2026, 9, 8);

    @Test
    void allZeroWindowIsEntirelyNoneExceptTodayWhichIsInProgress() {
        Map<LocalDate, UsageTier> tiers = service.assignTiers(start, today, today, Map.of());

        assertThat(tiers).hasSize(8);
        assertThat(tiers.get(today)).isEqualTo(UsageTier.IN_PROGRESS);
        for (LocalDate date = start; date.isBefore(today); date = date.plusDays(1)) {
            assertThat(tiers.get(date)).isEqualTo(UsageTier.NONE);
        }
    }

    @Test
    void singleNonZeroFinishedDayGetsTheTopTier() {
        Map<LocalDate, DayUsage> history = Map.of(start.plusDays(2), new DayUsage(1000, 0.1));

        Map<LocalDate, UsageTier> tiers = service.assignTiers(start, today, today, history);

        assertThat(tiers.get(start.plusDays(2))).isEqualTo(UsageTier.PEAK);
        assertThat(tiers.get(start)).isEqualTo(UsageTier.NONE);
        assertThat(tiers.get(today)).isEqualTo(UsageTier.IN_PROGRESS);
    }

    @Test
    void todayIsAlwaysInProgressAndExcludedFromRankingEvenWithHugeUsage() {
        Map<LocalDate, DayUsage> history = new HashMap<>();
        history.put(start, new DayUsage(100, 0));
        history.put(today, new DayUsage(999_999_999, 0));

        Map<LocalDate, UsageTier> tiers = service.assignTiers(start, today, today, history);

        assertThat(tiers.get(today)).isEqualTo(UsageTier.IN_PROGRESS);
        // Only one finished non-zero day, so it alone claims the top tier.
        assertThat(tiers.get(start)).isEqualTo(UsageTier.PEAK);
    }

    @Test
    void evenlySizedFinishedWindowSplitsIntoExactQuartiles() {
        LocalDate farFutureToday = start.plusDays(20); // keep all 8 sample days finished
        Map<LocalDate, DayUsage> history = new HashMap<>();
        for (int day = 0; day < 8; day++) {
            history.put(start.plusDays(day), new DayUsage((day + 1) * 100L, 0));
        }

        Map<LocalDate, UsageTier> tiers = service.assignTiers(start, start.plusDays(7), farFutureToday, history);

        assertThat(tiers.get(start.plusDays(0))).isEqualTo(UsageTier.LOW);
        assertThat(tiers.get(start.plusDays(1))).isEqualTo(UsageTier.LOW);
        assertThat(tiers.get(start.plusDays(2))).isEqualTo(UsageTier.MEDIUM);
        assertThat(tiers.get(start.plusDays(3))).isEqualTo(UsageTier.MEDIUM);
        assertThat(tiers.get(start.plusDays(4))).isEqualTo(UsageTier.HIGH);
        assertThat(tiers.get(start.plusDays(5))).isEqualTo(UsageTier.HIGH);
        assertThat(tiers.get(start.plusDays(6))).isEqualTo(UsageTier.PEAK);
        assertThat(tiers.get(start.plusDays(7))).isEqualTo(UsageTier.PEAK);
    }
}

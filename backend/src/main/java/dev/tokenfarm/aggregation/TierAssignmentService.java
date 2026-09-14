package dev.tokenfarm.aggregation;

import dev.tokenfarm.art.UsageTier;
import dev.tokenfarm.history.DayUsage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/**
 * Assigns each day in a date range a relative usage tier, the same way GitHub colors its
 * contribution graph: tiers are based on how a day's tokens rank against the other
 * non-zero, finished days in that range, not against a fixed absolute number. Used only
 * to color that day's bar in the chart.
 */
@Service
public class TierAssignmentService {

    private static final UsageTier[] NON_ZERO_TIERS = {UsageTier.LOW, UsageTier.MEDIUM, UsageTier.HIGH, UsageTier.PEAK};

    /**
     * @param today excluded from ranking and always tagged {@link UsageTier#IN_PROGRESS},
     *              since its totals are still accumulating and aren't comparable to a
     *              finished day.
     */
    public Map<LocalDate, UsageTier> assignTiers(LocalDate start, LocalDate end, LocalDate today, Map<LocalDate, DayUsage> history) {
        Map<LocalDate, UsageTier> tiers = new TreeMap<>();
        List<LocalDate> nonZeroDays = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.equals(today)) {
                tiers.put(date, UsageTier.IN_PROGRESS);
                continue;
            }
            DayUsage usage = history.get(date);
            if (usage == null || usage.totalTokens() <= 0) {
                tiers.put(date, UsageTier.NONE);
            } else {
                nonZeroDays.add(date);
            }
        }

        nonZeroDays.sort(Comparator.comparingLong(date -> history.get(date).totalTokens()));
        int n = nonZeroDays.size();
        for (int rank = 0; rank < n; rank++) {
            tiers.put(nonZeroDays.get(rank), NON_ZERO_TIERS[tierIndex(rank, n)]);
        }
        return tiers;
    }

    /**
     * Maps an ascending rank (0 = lowest usage) among {@code n} non-zero days to a tier index
     * 0..3, via a ceiling-quartile so the busiest day always lands in the top tier regardless
     * of how few data points exist, and quartiles split evenly once n is large enough.
     */
    private int tierIndex(int rank, int n) {
        int ceilDiv = ((rank + 1) * NON_ZERO_TIERS.length + n - 1) / n;
        return Math.min(NON_ZERO_TIERS.length, ceilDiv) - 1;
    }
}

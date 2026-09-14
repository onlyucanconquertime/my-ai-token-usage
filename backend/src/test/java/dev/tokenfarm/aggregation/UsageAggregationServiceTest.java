package dev.tokenfarm.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tokenfarm.collector.UsageEventDto;
import dev.tokenfarm.config.PricingProperties;
import dev.tokenfarm.config.PricingProperties.ModelRate;
import dev.tokenfarm.history.DayUsage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsageAggregationServiceTest {

    private final PricingService pricingService = new PricingService(new PricingProperties(
            "claude-sonnet-5",
            Map.of("claude-sonnet-5", new ModelRate(3.00, 15.00, 3.75, 0.30))));

    // Only aggregateByDay is exercised here; it takes no dependency on the scanner/history
    // store, so this service can be constructed with nulls for the unused collaborators.
    private final UsageAggregationService service = new UsageAggregationService(null, null, pricingService, null);

    @Test
    void sumsTokensAndCostPerLocalDay() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        List<UsageEventDto> events = List.of(
                event("a", zone, 2026, 9, 14, 8, 0, 100, 0, 0, 0),
                event("b", zone, 2026, 9, 14, 9, 0, 100, 0, 0, 0),
                event("c", zone, 2026, 9, 15, 1, 0, 50, 0, 0, 0));

        Map<LocalDate, DayUsage> result = service.aggregateByDay(events, zone);

        assertThat(result).hasSize(2);
        assertThat(result.get(LocalDate.of(2026, 9, 14)).totalTokens()).isEqualTo(200);
        assertThat(result.get(LocalDate.of(2026, 9, 15)).totalTokens()).isEqualTo(50);
        assertThat(result.get(LocalDate.of(2026, 9, 14)).estimatedCost())
                .isCloseTo(200.0 / 1_000_000 * 3.00, within(0.0001));
    }

    @Test
    void bucketsByTheGivenZoneNotUtc() {
        // 2026-09-14T23:30 in Shanghai (UTC+8) is 2026-09-14T15:30 UTC, but 2026-09-15T00:30 in Tokyo (UTC+9).
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        UsageEventDto event = event("a", shanghai, 2026, 9, 14, 23, 30, 10, 0, 0, 0);

        Map<LocalDate, DayUsage> result = service.aggregateByDay(List.of(event), shanghai);

        assertThat(result).containsOnlyKeys(LocalDate.of(2026, 9, 14));
    }

    private UsageEventDto event(String uuid, ZoneId zone, int y, int m, int d, int h, int min,
            long input, long cacheCreation, long cacheRead, long output) {
        var instant = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant();
        return new UsageEventDto(uuid, instant, "claude-sonnet-5", input, cacheCreation, cacheRead, output);
    }
}

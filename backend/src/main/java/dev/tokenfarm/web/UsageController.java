package dev.tokenfarm.web;

import dev.tokenfarm.aggregation.TierAssignmentService;
import dev.tokenfarm.aggregation.UsageAggregationService;
import dev.tokenfarm.art.TokenChartSvgGenerator;
import dev.tokenfarm.art.UsageTier;
import dev.tokenfarm.config.AppProperties;
import dev.tokenfarm.history.DayUsage;
import dev.tokenfarm.history.UsageHistoryStore;
import dev.tokenfarm.web.dto.RegenerateResponseDto;
import dev.tokenfarm.web.dto.TileDto;
import dev.tokenfarm.web.dto.WindowSummaryDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the local Vue3 dev dashboard: lets the user preview the chart before it's pushed
 * anywhere. Not part of the automated pipeline (that's the {@code sync}/{@code render}
 * CLI profiles) and never publishes to GitHub. Always reflects the same trailing window
 * ending today that the published chart uses - there's nothing to page through.
 */
@RestController
@RequestMapping("/api/usage")
@Profile("!sync & !render")
public class UsageController {

    private final AppProperties appProperties;
    private final UsageHistoryStore historyStore;
    private final TierAssignmentService tierAssignmentService;
    private final TokenChartSvgGenerator svgGenerator;
    private final UsageAggregationService aggregationService;

    public UsageController(
            AppProperties appProperties,
            UsageHistoryStore historyStore,
            TierAssignmentService tierAssignmentService,
            TokenChartSvgGenerator svgGenerator,
            UsageAggregationService aggregationService) {
        this.appProperties = appProperties;
        this.historyStore = historyStore;
        this.tierAssignmentService = tierAssignmentService;
        this.svgGenerator = svgGenerator;
        this.aggregationService = aggregationService;
    }

    @GetMapping("/window")
    public WindowSummaryDto window() {
        LocalDate today = LocalDate.now(ZoneId.of(appProperties.timezone()));
        LocalDate start = today.minusDays(appProperties.chartWindowDays() - 1L);
        var history = historyStore.load(appProperties.historyFilePath());
        var tiers = tierAssignmentService.assignTiers(start, today, today, history);

        List<TileDto> days = new ArrayList<>();
        for (Map.Entry<LocalDate, UsageTier> entry : tiers.entrySet()) {
            DayUsage usage = history.get(entry.getKey());
            long tokens = usage == null ? 0 : usage.totalTokens();
            double cost = usage == null ? 0 : usage.estimatedCost();
            days.add(new TileDto(entry.getKey(), tokens, cost, entry.getValue()));
        }
        days.sort(java.util.Comparator.comparing(TileDto::date));
        return new WindowSummaryDto(start, today, days);
    }

    @GetMapping(value = "/window/svg", produces = "image/svg+xml")
    public String windowSvg() {
        LocalDate today = LocalDate.now(ZoneId.of(appProperties.timezone()));
        var history = historyStore.load(appProperties.historyFilePath());
        return svgGenerator.generate(today, appProperties.chartWindowDays(), history);
    }

    @PostMapping(value = "/regenerate", produces = MediaType.APPLICATION_JSON_VALUE)
    public RegenerateResponseDto regenerate() {
        try {
            var updated = aggregationService.syncRecent();
            return new RegenerateResponseDto(true, "Synced " + updated.size() + " day(s)", Instant.now());
        } catch (RuntimeException e) {
            return new RegenerateResponseDto(false, e.getMessage(), Instant.now());
        }
    }
}

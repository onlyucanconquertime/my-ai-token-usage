package dev.tokenfarm.aggregation;

import dev.tokenfarm.collector.UsageEventDto;
import dev.tokenfarm.collector.UsageLogFileScanner;
import dev.tokenfarm.config.AppProperties;
import dev.tokenfarm.history.DayUsage;
import dev.tokenfarm.history.UsageHistoryStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Rescans recent Claude Code logs and merges the aggregated totals into the history file. */
@Service
public class UsageAggregationService {

    private static final Logger log = LoggerFactory.getLogger(UsageAggregationService.class);

    private final AppProperties appProperties;
    private final UsageLogFileScanner scanner;
    private final PricingService pricingService;
    private final UsageHistoryStore historyStore;

    public UsageAggregationService(
            AppProperties appProperties,
            UsageLogFileScanner scanner,
            PricingService pricingService,
            UsageHistoryStore historyStore) {
        this.appProperties = appProperties;
        this.scanner = scanner;
        this.pricingService = pricingService;
        this.historyStore = historyStore;
    }

    /** Rescans the trailing window of logs and writes updated totals for those days only. */
    public Map<LocalDate, DayUsage> syncRecent() {
        ZoneId zone = ZoneId.of(appProperties.timezone());
        Instant since = Instant.now().minus(appProperties.rescanWindowDays(), ChronoUnit.DAYS);

        List<UsageEventDto> events = scanner.scanRecent(appProperties.claudeLogsRoot(), since);
        Map<LocalDate, DayUsage> updates = aggregateByDay(events, zone);

        log.info("Synced {} usage events across {} day(s) from {}", events.size(), updates.size(), appProperties.claudeLogsRoot());
        return historyStore.merge(appProperties.historyFilePath(), updates);
    }

    /** Pure aggregation, exposed separately so it's testable without touching the filesystem. */
    public Map<LocalDate, DayUsage> aggregateByDay(List<UsageEventDto> events, ZoneId zone) {
        Map<LocalDate, long[]> tokenTotals = new TreeMap<>();
        Map<LocalDate, Double> costTotals = new TreeMap<>();

        for (UsageEventDto event : events) {
            LocalDate date = event.timestamp().atZone(zone).toLocalDate();
            tokenTotals.merge(date, new long[] {event.totalTokens()}, (a, b) -> new long[] {a[0] + b[0]});
            costTotals.merge(date, pricingService.estimateCost(event), Double::sum);
        }

        Map<LocalDate, DayUsage> result = new TreeMap<>();
        tokenTotals.forEach((date, tokens) -> result.put(date, new DayUsage(tokens[0], costTotals.getOrDefault(date, 0.0))));
        return result;
    }
}

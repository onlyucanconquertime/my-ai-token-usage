package dev.tokenfarm.cli;

import dev.tokenfarm.aggregation.UsageAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * One-shot CLI entry point, invoked by the Claude Code {@code SessionEnd} hook
 * ({@code hooks/sync-usage.sh}) after every session: rescans recent logs and updates
 * {@code data/usage-history.json}. Activate with {@code --spring.profiles.active=sync}.
 */
@Component
@Profile("sync")
public class SyncRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SyncRunner.class);

    private final UsageAggregationService aggregationService;

    public SyncRunner(UsageAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    public void run(String... args) {
        var updated = aggregationService.syncRecent();
        log.info("Sync complete, {} day(s) refreshed", updated.size());
    }
}

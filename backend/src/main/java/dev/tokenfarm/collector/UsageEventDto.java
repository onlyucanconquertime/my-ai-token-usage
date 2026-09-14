package dev.tokenfarm.collector;

import java.time.Instant;

/** One assistant message's token usage, parsed from a Claude Code JSONL log line. */
public record UsageEventDto(
        String uuid,
        Instant timestamp,
        String model,
        long inputTokens,
        long cacheCreationTokens,
        long cacheReadTokens,
        long outputTokens) {

    public long totalTokens() {
        return inputTokens + cacheCreationTokens + cacheReadTokens + outputTokens;
    }
}

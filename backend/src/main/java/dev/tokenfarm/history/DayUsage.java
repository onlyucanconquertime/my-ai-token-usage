package dev.tokenfarm.history;

/** One day's aggregated token usage, as stored in {@code data/usage-history.json}. */
public record DayUsage(long totalTokens, double estimatedCost) {
}

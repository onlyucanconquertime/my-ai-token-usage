package dev.tokenfarm.config;

import java.util.Map;

/**
 * Per-model $/token rates used only to produce an estimate of what usage would cost
 * if billed via the API price list. Claude Code subscription usage isn't billed
 * per-token, so {@link dev.tokenfarm.aggregation.PricingService} output is illustrative,
 * not a real charge.
 */
public record PricingProperties(String defaultModel, Map<String, ModelRate> models) {

    public record ModelRate(
            double inputPerMillion,
            double outputPerMillion,
            double cacheWritePerMillion,
            double cacheReadPerMillion) {
    }
}

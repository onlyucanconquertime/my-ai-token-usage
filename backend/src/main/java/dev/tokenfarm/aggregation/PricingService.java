package dev.tokenfarm.aggregation;

import dev.tokenfarm.collector.UsageEventDto;
import dev.tokenfarm.config.PricingProperties;
import dev.tokenfarm.config.PricingProperties.ModelRate;
import org.springframework.stereotype.Component;

/**
 * Estimates what a message's tokens would cost if billed via the API price list.
 * Claude Code subscription usage is not billed per-token, so this is an illustrative
 * estimate for the farm dashboard, not a real charge.
 */
@Component
public class PricingService {

    private static final double MILLION = 1_000_000.0;

    private final PricingProperties pricing;

    public PricingService(PricingProperties pricing) {
        this.pricing = pricing;
    }

    public double estimateCost(UsageEventDto event) {
        ModelRate rate = rateFor(event.model());
        return event.inputTokens() / MILLION * rate.inputPerMillion()
                + event.outputTokens() / MILLION * rate.outputPerMillion()
                + event.cacheCreationTokens() / MILLION * rate.cacheWritePerMillion()
                + event.cacheReadTokens() / MILLION * rate.cacheReadPerMillion();
    }

    private ModelRate rateFor(String model) {
        ModelRate rate = model == null ? null : pricing.models().get(model);
        if (rate != null) {
            return rate;
        }
        ModelRate fallback = pricing.models().get(pricing.defaultModel());
        if (fallback == null) {
            throw new IllegalStateException("No pricing configured for default model " + pricing.defaultModel());
        }
        return fallback;
    }
}

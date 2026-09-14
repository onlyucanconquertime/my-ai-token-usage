package dev.tokenfarm.aggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tokenfarm.collector.UsageEventDto;
import dev.tokenfarm.config.PricingProperties;
import dev.tokenfarm.config.PricingProperties.ModelRate;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingServiceTest {

    private final PricingProperties pricing = new PricingProperties(
            "claude-sonnet-5",
            Map.of("claude-sonnet-5", new ModelRate(3.00, 15.00, 3.75, 0.30)));

    private final PricingService service = new PricingService(pricing);

    @Test
    void computesCostFromPerMillionRates() {
        UsageEventDto event = new UsageEventDto(
                "uuid", Instant.now(), "claude-sonnet-5",
                1_000_000, 1_000_000, 1_000_000, 1_000_000);

        double cost = service.estimateCost(event);

        assertThat(cost).isCloseTo(3.00 + 15.00 + 3.75 + 0.30, within(0.0001));
    }

    @Test
    void fallsBackToDefaultModelWhenUnknown() {
        UsageEventDto event = new UsageEventDto(
                "uuid", Instant.now(), "some-future-model",
                1_000_000, 0, 0, 0);

        double cost = service.estimateCost(event);

        assertThat(cost).isCloseTo(3.00, within(0.0001));
    }
}

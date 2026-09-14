package dev.tokenfarm.web.dto;

import dev.tokenfarm.art.UsageTier;
import java.time.LocalDate;

public record TileDto(LocalDate date, long totalTokens, double estimatedCost, UsageTier tier) {
}

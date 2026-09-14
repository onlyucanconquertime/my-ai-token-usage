package dev.tokenfarm.web.dto;

import java.time.LocalDate;
import java.util.List;

public record WindowSummaryDto(LocalDate start, LocalDate end, List<TileDto> days) {
}

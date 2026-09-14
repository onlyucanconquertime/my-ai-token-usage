package dev.tokenfarm.web.dto;

import java.time.Instant;

public record RegenerateResponseDto(boolean success, String message, Instant generatedAt) {
}

package dev.tokenfarm.art;

import dev.tokenfarm.aggregation.TierAssignmentService;
import dev.tokenfarm.history.DayUsage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Renders a trailing window of daily token usage as a chunky retro pixel-bar chart:
 * days along the horizontal axis (today always rightmost), token count along the
 * vertical axis, each bar built from discrete stacked square "pixels" rather than a
 * smooth rect so it stays visually pixel-art rather than a generic chart.
 *
 * <p>Palette is fixed (not theme-adaptive): black, white, and lime {@code #D2FF00},
 * with tiers expressed as opacity steps of the lime accent rather than separate hues.
 * Today's bar, whose totals are still accumulating, is drawn as a dashed "ghost" bar
 * with its exact running total printed above it instead of being ranked against
 * finished days. Self-contained: no external fonts, images, or scripts, so it's safe to
 * embed directly as a static image in a GitHub README.
 */
@Component
public class TokenChartSvgGenerator {

    private static final int BLOCK_SIZE = 6;
    private static final int BLOCK_GAP = 2;
    private static final int BLOCK_PITCH = BLOCK_SIZE + BLOCK_GAP;
    private static final int BAR_COLUMNS = 2;
    private static final int BAR_COLUMN_GAP = 1;
    private static final int BAR_WIDTH = BAR_COLUMNS * BLOCK_SIZE + (BAR_COLUMNS - 1) * BAR_COLUMN_GAP;
    private static final int DAY_GAP = 6;
    private static final int DAY_PITCH = BAR_WIDTH + DAY_GAP;
    private static final int MAX_BAR_BLOCKS = 20;
    private static final int CHART_HEIGHT = MAX_BAR_BLOCKS * BLOCK_PITCH;

    /** Fixed, not derived from observed data: normal daily usage is tens of millions of
     * tokens, so a small per-block unit (e.g. 1M) would peg every bar at the cap. */
    private static final long PER_BLOCK_TOKENS = 10_000_000L;
    private static final long SCALE_MAX = MAX_BAR_BLOCKS * PER_BLOCK_TOKENS;

    private static final int LEFT_MARGIN = 56;
    private static final int RIGHT_MARGIN = 16;
    private static final int TOP_MARGIN = 64;
    private static final int BOTTOM_MARGIN = 34;
    private static final int GROUND_STRIP_HEIGHT = 4;
    private static final int TODAY_LABEL_RESERVED = 24;

    private static final String FONT = "ui-monospace,SFMono-Regular,Menlo,Consolas,monospace";

    private final TierAssignmentService tierAssignmentService;

    public TokenChartSvgGenerator(TierAssignmentService tierAssignmentService) {
        this.tierAssignmentService = tierAssignmentService;
    }

    /** Renders the {@code windowDays} trailing days ending on and including {@code today}. */
    public String generate(LocalDate today, int windowDays, Map<LocalDate, DayUsage> history) {
        LocalDate start = today.minusDays(windowDays - 1L);
        Map<LocalDate, UsageTier> tiers = tierAssignmentService.assignTiers(start, today, today, history);

        long totalTokens = 0;
        int activeDays = 0;
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            DayUsage usage = history.get(date);
            long tokens = usage == null ? 0 : usage.totalTokens();
            totalTokens += tokens;
            if (tokens > 0) {
                activeDays++;
            }
        }

        int width = LEFT_MARGIN + RIGHT_MARGIN + windowDays * DAY_PITCH - DAY_GAP;
        int height = TOP_MARGIN + CHART_HEIGHT + BOTTOM_MARGIN;
        int baselineY = TOP_MARGIN + CHART_HEIGHT;

        StringBuilder svg = new StringBuilder();
        appendHeader(svg, width, height);
        appendTitle(svg, start, today, totalTokens, activeDays, windowDays);
        appendGridAndAxis(svg, width, baselineY);

        int dayIndex = 0;
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1), dayIndex++) {
            DayUsage usage = history.get(date);
            long tokens = usage == null ? 0 : usage.totalTokens();
            int dayX = LEFT_MARGIN + dayIndex * DAY_PITCH;
            boolean isToday = date.equals(today);
            appendBar(svg, dayX, baselineY, tokens, tiers.get(date), isToday);
            appendDayLabel(svg, dayX, baselineY, date, dayIndex, windowDays);
        }
        svg.append("</svg>\n");
        return svg.toString();
    }

    private void appendHeader(StringBuilder svg, int width, int height) {
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n");
        svg.append("<style>\n");
        svg.append("svg{--surface:#000000;--ink:#ffffff;--ink2:#bfbfbf;--muted:#7a7a7a;--grid:#232323;--axis:#3d3d3d;")
                .append("--accent:#d2ff00;}\n");
        svg.append(".t{font-family:").append(FONT).append(";fill:var(--ink)}\n");
        svg.append(".t2{font-family:").append(FONT).append(";fill:var(--ink2)}\n");
        svg.append(".tm{font-family:").append(FONT).append(";fill:var(--muted);font-variant-numeric:tabular-nums}\n");
        svg.append(".px{shape-rendering:crispEdges}\n");
        svg.append(".grid{stroke:var(--grid);stroke-width:1;stroke-dasharray:3 3;shape-rendering:crispEdges}\n");
        svg.append(".axis{stroke:var(--axis);stroke-width:2;shape-rendering:crispEdges}\n");
        svg.append("</style>\n");
        svg.append("<rect class=\"px\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" fill=\"var(--surface)\"/>\n");
        svg.append("<rect class=\"px\" x=\"1\" y=\"1\" width=\"").append(width - 2).append("\" height=\"").append(height - 2)
                .append("\" fill=\"none\" stroke=\"var(--axis)\" stroke-width=\"2\"/>\n");
    }

    private void appendTitle(StringBuilder svg, LocalDate start, LocalDate today, long totalTokens, int activeDays, int windowDays) {
        svg.append("<text class=\"t\" x=\"").append(LEFT_MARGIN).append("\" y=\"24\" font-size=\"15\" font-weight=\"700\" letter-spacing=\"2\">DAILY TOKENS</text>\n");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
        String range = dateFmt.format(start).toUpperCase(Locale.ROOT) + " - " + dateFmt.format(today).toUpperCase(Locale.ROOT);
        String subtitle = range + "  ·  " + formatTokens(totalTokens) + " TOKENS OVER " + windowDays + " DAYS  ·  "
                + activeDays + " ACTIVE  ·  1 BLOCK = " + formatTokens(PER_BLOCK_TOKENS);
        svg.append("<text class=\"t2\" x=\"").append(LEFT_MARGIN).append("\" y=\"42\" font-size=\"10.5\">")
                .append(escape(subtitle)).append("</text>\n");
    }

    private void appendGridAndAxis(StringBuilder svg, int width, int baselineY) {
        int rightEdge = width - RIGHT_MARGIN;
        for (int i = 1; i <= 4; i++) {
            int y = baselineY - CHART_HEIGHT * i / 4;
            long value = SCALE_MAX * i / 4;
            svg.append("<line class=\"grid\" x1=\"").append(LEFT_MARGIN).append("\" y1=\"").append(y)
                    .append("\" x2=\"").append(rightEdge).append("\" y2=\"").append(y).append("\"/>\n");
            svg.append("<text class=\"tm\" x=\"").append(LEFT_MARGIN - 8).append("\" y=\"").append(y + 3)
                    .append("\" font-size=\"9.5\" text-anchor=\"end\">").append(formatTokens(value)).append("</text>\n");
        }
        svg.append("<text class=\"tm\" x=\"").append(LEFT_MARGIN - 8).append("\" y=\"").append(baselineY + 3)
                .append("\" font-size=\"9.5\" text-anchor=\"end\">0</text>\n");
        svg.append("<line class=\"axis\" x1=\"").append(LEFT_MARGIN).append("\" y1=\"").append(baselineY)
                .append("\" x2=\"").append(rightEdge).append("\" y2=\"").append(baselineY).append("\"/>\n");
        appendGroundStrip(svg, baselineY, rightEdge);
    }

    /** A small decorative checkered strip under the axis, purely cosmetic ground texture. */
    private void appendGroundStrip(StringBuilder svg, int baselineY, int rightEdge) {
        int y = baselineY + 2;
        boolean toggle = false;
        for (int x = LEFT_MARGIN; x < rightEdge; x += BLOCK_SIZE) {
            int stripWidth = Math.min(BLOCK_SIZE, rightEdge - x);
            String color = toggle ? "var(--axis)" : "var(--grid)";
            svg.append("<rect class=\"px\" x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(stripWidth).append("\" height=\"").append(GROUND_STRIP_HEIGHT)
                    .append("\" fill=\"").append(color).append("\"/>\n");
            toggle = !toggle;
        }
    }

    private void appendBar(StringBuilder svg, int dayX, int baselineY, long tokens, UsageTier tier, boolean isToday) {
        int blocks = tokens <= 0 ? 0 : Math.max(1, Math.min(MAX_BAR_BLOCKS, (int) Math.round(tokens / (double) SCALE_MAX * MAX_BAR_BLOCKS)));

        if (isToday) {
            appendGhostBar(svg, dayX, baselineY, blocks);
            appendTodayLabel(svg, dayX, baselineY, blocks, tokens);
            return;
        }
        if (blocks == 0) {
            return;
        }

        double opacity = tierOpacity(tier);
        svg.append("<g class=\"px\" fill=\"var(--accent)\" fill-opacity=\"").append(opacity)
                .append("\" stroke=\"#000000\" stroke-opacity=\"0.35\" stroke-width=\"1\">\n");
        for (int b = 0; b < blocks; b++) {
            int blockY = baselineY - (b + 1) * BLOCK_PITCH + BLOCK_GAP;
            for (int col = 0; col < BAR_COLUMNS; col++) {
                int x = dayX + col * (BLOCK_SIZE + BAR_COLUMN_GAP);
                svg.append("<rect x=\"").append(x).append("\" y=\"").append(blockY)
                        .append("\" width=\"").append(BLOCK_SIZE).append("\" height=\"").append(BLOCK_SIZE).append("\"/>\n");
            }
        }
        svg.append("</g>\n");

        if (tier == UsageTier.PEAK) {
            int topY = baselineY - blocks * BLOCK_PITCH + BLOCK_GAP;
            int centerX = dayX + BAR_WIDTH / 2;
            appendSparkle(svg, centerX, topY - 6);
        }
    }

    private double tierOpacity(UsageTier tier) {
        return switch (tier) {
            case LOW -> 0.3;
            case MEDIUM -> 0.55;
            case HIGH -> 0.8;
            case PEAK -> 1.0;
            default -> 1.0;
        };
    }

    /** Today's bar: a dashed, lightly-filled "still counting" outline instead of a ranked tier. */
    private void appendGhostBar(StringBuilder svg, int dayX, int baselineY, int blocks) {
        int drawBlocks = Math.max(1, blocks);
        int topY = baselineY - drawBlocks * BLOCK_PITCH + BLOCK_GAP;
        int barHeight = drawBlocks * BLOCK_PITCH - BLOCK_GAP;
        svg.append("<rect class=\"px\" x=\"").append(dayX).append("\" y=\"").append(topY)
                .append("\" width=\"").append(BAR_WIDTH).append("\" height=\"").append(barHeight)
                .append("\" fill=\"var(--accent)\" fill-opacity=\"0.18\" stroke=\"var(--ink)\" stroke-width=\"1\" stroke-dasharray=\"2 2\"/>\n");
    }

    /**
     * The running total for today, since it isn't comparable to a finished day's
     * tier yet. Right-aligned to the bar's right edge (rather than centered) because
     * today is always the rightmost bar, so a centered label would overflow past the
     * canvas edge.
     */
    private void appendTodayLabel(StringBuilder svg, int dayX, int baselineY, int blocks, long tokens) {
        int barTopY = baselineY - Math.max(1, blocks) * BLOCK_PITCH + BLOCK_GAP;
        int labelY = Math.max(TOP_MARGIN + TODAY_LABEL_RESERVED, barTopY - 14);
        int rightX = dayX + BAR_WIDTH;
        String formattedNumber = formatTokens(tokens);
        svg.append("<text class=\"t\" x=\"").append(rightX).append("\" y=\"").append(labelY)
                .append("\" font-size=\"9.5\" font-weight=\"700\" text-anchor=\"end\">").append(formattedNumber).append("</text>\n");
    }

    /** A tiny pixel-sar (plus shape) marking the window's busiest finished day. */
    private void appendSparkle(StringBuilder svg, int centerX, int centerY) {
        svg.append("<g class=\"px\" fill=\"var(--ink)\">\n");
        int[][] offsets = {{0, 0}, {0, -3}, {0, 3}, {-3, 0}, {3, 0}};
        for (int[] offset : offsets) {
            svg.append("<rect x=\"").append(centerX + offset[0] - 1).append("\" y=\"").append(centerY + offset[1] - 1)
                    .append("\" width=\"2\" height=\"2\"/>\n");
        }
        svg.append("</g>\n");
    }

    private void appendDayLabel(StringBuilder svg, int dayX, int baselineY, LocalDate date, int dayIndex, int windowDays) {
        int interval = windowDays <= 15 ? 1 : 5;
        boolean isEdge = dayIndex == 0 || dayIndex == windowDays - 1;
        if (!isEdge && dayIndex % interval != 0) {
            return;
        }
        String label = DateTimeFormatter.ofPattern("MM/dd").format(date);
        svg.append("<text class=\"tm\" x=\"").append(dayX + BAR_WIDTH / 2.0).append("\" y=\"").append(baselineY + 18)
                .append("\" font-size=\"8.5\" text-anchor=\"middle\">").append(label).append("</text>\n");
    }

    private String formatTokens(long value) {
        if (value >= 1_000_000_000L) {
            return trimTrailingZero(value / 1_000_000_000.0) + "B";
        }
        if (value >= 1_000_000L) {
            return trimTrailingZero(value / 1_000_000.0) + "M";
        }
        if (value >= 1_000L) {
            return trimTrailingZero(value / 1_000.0) + "k";
        }
        return String.valueOf(value);
    }

    private String trimTrailingZero(double value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

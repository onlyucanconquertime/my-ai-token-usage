package dev.tokenfarm.art;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tokenfarm.aggregation.TierAssignmentService;
import dev.tokenfarm.history.DayUsage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class TokenChartSvgGeneratorTest {

    private final TokenChartSvgGenerator generator = new TokenChartSvgGenerator(new TierAssignmentService());
    private final LocalDate today = LocalDate.of(2026, 9, 14);

    @Test
    void emptyWindowRendersFrameAxisAndTodayGhostBarOnly() throws Exception {
        String svg = generator.generate(today, 10, Map.of());

        Document doc = parse(svg);
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("svg");
        assertThat(svg).contains("DAILY TOKENS");
        assertThat(svg).contains("today · in progress");
        // no ranked-tier bar fills when every finished day is empty.
        assertThat(svg).doesNotContain("var(--accent)\" fill-opacity=\"0.3\"", "var(--accent)\" fill-opacity=\"1.0\"");
    }

    @Test
    void todayIsAlwaysTheRightmostDay() {
        String svg = generator.generate(today, 30, Map.of());

        int todayIndex = svg.indexOf("today · in progress");
        assertThat(todayIndex).isPositive();
        // the day-axis label for the window's start date must appear before today's marker.
        LocalDate start = today.minusDays(29);
        String startLabel = String.format("%02d/%02d", start.getMonthValue(), start.getDayOfMonth());
        int startLabelIndex = svg.indexOf(">" + startLabel + "<");
        assertThat(startLabelIndex).isPositive().isLessThan(todayIndex);
    }

    @Test
    void todaysExactTokenCountIsPrintedAboveItsBar() {
        Map<LocalDate, DayUsage> history = Map.of(today, new DayUsage(17_085_922, 9.5));

        String svg = generator.generate(today, 10, history);

        assertThat(svg).contains("17,085,922");
        assertThat(svg).contains("today · in progress");
    }

    @Test
    void finishedPeakDayGetsFullOpacityAndASparkleNotAGhost() {
        Map<LocalDate, DayUsage> history = Map.of(today.minusDays(1), new DayUsage(200_000_000, 0));

        String svg = generator.generate(today, 10, history);

        assertThat(svg).contains("fill-opacity=\"1.0\"");
    }

    @Test
    void usesTheFixedTwentyMillionPerBlockScale() {
        String svg = generator.generate(today, 5, Map.of());

        assertThat(svg).contains("1 BLOCK = 20M");
    }

    @Test
    void paletteIsBlackWhiteAndLimeOnly() {
        String svg = generator.generate(today, 5, Map.of());

        assertThat(svg).contains("#000000").contains("#ffffff").contains("#d2ff00");
    }

    private Document parse(String svg) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)));
    }
}

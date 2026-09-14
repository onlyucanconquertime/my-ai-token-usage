package dev.tokenfarm.art;

/** Relative usage tier for one day, used only to color its bar in the chart. */
public enum UsageTier {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    PEAK,
    /** Today: excluded from ranking against other days since it isn't over yet. */
    IN_PROGRESS
}

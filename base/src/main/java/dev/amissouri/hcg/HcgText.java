package dev.amissouri.hcg;

/**
 * Small text helpers shared by the base plugin and its addons.
 */
public final class HcgText {

    private HcgText() {
    }

    /** Formats a raw health value (2 = one heart) as a heart count, dropping a trailing .0. */
    public static String formatHearts(double hp) {
        double hearts = hp / 2.0;
        return hearts == Math.floor(hearts) ? String.valueOf((long) hearts) : String.valueOf(hearts);
    }
}

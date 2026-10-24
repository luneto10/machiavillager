package io.github.luneto10.machiavillager.client.notice;

import io.github.luneto10.machiavillager.minecraft.notice.NoticeStyle;

/** The colors of each notice style. This is the one place to restyle the notices. Client only. */
final class NoticeStyles {
    private static final int SUMMON_ACCENT = 0xFFF2B33D;

    private NoticeStyles() {}

    /** The accent color of a style: used for the title, the side stripe and the countdown bar. */
    static int accentOf(NoticeStyle style) {
        // Exhaustive switch: a new style will not compile until it is given a color here.
        return switch (style) {
            case SUMMON -> SUMMON_ACCENT;
        };
    }
}

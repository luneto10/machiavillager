package io.github.luneto10.machiavillager.client.notice;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.minecraft.notice.NoticePayload;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;

/**
 * The on-screen notice card. It shows the last notice the server sent, slides and fades it in and out, and
 * drains a bar along its bottom so the time left is visible at a glance. Client only.
 */
public final class NoticeHud implements HudElement {
    /** Where this element is registered in the HUD. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Machiavillager.MOD_ID, "notice");

    private static final int CARD_MAX_WIDTH = 220;
    private static final int SCREEN_MARGIN = 8;
    // The card hangs at a quarter of the screen height: below the boss bars, above the crosshair.
    private static final int TOP_DIVISOR = 4;
    private static final int PADDING = 8;
    private static final int ACCENT_STRIPE_WIDTH = 2;
    private static final int HEADER_GAP = 4;
    private static final int BODY_LINE_GAP = 2;
    private static final int BOTTOM_GAP = 4;
    private static final int PROGRESS_BAR_HEIGHT = 2;

    private static final int SLIDE_PIXELS = 12;
    private static final int SLIDE_MS = 200;
    private static final int FADE_MS = 300;
    private static final int MS_PER_TICK = 50;
    private static final int MS_PER_SECOND = 1000;
    private static final float FULL_PROGRESS = 1f;
    private static final float NO_PROGRESS = 0f;

    private static final int BACKGROUND = 0xCC101018;
    private static final int OUTLINE = 0x55FFFFFF;
    private static final int TRACK = 0x88202028;
    private static final int BODY_TEXT = 0xFFD0D0D0;
    private static final int COUNTDOWN_TEXT = 0xFFFFFFFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int ALPHA_MASK = 0xFF;
    private static final int RGB_MASK = 0xFFFFFF;

    /** The card's box in GUI pixels, plus its accent color and the opacity for this frame. */
    private record Frame(int left, int top, int width, int height, int accent, float opacity) {}

    private NoticePayload current;
    private long startedAtMs;

    /** Takes a notice from the server. A hide notice clears the card at once. */
    public void show(NoticePayload notice) {
        // Zero duration means hide now: there is nothing left to draw.
        if (notice.durationTicks() == NoticePayload.HIDE_DURATION_TICKS) {
            current = null;
            return;
        }

        // A new notice replaces the old one and restarts the slide and the countdown.
        current = notice;
        startedAtMs = Util.getMillis();
    }

    /** Draws the card for this frame, or nothing when no notice is live. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
        // No notice, or its time is up: nothing to draw, and the slot is cleared.
        if (current == null) {
            return;
        }
        long totalMs = (long) current.durationTicks() * MS_PER_TICK;
        long elapsedMs = Util.getMillis() - startedAtMs;
        if (elapsedMs >= totalMs) {
            current = null;
            return;
        }

        drawCard(g, current, elapsedMs, totalMs);
    }

    private void drawCard(GuiGraphicsExtractor g, NoticePayload notice, long elapsedMs, long totalMs) {
        // The body is wrapped to the text width up front, so the card can be sized from its line count.
        Font font = Minecraft.getInstance().font;
        int width = cardWidth(g.guiWidth());
        List<FormattedCharSequence> bodyLines = font.split(Component.literal(notice.body()), textWidth(width));
        int height = cardHeight(font, bodyLines.size());

        // Centered horizontally, hanging at the top quarter of the screen.
        Frame frame = new Frame(
                (g.guiWidth() - width) / 2,
                g.guiHeight() / TOP_DIVISOR,
                width,
                height,
                NoticeStyles.accentOf(notice.style()),
                alphaAt(elapsedMs, totalMs));
        long remainingMs = totalMs - elapsedMs;
        float remainingFraction = (float) remainingMs / totalMs;

        // The slide is a matrix offset, so the card moves as one piece and its own coordinates stay put.
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(0, slideOffsetAt(elapsedMs));
        drawBox(g, frame);
        drawTitle(g, font, notice.title(), frame, remainingMs);
        drawBody(g, font, bodyLines, frame);
        drawProgress(g, frame, remainingFraction);
        pose.popMatrix();
    }

    private static void drawBox(GuiGraphicsExtractor g, Frame frame) {
        // The stripe is drawn last so the accent stays on top of the fill and the outline.
        g.fill(frame.left(), frame.top(), frame.left() + frame.width(), frame.top() + frame.height(),
                withAlpha(BACKGROUND, frame.opacity()));
        g.outline(frame.left(), frame.top(), frame.width(), frame.height(), withAlpha(OUTLINE, frame.opacity()));
        g.fill(frame.left(), frame.top(), frame.left() + ACCENT_STRIPE_WIDTH, frame.top() + frame.height(),
                withAlpha(frame.accent(), frame.opacity()));
    }

    private static void drawTitle(GuiGraphicsExtractor g, Font font, String title, Frame frame, long remainingMs) {
        // The title is bold so it reads first; the shadow keeps it legible over bright blocks.
        int textTop = frame.top() + PADDING;
        int titleX = frame.left() + ACCENT_STRIPE_WIDTH + PADDING;
        Component styledTitle = Component.literal(title).withStyle(ChatFormatting.BOLD);
        g.text(font, styledTitle, titleX, textTop, withAlpha(frame.accent(), frame.opacity()), true);

        // The countdown sits at the right edge of the same line, in white.
        String seconds = String.valueOf(secondsLeft(remainingMs));
        int secondsX = frame.left() + frame.width() - PADDING - font.width(seconds);
        g.text(font, seconds, secondsX, textTop, withAlpha(COUNTDOWN_TEXT, frame.opacity()), true);
    }

    private static void drawBody(GuiGraphicsExtractor g, Font font, List<FormattedCharSequence> lines, Frame frame) {
        // The lines are already wrapped to the text width, so nothing runs past the card edge.
        int bodyX = frame.left() + ACCENT_STRIPE_WIDTH + PADDING;
        int bodyY = frame.top() + PADDING + font.lineHeight + HEADER_GAP;
        int argb = withAlpha(BODY_TEXT, frame.opacity());
        for (FormattedCharSequence line : lines) {
            g.text(font, line, bodyX, bodyY, argb, false);
            bodyY += font.lineHeight + BODY_LINE_GAP;
        }
    }

    private static void drawProgress(GuiGraphicsExtractor g, Frame frame, float remainingFraction) {
        // The track shows the full length; the accent covers whatever time is still left.
        int barTop = frame.top() + frame.height() - PROGRESS_BAR_HEIGHT;
        int barBottom = barTop + PROGRESS_BAR_HEIGHT;
        int filledRight = frame.left() + Math.round(frame.width() * Math.max(NO_PROGRESS, remainingFraction));
        g.fill(frame.left(), barTop, frame.left() + frame.width(), barBottom,
                withAlpha(TRACK, frame.opacity()));
        g.fill(frame.left(), barTop, filledRight, barBottom, withAlpha(frame.accent(), frame.opacity()));
    }

    /** Card width: the max, or the screen minus a margin on each side when the screen is narrow. */
    static int cardWidth(int screenWidth) {
        return Math.min(CARD_MAX_WIDTH, screenWidth - 2 * SCREEN_MARGIN);
    }

    private static int textWidth(int cardWidth) {
        // The text starts right of the accent stripe and keeps padding on its right.
        return cardWidth - ACCENT_STRIPE_WIDTH - 2 * PADDING;
    }

    private static int cardHeight(Font font, int bodyLineCount) {
        // Padding, title, a gap, the wrapped body, and the progress bar at the bottom.
        int bodyHeight = bodyLineCount * (font.lineHeight + BODY_LINE_GAP);
        return PADDING + font.lineHeight + HEADER_GAP + bodyHeight + BOTTOM_GAP + PROGRESS_BAR_HEIGHT;
    }

    /** Opacity for this frame: fades in over the first SLIDE_MS and out over the last FADE_MS. */
    static float alphaAt(long elapsedMs, long totalMs) {
        // The card shows at the lower of the two ramps, so it is faded at both ends.
        float fadeIn = Math.min(FULL_PROGRESS, (float) elapsedMs / SLIDE_MS);
        float fadeOut = Math.min(FULL_PROGRESS, (float) (totalMs - elapsedMs) / FADE_MS);
        return Math.min(fadeIn, fadeOut);
    }

    /** Vertical offset for this frame: starts SLIDE_PIXELS above its place and settles to zero over SLIDE_MS. */
    static float slideOffsetAt(long elapsedMs) {
        float progress = Math.min(FULL_PROGRESS, (float) elapsedMs / SLIDE_MS);
        return -SLIDE_PIXELS * (FULL_PROGRESS - progress);
    }

    /** Whole seconds left, rounded up, so the count reads 3, 2, 1 and never shows 0 while it still runs. */
    static int secondsLeft(long remainingMs) {
        return (int) Math.ceil((double) remainingMs / MS_PER_SECOND);
    }

    /** Scales the alpha byte of an ARGB color by an opacity, keeping its RGB as it is. */
    static int withAlpha(int argb, float opacity) {
        // Only the alpha byte changes; the color channels are copied untouched.
        int alpha = Math.round(((argb >>> ALPHA_SHIFT) & ALPHA_MASK) * opacity);
        return (alpha << ALPHA_SHIFT) | (argb & RGB_MASK);
    }
}

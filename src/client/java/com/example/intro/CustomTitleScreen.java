package com.example.intro;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Fallback Title Screen — shown only when intro.mp4 is missing entirely.
 *
 * Visual theme: dark cave / amber / lava — matching the Minecraft cave
 * screenshot aesthetic requested by the user.
 */
public class CustomTitleScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int BTN_W        = 240;
    private static final int BTN_H        = 44;
    private static final int BTN_GAP      = 10;
    private static final int TITLE_OFFSET = -110;

    // ── Cave / amber colour palette ───────────────────────────────────────────
    private static final int COL_BG         = 0xFF060402;
    private static final int COL_BTN_BG     = 0xFF0A0704;
    private static final int COL_BTN_HV     = 0xFF150E06;
    private static final int COL_BTN_TEXT   = 0xFFD4B896;
    private static final int COL_BTN_TEXTHV = 0xFFFFE8C8;

    // ── Particle embers ───────────────────────────────────────────────────────
    private static final int EMBER_COUNT = 80;
    // [x%, y%, radius, speed, phase, brightness]
    private final float[][] embers = new float[EMBER_COUNT][6];

    // ── Button descriptors ────────────────────────────────────────────────────
    private static final String[][] BUTTONS = {
        { "\u25B6  Singleplayer", "sp"   },
        { "\u26A1  Multiplayer",  "mp"   },
        { "\u2699  Options",      "opt"  },
        { "\u2715  Quit Game",    "quit" },
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private long  startMs    = -1;
    private int   hoveredBtn = -1;
    private final long[] pressedMs = new long[BUTTONS.length];

    // ── Constructor ───────────────────────────────────────────────────────────
    public CustomTitleScreen() {
        super(Component.literal("Title"));
        java.util.Arrays.fill(pressedMs, -1L);

        Random rng = new Random(99991);
        for (int i = 0; i < EMBER_COUNT; i++) {
            embers[i][0] = rng.nextFloat();
            embers[i][1] = rng.nextFloat();
            embers[i][2] = 0.5f + rng.nextFloat() * 1.8f;
            embers[i][3] = 0.2f + rng.nextFloat() * 0.5f;
            embers[i][4] = rng.nextFloat() * 6.28f;
            embers[i][5] = 0.3f + rng.nextFloat() * 0.7f;
        }
    }

    @Override
    protected void init() { startMs = System.currentTimeMillis(); }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long  now     = System.currentTimeMillis();
        long  elapsed = (startMs < 0) ? 0 : now - startMs;
        float fadeIn  = Math.min(1f, elapsed / 700f);

        // Background
        gfx.fill(0, 0, width, height, COL_BG);
        drawCaveGradient(gfx, now);
        drawEmbers(gfx, now);
        drawVignette(gfx);

        // Content
        drawTitle(gfx, now, fadeIn, elapsed);
        detectHover(mouseX, mouseY, now);
        drawButtons(gfx, now, fadeIn);
        drawFooter(gfx, fadeIn);

        // Global fade-in
        if (fadeIn < 1f)
            gfx.fill(0, 0, width, height, (int)((1f - fadeIn) * 255) << 24);

        checkPressedActions(now);
    }

    // ── Background ────────────────────────────────────────────────────────────
    /** Subtle warm gradient rising from the bottom, like lava glow. */
    private void drawCaveGradient(GuiGraphics gfx, long now) {
        int H = height, W = width;
        int bands = height / 3;
        for (int dy = 0; dy < bands; dy++) {
            float t = 1f - (dy / (float)bands);
            float pulse = 0.6f + 0.15f * (float)Math.abs(Math.sin(now / 3000.0));
            int a = (int)(t * t * pulse * 38);
            gfx.fill(0, H - dy - 1, W, H - dy, (a << 24) | 0xCC5500);
        }
        // Faint top-edge dark blue cave ceiling
        for (int dy = 0; dy < 80; dy++) {
            float t = 1f - dy / 80f;
            int a = (int)(t * t * 22);
            gfx.fill(0, dy, W, dy + 1, (a << 24) | 0x001133);
        }
    }

    private void drawEmbers(GuiGraphics gfx, long now) {
        float t = now / 1000f;
        for (float[] e : embers) {
            // Embers drift upward
            float yFrac = (e[1] - t * e[3] * 0.04f + 10f) % 1f;
            float x     = e[0] * width;
            float y     = yFrac * height;
            float twink = 0.4f + 0.6f * (float)Math.abs(Math.sin(t * e[3] + e[4]));
            float bright = e[5] * twink;
            int   a     = Math.max(10, Math.min(200, (int)(bright * 200)));
            int   r     = Math.max(1, (int)e[2]);
            // Warm amber/orange ember colour
            int   col   = lerpColor(0xDD5500, 0xFFAA22, bright);
            gfx.fill((int)x - r, (int)y - r, (int)x + r, (int)y + r, (a << 24) | col);
        }
    }

    private void drawVignette(GuiGraphics gfx) {
        int depth = Math.min(width, height) / 3;
        for (int i = 0; i < depth; i++) {
            float t = i / (float)depth;
            int   a = (int)((1 - t) * (1 - t) * 180);
            int   W = width, H = height;
            gfx.fill(i,     i,     W-i,   i+1,   a << 24);
            gfx.fill(i,     H-i-1, W-i,   H-i,   a << 24);
            gfx.fill(i,     i,     i+1,   H-i,   a << 24);
            gfx.fill(W-i-1, i,     W-i,   H-i,   a << 24);
        }
    }

    // ── Title ─────────────────────────────────────────────────────────────────
    private void drawTitle(GuiGraphics gfx, long now, float fadeIn, long elapsed) {
        String title  = "M I N E C R A F T";
        int    cx     = width / 2;
        int    ty     = height / 2 + TITLE_OFFSET;
        int    totalW = font.width(title);
        int    xCurs  = cx - totalW / 2;

        char[] letters = title.toCharArray();
        for (int i = 0; i < letters.length; i++) {
            String ch    = String.valueOf(letters[i]);
            int    chW   = font.width(ch);
            long   delay = i * 45L;
            float  t     = Math.min(1f, Math.max(0f, (elapsed - delay) / 350f));
            float  ease  = 1f - (float)Math.pow(1 - t, 3);
            int    yOff  = (int)((1f - ease) * 22);
            int    mainA = (int)(ease * fadeIn * 235);
            int    shadA = (int)(ease * fadeIn * 100);

            float pulse = 0.85f + 0.15f * (float)Math.abs(Math.sin(now / 1800.0 + i * 0.5));
            int   col   = lerpColor(0xEEDDCC, 0xFFFFFF, pulse);

            gfx.drawString(font, "\u00a7l" + ch, xCurs + 1, ty + 1 + yOff, (shadA << 24) | 0x2A1A08, false);
            gfx.drawString(font, "\u00a7l" + ch, xCurs,     ty     + yOff, (mainA << 24) | col,       false);
            xCurs += chW;
        }

        // Amber underline
        float barFill = Math.min(1f, Math.max(0f, (elapsed - 400f) / 500f));
        if (barFill > 0) {
            int barW = (int)(totalW * barFill);
            int barX = cx - totalW / 2;
            int barY = ty + font.lineHeight + 4;
            int barA = (int)(fadeIn * 200);
            for (int g = 4; g > 0; g--) {
                int ga = (int)(fadeIn * 20 / g);
                gfx.fill(barX - g*2, barY - g, barX + barW + g*2, barY + 2 + g, (ga << 24) | 0xAA6622);
            }
            gfx.fill(barX, barY, barX + barW, barY + 2, (barA << 24) | 0xCC8833);
        }

        // Subtitle
        String sub  = "Java Edition  \u2022  " + SharedConstants.getCurrentVersion().getName();
        int    subA = (int)(fadeIn * 0.55f * 210);
        gfx.drawCenteredString(font, sub, cx, ty + font.lineHeight + 14, (subA << 24) | 0xAA8855);
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    private void detectHover(int mx, int my, long now) {
        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mx >= r[0] && mx <= r[0]+r[2] && my >= r[1] && my <= r[1]+r[3]
                    && pressedMs[i] < 0)
                hoveredBtn = i;
        }
    }

    private int[] btnRect(int i) {
        int totalH = BUTTONS.length * BTN_H + (BUTTONS.length - 1) * BTN_GAP;
        int startY = height / 2 + TITLE_OFFSET + 70;
        int x      = (width - BTN_W) / 2;
        int y      = startY + i * (BTN_H + BTN_GAP);
        return new int[]{ x, y, BTN_W, BTN_H };
    }

    private void drawButtons(GuiGraphics gfx, long now, float fadeIn) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[]   r       = btnRect(i);
            boolean hov     = (i == hoveredBtn);
            boolean pressed = (pressedMs[i] >= 0);

            float bf = Math.min(1f, Math.max(0f,
                    (float)(System.currentTimeMillis() - startMs - 200 - i * 60) / 350f));
            bf = easeOut(bf) * fadeIn;

            int bx = r[0], by = r[1], bw = r[2], bh = r[3];
            if (pressed) by += (int)(Math.min(1f, (now - pressedMs[i]) / 100f) * 2);

            // Amber glow
            float gs = hov ? 1f : (0.4f + 0.3f * (float)Math.abs(Math.sin(now / 1500.0 + i)));
            for (int g = 6; g > 0; g--) {
                int ga  = (int)(bf * gs * (hov ? 50 : 20) / g);
                int col = (ga << 24) | (hov ? 0xDD8833 : 0x774411);
                int pad = g * 5;
                gfx.fill(bx - pad, by - pad/2, bx + bw + pad, by + bh + pad/2, col);
            }

            // Shadow + body
            gfx.fill(bx + 3, by + 3, bx + bw + 3, by + bh + 3, (int)(bf * 0x55) << 24);
            int bgA = (int)(bf * (hov ? 0xCC : 0xAA));
            gfx.fill(bx, by, bx + bw, by + bh, (bgA << 24) | (hov ? COL_BTN_HV : COL_BTN_BG) & 0x00FFFFFF);

            // Inner highlight
            for (int dy = 0; dy < bh / 2; dy++) {
                float edge = 1f - dy / (float)(bh / 2);
                int   a    = (int)(edge * bf * (hov ? 20 : 10));
                gfx.fill(bx, by + dy, bx + bw, by + dy + 1, (a << 24) | 0xFFEECC);
            }

            // Shimmer
            if (!pressed) {
                float sw = (now / 2000f + i * 0.4f) % 1f;
                int   sx = bx + (int)(sw * (bw + 60)) - 30;
                for (int si = 0; si < 28; si++) {
                    float e2 = 1f - Math.abs(si / 14f - 1f);
                    int   sa = (int)(e2 * bf * (hov ? 50 : 25));
                    gfx.fill(sx + si, by, sx + si + 1, by + bh, (sa << 24) | 0xFFE8BB);
                }
            }

            // Border
            int bCol = hov
                ? ((int)(bf * 255) << 24 | 0xDD9944)
                : ((int)(bf * 180) << 24 | lerpColor(0x7A4A22, 0xAA6633,
                    (float)Math.abs(Math.sin(now / 900.0 + i))));
            drawBorder(gfx, bx, by, bw, bh, bCol);

            // Corners + accent bar
            int cc = hov ? 0xFFDD9944 : (int)(bf * 200) << 24 | 0x9A6030;
            gfx.fill(bx,       by,       bx+3,   by+3,   cc);
            gfx.fill(bx+bw-3,  by,       bx+bw,  by+3,   cc);
            gfx.fill(bx,       by+bh-3,  bx+3,   by+bh,  cc);
            gfx.fill(bx+bw-3,  by+bh-3,  bx+bw,  by+bh,  cc);
            gfx.fill(bx, by, bx + 3, by + bh, hov ? 0xFFEEAA44 : (int)(bf*200) << 24 | 0x8B5020);

            // Label
            int   tA  = (int)(bf * 235);
            int   tC  = (tA << 24) | ((hov ? COL_BTN_TEXTHV : COL_BTN_TEXT) & 0x00FFFFFF);
            String lbl = hov ? "  " + BUTTONS[i][0] : BUTTONS[i][0];
            gfx.drawCenteredString(font, "\u00a7l" + lbl, bx + bw / 2, by + (bh - 8) / 2, tC);
        }
    }

    private void drawFooter(GuiGraphics gfx, float fadeIn) {
        int a  = (int)(fadeIn * 140);
        int a2 = (int)(fadeIn * 80);
        gfx.drawCenteredString(font, "Discover more at minecraft.net",
                width / 2, height - 22, (a << 24) | 0xAA8855);
        gfx.drawCenteredString(font, "\u00a78Copyright Mojang AB  \u2022  Do not distribute",
                width / 2, height - 11, (a2 << 24) | 0x664433);
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mx >= r[0] && mx <= r[0]+r[2] && my >= r[1] && my <= r[1]+r[3]
                    && pressedMs[i] < 0) {
                pressedMs[i] = System.currentTimeMillis();
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void checkPressedActions(long now) {
        for (int i = 0; i < BUTTONS.length; i++) {
            if (pressedMs[i] >= 0 && now - pressedMs[i] >= 120) {
                String id = BUTTONS[i][1];
                pressedMs[i] = -1;
                navigate(id);
            }
        }
    }

    private void navigate(String id) {
        Minecraft mc = Minecraft.getInstance();
        switch (id) {
            case "sp"   -> ScreenUtil.setScreen(new SelectWorldScreen(this));
            case "mp"   -> ScreenUtil.setScreen(new JoinMultiplayerScreen(this));
            case "opt"  -> ScreenUtil.setScreen(new OptionsScreen(this, mc.options));
            case "quit" -> mc.stop();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,     y,     x+w,   y+1,   col);
        gfx.fill(x,     y+h-1, x+w,   y+h,   col);
        gfx.fill(x,     y,     x+1,   y+h,   col);
        gfx.fill(x+w-1, y,     x+w,   y+h,   col);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar=(a>>16)&0xFF, ag=(a>>8)&0xFF, ab=a&0xFF;
        int br=(b>>16)&0xFF, bg=(b>>8)&0xFF, bb=b&0xFF;
        return 0xFF000000
             | ((int)(ar+(br-ar)*t) << 16)
             | ((int)(ag+(bg-ag)*t) <<  8)
             |  (int)(ab+(bb-ab)*t);
    }

    private static float easeOut(float t) {
        return 1f - (float)Math.pow(1 - t, 3);
    }
}

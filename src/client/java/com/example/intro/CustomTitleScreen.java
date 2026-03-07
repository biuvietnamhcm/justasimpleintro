package com.example.intro;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

public class CustomTitleScreen extends Screen {

    private static final int   BLOCK_PX           = 4;
    private static final float BUILD_DURATION_SEC = 1.4f;
    private static final long  BUILD_STAGGER_MS   = 120L;
    private static final float TEXT_SHOW_AT       = 0.85f;

    private static final int EMBER_COUNT = 80;
    private final float[][] embers = new float[EMBER_COUNT][6];

    private static final String[][] BUTTONS = {
        { "\u25B6  Singleplayer", "sp"   },
        { "\u26A1  Multiplayer",  "mp"   },
        { "\u2699  Options",      "opt"  },
        { "\u2715  Quit Game",    "quit" },
    };

    private long  startMs    = -1;
    private int   hoveredBtn = -1;

    private final long[]  pressedMs     = new long[BUTTONS.length];

    // ── Hover transition state ─────────────────────────────────────────────────
    /** Timestamp when hover began (0 = not hovered). */
    private final long[]  hoverStartMs  = new long[BUTTONS.length];
    /** Timestamp when hover ended (0 = not fading out). */
    private final long[]  hoverEndMs    = new long[BUTTONS.length];
    /**
     * Timestamp of the most recent hover-enter event.
     * Used to drive a short entry flash that fades independently of sustained hover.
     */
    private final long[]  hoverEnterMs  = new long[BUTTONS.length];

    /** Fade-in / fade-out duration in ms. */
    private static final float HOVER_FADE_MS  = 180f;
    /** Duration of the entry flash in ms. */
    private static final float FLASH_DURATION = 260f;

    // ── Constructor ───────────────────────────────────────────────────────────
    public CustomTitleScreen() {
        super(Component.literal("Title"));
        java.util.Arrays.fill(pressedMs,    -1L);
        java.util.Arrays.fill(hoverStartMs,  0L);
        java.util.Arrays.fill(hoverEndMs,    0L);
        java.util.Arrays.fill(hoverEnterMs,  0L);
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

        gfx.fill(0, 0, width, height, 0xFF060402);
        drawCaveGradient(gfx, now);
        drawEmbers(gfx, now);
        drawVignette(gfx);

        // Determine hovered button
        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]
                    && pressedMs[i] < 0)
                hoveredBtn = i;
        }
        updateHoverState(now);

        drawButtons(gfx, now, fadeIn);

        if (fadeIn < 1f)
            gfx.fill(0, 0, width, height, (int)((1f - fadeIn) * 255) << 24);

        checkPressedActions(now);
    }

    // ── Background ────────────────────────────────────────────────────────────
    private void drawCaveGradient(GuiGraphics gfx, long now) {
        int bands = height / 3;
        for (int dy = 0; dy < bands; dy++) {
            float t = 1f - (dy / (float) bands);
            float p = 0.6f + 0.15f * (float) Math.abs(Math.sin(now / 3000.0));
            gfx.fill(0, height - dy - 1, width, height - dy,
                     ((int)(t * t * p * 38) << 24) | 0xCC5500);
        }
        for (int dy = 0; dy < 80; dy++) {
            float t = 1f - dy / 80f;
            gfx.fill(0, dy, width, dy + 1, ((int)(t * t * 22) << 24) | 0x001133);
        }
    }

    private void drawEmbers(GuiGraphics gfx, long now) {
        float t = now / 1000f;
        for (float[] e : embers) {
            float yFrac  = (e[1] - t * e[3] * 0.04f + 10f) % 1f;
            float bright = e[5] * (0.4f + 0.6f * (float) Math.abs(Math.sin(t * e[3] + e[4])));
            int   a      = Math.max(10, Math.min(200, (int)(bright * 200)));
            int   r      = Math.max(1, (int) e[2]);
            int   col    = lerpColor(0xDD5500, 0xFFAA22, bright);
            gfx.fill((int)(e[0] * width) - r, (int)(yFrac * height) - r,
                     (int)(e[0] * width) + r, (int)(yFrac * height) + r,
                     (a << 24) | col);
        }
    }

    private void drawVignette(GuiGraphics gfx) {
        int depth = Math.min(width, height) / 3;
        for (int i = 0; i < depth; i++) {
            int a = (int)((1 - i / (float) depth) * (1 - i / (float) depth) * 180);
            gfx.fill(i,          i,          width - i,  i + 1,      a << 24);
            gfx.fill(i,          height-i-1, width - i,  height - i, a << 24);
            gfx.fill(i,          i,          i + 1,      height - i, a << 24);
            gfx.fill(width-i-1,  i,          width - i,  height - i, a << 24);
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    private int[] btnRect(int i) {
        int bw  = Math.min(260, Math.max(160, width  / 4));
        int bh  = Math.min(48,  Math.max(28,  height / 14));
        int gap = Math.max(6,   height / 60);
        int totalH = BUTTONS.length * (bh + gap) - gap;
        return new int[]{ (width - bw) / 2, (height - totalH) / 2 + i * (bh + gap), bw, bh };
    }

    // ── Hover state machine ───────────────────────────────────────────────────
    private void updateHoverState(long now) {
        for (int i = 0; i < BUTTONS.length; i++) {
            boolean isHov = (i == hoveredBtn);
            if (isHov && hoverStartMs[i] == 0) {
                // Fresh hover enter
                hoverStartMs[i] = now;
                hoverEnterMs[i] = now;   // record entry for flash
                hoverEndMs[i]   = 0;
            } else if (!isHov && hoverStartMs[i] != 0) {
                // Hover exit
                hoverEndMs[i]   = now;
                hoverStartMs[i] = 0;
            }
        }
    }

    /**
     * Smoothstep-eased sustained hover intensity [0, 1].
     * Fades in when entering, fades out when leaving.
     */
    private float hovF(int i, long now) {
        float raw;
        if (hoverStartMs[i] != 0)
            raw = Math.min(1f, (now - hoverStartMs[i]) / HOVER_FADE_MS);
        else if (hoverEndMs[i] != 0)
            raw = Math.max(0f, 1f - (now - hoverEndMs[i]) / HOVER_FADE_MS);
        else
            return 0f;
        // Smoothstep: 3t²-2t³
        return raw * raw * (3f - 2f * raw);
    }

    /**
     * Short sharp flash on hover entry — peaks quickly then decays.
     * Returns [0, 1], independent of the sustained hover value.
     */
    private float flashF(int i, long now) {
        if (hoverEnterMs[i] == 0) return 0f;
        float t = Math.min(1f, (now - hoverEnterMs[i]) / FLASH_DURATION);
        // Bell curve: sin(π·t)
        return (float) Math.sin(Math.PI * t);
    }

    // ── Draw buttons ──────────────────────────────────────────────────────────
    private void drawButtons(GuiGraphics gfx, long now, float fadeIn) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[]   r  = btnRect(i);
            int     bx = r[0], by = r[1], bw = r[2], bh = r[3];
            boolean pressed = pressedMs[i] >= 0;
            float   hov     = hovF(i, now) * fadeIn;
            float   flash   = flashF(i, now) * fadeIn;

            long  btnStart  = startMs + 200L + (long)(i * BUILD_STAGGER_MS);
            float buildProg = Math.max(0f, Math.min(1f,
                    (now - btnStart) / (BUILD_DURATION_SEC * 1000f)));
            if (buildProg <= 0f) continue;

            // Press depression — eased
            float pressT = pressed ? easeOutCubic(Math.min(1f, (now - pressedMs[i]) / 80f)) : 0f;
            by += (int)(pressT * 3);

            final float MERGE_START = 0.72f;
            float mergeProg  = Math.max(0f, (buildProg - MERGE_START) / (1f - MERGE_START));
            float blockAlpha = 1f - mergeProg;
            float solidAlpha = mergeProg;

            if (blockAlpha > 0f)
                drawBlockStorm(gfx, bx, by, bw, bh, buildProg, blockAlpha, i);

            if (solidAlpha > 0f)
                drawSolidButton(gfx, bx, by, bw, bh,
                                solidAlpha * fadeIn, hov, flash, pressed, now, i);

            float textFade = Math.max(0f, (buildProg - TEXT_SHOW_AT) / (1f - TEXT_SHOW_AT));
            if (textFade > 0f)
                drawButtonLabel(gfx, bx, by, bw, bh, textFade * fadeIn, hov, flash, i);
        }
    }

    // ── Solid button visual ───────────────────────────────────────────────────
    private void drawSolidButton(GuiGraphics gfx,
                                  int bx, int by, int bw, int bh,
                                  float f, float hov, float flash,
                                  boolean pressed, long now, int idx) {

        // ── Drop shadow — grows on hover ──────────────────────────────────────
        int shadowA = (int)(f * (35 + hov * 45));
        gfx.fill(bx + 4, by + bh + 1, bx + bw + 4, by + bh + 5, (shadowA / 3) << 24);
        gfx.fill(bx + 2, by + bh,     bx + bw + 2, by + bh + 3, (shadowA / 2) << 24);
        gfx.fill(bx + 1, by + bh - 1, bx + bw + 1, by + bh + 1, shadowA       << 24);

        // ── Body — 3-band vertical gradient ──────────────────────────────────
        int topBandH = Math.max(1, bh / 4);
        int midBandH = bh - topBandH * 2;

        // On hover: body brightens slightly toward warm amber
        int topCol = lerpColor(pressed ? 0x160B00 : 0x1E1000, 0x2E1C04, hov);
        int midCol = lerpColor(pressed ? 0x100800 : 0x180C00, 0x231205, hov);
        int botCol = lerpColor(0x080400, 0x0F0700, hov);

        int topA = (int)(f * (pressed ? 0x80 : 0xA8));
        int midA = (int)(f * (pressed ? 0x70 : 0x95));
        int botA = (int)(f * (pressed ? 0x60 : 0x82));

        gfx.fill(bx, by,                          bx + bw, by + topBandH,            (topA << 24) | (topCol & 0xFFFFFF));
        gfx.fill(bx, by + topBandH,               bx + bw, by + topBandH + midBandH, (midA << 24) | (midCol & 0xFFFFFF));
        gfx.fill(bx, by + topBandH + midBandH,    bx + bw, by + bh,                  (botA << 24) | (botCol & 0xFFFFFF));

        // ── Entry flash — full-width bright wash ──────────────────────────────
        if (flash > 0.01f) {
            int fA = (int)(flash * f * 55);
            gfx.fill(bx, by, bx + bw, by + bh, (fA << 24) | 0xFFEECC);
        }

        // ── Sustained shimmer sweep — only while hovered ──────────────────────
        if (hov > 0.05f) {
            // Two sweeps: a fast narrow one + a slow wide one
            for (int pass = 0; pass < 2; pass++) {
                long   period = pass == 0 ? 900L : 2200L;
                float  sw     = (now % period) / (float) period;
                int    shP    = bx + (int)(sw * bw);
                int    shW    = Math.min(pass == 0 ? 30 : 70, bw);
                float  peakA  = pass == 0 ? 32 : 16;
                for (int si = 0; si < shW; si++) {
                    float e = 1f - Math.abs(si / (shW * 0.5f) - 1f);
                    int   a = (int)(e * hov * f * peakA);
                    if (a < 1) continue;
                    gfx.fill(shP - shW / 2 + si, by,
                             shP - shW / 2 + si + 1, by + bh,
                             (a << 24) | 0xFFDD99);
                }
            }
        }

        // ── Border ────────────────────────────────────────────────────────────
        // Pulse frequency increases on hover for a "live" feel
        float pulseSpeed = 350f - hov * 180f;   // 350ms idle → 170ms when fully hovered
        float borderPulse = hov > 0f
                ? 0.65f + 0.35f * (float) Math.sin(now / pulseSpeed)
                : 1f;
        int borderCol = lerpColor(0xAA5522,
                lerpColor(0xCC7733, 0xFFDD44, borderPulse), hov);
        int borderA   = (int)(f * (hov > 0f ? 235 : 155));
        drawBorder(gfx, bx, by, bw, bh, (borderA << 24) | borderCol);

        // Flash brightens border briefly on enter
        if (flash > 0.01f) {
            int fbA = (int)(flash * f * 120);
            drawBorder(gfx, bx, by, bw, bh, (fbA << 24) | 0xFFFFDD);
        }

        // ── Outer glow rings — layered, pulsing ───────────────────────────────
        if (hov > 0.05f) {
            float gp = borderPulse;
            // Ring 1: tight, amber
            int g1A = (int)(hov * f * 100 * gp);
            drawBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, (g1A << 24) | 0xFF9922);
            // Ring 2: wider, orange, slower
            int g2A = (int)(hov * f * 42 * gp);
            drawBorder(gfx, bx - 2, by - 2, bw + 4, bh + 4, (g2A << 24) | 0xDD6600);
            // Ring 3: very faint wide bloom
            int g3A = (int)(hov * f * 14);
            drawBorder(gfx, bx - 4, by - 3, bw + 8, bh + 6, (g3A << 24) | 0xAA4400);
        }
        // Entry flash also blooms the outer ring
        if (flash > 0.01f) {
            int fg1A = (int)(flash * f * 80);
            drawBorder(gfx, bx - 1, by - 1, bw + 2, bh + 2, (fg1A << 24) | 0xFFFFAA);
            int fg2A = (int)(flash * f * 30);
            drawBorder(gfx, bx - 3, by - 2, bw + 6, bh + 4, (fg2A << 24) | 0xFFCC44);
        }

        // ── Top bevel highlight ───────────────────────────────────────────────
        int topBevelA = (int)(f * (25 + hov * 65 + flash * 80));
        gfx.fill(bx + 1, by + 1, bx + bw - 1, by + 2, (topBevelA << 24) | 0xFFEECC);

        // ── Bottom inner shadow ───────────────────────────────────────────────
        gfx.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1, (int)(f * 45) << 24);

        // ── Left accent bar — expands smoothly on hover ───────────────────────
        // Width eased: 3px idle → 5px hovered
        int accentW   = 3 + (int)(hov * 2f);
        int accentCol = lerpColor(0xAA6633, 0xFFEE66, hov * 0.85f + flash * 0.15f);
        int accentA   = (int)(f * (175 + hov * 70));
        gfx.fill(bx, by, bx + accentW, by + bh, (accentA << 24) | accentCol);

        // Bright inner edge of accent bar on hover
        if (hov > 0.2f) {
            int edgeA = (int)(hov * f * 110);
            gfx.fill(bx + accentW, by, bx + accentW + 1, by + bh, (edgeA << 24) | 0xFFFF99);
        }

        // ── Right dim edge ────────────────────────────────────────────────────
        gfx.fill(bx + bw - 2, by, bx + bw, by + bh, (int)(f * 55) << 24);

        // ── Corner diamond accents ────────────────────────────────────────────
        float cornerBright = hov * 0.8f + flash * 0.2f;
        int   cornerA = (int)(f * (110 + cornerBright * 130));
        int   cornerC = lerpColor(0xBB7722, 0xFFFF55, cornerBright);

        // Top-left — 2×2 bright + 1px outer sparkle on hover
        gfx.fill(bx,         by,         bx + 2,  by + 2,  (cornerA     << 24) | cornerC);
        gfx.fill(bx + bw-2,  by,         bx + bw, by + 2,  (cornerA     << 24) | cornerC);
        gfx.fill(bx,         by + bh-2,  bx + 2,  by + bh, (cornerA / 2 << 24) | cornerC);
        gfx.fill(bx + bw-2,  by + bh-2,  bx + bw, by + bh, (cornerA / 2 << 24) | cornerC);

        if (hov > 0.3f) {
            // 1px outer sparkle at top corners
            int spA = (int)(hov * f * 90);
            gfx.fill(bx - 1,    by - 1, bx + 1,    by,     (spA << 24) | 0xFFFF88);
            gfx.fill(bx + bw-1, by - 1, bx + bw+1, by,     (spA << 24) | 0xFFFF88);
        }
    }

    // ── Button label ─────────────────────────────────────────────────────────
    private void drawButtonLabel(GuiGraphics gfx,
                                  int bx, int by, int bw, int bh,
                                  float fade, float hov, float flash, int idx) {
        int cx = bx + bw / 2;
        // Subtle upward nudge on hover: text floats 1px up
        int cy = by + (bh - 8) / 2 - (int)(hov * 1f);
        int tA = (int)(fade * 240);

        // Colour: warm white on hover, flash briefly boosts to pure white
        int col = lerpColor(0xE8D4B0,
                lerpColor(0xFFEECC, 0xFFFFFF, flash),
                hov);

        // Soft text shadow (offset 1,1) — darker when not hovered
        int shA = (int)(fade * (80 + hov * 60));
        gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[idx][0],
                cx + 1, cy + 1, (shA << 24) | 0x110800);

        // Optional faint glow pass behind label on hover (offset 0, slightly transparent)
        if (hov > 0.2f) {
            int glA = (int)(hov * fade * 35);
            gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[idx][0],
                    cx - 1, cy, (glA << 24) | 0xFFDD88);
            gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[idx][0],
                    cx + 1, cy, (glA << 24) | 0xFFDD88);
        }

        // Main label
        gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[idx][0],
                cx, cy, (tA << 24) | col);
    }

    // ── Block storm (unchanged) ───────────────────────────────────────────────
    private void drawBlockStorm(GuiGraphics gfx,
                                int bx, int by, int bw, int bh,
                                float buildProg, float masterAlpha, int btnIdx) {
        int cols  = Math.max(1, bw / BLOCK_PX);
        int rows  = Math.max(1, bh / BLOCK_PX);
        int total = cols * rows;

        for (int b = 0; b < total; b++) {
            long  seed = (long)(btnIdx * 99991 + b);
            float r0   = hash(seed);
            float r1   = hash(seed + 1);
            float r2   = hash(seed + 2);
            float r3   = hash(seed + 3);
            float r4   = hash(seed + 4);

            float delay  = r0 * 0.65f;
            float dur    = 0.20f + r1 * 0.15f;
            float blockT = Math.max(0f, Math.min(1f, (buildProg - delay) / dur));
            if (blockT <= 0f) continue;

            int   gridX = b % cols;
            int   gridY = b / cols;
            float endX  = bx + gridX * BLOCK_PX + BLOCK_PX * 0.5f;
            float endY  = by + gridY * BLOCK_PX + BLOCK_PX * 0.5f;

            float ox, oy;
            switch ((int)(r2 * 4)) {
                case 0:  ox = r3 * width;          oy = -height * (0.2f + r4 * 0.8f); break;
                case 1:  ox = r3 * width;          oy =  height * (1.2f + r4 * 0.8f); break;
                case 2:  ox = -width  * (0.2f+r3); oy =  r4 * height;                 break;
                default: ox =  width  * (1.2f+r3); oy =  r4 * height;                 break;
            }

            float midX  = (ox + endX) * 0.5f;
            float midY  = (oy + endY) * 0.5f;
            float ctrlX = midX + (r1 - 0.5f) * 140f;
            float ctrlY = midY + (r0 - 0.6f) * 100f;

            float t  = 1f - (float) Math.pow(1f - blockT, 2.6f);
            float mt = 1f - t;
            float curX = mt*mt*ox + 2*mt*t*ctrlX + t*t*endX;
            float curY = mt*mt*oy + 2*mt*t*ctrlY + t*t*endY;

            int sz = (int)(BLOCK_PX * 0.5f * Math.min(1f, blockT * 4f));
            if (sz < 1) sz = 1;

            int col;
            if      (blockT < 0.35f) col = lerpColor(0x3A3A3A, 0x6B2E0E, blockT / 0.35f);
            else if (blockT < 0.75f) col = lerpColor(0x6B2E0E, 0xBB5511, (blockT-0.35f)/0.40f);
            else                     col = lerpColor(0xBB5511, 0xFF9922, (blockT-0.75f)/0.25f);

            int alpha = (int)(Math.min(1f, blockT * 3f) * masterAlpha * 210f);
            gfx.fill((int)(curX - sz), (int)(curY - sz),
                     (int)(curX + sz), (int)(curY + sz), (alpha << 24) | col);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mx >= r[0] && mx <= r[0] + r[2]
                    && my >= r[1] && my <= r[1] + r[3]
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
                String id = BUTTONS[i][1]; pressedMs[i] = -1; navigate(id);
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
        gfx.fill(x,       y,       x + w,   y + 1,   col);
        gfx.fill(x,       y + h-1, x + w,   y + h,   col);
        gfx.fill(x,       y,       x + 1,   y + h,   col);
        gfx.fill(x + w-1, y,       x + w,   y + h,   col);
    }

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int)(ar + (br - ar) * t) << 16)
             | ((int)(ag + (bg - ag) * t) <<  8)
             |  (int)(ab + (bb - ab) * t);
    }

    private static float hash(long seed) {
        long x = seed ^ (seed >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (x & 0x7FFFFFFFFFFFFFFFL) / (float) 0x7FFFFFFFFFFFFFFFL;
    }
}
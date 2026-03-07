package com.example.intro;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Fallback Title Screen — shown only when intro.mp4 is missing.
 *
 * Buttons use the same block-storm construction as VideoScreen:
 * 1000 blocks fly from outside the screen to build each button,
 * then the label text fades in once the build completes.
 */
public class CustomTitleScreen extends Screen {

    // ── Block storm config ─────────────────────────────────────────────────────
    private static final int   BLOCK_PX           = 4;
    private static final float BUILD_DURATION_SEC = 1.4f;
    private static final long  BUILD_STAGGER_MS   = 120L;
    private static final float TEXT_SHOW_AT       = 0.85f;

    // ── Particle embers ───────────────────────────────────────────────────────
    private static final int EMBER_COUNT = 80;
    private final float[][] embers = new float[EMBER_COUNT][6];

    // ── Button descriptors ────────────────────────────────────────────────────
    private static final String[][] BUTTONS = {
        { "\u25B6  Singleplayer", "sp"   },
        { "\u26A1  Multiplayer",  "mp"   },
        { "\u2699  Options",      "opt"  },
        { "\u2715  Quit Game",    "quit" },
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private long startMs    = -1;
    private int  hoveredBtn = -1;
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
        long  now    = System.currentTimeMillis();
        long  elapsed = (startMs < 0) ? 0 : now - startMs;
        float fadeIn  = Math.min(1f, elapsed / 700f);

        gfx.fill(0, 0, width, height, 0xFF060402);
        drawCaveGradient(gfx, now);
        drawEmbers(gfx, now);
        drawVignette(gfx);

        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]
                    && pressedMs[i] < 0)
                hoveredBtn = i;
        }

        drawButtons(gfx, now, fadeIn);

        if (fadeIn < 1f)
            gfx.fill(0, 0, width, height, (int)((1f - fadeIn) * 255) << 24);

        checkPressedActions(now);
    }

    // ── Background ────────────────────────────────────────────────────────────
    private void drawCaveGradient(GuiGraphics gfx, long now) {
        int bands = height / 3;
        for (int dy = 0; dy < bands; dy++) {
            float t = 1f - (dy / (float)bands);
            float p = 0.6f + 0.15f * (float)Math.abs(Math.sin(now / 3000.0));
            gfx.fill(0, height - dy - 1, width, height - dy, ((int)(t * t * p * 38) << 24) | 0xCC5500);
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
            float bright = e[5] * (0.4f + 0.6f * (float)Math.abs(Math.sin(t * e[3] + e[4])));
            int   a      = Math.max(10, Math.min(200, (int)(bright * 200)));
            int   r      = Math.max(1, (int)e[2]);
            int   col    = lerpColor(0xDD5500, 0xFFAA22, bright);
            gfx.fill((int)(e[0] * width) - r, (int)(yFrac * height) - r,
                     (int)(e[0] * width) + r, (int)(yFrac * height) + r,
                     (a << 24) | col);
        }
    }

    private void drawVignette(GuiGraphics gfx) {
        int depth = Math.min(width, height) / 3;
        for (int i = 0; i < depth; i++) {
            int a = (int)((1 - i / (float)depth) * (1 - i / (float)depth) * 180);
            gfx.fill(i,           i,           width - i,     i + 1,         a << 24);
            gfx.fill(i,           height-i-1,  width - i,     height - i,    a << 24);
            gfx.fill(i,           i,           i + 1,         height - i,    a << 24);
            gfx.fill(width-i-1,   i,           width - i,     height - i,    a << 24);
        }
    }

    // ── Button layout ─────────────────────────────────────────────────────────
    private int[] btnRect(int i) {
        int bw  = Math.min(260, Math.max(160, width  / 4));
        int bh  = Math.min(48,  Math.max(28,  height / 14));
        int gap = Math.max(6,   height / 60);
        int totalH = BUTTONS.length * (bh + gap) - gap;
        return new int[]{ (width - bw) / 2, (height - totalH) / 2 + i * (bh + gap), bw, bh };
    }

    // ── Draw buttons ──────────────────────────────────────────────────────────
    private void drawButtons(GuiGraphics gfx, long now, float fadeIn) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r  = btnRect(i);
            int   bx = r[0], by = r[1], bw = r[2], bh = r[3];
            boolean hov     = (i == hoveredBtn) && pressedMs[i] < 0;
            boolean pressed = pressedMs[i] >= 0;

            long  btnStart  = startMs + 200L + (long)(i * BUILD_STAGGER_MS);
            float buildProg = Math.max(0f, Math.min(1f,
                    (now - btnStart) / (BUILD_DURATION_SEC * 1000f)));
            if (buildProg <= 0f) continue;

            int pressOff = pressed ? (int)(Math.min(1f, (now - pressedMs[i]) / 100f) * 2) : 0;
            by += pressOff;

            final float MERGE_START = 0.72f;
            float mergeProg  = Math.max(0f, (buildProg - MERGE_START) / (1f - MERGE_START));
            float blockAlpha = 1f - mergeProg;
            float solidAlpha = mergeProg;

            // 1. Flying blocks fade out during merge
            if (blockAlpha > 0f)
                drawBlockStorm(gfx, bx, by, bw, bh, buildProg, blockAlpha, i);

            // 2. Solid button fades in during merge
            if (solidAlpha > 0f) {
                float f = solidAlpha * fadeIn;
                gfx.fill(bx, by, bx + bw, by + bh,
                        ((int)(f * (hov ? 0xCC : 0xAA)) << 24) | (hov ? 0x2A1400 : 0x100800));
                gfx.fill(bx, by, bx + bw, by + 1,
                        ((int)(f * (hov ? 40 : 20)) << 24) | 0xFFEECC);
                drawBorder(gfx, bx, by, bw, bh,
                        ((int)(f * (hov ? 240 : 180)) << 24) | (hov ? 0xFFCC55 : 0xCC7733));
                gfx.fill(bx, by, bx + 3, by + bh,
                        ((int)(f * 220) << 24) | (hov ? 0xFFDD88 : 0xAA6633));
            }

            // 3. Label fades in after merge
            float textFade = Math.max(0f, (buildProg - TEXT_SHOW_AT) / (1f - TEXT_SHOW_AT));
            if (textFade > 0f) {
                int tA = (int)(textFade * fadeIn * 240);
                gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[i][0],
                        bx + bw / 2, by + (bh - 8) / 2,
                        (tA << 24) | (hov ? 0xFFFFEE : 0xE8D4B0));
            }
        }
    }

    // ── Block storm ───────────────────────────────────────────────────────────
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

            // Exact grid-cell destination
            int   gridX = b % cols;
            int   gridY = b / cols;
            float endX  = bx + gridX * BLOCK_PX + BLOCK_PX * 0.5f;
            float endY  = by + gridY * BLOCK_PX + BLOCK_PX * 0.5f;

            // Origin: random off-screen
            float ox, oy;
            switch ((int)(r2 * 4)) {
                case 0:  ox = r3 * width;          oy = -height * (0.2f + r4 * 0.8f); break;
                case 1:  ox = r3 * width;          oy =  height * (1.2f + r4 * 0.8f); break;
                case 2:  ox = -width  * (0.2f+r3); oy =  r4 * height;                 break;
                default: ox =  width  * (1.2f+r3); oy =  r4 * height;                 break;
            }

            // Bezier arc
            float midX  = (ox + endX) * 0.5f;
            float midY  = (oy + endY) * 0.5f;
            float ctrlX = midX + (r1 - 0.5f) * 140f;
            float ctrlY = midY + (r0 - 0.6f) * 100f;

            float t  = 1f - (float)Math.pow(1f - blockT, 2.6f);
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
                     (int)(curX + sz), (int)(curY + sz),
                     (alpha << 24) | col);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]
                    && pressedMs[i] < 0) {
                pressedMs[i] = System.currentTimeMillis(); return true;
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
        return (x & 0x7FFFFFFFFFFFFFFFL) / (float)0x7FFFFFFFFFFFFFFFL;
    }
}

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
 * Custom full-replacement Title Screen.
 *
 * Visuals:
 * ─ Deep star-field background with three parallax layers + bloom
 * ─ Slow aurora wave bands
 * ─ Animated glowing title "MINECRAFT" with letter-stagger drop-in
 * ─ Subtitle version string, softly pulsing
 * ─ Five game buttons (Singleplayer, Multiplayer, Options, Quit + copyright)
 *   each with multi-layer purple glow, shimmer sweep, press squish and a
 *   bright hover state
 * ─ Vignette border, edge glow, animated particle dust
 */
public class CustomTitleScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int BTN_W        = 240;
    private static final int BTN_H        = 44;
    private static final int BTN_GAP      = 10;
    private static final int TITLE_OFFSET = -100; // from centre-y

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COL_BG        = 0xFF030408;
    private static final int COL_PURPLE    = 0xFF9B72CF;
    private static final int COL_LAVENDER  = 0xFFCDA9F5;
    private static final int COL_BTN_BG    = 0xFF0B0820;
    private static final int COL_BTN_HV    = 0xFF1A1040;
    private static final int COL_BTN_TEXT  = 0xFFDDCCFF;
    private static final int COL_BTN_TEXTHV= 0xFFFFFFFF;
    private static final int[] AURORA_COLS = {
        0xFF0D0825, 0xFF16082E, 0xFF0A0620, 0xFF180A30
    };

    // ── Stars ─────────────────────────────────────────────────────────────────
    private static final int STAR_COUNT = 140;
    // [x%, y%, radius, speed, layer(0-2), brightness]
    private final float[][] stars = new float[STAR_COUNT][6];

    // ── Button descriptors ────────────────────────────────────────────────────
    // [label, id]
    private static final String[][] BUTTONS = {
        { "▶  Singleplayer",  "sp" },
        { "⚡  Multiplayer",   "mp" },
        { "⚙  Options",       "opt" },
        { "✕  Quit Game",     "quit" },
    };

    // ── State ─────────────────────────────────────────────────────────────────
    private long  startMs    = -1;
    private int   hoveredBtn = -1;
    // per-button press time (ms), -1 = not pressed
    private final long[]   pressedMs = new long[BUTTONS.length];

    // ── Constructor ───────────────────────────────────────────────────────────
    public CustomTitleScreen() {
        super(Component.literal("Title"));
        java.util.Arrays.fill(pressedMs, -1L);

        Random rng = new Random(12345);
        for (int i = 0; i < STAR_COUNT; i++) {
            stars[i][0] = rng.nextFloat();
            stars[i][1] = rng.nextFloat();
            stars[i][2] = 0.5f + rng.nextFloat() * 2.2f;
            stars[i][3] = 0.15f + rng.nextFloat() * 0.45f;
            stars[i][4] = rng.nextInt(3);
            stars[i][5] = 0.3f + rng.nextFloat() * 0.7f;
        }
    }

    @Override
    protected void init() {
        startMs = System.currentTimeMillis();
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now     = System.currentTimeMillis();
        long elapsed = startMs < 0 ? 0 : now - startMs;
        float fadeIn = Math.min(1f, elapsed / 700f);

        // Background layers
        gfx.fill(0, 0, width, height, COL_BG);
        drawAurora(gfx, now);
        drawStars(gfx, now);
        drawEdgeGlow(gfx, now);
        drawVignette(gfx);

        // Content
        drawTitle(gfx, now, fadeIn, elapsed);
        detectHover(mouseX, mouseY);
        drawButtons(gfx, now, fadeIn);
        drawFooter(gfx, fadeIn);

        // Global fade-in
        if (fadeIn < 1f) {
            gfx.fill(0, 0, width, height, (int)((1f - fadeIn) * 255) << 24);
        }

        // Trigger deferred navigation after press animation (~120ms)
        checkPressedActions(now);
    }

    // ── Background ────────────────────────────────────────────────────────────
    private void drawAurora(GuiGraphics gfx, long now) {
        float t = now / 7000f;
        int   W = width, H = height;
        int   bands = 5;
        for (int i = 0; i < bands; i++) {
            float phase  = i * (float)(Math.PI * 2 / bands);
            float yOff   = H * 0.1f + (float)Math.sin(t + phase) * H * 0.07f + i * H / (bands + 1f);
            int   bandH  = (int)(H * 0.24f);
            int   col    = AURORA_COLS[i % AURORA_COLS.length];
            for (int dy = 0; dy < bandH; dy++) {
                float edge = 1f - Math.abs((dy / (float)bandH) * 2 - 1);
                int   a    = (int)(edge * 28);
                gfx.fill(0, (int)yOff + dy, W, (int)yOff + dy + 1, (a << 24) | (col & 0x00FFFFFF));
            }
        }
    }

    private void drawStars(GuiGraphics gfx, long now) {
        float t = now / 1000f;
        for (float[] s : stars) {
            int   layer   = (int)s[4];
            float twinkle = 0.5f + 0.5f * (float)Math.sin(t * s[3] * 2 + s[1] * 10);
            float bright  = s[5] * twinkle;
            int   a       = Math.max(20, Math.min(230, (int)(bright * 255)));
            int   r       = Math.max(1, (int)s[2]);
            int   x       = (int)(s[0] * width);
            int   y       = (int)(s[1] * height);
            gfx.fill(x - r, y - r, x + r, y + r, (a << 24) | 0xBBAEFF);
            if (r >= 2 && bright > 0.65f) {
                int ba = (int)(bright * 50);
                gfx.fill(x - r*2, y - r*2, x + r*2, y + r*2, (ba << 24) | 0x9980EE);
            }
        }
    }

    /** Subtle purple edge glow that pulses. */
    private void drawEdgeGlow(GuiGraphics gfx, long now) {
        float p   = 0.4f + 0.2f * (float)Math.abs(Math.sin(now / 3000.0));
        int   gW  = (int)(width * 0.25f);
        int   H   = height;
        for (int i = 0; i < gW; i++) {
            float t = 1f - (float)i / gW;
            int   a = (int)(t * t * p * 35);
            int   c = (a << 24) | 0x8855CC;
            gfx.fill(i, 0, i + 1, H, c);
            gfx.fill(width - i - 1, 0, width - i, H, c);
        }
    }

    private void drawVignette(GuiGraphics gfx) {
        int depth = Math.min(width, height) / 3;
        for (int i = 0; i < depth; i++) {
            float t = i / (float)depth;
            int   a = (int)((1 - t) * (1 - t) * 170);
            int   c = (a << 24);
            int   W = width, H = height;
            gfx.fill(i,     i,     W - i,   i + 1,   c);
            gfx.fill(i,     H-i-1, W - i,   H - i,   c);
            gfx.fill(i,     i,     i + 1,   H - i,   c);
            gfx.fill(W-i-1, i,     W - i,   H - i,   c);
        }
    }

    // ── Title ─────────────────────────────────────────────────────────────────
    private void drawTitle(GuiGraphics gfx, long now, float fadeIn, long elapsed) {
        String title = "M I N E C R A F T";
        int    cx    = width  / 2;
        int    ty    = height / 2 + TITLE_OFFSET;

        // Per-letter stagger drop-in (first 800 ms)
        char[] letters   = title.toCharArray();
        int    totalW    = font.width(title);
        int    startX    = cx - totalW / 2;
        int    xCursor   = startX;
        for (int i = 0; i < letters.length; i++) {
            String ch    = String.valueOf(letters[i]);
            int    chW   = font.width(ch);
            long   delay = i * 45L;
            float  t     = Math.min(1f, Math.max(0f, (elapsed - delay) / 350f));
            float  ease  = 1f - (float)Math.pow(1 - t, 3);
            int    yOff  = (int)((1f - ease) * 22);
            int    a     = (int)(ease * fadeIn * 255);
            // Glow layer
            int    ga    = (int)(ease * fadeIn * 80);
            gfx.drawString(font, "§l" + ch, xCursor - 1, ty - 1 + yOff, (ga << 24) | 0xCCAEFF, false);
            gfx.drawString(font, "§l" + ch, xCursor + 1, ty + 1 + yOff, (ga << 24) | 0xCCAEFF, false);
            // Main letter
            float pulse = 0.88f + 0.12f * (float)Math.abs(Math.sin(now / 1600.0 + i * 0.4));
            int   lCol  = lerpColor(0xFFCCAAFF, 0xFFFFEEFF, pulse);
            gfx.drawString(font, "§l" + ch, xCursor, ty + yOff, (a << 24) | (lCol & 0x00FFFFFF), false);
            xCursor += chW;
        }

        // Underline bar
        float barFill = Math.min(1f, Math.max(0f, (elapsed - 400f) / 500f));
        if (barFill > 0) {
            int barW  = (int)(totalW * barFill);
            int barX  = cx - totalW / 2;
            int barY  = ty + font.lineHeight + 4;
            int barA  = (int)(fadeIn * 200);
            // glow
            for (int g = 4; g > 0; g--) {
                int ga = (int)(fadeIn * (25 / g));
                gfx.fill(barX - g*2, barY - g, barX + barW + g*2, barY + 2 + g,
                         (ga << 24) | 0x9966EE);
            }
            gfx.fill(barX, barY, barX + barW, barY + 2, (barA << 24) | 0xBB99FF);
        }

        // Subtitle
        String sub  = "Java Edition  •  " + SharedConstants.getCurrentVersion().getName();
        int    subA = (int)(fadeIn * 0.55f * 220);
        gfx.drawCenteredString(font, "§7" + sub, cx, ty + font.lineHeight + 14, (subA << 24) | 0x8877AA);
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    private void detectHover(int mx, int my) {
        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]
                    && pressedMs[i] < 0) {
                hoveredBtn = i;
            }
        }
    }

    /** Returns [x, y, w, h] for button i. */
    private int[] btnRect(int i) {
        int totalH = BUTTONS.length * BTN_H + (BUTTONS.length - 1) * BTN_GAP;
        int startY = height / 2 + TITLE_OFFSET + 70;
        int x = (width - BTN_W) / 2;
        int y = startY + i * (BTN_H + BTN_GAP);
        return new int[]{ x, y, BTN_W, BTN_H };
    }

    private void drawButtons(GuiGraphics gfx, long now, float fadeIn) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[]   r        = btnRect(i);
            boolean hov      = (i == hoveredBtn);
            boolean isPressed = pressedMs[i] >= 0;

            // Stagger fade-in
            float btnFade = Math.min(1f, Math.max(0f,
                    (float)(System.currentTimeMillis() - startMs - 200 - i * 60) / 350f));
            btnFade = easeOut(btnFade) * fadeIn;

            int bx = r[0], by = r[1], bw = r[2], bh = r[3];

            // Press squish
            if (isPressed) {
                long since = now - pressedMs[i];
                float p = Math.min(1f, since / 100f);
                by += (int)(p * 2);
            }

            // Glow halos
            float glowStr = hov ? 1f : (0.5f + 0.5f * (float)Math.abs(Math.sin(now / 1500.0 + i)));
            for (int g = 6; g > 0; g--) {
                int ga  = (int)(btnFade * glowStr * (hov ? 50 : 22) / g);
                int col = (ga << 24) | (hov ? 0xCC99FF : 0x9966EE);
                int pad = g * 5;
                gfx.fill(bx - pad, by - pad/2, bx + bw + pad, by + bh + pad/2, col);
            }

            // Shadow
            gfx.fill(bx + 3, by + 3, bx + bw + 3, by + bh + 3, (int)(btnFade * 0x55) << 24);

            // Body
            int bgCol = hov ? COL_BTN_HV : COL_BTN_BG;
            gfx.fill(bx, by, bx + bw, by + bh, bgCol);

            // Top-half inner highlight
            for (int dy = 0; dy < bh / 2; dy++) {
                float edge = 1f - (dy / (float)(bh / 2));
                int   a    = (int)(edge * (hov ? 22 : 12));
                gfx.fill(bx, by + dy, bx + bw, by + dy + 1, (a << 24) | 0xFFFFFF);
            }

            // Shimmer sweep
            if (!isPressed) {
                float sw  = (now / 2000f + i * 0.4f) % 1f;
                int   sx  = bx + (int)(sw * (bw + 60)) - 30;
                for (int si = 0; si < 28; si++) {
                    float edge2 = 1f - Math.abs(si / 14f - 1f);
                    int   sa    = (int)(edge2 * (hov ? 55 : 28));
                    gfx.fill(sx + si, by, sx + si + 1, by + bh, (sa << 24) | 0xFFFFFF);
                }
            }

            // Animated border
            int borderCol = hov
                ? 0xFFCC99FF
                : lerpColor(0xFF7755BB, 0xFFAA88EE, (float)Math.abs(Math.sin(now / 900.0 + i)));
            drawBorder(gfx, bx, by, bw, bh, borderCol);

            // Bright corners
            int cc = hov ? 0xFFEECCFF : 0xFFAA88DD;
            gfx.fill(bx,        by,        bx + 3,   by + 3,   cc);
            gfx.fill(bx+bw-3,   by,        bx + bw,  by + 3,   cc);
            gfx.fill(bx,        by+bh-3,   bx + 3,   by + bh,  cc);
            gfx.fill(bx+bw-3,   by+bh-3,   bx + bw,  by + bh,  cc);

            // Left accent bar
            gfx.fill(bx, by, bx + 3, by + bh, hov ? 0xFFCC88FF : 0xFF7744BB);

            // Label
            int   textAlpha = (int)(btnFade * 255);
            int   textCol   = (textAlpha << 24) | ((hov ? COL_BTN_TEXTHV : COL_BTN_TEXT) & 0x00FFFFFF);
            String label    = BUTTONS[i][0];
            if (hov) label = "  " + label;
            gfx.drawCenteredString(font, "§l" + label, bx + bw / 2, by + (bh - 8) / 2, textCol);
        }
    }

    private void drawFooter(GuiGraphics gfx, float fadeIn) {
        int   a   = (int)(fadeIn * 0.35f * 200);
        String s  = "Copyright Mojang AB  •  Do not distribute";
        gfx.drawCenteredString(font, "§8" + s, width / 2, height - 14, (a << 24) | 0x665577);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let ESC / ENTER navigate focused button (accessibility)
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Fire deferred navigation ~120 ms after press to show the squish. */
    private void checkPressedActions(long now) {
        for (int i = 0; i < BUTTONS.length; i++) {
            if (pressedMs[i] >= 0 && now - pressedMs[i] >= 120) {
                long t = pressedMs[i];
                pressedMs[i] = -1;
                navigate(BUTTONS[i][1]);
            }
        }
    }

    private void navigate(String id) {
        Minecraft mc = Minecraft.getInstance();
        switch (id) {
            case "sp"   -> ScreenUtil.setScreen(new SelectWorldScreen(this));   // was: null
            case "mp"   -> ScreenUtil.setScreen(new JoinMultiplayerScreen(this)); // was: null
            case "opt"  -> ScreenUtil.setScreen(new OptionsScreen(this, mc.options)); // was: null
            case "quit" -> mc.stop();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,       y,       x + w,   y + 1,   col);
        gfx.fill(x,       y+h-1,   x + w,   y + h,   col);
        gfx.fill(x,       y,       x + 1,   y + h,   col);
        gfx.fill(x+w-1,   y,       x + w,   y + h,   col);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar=(a>>16)&0xFF, ag=(a>>8)&0xFF, ab=a&0xFF;
        int br=(b>>16)&0xFF, bg=(b>>8)&0xFF, bb=b&0xFF;
        return 0xFF000000
             | ((int)(ar+(br-ar)*t)<<16)
             | ((int)(ag+(bg-ag)*t)<<8)
             |  (int)(ab+(bb-ab)*t);
    }

    private static float easeOut(float t) {
        return 1f - (float)Math.pow(1 - t, 3);
    }
}

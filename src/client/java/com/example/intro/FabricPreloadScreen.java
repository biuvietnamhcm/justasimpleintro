package com.example.intro;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 1 — Fabric Preload Screen
 *
 * Shows the Fabric logo with an animated shimmer loading bar and floating
 * particle dots. Automatically advances to VideoScreen after the bar fills.
 */
public class FabricPreloadScreen extends Screen {

    // ── Tuneable constants ───────────────────────────────────────────────────
    /** Total duration of the preload phase in milliseconds. */
    private static final long PRELOAD_DURATION_MS = 3_200;

    /** How many decorative dots float in the background. */
    private static final int DOT_COUNT = 60;

    // ── Fabric brand colours ─────────────────────────────────────────────────
    private static final int COL_BG          = 0xFF0D0D0D;
    private static final int COL_FABRIC_DARK = 0xFF1A1A2E;
    private static final int COL_ACCENT      = 0xFFDBB5FF; // soft lavender
    private static final int COL_ACCENT2     = 0xFF9B72CF; // Fabric purple
    private static final int COL_BAR_BG      = 0xFF222233;
    private static final int COL_BAR_FILL    = 0xFF9B72CF;
    private static final int COL_BAR_SHINE   = 0xFFDDB9FF;
    private static final int COL_TEXT        = 0xFFEEEEEE;
    private static final int COL_TEXT_DIM    = 0xFF888899;

    // ── State ────────────────────────────────────────────────────────────────
    private long  startTime   = -1;
    private float progress    = 0f; // 0 → 1
    private boolean advanced  = false;

    /** Pseudo-random dot positions (x%, y%, size, speed, phase). */
    private final float[][] dots = new float[DOT_COUNT][5];

    // ── Constructor ──────────────────────────────────────────────────────────
    public FabricPreloadScreen() {
        super(Component.literal("Loading…"));
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < DOT_COUNT; i++) {
            dots[i][0] = rng.nextFloat();          // x %
            dots[i][1] = rng.nextFloat();          // y %
            dots[i][2] = 1 + rng.nextFloat() * 3; // radius px
            dots[i][3] = 0.3f + rng.nextFloat();  // drift speed
            dots[i][4] = rng.nextFloat() * 6.28f; // phase offset
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        startTime = System.currentTimeMillis();
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Render ───────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now      = System.currentTimeMillis();
        long elapsed  = (startTime < 0) ? 0 : now - startTime;
        float rawProg = Math.min(1f, (float) elapsed / PRELOAD_DURATION_MS);
        // ease-out cubic for a satisfying deceleration
        progress = easeOutCubic(rawProg);

        int W = width;
        int H = height;

        // ── Background ───────────────────────────────────────────────────
        gfx.fill(0, 0, W, H, COL_BG);
        drawGradientOverlay(gfx, W, H, now);
        drawDots(gfx, W, H, now);

        // ── Centre panel ─────────────────────────────────────────────────
        int panelW  = Math.min(420, W - 40);
        int panelH  = 220;
        int panelX  = (W - panelW) / 2;
        int panelY  = (H - panelH) / 2;
        drawPanel(gfx, panelX, panelY, panelW, panelH, now);

        // ── Fabric logo text ──────────────────────────────────────────────
        String logoLine1 = "fabric";
        String logoLine2 = "mod loader";
        int lx = W / 2;
        int ly = panelY + 30;
        gfx.drawCenteredString(font, "§d§l" + logoLine1.toUpperCase(), lx, ly,      0xFFDDBBFF);
        gfx.drawCenteredString(font, "§7"   + logoLine2.toUpperCase(), lx, ly + 20, 0xFF9988BB);

        // ── Loading bar ───────────────────────────────────────────────────
        int barW   = panelW - 60;
        int barH   = 8;
        int barX   = panelX + 30;
        int barY   = panelY + panelH - 60;
        drawLoadingBar(gfx, barX, barY, barW, barH, now);

        // ── Status text ───────────────────────────────────────────────────
        String status = getStatusText(rawProg);
        gfx.drawCenteredString(font, "§7" + status, W / 2, barY + 18, COL_TEXT_DIM);

        // ── Percentage ────────────────────────────────────────────────────
        String pct = (int)(progress * 100) + "%";
        gfx.drawCenteredString(font, "§f" + pct, W / 2, barY - 14, COL_TEXT);

        // ── Advance when done ─────────────────────────────────────────────
        if (rawProg >= 1f && !advanced) {
            advanced = true;
            ScreenUtil.setScreen(new VideoScreen());
        }
    }

    // ── Drawing helpers ──────────────────────────────────────────────────────

    /** Subtle radial gradient overlay for depth. */
    private void drawGradientOverlay(GuiGraphics gfx, int W, int H, long now) {
        int cx = W / 2, cy = H / 2;
        int r  = Math.max(W, H) / 2;
        // cheap approximation: concentric rectangles with decreasing alpha
        for (int i = 0; i < 8; i++) {
            float t   = i / 8f;
            int   a   = (int)(40 * (1 - t));
            int   col = (a << 24) | 0x6040A0;
            int   off = (int)(t * r * 0.6f);
            gfx.fill(cx - off, cy - off, cx + off, cy + off, col);
        }
    }

    /** Floating decorative dots. */
    private void drawDots(GuiGraphics gfx, int W, int H, long now) {
        float t = now / 1000f;
        for (float[] d : dots) {
            float x   = d[0] * W;
            float y   = (float)(d[1] * H + Math.sin(t * d[3] + d[4]) * 8);
            float rad = d[2];
            float alpha = 0.08f + 0.06f * (float)Math.sin(t * d[3] * 0.7 + d[4]);
            int   a   = Math.max(0, Math.min(255, (int)(alpha * 255)));
            int   col = (a << 24) | 0xAA88FF;
            int   ix  = (int) x;
            int   iy  = (int) y;
            int   ir  = Math.max(1, (int) rad);
            gfx.fill(ix - ir, iy - ir, ix + ir, iy + ir, col);
        }
    }

    /** Rounded-ish panel behind everything. */
    private void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, long now) {
        // shadow
        gfx.fill(x + 4, y + 4, x + w + 4, y + h + 4, 0x55000000);
        // body
        gfx.fill(x, y, x + w, y + h, 0xEE111122);
        // border shimmer
        float phase = (now / 1200f) % 1f;
        int   bCol  = lerpColor(0xFF3A2B6E, 0xFF9B72CF, (float) Math.abs(Math.sin(phase * Math.PI)));
        drawBorder(gfx, x, y, w, h, bCol);
    }

    /** Animated loading bar with shimmer. */
    private void drawLoadingBar(GuiGraphics gfx, int x, int y, int w, int h, long now) {
        // background track
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF333355);
        gfx.fill(x, y, x + w, y + h, COL_BAR_BG);

        // filled portion
        int fillW = (int)(w * progress);
        if (fillW > 0) {
            gfx.fill(x, y, x + fillW, y + h, COL_BAR_FILL);

            // shimmer sweep
            float shimmerX = (now % 1200) / 1200f;
            int   shimmerPos = x + (int)(shimmerX * fillW);
            int   shimmerW   = Math.min(30, fillW);
            gfx.fill(shimmerPos - shimmerW / 2, y,
                     shimmerPos + shimmerW / 2, y + h, 0x55FFFFFF);

            // bright leading edge
            gfx.fill(x + fillW - 2, y, x + fillW, y + h, COL_BAR_SHINE);
        }

        // corner dots
        gfx.fill(x - 3, y - 1, x,     y + h + 1, 0xFF9B72CF);
        gfx.fill(x + w, y - 1, x + w + 3, y + h + 1, 0xFF444466);
    }

    /** 1-pixel border. */
    private void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,         y,         x + w,     y + 1,     col); // top
        gfx.fill(x,         y + h - 1, x + w,     y + h,     col); // bottom
        gfx.fill(x,         y,         x + 1,     y + h,     col); // left
        gfx.fill(x + w - 1, y,         x + w,     y + h,     col); // right
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private static float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int)(ar + (br - ar) * t);
        int rg = (int)(ag + (bg - ag) * t);
        int rb = (int)(ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    private String getStatusText(float p) {
        if (p < 0.20f) return "Initialising Fabric runtime…";
        if (p < 0.45f) return "Loading mixin transformers…";
        if (p < 0.70f) return "Scanning mod dependencies…";
        if (p < 0.90f) return "Preparing resource packs…";
        return "Almost ready…";
    }
}

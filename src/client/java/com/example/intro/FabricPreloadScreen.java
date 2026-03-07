package com.example.intro;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FabricPreloadScreen extends Screen {

    public static boolean s_shown = false;

    private static final long PRELOAD_DURATION_MS = 3_200;

    // ── Block world ───────────────────────────────────────────────────────────
    private static final int WORLD_COLS   = 72;
    private static final int WORLD_HEIGHT = 10;
    private static final int BLOCK_PX     = 12;

    private static final int[] BLOCK_PALETTE = {
        0xFF4A3020, 0xFF555555, 0xFF3A7A1A, 0xFF4A8820,
        0xFF1A1A2E, 0xFF6A4A22, 0xFF334422,
    };

    private final int[]   terrain   = new int[WORLD_COLS];
    private final int[][] blockType = new int[WORLD_COLS][WORLD_HEIGHT];

    // ── Particles only (torches removed) ─────────────────────────────────────
    private static final int    PARTICLE_COUNT = 130;
    private final        float[][] particles   = new float[PARTICLE_COUNT][6];

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final int COL_SKY_TOP   = 0xFF050810;
    private static final int COL_SKY_MID   = 0xFF0A1020;
    private static final int COL_SKY_BOT   = 0xFF111830;
    private static final int COL_BAR_BG    = 0xFF1A1A2E;
    private static final int COL_BAR_FILL  = 0xFF9B72CF;
    private static final int COL_BAR_SHINE = 0xFFDDB9FF;
    private static final int COL_TEXT      = 0xFFEEEEEE;
    private static final int COL_TEXT_DIM  = 0xFF888899;

    // ── State ─────────────────────────────────────────────────────────────────
    private long    startTime = -1;
    private float   progress  = 0f;
    private boolean advanced  = false;

    // ── Constructor ───────────────────────────────────────────────────────────
    public FabricPreloadScreen() {
        super(Component.literal("Loading\u2026"));
        java.util.Random rng = new java.util.Random(20240101L);

        // Terrain
        int h = WORLD_HEIGHT / 2;
        for (int c = 0; c < WORLD_COLS; c++) {
            h = Math.max(2, Math.min(WORLD_HEIGHT - 1, h + (int)(rng.nextFloat() * 3) - 1));
            terrain[c] = h;
            for (int row = 0; row < h; row++) {
                if      (row == h - 1)              blockType[c][row] = 2;
                else if (row >= h - 3)              blockType[c][row] = 0;
                else if (rng.nextFloat() < 0.07f)   blockType[c][row] = 4;
                else                                blockType[c][row] = 1;
            }
        }

        // Particles
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles[i][0] = rng.nextFloat();
            particles[i][1] = rng.nextFloat();
            particles[i][2] = 0.018f + rng.nextFloat() * 0.055f;
            particles[i][3] = rng.nextFloat() < 0.3f ? 2 : 1;
            particles[i][4] = 0.25f + rng.nextFloat() * 0.75f;
            particles[i][5] = rng.nextFloat() < 0.22f ? 1 : 0;
        }
    }

    @Override
    protected void init() {
        s_shown   = true;
        startTime = System.currentTimeMillis();
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long  now     = System.currentTimeMillis();
        long  elapsed = (startTime < 0) ? 0 : now - startTime;
        float rawProg = Math.min(1f, (float) elapsed / PRELOAD_DURATION_MS);
        progress      = easeOutCubic(rawProg);

        int W = width, H = height;
        float sec = now / 1000f;

        drawSkyGradient(gfx, W, H);
        drawStars(gfx, W, H, sec);
        drawBlockWorld(gfx, W, H, sec);
        drawParticles(gfx, W, H, sec);
        drawScanLines(gfx, W, H);
        drawVignette(gfx, W, H);

        // ── Centre panel ─────────────────────────────────────────────────────
        int panelW = Math.min(400, W - 60);
        int panelH = 200;
        int panelX = (W - panelW) / 2;
        int panelY = (H - panelH) / 2 - 8;
        drawPanel(gfx, panelX, panelY, panelW, panelH, now);

        int lx = W / 2, ly = panelY + 26;
        if (isFontReady()) {
            gfx.drawCenteredString(font, "FABRIC",     lx, ly,      0xFFDDBBFF);
            gfx.drawCenteredString(font, "MOD LOADER", lx, ly + 18, 0xFF9988BB);
        }

        gfx.fill(panelX + 20, ly + 34, panelX + panelW - 20, ly + 35, 0x449B72CF);

        int barW = panelW - 60, barH = 8;
        int barX = panelX + 30, barY = panelY + panelH - 56;
        drawLoadingBar(gfx, barX, barY, barW, barH, now);

        if (isFontReady()) {
            gfx.drawCenteredString(font, getStatusText(rawProg), W / 2, barY + 16, COL_TEXT_DIM);
            gfx.drawCenteredString(font, (int)(progress * 100) + "%", W / 2, barY - 13, COL_TEXT);
        }

        drawFooter(gfx, W, H, now);

        if (rawProg >= 1f && !advanced) {
            advanced = true;
            ScreenUtil.setScreen(new VideoScreen());
        }
    }

    // =========================================================================
    // Background layers
    // =========================================================================

    private void drawSkyGradient(GuiGraphics gfx, int W, int H) {
        for (int i = 0; i < 40; i++) {
            float t  = i / 40f;
            int   y0 = (int)(t * H), y1 = (int)((i + 1f) / 40f * H);
            int   c  = (t < 0.5f)
                     ? lerpColor(COL_SKY_TOP, COL_SKY_MID, t * 2f)
                     : lerpColor(COL_SKY_MID, COL_SKY_BOT, (t - 0.5f) * 2f);
            gfx.fill(0, y0, W, y1, 0xFF000000 | c);
        }
    }

    private void drawStars(GuiGraphics gfx, int W, int H, float sec) {
        for (int i = 0; i < 180; i++) {
            float rx = hash(i * 3L);
            float ry = hash(i * 3L + 1) * 0.58f;
            float rp = hash(i * 3L + 2);
            float tw = 0.3f + 0.7f * (float)Math.pow(
                    Math.max(0f, (float)Math.sin(sec * (0.7f + rp * 1.6f) + rp * 6.28f)), 2);
            int a  = (int)(tw * (rp < 0.08f ? 230 : 130));
            int sz = rp < 0.04f ? 2 : 1;
            int x  = (int)(rx * W), y = (int)(ry * H);
            gfx.fill(x, y, x + sz, y + sz, (a << 24) | 0xCCBBFF);
        }
    }

    private void drawBlockWorld(GuiGraphics gfx, int W, int H, float sec) {
        int   baseY  = worldBaseY(H);
        float scroll = (sec * 20f) % (BLOCK_PX * WORLD_COLS);

        for (int c = 0; c < WORLD_COLS + 2; c++) {
            int sx = (int)(c * BLOCK_PX - scroll);
            if (sx + BLOCK_PX < 0 || sx > W) continue;

            int col = ((c % WORLD_COLS) + WORLD_COLS) % WORLD_COLS;
            int h   = terrain[col];

            for (int row = 0; row < h; row++) {
                int by = baseY - (row + 1) * BLOCK_PX;
                int bc = BLOCK_PALETTE[Math.min(blockType[col][row], BLOCK_PALETTE.length - 1)];
                int fc = (row == h - 1) ? lighten(bc, 0.28f) : bc;
                gfx.fill(sx, by, sx + BLOCK_PX, by + BLOCK_PX, 0xFF000000 | fc);
                gfx.fill(sx + BLOCK_PX - 2, by, sx + BLOCK_PX, by + BLOCK_PX, 0x2A000000);
                gfx.fill(sx, by + BLOCK_PX - 2, sx + BLOCK_PX, by + BLOCK_PX, 0x1A000000);
                gfx.fill(sx, by, sx + BLOCK_PX, by + 1, 0x15FFFFFF);
                gfx.fill(sx, by, sx + 1, by + BLOCK_PX, 0x15FFFFFF);
            }

            if (baseY < H) gfx.fill(sx, baseY, sx + BLOCK_PX, H, 0xFF130C00);
        }
    }

    private void drawParticles(GuiGraphics gfx, int W, int H, float sec) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float[] p = particles[i];
            p[1] = (p[1] + p[2] * 0.016f) % 1f;
            float x  = p[0] * W + (float)Math.sin(sec * 0.35f + i * 0.9f) * 4f;
            float y  = p[1] * H;
            float tw = 0.5f + 0.5f * (float)Math.sin(sec * 1.2f + i * 0.65f);
            int   a  = (int)(p[4] * tw * (p[5] == 1f ? 175 : 100));
            int   col= (p[5] == 1f) ? lerpColor(0xFF6600, 0xFFCC22, tw) : 0xBBBBBB;
            int   sz = (int) p[3];
            gfx.fill((int)x - sz, (int)y - sz, (int)x + sz, (int)y + sz, (a << 24) | col);
        }
    }

    private void drawScanLines(GuiGraphics gfx, int W, int H) {
        for (int y = 0; y < H; y += 2) gfx.fill(0, y, W, y + 1, 0x09000000);
    }

    private void drawVignette(GuiGraphics gfx, int W, int H) {
        int depth = Math.min(W, H) / 3;
        for (int i = 0; i < depth; i++) {
            float t = 1f - i / (float) depth;
            int   a = (int)(t * t * 155);
            gfx.fill(i,     i,     W - i, i + 1, a << 24);
            gfx.fill(i,     H-i-1, W - i, H - i, a << 24);
            gfx.fill(i,     i,     i + 1, H - i, a << 24);
            gfx.fill(W-i-1, i,     W - i, H - i, a << 24);
        }
    }

    // =========================================================================
    // Panel + bar
    // =========================================================================

    private void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, long now) {
        gfx.fill(x + 5, y + 6, x + w + 5, y + h + 6, 0x77000000);
        gfx.fill(x, y, x + w, y + h, 0xF00B0B18);
        int bCol = lerpColor(0xFF3A2B6E, 0xFF9B72CF,
                (float)Math.abs(Math.sin((now / 1500f) * Math.PI)));
        drawBorderLine(gfx, x, y, w, h, bCol);
        gfx.fill(x + 1, y + 1, x + w - 1, y + 2, 0x1AFFFFFF);
    }

    private void drawLoadingBar(GuiGraphics gfx, int x, int y, int w, int h, long now) {
        gfx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2A2A44);
        gfx.fill(x, y, x + w, y + h, COL_BAR_BG);

        int fillW = (int)(w * progress);
        if (fillW > 0) {
            for (int px = 0; px < fillW; px++) {
                float t   = (float) px / Math.max(1, fillW);
                int   col = lerpColor(0xFF6A44AA, COL_BAR_FILL, t);
                gfx.fill(x + px, y, x + px + 1, y + h, 0xFF000000 | col);
            }
            float sw  = (now % 1500) / 1500f;
            int   shP = x + (int)(sw * fillW);
            int   shW = Math.min(44, fillW);
            for (int si = 0; si < shW; si++) {
                float e = 1f - Math.abs(si / (shW * 0.5f) - 1f);
                gfx.fill(shP - shW / 2 + si, y, shP - shW / 2 + si + 1, y + h,
                         ((int)(e * 55) << 24) | 0xFFFFFF);
            }
            gfx.fill(x + fillW - 2, y, x + fillW, y + h, COL_BAR_SHINE);
        }

        gfx.fill(x - 3,     y - 1, x,         y + h + 1, 0xFF9B72CF);
        gfx.fill(x + w,     y - 1, x + w + 3, y + h + 1, 0xFF444466);
    }

    // =========================================================================
    // Footer
    // =========================================================================

    private void drawFooter(GuiGraphics gfx, int W, int H, long now) {
        int fh = 20, fy = H - fh;
        gfx.fill(0, fy, W, H, 0xCC04040C);
        int lineCol = lerpColor(0xFF1E1050, 0xFF6B44AA,
                (float)Math.abs(Math.sin((now / 2200f) * Math.PI)));
        gfx.fill(0, fy, W, fy + 1, 0xFF000000 | lineCol);
        gfx.fill(0,     fy, 2, H, 0xFF6B44AA);
        gfx.fill(W - 2, fy, W, H, 0xFF6B44AA);

        if (isFontReady()) {
            float pulse  = 0.60f + 0.40f * (float)Math.sin(now / 1900f);
            int   tAlpha = (int)(pulse * 195);
            gfx.drawCenteredString(font, "(C) 2024  biuvietnam  |  All rights reserved",
                    W / 2, fy + 5, (tAlpha << 24) | 0x9988CC);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private boolean isFontReady() { return font != null; }

    private int worldBaseY(int H) { return H - (int)(H * 0.04f); }

    private void drawBorderLine(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,         y,         x + w,     y + 1,     col);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     col);
        gfx.fill(x,         y,         x + 1,     y + h,     col);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     col);
    }

    private static float easeOutCubic(float t) {
        return 1f - (float)Math.pow(1 - t, 3);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int)(ar + (br - ar) * t) << 16)
             | ((int)(ag + (bg - ag) * t) <<  8)
             |  (int)(ab + (bb - ab) * t);
    }

    private static int lighten(int col, float amt) {
        int r = Math.min(255, (int)(((col >> 16) & 0xFF) * (1 + amt)));
        int g = Math.min(255, (int)(((col >>  8) & 0xFF) * (1 + amt)));
        int b = Math.min(255, (int)(( col        & 0xFF) * (1 + amt)));
        return (r << 16) | (g << 8) | b;
    }

    private static float hash(long seed) {
        long x = seed ^ (seed >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (x & 0x7FFFFFFFFFFFFFFFL) / (float)0x7FFFFFFFFFFFFFFFL;
    }

    private String getStatusText(float p) {
        if (p < 0.20f) return "Initialising Fabric runtime\u2026";
        if (p < 0.45f) return "Loading mixin transformers\u2026";
        if (p < 0.70f) return "Scanning mod dependencies\u2026";
        if (p < 0.90f) return "Preparing resource packs\u2026";
        return "Almost ready\u2026";
    }
}
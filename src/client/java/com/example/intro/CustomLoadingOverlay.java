package com.example.intro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;

import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;

public class CustomLoadingOverlay extends Overlay {

    private final Minecraft                     mc;
    private final ReloadInstance                reload;
    private final Consumer<Optional<Throwable>> onFinish;

    private float   displayProgress = 0f;
    private float   fadeAlpha       = 1f;
    private boolean reloadDone      = false;
    private boolean finishing       = false;

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

    // =========================================================================
    // Pixel font — int[][] per glyph (7 rows × int[1] of 5-bit mask)
    // =========================================================================
    private static final int PIXEL_SCALE = 2;

    private static final int[][] G_F    = {{0b11111},{0b10000},{0b11110},{0b10000},{0b10000},{0b10000},{0b10000}};
    private static final int[][] G_A    = {{0b01110},{0b10001},{0b10001},{0b11111},{0b10001},{0b10001},{0b10001}};
    private static final int[][] G_B    = {{0b11110},{0b10001},{0b10001},{0b11110},{0b10001},{0b10001},{0b11110}};
    private static final int[][] G_R    = {{0b11110},{0b10001},{0b10001},{0b11110},{0b10100},{0b10010},{0b10001}};
    private static final int[][] G_I    = {{0b11111},{0b00100},{0b00100},{0b00100},{0b00100},{0b00100},{0b11111}};
    private static final int[][] G_C    = {{0b01110},{0b10001},{0b10000},{0b10000},{0b10000},{0b10001},{0b01110}};
    private static final int[][] G_L    = {{0b10000},{0b10000},{0b10000},{0b10000},{0b10000},{0b10000},{0b11111}};
    private static final int[][] G_O    = {{0b01110},{0b10001},{0b10001},{0b10001},{0b10001},{0b10001},{0b01110}};
    private static final int[][] G_D    = {{0b11100},{0b10010},{0b10001},{0b10001},{0b10001},{0b10010},{0b11100}};
    private static final int[][] G_N    = {{0b10001},{0b11001},{0b10101},{0b10011},{0b10001},{0b10001},{0b10001}};
    private static final int[][] G_G    = {{0b01110},{0b10001},{0b10000},{0b10111},{0b10001},{0b10001},{0b01111}};
    private static final int[][] G_DOT  = {{0b00000},{0b00000},{0b00000},{0b00000},{0b00000},{0b01100},{0b01100}};
    private static final int[][] G_SPC  = {{0b00000},{0b00000},{0b00000},{0b00000},{0b00000},{0b00000},{0b00000}};
    private static final int[][] G_PCT  = {{0b11000},{0b11001},{0b00010},{0b00100},{0b01000},{0b10011},{0b00011}};
    private static final int[][] G_PIPE = {{0b00100},{0b00100},{0b00100},{0b00100},{0b00100},{0b00100},{0b00100}};
    private static final int[][] G_COPY = {{0b01110},{0b10001},{0b10110},{0b10100},{0b10110},{0b10001},{0b01110}};

    private static final int[][][] G_DIGITS = {
        {{0b01110},{0b10001},{0b10011},{0b10101},{0b11001},{0b10001},{0b01110}}, // 0
        {{0b00100},{0b01100},{0b00100},{0b00100},{0b00100},{0b00100},{0b01110}}, // 1
        {{0b01110},{0b10001},{0b00001},{0b00110},{0b01000},{0b10000},{0b11111}}, // 2
        {{0b11111},{0b00001},{0b00010},{0b00110},{0b00001},{0b10001},{0b01110}}, // 3
        {{0b00010},{0b00110},{0b01010},{0b10010},{0b11111},{0b00010},{0b00010}}, // 4
        {{0b11111},{0b10000},{0b11110},{0b00001},{0b00001},{0b10001},{0b01110}}, // 5
        {{0b01110},{0b10000},{0b10000},{0b11110},{0b10001},{0b10001},{0b01110}}, // 6
        {{0b11111},{0b00001},{0b00010},{0b00100},{0b01000},{0b01000},{0b01000}}, // 7
        {{0b01110},{0b10001},{0b10001},{0b01110},{0b10001},{0b10001},{0b01110}}, // 8
        {{0b01110},{0b10001},{0b10001},{0b01111},{0b00001},{0b00001},{0b01110}}, // 9
    };

    private static final int[][] G_BSMALL = {{0b10000},{0b10000},{0b11100},{0b10010},{0b10010},{0b10010},{0b11100}};
    private static final int[][] G_ISMALL = {{0b00100},{0b00000},{0b00100},{0b00100},{0b00100},{0b00100},{0b00110}};
    private static final int[][] G_USMALL = {{0b00000},{0b00000},{0b10010},{0b10010},{0b10010},{0b10010},{0b01110}};
    private static final int[][] G_VSMALL = {{0b00000},{0b00000},{0b10001},{0b10001},{0b10001},{0b01010},{0b00100}};
    private static final int[][] G_ESMALL = {{0b00000},{0b00000},{0b01110},{0b10001},{0b11111},{0b10000},{0b01110}};
    private static final int[][] G_TSMALL = {{0b00000},{0b01110},{0b00100},{0b00100},{0b00100},{0b00100},{0b00010}};
    private static final int[][] G_NSMALL = {{0b00000},{0b00000},{0b11010},{0b10110},{0b10010},{0b10010},{0b10010}};
    private static final int[][] G_ASMALL = {{0b00000},{0b00000},{0b01100},{0b10010},{0b11110},{0b10010},{0b10010}};
    private static final int[][] G_MSMALL = {{0b00000},{0b00000},{0b10101},{0b11111},{0b10101},{0b10001},{0b10001}};

    // ── Constructor ───────────────────────────────────────────────────────────
    public CustomLoadingOverlay(
            Minecraft mc,
            ReloadInstance reload,
            Consumer<Optional<Throwable>> onFinish,
            boolean fadeIn) {

        this.mc       = mc;
        this.reload   = reload;
        this.onFinish = onFinish;

        Random rng = new Random(20240101L);

        // Terrain
        int h = WORLD_HEIGHT / 2;
        for (int c = 0; c < WORLD_COLS; c++) {
            h = Mth.clamp(h + (int)(rng.nextFloat() * 3) - 1, 2, WORLD_HEIGHT - 1);
            terrain[c] = h;
            for (int row = 0; row < h; row++) {
                if      (row == h - 1)            blockType[c][row] = 2;
                else if (row >= h - 3)            blockType[c][row] = 0;
                else if (rng.nextFloat() < 0.07f) blockType[c][row] = 4;
                else                              blockType[c][row] = 1;
            }
        }

        // Particles only — no torches
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
    public boolean isPauseScreen() { return true; }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long  now = System.currentTimeMillis();
        float sec = now / 1000f;

        int W = mc.getWindow().getGuiScaledWidth();
        int H = mc.getWindow().getGuiScaledHeight();

        // Progress
        float realProgress = (float) reload.getActualProgress();
        displayProgress = Math.max(displayProgress,
                displayProgress + (realProgress - displayProgress) * 0.1f);
        displayProgress = Mth.clamp(displayProgress, 0f, 1f);

        if (!reloadDone && reload.isDone()) {
            reloadDone      = true;
            displayProgress = 1f;
        }
        if (reloadDone && !finishing) {
            finishing = true;
            try {
                reload.checkExceptions();
                onFinish.accept(Optional.empty());
            } catch (Throwable t) {
                onFinish.accept(Optional.of(t));
            }
        }
        if (finishing) {
            fadeAlpha -= delta * 0.04f;
            if (fadeAlpha <= 0f) { mc.setOverlay(null); return; }
        }

        // Background — no torch blooms or flames
        drawSkyGradient(gfx, W, H);
        drawStars(gfx, W, H, sec);
        drawBlockWorld(gfx, W, H, sec);
        drawParticles(gfx, W, H, sec);
        drawScanLines(gfx, W, H);
        drawVignette(gfx, W, H);

        // ── Panel ─────────────────────────────────────────────────────────────
        int panelW = Math.min(420, W - 60);
        int panelH = 210;
        int panelX = (W - panelW) / 2;
        int panelY = (H - panelH) / 2 - 8;
        drawPanel(gfx, panelX, panelY, panelW, panelH, now);

        // ── "FABRIC" logo — 2× scale (smaller) ───────────────────────────────
        int logoScale   = 2;                              // was 4, now 2
        int[][][] logoGlyphs = { G_F, G_A, G_B, G_R, G_I, G_C };
        int glyphW      = 5 * logoScale;
        int glyphH      = 7 * logoScale;
        int logoSpacing = logoScale;
        int logoTotalW  = logoGlyphs.length * glyphW + (logoGlyphs.length - 1) * logoSpacing;
        int lx          = (W - logoTotalW) / 2;
        int ly          = panelY + 20;
        for (int gi = 0; gi < logoGlyphs.length; gi++) {
            float shimmer  = (float)Math.sin(sec * 2.2f + gi * 0.55f) * 0.5f + 0.5f;
            int   glyphCol = lerpColor(0xDDBBFF, 0xFFEEFF, shimmer);
            drawGlyph(gfx, logoGlyphs[gi],
                      lx + gi * (glyphW + logoSpacing), ly,
                      logoScale, 0xFF000000 | glyphCol);
        }

        // ── "LOADING..." subtitle — 1× scale (smaller) ───────────────────────
        int subScale   = 1;                               // was 2, now 1
        int subGlyphW  = 5 * subScale;
        int subSpacing = subScale;
        int dotCount   = (int)((now / 500) % 4);
        int[][][] baseGlyphs = { G_L, G_O, G_A, G_D, G_I, G_N, G_G };
        int subSlots   = baseGlyphs.length + (dotCount > 0 ? 1 + dotCount : 0);
        int subTotalW  = subSlots * subGlyphW + (subSlots - 1) * subSpacing;
        int sx         = (W - subTotalW) / 2;
        int sy         = ly + glyphH + 8;
        for (int gi = 0; gi < baseGlyphs.length; gi++) {
            drawGlyph(gfx, baseGlyphs[gi],
                      sx + gi * (subGlyphW + subSpacing), sy,
                      subScale, 0xFF9988BB);
        }
        if (dotCount > 0) {
            int base = baseGlyphs.length + 1;
            for (int di = 0; di < dotCount; di++) {
                float dotFade = (di == dotCount - 1) ? Math.min(1f, (now % 500) / 300f) : 1f;
                int   da      = (int)(dotFade * 180);
                drawGlyph(gfx, G_DOT,
                          sx + (base + di) * (subGlyphW + subSpacing), sy,
                          subScale, (da << 24) | 0x9988BB);
            }
        }

        // ── Separator ─────────────────────────────────────────────────────────
        int sepY = sy + 7 * subScale + 14;               // extra gap pushed down
        gfx.fill(panelX + 20, sepY, panelX + panelW - 20, sepY + 1, 0x449B72CF);

        // ── Percentage counter — 2× scale, pushed down ────────────────────────
        int pct        = Math.min((int)(displayProgress * 100), 100);
        int pctScale   = 2;
        int pctGlyphW  = 5 * pctScale;
        int pctSpacing = pctScale;
        String pctStr  = pct + "%";
        int[][][] pctGlyphs = new int[pctStr.length()][][];
        for (int i = 0; i < pctStr.length(); i++) {
            char ch = pctStr.charAt(i);
            pctGlyphs[i] = (ch >= '0' && ch <= '9') ? G_DIGITS[ch - '0'] : G_PCT;
        }
        int pctTotalW = pctGlyphs.length * pctGlyphW + (pctGlyphs.length - 1) * pctSpacing;
        int px0       = (W - pctTotalW) / 2;

        // ── Loading bar — placed below percentage, further down ───────────────
        int barW = panelW - 60, barH = 10;
        int barX = panelX + 30;
        int barY = sepY + 36;                            // was sepY+12, now +36
        int py0  = barY - 7 * pctScale - 8;             // percentage sits just above bar

        int pctCol = lerpColor(0x9B72CF, 0xEEEEEE, displayProgress);
        for (int gi = 0; gi < pctGlyphs.length; gi++) {
            drawGlyph(gfx, pctGlyphs[gi],
                      px0 + gi * (pctGlyphW + pctSpacing), py0,
                      pctScale, 0xFF000000 | pctCol);
        }

        drawLoadingBar(gfx, barX, barY, barW, barH, now);

        // Footer
        drawFooter(gfx, W, H, now);

        // Fade
        if (fadeAlpha < 1f) {
            gfx.fill(0, 0, W, H, (int)((1f - fadeAlpha) * 255) << 24);
        }
    }

    // =========================================================================
    // Pixel glyph renderer
    // =========================================================================

    private void drawGlyph(GuiGraphics gfx, int[][] glyph, int x, int y, int scale, int colour) {
        for (int row = 0; row < glyph.length; row++) {
            int bits = glyph[row][0];
            for (int col = 0; col < 5; col++) {
                if (((bits >> (4 - col)) & 1) == 1) {
                    int px = x + col * scale;
                    int py = y + row * scale;
                    gfx.fill(px, py, px + scale, py + scale, colour);
                }
            }
        }
    }

    // =========================================================================
    // Background layers (torches removed)
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
            gfx.fill((int)(rx * W), (int)(ry * H),
                     (int)(rx * W) + sz, (int)(ry * H) + sz, (a << 24) | 0xCCBBFF);
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
        int fillW = (int)(w * displayProgress);
        if (fillW > 0) {
            for (int px = 0; px < fillW; px++) {
                int col = lerpColor(0xFF6A44AA, COL_BAR_FILL, (float) px / Math.max(1, fillW));
                gfx.fill(x + px, y, x + px + 1, y + h, 0xFF000000 | col);
            }
            float sw  = (now % 1500) / 1500f;
            int   shP = x + (int)(sw * fillW);
            int   shW = Math.min(44, fillW);
            for (int si = 0; si < shW; si++) {
                float e = 1f - Math.abs(si / (shW * 0.5f) - 1f);
                gfx.fill(shP - shW/2 + si, y, shP - shW/2 + si + 1, y + h,
                         ((int)(e * 55) << 24) | 0xFFFFFF);
            }
            gfx.fill(x + fillW - 2, y, x + fillW, y + h, COL_BAR_SHINE);
        }
        gfx.fill(x - 3, y - 1, x,         y + h + 1, 0xFF9B72CF);
        gfx.fill(x + w, y - 1, x + w + 3, y + h + 1, 0xFF444466);
    }

    // =========================================================================
    // Footer
    // =========================================================================

    private void drawFooter(GuiGraphics gfx, int W, int H, long now) {
        int fh = 22, fy = H - fh;
        gfx.fill(0, fy, W, H, 0xCC04040C);
        int lineCol = lerpColor(0xFF1E1050, 0xFF6B44AA,
                (float)Math.abs(Math.sin((now / 2200f) * Math.PI)));
        gfx.fill(0, fy, W, fy + 1, 0xFF000000 | lineCol);
        gfx.fill(0,     fy, 2, H,  0xFF6B44AA);
        gfx.fill(W - 2, fy, W, H,  0xFF6B44AA);

        int[][][] footerGlyphs = {
            G_COPY, G_SPC,
            G_DIGITS[2], G_DIGITS[0], G_DIGITS[2], G_DIGITS[4],
            G_SPC, G_SPC,
            G_BSMALL, G_ISMALL, G_USMALL, G_VSMALL, G_ISMALL,
            G_ESMALL, G_TSMALL, G_NSMALL, G_ASMALL, G_MSMALL,
            G_SPC, G_PIPE, G_SPC,
            G_A, G_L, G_L
        };

        int fScale   = 1;
        int fGlyphW  = 5 * fScale;
        int fSpacing = fScale;
        int fTotalW  = footerGlyphs.length * (fGlyphW + fSpacing) - fSpacing;
        int fx0      = (W - fTotalW) / 2;
        int fy0      = fy + (fh - 7 * fScale) / 2;

        float pulse  = 0.55f + 0.45f * (float)Math.sin(now / 1900f);
        int   tAlpha = (int)(pulse * 200);

        for (int gi = 0; gi < footerGlyphs.length; gi++) {
            drawGlyph(gfx, footerGlyphs[gi],
                      fx0 + gi * (fGlyphW + fSpacing), fy0,
                      fScale, (tAlpha << 24) | 0x9988CC);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private int worldBaseY(int H) { return H - (int)(H * 0.04f); }

    private void drawBorderLine(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,         y,         x + w,     y + 1,     col);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     col);
        gfx.fill(x,         y,         x + 1,     y + h,     col);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     col);
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
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
}
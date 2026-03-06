package com.example.intro;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;

/**
 * Phase 2 — Intro Video Screen
 *
 * ROOT CAUSE FIX: The original code called super.render() AFTER drawing the
 * video, which triggered Minecraft's background renderer and painted over the
 * texture every frame — making the video completely invisible.
 * super.render() is now intentionally NOT called here.
 *
 * FLOW:
 *   Phase A — Full intro video plays (SPACE or click skips after 1.5 s).
 *   Phase B — Video loops silently from LOOP_START_SEC (9 s) as background.
 *             A cave-themed title menu fades in on top.
 *             Stays here until the player enters a world or server.
 */
public class VideoScreen extends Screen {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String VIDEO_FILENAME = "intro.mp4";
    private static final double FALLBACK_FPS   = 30.0;
    private static final int    BUFFER_FRAMES  = 24;
    private static final long   SKIP_DELAY_MS  = 1_500;
    private static final long   FADE_IN_MS     = 500;
    private static final double LOOP_START_SEC = 9.0;

    // ── Menu layout ───────────────────────────────────────────────────────────
    private static final int BTN_W   = 230;
    private static final int BTN_H   = 42;
    private static final int BTN_GAP = 10;

    private static final String[][] BUTTONS = {
        { "\u25B6  Singleplayer", "sp"   },
        { "\u26A1  Multiplayer",  "mp"   },
        { "\u2699  Options",      "opt"  },
        { "\u2715  Quit Game",    "quit" },
    };

    // ── Sentinel for end-of-stream ────────────────────────────────────────────
    private static final int[] EOF_SENTINEL = new int[0];

    // ── Decode ────────────────────────────────────────────────────────────────
    private Thread                     decodeThread;
    private final BlockingQueue<int[]>  frameQueue  = new LinkedBlockingQueue<>(BUFFER_FRAMES);
    private final AtomicBoolean         decodeError = new AtomicBoolean(false);

    // ── Playback ──────────────────────────────────────────────────────────────
    private volatile int[]  currentPixels = null;
    private volatile int    videoWidth    = 1;
    private volatile int    videoHeight   = 1;
    private volatile long   frameDelayMs  = (long)(1000.0 / FALLBACK_FPS);
    private long            lastFrameMs   = 0;

    // ── Texture ───────────────────────────────────────────────────────────────
    private DynamicTexture   videoTexture;
    private ResourceLocation textureLocation;
    private int              texW = 0, texH = 0;

    // ── Phase tracking ────────────────────────────────────────────────────────
    private boolean loopMode    = false;
    private long    startMs     = -1;
    private long    menuStartMs = -1;

    // ── Menu state ────────────────────────────────────────────────────────────
    private int    hoveredBtn = -1;
    private final long[] pressedMs = new long[BUTTONS.length];

    // ── Constructor ───────────────────────────────────────────────────────────
    public VideoScreen() {
        super(Component.literal("Intro"));
        java.util.Arrays.fill(pressedMs, -1L);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        startMs = System.currentTimeMillis();
        startDecodeThread(false);
    }

    @Override
    public void removed() {
        if (decodeThread != null) decodeThread.interrupt();
        if (videoTexture  != null) { videoTexture.close(); videoTexture = null; }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Decode thread ─────────────────────────────────────────────────────────
    private void startDecodeThread(boolean seekToLoop) {
        if (decodeThread != null) decodeThread.interrupt();
        frameQueue.clear();
        decodeError.set(false);

        File file = new File(Minecraft.getInstance().gameDirectory, VIDEO_FILENAME);
        if (!file.exists()) file = new File(VIDEO_FILENAME);
        final File videoFile = file;

        decodeThread = new Thread(() -> {
            try {
                if (!videoFile.exists()) {
                    System.err.println("[Intro] Video not found: " + videoFile.getAbsolutePath());
                    decodeError.set(true);
                    frameQueue.put(EOF_SENTINEL);
                    return;
                }

                FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(videoFile));

                // Auto-detect FPS from container metadata
                try {
                    var meta   = grab.getVideoTrack().getMeta();
                    double sec = meta.getTotalDuration();
                    int    frm = meta.getTotalFrames();
                    if (sec > 0 && frm > 0)
                        frameDelayMs = Math.max(8L, (long)(1000.0 / (frm / sec)));
                } catch (Exception ignored) {}

                // Seek to loop point for loop mode (silent re-play)
                if (seekToLoop) {
                    try { grab.seekToSecondPrecise(LOOP_START_SEC); }
                    catch (Exception ignored) {}
                }

                Picture pic;
                while ((pic = grab.getNativeFrame()) != null) {
                    if (Thread.interrupted()) break;
                    BufferedImage img = AWTUtil.toBufferedImage(pic);
                    videoWidth  = img.getWidth();
                    videoHeight = img.getHeight();
                    int[] px = img.getRGB(0, 0, videoWidth, videoHeight, null, 0, videoWidth);
                    frameQueue.put(px);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                decodeError.set(true);
                System.err.println("[Intro] Decode error: " + e.getMessage());
            } finally {
                try { frameQueue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
            }
        }, "intro-decode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    // ── Frame upload ──────────────────────────────────────────────────────────
    private void uploadFrame() {
        int w = videoWidth, h = videoHeight;

        if (videoTexture == null || texW != w || texH != h) {
            if (videoTexture != null) videoTexture.close();
            videoTexture = new DynamicTexture(
                    new NativeImage(NativeImage.Format.RGBA, w, h, false));
            texW = w; texH = h;
            textureLocation = Minecraft.getInstance()
                    .getTextureManager()
                    .register("intro_video", videoTexture);
        }

        NativeImage img = videoTexture.getPixels();
        if (img == null) return;

        int[] px = currentPixels;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = px[y * w + x];
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        videoTexture.upload();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDER — super.render() is intentionally NOT called.
    // Calling it paints Minecraft's background OVER our video every frame.
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now     = System.currentTimeMillis();
        long elapsed = (startMs < 0) ? 0 : now - startMs;

        // ── Advance frame queue ───────────────────────────────────────────
        if (now - lastFrameMs >= frameDelayMs) {
            int[] next = frameQueue.poll();
            if (next == EOF_SENTINEL) {
                if (!loopMode) {
                    enterLoopMode();           // intro done → menu
                } else {
                    startDecodeThread(true);   // loop ended → restart from 9 s
                }
            } else if (next != null) {
                currentPixels = next;
                lastFrameMs   = now;
                uploadFrame();
            }
        }

        // ── Black base ────────────────────────────────────────────────────
        gfx.fill(0, 0, width, height, 0xFF000000);

        // ── Video — drawn FIRST so everything else renders on top ─────────
        if (videoTexture != null && currentPixels != null) {
            renderVideo(gfx);
        } else if (!decodeError.get() && !loopMode) {
            drawSpinner(gfx, now);
        } else if (decodeError.get() && !loopMode) {
            enterLoopMode(); // no video file — fall through to menu
        }

        // ── Intro-only overlays ───────────────────────────────────────────
        if (!loopMode) {
            if (elapsed < FADE_IN_MS) {
                int a = (int)((1.0 - elapsed / (double)FADE_IN_MS) * 255);
                gfx.fill(0, 0, width, height, a << 24);
            }
            if (elapsed > SKIP_DELAY_MS) drawSkipHint(gfx, now);
        }

        // ── Loop / title-screen phase ─────────────────────────────────────
        if (loopMode && menuStartMs > 0) {
            float menuFade = Math.min(1f, (now - menuStartMs) / 700f);

            // Darkening veil — enough to read buttons over cave video
            gfx.fill(0, 0, width, height, (int)(menuFade * 0x99) << 24);

            renderMenuUI(gfx, mouseX, mouseY, now, menuFade);
            checkPressedActions(now);
        }
    }

    // ── Phase transition ──────────────────────────────────────────────────────
    private void enterLoopMode() {
        if (loopMode) return;
        loopMode    = true;
        menuStartMs = System.currentTimeMillis();
        startDecodeThread(true);
    }

    // ── Video rendering ───────────────────────────────────────────────────────
    private void renderVideo(GuiGraphics gfx) {
        int vw = videoWidth;
        int vh = videoHeight;

        float scale = Math.max((float) width / vw, (float) height / vh);

        int dw = (int) (vw * scale);
        int dh = (int) (vh * scale);

        int dx = (width - dw) / 2;
        int dy = (height - dh) / 2;

        gfx.blit(textureLocation, dx, dy, dw, dh, 0, 0, vw, vh, vw, vh);
    }
    // ── Menu UI ───────────────────────────────────────────────────────────────
    private void renderMenuUI(GuiGraphics gfx, int mouseX, int mouseY, long now, float fade) {
        // Hover detection
        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]
                    && pressedMs[i] < 0)
                hoveredBtn = i;
        }

        drawTitle(gfx, now, fade);
        drawButtons(gfx, now, fade);
        drawFooter(gfx, fade);

        // Final fade-in veil
        if (fade < 1f)
            gfx.fill(0, 0, width, height, (int)((1f - fade) * 180) << 24);
    }

    /** MINECRAFT title — warm white, cave-friendly. */
    private void drawTitle(GuiGraphics gfx, long now, float fade) {
        String title  = "M I N E C R A F T";
        int    cx     = width / 2;
        int    ty     = height / 2 - 140;
        int    totalW = font.width(title);
        int    xCurs  = cx - totalW / 2;

        for (char ch : title.toCharArray()) {
            String s   = String.valueOf(ch);
            int    chW = font.width(s);
            float  p   = 0.85f + 0.15f * (float)Math.abs(Math.sin(now / 1800.0 + xCurs * 0.05));
            int    mainA = (int)(fade * 235);
            int    shadA = (int)(fade * 100);

            gfx.drawString(font, "\u00a7l" + s, xCurs + 1, ty + 1, (shadA << 24) | 0x2A1A08, false);
            int col = lerpColor(0xEEDDCC, 0xFFFFFF, p);
            gfx.drawString(font, "\u00a7l" + s, xCurs, ty, (mainA << 24) | col, false);
            xCurs += chW;
        }

        // Amber underline
        int barY = ty + font.lineHeight + 4;
        int barA = (int)(fade * 200);
        for (int g = 4; g > 0; g--) {
            int ga = (int)(fade * 18 / g);
            gfx.fill(cx - totalW/2 - g*2, barY - g,
                     cx + totalW/2 + g*2, barY + 2 + g, (ga << 24) | 0xAA6622);
        }
        gfx.fill(cx - totalW/2, barY, cx + totalW/2, barY + 2, (barA << 24) | 0xCC8833);

        // Edition tag
        String sub = "Java Edition  \u2022  " + SharedConstants.getCurrentVersion().getName();
        gfx.drawCenteredString(font, sub, cx, barY + 8, (int)(fade * 150) << 24 | 0xAA8855);
    }

    private int[] btnRect(int i) {
        int x = (width - BTN_W) / 2;
        int y = height / 2 - 50 + i * (BTN_H + BTN_GAP);
        return new int[]{ x, y, BTN_W, BTN_H };
    }

    private void drawButtons(GuiGraphics gfx, long now, float fade) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[]   r       = btnRect(i);
            boolean hov     = (i == hoveredBtn);
            boolean pressed = (pressedMs[i] >= 0);

            float bf = Math.min(1f, Math.max(0f,
                    (float)(now - menuStartMs - 150L - i * 65L) / 380f));
            bf = easeOut(bf) * fade;
            if (bf <= 0f) continue;

            int bx = r[0], by = r[1], bw = r[2], bh = r[3];
            if (pressed) by += (int)(Math.min(1f, (now - pressedMs[i]) / 100f) * 3);

            // Amber glow halos
            float gs = hov ? 1f : (0.35f + 0.25f * (float)Math.abs(Math.sin(now / 1400.0 + i)));
            for (int g = 5; g > 0; g--) {
                int ga = (int)(bf * gs * (hov ? 55 : 20) / g);
                gfx.fill(bx - g*4, by - g*2, bx + bw + g*4, by + bh + g*2,
                         (ga << 24) | (hov ? 0xDD8833 : 0x885522));
            }

            // Drop shadow
            gfx.fill(bx + 3, by + 4, bx + bw + 3, by + bh + 4, (int)(bf * 0x66) << 24);

            // Body — semi-transparent dark
            int bgA = (int)(bf * (hov ? 0xCC : 0xAA));
            gfx.fill(bx, by, bx + bw, by + bh, (bgA << 24) | (hov ? 0x100804 : 0x070402));

            // Inner highlight
            for (int dy = 0; dy < bh / 2; dy++) {
                float edge = 1f - (dy / (float)(bh / 2));
                int a = (int)(edge * bf * (hov ? 18 : 9));
                gfx.fill(bx, by + dy, bx + bw, by + dy + 1, (a << 24) | 0xFFEECC);
            }

            // Shimmer sweep
            if (!pressed) {
                float sw = (now / 2200f + i * 0.35f) % 1f;
                int   sx = bx + (int)(sw * (bw + 50)) - 25;
                for (int si = 0; si < 24; si++) {
                    float e2 = 1f - Math.abs(si / 12f - 1f);
                    int sa = (int)(e2 * bf * (hov ? 50 : 22));
                    gfx.fill(sx + si, by, sx + si + 1, by + bh, (sa << 24) | 0xFFE8BB);
                }
            }

            // Animated amber border
            int bCol = hov
                ? ((int)(bf * 255) << 24 | 0xDD9944)
                : ((int)(bf * 180) << 24 | lerpColor(0x7A4A22, 0xAA6633,
                    (float)Math.abs(Math.sin(now / 1000.0 + i))));
            drawBorder(gfx, bx, by, bw, bh, bCol);

            // Bright corners
            int cc = hov ? 0xFFDD9944 : (int)(bf * 200) << 24 | 0x9A6030;
            gfx.fill(bx,       by,       bx+3,   by+3,   cc);
            gfx.fill(bx+bw-3,  by,       bx+bw,  by+3,   cc);
            gfx.fill(bx,       by+bh-3,  bx+3,   by+bh,  cc);
            gfx.fill(bx+bw-3,  by+bh-3,  bx+bw,  by+bh,  cc);

            // Left accent bar
            int acA = (int)(bf * 220);
            gfx.fill(bx, by, bx + 3, by + bh, (acA << 24) | (hov ? 0xEEAA44 : 0x8B5020));

            // Label
            int tA  = (int)(bf * 235);
            int tC  = (tA << 24) | (hov ? 0xFFE8C8 : 0xD4B896);
            String lbl = hov ? "  " + BUTTONS[i][0] : BUTTONS[i][0];
            gfx.drawCenteredString(font, "\u00a7l" + lbl, bx + bw / 2, by + (bh - 8) / 2, tC);
        }
    }

    private void drawFooter(GuiGraphics gfx, float fade) {
        int a  = (int)(fade * 140);
        int a2 = (int)(fade * 80);
        gfx.drawCenteredString(font, "Discover more at minecraft.net",
                width / 2, height - 22, (a << 24) | 0xAA8855);
        gfx.drawCenteredString(font, "\u00a78Copyright Mojang AB  \u2022  Do not distribute",
                width / 2, height - 11, (a2 << 24) | 0x664433);
    }

    // ── Loading spinner ───────────────────────────────────────────────────────
    private void drawSpinner(GuiGraphics gfx, long now) {
        int cx = width / 2, cy = height / 2;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0 + now / 300.0;
            int sx = cx + (int)(Math.cos(angle) * 18);
            int sy = cy + (int)(Math.sin(angle) * 18);
            int a  = (i == (int)((now / 110) % 8)) ? 220 : 55;
            gfx.fill(sx - 2, sy - 2, sx + 2, sy + 2, (a << 24) | 0xCC8833);
        }
        gfx.drawCenteredString(font, "\u00a77Loading\u2026", cx, cy + 30, 0x77AA8855);
    }

    // ── Skip hint ─────────────────────────────────────────────────────────────
    private void drawSkipHint(GuiGraphics gfx, long now) {
        float p = 0.5f + 0.5f * (float)Math.abs(Math.sin(now / 700.0));
        int   a = (int)(p * 190);
        String s = "[ SPACE ]  Skip";
        gfx.drawString(font, s, width - font.width(s) - 14, height - 20,
                (a << 24) | 0xBBAA88, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!loopMode && (keyCode == 32 || keyCode == 257)
                && System.currentTimeMillis() - startMs > SKIP_DELAY_MS) {
            enterLoopMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        if (loopMode) {
            for (int i = 0; i < BUTTONS.length; i++) {
                int[] r = btnRect(i);
                if (mx >= r[0] && mx <= r[0] + r[2]
                        && my >= r[1] && my <= r[1] + r[3]
                        && pressedMs[i] < 0) {
                    pressedMs[i] = System.currentTimeMillis();
                    return true;
                }
            }
        } else if (System.currentTimeMillis() - startMs > SKIP_DELAY_MS) {
            enterLoopMode();
            return true;
        }
        return false;
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
        return ((int)(ar+(br-ar)*t) << 16)
             | ((int)(ag+(bg-ag)*t) <<  8)
             |  (int)(ab+(bb-ab)*t);
    }

    private static float easeOut(float t) {
        return 1f - (float)Math.pow(1 - t, 3);
    }
}

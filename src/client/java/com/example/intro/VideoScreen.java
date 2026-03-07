package com.example.intro;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * VideoScreen — Optimized for 720p / 60 fps
 *
 * CRASH FIX (NullPointerException from Fabric ScreenEvents)
 * ──────────────────────────────────────────────────────────
 * Fabric's ScreenEvents.afterRender() calls requireNonNull(screen) on the
 * PARENT of every screen that renders. Passing null or a removed screen
 * as parent causes an NPE crash.
 *
 * The correct pattern for 1.20.1 + Fabric API is:
 *   new SelectWorldScreen(new TitleScreen())
 *   new JoinMultiplayerScreen(new TitleScreen())
 *
 * A fresh TitleScreen() is a valid, non-null object. When the sub-screen
 * closes it calls mc.setScreen(parent) → TitleScreen.init() fires →
 * TitleScreenMixin intercepts it → sets a new VideoScreen() immediately.
 * Because VideoScreen's playback state is STATIC the video resumes from
 * exactly where it paused — no restart, no black flash.
 *
 * STATIC STATE (video survives screen transitions)
 * ─────────────────────────────────────────────────
 * All decode/playback fields are static so they survive the screen being
 * removed and recreated. The DynamicTexture (GL object) must still be
 * instance-level because it's tied to the render context.
 */
public class VideoScreen extends Screen {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String VIDEO_FILENAME = "intro.mp4";
    private static final double FALLBACK_FPS   = 60.0;
    private static final int    BUFFER_FRAMES  = 8;
    private static final long   SKIP_DELAY_MS  = 1_500;
    private static final long   FADE_IN_MS     = 500;
    private static final double LOOP_START_SEC = 9.0;
    private static final double MENU_SHOW_SEC  = 9.0;

    // ── Menu layout ───────────────────────────────────────────────────────────
    private static final String[][] BUTTONS = {
            { "\u25B6  Singleplayer", "sp"   },
            { "\u26A1  Multiplayer",  "mp"   },
            { "\u2699  Options",      "opt"  },
            { "\u2715  Quit Game",    "quit" },
    };

    // ── Sentinel ──────────────────────────────────────────────────────────────
    private static final int[] EOF_SENTINEL = new int[0];

    // =========================================================================
    // STATIC — survives screen transitions
    // =========================================================================
    private static Thread               s_decodeThread   = null;
    private static BlockingQueue<int[]> s_frameQueue     = new LinkedBlockingQueue<>(BUFFER_FRAMES);
    private static AtomicBoolean        s_decodeError    = new AtomicBoolean(false);
    private static int[]                s_decodePixelBuf = null;

    private static volatile int[]   s_currentPixels = null;
    private static volatile int     s_videoWidth    = 1;
    private static volatile int     s_videoHeight   = 1;
    private static volatile long    s_frameDelayMs  = (long)(1000.0 / FALLBACK_FPS);
    private static          long    s_lastFrameMs   = 0;
    private static          boolean s_frameDirty    = false;

    private static boolean s_loopMode    = false;
    private static long    s_startMs     = -1;
    private static long    s_menuStartMs = -1;

    // =========================================================================
    // INSTANCE — recreated each time the screen opens
    // =========================================================================
    private DynamicTexture    videoTexture;
    private ResourceLocation  textureLocation;
    private int texW = 0, texH = 0;

    private int    hoveredBtn = -1;
    private final long[] pressedMs = new long[BUTTONS.length];

    // ── Constructor ───────────────────────────────────────────────────────────
    public VideoScreen() {
        super(Component.literal("Intro"));
        Arrays.fill(pressedMs, -1L);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        // Returning from a sub-screen: decode thread still alive, skip re-init
        if (s_decodeThread != null && s_decodeThread.isAlive()) {
            videoTexture = null;
            texW = 0; texH = 0;
            s_frameDirty = true;
            return;
        }
        // First launch
        s_startMs     = System.currentTimeMillis();
        s_lastFrameMs = s_startMs;
        s_loopMode    = false;
        s_menuStartMs = -1;
        startDecodeThread(false);
    }

    @Override
    public void removed() {
        // GL texture must be closed — cannot survive context change
        if (videoTexture != null) {
            videoTexture.close();
            videoTexture = null;
        }
        // Do NOT interrupt decode thread — keep buffering while in sub-screens
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Decode thread ─────────────────────────────────────────────────────────
    private static void startDecodeThread(boolean seekToLoop) {
        if (s_decodeThread != null) s_decodeThread.interrupt();
        s_frameQueue.clear();
        s_decodeError.set(false);

        File file = resolveVideoFile();
        if (file == null) {
            System.out.println("[Intro] Video file not found.");
            s_decodeError.set(true);
            return;
        }

        File videoFile = file;
        s_decodeThread = new Thread(() -> {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();

                double fps = grabber.getFrameRate();
                if (fps > 0) s_frameDelayMs = (long)(1000.0 / fps);

                if (seekToLoop)
                    grabber.setTimestamp((long)(LOOP_START_SEC * 1_000_000));

                Java2DFrameConverter converter = new Java2DFrameConverter();
                Frame frame;

                while ((frame = grabber.grabImage()) != null) {
                    if (Thread.interrupted()) break;

                    BufferedImage img = converter.convert(frame);
                    int w = img.getWidth(), h = img.getHeight();
                    s_videoWidth  = w;
                    s_videoHeight = h;

                    int needed = w * h;
                    if (s_decodePixelBuf == null || s_decodePixelBuf.length < needed)
                        s_decodePixelBuf = new int[needed];

                    img.getRGB(0, 0, w, h, s_decodePixelBuf, 0, w);
                    s_frameQueue.put(Arrays.copyOf(s_decodePixelBuf, needed));
                }

            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                s_decodeError.set(true);
                e.printStackTrace();
            } finally {
                try { s_frameQueue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
            }
        }, "intro-decode");

        s_decodeThread.setDaemon(true);
        s_decodeThread.setPriority(Thread.MIN_PRIORITY);
        s_decodeThread.start();
    }

    private static File resolveVideoFile() {
        Minecraft mc = Minecraft.getInstance();
        File gameDir = mc.gameDirectory;
        File[] candidates = {
                gameDir != null ? new File(gameDir, VIDEO_FILENAME)             : null,
                new File(System.getProperty("user.dir"), VIDEO_FILENAME),
                new File(VIDEO_FILENAME),
                gameDir != null ? new File(gameDir.getParent(), VIDEO_FILENAME) : null,
        };
        for (File f : candidates) {
            if (f != null && f.exists()) {
                System.out.println("[Intro] Found video at: " + f.getAbsolutePath());
                return f;
            }
        }
        return null;
    }

    // ── NativeImage pixel pointer ─────────────────────────────────────────────
    private static java.lang.reflect.Field pixelsField;
    static {
        try {
            pixelsField = NativeImage.class.getDeclaredField("pixels");
            pixelsField.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("[Intro] Cannot access NativeImage.pixels", e);
        }
    }

    // ── Frame upload ──────────────────────────────────────────────────────────
    private void uploadFrame() {
        int w = s_videoWidth, h = s_videoHeight;

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

        try {
            long  dst = (long) pixelsField.get(img);
            int[] px  = s_currentPixels;
            int   len = w * h;
            for (int i = 0; i < len; i++) {
                int argb = px[i];
                int abgr = (argb & 0xFF00FF00)
                         | ((argb & 0x00FF0000) >>> 16)
                         | ((argb & 0x000000FF) <<  16);
                MemoryUtil.memPutInt(dst + (long) i * 4, abgr);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        videoTexture.upload();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    // super.render() intentionally NOT called — would repaint MC background
    // over our video every frame.
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now     = System.currentTimeMillis();
        long elapsed = (s_startMs < 0) ? 0 : now - s_startMs;

        // Advance frame queue
        while (now - s_lastFrameMs >= s_frameDelayMs) {
            int[] next = s_frameQueue.poll();
            if (next == null) break;

            if (next == EOF_SENTINEL) {
                if (!s_loopMode) enterLoopMode();
                else             startDecodeThread(true);
            } else {
                s_currentPixels = next;
                s_lastFrameMs  += s_frameDelayMs;
                s_frameDirty    = true;
            }
        }

        if (s_frameDirty && s_currentPixels != null) {
            uploadFrame();
            s_frameDirty = false;
        }

        // Black base
        gfx.fill(0, 0, width, height, 0xFF000000);

        // Video
        if (videoTexture != null && s_currentPixels != null) {
            renderVideo(gfx);
        } else if (!s_decodeError.get() && !s_loopMode) {
            drawSpinner(gfx, now);
        } else if (s_decodeError.get() && !s_loopMode) {
            enterLoopMode();
        }

        // Intro-only overlays
        if (!s_loopMode) {
            if (elapsed < FADE_IN_MS) {
                int a = (int)((1.0 - elapsed / (double) FADE_IN_MS) * 255);
                gfx.fill(0, 0, width, height, a << 24);
            }
            if (elapsed > SKIP_DELAY_MS)
                drawSkipHint(gfx, now);
        }

        // Buttons — visible from MENU_SHOW_SEC onward
        boolean menuVisible = s_loopMode || elapsed >= (long)(MENU_SHOW_SEC * 1000);
        if (menuVisible) {
            if (s_menuStartMs <= 0) s_menuStartMs = now;
            float menuFade = Math.min(1f, (now - s_menuStartMs) / 700f);
            gfx.fill(0, 0, width, height, (int)(menuFade * 0x44) << 24);
            renderMenuUI(gfx, mouseX, mouseY, now, menuFade);
            checkPressedActions(now);
        }
    }

    // ── Phase transition ──────────────────────────────────────────────────────
    private static void enterLoopMode() {
        if (s_loopMode) return;
        s_loopMode = true;
        if (s_menuStartMs <= 0) s_menuStartMs = System.currentTimeMillis();
        startDecodeThread(true);
    }

    // ── Video rendering ───────────────────────────────────────────────────────
    private void renderVideo(GuiGraphics gfx) {
        int vw = s_videoWidth, vh = s_videoHeight;
        float scale = Math.min((float) width / vw, (float) height / vh);
        int dw = (int)(vw * scale), dh = (int)(vh * scale);
        int dx = (width  - dw) / 2, dy = (height - dh) / 2;
        gfx.blit(textureLocation, dx, dy, dw, dh, 0, 0, vw, vh, vw, vh);
    }

    // ── Menu UI ───────────────────────────────────────────────────────────────
    private void renderMenuUI(GuiGraphics gfx, int mouseX, int mouseY, long now, float fade) {
        hoveredBtn = -1;
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r = btnRect(i);
            if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]
                    && pressedMs[i] < 0)
                hoveredBtn = i;
        }
        drawButtons(gfx, now, fade);
        if (fade < 1f)
            gfx.fill(0, 0, width, height, (int)((1f - fade) * 180) << 24);
    }

    private int[] btnRect(int i) {
        int bw  = Math.min(260, Math.max(160, width  / 4));
        int bh  = Math.min(48,  Math.max(28,  height / 14));
        int gap = Math.max(6,   height / 60);
        int totalH = BUTTONS.length * (bh + gap) - gap;
        int x = (width  - bw) / 2;
        int y = (height - totalH) / 2 + i * (bh + gap);
        return new int[]{ x, y, bw, bh };
    }

    private void drawButtons(GuiGraphics gfx, long now, float fade) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[]   r       = btnRect(i);
            boolean hov     = (i == hoveredBtn);
            boolean pressed = (pressedMs[i] >= 0);

            float bf = Math.min(1f, Math.max(0f,
                    (float)(now - s_menuStartMs - 150L - i * 65L) / 380f));
            bf = easeOut(bf) * fade;
            if (bf <= 0f) continue;

            int bx = r[0], by = r[1], bw = r[2], bh = r[3];
            if (pressed)
                by += (int)(Math.min(1f, (now - pressedMs[i]) / 100f) * 3);

            float gs = hov ? 1f : (0.35f + 0.25f * (float) Math.abs(Math.sin(now / 1400.0 + i)));
            for (int g = 5; g > 0; g--) {
                int ga = (int)(bf * gs * (hov ? 55 : 20) / g);
                gfx.fill(bx - g*4, by - g*2, bx + bw + g*4, by + bh + g*2,
                        (ga << 24) | (hov ? 0xDD8833 : 0x885522));
            }

            gfx.fill(bx + 3, by + 4, bx + bw + 3, by + bh + 4, (int)(bf * 0x66) << 24);

            int bgA = (int)(bf * (hov ? 0xCC : 0xAA));
            gfx.fill(bx, by, bx + bw, by + bh, (bgA << 24) | (hov ? 0x100804 : 0x070402));

            for (int dy = 0; dy < bh / 2; dy++) {
                float edge = 1f - (dy / (float)(bh / 2));
                int a = (int)(edge * bf * (hov ? 18 : 9));
                gfx.fill(bx, by + dy, bx + bw, by + dy + 1, (a << 24) | 0xFFEECC);
            }

            if (!pressed) {
                float sw = (now / 2200f + i * 0.35f) % 1f;
                int   sx = bx + (int)(sw * (bw + 50)) - 25;
                for (int si = 0; si < 24; si++) {
                    float e2 = 1f - Math.abs(si / 12f - 1f);
                    int   sa = (int)(e2 * bf * (hov ? 50 : 22));
                    gfx.fill(sx + si, by, sx + si + 1, by + bh, (sa << 24) | 0xFFE8BB);
                }
            }

            int bCol = hov
                    ? ((int)(bf * 255) << 24 | 0xDD9944)
                    : ((int)(bf * 180) << 24 | lerpColor(0x7A4A22, 0xAA6633,
                            (float) Math.abs(Math.sin(now / 1000.0 + i))));
            drawBorder(gfx, bx, by, bw, bh, bCol);

            int cc = hov ? 0xFFDD9944 : (int)(bf * 200) << 24 | 0x9A6030;
            gfx.fill(bx,          by,          bx + 3,      by + 3,      cc);
            gfx.fill(bx + bw - 3, by,          bx + bw,     by + 3,      cc);
            gfx.fill(bx,          by + bh - 3, bx + 3,      by + bh,     cc);
            gfx.fill(bx + bw - 3, by + bh - 3, bx + bw,     by + bh,     cc);

            int acA = (int)(bf * 220);
            gfx.fill(bx, by, bx + 3, by + bh, (acA << 24) | (hov ? 0xEEAA44 : 0x8B5020));

            int tA  = (int)(bf * 235);
            int tC  = (tA << 24) | (hov ? 0xFFE8C8 : 0xD4B896);
            String lbl = hov ? "  " + BUTTONS[i][0] : BUTTONS[i][0];
            gfx.drawCenteredString(font, "\u00a7l" + lbl, bx + bw / 2, by + (bh - 8) / 2, tC);
        }
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
        float p = 0.5f + 0.5f * (float) Math.abs(Math.sin(now / 700.0));
        int a   = (int)(p * 190);
        String s = "[ SPACE ]  Skip";
        gfx.drawString(font, s, width - font.width(s) - 14, height - 20,
                (a << 24) | 0xBBAA88, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!s_loopMode && (keyCode == 32 || keyCode == 257)
                && System.currentTimeMillis() - s_startMs > SKIP_DELAY_MS) {
            enterLoopMode();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return false;
        long now = System.currentTimeMillis();

        if (s_loopMode || now - s_startMs >= (long)(MENU_SHOW_SEC * 1000)) {
            for (int i = 0; i < BUTTONS.length; i++) {
                int[] r = btnRect(i);
                if (mx >= r[0] && mx <= r[0] + r[2]
                        && my >= r[1] && my <= r[1] + r[3]
                        && pressedMs[i] < 0) {
                    pressedMs[i] = now;
                    return true;
                }
            }
        }
        if (!s_loopMode && now - s_startMs > SKIP_DELAY_MS) {
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

    /**
     * Navigation — uses VideoScreen as parent for all sub-screens.
     *
     * Passing `this` (a live, initialized screen) as the parent satisfies
     * Fabric's ScreenEvents.afterRender() requireNonNull check at every
     * level of the screen stack — including grandchild screens like
     * CreateWorldScreen that open from inside SelectWorldScreen.
     *
     * Flow when player exits a world / leaves multiplayer:
     *   1. Sub-screen closes → mc.setScreen(parent) → mc.setScreen(this VideoScreen)
     *   2. VideoScreen.init() sees the decode thread is alive → skips re-init
     *   3. Video resumes from last frame with no restart
     *
     * If for any reason TitleScreen is shown instead, TitleScreenMixin
     * intercepts init() and redirects to a new VideoScreen automatically.
     */
    private void navigate(String id) {
        Minecraft mc = Minecraft.getInstance();
        // Use TitleScreen as the parent to satisfy Fabric's null-checks
        net.minecraft.client.gui.screens.TitleScreen parent = new net.minecraft.client.gui.screens.TitleScreen();

        switch (id) {
            case "sp"   -> mc.setScreen(new SelectWorldScreen(parent));
            case "mp"   -> mc.setScreen(new JoinMultiplayerScreen(parent));
            case "opt"  -> mc.setScreen(new OptionsScreen(parent, mc.options));
            case "quit" -> mc.stop();
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────
    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int col) {
        gfx.fill(x,         y,         x + w,     y + 1,     col);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     col);
        gfx.fill(x,         y,         x + 1,     y + h,     col);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     col);
    }

    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int)(ar + (br - ar) * t) << 16)
             | ((int)(ag + (bg - ag) * t) <<  8)
             |  (int)(ab + (bb - ab) * t);
    }

    private static float easeOut(float t) {
        return 1f - (float) Math.pow(1 - t, 3);
    }
}
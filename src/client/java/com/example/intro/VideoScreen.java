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
 * Button rendering: 1000 blocks fly from outside the screen to physically
 * construct each button, then the label text fades in once the build is done.
 */
public class VideoScreen extends Screen {

    // ── Config ────────────────────────────────────────────────────────────────
    private static final String VIDEO_FILENAME = "intro.mp4";
    private static final double FALLBACK_FPS   = 60.0;
    private static final int    BUFFER_FRAMES  = 8;
    private static final long   FADE_IN_MS     = 500;
    private static final double LOOP_START_SEC = 9.0;
    private static final double MENU_SHOW_SEC  = 9.0;

    // ── Block storm config ─────────────────────────────────────────────────────
    /** Each block is BLOCK_PX × BLOCK_PX pixels. Grid fills the button exactly. */
    private static final int   BLOCK_PX           = 4;
    private static final float BUILD_DURATION_SEC = 1.4f;
    private static final long  BUILD_STAGGER_MS   = 120L;
    private static final float TEXT_SHOW_AT       = 0.85f;

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
    private DynamicTexture   videoTexture;
    private ResourceLocation textureLocation;
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
        if (s_decodeThread != null && s_decodeThread.isAlive()) {
            videoTexture = null; texW = 0; texH = 0; s_frameDirty = true;
            return;
        }
        s_startMs     = System.currentTimeMillis();
        s_lastFrameMs = s_startMs;
        s_loopMode    = false;
        s_menuStartMs = -1;
        startDecodeThread(false);
    }

    @Override
    public void removed() {
        if (videoTexture != null) { videoTexture.close(); videoTexture = null; }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Decode thread ─────────────────────────────────────────────────────────
    private static void startDecodeThread(boolean seekToLoop) {
        if (s_decodeThread != null) s_decodeThread.interrupt();
        s_frameQueue.clear();
        s_decodeError.set(false);

        File file = resolveVideoFile();
        if (file == null) { s_decodeError.set(true); return; }

        File videoFile = file;
        s_decodeThread = new Thread(() -> {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile)) {
                grabber.start();
                double fps = grabber.getFrameRate();
                if (fps > 0) s_frameDelayMs = (long)(1000.0 / fps);
                if (seekToLoop) grabber.setTimestamp((long)(LOOP_START_SEC * 1_000_000));

                Java2DFrameConverter conv = new Java2DFrameConverter();
                Frame frame;
                while ((frame = grabber.grabImage()) != null) {
                    if (Thread.interrupted()) break;
                    BufferedImage img = conv.convert(frame);
                    int w = img.getWidth(), h = img.getHeight();
                    s_videoWidth = w; s_videoHeight = h;
                    int need = w * h;
                    if (s_decodePixelBuf == null || s_decodePixelBuf.length < need)
                        s_decodePixelBuf = new int[need];
                    img.getRGB(0, 0, w, h, s_decodePixelBuf, 0, w);
                    s_frameQueue.put(Arrays.copyOf(s_decodePixelBuf, need));
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) { s_decodeError.set(true); e.printStackTrace();
            } finally {
                try { s_frameQueue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
            }
        }, "intro-decode");
        s_decodeThread.setDaemon(true);
        s_decodeThread.setPriority(Thread.MIN_PRIORITY);
        s_decodeThread.start();
    }

    private static File    s_cachedVideoFile = null;
    private static boolean s_videoFileCached = false;

    private static File resolveVideoFile() {
        if (s_videoFileCached) return s_cachedVideoFile;

        try {
            ResourceLocation rl = new ResourceLocation("modid", "video/intro.mp4");

            var resOpt = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(rl);

            if (resOpt.isEmpty()) {
                System.out.println("[Intro] Video resource not found: " + rl);
                s_videoFileCached = true;
                return null;
            }

            File temp = File.createTempFile("intro_video", ".mp4");
            temp.deleteOnExit();

            try (var in = resOpt.get().open();
                var out = new java.io.FileOutputStream(temp)) {
                in.transferTo(out);
            }

            s_cachedVideoFile = temp;
            s_videoFileCached = true;

            System.out.println("[Intro] Loaded bundled video: " + temp.getAbsolutePath());

            return temp;

        } catch (Exception e) {
            e.printStackTrace();
        }

        s_videoFileCached = true;
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
            videoTexture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, w, h, false));
            texW = w; texH = h;
            textureLocation = Minecraft.getInstance().getTextureManager()
                    .register("intro_video", videoTexture);
        }
        NativeImage img = videoTexture.getPixels();
        if (img == null) return;
        try {
            long dst = (long) pixelsField.get(img);
            int[] px = s_currentPixels;
            for (int i = 0; i < w * h; i++) {
                int argb = px[i];
                int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
                MemoryUtil.memPutInt(dst + (long)i * 4, abgr);
            }
        } catch (Exception e) { e.printStackTrace(); return; }
        videoTexture.upload();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now     = System.currentTimeMillis();
        long elapsed = (s_startMs < 0) ? 0 : now - s_startMs;

        while (now - s_lastFrameMs >= s_frameDelayMs) {
            int[] next = s_frameQueue.poll();
            if (next == null) break;
            if (next == EOF_SENTINEL) {
                if (!s_loopMode) enterLoopMode(); else startDecodeThread(true);
            } else {
                s_currentPixels = next; s_lastFrameMs += s_frameDelayMs; s_frameDirty = true;
            }
        }
        if (s_frameDirty && s_currentPixels != null) { uploadFrame(); s_frameDirty = false; }

        gfx.fill(0, 0, width, height, 0xFF000000);

        if (videoTexture != null && s_currentPixels != null)      renderVideo(gfx);
        else if (!s_decodeError.get() && !s_loopMode)             drawSpinner(gfx, now);
        else if (s_decodeError.get()  && !s_loopMode)             enterLoopMode();

        if (!s_loopMode) {
            if (elapsed < FADE_IN_MS) {
                int a = (int)((1.0 - elapsed / (double)FADE_IN_MS) * 255);
                gfx.fill(0, 0, width, height, a << 24);
            }
        }

        boolean menuVisible = s_loopMode || elapsed >= (long)(MENU_SHOW_SEC * 1000);
        if (menuVisible) {
            if (s_menuStartMs <= 0) s_menuStartMs = now;
            float menuFade = Math.min(1f, (now - s_menuStartMs) / 700f);
            gfx.fill(0, 0, width, height, (int)(menuFade * 0x44) << 24);

            hoveredBtn = -1;
            for (int i = 0; i < BUTTONS.length; i++) {
                int[] r = btnRect(i);
                if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                        && mouseY >= r[1] && mouseY <= r[1] + r[3]
                        && pressedMs[i] < 0)
                    hoveredBtn = i;
            }

            drawButtons(gfx, now, menuFade);
            checkPressedActions(now);

            if (menuFade < 1f)
                gfx.fill(0, 0, width, height, (int)((1f - menuFade) * 180) << 24);
        }
    }

    private static void enterLoopMode() {
        if (s_loopMode) return;
        s_loopMode = true;
        if (s_menuStartMs <= 0) s_menuStartMs = System.currentTimeMillis();
        startDecodeThread(true);
    }

    private void renderVideo(GuiGraphics gfx) {
        int vw = s_videoWidth, vh = s_videoHeight;
        float scale = Math.min((float)width / vw, (float)height / vh);
        int dw = (int)(vw * scale), dh = (int)(vh * scale);
        gfx.blit(textureLocation, (width - dw) / 2, (height - dh) / 2, dw, dh, 0, 0, vw, vh, vw, vh);
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
    /**
     * Timeline per button (buildProg 0 → 1):
     *   0.00 → 0.72  blocks fly in and land on the button area
     *   0.72 → 1.00  blocks fade out, solid button fades in (merge)
     *   0.85 → 1.00  label text fades in
     */
    private void drawButtons(GuiGraphics gfx, long now, float menuFade) {
        for (int i = 0; i < BUTTONS.length; i++) {
            int[] r  = btnRect(i);
            int   bx = r[0], by = r[1], bw = r[2], bh = r[3];
            boolean hov     = (i == hoveredBtn) && pressedMs[i] < 0;
            boolean pressed = pressedMs[i] >= 0;

            long  btnStart  = s_menuStartMs + (long)(i * BUILD_STAGGER_MS);
            float buildProg = Math.max(0f, Math.min(1f,
                    (now - btnStart) / (BUILD_DURATION_SEC * 1000f)));
            if (buildProg <= 0f) continue;

            int pressOff = pressed ? (int)(Math.min(1f, (now - pressedMs[i]) / 100f) * 3) : 0;
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
                float f = solidAlpha * menuFade;
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
                int tA = (int)(textFade * menuFade * 240);
                gfx.drawCenteredString(font, "\u00a7l  " + BUTTONS[i][0],
                        bx + bw / 2, by + (bh - 8) / 2,
                        (tA << 24) | (hov ? 0xFFFFEE : 0xE8D4B0));
            }
        }
    }

    // ── Block storm ───────────────────────────────────────────────────────────
    /**
     * 1000 blocks fly from random positions outside the screen to random
     * landing spots inside the button rect along Bezier arcs.
     * No trails, no extra effects — just blocks moving and landing.
     * masterAlpha fades the entire storm out during the merge phase.
     */
    private void drawBlockStorm(GuiGraphics gfx,
                                int bx, int by, int bw, int bh,
                                float buildProg, float masterAlpha, int btnIdx) {

        // Divide the button into an exact grid of BLOCK_PX × BLOCK_PX cells.
        // Every block has a fixed grid-cell destination so they tile the button
        // perfectly when fully landed.
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

            // Each block's stagger: random delay spread across the flight window
            float delay   = r0 * 0.65f;
            float dur     = 0.20f + r1 * 0.15f;
            float blockT  = Math.max(0f, Math.min(1f, (buildProg - delay) / dur));
            if (blockT <= 0f) continue;

            // ── Exact grid-cell destination ───────────────────────────────────
            int   gridX = b % cols;
            int   gridY = b / cols;
            float endX  = bx + gridX * BLOCK_PX + BLOCK_PX * 0.5f;
            float endY  = by + gridY * BLOCK_PX + BLOCK_PX * 0.5f;

            // ── Origin: random point well outside the screen ──────────────────
            float ox, oy;
            switch ((int)(r2 * 4)) {
                case 0:  ox = r3 * width;          oy = -height * (0.2f + r4 * 0.8f); break; // top
                case 1:  ox = r3 * width;          oy =  height * (1.2f + r4 * 0.8f); break; // bottom
                case 2:  ox = -width  * (0.2f+r3); oy =  r4 * height;                 break; // left
                default: ox =  width  * (1.2f+r3); oy =  r4 * height;                 break; // right
            }

            // ── Quadratic Bezier arc ──────────────────────────────────────────
            float midX  = (ox + endX) * 0.5f;
            float midY  = (oy + endY) * 0.5f;
            float ctrlX = midX + (r1 - 0.5f) * 140f;
            float ctrlY = midY + (r0 - 0.6f) * 100f; // slight upward bias

            // Ease-out: fast off-screen, decelerates into grid slot
            float t  = 1f - (float)Math.pow(1f - blockT, 2.6f);
            float mt = 1f - t;
            float curX = mt*mt*ox + 2*mt*t*ctrlX + t*t*endX;
            float curY = mt*mt*oy + 2*mt*t*ctrlY + t*t*endY;

            // Block size is always BLOCK_PX; scales up from 0 in first 25% of flight
            int sz = (int)(BLOCK_PX * 0.5f * Math.min(1f, blockT * 4f));
            if (sz < 1) sz = 1;

            // Color: grey stone while far, amber as landing, gold when settled
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
    // ── Loading spinner

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

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
                    pressedMs[i] = now; return true;
                }
            }
        }
        return false;
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
        net.minecraft.client.gui.screens.TitleScreen parent =
                new net.minecraft.client.gui.screens.TitleScreen();
        switch (id) {
            case "sp"   -> mc.tell(() -> mc.setScreen(new SelectWorldScreen(parent)));
            case "mp"   -> mc.tell(() -> mc.setScreen(new JoinMultiplayerScreen(parent)));
            case "opt"  -> mc.tell(() -> mc.setScreen(new OptionsScreen(parent, mc.options)));
            case "quit" -> mc.tell(mc::stop);
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

    /** Murmur3-inspired hash: long seed → deterministic float in [0, 1). */
    private static float hash(long seed) {
        long x = seed ^ (seed >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (x & 0x7FFFFFFFFFFFFFFFL) / (float)0x7FFFFFFFFFFFFFFFL;
    }
}
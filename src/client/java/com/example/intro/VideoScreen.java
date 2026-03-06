package com.example.intro;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

/**
 * Phase 2 — Intro Video Screen
 *
 * Decodes {@code intro.mp4} with jcodec (pure Java, no native VLC needed).
 * A background thread fills a bounded frame queue; the render thread drains it
 * at the correct FPS and uploads each frame to an OpenGL DynamicTexture.
 *
 * Advances to {@link CustomTitleScreen} on completion or SPACE/ENTER skip.
 */
public class VideoScreen extends Screen {

    private static final String VIDEO_FILENAME = "intro.mp4";
    private static final double FALLBACK_FPS   = 30.0;
    private static final int    BUFFER_FRAMES  = 24;
    private static final long   SKIP_DELAY_MS  = 1_500;
    private static final long   FADE_IN_MS     = 500;

    /** Sentinel pushed by decode thread to signal EOF. */
    private static final int[] EOF_SENTINEL = new int[0];

    // decode
    private Thread                     decodeThread;
    private final BlockingQueue<int[]>  frameQueue   = new LinkedBlockingQueue<>(BUFFER_FRAMES);
    private final AtomicBoolean         decodeError  = new AtomicBoolean(false);

    // playback
    private volatile int[]   currentPixels = null;
    private volatile int     videoWidth    = 1;
    private volatile int     videoHeight   = 1;
    private volatile long    frameDelayMs  = (long)(1000.0 / FALLBACK_FPS);
    private long             lastFrameMs   = 0;
    private boolean          playbackEnded = false;
    private boolean          advanced      = false;

    // texture
    private DynamicTexture videoTexture;
    private int            texW = 0, texH = 0;

    private long startMs = -1;

    public VideoScreen() {
        super(Component.literal("Intro"));
    }

    @Override
    protected void init() {
        startMs = System.currentTimeMillis();
        startDecodeThread();
    }

    @Override
    public void removed() {
        if (decodeThread != null) decodeThread.interrupt();
        if (videoTexture != null) { videoTexture.close(); videoTexture = null; }
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // ── Decode thread ─────────────────────────────────────────────────────────
    private void startDecodeThread() {
        File file = new File(Minecraft.getInstance().gameDirectory, VIDEO_FILENAME);
        if (!file.exists()) {
            file = new File(VIDEO_FILENAME);
        }
        final File videoFile = file;

        decodeThread = new Thread(() -> {
            try {
                FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(videoFile));

                // Detect FPS from container metadata
                try {
                    var meta = grab.getVideoTrack().getMeta();
                    double secs   = meta.getTotalDuration();
                    int    frames = meta.getTotalFrames();
                    if (secs > 0 && frames > 0) {
                        frameDelayMs = Math.max(8L, (long)(1000.0 / (frames / secs)));
                    }
                } catch (Exception ignored) {}

                Picture pic;
                while ((pic = grab.getNativeFrame()) != null) {
                    if (Thread.interrupted()) break;
                    BufferedImage img = AWTUtil.toBufferedImage(pic);
                    videoWidth  = img.getWidth();
                    videoHeight = img.getHeight();
                    int[] px = img.getRGB(0, 0, videoWidth, videoHeight, null, 0, videoWidth);
                    frameQueue.put(px); // blocks when buffer is full
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                decodeError.set(true);
            } finally {
                try { frameQueue.put(EOF_SENTINEL); } catch (InterruptedException ignored) {}
            }
        }, "intro-decode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        long now     = System.currentTimeMillis();
        long elapsed = startMs < 0 ? 0 : now - startMs;

        // Advance frame?
        if (!playbackEnded && now - lastFrameMs >= frameDelayMs) {
            int[] next = frameQueue.poll();
            if (next == EOF_SENTINEL) {
                playbackEnded = true;
            } else if (next != null) {
                currentPixels = next;
                lastFrameMs   = now;
                uploadFrame();
            }
        }

        gfx.fill(0, 0, width, height, 0xFF000000);

        if (videoTexture != null && currentPixels != null) {
            renderVideo(gfx);
        } else if (!decodeError.get()) {
            drawSpinner(gfx, now);
        }

        // Fade in
        if (elapsed < FADE_IN_MS) {
            gfx.fill(0, 0, width, height, (int)((1.0 - elapsed / (double)FADE_IN_MS) * 255) << 24);
        }

        // Skip hint
        if (elapsed > SKIP_DELAY_MS) drawSkipHint(gfx, now);

        if ((playbackEnded || decodeError.get()) && !advanced) advance();
    }

    // ── Texture ───────────────────────────────────────────────────────────────
    private void uploadFrame() {
        int w = videoWidth, h = videoHeight;
        if (videoTexture == null || texW != w || texH != h) {
            if (videoTexture != null) videoTexture.close();
            videoTexture = new DynamicTexture(
                    new NativeImage(NativeImage.Format.RGBA, w, h, false));
            texW = w; texH = h;
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
                // NativeImage is ABGR on little-endian
                img.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        videoTexture.upload();
    }

    /** Cover-scale the video onto the screen. */
    private void renderVideo(GuiGraphics gfx) {
        int vw = videoWidth, vh = videoHeight;
        float scale = Math.max((float)width / vw, (float)height / vh);
        int dw = (int)(vw * scale), dh = (int)(vh * scale);
        int dx = (width - dw) / 2, dy = (height - dh) / 2;

        var loc = Minecraft.getInstance().getTextureManager()
                           .register("intro_video", videoTexture);
        gfx.blit(loc, dx, dy, dw, dh, 0, 0, vw, vh, vw, vh);
    }

    private void drawSpinner(GuiGraphics gfx, long now) {
        int cx = width / 2, cy = height / 2;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8.0 + now / 300.0;
            int sx = cx + (int)(Math.cos(angle) * 18);
            int sy = cy + (int)(Math.sin(angle) * 18);
            int a  = (i == (int)((now / 110) % 8)) ? 230 : 60;
            gfx.fill(sx - 2, sy - 2, sx + 2, sy + 2, (a << 24) | 0xAA88FF);
        }
        gfx.drawCenteredString(font, "§7Loading…", cx, cy + 30, 0x88778899);
    }

    private void drawSkipHint(GuiGraphics gfx, long now) {
        float p = 0.5f + 0.5f * (float)Math.abs(Math.sin(now / 700.0));
        int   a = (int)(p * 195);
        String s = "[ SPACE ]  Skip";
        gfx.drawString(font, s, width - font.width(s) - 14, height - 20,
                       (a << 24) | 0xBBBBBB, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 32 || keyCode == 257)
                && System.currentTimeMillis() - startMs > SKIP_DELAY_MS && !advanced) {
            advance(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0 && System.currentTimeMillis() - startMs > SKIP_DELAY_MS && !advanced) {
            advance(); return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void advance() {
        if (advanced) return;
        advanced = true;
        if (decodeThread != null) decodeThread.interrupt();
        Minecraft.getInstance().execute(
                () -> Minecraft.getInstance().setScreen(new CustomTitleScreen()));
    }
}

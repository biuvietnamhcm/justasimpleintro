package com.example.mixin.client;

import com.example.intro.CustomLoadingOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Replaces Minecraft's vanilla LoadingOverlay with our custom animated one.
 *
 * A short frame delay (SWAP_DELAY_FRAMES) is intentional: the font renderer
 * and other early resources aren't ready on frame 0, which causes square-box
 * glyphs if we swap immediately.
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;

    /** Counts render calls before we perform the swap. */
    private int     frameDelay = 0;
    private boolean swapped    = false;

    /**
     * Number of vanilla frames to let pass before swapping.
     * 3 frames is enough for mc.font to be non-null on all tested machines;
     * raise to 5 if you still see glyph corruption on slow hardware.
     */
    private static final int SWAP_DELAY_FRAMES = 3;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics gfx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (swapped) return; // shouldn't happen, but guard anyway

        frameDelay++;

        if (frameDelay < SWAP_DELAY_FRAMES) {
            // Blank out the vanilla Mojang splash for these early frames
            // so the user sees nothing rather than the default screen.
            int W = minecraft.getWindow().getGuiScaledWidth();
            int H = minecraft.getWindow().getGuiScaledHeight();
            gfx.fill(0, 0, W, H, 0xFF000000);
            ci.cancel();
            return;
        }

        // Font should be ready — perform the one-time swap
        swapped = true;
        minecraft.setOverlay(new CustomLoadingOverlay(minecraft, reload, onFinish, true));
        ci.cancel(); // skip vanilla render on this final swap frame too
    }
}
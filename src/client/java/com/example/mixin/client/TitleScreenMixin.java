package com.example.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.intro.VideoScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Intercepts TitleScreen after it initialises.
 *
 * Every time Minecraft would show TitleScreen (first launch, returning
 * from a world, returning from multiplayer, etc.) we replace it with
 * VideoScreen instead.
 *
 * VideoScreen's static state means the video keeps running from where
 * it left off on every return — no restart, no black flash.
 *
 * FabricPreloadScreen / CustomTitleScreen are no longer needed here;
 * VideoScreen handles both the intro phase and the menu phase itself.
 *
 * IMPORTANT: Schedule the screen change on the next tick to avoid
 * NullPointerException from Fabric's ScreenEvents.afterRender() being
 * called while the screen is null during this render frame.
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Schedule screen change for next tick to avoid NPE in ScreenEvents
        Minecraft mc = Minecraft.getInstance();
        mc.tell(() -> mc.setScreen(new VideoScreen()));
    }
}

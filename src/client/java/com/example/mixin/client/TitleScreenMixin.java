package com.example.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.example.intro.FabricPreloadScreen;
import com.example.intro.VideoScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Intercepts TitleScreen after it initialises and redirects to
 * FabricPreloadScreen, which then advances to VideoScreen once done.
 *
 * Flow:
 *   TitleScreen.init()
 *     └─► FabricPreloadScreen  (3.2 s animated loading bar)
 *           └─► VideoScreen    (intro video + menu)
 *
 * On return from a world / multiplayer, VideoScreen's static decode
 * thread keeps running so the video resumes seamlessly — but the
 * preload screen is NOT shown again on those returns because
 * TitleScreenMixin only fires when Minecraft actually shows TitleScreen,
 * and VideoScreen.navigate() uses a fresh TitleScreen as parent so
 * mc.setScreen(parent) → TitleScreen.init() → this mixin fires again.
 *
 * If you do NOT want the preload shown again on every return, guard it
 * with a static boolean (see FabricPreloadScreen.s_shown below).
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // Schedule on next tick to avoid NPE from Fabric's ScreenEvents.afterRender()
        mc.tell(() -> {
            if (!FabricPreloadScreen.s_shown) {
                // First launch: show the preload screen
                mc.setScreen(new FabricPreloadScreen());
            } else {
                // Returning from a world / multiplayer: go straight to video
                mc.setScreen(new VideoScreen());
            }
        });
    }
}

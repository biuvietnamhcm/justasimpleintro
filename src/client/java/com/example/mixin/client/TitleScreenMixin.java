package com.example.mixin.client;

import com.example.intro.CustomTitleScreen;
import com.example.intro.FabricPreloadScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts TitleScreen after it initializes.
 * First launch → Preload → Video → Custom menu
 * Later launches → Custom menu directly
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    private static boolean introPlayed = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {

        Minecraft client = Minecraft.getInstance();

        if (!introPlayed) {
            introPlayed = true;
            client.setScreen(new FabricPreloadScreen());
        } else {
            client.setScreen(new CustomTitleScreen());
        }
    }
}
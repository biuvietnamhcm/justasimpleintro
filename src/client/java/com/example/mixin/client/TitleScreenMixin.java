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
 * Intercepts every {@link TitleScreen} initialisation:
 * <ul>
 *   <li>First launch → FabricPreloadScreen → VideoScreen → CustomTitleScreen</li>
 *   <li>All subsequent (return from Options, etc.) → CustomTitleScreen directly</li>
 * </ul>
 */
@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    /** Whether the one-time intro (preload + video) has already played. */
    private static boolean introPlayed = false;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void onInit(CallbackInfo ci) {
        if (!introPlayed) {
            introPlayed = true;
            // Full intro flow: preload animation → video → custom menu
            Minecraft.getInstance().setScreen(new FabricPreloadScreen());
        } else {
            // Skip intro; show custom menu immediately
            Minecraft.getInstance().setScreen(new CustomTitleScreen());
        }
        ci.cancel();
    }
}

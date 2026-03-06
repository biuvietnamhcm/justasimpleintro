package com.example;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry-point for the intro mod.
 *
 * The actual intro logic is driven by {@link com.example.mixin.client.TitleScreenMixin}
 * which intercepts the first {@code TitleScreen.init()} and redirects to:
 *
 * <pre>
 *   FabricPreloadScreen  →  VideoScreen  →  EnterGameScreen  →  TitleScreen
 * </pre>
 */
public class ExampleModClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("intro-mod");

    @Override
    public void onInitializeClient() {
        LOGGER.info("[IntroMod] Client initialised — intro will show on first TitleScreen.");
    }
}
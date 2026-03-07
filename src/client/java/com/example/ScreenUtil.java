package com.example.intro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ScreenUtil {

    public static void setScreen(Screen screen) {
        Minecraft client = Minecraft.getInstance();

        if (screen == null) {
            return; // Prevent Fabric ScreenEvents crash
        }

        if (client.isSameThread()) {
            client.setScreen(screen);
        } else {
            client.execute(() -> client.setScreen(screen));
        }
    }
}
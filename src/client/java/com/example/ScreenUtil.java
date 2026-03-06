package com.example.intro;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class ScreenUtil {

    public static void setScreen(Screen screen) {
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(screen);
        });
    }

}
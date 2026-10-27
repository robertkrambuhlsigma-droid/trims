package com.example.trimpreview.client;

import com.example.trimpreview.TrimPreviewMod;
import com.example.trimpreview.network.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.HandledScreens;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TrimPreviewClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        HandledScreens.register(TrimPreviewMod.MENU_TYPE, TrimPreviewScreen::new);

        // VERIFY: in newer 1.21.x builds the category argument takes KeyBinding.Category.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.trimpreview.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.trimpreview"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new Payloads.OpenMenu());
                }
            }
        });
    }
}

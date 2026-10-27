package com.example.trimpreview.client;

import com.example.trimpreview.TrimPreviewMod;
import com.example.trimpreview.network.Payloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TrimPreviewClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        MenuScreens.register(TrimPreviewMod.MENU_TYPE, TrimPreviewScreen::new);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(TrimPreviewMod.MOD_ID, "general"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.trimpreview.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.consumeClick()) {
                if (client.player != null) {
                    ClientPlayNetworking.send(new Payloads.OpenMenu());
                }
            }
        });
    }
}

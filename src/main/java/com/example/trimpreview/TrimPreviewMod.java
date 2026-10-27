package com.example.trimpreview;

import com.example.trimpreview.menu.TrimPreviewMenu;
import com.example.trimpreview.network.Payloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Blocks;

public class TrimPreviewMod implements ModInitializer {
    public static final String MOD_ID = "trimpreview";

    public static final MenuType<TrimPreviewMenu> MENU_TYPE = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(MOD_ID, "trim_preview"),
            new MenuType<>(TrimPreviewMenu::new, FeatureFlagSet.of()));

    @Override
    public void onInitialize() {
        // Payload types must be registered on both sides before use.
        PayloadTypeRegistry.serverboundPlay().register(Payloads.OpenMenu.TYPE, Payloads.OpenMenu.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.ApplyTrim.TYPE, Payloads.ApplyTrim.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.OpenMenu.TYPE, (payload, context) ->
                context.server().execute(() -> openMenu(context.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ApplyTrim.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    // Only act if the player actually has our menu open.
                    if (context.player().containerMenu instanceof TrimPreviewMenu menu) {
                        menu.applyTrim(context.player(), payload.pattern(), payload.material());
                    }
                }));

        // Sneak + right-click a smithing table opens the preview.
        // Plain right-click is untouched, so vanilla smithing still works.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).is(Blocks.SMITHING_TABLE)) {
                return InteractionResult.PASS;
            }
            openMenu((ServerPlayer) player);
            return InteractionResult.SUCCESS;
        });
    }

    public static void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new TrimPreviewMenu(syncId, inv),
                Component.translatable("screen." + MOD_ID + ".title")));
    }
}

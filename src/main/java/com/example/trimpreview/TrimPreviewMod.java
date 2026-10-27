package com.example.trimpreview;

import com.example.trimpreview.menu.TrimPreviewMenu;
import com.example.trimpreview.network.Payloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.FeatureSet;

public class TrimPreviewMod implements ModInitializer {
    public static final String MOD_ID = "trimpreview";

    public static final ScreenHandlerType<TrimPreviewMenu> MENU_TYPE = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(MOD_ID, "trim_preview"),
            new ScreenHandlerType<>(TrimPreviewMenu::new, FeatureSet.empty()));

    @Override
    public void onInitialize() {
        // Payload types must be registered on both sides before use.
        PayloadTypeRegistry.playC2S().register(Payloads.OpenMenu.ID, Payloads.OpenMenu.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.ApplyTrim.ID, Payloads.ApplyTrim.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Payloads.OpenMenu.ID, (payload, ctx) ->
                ctx.server().execute(() -> openMenu(ctx.player())));

        ServerPlayNetworking.registerGlobalReceiver(Payloads.ApplyTrim.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    // Only act if the player actually has our menu open.
                    if (ctx.player().currentScreenHandler instanceof TrimPreviewMenu menu) {
                        menu.applyTrim(ctx.player(), payload.pattern(), payload.material());
                    }
                }));

        // Sneak + right-click a smithing table opens the preview.
        // Plain right-click is untouched, so vanilla smithing still works.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND || !player.isSneaking()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.SMITHING_TABLE)) {
                return ActionResult.PASS;
            }
            openMenu((ServerPlayerEntity) player);
            return ActionResult.SUCCESS;
        });
    }

    public static void openMenu(ServerPlayerEntity player) {
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> new TrimPreviewMenu(syncId, inv),
                Text.translatable("screen." + MOD_ID + ".title")));
    }
}

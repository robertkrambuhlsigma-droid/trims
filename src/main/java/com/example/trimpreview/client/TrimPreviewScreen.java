package com.example.trimpreview.client;

import com.example.trimpreview.menu.TrimPreviewMenu;
import com.example.trimpreview.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
// VERIFY: 1.21.2+ package. Older 1.21.x uses net.minecraft.item.trim.*
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;

import java.util.ArrayList;
import java.util.List;

public class TrimPreviewScreen extends HandledScreen<TrimPreviewMenu> {
    private static final int W = 248;
    private static final int H = 256;

    private enum Piece {
        HELMET("helmet", EquipmentSlot.HEAD),
        CHESTPLATE("chestplate", EquipmentSlot.CHEST),
        LEGGINGS("leggings", EquipmentSlot.LEGS),
        BOOTS("boots", EquipmentSlot.FEET);

        final String suffix;
        final EquipmentSlot slot;

        Piece(String suffix, EquipmentSlot slot) {
            this.suffix = suffix;
            this.slot = slot;
        }

        Item itemFor(Tier tier) {
            return Registries.ITEM.get(Identifier.ofVanilla(tier.prefix + "_" + suffix));
        }
    }

    private enum Tier {
        LEATHER("leather"), IRON("iron"), GOLD("golden"), DIAMOND("diamond"), NETHERITE("netherite");

        final String prefix; // Gold armor items are "golden_*", not "gold_*"

        Tier(String prefix) {
            this.prefix = prefix;
        }
    }

    private final List<Identifier> patterns = new ArrayList<>();
    private final List<Identifier> materials = new ArrayList<>();
    private Identifier selectedPattern;
    private Identifier selectedMaterial;
    private Piece selectedPiece = Piece.CHESTPLATE;
    private Tier selectedTier = Tier.DIAMOND;

    private ArmorStandEntity previewStand;
    private ItemStack previewIcon = ItemStack.EMPTY;

    public TrimPreviewScreen(TrimPreviewMenu handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = W;
        this.backgroundHeight = H;
        this.playerInventoryTitleY = 160;
    }

    @Override
    protected void init() {
        super.init();
        var registries = client.world.getRegistryManager();

        // Pull everything from the registry, including data pack additions.
        patterns.clear();
        patterns.addAll(registries.getOrThrow(RegistryKeys.TRIM_PATTERN).getIds().stream().sorted().toList());
        materials.clear();
        materials.addAll(registries.getOrThrow(RegistryKeys.TRIM_MATERIAL).getIds().stream().sorted().toList());

        if (selectedPattern == null && !patterns.isEmpty()) selectedPattern = patterns.get(0);
        if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);

        // A 3D model: armor stand wearing the full selected set. Built client-side only.
        previewStand = new ArmorStandEntity(client.world, 0, 0, 0);
        previewStand.setShowArms(true);

        // Pattern grid: 4 columns, top-right.
        for (int i = 0; i < patterns.size(); i++) {
            Identifier id = patterns.get(i);
            int col = i % 4, row = i / 4;
            RegistryEntry<ArmorTrimPattern> entry = lookup(registries, RegistryKeys.TRIM_PATTERN, id);
            ItemStack icon = entry == null ? new ItemStack(Items.BARRIER)
                    : entry.value().templateItem().value().getDefaultStack(); // VERIFY accessor
            addDrawableChild(new IconSelector(
                    x + 136 + col * 18, y + 18 + row * 18, icon, Text.literal(id.getPath()),
                    () -> id.equals(selectedPattern),
                    () -> { selectedPattern = id; refreshPreview(); }));
        }

        // Material grid: 5 columns, bottom-left.
        for (int i = 0; i < materials.size(); i++) {
            Identifier id = materials.get(i);
            int col = i % 5, row = i / 5;
            RegistryEntry<ArmorTrimMaterial> entry = lookup(registries, RegistryKeys.TRIM_MATERIAL, id);
            ItemStack icon = entry == null ? new ItemStack(Items.BARRIER)
                    : entry.value().ingredient().value().getDefaultStack(); // VERIFY accessor
            addDrawableChild(new IconSelector(
                    x + 10 + col * 18, y + 124 + row * 18, icon, Text.literal(id.getPath()),
                    () -> id.equals(selectedMaterial),
                    () -> { selectedMaterial = id; refreshPreview(); }));
        }

        // Tier selectors.
        for (int i = 0; i < Tier.values().length; i++) {
            Tier t = Tier.values()[i];
            addDrawableChild(new IconSelector(
                    x + 112 + i * 18, y + 124, new ItemStack(Piece.CHESTPLATE.itemFor(t)),
                    Text.literal(t.name().toLowerCase()),
                    () -> selectedTier == t,
                    () -> { selectedTier = t; refreshPreview(); }));
        }

        // Piece selectors.
        for (int i = 0; i < Piece.values().length; i++) {
            Piece p = Piece.values()[i];
            addDrawableChild(new IconSelector(
                    x + 112 + i * 18, y + 146, new ItemStack(p.itemFor(Tier.IRON)),
                    Text.literal(p.suffix),
                    () -> selectedPiece == p,
                    () -> { selectedPiece = p; refreshPreview(); }));
        }

        // Apply: the server validates ingredients and the armor in the input slot.
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.trimpreview.apply"), b -> {
                    if (selectedPattern != null && selectedMaterial != null) {
                        ClientPlayNetworking.send(new Payloads.ApplyTrim(selectedPattern, selectedMaterial));
                    }
                })
                .dimensions(x + 190, y + 146, 52, 18)
                .build());

        refreshPreview();
    }

    /** Rebuilds the preview stacks whenever a selection changes. */
    private void refreshPreview() {
        if (client == null || client.world == null || previewStand == null) return;
        var registries = client.world.getRegistryManager();
        RegistryEntry<ArmorTrimPattern> pattern = lookup(registries, RegistryKeys.TRIM_PATTERN, selectedPattern);
        RegistryEntry<ArmorTrimMaterial> material = lookup(registries, RegistryKeys.TRIM_MATERIAL, selectedMaterial);

        for (Piece p : Piece.values()) {
            previewStand.equipStack(p.slot, trimmed(p.itemFor(selectedTier), pattern, material));
        }
        previewIcon = trimmed(selectedPiece.itemFor(selectedTier), pattern, material);
    }

    private static ItemStack trimmed(Item item, RegistryEntry<ArmorTrimPattern> pattern,
                                     RegistryEntry<ArmorTrimMaterial> material) {
        ItemStack stack = new ItemStack(item);
        if (pattern != null && material != null) {
            stack.set(DataComponentTypes.TRIM, new ArmorTrim(material, pattern));
        }
        return stack;
    }

    private static <T> RegistryEntry<T> lookup(DynamicRegistryManager registries,
                                               RegistryKey<Registry<T>> key, Identifier id) {
        if (id == null) return null;
        return registries.getOrThrow(key).getEntry(id).orElse(null);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Translucent panel in place of the vanilla texture (the widgets draw on top).
        context.fill(x, y, x + W, y + H, 0xD0101010);
        context.fill(x + 36, y + 16, x + 116, y + 116, 0xFF181818);

        if (previewStand != null) {
            // Signature changed across 1.21.x. This matches the 1.21.2+ form.
            // VERIFY: older versions take (context, x, y, size, mouseX, mouseY, entity).
            InventoryScreen.drawEntity(context,
                    x + 36, y + 16, x + 116, y + 116,
                    28, 0.0625F, mouseX, mouseY, previewStand);
        }

        context.drawItem(previewIcon, x + 10, y + 96);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

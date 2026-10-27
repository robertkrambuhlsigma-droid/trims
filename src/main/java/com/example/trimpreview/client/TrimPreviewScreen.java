package com.example.trimpreview.client;

import com.example.trimpreview.menu.TrimPreviewMenu;
import com.example.trimpreview.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class TrimPreviewScreen extends AbstractContainerScreen<TrimPreviewMenu> {
    private static final int W = 248;
    private static final int H = 256;

    private enum Piece {
        HELMET("helmet"),
        CHESTPLATE("chestplate"),
        LEGGINGS("leggings"),
        BOOTS("boots");

        final String suffix;

        Piece(String suffix) {
            this.suffix = suffix;
        }

        Item itemFor(Tier tier) {
            return BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("minecraft", tier.prefix + "_" + suffix));
        }
    }

    private enum Tier {
        LEATHER("leather"), IRON("iron"), GOLD("golden"), DIAMOND("diamond"), NETHERITE("netherite");

        final String prefix; // Gold armor items are "golden_*", not "gold_*"

        Tier(String prefix) {
            this.prefix = prefix;
        }
    }

    /** A clickable item-icon cell with a selected outline. */
    private record Cell(int x, int y, ItemStack icon, BooleanSupplier selected) {}

    private final List<Cell> cells = new ArrayList<>();
    private final List<Identifier> patterns = new ArrayList<>();
    private final List<Identifier> materials = new ArrayList<>();
    private Identifier selectedPattern;
    private Identifier selectedMaterial;
    private Piece selectedPiece = Piece.CHESTPLATE;
    private Tier selectedTier = Tier.DIAMOND;
    private ItemStack previewIcon = ItemStack.EMPTY;

    public TrimPreviewScreen(TrimPreviewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventoryLabelY = 160;
    }

    @Override
    protected void init() {
        super.init();
        RegistryAccess registries = minecraft.level.registryAccess();

        // Pull everything from the registry, including data pack additions.
        patterns.clear();
        registries.lookupOrThrow(Registries.TRIM_PATTERN).listElementIds()
                .map(ResourceKey::identifier).sorted().forEach(patterns::add);
        materials.clear();
        registries.lookupOrThrow(Registries.TRIM_MATERIAL).listElementIds()
                .map(ResourceKey::identifier).sorted().forEach(materials::add);

        if (selectedPattern == null && !patterns.isEmpty()) selectedPattern = patterns.get(0);
        if (selectedMaterial == null && !materials.isEmpty()) selectedMaterial = materials.get(0);

        cells.clear();

        // Pattern grid: 4 columns, top-right.
        for (int i = 0; i < patterns.size(); i++) {
            Identifier id = patterns.get(i);
            int col = i % 4, row = i / 4;
            Holder<TrimPattern> entry = lookup(registries, Registries.TRIM_PATTERN, id);
            ItemStack icon = entry == null ? new ItemStack(Items.BARRIER)
                    : entry.value().templateItem().value().getDefaultInstance();
            addCell(leftPos + 136 + col * 18, topPos + 18 + row * 18, icon,
                    () -> id.equals(selectedPattern),
                    () -> { selectedPattern = id; refreshPreview(); });
        }

        // Material grid: 5 columns, bottom-left.
        for (int i = 0; i < materials.size(); i++) {
            Identifier id = materials.get(i);
            int col = i % 5, row = i / 5;
            Holder<TrimMaterial> entry = lookup(registries, Registries.TRIM_MATERIAL, id);
            ItemStack icon = entry == null ? new ItemStack(Items.BARRIER)
                    : entry.value().ingredient().value().getDefaultInstance();
            addCell(leftPos + 10 + col * 18, topPos + 124 + row * 18, icon,
                    () -> id.equals(selectedMaterial),
                    () -> { selectedMaterial = id; refreshPreview(); });
        }

        // Tier selectors.
        for (int i = 0; i < Tier.values().length; i++) {
            Tier t = Tier.values()[i];
            addCell(leftPos + 112 + i * 18, topPos + 124, new ItemStack(Piece.CHESTPLATE.itemFor(t)),
                    () -> selectedTier == t,
                    () -> { selectedTier = t; refreshPreview(); });
        }

        // Piece selectors.
        for (int i = 0; i < Piece.values().length; i++) {
            Piece p = Piece.values()[i];
            addCell(leftPos + 112 + i * 18, topPos + 146, new ItemStack(p.itemFor(Tier.IRON)),
                    () -> selectedPiece == p,
                    () -> { selectedPiece = p; refreshPreview(); });
        }

        // Apply: the server validates ingredients and the armor in the input slot.
        addRenderableWidget(Button.builder(Component.translatable("gui.trimpreview.apply"), b -> {
                    if (selectedPattern != null && selectedMaterial != null) {
                        ClientPlayNetworking.send(new Payloads.ApplyTrim(selectedPattern, selectedMaterial));
                    }
                })
                .bounds(leftPos + 190, topPos + 146, 52, 18)
                .build());

        refreshPreview();
    }

    /** Adds an invisible button for hit-testing; the icon and outline are drawn in extractRenderState. */
    private void addCell(int x, int y, ItemStack icon, BooleanSupplier selected, Runnable action) {
        cells.add(new Cell(x, y, icon, selected));
        addRenderableWidget(Button.builder(Component.empty(), b -> action.run())
                .bounds(x, y, 18, 18)
                .build());
    }

    /** Rebuilds the preview icon whenever a selection changes. */
    private void refreshPreview() {
        if (minecraft == null || minecraft.level == null) return;
        RegistryAccess registries = minecraft.level.registryAccess();
        Holder<TrimPattern> pattern = lookup(registries, Registries.TRIM_PATTERN, selectedPattern);
        Holder<TrimMaterial> material = lookup(registries, Registries.TRIM_MATERIAL, selectedMaterial);
        previewIcon = trimmed(selectedPiece.itemFor(selectedTier), pattern, material);
    }

    private static ItemStack trimmed(Item item, Holder<TrimPattern> pattern,
                                     Holder<TrimMaterial> material) {
        ItemStack stack = new ItemStack(item);
        if (pattern != null && material != null) {
            stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
        }
        return stack;
    }

    private static <T> Holder<T> lookup(RegistryAccess registries, ResourceKey<? extends Registry<T>> key,
                                        Identifier id) {
        if (id == null) return null;
        return registries.lookupOrThrow(key).get(ResourceKey.create(key, id)).orElse(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        for (Cell cell : cells) {
            int border = cell.selected().getAsBoolean() ? 0xFFFFD700 : 0xFF404040;
            outline(graphics, cell.x(), cell.y(), 18, 18, border);
            graphics.item(cell.icon(), cell.x() + 1, cell.y() + 1);
        }

        graphics.item(previewIcon, leftPos + 10, topPos + 96);
        graphics.text(this.font,
                selectedPattern == null ? "" : selectedPattern.getPath() + " / "
                        + (selectedMaterial == null ? "" : selectedMaterial.getPath()),
                leftPos + 10, topPos + 232 - 20, 0xFFFFFFFF, true);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

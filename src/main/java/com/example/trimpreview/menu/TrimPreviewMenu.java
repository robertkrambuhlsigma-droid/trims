package com.example.trimpreview.menu;

import com.example.trimpreview.TrimPreviewMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
// VERIFY: 1.21.2+ package. Older 1.21.x versions use net.minecraft.item.trim.*
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterial;
import net.minecraft.item.equipment.trim.ArmorTrimPattern;

public class TrimPreviewMenu extends ScreenHandler {
    public static final int ARMOR_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private final SimpleInventory input = new SimpleInventory(1);
    private final SimpleInventory output = new SimpleInventory(1);

    public TrimPreviewMenu(int syncId, PlayerInventory playerInv) {
        super(TrimPreviewMod.MENU_TYPE, syncId);

        // Slot 0: armor input. Only trimmable armor may be placed here.
        addSlot(new Slot(input, 0, 10, 18) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isIn(ItemTags.TRIMMABLE_ARMOR);
            }
        });

        // Slot 1: output. Take-only.
        addSlot(new Slot(output, 0, 10, 60) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return false;
            }
        });

        // Player inventory: 9x3 main, then hotbar. Positions match the Screen layout.
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, 43 + col * 18, 174 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, 43 + col * 18, 232));
    }

    /**
     * Server-authoritative apply. The client only sends IDs; everything else is validated here.
     * Consumes one smithing template and one material item from the player's inventory.
     */
    public void applyTrim(ServerPlayerEntity player, Identifier patternId, Identifier materialId) {
        ItemStack armor = input.getStack(0);
        if (armor.isEmpty()) {
            player.sendMessage(Text.translatable("message.trimpreview.no_armor"), true);
            return;
        }
        if (!output.getStack(0).isEmpty()) {
            return; // Previous result hasn't been taken yet.
        }

        var registries = player.getWorld().getRegistryManager();
        RegistryEntry<ArmorTrimPattern> pattern = registries.getOrThrow(RegistryKeys.TRIM_PATTERN)
                .getEntry(patternId).orElse(null);
        RegistryEntry<ArmorTrimMaterial> material = registries.getOrThrow(RegistryKeys.TRIM_MATERIAL)
                .getEntry(materialId).orElse(null);
        if (pattern == null || material == null) {
            return; // Unknown or removed registry entry.
        }

        // Ingredient items come from the registry, so data pack entries work with no code changes.
        Item templateItem = pattern.value().templateItem().value();     // VERIFY accessor name
        Item ingredientItem = material.value().ingredient().value();    // VERIFY accessor name

        var inv = player.getInventory();
        if (inv.count(templateItem) < 1 || inv.count(ingredientItem) < 1) {
            player.sendMessage(Text.translatable("message.trimpreview.missing"), true);
            return;
        }

        // Consume ingredients (survival rules). Creative skip can be added here.
        consume(inv, templateItem);
        consume(inv, ingredientItem);

        ItemStack result = armor.copyWithCount(1);
        result.set(DataComponentTypes.TRIM, new ArmorTrim(material, pattern));

        output.setStack(0, result);
        input.setStack(0, ItemStack.EMPTY);
        inv.markDirty();
        sendContentUpdates();
    }

    private static void consume(PlayerInventory inv, Item item) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isOf(item)) {
                stack.decrement(1);
                return;
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) {
            return result;
        }

        ItemStack stack = slot.getStack();
        result = stack.copy();

        if (index <= OUTPUT_SLOT) {
            // From menu slots to player inventory (slots 2..37).
            if (!insertItem(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory into the armor input, if trimmable.
            if (stack.isIn(ItemTags.TRIMMABLE_ARMOR)) {
                if (!insertItem(stack, ARMOR_SLOT, ARMOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (index == OUTPUT_SLOT) {
            slot.onTakeItem(player, stack);
        }
        return result;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // Return anything still sitting in the menu slots.
        dropInventory(player, input);
        dropInventory(player, output);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}

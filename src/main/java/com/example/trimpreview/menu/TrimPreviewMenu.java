package com.example.trimpreview.menu;

import com.example.trimpreview.TrimItems;
import com.example.trimpreview.TrimPreviewMod;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

public class TrimPreviewMenu extends AbstractContainerMenu {
    public static final int ARMOR_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    private final SimpleContainer input = new SimpleContainer(1);
    private final SimpleContainer output = new SimpleContainer(1);

    public TrimPreviewMenu(int containerId, Inventory playerInv) {
        super(TrimPreviewMod.MENU_TYPE, containerId);

        // Slot 0: armor input. Only trimmable armor may be placed here.
        addSlot(new Slot(input, 0, 10, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isTrimmableArmor(stack);
            }
        });

        // Slot 1: output. Take-only.
        addSlot(new Slot(output, 0, 10, 60) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Player inventory: 9x3 main, then hotbar.
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
    public void applyTrim(ServerPlayer player, Identifier patternId, Identifier materialId) {
        ItemStack armor = input.getItem(0);
        if (!armor.isEmpty() && !isTrimmableArmor(armor)) {
            player.sendSystemMessage(Component.literal("[Trim] That item can't be trimmed."));
            return;
        }
        if (armor.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.trimpreview.no_armor"));
            return;
        }

        var registries = player.level().registryAccess();
        Holder<TrimPattern> pattern = registries.lookupOrThrow(Registries.TRIM_PATTERN)
                .get(ResourceKey.create(Registries.TRIM_PATTERN, patternId)).orElse(null);
        Holder<TrimMaterial> material = registries.lookupOrThrow(Registries.TRIM_MATERIAL)
                .get(ResourceKey.create(Registries.TRIM_MATERIAL, materialId)).orElse(null);
        if (pattern == null || material == null) {
            player.sendSystemMessage(Component.literal("[Trim] Unknown pattern or material: "
                    + patternId + " / " + materialId));
            return; // Unknown or removed registry entry.
        }

        // Ingredient items come from the registry, so data pack entries work with no code changes.
        Item templateItem = TrimItems.templateFor(patternId);
        Item ingredientItem = TrimItems.ingredientFor(materialId);

        // No template or ingredient is needed or used up; the trim is free.
        var inv = player.getInventory();

        ItemStack result = armor.copyWithCount(1);
        result.set(DataComponents.TRIM, new ArmorTrim(material, pattern));

        // The trimmed armor replaces the input armor in place, so it looks like the armor changed.
        input.setItem(0, result);
        broadcastChanges();
        player.sendSystemMessage(Component.literal("[Trim] Applied " + patternId.getPath()
                + " with " + materialId.getPath() + "."));
    }

    /**
     * True for armor that can take a trim. Checks the vanilla trimmable-armor tag first,
     * then falls back to vanilla armor names so a changed or missing tag can't block it.
     */
    public static boolean isTrimmableArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ItemTags.TRIMMABLE_ARMOR)) {
            return true;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        return id.getNamespace().equals("minecraft")
                && (path.endsWith("_helmet") || path.endsWith("_chestplate")
                    || path.endsWith("_leggings") || path.endsWith("_boots"));
    }

    private static void consume(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index <= OUTPUT_SLOT) {
            // From menu slots to player inventory (slots 2..37).
            if (!this.moveItemStackTo(stack, 2, 38, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory into the armor input, if trimmable.
            if (isTrimmableArmor(stack)) {
                if (!this.moveItemStackTo(stack, ARMOR_SLOT, ARMOR_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (index == OUTPUT_SLOT) {
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Return anything still sitting in the menu slots.
        clearContainer(player, input);
        clearContainer(player, output);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

package com.example.trimpreview;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * Finds the smithing template and ingredient item for a trim pattern or material.
 * 26.2 no longer stores these on the trim itself, so vanilla naming is used instead.
 */
public final class TrimItems {
    private TrimItems() {}

    public static Item templateFor(Identifier pattern) {
        return item(pattern.getPath() + "_armor_trim_smithing_template");
    }

    public static Item ingredientFor(Identifier material) {
        return switch (material.getPath()) {
            case "amethyst" -> item("amethyst_shard");
            case "copper" -> item("copper_ingot");
            case "gold" -> item("gold_ingot");
            case "iron" -> item("iron_ingot");
            case "lapis" -> item("lapis_lazuli");
            case "netherite" -> item("netherite_ingot");
            case "resin" -> item("resin_brick");
            default -> item(material.getPath());
        };
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", path));
    }
}

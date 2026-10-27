package com.example.trimpreview.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** A square, clickable item-icon button with a selected highlight and hover tooltip. */
public class IconSelector extends PressableWidget {
    private final ItemStack icon;
    private final Text label;
    private final BooleanSupplier selected;
    private final Runnable action;

    public IconSelector(int x, int y, ItemStack icon, Text label,
                        BooleanSupplier selected, Runnable action) {
        super(x, y, 18, 18, label);
        this.icon = icon;
        this.label = label;
        this.selected = selected;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int border = selected.getAsBoolean() ? 0xFFFFD700
                : (isHovered() ? 0xFFFFFFFF : 0xFF404040);
        context.fill(getX(), getY(), getX() + width, getY() + height, border);
        context.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0xFF1E1E1E);
        context.drawItem(icon, getX() + 1, getY() + 1);
        if (isHovered()) {
            context.drawTooltip(MinecraftClient.getInstance().textRenderer, label, mouseX, mouseY);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}

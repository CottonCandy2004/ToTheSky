package com.fst.tothesky.client.gui.calendar;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 纯贴图按钮：只绘制贴图，不绘制任何文本（文本仅用于无障碍朗读）。
 * 悬停/聚焦时轻微提亮，禁用时压暗，作为视觉反馈。
 */
public class CalendarIconButton extends Button {
    private final ResourceLocation texture;
    private final int textureSize;

    public CalendarIconButton(int x, int y, int textureSize, ResourceLocation texture,
                              Component narrationText, OnPress onPress) {
        super(x, y, textureSize, textureSize, narrationText, onPress, DEFAULT_NARRATION);
        this.texture = texture;
        this.textureSize = textureSize;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 默认 1:1 原始颜色；悬停略暗、禁用压暗，作为反馈。
        // 只用 isHovered()：点按后按钮保留键盘焦点，用 isHoveredOrFocused() 会让
        // 鼠标移开后仍显示按下态（残留变暗）。
        float tint = !this.active ? 0.5F : (this.isHovered() ? 0.85F : 1.0F);
        graphics.setColor(tint, tint, tint, 1.0F);
        graphics.blit(this.texture, this.getX(), this.getY(), 0.0F, 0.0F,
                this.textureSize, this.textureSize, this.textureSize, this.textureSize);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
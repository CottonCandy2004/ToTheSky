package com.fst.tothesky.item;

import com.fst.tothesky.ToTheSky;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** 带多行灰色 tooltip 的物品基类，文本来自 lang 中的 tooltip.tothesky.&lt;name&gt;.&lt;i&gt; */
public class TooltipItem extends Item {
    private final String tooltipKey;
    private final int tooltipLines;

    public TooltipItem(Properties properties, String name, int tooltipLines) {
        super(properties);
        this.tooltipKey = "tooltip." + ToTheSky.MODID + "." + name;
        this.tooltipLines = tooltipLines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipAdder, TooltipFlag flag) {
        for (int i = 0; i < tooltipLines; i++) {
            tooltipAdder.add(Component.translatable(tooltipKey + "." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}

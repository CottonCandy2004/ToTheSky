package com.fst.tothesky.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import com.fst.tothesky.ToTheSky;

import java.util.List;

/** 带多行灰色 tooltip 的方块物品 */
public class TooltipBlockItem extends BlockItem {
    private final String tooltipKey;
    private final int tooltipLines;

    public TooltipBlockItem(Block block, Properties properties, String name, int tooltipLines) {
        super(block, properties);
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

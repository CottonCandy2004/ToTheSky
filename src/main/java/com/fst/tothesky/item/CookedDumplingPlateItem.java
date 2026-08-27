package com.fst.tothesky.item;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.registry.ModDataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 一盘熟饺子的物品形态：放置时把内容物组件写入方块实体 */
public class CookedDumplingPlateItem extends TooltipBlockItem {
    public CookedDumplingPlateItem(Block block, Properties properties) {
        super(block, properties, "cooked_dumpling_plate", 0);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            if (blockEntity instanceof DumplingPlateBlockEntity plate) {
                plate.setContents(context.getItemInHand().get(ModDataComponents.DUMPLING_PLATE));
            }
        }
        return result;
    }
}

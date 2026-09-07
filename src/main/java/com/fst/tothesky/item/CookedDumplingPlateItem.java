package com.fst.tothesky.item;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.registry.ModNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import com.fst.tothesky.block.CookedDumplingPlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 一盘熟饺子的物品形态：放置时把内容物组件写入方块实体 */
public class CookedDumplingPlateItem extends TooltipBlockItem {
    public CookedDumplingPlateItem(Block block, Properties properties) {
        super(block, properties, "cooked_dumpling_plate", 0);
    }

    /**
     * 手持一盘熟饺子右键已放置的空盘：把整盘内容转移到目标盘（kjs 的"饺子放置"分支）。
     * 目标盘为空（无馅料记录）时才转移；正常放置（右键非盘方块）不受影响。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos target = context.getClickedPos().relative(context.getClickedFace());
        if (level.getBlockState(context.getClickedPos()).getBlock() instanceof CookedDumplingPlateBlock
                || level.getBlockState(target).getBlock() instanceof CookedDumplingPlateBlock) {
            BlockPos platePos = level.getBlockState(context.getClickedPos()).getBlock() instanceof CookedDumplingPlateBlock
                    ? context.getClickedPos() : target;
            if (!level.isClientSide && level.getBlockEntity(platePos) instanceof DumplingPlateBlockEntity plate
                    && plate.contents() == null) {
                var tag = context.getItemInHand().getTag();
                if (tag != null && tag.contains(ModNbt.DUMPLING_PLATE)) {
                    plate.setContents(com.fst.tothesky.dumpling.DumplingPlateContents
                            .load(tag.getCompound(ModNbt.DUMPLING_PLATE)));
                    context.getItemInHand().shrink(1);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            if (blockEntity instanceof DumplingPlateBlockEntity plate) {
                var tag = context.getItemInHand().getTag();
                if (tag != null && tag.contains(ModNbt.DUMPLING_PLATE)) {
                    plate.setContents(com.fst.tothesky.dumpling.DumplingPlateContents
                            .load(tag.getCompound(ModNbt.DUMPLING_PLATE)));
                }
            }
        }
        return result;
    }
}
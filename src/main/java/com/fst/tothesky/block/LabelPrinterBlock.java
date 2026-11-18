package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 高度为 7/16 格的水平朝向标签打印机。 */
public class LabelPrinterBlock extends FacingBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

    public LabelPrinterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}

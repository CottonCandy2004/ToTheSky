package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 带固定碰撞箱的简单装饰方块 */
public class SimpleShapeBlock extends Block {
    private final VoxelShape shape;

    public SimpleShapeBlock(VoxelShape shape, Properties properties) {
        super(properties);
        this.shape = shape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}

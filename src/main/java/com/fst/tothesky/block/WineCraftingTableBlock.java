package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 酿酒工作台：带 AGE_2 属性（0-2）的水平朝向方块。
 * 酿酒逻辑不迁移，仅保留方块形态（移植自 kubejs/startup_scripts/wine_startup.js）。
 */
public class WineCraftingTableBlock extends FacingBlock {
    public static final IntegerProperty AGE_2 = IntegerProperty.create("age", 0, 2);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public WineCraftingTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(AGE_2, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE_2);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(AGE_2, 0);
    }
}

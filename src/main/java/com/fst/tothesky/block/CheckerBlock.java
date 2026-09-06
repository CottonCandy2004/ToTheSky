package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 检查点方块：放置/破坏时维护检查站索引，玩家经过时记录时间戳。
 * 数据存储在 config/CheckerData/ 下（移植自 kubejs checker_server.js）。
 */
public class CheckerBlock extends Block {
    public CheckerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) {
            com.fst.tothesky.event.CheckerEvents.onCheckerPlaced(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            com.fst.tothesky.event.CheckerEvents.onCheckerBroken(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}

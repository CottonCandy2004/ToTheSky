package com.fst.tothesky.block;

import com.simibubi.create.content.decoration.encasing.CasingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玻璃机壳：Create 机壳的玻璃版本（移植自 Create: Crystal Clear 的 {@code GlassCasing}）。
 * <p>
 * 与普通机壳的唯一区别是透光——玻璃与玻璃之间隐藏共面（{@link #skipRendering}），
 * 天光不衰减、亮度不遮挡。连接纹理由客户端注册的 {@code SimpleCTBehaviour} 负责。
 */
public class GlassCasingBlock extends CasingBlock {

    public GlassCasingBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.getBlock() instanceof GlassCasingBlock;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1f;
    }
}

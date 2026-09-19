package com.fst.tothesky.block;

import com.fst.tothesky.crystalclear.CrystalClearBlockEntities;
import com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 玻璃包裹齿轮 / 大齿轮（移植自 Create: Crystal Clear 的 {@code GlassEncasedCogwheel}）。
 * <p>
 * 小齿轮与大齿轮各一份方块实体类型（与原 mod 一致），按 {@code isLarge} 分派。
 */
public class GlassEncasedCogwheelBlock extends EncasedCogwheelBlock {

    public GlassEncasedCogwheelBlock(Properties properties, boolean large, Supplier<Block> casing) {
        super(properties, large, casing);
    }

    @Override
    public BlockEntityType<? extends SimpleKineticBlockEntity> getBlockEntityType() {
        return isLarge
                ? CrystalClearBlockEntities.GLASS_ENCASED_LARGE_COG.get()
                : CrystalClearBlockEntities.GLASS_ENCASED_COG.get();
    }

    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.getBlock() instanceof EncasedCogwheelBlock
                || adjacent.getBlock() instanceof EncasedShaftBlock;
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

package com.fst.tothesky.block;

import com.fst.tothesky.crystalclear.CrystalClearBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
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
 * 玻璃包裹传动杆（移植自 Create: Crystal Clear 的 {@code GlassEncasedShaft}）。
 * <p>
 * 除了方块实体类型指向本 mod 的注册项、以及玻璃面之间互相隐藏/透光外，行为完全继承 Create 的
 * {@link EncasedShaftBlock}：应力影响为 0、同上、扳手脱壳返回传动杆。
 */
public class GlassEncasedShaftBlock extends EncasedShaftBlock {

    public GlassEncasedShaftBlock(Properties properties, Supplier<Block> casing) {
        super(properties, casing);
    }

    @Override
    public BlockEntityType<? extends KineticBlockEntity> getBlockEntityType() {
        return CrystalClearBlockEntities.GLASS_ENCASED_SHAFT.get();
    }

    /** 相邻的包裹传动杆/齿轮箱都透光，共面一律隐藏 */
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

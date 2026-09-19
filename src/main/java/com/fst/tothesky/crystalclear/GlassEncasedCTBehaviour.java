package com.fst.tothesky.crystalclear;

import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玻璃包裹传动杆的连接纹理（移植自 Create: Crystal Clear 的 {@code GlassEncasedCTBehaviour}）。
 * <p>
 * 同类玻璃机壳之间直接相连（不看 Create 的“能否被遮挡”判定）；相邻的齿轮箱则一律不相连，
 * 因为齿轮箱的侧面贴图是另一套。
 */
public class GlassEncasedCTBehaviour extends EncasedCTBehaviour {

    public GlassEncasedCTBehaviour(CTSpriteShiftEntry shift) {
        super(shift);
    }

    @Override
    public boolean buildContextForOccludedDirections() {
        return true;
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState adjacent, BlockAndTintGetter level, BlockPos pos, BlockPos adjacentPos, Direction face) {
        if (adjacent.getBlock() == state.getBlock()) {
            return true;
        }
        if (adjacent.getBlock() instanceof EncasedCogwheelBlock) {
            return false;
        }
        return super.connectsTo(state, adjacent, level, pos, adjacentPos, face);
    }
}

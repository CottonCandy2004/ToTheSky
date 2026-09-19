package com.fst.tothesky.crystalclear;

import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogCTBehaviour;
import com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玻璃包裹齿轮的连接纹理（移植自 Create: Crystal Clear 的 {@code GlassEncasedCogCTBehaviour}）。
 * <p>
 * 与传动杆相反：相邻的包裹传动杆一律不相连。
 */
public class GlassEncasedCogCTBehaviour extends EncasedCogCTBehaviour {

    public GlassEncasedCogCTBehaviour(CTSpriteShiftEntry shift) {
        super(shift);
    }

    public GlassEncasedCogCTBehaviour(CTSpriteShiftEntry shift, Couple<CTSpriteShiftEntry> sideShifts) {
        super(shift, sideShifts);
    }

    @Override
    public boolean connectsTo(BlockState state, BlockState adjacent, BlockAndTintGetter level, BlockPos pos, BlockPos adjacentPos, Direction face) {
        if (adjacent.getBlock() instanceof EncasedShaftBlock) {
            return false;
        }
        return super.connectsTo(state, adjacent, level, pos, adjacentPos, face);
    }

    @Override
    public boolean buildContextForOccludedDirections() {
        return true;
    }
}

package com.fst.tothesky.block;

import com.fst.tothesky.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 日历方块：右键打开日历 GUI（服务端组包，客户端渲染）。
 * 完整立方体，无方块实体；数据在服务器级 CalendarData，方块仅是入口。
 */
public class CalendarBlock extends Block {

    public CalendarBlock(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ModNetwork.sendCalendar(serverPlayer, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
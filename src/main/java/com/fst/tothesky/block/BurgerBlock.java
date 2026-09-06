package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 汉堡模型：非潜行右键触发彩蛋（移植自 kubejs block_events.js）。
 */
public class BurgerBlock extends SimpleShapeBlock {
    public BurgerBlock(VoxelShape shape, Properties properties) {
        super(shape, properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (!player.isCrouching() && !level.isClientSide) {
            player.displayClientMessage(Component.literal("§a你的嘴巴似乎被什么粘住了"), true);
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("• §8<§7" + player.getName().getString() + "§8>: §f唔唔唔，呜呜！"), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

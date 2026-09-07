package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * 旧 KubeJS 披萨阶段方块（pizza_margarita2/3/4 等九个）的兼容形态。
 * 旧脚本用四个独立方块表示取片进度；mod 主方块改用 slices 属性，但这些阶段方块
 * 仍然注册（无物品、无掉落），保证旧存档 MissingMappings 同名 remap 后外观与行为不变：
 * 非潜行右键给一片切片并进入下一阶段（最后一阶段变空气）。
 */
public class PizzaStageBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 2, 14);

    private final Supplier<? extends Block> next;
    private final Supplier<? extends Item> sliceItem;

    public PizzaStageBlock(Properties properties, Supplier<? extends Block> next, Supplier<? extends Item> sliceItem) {
        super(properties);
        this.next = next;
        this.sliceItem = sliceItem;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack slice = new ItemStack(sliceItem.get());
            if (!player.addItem(slice)) {
                player.drop(slice, false);
            }
            Block nextBlock = next.get();
            level.setBlock(pos, nextBlock == null ? Blocks.AIR.defaultBlockState()
                    : nextBlock.defaultBlockState(), 3);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

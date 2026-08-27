package com.fst.tothesky.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * 整张披萨：非潜行右键拿取一片，拿完四片后消失。
 * 旧脚本每种披萨用四个独立方块表示阶段，这里合并为单一方块的 slices 属性。
 * 破坏时仅未动过的整张披萨掉落自身（见 loot_table）。
 */
public class PizzaBlock extends Block {
    /** 已被取走的片数：0=完整，3=仅剩一片 */
    public static final IntegerProperty SLICES = IntegerProperty.create("slices", 0, 3);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 2, 14);

    private final Supplier<? extends Item> sliceItem;

    public PizzaBlock(Properties properties, Supplier<? extends Item> sliceItem) {
        super(properties);
        this.sliceItem = sliceItem;
        registerDefaultState(stateDefinition.any().setValue(SLICES, 0));
    }

    @Override
    protected MapCodec<? extends PizzaBlock> codec() {
        return simpleCodec(props -> new PizzaBlock(props, sliceItem));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SLICES);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return takeSlice(state, level, pos, player) ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        return takeSlice(state, level, pos, player) ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    private boolean takeSlice(BlockState state, Level level, BlockPos pos, Player player) {
        if (player.isShiftKeyDown()) {
            return false;
        }
        if (!level.isClientSide) {
            ItemStack slice = new ItemStack(sliceItem.get());
            if (!player.addItem(slice)) {
                player.drop(slice, false);
            }
            int taken = state.getValue(SLICES);
            if (taken >= 3) {
                level.removeBlock(pos, false);
            } else {
                level.setBlock(pos, state.setValue(SLICES, taken + 1), 3);
            }
        }
        return true;
    }
}

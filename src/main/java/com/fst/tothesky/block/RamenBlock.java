package com.fst.tothesky.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 拉面：右键吃一口（4 饥饿 + 4 饱和度），共五口；
 * 吃完最后一口返还一个碗。破坏不掉落。
 */
public class RamenBlock extends Block {
    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 4);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public RamenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BITES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BITES);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        eat(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void eat(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        int bites = state.getValue(BITES);
        if (bites >= 4) {
            ItemStack bowl = new ItemStack(Items.BOWL);
            if (!player.addItem(bowl)) {
                player.drop(bowl, false);
            }
            level.removeBlock(pos, false);
        } else {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
            player.getFoodData().eat(4, 4.0f);
            level.setBlock(pos, state.setValue(BITES, bites + 1), 3);
        }
    }
}
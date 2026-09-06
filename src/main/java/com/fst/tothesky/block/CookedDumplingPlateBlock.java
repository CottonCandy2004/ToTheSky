package com.fst.tothesky.block;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.dumpling.DumplingFactory;
import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.item.CookedDumplingPlateItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 一盘熟饺子：右键逐个取用（每次取出该位置馅料对应的熟饺子），
 * 第 8 次（bite=8）取走后返还碗并移除方块。破坏不掉落。
 */
public class CookedDumplingPlateBlock extends BaseEntityBlock {
    public static final IntegerProperty BITE = IntegerProperty.create("bite", 0, 8);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public CookedDumplingPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BITE, 0));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DumplingPlateBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BITE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** BaseEntityBlock 默认返回 INVISIBLE，必须覆写为 MODEL 否则方块模型不渲染 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        // 手持另一盘饺子时放行，以便正常放置
        if (player.getItemInHand(hand).getItem() instanceof CookedDumplingPlateItem) {
            return InteractionResult.PASS;
        }
        takeOne(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void takeOne(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        int bite = state.getValue(BITE);
        if (bite >= 8) {
            // 最后一份：返还碗并移除方块
            ItemStack bowl = new ItemStack(Items.BOWL);
            if (!player.addItem(bowl)) {
                player.drop(bowl, false);
            }
            level.removeBlock(pos, false);
            return;
        }
        DumplingPlateContents contents = level.getBlockEntity(pos) instanceof DumplingPlateBlockEntity plate
                ? plate.contents() : null;
        ItemStack dumpling = contents != null
                ? DumplingFactory.cookedDumpling(contents.fillingAt(bite), contents.authorAt(bite))
                : new ItemStack(com.fst.tothesky.registry.ModItems.COOKED_DUMPLING.get());
        if (!player.addItem(dumpling)) {
            player.drop(dumpling, false);
        }
        level.setBlock(pos, state.setValue(BITE, bite + 1), 3);
    }
}
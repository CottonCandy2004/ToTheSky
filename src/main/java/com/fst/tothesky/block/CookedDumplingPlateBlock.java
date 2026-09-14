package com.fst.tothesky.block;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import com.fst.tothesky.dumpling.DumplingFactory;
import com.fst.tothesky.registry.ModItems;
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
 * 一盘熟饺子：右键逐个取出一份熟饺子（移植自 {@code dumpling_making.js} 的 rightClicked 分支）。
 *
 * <p>方块状态与旧 KubeJS 方块（{@code springFestival.js} 的 {@code cooked_dumpling_plate}：
 * {@code cardinal} + {@code bite 0..9}）一致，存档里的 {@code bite}/{@code facing}
 * 原值解析；内容物存在方块实体的 {@code data} 标签里（见 {@link DumplingPlateBlockEntity}）。
 *
 * <p>取食规则与旧脚本逐条对齐：
 * <ul>
 *   <li>{@code bite} 同时是「已取走份数」与「下一份的下标」，模型随 {bite} 显示剩余数量；</li>
 *   <li>{@code bite == 8}（8 份取完）时再右键返还一个碗并移除方块；</li>
 *   <li>手持另一盘饺子时不取食，放行给 {@code BlockItem} 正常放置；</li>
 *   <li>仅主手生效，副手不参与，避免一次右键取两份。</li>
 * </ul>
 */
public class CookedDumplingPlateBlock extends BaseEntityBlock {
    public static final IntegerProperty BITE = IntegerProperty.create("bite", 0, 9);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 与旧 KubeJS 的 {@code .box(1, 0, 1, 15, 4, 15)} 一致 */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 4, 15);

    public CookedDumplingPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BITE, 0).setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DumplingPlateBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BITE, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        // 手持另一盘饺子时放行，交给 BlockItem 正常放置
        if (player.getItemInHand(hand).is(ModItems.COOKED_DUMPLING_PLATE_ITEM.get())) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        int bite = state.getValue(BITE);
        if (bite >= DumplingPlateBlockEntity.SLOTS) {
            // 8 份已取完：还碗并移除方块
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.BOWL));
            level.removeBlock(pos, false);
            return InteractionResult.SUCCESS;
        }

        ItemStack dumpling = level.getBlockEntity(pos) instanceof DumplingPlateBlockEntity plate
                ? DumplingFactory.cookedDumpling(plate.fillingAt(bite), plate.authorAt(bite))
                : new ItemStack(ModItems.COOKED_DUMPLING.get());
        player.getInventory().placeItemBackInInventory(dumpling);
        level.setBlock(pos, state.setValue(BITE, bite + 1), 3);
        return InteractionResult.SUCCESS;
    }
}

package com.fst.tothesky.block;

import com.fst.tothesky.blockentity.DumplingPlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 一盘熟饺子：注册保留，供旧存档与脚本继续使用。
 *
 * <p>方块状态与旧 KubeJS 方块（{@code springFestival.js} 的 {@code cooked_dumpling_plate}：
 * {@code cardinal} + {@code bite 0..9}）一致，存档里的 {@code bite}/{@code facing}
 * 原值解析；内容物存在方块实体的 {@code data} 标签里（见 {@link DumplingPlateBlockEntity}）。
 *
 * <p>取食交互仍由脚本负责（{@code dumpling_making.js} 的 rightClicked 分支），本类不含玩法逻辑。
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
}

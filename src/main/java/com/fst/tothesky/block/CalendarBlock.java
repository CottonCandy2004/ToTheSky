package com.fst.tothesky.block;

import com.fst.tothesky.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 日历方块：贴墙悬挂、四向朝向、非完整立方体（自定义碰撞箱），右键打开日历 GUI
 * （服务端组包，客户端渲染）。
 * <p>{@code FACING} = 日历正面法线（背板贴在 {@code FACING} 反向的那面上）：{@code facing=north}
 * 时模型不旋转、背板贴方块南缘 {@code z=16}——与 {@code models/block/calendar.json} 的模型朝向一致，
 * 故 blockstate 的 {@code y} 旋转与 {@code getShape} 的碰撞箱一一对应（复用原版挂墙方块的约定）。
 * <p>无方块实体；数据在服务器级 {@code CalendarData}，方块仅是入口。
 */
public class CalendarBlock extends HorizontalDirectionalBlock {

    /** 模型包围盒 x∈[2,14]、y∈[1.5,14.5]、z∈[13,16]（背板贴南缘），按 facing 绕方块中心旋转 */
    private static final VoxelShape SHAPE_NORTH = Block.box(2.0, 1.5, 13.0, 14.0, 14.5, 16.0);
    private static final VoxelShape SHAPE_SOUTH = Block.box(2.0, 1.5, 0.0, 14.0, 14.5, 3.0);
    private static final VoxelShape SHAPE_EAST = Block.box(0.0, 1.5, 2.0, 3.0, 14.5, 14.0);
    private static final VoxelShape SHAPE_WEST = Block.box(13.0, 1.5, 2.0, 16.0, 14.5, 14.0);

    public CalendarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** 挂在点击的那面墙上；点到顶/底面时退化为朝向玩家，背后没有可依附的方块则不放置 */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal()
                ? clicked
                : context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** 背板侧（{@code FACING} 的反向）必须是有整面坚实贴面的方块 */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, facing);
    }

    /** 依附的方块被拆除或替换后掉落自身 */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            // HORIZONTAL_FACING 只含四个水平方向，UP/DOWN 不可达
            case NORTH, UP, DOWN -> SHAPE_NORTH;
        };
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

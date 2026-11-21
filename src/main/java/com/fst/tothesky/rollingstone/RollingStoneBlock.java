package com.fst.tothesky.rollingstone;

import com.fst.tothesky.registry.ModBlockEntities;
import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Eight full stone cubes, with a single controller at the minimum XYZ corner. */
public class RollingStoneBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PART_X = BooleanProperty.create("part_x");
    public static final BooleanProperty PART_Y = BooleanProperty.create("part_y");
    public static final BooleanProperty PART_Z = BooleanProperty.create("part_z");
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public RollingStoneBlock() {
        super(Properties.copy(Blocks.STONE).pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(PART_X, false).setValue(PART_Y, false).setValue(PART_Z, false));
    }

    @Override
    public Item asItem() {
        // The registered item is a RecordItem, so vanilla's BlockItem registration map does not contain it.
        // Clone stacks and new ItemStack(block) must still resolve to that same registered music disc.
        return ModItems.ROLLING_STONE.get();
    }

    public static boolean isRoot(BlockState state) {
        return !state.getValue(PART_X) && !state.getValue(PART_Y) && !state.getValue(PART_Z);
    }

    public static BlockPos rootPos(BlockPos pos, BlockState state) {
        return pos.offset(state.getValue(PART_X) ? -1 : 0,
                state.getValue(PART_Y) ? -1 : 0, state.getValue(PART_Z) ? -1 : 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos root = context.getClickedPos();
        if (!canOccupy(context.getLevel(), root, null)) return null;
        Player player = context.getPlayer();
        for (BlockPos part : BlockPos.betweenClosed(root, root.offset(1, 1, 1))) {
            if (player != null && !player.mayUseItemAt(part, context.getClickedFace(), context.getItemInHand())) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide) {
            fillParts(level, pos, state.getValue(FACING));
            if (level.getBlockEntity(pos) instanceof RollingStoneBlockEntity controller) {
                controller.setOwner(placer == null ? null : placer.getUUID());
            }
        }
    }

    private static boolean canOccupy(Level level, BlockPos root, @Nullable Entity ignored) {
        for (BlockPos part : BlockPos.betweenClosed(root, root.offset(1, 1, 1))) {
            if (!level.isInWorldBounds(part) || !level.getWorldBorder().isWithinBounds(part)
                    || !level.hasChunkAt(part) || !level.getBlockState(part).canBeReplaced()) return false;
        }
        AABB volume = new AABB(root.getX(), root.getY(), root.getZ(),
                root.getX() + 2, root.getY() + 2, root.getZ() + 2).deflate(0.001);
        return level.getEntities(ignored, volume, e -> e.blocksBuilding && !e.isSpectator()).isEmpty();
    }

    private static void fillParts(Level level, BlockPos root, Direction direction) {
        BlockState base = ModBlocks.ROLLING_STONE.get().defaultBlockState().setValue(FACING, direction);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            level.setBlock(root.offset(x, y, z), base.setValue(PART_X, x == 1)
                    .setValue(PART_Y, y == 1).setValue(PART_Z, z == 1), Block.UPDATE_CLIENTS);
        }
        for (BlockPos part : BlockPos.betweenClosed(root, root.offset(1, 1, 1))) {
            level.updateNeighborsAt(part, base.getBlock());
        }
    }

    /** Called when the moving stone rests; never replaces terrain or traps another entity. */
    public static boolean tryPlace(Level level, BlockPos root, Direction direction,
                                   @Nullable UUID owner, @Nullable Entity ignoredEntity) {
        if (!canOccupy(level, root, ignoredEntity)) return false;
        boolean supported = false;
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            BlockPos below = root.offset(x, -1, z);
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) supported = true;
        }
        if (!supported) return false;
        fillParts(level, root, direction);
        if (level.getBlockEntity(root) instanceof RollingStoneBlockEntity controller) controller.setOwner(owner);
        return true;
    }

    /** Removal is atomic with respect to our onRemove callbacks, so activation never drops an item. */
    public static void removeStructure(Level level, BlockPos root) {
        if (REMOVING.get()) return;
        REMOVING.set(true);
        try {
            for (BlockPos part : BlockPos.betweenClosed(root, root.offset(1, 1, 1))) {
                BlockState state = level.getBlockState(part);
                if (state.is(ModBlocks.ROLLING_STONE.get()) && rootPos(part, state).equals(root)) {
                    level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        } finally {
            REMOVING.set(false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        super.onRemove(state, level, pos, replacement, moving);
        if (!level.isClientSide && !state.is(replacement.getBlock()) && !REMOVING.get()) {
            BlockPos root = rootPos(pos, state);
            removeStructure(level, root);
        }
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isRoot(state) ? new RollingStoneBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && isRoot(state) && type == ModBlockEntities.ROLLING_STONE.get()
                ? (l, p, s, be) -> RollingStoneBlockEntity.serverTick(l, p, s, (RollingStoneBlockEntity) be) : null;
    }
}

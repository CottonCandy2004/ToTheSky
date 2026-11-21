package com.fst.tothesky.rollingstone;

import com.fst.tothesky.registry.ModBlockEntities;
import com.fst.tothesky.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The dormant boulder follows the surface ahead instead of seeing through hills. */
public class RollingStoneBlockEntity extends BlockEntity {
    private static final double EPSILON = 1.0E-5;
    @Nullable private UUID owner;
    private int cooldown = 20;

    public RollingStoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROLLING_STONE.get(), pos, state);
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RollingStoneBlockEntity stone) {
        if (level.isClientSide || !RollingStoneBlock.isRoot(state)) return;
        if (stone.cooldown > 0) {
            stone.cooldown--;
            return;
        }
        if (level.getGameTime() % 5 != 0) return;
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            BlockPos partPos = pos.offset(x, y, z);
            if (!level.hasChunkAt(partPos)) return;
            BlockState part = level.getBlockState(partPos);
            if (part.getBlock() != state.getBlock()
                    || !RollingStoneBlock.rootPos(partPos, part).equals(pos)
                    || part.getValue(RollingStoneBlock.FACING) != state.getValue(RollingStoneBlock.FACING)) return;
        }

        Direction direction = state.getValue(RollingStoneBlock.FACING);
        if (!stone.hasTargetAhead(level, pos, direction)) return;
        RollingStoneEntity rolling = ModEntities.ROLLING_STONE.get().create(level);
        if (rolling == null) return;
        rolling.configure(direction, stone.owner);
        rolling.setPos(pos.getX() + 1.0, pos.getY(), pos.getZ() + 1.0);
        // Keep the structure intact if a mod cancels spawning.
        if (level.addFreshEntity(rolling)) RollingStoneBlock.removeStructure(level, pos);
    }

    private boolean hasTargetAhead(Level level, BlockPos origin, Direction direction) {
        double x = origin.getX();
        double z = origin.getZ();
        double floor = origin.getY();
        AABB previous = volume(x, floor, z);
        for (int step = 1; step <= 10; step++) {
            x += direction.getStepX();
            z += direction.getStepZ();
            Double nextFloor = findFloor(level, origin, x, z, floor + 1.0);
            if (nextFloor == null) return false;
            AABB next = volume(x, nextFloor, z);
            // Raise before advancing uphill; advance before falling downhill.
            AABB corner = nextFloor > floor
                    ? previous.move(0, nextFloor - floor, 0)
                    : next.move(0, floor - nextFloor, 0);
            if (!isClear(level, origin, previous.minmax(corner))
                    || !isClear(level, origin, corner.minmax(next))) return false;
            if (!level.getEntitiesOfClass(Player.class, next.inflate(0.001), this::canTrigger).isEmpty()) return true;
            floor = nextFloor;
            previous = next;
        }
        return false;
    }

    private boolean canTrigger(Player player) {
        return player.isAlive() && !player.isSpectator()
                && (owner == null || !owner.equals(player.getUUID()));
    }

    @Nullable
    private static Double findFloor(Level level, BlockPos origin, double x, double z, double ceiling) {
        AABB footprint = new AABB(x + EPSILON, level.getMinBuildHeight(), z + EPSILON,
                x + 2 - EPSILON, ceiling, z + 2 - EPSILON);
        if (!loadedAndInsideBorder(level, footprint)) return null;
        for (int y = Mth.floor(ceiling); y >= level.getMinBuildHeight(); y--) {
            double highest = Double.NEGATIVE_INFINITY;
            for (int bx = Mth.floor(footprint.minX); bx <= Mth.floor(footprint.maxX); bx++) {
                for (int bz = Mth.floor(footprint.minZ); bz <= Mth.floor(footprint.maxZ); bz++) {
                    BlockPos blockPos = new BlockPos(bx, y, bz);
                    if (isOriginalPart(origin, blockPos)) continue;
                    BlockState block = level.getBlockState(blockPos);
                    for (AABB box : block.getCollisionShape(level, blockPos, CollisionContext.empty()).toAabbs()) {
                        AABB worldBox = box.move(blockPos);
                        if (worldBox.maxY <= ceiling + EPSILON
                                && worldBox.maxX > footprint.minX && worldBox.minX < footprint.maxX
                                && worldBox.maxZ > footprint.minZ && worldBox.minZ < footprint.maxZ) {
                            highest = Math.max(highest, worldBox.maxY);
                        }
                    }
                }
            }
            if (highest != Double.NEGATIVE_INFINITY) return highest;
        }
        return null;
    }

    private static boolean isClear(Level level, BlockPos origin, AABB swept) {
        AABB area = swept.deflate(EPSILON);
        if (!loadedAndInsideBorder(level, area) || area.maxY > level.getMaxBuildHeight()) return false;
        for (int x = Mth.floor(area.minX); x <= Mth.floor(area.maxX); x++) {
            for (int z = Mth.floor(area.minZ); z <= Mth.floor(area.maxZ); z++) {
                for (int y = Math.max(level.getMinBuildHeight(), Mth.floor(area.minY) - 1);
                     y <= Mth.floor(area.maxY); y++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (isOriginalPart(origin, blockPos)) continue;
                    BlockState block = level.getBlockState(blockPos);
                    for (AABB box : block.getCollisionShape(level, blockPos, CollisionContext.empty()).toAabbs()) {
                        if (box.move(blockPos).intersects(area)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean loadedAndInsideBorder(Level level, AABB area) {
        if (!level.getWorldBorder().isWithinBounds(area)) return false;
        for (int x = Mth.floor(area.minX); x <= Mth.floor(area.maxX); x++) {
            for (int z = Mth.floor(area.minZ); z <= Mth.floor(area.maxZ); z++) {
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) return false;
            }
        }
        return true;
    }

    private static boolean isOriginalPart(BlockPos origin, BlockPos pos) {
        return pos.getX() >= origin.getX() && pos.getX() <= origin.getX() + 1
                && pos.getY() >= origin.getY() && pos.getY() <= origin.getY() + 1
                && pos.getZ() >= origin.getZ() && pos.getZ() <= origin.getZ() + 1;
    }

    private static AABB volume(double x, double y, double z) {
        return new AABB(x, y, z, x + 2, y + 2, z + 2);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Cooldown", cooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        cooldown = tag.contains("Cooldown") ? Math.max(0, tag.getInt("Cooldown")) : 20;
    }
}

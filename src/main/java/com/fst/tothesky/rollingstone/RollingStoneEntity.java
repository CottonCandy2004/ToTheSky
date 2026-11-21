package com.fst.tothesky.rollingstone;

import com.fst.tothesky.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A two-block boulder; movement and damage are authoritative on the server. */
public class RollingStoneEntity extends Entity {
    private static final EntityDataAccessor<Direction> DIRECTION =
            SynchedEntityData.defineId(RollingStoneEntity.class, EntityDataSerializers.DIRECTION);
    private static final double MAX_SPEED = 0.48D;
    private static final int MAX_LIFETIME = 600;
    private final Map<UUID, Integer> nextHit = new HashMap<>();
    @Nullable
    private UUID owner;
    private double speed = 0.16D;
    private int lifetime;
    private int blockedTicks;
    private float rollAngle;
    private float previousRollAngle;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public RollingStoneEntity(EntityType<? extends RollingStoneEntity> type, Level level) {
        super(type, level);
        blocksBuilding = true;
        setMaxUpStep(1.0F);
    }

    public void configure(Direction direction, @Nullable UUID owner) {
        entityData.set(DIRECTION, direction.getAxis().isHorizontal() ? direction : Direction.SOUTH);
        this.owner = owner;
    }

    public Direction getRollDirection() {
        return entityData.get(DIRECTION);
    }

    public float getRollAngle(float partialTick) {
        return Mth.lerp(partialTick, previousRollAngle, rollAngle);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DIRECTION, Direction.SOUTH);
    }

    @Override
    public void tick() {
        super.tick();
        previousRollAngle = rollAngle;
        Vec3 start = position();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(getX() + (lerpX - getX()) / lerpSteps,
                        getY() + (lerpY - getY()) / lerpSteps,
                        getZ() + (lerpZ - getZ()) / lerpSteps);
                --lerpSteps;
            }
            updateRoll(start);
            return;
        }

        ++lifetime;
        Direction direction = getRollDirection();
        speed = Math.min(MAX_SPEED, speed + 0.015D);
        double vertical = isNoGravity() ? 0 : Math.max(-1.5D, getDeltaMovement().y - 0.08D);
        Vec3 movement = new Vec3(direction.getStepX() * speed, vertical, direction.getStepZ() * speed);
        AABB before = getBoundingBox();
        AABB projected = before.expandTowards(movement);
        if (!level().hasChunksAt(BlockPos.containing(projected.minX, projected.minY, projected.minZ),
                BlockPos.containing(projected.maxX, projected.maxY, projected.maxZ))) {
            finishRolling();
            return;
        }
        setDeltaMovement(movement);
        move(MoverType.SELF, movement);
        Vec3 actual = position().subtract(start);
        setDeltaMovement(direction.getStepX() * speed, onGround() ? 0 : vertical,
                direction.getStepZ() * speed);
        updateRoll(start);
        hurtContacts(before.minmax(getBoundingBox()).inflate(0.025D));
        if (isRemoved()) {
            return;
        }
        if (horizontalCollision && actual.horizontalDistanceSqr() < 0.0025D && onGround()) {
            ++blockedTicks;
        } else {
            blockedTicks = 0;
        }
        if (onGround() && lifetime % 8 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_STEP,
                    SoundSource.BLOCKS, 0.7F, 0.65F);
        }
        if (blockedTicks >= 10 || lifetime >= MAX_LIFETIME || getY() < level().getMinBuildHeight() - 16) {
            finishRolling();
        }
    }

    private void updateRoll(Vec3 start) {
        double distance = position().subtract(start).horizontalDistance();
        rollAngle += (float) (distance * Mth.RAD_TO_DEG);
    }

    private void hurtContacts(AABB swept) {
        if (lifetime % 20 == 0) {
            nextHit.entrySet().removeIf(entry -> entry.getValue() <= lifetime);
        }
        for (Entity target : level().getEntities(this, swept, this::canHurt)) {
            if (nextHit.getOrDefault(target.getUUID(), 0) > lifetime) {
                continue;
            }
            nextHit.put(target.getUUID(), lifetime + 10);
            if (target.hurt(damageSources().fallingBlock(this), 10.0F)) {
                Direction direction = getRollDirection();
                target.push(direction.getStepX() * 0.5D, 0.2D, direction.getStepZ() * 0.5D);
                target.hurtMarked = true;
            }
        }
    }

    private boolean canHurt(Entity target) {
        return target.isAlive() && !(target instanceof RollingStoneEntity) && !target.isSpectator()
                && !target.getUUID().equals(owner);
    }

    private void finishRolling() {
        boolean placed = false;
        if (onGround()) {
            int rootY = Mth.floor(getY() + 0.05D);
            // Prefer the closest aligned footprint; then try the surrounding floor cells.
            int rootX = (int) Math.round(getX() - 1.0D);
            int rootZ = (int) Math.round(getZ() - 1.0D);
            for (int radius = 0; radius <= 1 && !placed; ++radius) {
                for (int dx = -radius; dx <= radius && !placed; ++dx) {
                    for (int dz = -radius; dz <= radius && !placed; ++dz) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        placed = RollingStoneBlock.tryPlace(level(), new BlockPos(rootX + dx, rootY, rootZ + dz),
                                getRollDirection(), owner, this);
                    }
                }
            }
        }
        if (!placed && level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            spawnAtLocation(ModItems.ROLLING_STONE.get());
        }
        discard();
    }

    /** Entities are damaged by contact rather than stopping the boulder's movement. */
    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, Math.min(steps, 3));
        setRot(yaw, pitch);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Direction", getRollDirection().getName());
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putDouble("Speed", speed);
        tag.putInt("Lifetime", lifetime);
        tag.putFloat("RollAngle", rollAngle);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("Direction"));
        configure(direction == null ? Direction.SOUTH : direction,
                tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        speed = Mth.clamp(tag.getDouble("Speed"), 0.16D, MAX_SPEED);
        lifetime = Mth.clamp(tag.getInt("Lifetime"), 0, MAX_LIFETIME);
        rollAngle = previousRollAngle = tag.getFloat("RollAngle");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}

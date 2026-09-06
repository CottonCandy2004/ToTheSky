package com.fst.tothesky.block;

import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * 鞭炮：主手手持打火石右键 → 以玩家为原点向 40 格内播放 tnt.primed；
 * 40 tick 后引爆（带防重入）。
 *
 * 引爆：在方块下一格召唤一只可见的小盔甲架作标记（firecracker_marker 标签，
 * NoAI/Invulnerable/PersistenceRequired/Silent/Invisible:0/ShowArms:1/Small:1 与旧脚本
 * summon NBT 逐字一致；其中 NoAI 与 PersistenceRequired 对盔甲架无实际作用，仅保留原样）。
 * 3/6/…/24 tick 共 8 次脉冲：
 * 机枪声（createbigcannons:fire_machine_gun，随机音高 1~2、音量 10，40 格内）、
 * 岩浆粒子、营火信号烟与火焰粒子；第 24 tick 移除标记、方块变空气，
 * 并检查周围 8 个水平邻格：仍是鞭炮且未被点燃（下方 0.5 范围内无标记盔甲架）
 * 则连锁引爆。
 *
 * 移植自 kubejs/server_scripts/feature/block_events.js 的
 * kubejs:multiple_firecrackers 分支与 fireCracker/isFirecrackerLit 函数（RiaFST 4 版）。
 */
public class MultipleFirecrackersBlock extends SimpleShapeBlock {
    /** 引信时长（右键到引爆） */
    private static final int FUSE_TICKS = 40;
    /** 脉冲次数（3 + i*3 tick，i = 0..7） */
    private static final int PULSES = 8;
    private static final String MARKER_TAG = "firecracker_marker";
    /** 声音与粒子播给玩家的半径（旧脚本 @a[distance=..40]） */
    private static final double SOUND_RADIUS = 40;
    private static final ResourceLocation FIRE_MACHINE_GUN =
            new ResourceLocation("createbigcannons", "fire_machine_gun");
    /** 8 个水平邻格偏移（含斜角） */
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}
    };
    /** 机枪声事件（createbigcannons 未安装时查不到，保持 null 则脉冲只放粒子） */
    private static SoundEvent machineGunSound;

    public MultipleFirecrackersBlock(VoxelShape shape, Properties properties) {
        super(shape, properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND
                || !player.getItemInHand(hand).is(Items.FLINT_AND_STEEL)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 点燃引信：以玩家为执行位置向 40 格内广播（旧脚本 execute as player at player）
            playSoundAt(serverLevel, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TNT_PRIMED, 1.0F, 1.0F);
            DelayedTasks.schedule(serverLevel.getServer(), FUSE_TICKS, () -> ignite(serverLevel, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 引爆（对应旧 fireCracker）：防重入检查 → 放标记 → 8 次脉冲 → 24 tick 后收尾并连锁 */
    private static void ignite(ServerLevel level, BlockPos pos) {
        if (isFirecrackerLit(level, pos)) {
            return; // 已有点燃标记（自己被连锁或重复引燃过），跳过
        }
        ArmorStand marker = spawnMarker(level, pos);
        for (int i = 0; i < PULSES; i++) {
            DelayedTasks.schedule(level.getServer(), 3 + i * 3, () -> pulse(level, marker));
        }
        DelayedTasks.schedule(level.getServer(), 24, () -> finish(level, pos, marker));
    }

    /** 检查方块下方 0.5 范围内是否有带 firecracker_marker 标签的盔甲架 */
    private static boolean isFirecrackerLit(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(pos.below()).inflate(0.5);
        return !level.getEntitiesOfClass(ArmorStand.class, box,
                stand -> stand.getTags().contains(MARKER_TAG)).isEmpty();
    }

    /** 在方块下一格召唤标记盔甲架（NBT 与旧脚本 summon 一致，Invisible:0 即可见） */
    private static ArmorStand spawnMarker(ServerLevel level, BlockPos pos) {
        ArmorStand marker = new ArmorStand(level, pos.getX(), pos.getY() - 1, pos.getZ());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("NoAI", true); // 对盔甲架无效（非 Mob），仅为与旧脚本 NBT 逐字一致
        tag.putBoolean("PersistenceRequired", true); // 同上：盔甲架不会自然消失
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("Silent", true);
        tag.putBoolean("Invisible", false);
        tag.putBoolean("Small", true);
        tag.putBoolean("ShowArms", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(MARKER_TAG));
        tag.put("Tags", tags);
        marker.readAdditionalSaveData(tag);
        level.addFreshEntity(marker);
        return marker;
    }

    /** 一次脉冲：机枪声 + 岩浆/营火信号烟/火焰粒子（坐标与旧脚本 execute at @e[tag] 一致） */
    private static void pulse(ServerLevel level, ArmorStand marker) {
        if (marker.isRemoved()) {
            return;
        }
        SoundEvent gun = machineGun();
        if (gun != null) {
            // 旧脚本：~ ~1 ~ 播放，随机音高 1~2，音量 10，@a[distance=..40]
            playSoundAt(level, marker.getX(), marker.getY() + 1, marker.getZ(),
                    gun, 10.0F, 1.0F + level.random.nextFloat());
        }
        // 岩浆：x/z 随机 ±0.5（旧脚本 ~<-0.5+rand> ~1 ~<-0.5+rand>），count 1、速度 1
        level.sendParticles(ParticleTypes.LAVA,
                marker.getX() + level.random.nextDouble() - 0.5,
                marker.getY() + 1,
                marker.getZ() + level.random.nextDouble() - 0.5,
                1, 0, 0, 0, 1);
        // 营火信号烟：~ ~1.5 ~，扩散 0.3、速度 0.01、count 5
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                marker.getX(), marker.getY() + 1.5, marker.getZ(),
                5, 0.3, 0.3, 0.3, 0.01);
        // 火焰：~ ~1.1 ~，扩散 0.2、速度 0.01、count 4
        level.sendParticles(ParticleTypes.FLAME,
                marker.getX(), marker.getY() + 1.1, marker.getZ(),
                4, 0.2, 0.2, 0.2, 0.01);
    }

    /** 24 tick 收尾：杀标记、方块变空气、向未点燃的邻格连锁引爆 */
    private static void finish(ServerLevel level, BlockPos pos, ArmorStand marker) {
        if (!marker.isRemoved()) {
            marker.discard();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (int[] offset : NEIGHBORS) {
            BlockPos neighbor = pos.offset(offset[0], offset[1], offset[2]);
            if (level.getBlockState(neighbor).is(ModBlocks.MULTIPLE_FIRECRACKERS.get())
                    && !isFirecrackerLit(level, neighbor)) {
                ignite(level, neighbor);
            }
        }
    }

    /** 以 x/y/z 为原点向 40 格内所有玩家发送音效包（等效 playsound … block @a[distance=..40]） */
    private static void playSoundAt(ServerLevel level, double x, double y, double z,
                                    SoundEvent sound, float volume, float pitch) {
        Optional<Holder<SoundEvent>> holder = ForgeRegistries.SOUND_EVENTS.getHolder(sound);
        if (holder.isEmpty()) {
            return;
        }
        ClientboundSoundPacket packet = new ClientboundSoundPacket(holder.get(), SoundSource.BLOCKS,
                x, y, z, volume, pitch, level.random.nextLong());
        double radiusSq = SOUND_RADIUS * SOUND_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= radiusSq) {
                player.connection.send(packet);
            }
        }
    }

    /** 惰性查找 createbigcannons 的机枪声事件（注册表冻结后再取，未安装则返回 null） */
    private static SoundEvent machineGun() {
        SoundEvent sound = machineGunSound;
        if (sound == null) {
            sound = ForgeRegistries.SOUND_EVENTS.getValue(FIRE_MACHINE_GUN);
            machineGunSound = sound;
        }
        return sound;
    }
}

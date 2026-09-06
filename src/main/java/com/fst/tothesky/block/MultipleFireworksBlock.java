package com.fst.tothesky.block;

import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 礼炮：主手手持打火石右键 → 播放 tnt.primed；5 tick 后清除 3×3×3 内的火焰，
 * 并把本格换成"燃放的礼炮"；此后 20/30/…/100 tick 每 10 tick 发射一发彩色烟花
 * （发射点 x/z 随机 ±0.5、y+1，Motion 随机偏移 0.1 + 向上 1.0），共 9 发，
 * 最后一发的同时把方块换成礼炮纸壳 fireworks_box。
 *
 * 移植自 kubejs/server_scripts/feature/block_events.js 的 kubejs:multiple_fireworks 分支。
 * 旧脚本的 fill/setblock 在延迟回调里对点击时刻的坐标无条件执行（不校验方块是否被换），
 * 这里保持一致。
 */
public class MultipleFireworksBlock extends Block {
    /** 9 发烟花的颜色（20/30/40/50/60/70/80/90/100 tick） */
    private static final int[] COLORS = {
            16711680,  // 红
            16746752,  // 橙
            16383744,  // 黄
            65300,     // 绿
            65468,     // 青
            33791,     // 蓝
            3866879,   // 紫
            16081663,  // 粉
            16711680   // 红
    };
    /** Motion 的水平随机偏移幅度（脚本 mOffset = 0.1） */
    private static final double MOTION_OFFSET = 0.1;
    /** Motion 的垂直速度（脚本 speed = 1） */
    private static final double MOTION_SPEED = 1.0;

    public MultipleFireworksBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (hand != InteractionHand.MAIN_HAND
                || !player.getItemInHand(hand).is(Items.FLINT_AND_STEEL)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            // 点燃引信（旧脚本 event.player.playSound，这里从方块位置播给周围玩家）
            serverLevel.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            // 5 tick 后：扑灭打火石点着的火（fill … air replace fire），并换成"燃放的礼炮"
            DelayedTasks.schedule(serverLevel.getServer(), 5, () -> {
                for (BlockPos p : BlockPos.betweenClosed(
                        pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
                    if (serverLevel.getBlockState(p).is(Blocks.FIRE)) {
                        serverLevel.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
                serverLevel.setBlock(pos,
                        ModBlocks.FIRING_MULTIPLE_FIREWORKS.get().defaultBlockState(), 3);
            });
            // 20~100 tick 每 10 tick 一发；最后一发同时把方块换成纸壳
            for (int i = 0; i < COLORS.length; i++) {
                int color = COLORS[i];
                boolean last = i == COLORS.length - 1;
                DelayedTasks.schedule(serverLevel.getServer(), 20 + i * 10, () -> {
                    launchFirework(serverLevel, pos, color);
                    if (last) {
                        serverLevel.setBlock(pos,
                                ModBlocks.FIREWORKS_BOX.get().defaultBlockState(), 3);
                    }
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 从方块中心上方 1 格（x/z 随机 ±0.5、y+1）发射一发烟花，
     * Motion：x/z 随机 ±0.05，y 随机 ±0.05 + 1
     */
    private static void launchFirework(ServerLevel level, BlockPos pos, int color) {
        spawnRocket(level,
                pos.getX() + level.random.nextDouble() - 0.5,
                pos.getY() + 1,
                pos.getZ() + level.random.nextDouble() - 0.5,
                (level.random.nextDouble() - 0.5) * MOTION_OFFSET,
                (level.random.nextDouble() - 0.5) * MOTION_OFFSET + MOTION_SPEED,
                (level.random.nextDouble() - 0.5) * MOTION_OFFSET,
                color);
    }

    /** 生成一发 Flight:1、Trail 小球的烟花火箭并赋予指定 Motion */
    private static void spawnRocket(ServerLevel level, double x, double y, double z,
                                    double mx, double my, double mz, int color) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        // 1.20.1 烟花 NBT：Colors/FadeColors 写 int 数组（旧脚本 [I;…] 形式，客户端按 int[] 读取）
        CompoundTag explosion = new CompoundTag();
        explosion.putInt("Type", 0); // SMALL_BALL
        explosion.putByte("Trail", (byte) 1);
        explosion.putIntArray("Colors", new int[]{color});
        explosion.putIntArray("FadeColors", new int[]{color});
        ListTag explosions = new ListTag();
        explosions.add(explosion);
        CompoundTag fireworks = new CompoundTag();
        fireworks.putByte("Flight", (byte) 1);
        fireworks.put("Explosions", explosions);
        rocket.getOrCreateTag().put("Fireworks", fireworks);

        FireworkRocketEntity entity = new FireworkRocketEntity(level, x, y, z, rocket);
        entity.setDeltaMovement(mx, my, mz);
        level.addFreshEntity(entity);
    }
}

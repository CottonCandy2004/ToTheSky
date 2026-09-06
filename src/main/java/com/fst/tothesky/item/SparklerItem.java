package com.fst.tothesky.item;

import com.fst.tothesky.util.DelayedTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 礼花：主手右键消耗 1 个，以玩家当前朝向发射 4 发小型烟花火箭
 * （0/10/20/30 tick 各一发，速度 0.5，红/橙/黄/青绿），冷却 50 tick。
 *
 * 移植自 kubejs/server_scripts/feature/item_events.js 的 kubejs:sparkler 分支。
 * 注意旧脚本在每次发射时（含延迟回调内）实时读取玩家的方块坐标与 yaw/pitch，
 * 这里同样在发射时刻取玩家当前状态；玩家下线则跳过后续发射。
 *
 * 烟花实体用 FireworkRocketEntity 构造 + Fireworks NBT（Flight:1、Trail:1b、Type:0）。
 * 旧脚本的 summon 命令额外写死实体字段 Life:0/LifeTime:20；该字段私有不可写，
 * 改用构造器按 Flight:1 生成的寿命（20~31 tick），视觉等价。
 */
public class SparklerItem extends Item {
    /** 发射速度（脚本 speed = 0.5） */
    private static final double SPEED = 0.5;
    /** 4 连发：{延迟 tick, 颜色}，Colors 与 FadeColors 相同 */
    private static final int[][] SHOTS = {
            {0, 16711680},   // 红
            {10, 16383744},  // 橙
            {20, 50943},     // 黄
            {30, 65311}      // 青绿
    };

    public SparklerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 旧脚本只看主手（switch mainHandItem），副手持礼花不触发
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            for (int[] shot : SHOTS) {
                int delay = shot[0];
                int color = shot[1];
                if (delay == 0) {
                    fireFirework(serverLevel, player, color);
                } else {
                    DelayedTasks.schedule(serverLevel.getServer(), delay,
                            () -> fireFirework(serverLevel, player, color));
                }
            }
            player.getCooldowns().addCooldown(this, 50);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 从玩家当前脚下方块 +2 格发射一发烟花（旧脚本 event.player.block + y+2），
     * Motion 由当前 yaw/pitch 换算（deg→rad × 0.01745）：
     * mx = -sin(yaw)·cos(pitch)·speed；my = -sin(pitch)·speed；mz = cos(yaw)·cos(pitch)·speed
     */
    private static void fireFirework(ServerLevel level, Player player, int color) {
        if (player.isRemoved()) {
            return;
        }
        double yaw = player.getYRot() * Math.PI / 180.0;
        double pitch = player.getXRot() * Math.PI / 180.0;
        double mx = Math.sin(yaw) * Math.cos(pitch) * SPEED * -1;
        double my = -Math.sin(pitch) * SPEED;
        double mz = Math.cos(yaw) * Math.cos(pitch) * SPEED;
        BlockPos pos = player.blockPosition();
        spawnRocket(level, pos.getX(), pos.getY() + 2, pos.getZ(), mx, my, mz, color);
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

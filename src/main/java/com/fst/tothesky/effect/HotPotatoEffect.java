package com.fst.tothesky.effect;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * 击鼓传花：自驱动的 mob effect。
 *
 * 原 KubeJS 用 server.persistentData + 每 tick 递归调度（hotPotatoCounter）实现
 * 「全服唯一的击鼓传花」。这里改为效果本身驱动，并把「时钟」编码在效果的
 * duration 里：谁持有 hot_potato，谁就是当前受害者；剩余 duration 就是剩余时间。
 * 每一场的时钟是各自独立的 MobEffectInstance，因此天然支持多场同时进行。
 *
 * - 饮用危险派对：给饮用者加 hot_potato（duration = 1200 tick / 60s）。
 * - 持有者被攻击：见 {@code ModGameEvents.onPlayerHurt}，把效果传给攻击者。
 * - 每 200 tick（10s）广播一次剩余秒数。
 * - 效果到期（剩余时长即将耗尽）：击杀持有者，本场结束。
 *
 * 注意：务必用 BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this) 查询 activeEffects——
 * 它返回注册表里的 reference holder，与 addEffect 存入的 key 按 equals 匹配；
 * 用 Holder.direct(this) 会因 kind=Direct 而永远查不到实例（无广播、无击杀）。
 */
public class HotPotatoEffect extends MobEffect {
    /** 一场击鼓传花的时长（tick），与 KubeJS 的 1200 一致 */
    public static final int DURATION = 1200;
    /** 剩余秒数广播间隔（tick）：每 10 秒报一次 */
    private static final int ANNOUNCE_EVERY = 200;

    public HotPotatoEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6D37);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) {
            Holder<MobEffect> self = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this);
            MobEffectInstance instance = player.getEffect(self);
            if (instance == null) {
                return true;
            }
            // getDuration() 是递减前的剩余时长
            int remaining = instance.getDuration();
            // 每 10s 广播一次剩余秒数（首 tick 在 1200 处也会命中，与 kjs 行为一致）
            if (remaining > 1 && remaining % ANNOUNCE_EVERY == 0) {
                int remainSec = Math.round(remaining / 20.0f);
                String name = player.getGameProfile().getName();
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.literal(name + "身上的击鼓传花还剩余" + remainSec + "秒"), false);
            }
            // 最后一 tick（剩余 1 时即将被移除）：击杀持有者，本场结束
            if (remaining <= 1) {
                String name = player.getGameProfile().getName();
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.literal(name + "没能及时把好运传给下一个人"), false);
                player.kill();
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // 每 tick 都进来检查到期，才能捕捉"最后一 tick"击杀持有者
        return true;
    }
}

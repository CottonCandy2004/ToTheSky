package com.fst.tothesky.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

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
 * - 效果到期（剩余时长即将耗尽）：对持有者结算 {@link #EXPIRE_DAMAGE} 点普通伤害，
 *   本场结束。用伤害而非 {@code kill()}：见 {@link #EXPIRE_DAMAGE} 的说明。
 *
 * 1.20.1 适配：applyEffectTick 返回 void（1.21 返回 boolean）；
 * getEffect(MobEffect) 直接按注册表身份匹配实例，无需 Holder 包装。
 */
public class HotPotatoEffect extends MobEffect {
    /** 一场击鼓传花的时长（tick），与 KubeJS 的 1200 一致 */
    public static final int DURATION = 1200;
    /** 剩余秒数广播间隔（tick）：每 10 秒报一次 */
    private static final int ANNOUNCE_EVERY = 200;
    /**
     * 到期结算的伤害量。必须走 {@code hurt()} 而不是 {@code kill()}：
     * {@code LivingEntity.kill()} 用 {@code generic_kill} 伤害源（属
     * {@code bypasses_invulnerability} 标签），不死图腾（{@code checkTotemDeathProtection}
     * 开头就因该标签直接返回 false）、死亡回溯等免死手段一律无效。
     * 这里用 200 点 {@code generic} 伤害：正常走伤害管线，图腾等可规避；
     * 它属 {@code bypasses_armor}（护甲不减免），只被抗性/保护附魔削减，
     * 因此 200 点对正常玩家仍是致命伤。
     */
    public static final float EXPIRE_DAMAGE = 200.0F;

    public HotPotatoEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6D37);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) {
            // 1.20.1 getEffect(MobEffect) 按注册表身份匹配实例，无需 Holder 包装
            MobEffectInstance instance = player.getEffect(this);
            if (instance == null) {
                return;
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
            // 最后一 tick（剩余 1 时即将被移除）：重伤持有者，本场结束
            if (remaining <= 1) {
                String name = player.getGameProfile().getName();
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.literal(name + "没能及时把好运传给下一个人"), false);
                player.hurt(player.damageSources().generic(), EXPIRE_DAMAGE);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // 每 tick 都进来检查到期，才能捕捉"最后一 tick"的伤害结算
        return true;
    }
}
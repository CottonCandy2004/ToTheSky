package com.fst.tothesky.effect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 谵妄：效果存续期间玩家无法发言（见 {@code ModGameEvents#onChat}），
 * 并会以平均约 250 tick 一次的频率不受控地"说胡话"。
 * 旧脚本用递归调度 + persistentData 实现且需要上线断点续传；
 * 效果自驱动后这些状态都不再需要。
 */
public class MadnessEffect extends MobEffect {
    /** 平均每 tick 1/250 的概率说胡话，期望间隔与旧脚本（1~500 均匀分布）一致 */
    private static final int TALK_CHANCE_INTERVAL = 250;

    public MadnessEffect() {
        super(MobEffectCategory.HARMFUL, 0xC0C0C0);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) {
            RandomSource random = entity.getRandom();
            if (random.nextInt(TALK_CHANCE_INTERVAL) == 0) {
                String talk = MadnessQuotes.random(random);
                Component message = Component.literal(" • ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("<").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(">: ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(talk).withStyle(ChatFormatting.WHITE));
                player.server.getPlayerList().broadcastSystemMessage(message, false);
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }
}
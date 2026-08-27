package com.fst.tothesky.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 绿玩：效果存续期间每 200 tick 让 1~20 格内的其他生物发光 7 秒。
 * 等价于旧 KubeJS 脚本中食用三角粥后的六次定时脉冲，但由效果自身驱动，
 * 无需调度器，掉线/重进也不会泄漏任务。
 */
public class FairPlayEffect extends MobEffect {
    private static final int PULSE_INTERVAL = 200;
    private static final int GLOWING_DURATION = 140;

    public FairPlayEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x00FF00);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        AABB area = entity.getBoundingBox().inflate(20.0);
        for (LivingEntity other : entity.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e != entity && e.distanceTo(entity) >= 1.0f)) {
            other.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOWING_DURATION, 0, false, false));
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % PULSE_INTERVAL == 0;
    }
}

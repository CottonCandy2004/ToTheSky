package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.effect.FairPlayEffect;
import com.fst.tothesky.effect.MadnessEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ToTheSky.MODID);

    /** 绿玩：周期性地给周围实体发光效果（食用三角粥获得） */
    public static final DeferredHolder<MobEffect, MobEffect> FAIR_PLAY =
            EFFECTS.register("fair_play", FairPlayEffect::new);
    /** 死亡回溯：致命伤时回溯到记录的地点（饮品659） */
    public static final DeferredHolder<MobEffect, MobEffect> REWIND =
            EFFECTS.register("rewind", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xBA0000) {});
    /** 谵妄：无法发言，并不由自主地说胡话（肥虫汤） */
    public static final DeferredHolder<MobEffect, MobEffect> MADNESS =
            EFFECTS.register("madness", MadnessEffect::new);

    private ModEffects() {
    }
}

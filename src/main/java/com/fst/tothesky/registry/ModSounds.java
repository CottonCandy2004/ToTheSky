package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 音效注册。移植自 kubejs/startup_scripts/instruments.js */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ToTheSky.MODID);

    /** 吉他弹奏音（assets/tothesky/sounds/guitar_sound.ogg） */
    public static final RegistryObject<SoundEvent> GUITAR_SOUND =
            SOUNDS.register("guitar_sound", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, "guitar_sound")));

    /**
     * 唱片音轨 Never gonna give you up（assets/tothesky/sounds/never_gonna_give_you_up.ogg，stream=true）。
     * 注册名沿用旧 kjs 的 {@code kubejs:music.never_gonna_give_you_up}，以便 {@link
     * com.fst.tothesky.event.MissingMappingEvents} 按同名规则 remap 旧存档里的音效引用。
     */
    public static final RegistryObject<SoundEvent> MUSIC_NEVER_GONNA_GIVE_YOU_UP =
            SOUNDS.register("music.never_gonna_give_you_up", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, "music.never_gonna_give_you_up")));

    private ModSounds() {
    }
}

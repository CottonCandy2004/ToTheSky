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

    private ModSounds() {
    }
}

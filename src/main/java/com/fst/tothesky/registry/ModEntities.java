package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.rollingstone.RollingStoneEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ToTheSky.MODID);

    public static final RegistryObject<EntityType<RollingStoneEntity>> ROLLING_STONE =
            ENTITIES.register("rolling_stone", () -> EntityType.Builder
                    .<RollingStoneEntity>of(RollingStoneEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F).fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build(ToTheSky.MODID + ":rolling_stone"));

    private ModEntities() {
    }
}

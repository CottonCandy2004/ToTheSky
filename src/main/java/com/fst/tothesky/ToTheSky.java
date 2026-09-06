package com.fst.tothesky;

import com.fst.tothesky.registry.ModBlockEntities;
import com.fst.tothesky.registry.ModBlocks;
import com.fst.tothesky.registry.ModEffects;
import com.fst.tothesky.registry.ModFluids;
import com.fst.tothesky.registry.ModFluidTypes;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModRecipes;
import com.fst.tothesky.registry.ModTabs;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ToTheSky.MODID)
public class ToTheSky {
    public static final String MODID = "tothesky";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ToTheSky() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModTabs.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModFluidTypes.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModFluids.BUCKETS.register(modEventBus);
    }
}
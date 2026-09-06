package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ToTheSky.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ToTheSky.MODID))
                    .icon(() -> new ItemStack(ModItems.SUNSHINE_COD.get()))
                    .displayItems((parameters, output) -> {
                        ModItems.ITEMS.getEntries().forEach(holder -> output.accept(holder.get()));
                        // 流体桶单独注册在 ModFluids.BUCKETS，加入主创造栏
                        output.accept(ModFluids.NETHERITE_LIQUAR_BUCKET.get());
                        output.accept(ModFluids.UNSTABLE_NETHERITE_LIQUAR_BUCKET.get());
                        output.accept(ModFluids.BEAN_SAUSE_BUCKET.get());
                        output.accept(ModFluids.BEAN_OIL_BUCKET.get());
                        output.accept(ModFluids.SOY_SAUSE_BUCKET.get());
                        output.accept(ModFluids.GHAST_TEAR_BUCKET.get());
                    })
                    .build());

    private ModTabs() {
    }
}
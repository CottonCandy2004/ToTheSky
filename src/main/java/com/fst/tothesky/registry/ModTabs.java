package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ToTheSky.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ToTheSky.MODID))
                    .icon(() -> new ItemStack(ModItems.BOMB_COD_BURGER.get()))
                    .displayItems((parameters, output) ->
                            ModItems.ITEMS.getEntries().forEach(holder -> output.accept(holder.get())))
                    .build());

    private ModTabs() {
    }
}

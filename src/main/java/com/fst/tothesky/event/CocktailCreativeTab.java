package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModCocktails;
import io.github.tt432.kitchenkarrot.item.CocktailItem;
import io.github.tt432.kitchenkarrot.registries.ModItems;
import io.github.tt432.kitchenkarrot.registries.ModTabs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * 把 fstwines 鸡尾酒加进 kitchenkarrot 的「胡萝卜厨房-鸡尾酒」创造栏。
 *
 * kitchenkarrot 的 ModTabs.addCreative 只遍历它自己的 ModCocktails.COCKTAIL_PROPERTIES，
 * 不会包含我们在别处注册的 fstwines 鸡尾酒，因此这里补一份。
 * 复用 {@link CocktailHelper} 从已注册的 CocktailProperty 创建带正确组件的酒杯。
 */
@EventBusSubscriber(modid = ToTheSky.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class CocktailCreativeTab {
    private CocktailCreativeTab() {
    }

    @SubscribeEvent
    public static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() != ModTabs.COCKTAIL_TAB.get()) {
            return;
        }
        ModCocktails.COCKTAILS.getEntries().forEach(holder -> {
            ItemStack stack = new ItemStack(ModItems.COCKTAIL.get());
            CocktailItem.setCocktail(stack, holder.get());
            event.accept(stack);
        });
    }
}

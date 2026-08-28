package com.fst.tothesky.client;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.registry.ModCocktails;
import io.github.tt432.kitchenkarrot.client.cocktail.CocktailModelRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * 客户端：把 fstwines 鸡尾酒的模型位置注册进 ModelManager。
 *
 * kitchenkarrot 的鸡尾酒由 CocktailBakedModel 渲染，它在 ItemOverrides.resolve 里通过
 * CocktailModelRegistry.RLtoMRL(prop.id()) 再 ModelManager.getModel(MRL) 取模型。
 * 但 kitchenkarrot 的 CocktailModelRegistry.register 只遍历它自己的 COCKTAIL_PROPERTIES，
 * 不会包含我们在别处注册的 fstwines 鸡尾酒，因此这里补注册。
 *
 * RLtoMRL(fstwines:david) → standalone 模型 fstwines:cocktail/david，
 * 对应模型文件 assets/fstwines/models/cocktail/david.json（与 kubejs 布局一致）。
 */
@EventBusSubscriber(modid = ToTheSky.MODID, value = Dist.CLIENT)
public final class CocktailClientEvents {
    private CocktailClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        ModCocktails.COCKTAILS.getEntries().forEach(holder ->
                event.register(CocktailModelRegistry.RLtoMRL(holder.get().id())));
    }
}

package com.fst.tothesky.registry;

import io.github.tt432.kitchenkarrot.cocktail.CocktailProperty;
import io.github.tt432.kitchenkarrot.recipes.object.EffectStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * fstwines 鸡尾酒的 CocktailProperty 注册。
 *
 * 背景：kitchenkarrot 1.21.1 把鸡尾酒身份从「NBT id → 查配方」改成「数据组件里的 CocktailProperty 对象」，
 * 对象由静态同步注册表 COCKTAILS_REGISTRY（RegistryBuilder.sync(true).create()）持有，
 * 配方 JSON 的 cocktail_property 用 Registry.holderByNameCodec() 解析——引用的 id 必须已在此注册表注册，
 * 否则配方解析失败、酒不存在。该注册表只能代码注册（NewRegistryEvent + DeferredRegister），
 * 数据包/资源包的 list.json 无法往里加。故：数据包（配方/资源/效果数据）原样保留，
 * 这里仅补 CocktailProperty 注册表项，以满足配方引用。
 *
 * 注意：
 * - 效果来自 kubejs data/fstwines/recipes/*.json 的 content.effect（1.20.1 schema），
 *   在 1.21.1 中由 CocktailItem.finishUsingItem 直接从本注册表项的 effectStack() 应用。
 * - dangerous_party 的 kubejs:hot_potato 不是真实效果（旧脚本击鼓传花标记），剔除，否则
 *   EffectStack.get() 解析不到效果会 NPE；击鼓传花由 CocktailEffects 事件触发。
 */
public final class ModCocktails {
    public static final DeferredRegister<CocktailProperty> COCKTAILS =
            DeferredRegister.create(io.github.tt432.kitchenkarrot.registries.ModCocktails.COCKTAILS_REGISTRY, "fstwines");

    public static final DeferredHolder<CocktailProperty, CocktailProperty> DAVID =
            register("david", "xtaotie233,ACHTER_BERG", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600),
                    new EffectStack("minecraft:speed", 1, 600),
                    new EffectStack("minecraft:slowness", 0, 200),
                    new EffectStack("minecraft:nausea", 1, 100)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> CALL_OF_TAHITI =
            register("call_of_tahiti", "Volloval", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600),
                    new EffectStack("minecraft:blindness", 0, 200),
                    new EffectStack("minecraft:mining_fatigue", 0, 600)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> FREE_NIGHTINGALE =
            register("free_nightingale", "ACHTER_BERG", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600),
                    new EffectStack("minecraft:night_vision", 0, 3600)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> DANGEROUS_PARTY =
            register("dangerous_party", "EndGlacier", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600),
                    new EffectStack("minecraft:glowing", 0, 1200)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> SILENT_MIDNIGHT =
            register("silent_midnight", "EndGlacier", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600),
                    new EffectStack("create_confectionery:rest", 0, 36000)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> SHOAL_IN_DREAM =
            register("shoal_in_dream", "EndGlacier", List.of(
                    new EffectStack("kitchenkarrot:tipsy", 0, 3600)));

    public static final DeferredHolder<CocktailProperty, CocktailProperty> LAGO_DE_TEXCOCO =
            register("lago_de_texcoco", "Vic_Bloomfield", List.of(
                    new EffectStack("minecraft:speed", 1, 6000),
                    new EffectStack("minecraft:absorption", 1, 6000),
                    new EffectStack("kitchenkarrot:tipsy", 1, 3600)));

    private static DeferredHolder<CocktailProperty, CocktailProperty> register(
            String name, String author, List<EffectStack> effects) {
        return COCKTAILS.register(name,
                () -> new CocktailProperty(ResourceLocation.fromNamespaceAndPath("fstwines", name), author, effects));
    }

    private ModCocktails() {
    }
}

package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.item.CookedDumplingItem;
import com.fst.tothesky.item.CookedDumplingPlateItem;
import com.fst.tothesky.item.DumplingWrapperItem;
import com.fst.tothesky.item.SpecialFoodItems;
import com.fst.tothesky.item.TooltipBlockItem;
import com.fst.tothesky.item.TooltipItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册：特色食品与其食材。
 * 数值移植自 kubejs/startup_scripts/registry.js（KubeJS 未写 saturation 时默认 0.5）。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ToTheSky.MODID);

    /** 农夫乐事的滋养效果 */
    private static final net.minecraft.world.effect.MobEffect NOURISHMENT_EFFECT =
            vectorwing.farmersdelight.common.registry.ModEffects.NOURISHMENT.get();

    // ---------------- 方块物品 ----------------
    public static final RegistryObject<TooltipBlockItem> RAMEN = ITEMS.register("ramen",
            () -> new TooltipBlockItem(ModBlocks.RAMEN.get(), new Item.Properties(), "ramen", 0));
    public static final RegistryObject<TooltipBlockItem> PIZZA_MARGARITA = ITEMS.register("pizza_margarita",
            () -> new TooltipBlockItem(ModBlocks.PIZZA_MARGARITA.get(), new Item.Properties(), "pizza_margarita", 3));
    public static final RegistryObject<TooltipBlockItem> PORK_PIZZA = ITEMS.register("pork_pizza",
            () -> new TooltipBlockItem(ModBlocks.PORK_PIZZA.get(), new Item.Properties(), "pork_pizza", 2));
    public static final RegistryObject<TooltipBlockItem> APPLE_PIZZA = ITEMS.register("apple_pizza",
            () -> new TooltipBlockItem(ModBlocks.APPLE_PIZZA.get(), new Item.Properties(), "apple_pizza", 4));

    // ---------------- 特色食品 ----------------
    // 巧克力意面
    public static final RegistryObject<Item> PASTA_WITH_CHOCOLATE = ITEMS.register("pasta_with_chocolate",
            () -> new TooltipItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.BAD_OMEN, 600, 1), 0.02f)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 2), 0.75f)
                    .build()), "pasta_with_chocolate", 3));
    // 焦糖鳕鱼羹
    public static final RegistryObject<Item> CARAMEL_COD_SOUP = ITEMS.register("caramel_cod_soup",
            () -> new SpecialFoodItems.CaramelCodSoup(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(10).saturationMod(0.5f)
                    .build())));
    // 健胃消食片
    public static final RegistryObject<Item> DIGESTION_PELLOW = ITEMS.register("digestion_pellow",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(0).saturationMod(0.5f)
                    .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 300, 79), 1.0f)
                    .alwaysEat()
                    .build()), "digestion_pellow", 7));
    // 鱿鱼狂欢节（碗装，吃完返碗）
    public static final RegistryObject<Item> SQUID_FESTIVAL = ITEMS.register("squid_festival",
            () -> new SpecialFoodItems.SquidFestival(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 5), 1.0f)
                    .build())));
    // 深海鳕鱼堡
    public static final RegistryObject<Item> COD_BURGER = ITEMS.register("cod_burger",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(12).saturationMod(1.0f)
                    .build()), "cod_burger", 6));
    // 油炸鳕鱼
    public static final RegistryObject<Item> FRIED_COD = ITEMS.register("fried_cod",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(8).saturationMod(1.0f)
                    .build()), "fried_cod", 5));
    // 切制奶酪
    public static final RegistryObject<Item> CUT_CHEESE = ITEMS.register("cut_cheese",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .build()), "cut_cheese", 2));
    // 切片玛格丽特披萨
    public static final RegistryObject<Item> SLICED_PIZZA_MARGARITA = ITEMS.register("sliced_pizza_margarita",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(NOURISHMENT_EFFECT, 1200, 1), 1.0f)
                    .build()), "sliced_pizza_margarita", 3));
    // 切片猪肉碎披萨
    public static final RegistryObject<Item> SLICED_PORK_PIZZA = ITEMS.register("sliced_pork_pizza",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationMod(1.2f)
                    .effect(() -> new MobEffectInstance(NOURISHMENT_EFFECT, 1800, 1), 1.0f)
                    .build()), "sliced_pork_pizza", 3));
    // 切片苹果披萨
    public static final RegistryObject<Item> SLICED_APPLE_PIZZA = ITEMS.register("sliced_apple_pizza",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationMod(1.0f)
                    .effect(() -> new MobEffectInstance(NOURISHMENT_EFFECT, 1300, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.BAD_OMEN, 600, 1), 0.02f)
                    .build()), "sliced_apple_pizza", 3));
    // 劲爆鳕鱼堡
    public static final RegistryObject<Item> BOMB_COD_BURGER = ITEMS.register("bomb_cod_burger",
            () -> new SpecialFoodItems.BombCodBurger(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(14).saturationMod(14.0f)
                    .build())));
    // 幻翼虾仁
    public static final RegistryObject<Item> PHANTOM_SHRIMP = ITEMS.register("phantom_shrimp",
            () -> new SpecialFoodItems.PhantomShrimp(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(7).saturationMod(1.5f)
                    .build())));
    // 三角粥
    public static final RegistryObject<Item> DELTA_PORRIDGE = ITEMS.register("delta_porridge",
            () -> new SpecialFoodItems.DeltaPorridge(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(9).saturationMod(0.8f)
                    .build())));
    // 饮品659
    public static final RegistryObject<Item> DRINK_659 = ITEMS.register("drink659",
            () -> new SpecialFoodItems.Drink659(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationMod(1.5f)
                    .alwaysEat()
                    .build())));
    // 晴天鳕鱼
    public static final RegistryObject<Item> SUNSHINE_COD = ITEMS.register("sunshine_cod",
            () -> new SpecialFoodItems.SunshineCod(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationMod(1.5f)
                    .alwaysEat()
                    .build())));
    // 温泉蛋牛肉盖饭
    public static final RegistryObject<Item> BEEF_OVER_RICE = ITEMS.register("beef_over_rice",
            () -> new TooltipItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(10).saturationMod(0.6f)
                    .alwaysEat()
                    .build()), "beef_over_rice", 0));
    // 秘封洋葱绿叶肥虫汤
    public static final RegistryObject<Item> BUG_SOUP = ITEMS.register("bug_soup",
            () -> new SpecialFoodItems.BugSoup(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(0.2f)
                    .effect(() -> new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1), 1.0f)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0), 1.0f)
                    .alwaysEat()
                    .build())));
    // 饺子
    public static final RegistryObject<Item> COOKED_DUMPLING = ITEMS.register("cooked_dumpling",
            () -> new CookedDumplingItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationMod(1.0f)
                    .fast()
                    .build())));
    // 一盘熟饺子（方块物品）
    public static final RegistryObject<CookedDumplingPlateItem> COOKED_DUMPLING_PLATE_ITEM =
            ITEMS.register("cooked_dumpling_plate",
                    () -> new CookedDumplingPlateItem(ModBlocks.COOKED_DUMPLING_PLATE.get(),
                            new Item.Properties().stacksTo(1)));

    // ---------------- 食材 ----------------
    public static final RegistryObject<Item> CHEESE = simple("cheese", 2);
    public static final RegistryObject<Item> PIZZA_BASE = simple("pizza_base", 3);
    public static final RegistryObject<Item> RAW_PIZZA_MARGARITA = simple("raw_pizza_margarita", 2);
    public static final RegistryObject<Item> RAW_PORK_PIZZA = simple("raw_pork_pizza", 2);
    public static final RegistryObject<Item> RAW_APPLE_PIZZA = simple("raw_apple_pizza", 2);
    public static final RegistryObject<Item> RAW_SUNSHINE_COD = simple("raw_sunshine_cod", 0);
    public static final RegistryObject<Item> DUMPLING_WRAPPER = ITEMS.register("dumpling_wrapper",
            () -> new DumplingWrapperItem(new Item.Properties()));
    public static final RegistryObject<Item> RAW_DUMPLING = simple("raw_dumpling", 1);
    public static final RegistryObject<Item> RAW_DUMPLING_PLATE = ITEMS.register("raw_dumpling_plate",
            () -> new TooltipItem(new Item.Properties().stacksTo(1), "raw_dumpling_plate", 1));

    // ---------------- 下界合金产线材料 ----------------
    /** 血瓶：下界溶液原料（移植自 kjs，maxStackSize 1，用完返还玻璃瓶） */
    public static final RegistryObject<Item> BLOOD_BOTTLE = ITEMS.register("blood_bottle",
            () -> new Item(new Item.Properties().stacksTo(1).craftRemainder(Items.GLASS_BOTTLE)));
    /** 不纯合金坯：钻石+铁+金 超高温混合 */
    public static final RegistryObject<Item> IMPURE_ALLOY_BASE = simple("impure_alloy_base", 0);
    /** 粗制合金坯：不纯合金坯+岩浆 压实 */
    public static final RegistryObject<Item> RAW_ALLOY_BASE = simple("raw_alloy_base", 0);
    /** 半成品合金：下界合金序列组装的中间品 */
    public static final RegistryObject<Item> INCOMPLETE_NETHERITE_INGOT = simple("incomplete_netherite_ingot", 0);
    /** 魔女因子：AE2 奇点 + 下界合金碎片 + 灵魂珠 + 下界溶液 超高温压实 */
    public static final RegistryObject<Item> WITCH_FACTOR = simple("witch_factor", 0);
    /** 活化的魔女因子：魔女因子经 createaddition 充电活化（饮品659 原料） */
    public static final RegistryObject<Item> ACTIVATED_WITCH_FACTOR = simple("activated_witch_factor", 0);
    /** 三角尘：三角币掰碎的粉末（饮品659 原料；上游来自三角币经济系统） */
    public static final RegistryObject<Item> DELTA_DUST = simple("delta_dust", 0);
    /** 三角币：经济系统货币（生晴天鳕鱼原料；上游来自 sell&roll 经济系统） */
    public static final RegistryObject<Item> DELTA_COIN = simple("delta_coin", 0);
    /** 三角片：三角币掰碎（三角粥原料；上游来自经济系统） */
    public static final RegistryObject<Item> DELTA_COIN_CHIP = simple("delta_coin_chip", 0);

    // ---------------- 售货机 / 扭蛋机 ----------------
    /** 抽奖券：右键扭蛋机绑定 key，消费时需手持匹配的券 */
    public static final RegistryObject<Item> ROLLER_TICKET = simple("roller_ticket", 0);
    /** 售货机方块物品 */
    public static final RegistryObject<BlockItem> SELLER_ITEM =
            ITEMS.register("seller", () -> new BlockItem(ModBlocks.SELLER.get(), new Item.Properties()));
    /** 扭蛋机方块物品 */
    public static final RegistryObject<BlockItem> ROLLER_ITEM =
            ITEMS.register("roller", () -> new BlockItem(ModBlocks.ROLLER.get(), new Item.Properties()));

    private static RegistryObject<Item> simple(String name, int tooltipLines) {
        return ITEMS.register(name, () -> tooltipLines > 0
                ? new TooltipItem(new Item.Properties(), name, tooltipLines)
                : new Item(new Item.Properties()));
    }

    private ModItems() {
    }
}
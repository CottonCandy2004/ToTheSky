package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.CookedDumplingPlateBlock;
import com.fst.tothesky.block.DrinkGlassBlock;
import com.fst.tothesky.block.EmptyGlassBlock;
import com.fst.tothesky.block.PizzaBlock;
import com.fst.tothesky.block.RollerBlock;
import com.fst.tothesky.block.SellerBlock;
import com.fst.tothesky.block.RamenBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * 方块注册：拉面、三种披萨、十二种可放置鸡尾酒与三种空杯。
 * 移植自 kubejs/startup_scripts/registry.js 与 drink_block_registry.js。
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ToTheSky.MODID);

    private static final BlockBehaviour.Properties FOOD_BLOCK_PROPS =
            BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(1.0f).noOcclusion();

    private static BlockBehaviour.Properties glassProps() {
        return BlockBehaviour.Properties.of().sound(SoundType.GLASS).strength(0.1f).noOcclusion();
    }

    // 拉面：右键分五口吃掉，吃完返碗
    public static final DeferredBlock<RamenBlock> RAMEN = BLOCKS.register("ramen", () -> new RamenBlock(FOOD_BLOCK_PROPS));

    // 披萨：右键取片，取完四片消失
    public static final DeferredBlock<PizzaBlock> PIZZA_MARGARITA =
            BLOCKS.register("pizza_margarita", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PIZZA_MARGARITA));
    public static final DeferredBlock<PizzaBlock> PORK_PIZZA =
            BLOCKS.register("pork_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PORK_PIZZA));
    public static final DeferredBlock<PizzaBlock> APPLE_PIZZA =
            BLOCKS.register("apple_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_APPLE_PIZZA));

    // 一盘熟饺子：右键逐个取食，内容物存于方块实体
    public static final DeferredBlock<CookedDumplingPlateBlock> COOKED_DUMPLING_PLATE =
            BLOCKS.register("cooked_dumpling_plate", () -> new CookedDumplingPlateBlock(FOOD_BLOCK_PROPS));

    // 空杯
    public static final DeferredBlock<EmptyGlassBlock> MARTINI_GLASS =
            BLOCKS.register("martini_glass", () -> new EmptyGlassBlock(glassProps(), 10));
    public static final DeferredBlock<EmptyGlassBlock> HURRICANE_GLASS =
            BLOCKS.register("hurricane_glass", () -> new EmptyGlassBlock(glassProps(), 11));
    public static final DeferredBlock<EmptyGlassBlock> OLD_FASHIONED_GLASS =
            BLOCKS.register("old_fashioned_glass", () -> new EmptyGlassBlock(glassProps(), 7));

    // 鸡尾酒方块（无物品形态），对应关系移植自 place_drink_block.js 的 COCKTAIL_MAP
    public static final DeferredBlock<DrinkGlassBlock> JULY_21_BLOCK =
            martini("july_21_block", "july_21");
    public static final DeferredBlock<DrinkGlassBlock> TSUNDERE_HEROINE_BLOCK =
            martini("tsundere_heroine_block", "tsundere_heroine");
    public static final DeferredBlock<DrinkGlassBlock> SWEET_BERRY_MARTINI_BLOCK =
            martini("sweet_berry_martini_block", "sweet_berry_martini");
    public static final DeferredBlock<DrinkGlassBlock> BIRCH_SAP_VODKA_BLOCK =
            martini("birch_sap_vodka_block", "birch_sap_vodka");
    public static final DeferredBlock<DrinkGlassBlock> RED_LIZARD_BLOCK =
            hurricane("red_lizard_block", "red_lizard");
    public static final DeferredBlock<DrinkGlassBlock> SECOND_GUESS_BLOCK =
            hurricane("second_guess_block", "second_guess");
    public static final DeferredBlock<DrinkGlassBlock> LIGHT_YELLOW_FIREFLY_BLOCK =
            hurricane("light_yellow_firefly_block", "light_yellow_firefly");
    public static final DeferredBlock<DrinkGlassBlock> SHOOTING_STAR_BLOCK =
            hurricane("shooting_star_block", "shooting_star");
    public static final DeferredBlock<DrinkGlassBlock> TWILIGHT_FOREST_BLOCK =
            oldFashioned("twilight_forest_block", "twilight_forest");
    public static final DeferredBlock<DrinkGlassBlock> JACKS_STORY_BLOCK =
            oldFashioned("jacks_story_block", "jacks_story");
    public static final DeferredBlock<DrinkGlassBlock> SHANGHAI_BEACH_BLOCK =
            oldFashioned("shanghai_beach_block", "shanghai_beach");
    public static final DeferredBlock<DrinkGlassBlock> BANE_OF_ARTHROPODS_BLOCK =
            oldFashioned("bane_of_arthropods_block", "bane_of_arthropods");

    /** 所有鸡尾酒方块，供放置事件按鸡尾酒 id 反查 */
    public static final List<DeferredBlock<DrinkGlassBlock>> COCKTAIL_BLOCKS = List.of(
            JULY_21_BLOCK, TSUNDERE_HEROINE_BLOCK, SWEET_BERRY_MARTINI_BLOCK, BIRCH_SAP_VODKA_BLOCK,
            RED_LIZARD_BLOCK, SECOND_GUESS_BLOCK, LIGHT_YELLOW_FIREFLY_BLOCK, SHOOTING_STAR_BLOCK,
            TWILIGHT_FOREST_BLOCK, JACKS_STORY_BLOCK, SHANGHAI_BEACH_BLOCK, BANE_OF_ARTHROPODS_BLOCK);

    // ---------------- 售货机 / 扭蛋机 ----------------
    public static final DeferredBlock<SellerBlock> SELLER =
            BLOCKS.register("seller", SellerBlock::new);
    public static final DeferredBlock<RollerBlock> ROLLER =
            BLOCKS.register("roller", RollerBlock::new);

    @Nullable
    public static DrinkGlassBlock drinkBlockFor(ResourceLocation cocktailId) {
        for (DeferredBlock<DrinkGlassBlock> holder : COCKTAIL_BLOCKS) {
            if (holder.get().cocktailId().equals(cocktailId)) {
                return holder.get();
            }
        }
        return null;
    }

    private static DeferredBlock<DrinkGlassBlock> martini(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Martini(kk(cocktail), MARTINI_GLASS, glassProps()));
    }

    private static DeferredBlock<DrinkGlassBlock> hurricane(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Hurricane(kk(cocktail), HURRICANE_GLASS, glassProps()));
    }

    private static DeferredBlock<DrinkGlassBlock> oldFashioned(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.OldFashioned(kk(cocktail), OLD_FASHIONED_GLASS, glassProps()));
    }

    private static ResourceLocation kk(String path) {
        return ResourceLocation.fromNamespaceAndPath("kitchenkarrot", path);
    }

    private ModBlocks() {
    }
}

package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.CookedDumplingPlateBlock;
import com.fst.tothesky.block.DrinkGlassBlock;
import com.fst.tothesky.block.EmptyGlassBlock;
import com.fst.tothesky.block.PizzaBlock;
import com.fst.tothesky.block.RollerBlock;
import com.fst.tothesky.block.SellerBlock;
import com.fst.tothesky.block.RamenBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 方块注册：拉面、三种披萨、十二种可放置鸡尾酒与三种空杯。
 * 移植自 kubejs/startup_scripts/registry.js 与 drink_block_registry.js。
 */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ToTheSky.MODID);

    private static final BlockBehaviour.Properties FOOD_BLOCK_PROPS =
            BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(1.0f).noOcclusion();

    private static BlockBehaviour.Properties glassProps() {
        return BlockBehaviour.Properties.of().sound(SoundType.GLASS).strength(0.1f).noOcclusion();
    }

    // 拉面：右键分五口吃掉，吃完返碗
    public static final RegistryObject<RamenBlock> RAMEN =
            BLOCKS.register("ramen", () -> new RamenBlock(FOOD_BLOCK_PROPS));

    // 披萨：右键取片，取完四片消失
    public static final RegistryObject<PizzaBlock> PIZZA_MARGARITA =
            BLOCKS.register("pizza_margarita", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PIZZA_MARGARITA));
    public static final RegistryObject<PizzaBlock> PORK_PIZZA =
            BLOCKS.register("pork_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_PORK_PIZZA));
    public static final RegistryObject<PizzaBlock> APPLE_PIZZA =
            BLOCKS.register("apple_pizza", () -> new PizzaBlock(FOOD_BLOCK_PROPS, ModItems.SLICED_APPLE_PIZZA));

    // 一盘熟饺子：右键逐个取食，内容物存于方块实体
    public static final RegistryObject<CookedDumplingPlateBlock> COOKED_DUMPLING_PLATE =
            BLOCKS.register("cooked_dumpling_plate", () -> new CookedDumplingPlateBlock(FOOD_BLOCK_PROPS));

    // 空杯
    public static final RegistryObject<EmptyGlassBlock> MARTINI_GLASS =
            BLOCKS.register("martini_glass", () -> new EmptyGlassBlock(glassProps(), 10));
    public static final RegistryObject<EmptyGlassBlock> HURRICANE_GLASS =
            BLOCKS.register("hurricane_glass", () -> new EmptyGlassBlock(glassProps(), 11));
    public static final RegistryObject<EmptyGlassBlock> OLD_FASHIONED_GLASS =
            BLOCKS.register("old_fashioned_glass", () -> new EmptyGlassBlock(glassProps(), 7));

    // 鸡尾酒方块（无物品形态），对应关系移植自 place_drink_block.js 的 COCKTAIL_MAP
    public static final RegistryObject<DrinkGlassBlock> JULY_21_BLOCK =
            martini("july_21_block", "july_21");
    public static final RegistryObject<DrinkGlassBlock> TSUNDERE_HEROINE_BLOCK =
            martini("tsundere_heroine_block", "tsundere_heroine");
    public static final RegistryObject<DrinkGlassBlock> SWEET_BERRY_MARTINI_BLOCK =
            martini("sweet_berry_martini_block", "sweet_berry_martini");
    public static final RegistryObject<DrinkGlassBlock> BIRCH_SAP_VODKA_BLOCK =
            martini("birch_sap_vodka_block", "birch_sap_vodka");
    public static final RegistryObject<DrinkGlassBlock> RED_LIZARD_BLOCK =
            hurricane("red_lizard_block", "red_lizard");
    public static final RegistryObject<DrinkGlassBlock> SECOND_GUESS_BLOCK =
            hurricane("second_guess_block", "second_guess");
    public static final RegistryObject<DrinkGlassBlock> LIGHT_YELLOW_FIREFLY_BLOCK =
            hurricane("light_yellow_firefly_block", "light_yellow_firefly");
    public static final RegistryObject<DrinkGlassBlock> SHOOTING_STAR_BLOCK =
            hurricane("shooting_star_block", "shooting_star");
    public static final RegistryObject<DrinkGlassBlock> TWILIGHT_FOREST_BLOCK =
            oldFashioned("twilight_forest_block", "twilight_forest");
    public static final RegistryObject<DrinkGlassBlock> JACKS_STORY_BLOCK =
            oldFashioned("jacks_story_block", "jacks_story");
    public static final RegistryObject<DrinkGlassBlock> SHANGHAI_BEACH_BLOCK =
            oldFashioned("shanghai_beach_block", "shanghai_beach");
    public static final RegistryObject<DrinkGlassBlock> BANE_OF_ARTHROPODS_BLOCK =
            oldFashioned("bane_of_arthropods_block", "bane_of_arthropods");

    /** 所有鸡尾酒方块，供放置事件按鸡尾酒 id 反查 */
    public static final List<RegistryObject<DrinkGlassBlock>> COCKTAIL_BLOCKS = List.of(
            JULY_21_BLOCK, TSUNDERE_HEROINE_BLOCK, SWEET_BERRY_MARTINI_BLOCK, BIRCH_SAP_VODKA_BLOCK,
            RED_LIZARD_BLOCK, SECOND_GUESS_BLOCK, LIGHT_YELLOW_FIREFLY_BLOCK, SHOOTING_STAR_BLOCK,
            TWILIGHT_FOREST_BLOCK, JACKS_STORY_BLOCK, SHANGHAI_BEACH_BLOCK, BANE_OF_ARTHROPODS_BLOCK);

    // ---------------- 售货机 / 扭蛋机 ----------------
    public static final RegistryObject<SellerBlock> SELLER =
            BLOCKS.register("seller", SellerBlock::new);
    public static final RegistryObject<RollerBlock> ROLLER =
            BLOCKS.register("roller", RollerBlock::new);

    @Nullable
    public static DrinkGlassBlock drinkBlockFor(ResourceLocation cocktailId) {
        for (RegistryObject<DrinkGlassBlock> holder : COCKTAIL_BLOCKS) {
            if (holder.get().cocktailId().equals(cocktailId)) {
                return holder.get();
            }
        }
        return null;
    }

    private static RegistryObject<DrinkGlassBlock> martini(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Martini(kk(cocktail), MARTINI_GLASS, glassProps()));
    }

    private static RegistryObject<DrinkGlassBlock> hurricane(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.Hurricane(kk(cocktail), HURRICANE_GLASS, glassProps()));
    }

    private static RegistryObject<DrinkGlassBlock> oldFashioned(String name, String cocktail) {
        return BLOCKS.register(name, () -> new DrinkGlassBlock.OldFashioned(kk(cocktail), OLD_FASHIONED_GLASS, glassProps()));
    }

    private static ResourceLocation kk(String path) {
        return new ResourceLocation("kitchenkarrot", path);
    }

    private ModBlocks() {
    }
}
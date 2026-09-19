package com.fst.tothesky.crystalclear;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.block.GlassCasingBlock;
import com.fst.tothesky.block.GlassEncasedCogwheelBlock;
import com.fst.tothesky.block.GlassEncasedShaftBlock;
import com.simibubi.create.content.decoration.MetalScaffoldingBlock;
import com.simibubi.create.content.decoration.MetalScaffoldingBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Create: Crystal Clear（Crystal-Clear-Arch / Cyvack）的 32 个方块，移植到本 mod。
 * <p>
 * 注册名与上游<b>逐字相同</b>（只是命名空间换成 {@code tothesky}），旧存档里的
 * {@code crystal_clear:*} / {@code create_crystal_clear:*} 由
 * {@link com.fst.tothesky.event.MissingMappingEvents} 按同名规则 remap 到本 mod，
 * 方块实体类型则走 {@link com.fst.tothesky.event.RegistryAliasEvents} 的注册表别名。
 * <p>
 * 于是这套方块自身的 id 与上游一致（形状、数量、分组都按上游）：
 * <ul>
 *   <li>8 种玻璃机壳：4 种基底（安山/黄铜/铜/列车）× 普通、透明；</li>
 *   <li>18 种玻璃包裹方块：3 种机壳（安山/黄铜/列车）× 普通、透明 × 传动杆/齿轮/大齿轮；</li>
 *   <li>6 种玻璃脚手架：3 种基底（安山/黄铜/铜）× 普通、透明。</li>
 * </ul>
 * 上游另有钢铁（steel）机壳，但它在 2.1-Beta 之前就被作者删掉了——本 mod 同样不注册，
 * 旧存档里那三个条目会按 Forge 默认策略报缺失。
 */
public final class CrystalClearBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ToTheSky.MODID);
    /**
     * 这 32 个方块自带的 BlockItem。
     * <p>
     * 单独一个注册表（而不是塞进 {@code ModItems}）是为了让创造栏挑选：包裹传动杆/齿轮的物品
     * 在上游就是隐藏的（只能由装壳得到），只有玻璃机壳与玻璃脚手架出现在创造栏里。
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ToTheSky.MODID);

    private static final List<String> GLASS_CASING_TYPES = List.of("andesite", "brass", "copper", "train");
    private static final List<String> ENCASED_CASING_TYPES = List.of("andesite", "brass", "train");
    private static final List<String> SCAFFOLD_CASING_TYPES = List.of("andesite", "brass", "copper");

    private static final List<RegistryObject<? extends Block>> ALL_BLOCKS = new ArrayList<>();
    private static final List<RegistryObject<? extends Item>> CREATIVE_TAB_ITEMS = new ArrayList<>();

    /** {@code <casing>_glass_casing}（普通玻璃机壳），键为机壳类型名 */
    public static final Map<String, RegistryObject<GlassCasingBlock>> GLASS_CASINGS = glassCasings(false);
    /** {@code <casing>_clear_glass_casing}（透明玻璃机壳） */
    public static final Map<String, RegistryObject<GlassCasingBlock>> CLEAR_GLASS_CASINGS = glassCasings(true);

    /** {@code <casing>_glass_encased_shaft} */
    public static final Map<String, RegistryObject<GlassEncasedShaftBlock>> GLASS_ENCASED_SHAFTS = encasedShafts(false);
    /** {@code <casing>_clear_glass_encased_shaft} */
    public static final Map<String, RegistryObject<GlassEncasedShaftBlock>> CLEAR_GLASS_ENCASED_SHAFTS = encasedShafts(true);

    /** {@code <casing>_glass_encased_cogwheel} */
    public static final Map<String, RegistryObject<GlassEncasedCogwheelBlock>> GLASS_ENCASED_COGWHEELS = encasedCogwheels(false, false);
    /** {@code <casing>_clear_glass_encased_cogwheel} */
    public static final Map<String, RegistryObject<GlassEncasedCogwheelBlock>> CLEAR_GLASS_ENCASED_COGWHEELS = encasedCogwheels(true, false);
    /** {@code <casing>_glass_encased_large_cogwheel} */
    public static final Map<String, RegistryObject<GlassEncasedCogwheelBlock>> GLASS_ENCASED_LARGE_COGWHEELS = encasedCogwheels(false, true);
    /** {@code <casing>_clear_glass_encased_large_cogwheel} */
    public static final Map<String, RegistryObject<GlassEncasedCogwheelBlock>> CLEAR_GLASS_ENCASED_LARGE_COGWHEELS = encasedCogwheels(true, true);

    /** {@code <casing>_glass_scaffolding} */
    public static final Map<String, RegistryObject<MetalScaffoldingBlock>> GLASS_SCAFFOLDINGS = scaffoldings(false);
    /** {@code <casing>_clear_glass_scaffolding} */
    public static final Map<String, RegistryObject<MetalScaffoldingBlock>> CLEAR_GLASS_SCAFFOLDINGS = scaffoldings(true);

    /** 全部 32 个方块（客户端批量设置渲染层用） */
    public static List<RegistryObject<? extends Block>> allBlocks() {
        return List.copyOf(ALL_BLOCKS);
    }

    /** 创造栏可见的物品：8 种玻璃机壳 + 6 种玻璃脚手架 */
    public static List<RegistryObject<? extends Item>> creativeTabItems() {
        return List.copyOf(CREATIVE_TAB_ITEMS);
    }

    /** 6 种玻璃包裹传动杆（方块实体类型的有效方块） */
    public static Block[] encasedShaftBlocks() {
        return union(GLASS_ENCASED_SHAFTS, CLEAR_GLASS_ENCASED_SHAFTS);
    }

    /** 6 种玻璃包裹齿轮 */
    public static Block[] encasedCogwheelBlocks() {
        return union(GLASS_ENCASED_COGWHEELS, CLEAR_GLASS_ENCASED_COGWHEELS);
    }

    /** 6 种玻璃包裹大齿轮 */
    public static Block[] encasedLargeCogwheelBlocks() {
        return union(GLASS_ENCASED_LARGE_COGWHEELS, CLEAR_GLASS_ENCASED_LARGE_COGWHEELS);
    }

    private static Block[] union(Map<String, ? extends RegistryObject<? extends Block>> normal,
                                 Map<String, ? extends RegistryObject<? extends Block>> clear) {
        List<Block> blocks = new ArrayList<>();
        normal.values().forEach(block -> blocks.add(block.get()));
        clear.values().forEach(block -> blocks.add(block.get()));
        return blocks.toArray(Block[]::new);
    }

    private static Map<String, RegistryObject<GlassCasingBlock>> glassCasings(boolean clear) {
        Map<String, RegistryObject<GlassCasingBlock>> casings = new LinkedHashMap<>();
        for (String casing : GLASS_CASING_TYPES) {
            String name = casing + (clear ? "_clear" : "") + "_glass_casing";
            RegistryObject<GlassCasingBlock> block = register(name,
                    () -> new GlassCasingBlock(BlockBehaviour.Properties.copy(Blocks.GLASS)));
            CREATIVE_TAB_ITEMS.add(simpleItem(name, block));
            casings.put(casing, block);
        }
        return casings;
    }

    private static Map<String, RegistryObject<GlassEncasedShaftBlock>> encasedShafts(boolean clear) {
        Map<String, RegistryObject<GlassEncasedShaftBlock>> shafts = new LinkedHashMap<>();
        for (String casing : ENCASED_CASING_TYPES) {
            String name = casing + (clear ? "_clear" : "") + "_glass_encased_shaft";
            RegistryObject<GlassCasingBlock> casingBlock = casingOf(casing, clear);
            RegistryObject<GlassEncasedShaftBlock> block = register(name,
                    () -> new GlassEncasedShaftBlock(BlockBehaviour.Properties.copy(Blocks.GLASS), casingBlock::get));
            simpleItem(name, block);
            shafts.put(casing, block);
        }
        return shafts;
    }

    private static Map<String, RegistryObject<GlassEncasedCogwheelBlock>> encasedCogwheels(boolean clear, boolean large) {
        Map<String, RegistryObject<GlassEncasedCogwheelBlock>> cogwheels = new LinkedHashMap<>();
        for (String casing : ENCASED_CASING_TYPES) {
            String name = casing + (clear ? "_clear" : "") + "_glass_encased_"
                    + (large ? "large_cogwheel" : "cogwheel");
            RegistryObject<GlassCasingBlock> casingBlock = casingOf(casing, clear);
            RegistryObject<GlassEncasedCogwheelBlock> block = register(name,
                    () -> new GlassEncasedCogwheelBlock(BlockBehaviour.Properties.copy(Blocks.GLASS), large, casingBlock::get));
            simpleItem(name, block);
            cogwheels.put(casing, block);
        }
        return cogwheels;
    }

    private static Map<String, RegistryObject<MetalScaffoldingBlock>> scaffoldings(boolean clear) {
        Map<String, RegistryObject<MetalScaffoldingBlock>> scaffoldings = new LinkedHashMap<>();
        for (String casing : SCAFFOLD_CASING_TYPES) {
            String name = casing + (clear ? "_clear" : "") + "_glass_scaffolding";
            RegistryObject<MetalScaffoldingBlock> block = register(name,
                    () -> new MetalScaffoldingBlock(BlockBehaviour.Properties.copy(Blocks.SCAFFOLDING)
                            .sound(SoundType.COPPER)));
            CREATIVE_TAB_ITEMS.add(ITEMS.register(name, () -> new MetalScaffoldingBlockItem(block.get(), new Item.Properties())));
            scaffoldings.put(casing, block);
        }
        return scaffoldings;
    }

    private static RegistryObject<GlassCasingBlock> casingOf(String casing, boolean clear) {
        return (clear ? CLEAR_GLASS_CASINGS : GLASS_CASINGS).get(casing);
    }

    private static <B extends Block> RegistryObject<B> register(String name, Supplier<B> factory) {
        RegistryObject<B> block = BLOCKS.register(name, factory);
        ALL_BLOCKS.add(block);
        return block;
    }

    /** 与方块同名的普通 BlockItem（注册进去即可，是否进创造栏由 {@link #CREATIVE_TAB_ITEMS} 决定） */
    private static RegistryObject<BlockItem> simpleItem(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private CrystalClearBlocks() {
    }
}

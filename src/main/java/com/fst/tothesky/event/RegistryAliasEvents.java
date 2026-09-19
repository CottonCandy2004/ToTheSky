package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 方块实体类型的旧命名空间别名（kubejs: / crystal_clear: / create_crystal_clear: → tothesky:）。
 *
 * <p>为什么不能走 {@link MissingMappingsEvent}：方块实体类型注册表在 Forge 侧是
 * {@code disableSaving()} 的（{@code GameData} 建表时即如此），从不写进存档的
 * {@code level.dat → fml.Registries} 快照，因此永远不会产生“缺失映射”事件。
 * 而区块里的方块实体是以名字（{@code block_entities[].id}）解析的，旧存档写着
 * {@code kubejs:cooked_dumpling_plate} 或 {@code create_crystal_clear:glass_encased_shaft}
 * 时只有别名能让它落到本 mod 的注册项上——否则该方块实体会解析失败、整盘馅料/整根传动轴的
 * 旋转状态丢失。
 *
 * <p>{@link RegisterEvent} 在 Forge 注册表冻结之前触发（LOAD_REGISTRIES 状态里
 * 临时 unfreeze），是唯一能 {@code addAlias} 的时机；冻结后调用会抛
 * {@code IllegalStateException}。别名会随注册表在存档加载时被
 * {@code ForgeRegistry.sync} 合并保留，对游戏后期建表的注册表同样有效。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RegistryAliasEvents {
    /** Create: Crystal Clear 的两个历史命名空间（0.5.1 时代为 create_crystal_clear，2.x 改名 crystal_clear） */
    private static final String CRYSTAL_CLEAR = "crystal_clear";
    private static final String CRYSTAL_CLEAR_LEGACY = "create_crystal_clear";

    /** 旧命名空间 → (旧注册名 → mod 注册名)。同一命名空间内可以有多个条目。 */
    private static final Map<String, Map<String, String>> BLOCK_ENTITY_ALIASES = blockEntityAliases();

    private static Map<String, Map<String, String>> blockEntityAliases() {
        Map<String, String> kjs = Map.of(
                "cooked_dumpling_plate", "dumpling_plate",
                "seller", "seller",
                "roller", "roller",
                "mechanical_chisel_table", "mechanical_chisel_table");

        // 移植过来的三个方块实体类型与上游注册名逐字相同，只是命名空间换了
        Map<String, String> crystalClear = Map.of(
                "glass_encased_shaft", "glass_encased_shaft",
                "glass_encased_cog", "glass_encased_cog",
                "glass_encased_large_cog", "glass_encased_large_cog");

        Map<String, Map<String, String>> aliases = new LinkedHashMap<>();
        aliases.put("kubejs", kjs);
        aliases.put(CRYSTAL_CLEAR, crystalClear);
        aliases.put(CRYSTAL_CLEAR_LEGACY, crystalClear);
        return aliases;
    }

    private RegistryAliasEvents() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            return;
        }
        // addAlias 只在具体实现类上（IForgeRegistry 不暴露）；别名与元素泛型无关
        if (event.getForgeRegistry() instanceof ForgeRegistry<?> registry) {
            BLOCK_ENTITY_ALIASES.forEach((namespace, aliases) -> aliases.forEach((from, to) ->
                    registry.addAlias(
                            ResourceLocation.fromNamespaceAndPath(namespace, from),
                            ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, to))));
        }
    }
}

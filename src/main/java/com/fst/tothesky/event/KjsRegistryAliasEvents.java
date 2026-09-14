package com.fst.tothesky.event;

import com.fst.tothesky.ToTheSky;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegisterEvent;

import java.util.Map;

/**
 * 方块实体类型的 kjs 别名（kubejs: → tothesky:）。
 *
 * <p>为什么不能走 {@link MissingMappingsEvent}：方块实体类型注册表在 Forge 侧是
 * {@code disableSaving()} 的（{@code GameData} 建表时即如此），从不写进存档的
 * {@code level.dat → fml.Registries} 快照，因此永远不会产生“缺失映射”事件。
 * 而区块里的方块实体是以名字（{@code block_entities[].id}）解析的，旧存档写着
 * {@code kubejs:cooked_dumpling_plate} 时只有别名能让它落到
 * {@code tothesky:dumpling_plate} 上——否则该方块实体会解析失败、整盘馅料丢失。
 *
 * <p>{@link RegisterEvent} 在 Forge 注册表冻结之前触发（LOAD_REGISTRIES 状态里
 * 临时 unfreeze），是唯一能 {@code addAlias} 的时机；冻结后调用会抛
 * {@code IllegalStateException}。别名会随注册表在存档加载时被
 * {@code ForgeRegistry.sync} 合并保留，对游戏后期建表的注册表同样有效。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KjsRegistryAliasEvents {
    /** kjs 方块实体注册名 → mod 注册名 */
    private static final Map<String, String> BLOCK_ENTITY_ALIASES = Map.of(
            "cooked_dumpling_plate", "dumpling_plate",
            "seller", "seller",
            "roller", "roller",
            "mechanical_chisel_table", "mechanical_chisel_table"
    );

    private static final String KJS = "kubejs";

    private KjsRegistryAliasEvents() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            return;
        }
        // addAlias 只在具体实现类上（IForgeRegistry 不暴露）；别名与元素泛型无关
        if (event.getForgeRegistry() instanceof ForgeRegistry<?> registry) {
            BLOCK_ENTITY_ALIASES.forEach((from, to) -> registry.addAlias(
                    ResourceLocation.fromNamespaceAndPath(KJS, from),
                    ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, to)));
        }
    }
}

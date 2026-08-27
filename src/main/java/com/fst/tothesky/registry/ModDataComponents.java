package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.dumpling.ItemStackSnapshot;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ToTheSky.MODID);

    /** 饺子的馅料（单个物品栈快照，数量恒为 1） */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStackSnapshot>> DUMPLING_FILLING =
            DATA_COMPONENTS.register("dumpling_filling", () -> DataComponentType.<ItemStackSnapshot>builder()
                    .persistent(ItemStackSnapshot.CODEC)
                    .networkSynchronized(ItemStackSnapshot.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    /** 包制中的馅料快照（use 时从另一手拆入，完成/中止时移除） */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStackSnapshot>> DUMPLING_WRAPPING =
            DATA_COMPONENTS.register("dumpling_wrapping", () -> DataComponentType.<ItemStackSnapshot>builder()
                    .persistent(ItemStackSnapshot.CODEC)
                    .networkSynchronized(ItemStackSnapshot.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    /** 饺子的厨师名 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DUMPLING_AUTHOR =
            DATA_COMPONENTS.register("dumpling_author", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .cacheEncoding()
                    .build());

    /** 一盘饺子的内容物 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DumplingPlateContents>> DUMPLING_PLATE =
            DATA_COMPONENTS.register("dumpling_plate", () -> DataComponentType.<DumplingPlateContents>builder()
                    .persistent(DumplingPlateContents.CODEC)
                    .networkSynchronized(DumplingPlateContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private ModDataComponents() {
    }
}

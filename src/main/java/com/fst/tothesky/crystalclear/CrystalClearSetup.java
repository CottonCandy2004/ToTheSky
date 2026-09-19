package com.fst.tothesky.crystalclear;

import com.fst.tothesky.ToTheSky;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.encasing.EncasableBlock;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.decoration.encasing.EncasingRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;

/**
 * 两端都要做的接线：把玻璃包裹方块注册成 Create 传动杆/齿轮的“机壳变体”。
 * <p>
 * 注册进去之后，对传动杆/齿轮使用玻璃机壳（{@code EncasableBlock}）就能得到玻璃包裹方块，
 * 与上游行为一致。时机放在 {@link FMLCommonSetupEvent}：此时方块注册表已填满，
 * 而 {@code EncasingRegistry} 只是一张静态表，注册早晚都不影响存档。
 */
@Mod.EventBusSubscriber(modid = ToTheSky.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CrystalClearSetup {

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            addVariants(CrystalClearBlocks.GLASS_ENCASED_SHAFTS, AllBlocks.SHAFT.get());
            addVariants(CrystalClearBlocks.CLEAR_GLASS_ENCASED_SHAFTS, AllBlocks.SHAFT.get());
            addVariants(CrystalClearBlocks.GLASS_ENCASED_COGWHEELS, AllBlocks.COGWHEEL.get());
            addVariants(CrystalClearBlocks.CLEAR_GLASS_ENCASED_COGWHEELS, AllBlocks.COGWHEEL.get());
            addVariants(CrystalClearBlocks.GLASS_ENCASED_LARGE_COGWHEELS, AllBlocks.LARGE_COGWHEEL.get());
            addVariants(CrystalClearBlocks.CLEAR_GLASS_ENCASED_LARGE_COGWHEELS, AllBlocks.LARGE_COGWHEEL.get());
        });
    }

    private static <B extends Block & EncasableBlock> void addVariants(Map<String, ? extends RegistryObject<? extends Block>> blocks,
                                                                      B encasable) {
        for (RegistryObject<? extends Block> block : blocks.values()) {
            // 传入顺序是 (被装壳的方块, 装壳后的方块)，见 EncasingRegistry#addVariant 的泛型约束
            EncasingRegistry.addVariant(encasable, (Block & EncasedBlock) block.get());
        }
    }

    private CrystalClearSetup() {
    }
}

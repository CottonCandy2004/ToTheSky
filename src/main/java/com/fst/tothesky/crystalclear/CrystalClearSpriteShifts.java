package com.fst.tothesky.crystalclear;

import com.fst.tothesky.ToTheSky;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.CTType;
import net.minecraft.resources.ResourceLocation;

/**
 * 连接纹理位移表（移植自 Create: Crystal Clear 的 {@code CCSpriteShifts}）。
 * <p>
 * 贴图约定与上游一致：{@code block/<名字>.png} 是单块贴图，
 * {@code block/<名字>_connected.png} 是连接后的贴图。位移表本体由 Create 按
 * (类型, 单块贴图, 连接贴图) 三元组缓存，因此这里可以按需反复取。
 */
public final class CrystalClearSpriteShifts {

    /** 玻璃机壳（{@code <casing>[_clear]_glass_casing}）的全向连接位移 */
    public static CTSpriteShiftEntry glassCasing(String casing, boolean clear) {
        return omni(casing + (clear ? "_clear" : "") + "_glass_casing");
    }

    /** 包裹齿轮侧面的竖直/水平连接位移（大齿轮只有竖直方向，与原 mod 一致） */
    public static CTSpriteShiftEntry cogwheelSide(String casing) {
        return vertical("encased_cogwheels/" + casing + "_encased_cogwheel_side");
    }

    public static CTSpriteShiftEntry omni(String name) {
        return getCT(AllCTTypes.OMNIDIRECTIONAL, name);
    }

    public static CTSpriteShiftEntry horizontal(String name) {
        return getCT(AllCTTypes.HORIZONTAL, name);
    }

    public static CTSpriteShiftEntry vertical(String name) {
        return getCT(AllCTTypes.VERTICAL, name);
    }

    private static CTSpriteShiftEntry getCT(CTType type, String name) {
        return CTSpriteShifter.getCT(type,
                ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, "block/" + name),
                ResourceLocation.fromNamespaceAndPath(ToTheSky.MODID, "block/" + name + "_connected"));
    }

    private CrystalClearSpriteShifts() {
    }
}

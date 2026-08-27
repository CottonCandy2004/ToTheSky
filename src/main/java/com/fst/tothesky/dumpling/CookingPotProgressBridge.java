package com.fst.tothesky.dumpling;

import net.minecraft.world.inventory.ContainerData;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

import java.lang.reflect.Field;

/**
 * 把本模组饺子烹饪会话的进度写进农夫乐事厨锅，驱动其 GUI 进度箭头
 * （cookTime 在 data[0]、cookTimeTotal 在 data[1]，见 FD createIntArray）。
 *
 * <p>FD 未提供公开的煮制进度 setter。原本想用"跨包同包访问 protected 字段"，但 NeoForge
 * 模块化启动器禁止 split package（tothesky 与 farmersdelight 同时导出 FD 包 → ResolutionException），
 * 只能反射。FD 是无 module-info 的 automatic module（包全量 opens），setAccessible 可用。
 * 字段按类型扫描（ContainerData）而非硬编码名字，改名不易静默失效。</p>
 */
public final class CookingPotProgressBridge {
    private static final Field COOKING_POT_DATA;

    static {
        Field found = null;
        for (Field field : CookingPotBlockEntity.class.getDeclaredFields()) {
            if (ContainerData.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                found = field;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("CookingPotBlockEntity 找不到 ContainerData 字段");
        }
        COOKING_POT_DATA = found;
    }

    private CookingPotProgressBridge() {
    }

    public static void setProgress(CookingPotBlockEntity pot, int elapsed, int total) {
        try {
            ContainerData data = (ContainerData) COOKING_POT_DATA.get(pot);
            if (data != null && data.getCount() >= 2) {
                data.set(0, elapsed);
                data.set(1, total);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("访问 FD 厨锅 cookingPotData 失败", e);
        }
    }
}

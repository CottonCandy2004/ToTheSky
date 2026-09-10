package com.fst.tothesky.cocktail;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * kitchenkarrot 鸡尾酒物品的读写辅助（1.20.1 NBT 版）。
 *
 * kk 1.20.1 的鸡尾酒身份是物品 NBT 里的 "cocktail" 字符串（非注册表对象）。
 * 本类不引用 kk 类，避免 runClient 在无 kk 时加载其 SRG mixin。
 */
public final class CocktailHelper {
    private static final ResourceLocation KK_COCKTAIL = ResourceLocation.fromNamespaceAndPath("kitchenkarrot", "cocktail");
    private static final ResourceLocation UNKNOWN_COCKTAIL = ResourceLocation.fromNamespaceAndPath("kitchenkarrot", "unknown");
    private static final String NBT_COCKTAIL = "cocktail";

    private CocktailHelper() {
    }

    /** 读取物品栈上的鸡尾酒 id，不是鸡尾酒或无法识别时返回 null */
    @Nullable
    public static ResourceLocation cocktailId(ItemStack stack) {
        if (!isCocktailItem(stack)) {
            return null;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_COCKTAIL)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(NBT_COCKTAIL));
        if (id == null || UNKNOWN_COCKTAIL.equals(id)) {
            return null;
        }
        return id;
    }

    /** 创建一份指定 id 的鸡尾酒；id 无效或 kk 未安装时返回 EMPTY */
    public static ItemStack createCocktail(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(KK_COCKTAIL);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(NBT_COCKTAIL, id.toString());
        return stack;
    }

    private static boolean isCocktailItem(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return KK_COCKTAIL.equals(key);
    }
}

package com.fst.tothesky.cocktail;

import com.fst.tothesky.registry.ModNbt;
import io.github.tt432.kitchenkarrot.item.CocktailItem;
import io.github.tt432.kitchenkarrot.registries.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * kitchenkarrot 鸡尾酒物品的读写辅助（1.20.1 NBT 版）。
 *
 * kk 1.20.1 的鸡尾酒身份是物品 NBT 里的 "cocktail" 字符串（非注册表对象），
 * 效果存于 kitchenkarrot:cocktail 配方 JSON 的 content.effect，由 CocktailItem 自行应用。
 */
public final class CocktailHelper {
    /** fstwines 命名空间 */
    private static final String NS_FSTWINES = "fstwines";
    /** kitchenkarrot 命名空间 */
    private static final String NS_KK = "kitchenkarrot";

    private CocktailHelper() {
    }

    /** 读取物品栈上的鸡尾酒 id，不是鸡尾酒或无法识别时返回 null */
    @Nullable
    public static ResourceLocation cocktailId(ItemStack stack) {
        if (!(stack.getItem() instanceof CocktailItem)) {
            return null;
        }
        ResourceLocation id = CocktailItem.getCocktail(stack);
        if (id == null || CocktailItem.UNKNOWN_COCKTAIL.equals(id)) {
            return null;
        }
        return id;
    }

    /** 创建一份指定 id 的鸡尾酒；id 无效时返回 EMPTY */
    public static ItemStack createCocktail(ResourceLocation id) {
        ItemStack stack = new ItemStack(ModItems.COCKTAIL.get());
        CocktailItem.setCocktail(stack, id);
        return stack;
    }
}
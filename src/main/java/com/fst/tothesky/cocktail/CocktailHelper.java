package com.fst.tothesky.cocktail;

import io.github.tt432.kitchenkarrot.cocktail.CocktailProperty;
import io.github.tt432.kitchenkarrot.item.CocktailItem;
import io.github.tt432.kitchenkarrot.registries.ModCocktails;
import io.github.tt432.kitchenkarrot.registries.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** kitchenkarrot 鸡尾酒物品的读写辅助 */
public final class CocktailHelper {
    private CocktailHelper() {
    }

    /** 读取物品栈上的鸡尾酒 id，不是鸡尾酒或无法识别时返回 null */
    @Nullable
    public static ResourceLocation cocktailId(ItemStack stack) {
        if (!(stack.getItem() instanceof CocktailItem)) {
            return null;
        }
        CocktailProperty property = CocktailItem.getCocktail(stack);
        if (property == null || CocktailItem.UNKNOWN_COCKTAIL.equals(property.id())) {
            return null;
        }
        return property.id();
    }

    /** 创建一份指定 id 的鸡尾酒；id 无效时返回 EMPTY */
    public static ItemStack createCocktail(ResourceLocation id) {
        CocktailProperty property = ModCocktails.COCKTAILS_REGISTRY.get(id);
        if (property == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.COCKTAIL.get());
        CocktailItem.setCocktail(stack, property);
        return stack;
    }
}

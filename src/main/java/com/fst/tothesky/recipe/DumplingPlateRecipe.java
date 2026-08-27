package com.fst.tothesky.recipe;

import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.dumpling.ItemStackSnapshot;
import com.fst.tothesky.registry.ModDataComponents;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 8 个生饺子 + 1 个碗 → 一盘生饺子。
 * 自定义配方：聚合每个饺子的馅料与厨师名写入成品组件。
 * 没有馅料的生饺子不允许参与合成（防止产出无馅的盘子）。
 */
public class DumplingPlateRecipe extends CustomRecipe {
    public DumplingPlateRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !collect(input).isEmpty();
    }

    /** 收集网格中的生饺子；数量不为 8、混入其他物品或有无馅饺子时返回空表 */
    private static List<ItemStack> collect(CraftingInput input) {
        List<ItemStack> dumplings = new ArrayList<>();
        int dumplingCount = 0;
        int bowlCount = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.RAW_DUMPLING.get())) {
                ItemStack filling = ItemStackSnapshot.unwrap(stack.get(ModDataComponents.DUMPLING_FILLING));
                if (filling.isEmpty()) {
                    return List.of();
                }
                dumplingCount += stack.getCount();
                for (int n = 0; n < stack.getCount(); n++) {
                    dumplings.add(stack);
                }
            } else if (stack.is(Items.BOWL)) {
                bowlCount += stack.getCount();
            } else {
                return List.of();
            }
        }
        return dumplingCount == DumplingPlateContents.SIZE && bowlCount >= 1 ? dumplings : List.of();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<ItemStack> dumplings = collect(input);
        if (dumplings.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack plate = new ItemStack(ModItems.RAW_DUMPLING_PLATE.get());
        plate.set(ModDataComponents.DUMPLING_PLATE,
                com.fst.tothesky.dumpling.DumplingFactory.aggregate(dumplings));
        return plate;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= DumplingPlateContents.SIZE + 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DUMPLING_PLATE.get();
    }
}

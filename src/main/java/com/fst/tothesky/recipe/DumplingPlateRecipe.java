package com.fst.tothesky.recipe;

import com.fst.tothesky.dumpling.DumplingFactory;
import com.fst.tothesky.dumpling.DumplingPlateContents;
import com.fst.tothesky.registry.ModItems;
import com.fst.tothesky.registry.ModNbt;
import com.fst.tothesky.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 8 个生饺子 + 1 个碗 → 一盘生饺子。
 * 自定义配方：聚合每个饺子的馅料与厨师名写入成品组件。
 * 没有馅料的生饺子不允许参与合成（防止产出无馅的盘子）。
 * 按槽位计数：每格生饺子无论堆叠多少都占一个"饺子位"，原版合成每槽消耗 1 个。
 *
 * 1.20.1 适配：CraftingContainer + RegistryAccess（1.21 是 CraftingInput + HolderLookup.Provider）。
 */
public class DumplingPlateRecipe extends CustomRecipe {
    public DumplingPlateRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !collect(input).isEmpty();
    }

    /** 收集网格中的生饺子（每槽一份）；不足 8 槽、混入其他物品或有无馅饺子时返回空表 */
    private static List<ItemStack> collect(CraftingContainer input) {
        List<ItemStack> dumplings = new ArrayList<>();
        int bowlCount = 0;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.RAW_DUMPLING.get())) {
                ItemStack filling = ModNbt.getFilling(stack);
                if (filling.isEmpty()) {
                    return List.of();
                }
                dumplings.add(stack); // 每槽一份，堆叠数量不影响匹配
            } else if (stack.is(Items.BOWL)) {
                bowlCount += stack.getCount();
            } else {
                return List.of();
            }
        }
        return dumplings.size() == DumplingPlateContents.SIZE && bowlCount >= 1 ? dumplings : List.of();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        List<ItemStack> dumplings = collect(input);
        if (dumplings.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack plate = new ItemStack(ModItems.RAW_DUMPLING_PLATE.get());
        plate.getOrCreateTag().put(ModNbt.DUMPLING_PLATE, DumplingFactory.aggregate(dumplings).save());
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
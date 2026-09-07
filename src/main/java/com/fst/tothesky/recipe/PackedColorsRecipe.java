package com.fst.tothesky.recipe;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.item.PackedColorsItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 包装颜料（PR#59）：宣纸 + 1~4 个染料 → 记录染料列表的包装颜料。
 * kjs 的 modifyResult 回调在此实现为 matches+assemble。
 */
public class PackedColorsRecipe extends CustomRecipe {
    private static final ResourceLocation XUAN_PAPER = new ResourceLocation("ultramarine", "xuan_paper");
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> CHISEL_DYE =
            net.minecraft.tags.ItemTags.create(new ResourceLocation("ultramarine", "chisel_dye"));

    public PackedColorsRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int papers = 0;
        int dyes = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(XUAN_PAPER))) {
                papers++;
            } else if (stack.is(CHISEL_DYE)) {
                dyes++;
            } else {
                return false;
            }
        }
        return papers == 1 && dyes >= 1 && dyes <= 4;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack result = new ItemStack(com.fst.tothesky.registry.ModItems.PACKED_COLORS.get());
        CompoundTag nbt = result.getOrCreateTag();
        ListTag colors = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null && !id.equals(XUAN_PAPER)) {
                colors.add(StringTag.valueOf(id.toString()));
            }
        }
        nbt.put(PackedColorsItem.TAG_COLORS, colors);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    public static final net.minecraftforge.registries.RegistryObject<RecipeSerializer<PackedColorsRecipe>> SERIALIZER =
            com.fst.tothesky.registry.ModRecipes.RECIPE_SERIALIZERS
                    .register("packed_colors", () -> new SimpleCraftingRecipeSerializer<>(PackedColorsRecipe::new));
}
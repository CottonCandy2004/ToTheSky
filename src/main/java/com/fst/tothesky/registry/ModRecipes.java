package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.recipe.PackedColorsRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ToTheSky.MODID);
    /** 包装颜料（宣纸 + 1~4 凿刻染料）自定义合成 */
    public static final RegistryObject<RecipeSerializer<PackedColorsRecipe>> PACKED_COLORS =
            RECIPE_SERIALIZERS.register("packed_colors",
                    () -> new SimpleCraftingRecipeSerializer<>(PackedColorsRecipe::new));

    private ModRecipes() {
    }
}

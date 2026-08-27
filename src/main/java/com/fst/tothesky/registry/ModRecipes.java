package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.recipe.DumplingPlateRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ToTheSky.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DumplingPlateRecipe>> DUMPLING_PLATE =
            RECIPE_SERIALIZERS.register("dumpling_plate",
                    () -> new SimpleCraftingRecipeSerializer<>(DumplingPlateRecipe::new));

    private ModRecipes() {
    }
}

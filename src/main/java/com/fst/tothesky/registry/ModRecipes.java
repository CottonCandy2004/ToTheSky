package com.fst.tothesky.registry;

import com.fst.tothesky.ToTheSky;
import com.fst.tothesky.recipe.DumplingPlateRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ToTheSky.MODID);

    public static final RegistryObject<RecipeSerializer<DumplingPlateRecipe>> DUMPLING_PLATE =
            RECIPE_SERIALIZERS.register("dumpling_plate",
                    () -> new SimpleCraftingRecipeSerializer<>(DumplingPlateRecipe::new));

    private ModRecipes() {
    }
}
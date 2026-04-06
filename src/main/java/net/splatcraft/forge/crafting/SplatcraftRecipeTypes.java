package net.splatcraft.forge.crafting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.splatcraft.forge.Splatcraft;

public class SplatcraftRecipeTypes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Splatcraft.MODID);

    public static final RegistryObject<RecipeSerializer<InkVatColorRecipe>> INK_VAT_COLOR_CRAFTING = REGISTRY.register("ink_vat_color", InkVatColorRecipe.InkVatColorSerializer::new);
    public static final RegistryObject<RecipeSerializer<WeaponWorkbenchTab>> WEAPON_STATION_TAB = REGISTRY.register("weapon_workbench_tab", WeaponWorkbenchTab.WeaponWorkbenchTabSerializer::new);
    public static final RegistryObject<RecipeSerializer<WeaponWorkbenchRecipe>> WEAPON_STATION = REGISTRY.register("weapon_workbench", WeaponWorkbenchRecipe.Serializer::new);
    public static final RegistryObject<RecipeSerializer<SingleUseSubRecipe>> SINGLE_USE_SUB = REGISTRY.register("single_use_sub", () -> new SimpleCraftingRecipeSerializer<>(SingleUseSubRecipe::new));
    public static final RegistryObject<RecipeSerializer<ShapedRecipe>> COLORED_SHAPED_CRAFTING = REGISTRY.register("colored_crafting_shaped", ColoredShapedRecipe.Serializer::new);

    public static final RecipeType<AbstractWeaponWorkbenchRecipe> WEAPON_STATION_TYPE = registerType("weapon_workbench");
    public static final RecipeType<WeaponWorkbenchTab> WEAPON_STATION_TAB_TYPE = registerType("weapon_workbench_tab");
    public static final RecipeType<InkVatColorRecipe> INK_VAT_COLOR_CRAFTING_TYPE = registerType("ink_vat_color");

    private static <T extends Recipe<?>> RecipeType<T> registerType(String id) {
        return new RecipeType<>() {
            @Override
            public String toString() {
                return Splatcraft.MODID + ":" + id;
            }
        };
    }

    public static boolean getItem(Player player, Ingredient ingredient, int count, boolean takeItems) {
        for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
            ItemStack invStack = player.getInventory().getItem(i);
            if (!takeItems) {
                invStack = invStack.copy();
            }

            if (ingredient.test(invStack)) {
                if (count > invStack.getCount()) {
                    count -= invStack.getCount();
                    invStack.setCount(0);
                } else {
                    invStack.setCount(invStack.getCount() - count);
                    return true;
                }
            }
        }
        return false;
    }
}

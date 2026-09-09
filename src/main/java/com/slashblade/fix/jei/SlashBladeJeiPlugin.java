package com.slashblade.fix.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipe;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import com.slashblade.fix.SlashBladeResharpedRecipeFix;

import java.util.ArrayList;
import java.util.List;

/**
 * 拔刀剑 JEI 插件 (1.21.1 NeoForge)
 */
@JeiPlugin
public class SlashBladeJeiPlugin implements IModPlugin {

    public static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(SlashBladeResharpedRecipeFix.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        Item defaultBlade = SlashBladeItems.SLASHBLADE.get();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof ItemSlashBlade && item != defaultBlade) {
                try {
                    registration.registerSubtypeInterpreter(item, mods.flammpfeil.slashblade.compat.jei.SlashBladeSubtypeInterpreter.INSTANCE);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addExtension(
                SlashBladeShapedRecipe.class,
                SlashBladeCraftingCategoryExtension.INSTANCE
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return;
        }

        try {
            var registry = SlashBlade.getSlashBladeDefinitionRegistry(mc.getConnection().registryAccess());
            List<ItemStack> bladesToAdd = new ArrayList<>();
            registry.listElements().sorted(SlashBladeDefinition.COMPARATOR).forEach(holder -> {
                SlashBladeDefinition definition = holder.value();
                try {
                    ItemStack blade = definition.getBlade(mc.getConnection().registryAccess());
                    if (!blade.isEmpty()) {
                        bladesToAdd.add(blade);
                    }
                } catch (Exception ignored) {
                }
            });

            if (!bladesToAdd.isEmpty()) {
                jeiRuntime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, bladesToAdd);
            }
        } catch (Exception ignored) {
        }
    }
}

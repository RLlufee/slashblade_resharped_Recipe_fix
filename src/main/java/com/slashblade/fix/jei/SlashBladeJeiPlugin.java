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
                    registration.registerSubtypeInterpreter(item, EnhancedSlashBladeSubtypeInterpreter.INSTANCE);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 拔刀剑通用子类型解释器：直接使用 translationKey 进行唯一标识，
     * 保证与原版 SlashBladeSubtypeInterpreter 完全一致，避免因封刀/妖刀状态导致配方材料无法匹配。
     */
    public static class EnhancedSlashBladeSubtypeInterpreter implements mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter<ItemStack> {
        public static final EnhancedSlashBladeSubtypeInterpreter INSTANCE = new EnhancedSlashBladeSubtypeInterpreter();

        @Override
        public Object getSubtypeData(ItemStack ingredient, mezz.jei.api.ingredients.subtypes.UidContext context) {
            return BladeStateAccess.of(ingredient).map(state -> {
                String key = state.getTranslationKey();
                if (key == null || key.isEmpty()) {
                    return "";
                }
                return key;
            }).orElse("");
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack ingredient, mezz.jei.api.ingredients.subtypes.UidContext context) {
            Object data = getSubtypeData(ingredient, context);
            return data != null ? data.toString() : "";
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

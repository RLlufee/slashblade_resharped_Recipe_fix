package com.slashblade.fix.jei;

import com.slashblade.fix.SlashBladeResharpedFix;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipe;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
@SuppressWarnings("removal")
public class SlashBladeJeiPlugin implements IModPlugin {

    public static final ResourceLocation PLUGIN_UID = new ResourceLocation(SlashBladeResharpedFix.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof ItemSlashBlade && item != mods.flammpfeil.slashblade.registry.SlashBladeItems.SLASHBLADE.get()) {
                if (hasInterpreter(registration, item)) {
                    continue;
                }
                try {
                    registration.registerSubtypeInterpreter(item, (stack, context) -> {
                        stack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(cap -> {
                            if (stack.hasTag() && stack.getOrCreateTag().contains("bladeState")) {
                                cap.deserializeNBT(stack.getOrCreateTag().getCompound("bladeState"));
                            }
                        });
                        return stack.getCapability(ItemSlashBlade.BLADESTATE)
                                .map(ISlashBladeState::getTranslationKey)
                                .orElse("");
                    });
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean hasInterpreter(ISubtypeRegistration registration, Item item) {
        try {
            if (registration instanceof mezz.jei.library.load.registration.SubtypeRegistration internalReg) {
                java.lang.reflect.Field mapField = internalReg.getInterpreters().getClass().getDeclaredField("map");
                mapField.setAccessible(true);
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) mapField.get(internalReg.getInterpreters());
                return map != null && map.containsKey(item);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }


    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addCategoryExtension(
                SlashBladeShapedRecipe.class,
                SlashBladeCraftingCategoryExtension::new
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
                    ItemStack blade = definition.getBlade();
                    if (!blade.isEmpty()) {
                        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(cap -> {
                            if (blade.hasTag() && blade.getOrCreateTag().contains("bladeState")) {
                                cap.deserializeNBT(blade.getOrCreateTag().getCompound("bladeState"));
                            }
                        });
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

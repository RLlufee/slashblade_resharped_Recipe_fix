package com.slashblade.fix.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.recipe.RequestDefinition;
import mods.flammpfeil.slashblade.recipe.SlashBladeIngredient;
import mods.flammpfeil.slashblade.recipe.SlashBladeShapedRecipe;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import mods.flammpfeil.slashblade.registry.slashblade.EnchantmentDefinition;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SlashBladeCraftingCategoryExtension implements ICraftingCategoryExtension {

    private final SlashBladeShapedRecipe recipe;
    private static Field requestField = null;

    static {
        try {
            requestField = SlashBladeIngredient.class.getDeclaredField("request");
            requestField.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    public SlashBladeCraftingCategoryExtension(SlashBladeShapedRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        ItemStack outputStack = getOutputStack();
        craftingGridHelper.createAndSetOutputs(builder, List.of(outputStack));

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            builder.setShapeless();
            width = height = (int) Math.ceil(Math.sqrt(recipe.getIngredients().size()));
        }

        List<Ingredient> ingredients = recipe.getIngredients();
        List<List<ItemStack>> inputItemsList = new ArrayList<>();
        List<RequestDefinition> requests = new ArrayList<>();

        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                inputItemsList.add(Collections.emptyList());
                requests.add(null);
                continue;
            }

            if (ingredient instanceof SlashBladeIngredient bladeIngredient) {
                RequestDefinition req = getRequest(bladeIngredient);
                requests.add(req);
                inputItemsList.add(getBladeDisplayStacks(bladeIngredient, req));
            } else {
                requests.add(null);
                ItemStack[] items = ingredient.getItems();
                if (items.length > 0) {
                    inputItemsList.add(Arrays.asList(items));
                } else {
                    inputItemsList.add(Collections.emptyList());
                }
            }
        }

        List<IRecipeSlotBuilder> slotBuilders = craftingGridHelper.createAndSetInputs(builder, inputItemsList, width, height);

        if (slotBuilders != null) {
            for (int i = 0; i < ingredients.size(); i++) {
                RequestDefinition req = requests.get(i);
                if (req != null) {
                    int slotIndex = getCraftingIndex(i, width, height);
                    if (slotIndex >= 0 && slotIndex < slotBuilders.size()) {
                        IRecipeSlotBuilder slot = slotBuilders.get(slotIndex);
                        attachRequestTooltip(slot, req);
                    }
                }
            }
        }
    }

    public ItemStack getOutputStack() {
        ResourceLocation outputBlade = recipe.getOutputBlade();
        if (outputBlade == null) {
            return recipe.getResultItem(RegistryAccess.EMPTY);
        }

        if (ForgeRegistries.ITEMS.containsKey(outputBlade)) {
            Item item = ForgeRegistries.ITEMS.getValue(outputBlade);
            if (item != null) {
                return new ItemStack(item);
            }
        }

        ItemStack resolved = getBladeFromRegistry(outputBlade);
        if (!resolved.isEmpty()) {
            return resolved;
        }

        return createFallbackBlade(outputBlade);
    }

    private ItemStack getBladeFromRegistry(ResourceLocation bladeName) {
        if (bladeName == null || bladeName.equals(SlashBlade.prefix("none"))) {
            return ItemStack.EMPTY;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            RegistryAccess access = null;
            if (mc.level != null) {
                access = mc.level.registryAccess();
            } else if (mc.getConnection() != null) {
                access = mc.getConnection().registryAccess();
            }
            if (access != null) {
                var reg = access.registry(SlashBladeDefinition.REGISTRY_KEY);
                if (reg.isPresent()) {
                    SlashBladeDefinition def = reg.get().get(bladeName);
                    if (def != null) {
                        ItemStack blade = def.getBlade();
                        if (!blade.isEmpty()) {
                            syncCapFromNbt(blade);
                            return blade;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack createFallbackBlade(ResourceLocation bladeName) {
        ItemStack blade = new ItemStack(SlashBladeItems.SLASHBLADE.get());
        String translationKey = Util.makeDescriptionId("item", bladeName);
        CompoundTag tag = blade.getOrCreateTagElement("bladeState");
        tag.putString("translationKey", translationKey);
        tag.putBoolean("isNonEmpty", true);
        syncCapFromNbt(blade);
        return blade;
    }

    private static void syncCapFromNbt(ItemStack blade) {
        blade.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(cap -> {
            if (blade.hasTag() && blade.getOrCreateTag().contains("bladeState")) {
                cap.deserializeNBT(blade.getOrCreateTag().getCompound("bladeState"));
            }
        });
    }

    @Nullable
    private RequestDefinition getRequest(SlashBladeIngredient ingredient) {
        if (requestField != null) {
            try {
                return (RequestDefinition) requestField.get(ingredient);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<ItemStack> getBladeDisplayStacks(SlashBladeIngredient ingredient, @Nullable RequestDefinition req) {
        List<ItemStack> list = new ArrayList<>();
        if (req != null && req.name() != null && !req.name().equals(SlashBlade.prefix("none"))) {
            ItemStack stack = getBladeFromRegistry(req.name());
            if (stack.isEmpty()) {
                stack = createFallbackBlade(req.name());
            }
            req.initItemStack(stack);
            syncCapFromNbt(stack);
            list.add(stack);
        }

        if (list.isEmpty()) {
            ItemStack[] items = ingredient.getItems();
            if (items.length > 0) {
                for (ItemStack item : items) {
                    ItemStack copy = item.copy();
                    syncCapFromNbt(copy);
                    list.add(copy);
                }
            } else {
                list.add(new ItemStack(SlashBladeItems.SLASHBLADE.get()));
            }
        }
        return list;
    }

    private void attachRequestTooltip(IRecipeSlotBuilder slot, RequestDefinition request) {
        slot.addRichTooltipCallback((recipeSlotView, tooltip) -> {
            if (request.killCount() > 0) {
                tooltip.add(Component.translatable("slashblade_resharped_fix.jei.req_kill", request.killCount())
                        .withStyle(ChatFormatting.RED));
            }
            if (request.proudSoulCount() > 0) {
                tooltip.add(Component.translatable("slashblade_resharped_fix.jei.req_proud_soul", request.proudSoulCount())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (request.refineCount() > 0) {
                tooltip.add(Component.translatable("slashblade_resharped_fix.jei.req_refine", request.refineCount())
                        .withStyle(ChatFormatting.AQUA));
            }
            for (EnchantmentDefinition enchDef : request.enchantments()) {
                Enchantment ench = ForgeRegistries.ENCHANTMENTS.getValue(enchDef.getEnchantmentID());
                if (ench != null) {
                    tooltip.add(Component.translatable("slashblade_resharped_fix.jei.req_enchantment", ench.getFullname(enchDef.getEnchantmentLevel()))
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            for (SwordType type : request.defaultType()) {
                tooltip.add(Component.translatable("slashblade_resharped_fix.jei.req_sword_type", type.name())
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    private static int getCraftingIndex(int i, int width, int height) {
        int index;
        if (width == 1) {
            if (height == 3) {
                index = (i * 3) + 1;
            } else if (height == 2) {
                index = (i * 3) + 1;
            } else {
                index = 4;
            }
        } else if (height == 1) {
            index = i + 3;
        } else if (width == 2) {
            index = i;
            if (i > 1) {
                index++;
                if (i > 3) {
                    index++;
                }
            }
        } else if (height == 2) {
            index = i + 3;
        } else {
            index = i;
        }
        return index;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }

    @Override
    public int getWidth() {
        return recipe.getWidth();
    }

    @Override
    public int getHeight() {
        return recipe.getHeight();
    }
}

package com.slashblade.fix.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 拔刀剑工作台配方 JEI 分类扩展 (1.21.1 NeoForge)
 */
public class SlashBladeCraftingCategoryExtension implements ICraftingCategoryExtension<SlashBladeShapedRecipe> {

    public static final SlashBladeCraftingCategoryExtension INSTANCE = new SlashBladeCraftingCategoryExtension();

    private static Field requestField = null;

    static {
        try {
            requestField = SlashBladeIngredient.class.getDeclaredField("request");
            requestField.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    private SlashBladeCraftingCategoryExtension() {
    }

    @Override
    public void setRecipe(RecipeHolder<SlashBladeShapedRecipe> recipeHolder, IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        SlashBladeShapedRecipe recipe = recipeHolder.value();
        ItemStack outputStack = getOutputStack(recipe);
        craftingGridHelper.createAndSetOutputs(builder, List.of(outputStack));

        int width = getWidth(recipeHolder);
        int height = getHeight(recipeHolder);
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

            SlashBladeIngredient bladeIngredient = extractSlashBladeIngredient(ingredient);
            if (bladeIngredient != null) {
                RequestDefinition req = getRequest(bladeIngredient);
                requests.add(req);
                inputItemsList.add(getBladeDisplayStacks(ingredient, bladeIngredient, req));
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

    public ItemStack getOutputStack(SlashBladeShapedRecipe recipe) {
        ResourceLocation outputBlade = recipe.getOutputBlade();

        // 1. 优先尝试从拔刀剑定义注册表中获取权威成品（无论物理上是基底刀还是独立刀物品）
        if (outputBlade != null && !outputBlade.equals(SlashBlade.prefix("none"))) {
            ItemStack resolved = getBladeFromRegistry(outputBlade);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }

        // 2. 尝试从配方自身的 getResultItem 获取（兼容派生配方等自主产物逻辑）
        try {
            Minecraft mc = Minecraft.getInstance();
            HolderLookup.Provider access = mc.level != null ? mc.level.registryAccess() :
                    (mc.getConnection() != null ? mc.getConnection().registryAccess() : null);
            if (access != null) {
                ItemStack result = recipe.getResultItem(access);
                if (!result.isEmpty()) {
                    ensureBladeStateInitialized(result, outputBlade);
                    return result;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. 权威定义与配方自主解析均未成功时的智能保底
        return outputBlade != null ? createFallbackBlade(outputBlade) : recipe.getResultStack().copy();
    }

    private ItemStack getBladeFromRegistry(ResourceLocation bladeName) {
        if (bladeName == null || bladeName.equals(SlashBlade.prefix("none"))) {
            return ItemStack.EMPTY;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            HolderLookup.Provider access = mc.level != null ? mc.level.registryAccess() :
                    (mc.getConnection() != null ? mc.getConnection().registryAccess() : null);
            if (access != null) {
                var reg = access.lookup(SlashBladeDefinition.REGISTRY_KEY);
                if (reg.isPresent()) {
                    var holder = reg.get().get(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, bladeName));
                    if (holder.isPresent()) {
                        ItemStack blade = holder.get().value().getBlade(access);
                        if (!blade.isEmpty()) {
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
        Item item = SlashBladeItems.SLASHBLADE.get();
        if (bladeName != null && BuiltInRegistries.ITEM.containsKey(bladeName)) {
            Item customItem = BuiltInRegistries.ITEM.get(bladeName);
            if (customItem != null && customItem != Items.AIR) {
                item = customItem;
            }
        }
        ItemStack blade = new ItemStack(item);
        if (bladeName != null) {
            BladeStateAccess.of(blade).ifPresent(state -> {
                state.setNonEmpty();
                state.setTranslationKey(Util.makeDescriptionId("item", bladeName));
            });
        }
        return blade;
    }

    private static void ensureBladeStateInitialized(ItemStack blade, @Nullable ResourceLocation bladeName) {
        if (blade.isEmpty() || !(blade.getItem() instanceof ItemSlashBlade)) {
            return;
        }
        boolean needsKey = BladeStateAccess.of(blade)
                .map(state -> {
                    String key = state.getTranslationKey();
                    return key == null || key.isEmpty() || "item.slashblade.namedblade".equals(key);
                })
                .orElse(true);

        if (needsKey) {
            ResourceLocation targetId = bladeName;
            if (targetId == null) {
                targetId = BuiltInRegistries.ITEM.getKey(blade.getItem());
            }
            if (targetId != null) {
                String translationKey = Util.makeDescriptionId("item", targetId);
                BladeStateAccess.of(blade).ifPresent(state -> {
                    state.setNonEmpty();
                    state.setTranslationKey(translationKey);
                });
            }
        }
    }

    @Nullable
    private SlashBladeIngredient extractSlashBladeIngredient(Ingredient ingredient) {
        try {
            var custom = ingredient.getCustomIngredient();
            if (custom instanceof SlashBladeIngredient sb) {
                return sb;
            }
        } catch (Throwable ignored) {
        }
        return null;
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

    private List<ItemStack> getBladeDisplayStacks(Ingredient original, SlashBladeIngredient ingredient, @Nullable RequestDefinition req) {
        List<ItemStack> list = new ArrayList<>();
        if (req != null && req.name() != null && !req.name().equals(SlashBlade.prefix("none"))) {
            ItemStack stack = getBladeFromRegistry(req.name());
            if (stack.isEmpty()) {
                stack = createFallbackBlade(req.name());
            }
            req.initItemStack(stack);
            list.add(stack);
        }

        if (list.isEmpty()) {
            ItemStack[] items = original.getItems();
            if (items.length > 0) {
                for (ItemStack item : items) {
                    ItemStack copy = item.copy();
                    if (copy.getItem() instanceof ItemSlashBlade) {
                        ensureBladeStateInitialized(copy, null);
                    }
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
                tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_kill", request.killCount())
                        .withStyle(ChatFormatting.RED));
            }
            if (request.proudSoulCount() > 0) {
                tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_proud_soul", request.proudSoulCount())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            if (request.refineCount() > 0) {
                tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_refine", request.refineCount())
                        .withStyle(ChatFormatting.AQUA));
            }
            for (EnchantmentDefinition enchDef : request.enchantments()) {
                try {
                    tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_enchantment",
                            Enchantment.getFullname(enchDef.getEnchantment(), enchDef.getEnchantmentLevel()))
                            .withStyle(ChatFormatting.YELLOW));
                } catch (Throwable t) {
                    tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_enchantment",
                            enchDef.getEnchantment().getRegisteredName() + " " + enchDef.getEnchantmentLevel())
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
            for (SwordType type : request.defaultType()) {
                tooltip.add(Component.translatable("slashblade_resharped_recipe_fix.jei.req_sword_type", getSwordTypeName(type))
                        .withStyle(ChatFormatting.GOLD));
            }
        });
    }

    private static Component getSwordTypeName(SwordType type) {
        return switch (type) {
            case BEWITCHED -> Component.translatable("slashblade.sword_type.bewitched");
            case ENCHANTED -> Component.translatable("slashblade.sword_type.enchanted");
            case SEALED -> Component.translatable("slashblade.sword_type.noname");
            default -> Component.literal(type.name());
        };
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

    @Override
    public int getWidth(RecipeHolder<SlashBladeShapedRecipe> recipeHolder) {
        return recipeHolder.value().getWidth();
    }

    @Override
    public int getHeight(RecipeHolder<SlashBladeShapedRecipe> recipeHolder) {
        return recipeHolder.value().getHeight();
    }
}

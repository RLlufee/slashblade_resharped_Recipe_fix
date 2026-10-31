package com.slashblade.fix.compat;

import com.slashblade.fix.SlashBladeResharpedRecipeFix;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.SlashBladeItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.Optional;

/**
 * 修复官方拔刀剑 1.21.1 针对 EMI 注入的 Comparison 存在缺陷导致混淆合成的兼容模块。
 * 避免在游戏内按下 U 键查找物品关系时官方比较器抛出异常导致被 EMI 全局禁用，
 * 并补全官方遗漏的 isBroken、isSealed、Model 与 Texture 多维度状态比对。
 */
public class EmiSlashBladeFix {

    private static boolean patched = false;
    private static Method getItemStackMethod = null;

    public static synchronized void init() {
        if (patched) {
            return;
        }

        if (!isEmiLoaded()) {
            SlashBladeResharpedRecipeFix.LOGGER.info("[EmiSlashBladeFix] EMI 未安装，无需应用 EMI 比较器修复补丁。");
            return;
        }

        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            getItemStackMethod = emiStackClass.getMethod("getItemStack");
            getItemStackMethod.setAccessible(true);

            Class<?> predicateClass = Class.forName("dev.emi.emi.api.stack.Comparison$Predicate");
            Class<?> comparisonClass = Class.forName("dev.emi.emi.api.stack.Comparison");

            // 动态代理实现 dev.emi.emi.api.stack.Comparison.Predicate 接口
            Object proxyPredicate = Proxy.newProxyInstance(
                    predicateClass.getClassLoader(),
                    new Class<?>[]{predicateClass},
                    (proxy, method, args) -> {
                        if ("test".equals(method.getName()) && args != null && args.length == 2) {
                            return safeCompare(args[0], args[1]);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("toString".equals(method.getName())) {
                            return "SlashBlade121SafeEmiComparisonPredicate";
                        }
                        return false;
                    }
            );

            Method ofMethod = comparisonClass.getMethod("of", predicateClass);
            Object safeComparison = ofMethod.invoke(null, proxyPredicate);

            // 替换官方拔刀剑的 EMIUtils.SLASHBLADE_COMPARISON 静态字段
            Class<?> emiUtilsClass = Class.forName("mods.flammpfeil.slashblade.compat.emi.EMIUtils");
            Field field = emiUtilsClass.getDeclaredField("SLASHBLADE_COMPARISON");
            field.setAccessible(true);
            field.set(null, safeComparison);

            patched = true;
            SlashBladeResharpedRecipeFix.LOGGER.info("[EmiSlashBladeFix] 成功修复并替换 1.21.1 EMIUtils.SLASHBLADE_COMPARISON 比较器，彻底杜绝 NPE 与比较器禁用导致的混淆合成！");
        } catch (Throwable t) {
            SlashBladeResharpedRecipeFix.LOGGER.warn("[EmiSlashBladeFix] 尝试修复 EMIUtils.SLASHBLADE_COMPARISON 时遇到异常: {}", t.getMessage());
        }
    }

    private static boolean isEmiLoaded() {
        try {
            if (ModList.get() != null && ModList.get().isLoaded("emi")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (LoadingModList.get() != null && LoadingModList.get().getModFileById("emi") != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 安全比对两个 EmiStack，包含严格的空安全检查与完整的名刀状态区分。
     */
    private static boolean safeCompare(Object aObj, Object bObj) {
        if (aObj == bObj) {
            return true;
        }
        if (aObj == null || bObj == null) {
            return false;
        }

        try {
            ItemStack stackA = getItemStackMethod != null ? (ItemStack) getItemStackMethod.invoke(aObj) : null;
            ItemStack stackB = getItemStackMethod != null ? (ItemStack) getItemStackMethod.invoke(bObj) : null;

            if (stackA == null && stackB == null) {
                return true;
            }
            if (stackA == null || stackB == null) {
                return false;
            }
            if (!ItemStack.isSameItem(stackA, stackB)) {
                return false;
            }
            if (!(stackA.getItem() instanceof ItemSlashBlade)) {
                return ItemStack.matches(stackA, stackB);
            }

            String keyA = getBladeKey(stackA);
            String keyB = getBladeKey(stackB);

            boolean hasKeyA = !keyA.isEmpty();
            boolean hasKeyB = !keyB.isEmpty();

            // 1. 如果双方都指定了名刀 translationKey（或独立注册刀的 descriptionId），严格按名刀标识比对
            // 不得过度比对 isBroken、isSealed、Model 或 Texture，避免妖刀（有附魔）与默认封刀模板无法匹配
            if (hasKeyA && hasKeyB) {
                return Objects.equals(keyA, keyB);
            }

            // 2. 如果两者均未指定名刀 key（如无特殊定义的默认白板刀），按原生 matches 比对
            if (!hasKeyA && !hasKeyB) {
                return ItemStack.matches(stackA, stackB);
            }

            // 3. 一方有名刀标识另一方无标识，不相等
            return false;
        } catch (Throwable t) {
            // 异常安全沙盒：绝不向外抛出异常，防止 EMI 触发 disabling 机制将比较器全局禁用
            return false;
        }
    }

    private static String getBladeKey(ItemStack stack) {
        Optional<ISlashBladeState> opt = BladeStateAccess.of(stack);
        if (opt.isPresent()) {
            String key = opt.get().getTranslationKey();
            if (key != null && !key.isEmpty() && !"item.slashblade.namedblade".equals(key)) {
                return key;
            }
        }
        if (stack.getItem() != SlashBladeItems.SLASHBLADE.get()) {
            return stack.getItem().getDescriptionId(stack);
        }
        return "";
    }
}

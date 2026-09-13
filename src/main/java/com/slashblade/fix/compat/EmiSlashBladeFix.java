package com.slashblade.fix.compat;

import com.slashblade.fix.SlashBladeResharpedFix;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;

/**
 * 修复官方拔刀剑针对 EMI 注入的 Comparison 存在未判空缺陷导致 NPE 的兼容模块。
 * 避免在游戏内按下 U 键查找物品关系时比较器抛出异常导致被 EMI 全局禁用，
 * 进而引发全量拔刀剑退化为按物品 ID 比对的混淆合成问题。
 */
public class EmiSlashBladeFix {

    private static boolean patched = false;
    private static Method getItemStackMethod = null;
    private static Method getNbtMethod = null;

    public static synchronized void init() {
        if (patched) {
            return;
        }

        if (!ModList.get().isLoaded("emi")) {
            SlashBladeResharpedFix.LOGGER.info("[EmiSlashBladeFix] EMI 未安装，无需应用 EMI 比较器修复补丁。");
            return;
        }

        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            getItemStackMethod = emiStackClass.getMethod("getItemStack");
            getItemStackMethod.setAccessible(true);
            getNbtMethod = emiStackClass.getMethod("getNbt");
            getNbtMethod.setAccessible(true);

            Class<?> predicateClass = Class.forName("dev.emi.emi.api.stack.Comparison$Predicate");
            Class<?> comparisonClass = Class.forName("dev.emi.emi.api.stack.Comparison");

            // 动态代理实现 Comparison.Predicate 接口
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
                            return "SlashBladeSafeEmiComparisonPredicate";
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
            SlashBladeResharpedFix.LOGGER.info("[EmiSlashBladeFix] 成功修复并替换 EMIUtils.SLASHBLADE_COMPARISON 比较器，彻底杜绝 NPE 与比较器禁用导致的混淆合成！");
        } catch (Throwable t) {
            SlashBladeResharpedFix.LOGGER.warn("[EmiSlashBladeFix] 尝试修复 EMIUtils.SLASHBLADE_COMPARISON 时遇到异常: {}", t.getMessage());
        }
    }

    /**
     * 安全比对两个 EmiStack。
     * 同时引入全量空安全判断，防止 NPE 导致比较器被 EMI 全局禁用。
     * 确保妖刀、封刀等各种形态的前置刀均能被 EMI 正确识别并正常参与合成。
     */
    private static boolean safeCompare(Object aObj, Object bObj) {
        if (aObj == bObj) {
            return true;
        }
        if (aObj == null || bObj == null) {
            return false;
        }

        try {
            ItemStack stackA = (ItemStack) getItemStackMethod.invoke(aObj);
            ItemStack stackB = (ItemStack) getItemStackMethod.invoke(bObj);

            if (stackA == null && stackB == null) {
                return true;
            }
            if (stackA == null || stackB == null) {
                return false;
            }
            if (stackA.getItem() != stackB.getItem()) {
                return false;
            }

            CompoundTag nbtA = (CompoundTag) getNbtMethod.invoke(aObj);
            CompoundTag nbtB = (CompoundTag) getNbtMethod.invoke(bObj);

            // 1. 如果双方都没有 NBT，视为同一种未命名刀
            if (nbtA == null && nbtB == null) {
                return true;
            }

            String keyA = getTranslationKey(stackA, nbtA);
            String keyB = getTranslationKey(stackB, nbtB);

            // 2. 如果双方都有 translationKey，按原版拔刀剑规则比对名刀唯一标识
            if (!keyA.isEmpty() && !keyB.isEmpty()) {
                return Objects.equals(keyA, keyB);
            }

            // 3. 若均无有效 key（白板刀），按原版 NBT 语义比对
            if (keyA.isEmpty() && keyB.isEmpty()) {
                if (nbtA == null || nbtB == null) {
                    return nbtA == nbtB;
                }
                return nbtA.equals(nbtB);
            }

            // 4. 一方有名刀 key 另一方无 key，不相等
            return false;
        } catch (Throwable t) {
            // 发生任何异常直接返回 false，绝不向上抛出导致 EMI 禁用比较器！
            return false;
        }
    }

    private static String getTranslationKey(ItemStack stack, CompoundTag nbt) {
        if (nbt != null && nbt.contains("bladeState")) {
            CompoundTag state = nbt.getCompound("bladeState");
            if (state.contains("translationKey")) {
                String key = state.getString("translationKey");
                if (key != null && !key.isEmpty()) {
                    return key;
                }
            }
        }
        if (stack != null && stack.getItem() instanceof mods.flammpfeil.slashblade.item.ItemSlashBlade) {
            return stack.getCapability(mods.flammpfeil.slashblade.item.ItemSlashBlade.BLADESTATE)
                    .map(mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState::getTranslationKey)
                    .orElse("");
        }
        return "";
    }
}

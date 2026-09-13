package com.slashblade.fix;

import com.mojang.logging.LogUtils;
import com.slashblade.fix.compat.EmiSlashBladeFix;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * 拔刀剑：重锋 JEI/EMI 配方与展示修复补丁 (1.21.1 NeoForge)
 */
@Mod(SlashBladeResharpedRecipeFix.MODID)
public class SlashBladeResharpedRecipeFix {
    public static final String MODID = "slashblade_resharped_recipe_fix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SlashBladeResharpedRecipeFix(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("SlashBlade Resharped Recipe Fix for Minecraft 1.21.1 NeoForge initialized!");

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(this::onClientSetup);
            // 早期预初始化，尽早替换 EMI 比较器
            EmiSlashBladeFix.init();
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(EmiSlashBladeFix::init);
    }
}

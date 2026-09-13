package com.slashblade.fix;

import com.slashblade.fix.compat.EmiSlashBladeFix;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SlashBladeResharpedFix.MODID)
public class SlashBladeResharpedFix {
    public static final String MODID = "slashblade_resharped_recipe_fix";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public SlashBladeResharpedFix(FMLJavaModLoadingContext context) {
        LOGGER.info("SlashBlade Resharped JEI Fix mod initialized.");

        // 早期初始化：在客户端加载阶段第一时间修补 EMI 比较器
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            EmiSlashBladeFix.init();
            context.getModEventBus().addListener(this::onClientSetup);
        });
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(EmiSlashBladeFix::init);
    }
}


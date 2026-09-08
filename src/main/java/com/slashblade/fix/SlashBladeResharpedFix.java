package com.slashblade.fix;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SlashBladeResharpedFix.MODID)
public class SlashBladeResharpedFix {
    public static final String MODID = "slashblade_resharped_fix";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public SlashBladeResharpedFix() {
        LOGGER.info("SlashBlade Resharped JEI Fix mod initialized.");
    }
}

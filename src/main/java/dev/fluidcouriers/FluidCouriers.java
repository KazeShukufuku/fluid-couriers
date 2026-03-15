package dev.fluidcouriers;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FluidCouriers.MOD_ID)
public class FluidCouriers {

    public static final String MOD_ID = "fluidcouriers";
    private static final Logger LOGGER = LogManager.getLogger();

    public FluidCouriers() {
        LOGGER.info("[FluidCouriers] Fluid Couriers compat loaded – FluidLogistics × PackageCouriers.");
    }
}

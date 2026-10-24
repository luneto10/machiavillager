package io.github.luneto10.machiavillager;

import io.github.luneto10.machiavillager.minecraft.DevCommands;
import io.github.luneto10.machiavillager.minecraft.ModEntities;
import io.github.luneto10.machiavillager.minecraft.ServerLifecycleLog;
import io.github.luneto10.machiavillager.minecraft.notice.Notices;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: runs on both the dedicated server and the client's integrated server. */
public class Machiavillager implements ModInitializer {
	public static final String MOD_ID = "machiavillager";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Machiavillager is plotting. Emerald prices will never recover.");
		ModEntities.register(); // first: commands below use the entity type
		        ServerLifecycleLog.register();
        Notices.register(); // payload types must exist on both ends before any player joins
		DevCommands.register();
	}
}

package io.github.luneto10.machiavillager.client;

import io.github.luneto10.machiavillager.Machiavillager;
import net.fabricmc.api.ClientModInitializer;

/** Client-only entrypoint: never loaded on a dedicated server. */
public class MachiavillagerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Machiavillager.LOGGER.info("Machiavillager client ready.");
	}
}

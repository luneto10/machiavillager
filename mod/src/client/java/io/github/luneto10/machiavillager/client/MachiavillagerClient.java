package io.github.luneto10.machiavillager.client;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.minecraft.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: never loaded on a dedicated server. */
public class MachiavillagerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.MACHIAVILLAGER, MachiavillagerRenderer::new);
		Machiavillager.LOGGER.info("Machiavillager client ready.");
	}
}

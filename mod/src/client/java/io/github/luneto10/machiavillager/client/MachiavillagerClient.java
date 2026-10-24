package io.github.luneto10.machiavillager.client;

import io.github.luneto10.machiavillager.Machiavillager;
import io.github.luneto10.machiavillager.client.notice.NoticeHud;
import io.github.luneto10.machiavillager.minecraft.ModEntities;
import io.github.luneto10.machiavillager.minecraft.notice.NoticePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** Client-only entrypoint: never loaded on a dedicated server. */
public class MachiavillagerClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(ModEntities.MACHIAVILLAGER, MachiavillagerRenderer::new);

		// One HUD card for all notices, drawn just under the boss bars.
		NoticeHud noticeHud = new NoticeHud();
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR, NoticeHud.ID, noticeHud);
		ClientPlayNetworking.registerGlobalReceiver(NoticePayload.TYPE, (payload, context) -> noticeHud.show(payload));
		Machiavillager.LOGGER.info("Machiavillager client ready.");
	}
}

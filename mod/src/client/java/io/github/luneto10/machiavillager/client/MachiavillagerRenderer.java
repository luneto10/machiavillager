package io.github.luneto10.machiavillager.client;

import io.github.luneto10.machiavillager.minecraft.npc.MachiavillagerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

/** Draws Machiavillager with the player model and, for now, the default Steve skin. */
public class MachiavillagerRenderer
        extends HumanoidMobRenderer<MachiavillagerEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

    private static final float SHADOW_RADIUS = 0.5f;

    public MachiavillagerRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), SHADOW_RADIUS);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return DefaultPlayerSkin.getDefaultTexture();
    }
}

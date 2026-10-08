package com.projecthero.mod.client.nova;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * v0.15.13: the Nova Corps uniform on every player who has it on (every viewer: the state is synced). See
 * {@link NovaSuitRender}.
 */
public class NovaSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public NovaSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		NovaSuitRender.Anim anim = NovaSuitRender.anim(player, partialTick);
		if (!anim.anything()) {
			return;
		}
		NovaSuitRender.render(pose, buffers, light, getParentModel(), anim, NovaSuitRender.overloaded(player));
		// v0.15.15 (user: "give Nova the body glow aswell similar to green lantern, make it his colours tho"): while flying,
		// a thin skin of gold light over the suit (the uniform is always wide-armed; the shells clear its +0.54 helmet)
		float glow = NovaEffectsRenderer.glowStrength(player, partialTick);
		if (glow > 0.01f) {
			com.projecthero.mod.client.flight.BodyGlow.render(pose, buffers, player, getParentModel(), ageInTicks, glow,
					player.isSprinting() ? 1f : 0f, com.projecthero.mod.client.flight.BodyGlow.NOVA, true);
		}
	}
}

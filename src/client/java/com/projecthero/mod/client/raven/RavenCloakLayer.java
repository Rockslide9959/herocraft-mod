package com.projecthero.mod.client.raven;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.raven.Raven;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Raven: the black shadow cape of Trigon's Wrath (the hood is drawn by {@link RavenAuraLayer}), on the shared
 * {@link FlowingCapeLayer} cloth -- it drifts behind her and streams when she flies fast. Only in the Wrath: the craftable Raven's
 * Cloak costume was removed on 2026-11-12, so outside the Wrath nothing is drawn here.
 */
public class RavenCloakLayer extends FlowingCapeLayer {
	private static final ResourceLocation BLACK = ProjectHeroMod.id("textures/entity/raven/cloak_black.png");

	public RavenCloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return Raven.inWrath(player) && !Raven.inShadow(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return BLACK;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return Raven.isFlying(player) || player.getAbilities().flying || player.isFallFlying();
	}
}

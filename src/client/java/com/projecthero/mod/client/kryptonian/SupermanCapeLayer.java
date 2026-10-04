package com.projecthero.mod.client.kryptonian;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.kryptonian.SupermanSuit;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.9: the Superman Suit's red cloth cape, drawn for anyone wearing the Superman chestplate (so every player sees
 * it): Moon Knight's cape mesh without the hood, with the shield on its back and a short collar over the shoulders.
 *
 * <p>v0.14.21: moved onto the shared {@link FlowingCapeLayer} (the cloth, the collar and the wind physics this class
 * used to carry itself, generalised in v0.14.16 for Thor's cape). Same texture, size and attachment point as before;
 * it also picks up the shared layer's crouch anchor, so crouching no longer sinks the cape into the back. When it
 * shows ({@link SupermanSuit#wearsCape}) and when it streams in the wind ({@link SupermanSuit#capeInWind}) live in the
 * common code so they are gametested.
 */
public class SupermanCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/superman_cape.png");

	public SupermanCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return SupermanSuit.wearsCape(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return SupermanSuit.capeInWind(player);
	}
}

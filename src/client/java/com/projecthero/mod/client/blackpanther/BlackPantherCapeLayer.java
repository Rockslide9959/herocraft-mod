package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.client.render.FlowingCapeLayer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7 (user: "make this the new black panther suit ... Remove the cape and and high collar from the skin and make it a model that
 * reacts with the players movement"): the comics Black Panther's long cape, on the shared cloth {@link FlowingCapeLayer} (the
 * Superman / Shazam / Thor cape): it swings with walking, lifts with crouching and sways side to side. The skin painted it from the
 * shoulders to the ankles, so this is the full-length cloth (1.25 blocks, to the shins). Purple-grey with soft folds, a dark-gold hem
 * like the suit's trims and a violet-black lining ({@code scratchpad/v0177/make_bp_suit_skin.js}).
 *
 * <p>He doesn't fly, but when he drops fast (the end of a Panther Leap, a fall, gliding) the cloth switches to the wind-driven mode
 * and streams up behind him. Shown only while the suit is out: v0.17.7 it builds pixel by pixel from the collar down once the spread
 * from the necklace reaches the top of the back ({@link BlackPantherClothReveal}), and its pixels vanish hem-first on the retract.
 */
public class BlackPantherCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/black_panther/black_panther_cape.png");
	/** Falling faster than this (blocks/tick) streams the cape. */
	private static final double STREAM_FALL = 0.45;

	public BlackPantherCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return BlackPanther.hasPower(player) && BlackPantherRender.capeProgress(BlackPantherRender.anim(player, 0f).cover()) > 0f;
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		// v0.17.7 (user: "make the cape build itself pixel by pixel aswell please like how it does for thor"): texel by texel from the
		// collar down as the spread from the necklace reaches the back, hem first on the way back
		float pt = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return BlackPantherClothReveal.cape(TEXTURE, BlackPantherRender.capeProgress(BlackPantherRender.anim(player, pt).cover()));
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		if (player.isFallFlying()) {
			return true;
		}
		// positions, not getDeltaMovement: other players' velocity is not synced every tick
		return !player.onGround() && !player.isPassenger() && !player.isInWater() && player.getY() - player.yo < -STREAM_FALL;
	}

	@Override
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		// v0.17.7: full length as soon as its part of the build starts -- it no longer unrolls, its texels fill in
		return BlackPantherRender.capeProgress(BlackPantherRender.anim(player, partialTick).cover()) > 0f ? 1f : 0f;
	}
}

package com.projecthero.mod.client.blackpanther;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.client.render.FlowingCapeLayer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.7 (user: "make this the new black panther suit ... Remove the cape and and high collar from the skin and make it a model that
 * reacts with the players movement"): the comics Black Panther's long cape, on the shared cloth {@link FlowingCapeLayer} (the
 * Superman / Shazam / Thor cape): it swings with walking, lifts with crouching and sways side to side. The skin painted it from the
 * shoulders to the ankles, so this is the full-length cloth (1.25 blocks, to the shins). Purple-grey with soft folds, a dark-gold hem
 * like the suit's trims and a violet-black lining ({@code scratchpad/v0177/make_bp_suit_skin.js}).
 *
 * <p>He doesn't fly, but when he drops fast (the end of a Panther Leap, a fall, gliding) the cloth switches to the wind-driven mode
 * and streams up behind him. Shown only while the suit is out: it unrolls from the collar over the last part of the texel build and
 * rolls back up first when the suit drains back into the necklace.
 */
public class BlackPantherCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/black_panther/black_panther_cape.png");
	/** The build share (0..1) after which the cape starts to unroll; it is fully down when the suit is. */
	private static final float UNROLL_FROM = 0.55f;
	/** Falling faster than this (blocks/tick) streams the cape. */
	private static final double STREAM_FALL = 0.45;

	public BlackPantherCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return BlackPanther.hasPower(player) && BlackPantherRender.anim(player, 0f).anything();
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
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
		float cover = BlackPantherRender.anim(player, partialTick).cover();
		return Mth.clamp((cover - UNROLL_FROM) / (1f - UNROLL_FROM), 0f, 1f);
	}
}

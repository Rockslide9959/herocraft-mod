package com.projecthero.mod.client.vision;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.vision.Vision;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Vision: his long mustard-gold cape on the shared {@link FlowingCapeLayer} cloth (the Superman cape's mesh, collar and wind):
 * it hangs to his ankles standing and streams out behind him in flight and in the dive. Its look follows the cape painted on the
 * user's chosen skin, which was taken off the suit ({@code scratchpad/vision/make_vision_suit.js}) so this model is the only cape.
 * The Human Hologram rolls it up as it builds over him (and unrolls it as it drains away); with the whole hologram up it's gone.
 */
public class VisionCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/vision/cape.png");

	public VisionCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return VisionSuitLayer.suitShows(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return Vision.isFlying(player) || Vision.diving(player) || Vision.dashing(player);
	}

	@Override
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		float[] holo = VisionSuitLayer.hologramLine(player, partialTick);
		return holo == null ? 1f : 1f - holo[0];
	}
}

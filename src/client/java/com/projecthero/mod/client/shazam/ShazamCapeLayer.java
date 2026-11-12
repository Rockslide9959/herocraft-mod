package com.projecthero.mod.client.shazam;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.shazam.Shazam;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Shazam: the short white cape with a gold hem, on the shared {@link FlowingCapeLayer} (the Superman cape's cloth): it hangs
 * to the belt ({@link #unfurl}) and streams out behind him in flight. Shown only while transformed. Its look follows the cape painted
 * on the user's chosen skin (off-white, a lavender-grey shadow down the middle, gold hem), which was taken off the suit
 * ({@code scratchpad/shazam/make_shazam_suit.js}) so this model is the only cape.
 */
public class ShazamCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/shazam/shazam_cape.png");
	/** The short cape: this much of the full cloth's length (the painted cape ends just above the belt). */
	private static final float SHORT = 0.4f;

	public ShazamCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return Shazam.transformed(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return Shazam.isFlying(player) || Shazam.eternityAge(player) >= 0;
	}

	@Override
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		return SHORT;
	}
}

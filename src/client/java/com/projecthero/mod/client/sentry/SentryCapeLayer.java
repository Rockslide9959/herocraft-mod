package com.projecthero.mod.client.sentry;

import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.item.SentrySuitItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.5 rework (user: "Add a blue cape to the regular suit and a black cape to the void suit"): the Sentry Suit top's cape, on the
 * shared {@link FlowingCapeLayer} cloth (the Superman / Vision cape: hangs walking, streams out in flight). Royal blue on the Sentry
 * suit, black on the Void suit, blending between them through the Let It Out fade ({@link SentrySuitLayer#voidFade}); in the takeover
 * it is flat black like the rest of the body. Only while the top is worn; the wearer's own Minecraft cape is hidden meanwhile
 * ({@code GyroCoatCapeMixin}). Textures {@code sentry_cape_blue.png} / {@code sentry_cape_black.png} (original, made by
 * {@code scratchpad/sentry/make_suit_textures.js}).
 */
public class SentryCapeLayer extends FlowingCapeLayer {
	public SentryCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/**
	 * The Sentry cape is drawn on this player: the top is on, or (round 3) merged with the Void without the suit -- the black cape comes
	 * with the Void suit and goes with it. Its texture must exist.
	 */
	public static boolean shown(AbstractClientPlayer player) {
		if (player.isInvisible() || SentrySuitTextures.cape(0f, false) == null) {
			return false;
		}
		return SentrySuitItem.topWorn(player) || voidOverSkin(player);
	}

	/** Round 3: the Void suit is over his bare skin (merged without the suit, or fading back). */
	static boolean voidOverSkin(AbstractClientPlayer player) {
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		return Sentry.look(player, SentrySuitLayer.voidFade(player, pt)) == Sentry.Look.VOID_OVER_SKIN;
	}

	/** Round 3: without the suit the black cape unrolls from the collar as the Void suit fades in (and rolls back up as it fades out). */
	@Override
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		if (!SentrySuitItem.topWorn(player) && voidOverSkin(player)) {
			return SentrySuitLayer.voidFade(player, partialTick);
		}
		return 1f;
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return shown(player);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean bare = !SentrySuitItem.topWorn(player);
		ResourceLocation id = SentrySuitTextures.cape(bare ? 1f : SentrySuitLayer.voidFade(player, pt), Sentry.inTakeover(player));
		return id != null ? id : SentrySuitTextures.cape(0f, false);
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return Sentry.isFlying(player);
	}
}

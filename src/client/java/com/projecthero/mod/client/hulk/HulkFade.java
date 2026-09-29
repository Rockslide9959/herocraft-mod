package com.projecthero.mod.client.hulk;

import com.projecthero.mod.hulk.Hulk;

import net.minecraft.world.entity.player.Player;

/**
 * v0.13.15: the Hulk cross-fade, client side. As Banner grows the Hulk model phases onto him ({@link #hulkAlpha} climbs
 * 0 -> 1) while Banner's own body fades out underneath ({@link #bannerAlpha}); shrinking back runs it the other way.
 * Both follow {@link Hulk#visibility}, which runs on the same clock as the growth itself.
 */
public final class HulkFade {
	private HulkFade() {
	}

	/** How solid the Hulk model is, 0..1 (0 for anyone without the Gamma power). */
	public static float hulkAlpha(Player player, float partialTick) {
		return Hulk.hasPower(player) ? Hulk.visibility(player, partialTick) : 0.0f;
	}

	/** How solid Banner's own body is, 0..1 (always 1 for anyone without the Gamma power). */
	public static float bannerAlpha(Player player, float partialTick) {
		return Hulk.hasPower(player) ? 1.0f - Hulk.visibility(player, partialTick) : 1.0f;
	}

	/**
	 * Whether the Hulk model is drawn at all. During the unwilling change it is drawn from the first tick (invisible until
	 * the growth starts) so its GeckoLib clip starts on the same tick as Banner drops to his knees.
	 */
	public static boolean drawHulk(Player player, float partialTick) {
		return Hulk.hasPower(player) && (Hulk.visibility(player, partialTick) > 0.001f || Hulk.changing(player));
	}
}

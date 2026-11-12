package com.projecthero.mod.client.beastboy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastForm;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.17.3 Beast Boy: contract for the custom form bodies (docs/TEEN_TITANS_DESIGN.md). Owned by the BEASTBOY-ART agent;
 * the power side only calls it. Until the art lands every call returns false and the power draws its fallback.
 */
public final class BeastFormArt {
	private BeastFormArt() {
	}

	/**
	 * Draws {@code form}'s body in place of the player. The pose stack is at the player's feet, already rotated to the body yaw
	 * (+Z forward, like a vanilla mob renderer before its own flip), 1 unit = 1 block; draw to fit {@code form.width/height}.
	 *
	 * @param move         the move animating right now ("bite", "roar", "stomp", "charge", "swipe", "slam", "spray", "dive",
	 *                     "grab", "ink", "screech", "pounce", "strike", "smash", "morph_in", "morph_out", ...) or null
	 * @param moveProgress 0..1 through that move (ignored when move is null)
	 * @param tint         ARGB the body is tinted with (Beast Boy green by default; red-shifted while hurt)
	 * @return false when this form has no custom art (the caller falls back)
	 */
	public static boolean render(BeastForm form, AbstractClientPlayer player, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, String move, float moveProgress, int tint) {
		return false;
	}

	/**
	 * Human-form quick morphs: draws the part of an animal that flashes on the player for a split second (gorilla arms,
	 * rhino head and horn, T-Rex head, snake body...). Pose stack is at the player's model root (same space as the player
	 * model parts). {@code progress} 0..1 over the morph (grows in, holds, shrinks out).
	 *
	 * @return false when there's nothing to draw (the caller falls back)
	 */
	public static boolean renderQuickMorph(BeastForm form, AbstractClientPlayer player, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light, float progress, int tint) {
		return false;
	}
}

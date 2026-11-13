package com.projecthero.mod.client.beastboy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.client.beastboy.model.BeastMesh;
import com.projecthero.mod.client.beastboy.model.BeastModel;
import com.projecthero.mod.client.beastboy.model.BeastPose;
import com.projecthero.mod.client.beastboy.model.BatModel;
import com.projecthero.mod.client.beastboy.model.ElephantModel;
import com.projecthero.mod.client.beastboy.model.FlyModel;
import com.projecthero.mod.client.beastboy.model.GorillaModel;
import com.projecthero.mod.client.beastboy.model.HawkModel;
import com.projecthero.mod.client.beastboy.model.LandArt;
import com.projecthero.mod.client.beastboy.model.PteroModel;
import com.projecthero.mod.client.beastboy.model.QuickMorphs;
import com.projecthero.mod.client.beastboy.model.RhinoModel;
import com.projecthero.mod.client.beastboy.model.SeaArt;
import com.projecthero.mod.client.beastboy.model.SmallModels;
import com.projecthero.mod.client.beastboy.model.SnakeModel;
import com.projecthero.mod.client.beastboy.model.TRexModel;
import com.projecthero.mod.client.beastboy.model.SwimRig;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.17.3 Beast Boy: contract for the custom form bodies (docs/TEEN_TITANS_DESIGN.md). Owned by the BEASTBOY-ART agent;
 * the power side only calls it. Forms without custom art (and quick morphs without a partial) return false and the power draws its
 * fallback.
 *
 * <p>The bodies are procedural meshes ({@code client/beastboy/model}, one class per animal on the shared {@link BeastMesh} kit) painted
 * from one swatch atlas. Tint: a green or white tint keeps the painted greens (scaled by its brightness); a red-dominant tint (hurt)
 * shifts the body red and adds the vanilla red hurt overlay; tint alpha below 255 draws see-through.
 *
 * <p>Moves each body animates (anything else falls back to idle / walk): every form {@code morph_in}, {@code morph_out};
 * T-Rex bite, roar, swipe / sweep / tail, stomp, charge; Gorilla smash, grab, throw, strike / punch, roar / chest_beat / beat;
 * Rhino charge / ram, stomp; Elephant stomp, spray, grab, throw; Pterodactyl dive, screech, bite, grab; Shark bite / strike;
 * Octopus grab, strike, ink; Whale slam; Mouse bite; Snake strike / bite; Beast swipe / strike, roar, pounce, smash.
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
		if (form == null || !form.customArt || player == null) {
			return false;
		}
		if (SeaArt.handles(form)) {
			SwimRig.track(player, partialTick); // HQ round: the sea animals' per-player swim smoothing
		}
		return renderPose(form, BeastPose.of(player, partialTick, move, moveProgress), pose, buffers, light, tint);
	}

	/**
	 * Same as {@link #render} but from an explicit animation state (previews, the Guidebook, harnesses). Pose stack at the feet, +Z
	 * forward, blocks.
	 */
	public static boolean renderPose(BeastForm form, BeastPose s, PoseStack pose, MultiBufferSource buffers, int light, int tint) {
		if (form == null || s == null) {
			return false;
		}
		if (SeaArt.handles(form)) { // HQ round: shark, dolphin, whale, octopus are blocky ModelPart animals with their own textures
			return SeaArt.render(form, s, pose, buffers, light, tint);
		}
		// hq round: the land mammals are blocky box models with their own textures
		if (LandArt.handles(form)) {
			return LandArt.render(form, s, pose, buffers, light, tint);
		}
		if (com.projecthero.mod.client.beastboy.model.BigAnimals.render(form, s, pose, buffers, light, tint)) {
			return true; // HQ round: blocky rhino / elephant / gorilla / Beast with their own textures
		}
		BeastMesh.Body body = bodyOf(form, s);
		if (body == null) {
			return false;
		}
		float[] m = s.morph();
		pose.pushPose();
		if (m[0] != 1f) {
			// morphs grow from / shrink to the middle of the body, feet staying near the ground
			float cy = form.height * 0.35f;
			pose.translate(0f, cy * (1f - m[0]) * 0.3f, 0f);
			pose.scale(m[0], m[0], m[0]);
		}
		BeastMesh.draw(body, pose, buffers, light, tint, m[1], form == BeastForm.FLY);
		pose.popPose();
		return true;
	}

	private static BeastMesh.Body bodyOf(BeastForm form, BeastPose s) {
		return switch (form) {
			case TREX -> p -> TRexModel.draw(p, s);
			case GORILLA -> p -> GorillaModel.draw(p, s);
			case RHINO -> p -> RhinoModel.draw(p, s);
			case ELEPHANT -> p -> ElephantModel.draw(p, s);
			case PTERODACTYL -> p -> PteroModel.draw(p, s);
			case MOUSE -> p -> SmallModels.mouse(p, s);
			case FLY -> p -> FlyModel.draw(p, s); // HQ round: own class (was SmallModels.fly)
			case BEAST -> p -> BeastModel.draw(p, s);
			case SNAKE -> p -> SnakeModel.draw(p, s); // HQ round: own class (was SmallModels.snake)
			case HAWK -> p -> HawkModel.draw(p, s); // HQ round
			case BAT -> p -> BatModel.draw(p, s); // HQ round
			default -> null;
		};
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
		float age = player != null ? player.tickCount + partialTick : 0f;
		pose.pushPose();
		// player model root (Y down, front -Z, feet 1.5 below) -> feet origin, +Y up, +Z front
		pose.mulPose(Axis.XP.rotationDegrees(180f));
		pose.translate(0f, -1.5f, 0f);
		boolean drawn = renderQuickMorphAtFeet(form, progress, age, pose, buffers, light, tint);
		pose.popPose();
		return drawn;
	}

	/** Quick morph from a feet-origin pose stack (+Y up, +Z the player's front, blocks): for previews. */
	public static boolean renderQuickMorphAtFeet(BeastForm form, float progress, float age, PoseStack pose, MultiBufferSource buffers, int light,
			int tint) {
		if (form == null) {
			return false;
		}
		// morph round: the quick-morph partials are the new blocky animal parts attached to his player model (QuickMorphs, drawn from
		// the player renderer); nothing is drawn at the feet any more
		return false;
	}
}

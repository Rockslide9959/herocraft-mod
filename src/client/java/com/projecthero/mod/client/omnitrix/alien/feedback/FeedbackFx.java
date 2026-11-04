package com.projecthero.mod.client.omnitrix.alien.feedback;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.FeedbackCables;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.alien.feedback.FeedbackKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Feedback's rendered energy, on Brainstorm's lightning ({@link BrainstormFx#bolt}: jagged additive ribbons with a white-hot
 * core) in his pale electric yellow: his fingertip bolts and the thick Redirect / Cannon / Discharge ones (from his hand, or from
 * an entity the carrier names), and the "suck" -- energy pulled INTO him, drawn from what he absorbs or drains to the nearest of his
 * plugs.
 */
public final class FeedbackFx {
	private FeedbackFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("feedback_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("feedback_big", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("feedback_suck", FeedbackFx::suck);
	}

	private static float fade(AlienProjectileEntity e, float partial, float life) {
		return Mth.clamp(1f - (e.tickCount + partial) / life, 0f, 1f);
	}

	/** A bolt ending here; colour 0 = from the owner's hand, n = from entity n - 1. */
	private static void arc(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean big) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 end = e.getPosition(partial);
		Vec3 start = null;
		int c = e.colour();
		if (c > 0) {
			Entity from = mc.level.getEntity(c - 1);
			if (from != null) {
				start = from.getPosition(partial).add(0, from.getBbHeight() * 0.55, 0);
			}
		} else if (e.getOwner() instanceof Player owner) {
			start = BrainstormFx.hand(owner, partial);
		}
		if (start == null) {
			return;
		}
		float k = 0.55f + 0.45f * fade(e, partial, big ? 12f : 7f);
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		long seed = e.getId() * 7919L + (e.tickCount / 2);
		BrainstormFx.bolt(pose, buffers, start, end, seed, big ? 0.26f : 0.1f, k, FeedbackKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, start, end, seed + 5, big ? 0.12f : 0.04f, k * 0.7f, 0xFFF6C8);
		pose.popPose();
	}

	/** Energy pulled from the carrier's spot into the owner's nearest plug (his chest if the cords aren't simulated yet). */
	private static void suck(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		if (!(e.getOwner() instanceof AbstractClientPlayer owner)) {
			return;
		}
		Vec3 from = e.getPosition(partial);
		Vec3 to = null;
		double best = Double.MAX_VALUE;
		for (int r = 0; r < 3; r++) {
			Vec3 t = FeedbackCables.tip(owner, r, partial);
			if (t != null && t.distanceToSqr(from) < best) {
				best = t.distanceToSqr(from);
				to = t;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (to == null || (owner == mc.player && mc.options.getCameraType().isFirstPerson())) {
			to = BrainstormFx.hand(owner, partial);
		}
		float k = 0.5f + 0.5f * fade(e, partial, 6f);
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		long seed = e.getId() * 104729L + (e.tickCount / 2);
		BrainstormFx.bolt(pose, buffers, from, to, seed, 0.08f, k, FeedbackKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, from, to, seed + 3, 0.03f, k * 0.8f, 0xFFFFFF);
		pose.popPose();
	}
}

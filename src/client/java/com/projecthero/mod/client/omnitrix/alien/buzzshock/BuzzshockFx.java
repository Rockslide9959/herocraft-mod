package com.projecthero.mod.client.omnitrix.alien.buzzshock;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.alien.buzzshock.BuzzshockKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Buzzshock's rendered electricity, on Brainstorm's lightning ({@link BrainstormFx#bolt}: jagged additive ribbons with a
 * white-hot core and forks, re-rolled every other tick) in his own lime green: his bolts (from his hand, or from a copy / the Spark
 * Bomb when the carrier names one), Megavolt's thick one, and the Spark Bomb -- a churning green-white orb licking arcs.
 */
public final class BuzzshockFx {
	private BuzzshockFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("buzzshock_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("buzzshock_big", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("buzzshock_ball", BuzzshockFx::ball);
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
		BrainstormFx.bolt(pose, buffers, start, end, seed, big ? 0.26f : 0.11f, k, BuzzshockKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, start, end, seed + 5, big ? 0.12f : 0.04f, k * 0.7f, 0xC8FF80);
		pose.popPose();
	}

	/** The Spark Bomb: a churning green-white orb with little arcs licking off it. */
	private static void ball(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size() * 0.6f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.sphere(pose, vc, r, 0xFFF0FFD0, 0xFF3C8010, 0.12f, t, PyroDraw.FULL);
		PyroDraw.sphere(pose, vc, r * 1.7f, 0xFF204010, 0xFF081004, 0.05f, t * 0.5f, PyroDraw.FULL);
		pose.popPose();
		pose.pushPose();
		BrainstormFx.toCamera(pose, e, partial);
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
		Random rnd = new Random(e.getId() * 17L + e.tickCount / 2);
		for (int i = 0; i < 4; i++) {
			Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(r * 2.4);
			BrainstormFx.bolt(pose, buffers, c, c.add(d), rnd.nextLong(), 0.035f, 0.85f, BuzzshockKit.COLOUR);
		}
		pose.popPose();
	}
}

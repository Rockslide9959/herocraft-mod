package com.projecthero.mod.client.omnitrix.alien.frankenstrike;

import java.util.Random;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.FrankenstrikeClient;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.frankenstrike.FrankenstrikeKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Frankenstrike's electricity, drawn on Brainstorm's lightning ({@link BrainstormFx#bolt}) in his pale electric blue: the bolts of
 * his moves (from his hand, or jumping on from a creature the carrier names), the arc that crackles between his two electrode bolts --
 * now and then when he is low, without a break when he is full, wild while he recharges or calls the storm -- and the current crawling
 * in from the air to his bolts while he recharges.
 */
public final class FrankenstrikeFx {
	private FrankenstrikeFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("frankenstrike_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("frankenstrike_big", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		WorldRenderEvents.AFTER_ENTITIES.register(FrankenstrikeFx::world);
	}

	/** A bolt tip in the world (body yaw only: close enough for an arc that never stands still). */
	public static Vec3 tip(AbstractClientPlayer p, float side, float partial) {
		Vector3f m = FrankenstrikeClient.tip(side);
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float s = p.getScale();
		Vector3f v = new Vector3f(-m.x * s, (1.501f - m.y) * s, m.z * s);
		if (p.isCrouching()) {
			v.add(0f, -0.2f * s, 0f);
		}
		new Quaternionf().rotationY((180f - yaw) * Mth.DEG_TO_RAD).transform(v);
		return p.getPosition(partial).add(v.x, v.y, v.z);
	}

	private static void world(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		for (var pl : mc.level.players()) {
			if (!(pl instanceof AbstractClientPlayer p) || !FrankenstrikeKit.isFrankenstrike(p) || p.isInvisible()) {
				continue;
			}
			teslaAndStatic(context, p, partial);
			if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			PoseStack pose = context.matrixStack();
			MultiBufferSource buffers = context.consumers();
			int flags = Omnitrix.state(p).kitFlags;
			boolean wild = (flags & (FrankenstrikeKit.F_RECHARGE | FrankenstrikeKit.F_STORM)) != 0;
			float c = FrankenstrikeKit.charge(p) / (float) FrankenstrikeKit.MAX_CHARGE;
			long gt = p.level().getGameTime();
			// the neck-bolt arc: low Charge -- a flicker every so often; full -- always there, re-struck every other tick
			int period = wild ? 1 : c >= 0.5f ? 2 : c > 0.15f ? 6 : 14;
			boolean on = wild || c >= 0.5f || (gt + p.getId()) % period < 2;
			Vec3 a = tip(p, -1f, partial), b = tip(p, 1f, partial);
			if (on) {
				long seed = p.getId() * 131L + gt / 2;
				float k = wild ? 1f : 0.55f + 0.45f * c;
				BrainstormFx.bolt(pose, buffers, a, b, seed, wild ? 0.045f : 0.03f, k, FrankenstrikeKit.COLOUR);
				if (wild || c > 0.8f) {
					BrainstormFx.bolt(pose, buffers, a, b, seed + 7, 0.02f, k * 0.8f, FrankenstrikeKit.COLOUR);
				}
			}
			if ((flags & FrankenstrikeKit.F_RECHARGE) != 0) {
				// current crawling in from the air to each bolt
				Random rnd = new Random(p.getId() * 29L + gt / 2);
				for (int i = 0; i < 4; i++) {
					Vec3 to = i % 2 == 0 ? a : b;
					double ang = rnd.nextDouble() * Math.PI * 2;
					Vec3 from = to.add(Math.cos(ang) * 1.6, 0.4 + rnd.nextDouble() * 1.4, Math.sin(ang) * 1.6);
					BrainstormFx.bolt(pose, buffers, from, to, rnd.nextLong(), 0.02f, 0.6f, FrankenstrikeKit.COLOUR);
				}
			}
			if ((flags & FrankenstrikeKit.F_GRAB) != 0) {
				// the held creature crackling in his hands
				Random rnd = new Random(p.getId() * 17L + gt);
				Vec3 h = BrainstormFx.hand(p, partial);
				for (int i = 0; i < 2; i++) {
					Vec3 off = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.2, rnd.nextDouble() - 0.5).scale(1.2);
					BrainstormFx.bolt(pose, buffers, h, h.add(off), rnd.nextLong(), 0.025f, 0.7f, FrankenstrikeKit.COLOUR);
				}
			}
		}
	}

	private static float fade(AlienProjectileEntity e, float partial, float life) {
		return Mth.clamp(1f - (e.tickCount + partial) / life, 0f, 1f);
	}

	/** A bolt ending at the carrier: colour 0 = from the owner's hand, n = from entity n - 1. */
	/**
	 * v0.17.4: the Tesla Field -- a bolt crackling between the tops of its two electrodes, re-rolled every other tick -- and Static Armour,
	 * short arcs jumping over his body.
	 */
	private static void teslaAndStatic(WorldRenderContext context, AbstractClientPlayer p, float partial) {
		Vec3 cam = context.camera().getPosition();
		double[] t = FrankenstrikeKit.tesla(p);
		long tick = p.level().getGameTime();
		if (t != null) {
			Vec3[] el = FrankenstrikeKit.electrodes(new Vec3(t[0], t[1], t[2]), (float) t[3]);
			Vec3 a = el[0].add(0, 1.5, 0).subtract(cam), b = el[1].add(0, 1.5, 0).subtract(cam);
			com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx.bolt(context.matrixStack(), context.consumers(), a, b, 0.14f, FrankenstrikeKit.WHITE,
					FrankenstrikeKit.COLOUR, 1f, tick / 2 * 13L + p.getId());
			com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx.bolt(context.matrixStack(), context.consumers(), a.subtract(0, 0.6, 0), b.subtract(0, 0.6, 0), 0.08f,
					FrankenstrikeKit.WHITE, FrankenstrikeKit.COLOUR, 0.6f, tick / 2 * 17L + p.getId());
		}
		if (FrankenstrikeKit.staticUp(p)) {
			Vec3 base = p.getPosition(partial);
			float s = p.getScale();
			java.util.Random r = new java.util.Random(tick / 2 * 31L + p.getId());
			for (int i = 0; i < 3; i++) {
				double a0 = r.nextDouble() * Math.PI * 2, a1 = a0 + 0.6 + r.nextDouble();
				double rad = 0.45 * s;
				Vec3 from = base.add(Math.cos(a0) * rad, (0.3 + r.nextDouble() * 1.4) * s, Math.sin(a0) * rad);
				Vec3 to = base.add(Math.cos(a1) * rad, (0.3 + r.nextDouble() * 1.4) * s, Math.sin(a1) * rad);
				com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx.bolt(context.matrixStack(), context.consumers(), from.subtract(cam), to.subtract(cam), 0.06f,
						FrankenstrikeKit.WHITE, FrankenstrikeKit.COLOUR, 0.9f, r.nextLong());
			}
		}
	}

	private static void arc(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean big) {
		Minecraft mc = Minecraft.getInstance();
		if (e.getOwner() != null && com.projecthero.mod.client.beamclash.BeamClashClient.hides(e.getOwner())) {
			return; // v0.17.4: his Arc Stream is locked in a beam clash, and the clash draws it
		}
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
		BrainstormFx.bolt(pose, buffers, start, end, seed, big ? 0.24f : 0.09f, k, FrankenstrikeKit.COLOUR);
		BrainstormFx.bolt(pose, buffers, start, end, seed + 5, big ? 0.11f : 0.04f, k * 0.7f, FrankenstrikeKit.WHITE);
		pose.popPose();
	}
}

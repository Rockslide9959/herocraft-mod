package com.projecthero.mod.client.omnitrix.alien.brainstorm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.brainstorm.BrainstormKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (Recalibrated Omnitrix, basic Brainstorm): his rendered electricity -- jagged additive lightning ribbons (a white-hot core
 * in a yellow-violet glow, flickering forks re-rolled every other tick), the Mega Bolt's thick beam, sky strikes under the storm
 * cloud (a flat churning dark disc with flashes inside it), the Static Cage's crackling bars, the Ball Lightning orb and the
 * Force Field's bubble (a faint additive sphere with a brighter shimmering band).
 */
public final class BrainstormFx {
	private BrainstormFx() {
	}

	private static boolean registered;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("brainstorm_arc", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("brainstorm_mega", (e, partial, pose, buffers) -> arc(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("brainstorm_strike", BrainstormFx::strike);
		OmnitrixRenderers.projectileLook("brainstorm_cloud", BrainstormFx::cloud);
		OmnitrixRenderers.projectileLook("brainstorm_cage", BrainstormFx::cage);
		OmnitrixRenderers.projectileLook("brainstorm_ball", BrainstormFx::ball);
		WorldRenderEvents.AFTER_ENTITIES.register(BrainstormFx::fields);
	}

	// ---------------------------------------------------------------- shared

	public static Vec3 cam() {
		return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
	}

	/** Moves {@code pose} (an entity renderer's, at {@code e}'s feet) to the camera, so world points minus the camera can be drawn. */
	public static void toCamera(PoseStack pose, Entity e, float partial) {
		Vec3 at = e.getPosition(partial);
		Vec3 c = cam();
		pose.translate(c.x - at.x, c.y - at.y, c.z - at.z);
	}

	/** Where a player's claws / hands are, for a bolt or a strand to leave from. */
	public static Vec3 hand(Player p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 look = p.getViewVector(partial);
		if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			return p.getEyePosition(partial).add(look.scale(0.7)).add(right.scale(0.28)).add(0, -0.28, 0);
		}
		float yaw = Mth.lerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float s = p.getScale();
		return p.getPosition(partial).add(0, 1.2 * s, 0).add(fwd.scale(0.45 * s)).add(right.scale(0.36 * s));
	}

	/** A jagged path from {@code a} to {@code b}: {@code amp} the sideways jitter, fixed for one {@code seed}. */
	public static List<Vec3> jag(Vec3 a, Vec3 b, long seed, double amp) {
		Random r = new Random(seed);
		Vec3 d = b.subtract(a);
		double len = d.length();
		int n = Math.max(3, Math.min(48, (int) (len * 2.2)));
		Vec3 dir = len < 1.0e-6 ? new Vec3(0, 1, 0) : d.scale(1.0 / len);
		Vec3 u = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 v = dir.cross(u).normalize();
		List<Vec3> pts = new ArrayList<>(n + 1);
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n;
			Vec3 p = a.add(d.scale(t));
			if (i > 0 && i < n) {
				double k = amp * Math.sqrt(Math.sin(Math.PI * t));
				p = p.add(u.scale((r.nextDouble() - 0.5) * 2 * k)).add(v.scale((r.nextDouble() - 0.5) * 2 * k));
			}
			pts.add(p);
		}
		return pts;
	}

	private static List<Vec3> rel(List<Vec3> pts, Vec3 c) {
		List<Vec3> out = new ArrayList<>(pts.size());
		for (Vec3 p : pts) {
			out.add(p.subtract(c));
		}
		return out;
	}

	/** One lightning bolt from {@code a} to {@code b} (world), {@code k} its brightness 0..1, with a few forks. */
	public static void bolt(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, long seed, float width, float k, int glow) {
		Vec3 c = cam();
		double len = a.distanceTo(b);
		List<Vec3> pts = jag(a, b, seed, Math.min(0.6, 0.12 + len * 0.05) * (width > 0.2f ? 1.6 : 1.0));
		VertexConsumer vc = PyroDraw.additive(buffers);
		List<Vec3> r = rel(pts, c);
		PyroDraw.ribbon(pose, vc, r, s -> width * 3.2, s -> PyroDraw.dim(glow, k * 0.55f));
		PyroDraw.ribbon(pose, vc, r, s -> width, s -> PyroDraw.dim(0xFFFFFF, k));
		// forks: short side bolts off the main one
		Random rnd = new Random(seed * 31 + 7);
		int forks = (int) Math.min(4, 1 + len / 4);
		for (int i = 0; i < forks; i++) {
			int at = 1 + rnd.nextInt(Math.max(1, pts.size() - 2));
			Vec3 from = pts.get(at);
			Vec3 dir = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize()
					.scale(0.6 + rnd.nextDouble() * Math.min(2.0, len * 0.15));
			List<Vec3> f = rel(jag(from, from.add(dir), seed + i * 97L, 0.15), c);
			PyroDraw.ribbon(pose, vc, f, s -> width * 2.0 * (1 - s), s -> PyroDraw.dim(glow, k * 0.5f * (1f - (float) s)));
			PyroDraw.ribbon(pose, vc, f, s -> width * 0.6 * (1 - s), s -> PyroDraw.dim(0xFFFFFF, k * (1f - (float) s)));
		}
	}

	private static float fade(AlienProjectileEntity e, float partial, float life) {
		float t = e.tickCount + partial;
		return Mth.clamp(1f - t / life, 0f, 1f);
	}

	// ---------------------------------------------------------------- the looks

	/** A bolt ending here; colour 0 = from the owner's claws, n = from entity n - 1. */
	private static void arc(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean mega) {
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
			start = hand(owner, partial);
		}
		if (start == null) {
			return;
		}
		float k = 0.55f + 0.45f * fade(e, partial, mega ? 12f : 7f);
		pose.pushPose();
		toCamera(pose, e, partial);
		long seed = e.getId() * 7919L + (e.tickCount / 2);
		bolt(pose, buffers, start, end, seed, mega ? 0.28f : 0.09f, k, mega ? 0xA898FF : 0xFFE060);
		if (mega) {
			bolt(pose, buffers, start, end, seed + 3, 0.12f, k * 0.8f, 0xFFF0A0);
		}
		pose.popPose();
	}

	/** A bolt from the sky (14 blocks up) down to here. */
	private static void strike(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Vec3 end = e.getPosition(partial);
		float k = 0.4f + 0.6f * fade(e, partial, 6f);
		pose.pushPose();
		toCamera(pose, e, partial);
		long seed = e.getId() * 104729L + (e.tickCount / 2);
		bolt(pose, buffers, end.add(0, 9, 0), end, seed, 0.16f, k, 0xC8B8FF);
		pose.popPose();
	}

	/** The storm cloud: a churning dark disc of puffs (size = radius) with flashes inside it. */
	private static void cloud(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size();
		float in = Mth.clamp(t / 15f, 0f, 1f) * Mth.clamp((BrainstormKit.STORM_TICKS + 10 - t) / 15f, 0f, 1f);
		if (in <= 0.01f) {
			return;
		}
		pose.pushPose();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		int a = (int) (170 * in);
		// three slowly turning layers of grey, darker underneath
		for (int layer = 0; layer < 3; layer++) {
			pose.pushPose();
			pose.translate(0, layer * 0.35f, 0);
			pose.mulPose(com.mojang.math.Axis.YP.rotation(t * 0.01f * (layer % 2 == 0 ? 1 : -1)));
			int grey = 0x30 + layer * 0x18;
			int col = (a << 24) | (grey << 16) | (grey << 8) | (grey + 0x10);
			PyroDraw.annulus(pose, vc, 0f, r * (1f - layer * 0.12f) * (0.5f + 0.5f * in), col, (a / 3) << 24 | (grey << 16) | (grey << 8) | grey, 0f);
			pose.popPose();
		}
		pose.popPose();
		// flashes inside it
		Random rnd = new Random(e.getId() * 13L + (long) (t / 3));
		VertexConsumer glow = PyroDraw.additive(buffers);
		if (rnd.nextFloat() < 0.6f) {
			double ang = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * r * 0.8;
			PyroDraw.glow(pose, glow, new Vec3(Math.cos(ang) * d, 0.2, Math.sin(ang) * d), 1.2f + rnd.nextFloat() * 1.5f, PyroDraw.dim(0x9C8CFF, 0.6f * in));
		}
	}

	/** The Static Cage: crackling bars round the creature (entity colour - 1), two rings. */
	private static void cage(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		Entity t = mc.level.getEntity(e.colour() - 1);
		if (t == null) {
			return;
		}
		Vec3 base = t.getPosition(partial);
		float w = t.getBbWidth() * 0.5f + 0.35f;
		float h = t.getBbHeight() + 0.3f;
		float k = 0.6f + 0.4f * Mth.sin((e.tickCount + partial) * 1.3f);
		pose.pushPose();
		toCamera(pose, e, partial);
		long seed = e.getId() * 31L + e.tickCount / 2;
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + e.tickCount * 0.05;
			Vec3 foot = base.add(Math.cos(a) * w, 0, Math.sin(a) * w);
			bolt(pose, buffers, foot, foot.add(0, h, 0), seed + i, 0.05f, k, 0xFFE060);
		}
		pose.popPose();
		pose.pushPose();
		Vec3 off = base.subtract(e.getPosition(partial));
		pose.translate(off.x, off.y, off.z);
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.band(pose, vc, w, 0.05f, PyroDraw.dim(0xFFE060, k), 0xFF000000);
		pose.translate(0, h, 0);
		PyroDraw.band(pose, vc, w, 0.05f, PyroDraw.dim(0xFFE060, k), 0xFF000000);
		pose.popPose();
	}

	/** Ball Lightning: a churning white-yellow orb with little arcs licking off it. */
	private static void ball(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float r = e.size() * 0.6f;
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		VertexConsumer vc = PyroDraw.additive(buffers);
		PyroDraw.sphere(pose, vc, r, 0xFFFFF0B0, 0xFF806020, 0.12f, t, PyroDraw.FULL);
		PyroDraw.sphere(pose, vc, r * 1.6f, 0xFF403010, 0xFF100804, 0.05f, t * 0.5f, PyroDraw.FULL);
		pose.popPose();
		pose.pushPose();
		toCamera(pose, e, partial);
		Vec3 c = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
		Random rnd = new Random(e.getId() * 17L + e.tickCount / 2);
		for (int i = 0; i < 3; i++) {
			Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize().scale(r * 2.6);
			bolt(pose, buffers, c, c.add(d), rnd.nextLong(), 0.04f, 0.8f, 0xFFE060);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Force Field

	private static void fields(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (Player p : mc.level.players()) {
			if (!BrainstormKit.ID.equals(Omnitrix.active(p))) {
				continue;
			}
			OmnitrixState s = Omnitrix.state(p);
			if ((s.kitFlags & BrainstormKit.F_FIELD) == 0) {
				continue;
			}
			float t = mc.level.getGameTime() + partial;
			long end = s.readyAt(BrainstormKit.ID + "." + BrainstormKit.FIELD_END);
			float left = end - mc.level.getGameTime() - partial;
			float k = Mth.clamp(left / 10f, 0f, 1f) * (0.85f + 0.15f * Mth.sin(t * 0.6f));
			Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.5, 0);
			pose.pushPose();
			pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
			VertexConsumer vc = PyroDraw.additive(context.consumers());
			float r = (float) BrainstormKit.FIELD_RADIUS;
			PyroDraw.sphere(pose, vc, r, PyroDraw.dim(0x6A5A2A, k), PyroDraw.dim(0x241C0C, k), 0.015f, t * 2f, PyroDraw.FULL);
			// a bright shimmering band sweeping up and down it
			float y = Mth.sin(t * 0.15f) * r * 0.8f;
			float br = Mth.sqrt(Math.max(0.01f, r * r - y * y));
			pose.translate(0, y, 0);
			PyroDraw.band(pose, vc, br, 0.12f, PyroDraw.dim(0xFFE68A, k * 0.7f), 0xFF000000);
			pose.popPose();
		}
	}
}

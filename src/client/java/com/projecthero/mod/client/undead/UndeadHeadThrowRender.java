package com.projecthero.mod.client.undead;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBulletConfig;
import com.projecthero.mod.undead.UndeadHeadThrow;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3 Head Throw (hold X, {@link UndeadHeadThrow}) on the client: the poses -- the struggle to wrench the head off with both
 * hands, then the head held back behind the throwing shoulder (drawn in that hand) while the other arm points along the aim, like
 * winding up a baseball throw -- the raw neck and head faces while it is off, the dotted trajectory arc to where it will land
 * (the local player only, worked out by {@link UndeadHeadThrow#predict} with the flight's own physics), and right click mid-air
 * to start the regrowth early.
 */
public final class UndeadHeadThrowRender {
	/** Models whose head pivot was moved into the hand this frame (the shared model must be put back for the next player). */
	private static final Map<HumanoidModel<?>, Boolean> DISPLACED = Collections.synchronizedMap(new WeakHashMap<>());
	/** Blocks between the trajectory line's dots. */
	static final double DOT_GAP = 0.55;

	private UndeadHeadThrowRender() {
	}

	// ---------------------------------------------------------------- poses

	/**
	 * From {@link UndeadPose#apply} before anything else: a shared model whose head was moved into a hand for the last player
	 * drawn gets it back (vanilla's setupAnim never resets the head's x / z).
	 */
	static void resetModel(HumanoidModel<?> model) {
		if (DISPLACED.remove(model) != null) {
			model.head.x = 0f;
			model.head.z = 0f;
			model.hat.copyFrom(model.head);
		}
	}

	/** From {@link UndeadPose#apply}: true if the rip or the aim owns the pose. */
	static boolean pose(Player player, UndeadState s, HumanoidModel<?> model, float now) {
		if (s.animId != UndeadHeadThrow.ANIM_RIP && s.animId != UndeadHeadThrow.ANIM_AIM) {
			return false;
		}
		float age = Math.max(0f, now - s.animStart);
		if (s.animId == UndeadHeadThrow.ANIM_RIP) {
			rip(model, age);
			return true;
		}
		aim(player, s, model, age);
		return true;
	}

	/**
	 * The struggle: both hands clamped on the sides of the head, wrenching it -- twists side to side that get bigger and quicker,
	 * the head lifting off the neck as it gives, the shoulders hunched and shaking.
	 */
	private static void rip(HumanoidModel<?> model, float age) {
		float span = UndeadBulletConfig.HEAD_RIP_HOLD - UndeadBulletConfig.HEAD_RIP_SHOW;
		float k = Mth.clamp(age / span, 0f, 1f);
		float in = Mth.clamp(age / 4f, 0f, 1f);
		float rate = 0.55f + 0.9f * k;
		float jerk = Mth.sin(age * rate) * (0.15f + 0.45f * k) + Mth.sin(age * 2.7f) * 0.05f * k;
		float shake = Mth.sin(age * 5.3f) * 0.04f * (0.3f + k);
		model.head.yRot = Mth.lerp(in, model.head.yRot, jerk);
		model.head.xRot = Mth.lerp(in, model.head.xRot, -0.2f * k + shake);
		model.head.zRot = Mth.lerp(in, model.head.zRot, jerk * 0.35f);
		model.head.y -= in * (0.4f + 2.2f * k * k); // the neck stretching as it gives
		model.hat.copyFrom(model.head);
		// hands up on either side of the head, elbows out, twisting with it
		set(model.rightArm, in, -2.75f + shake, -0.35f + jerk * 0.25f, 0.55f + jerk * 0.3f);
		set(model.leftArm, in, -2.75f - shake, 0.35f + jerk * 0.25f, -0.55f + jerk * 0.3f);
		model.body.yRot += jerk * 0.15f * in;
	}

	/**
	 * Holding it back: the throwing arm up and behind the shoulder with the head in its hand (the head's pivot moved there), the
	 * other arm straight out along the look, the shoulders turned back toward the throwing side.
	 */
	private static void aim(Player player, UndeadState s, HumanoidModel<?> model, float age) {
		int arm = UndeadHeadThrow.throwingArm(player, s);
		boolean right = arm == UndeadState.RIGHT_ARM;
		float side = right ? 1f : -1f;
		ModelPart throwArm = right ? model.rightArm : model.leftArm;
		ModelPart pointArm = right ? model.leftArm : model.rightArm;
		float in = Mth.clamp(age / 5f, 0f, 1f);
		float e = in * in * (3f - 2f * in);
		float breathe = Mth.sin(age * 0.18f) * 0.03f;
		float lookX = model.head.xRot;
		float lookY = model.head.yRot;
		model.body.yRot += 0.32f * side * e;
		// the throwing arm cocked right back past the vertical, elbow out: the hand (and the head in it) behind the shoulder
		set(throwArm, e, -3.45f + breathe, 0.25f * side, 0.55f * side);
		// the other arm points where he aims
		set(pointArm, e, lookX - Mth.HALF_PI, lookY, 0f);
		// the head sits in the throwing hand: the hand's point in model space (the arm hangs down +y from its pivot)
		float handX = right ? -1f : 1f;
		Vector3f hand = new Vector3f(handX, 11.5f, 0f);
		new Quaternionf().rotationZYX(throwArm.zRot, throwArm.yRot, throwArm.xRot).transform(hand);
		hand.add(throwArm.x, throwArm.y, throwArm.z);
		// cupped in the palm: the head's middle just behind and outside the hand (its pivot is the bottom of the head)
		model.head.x = Mth.lerp(e, 0f, hand.x - 1.5f * side);
		model.head.y = Mth.lerp(e, model.head.y, hand.y + 4.5f);
		model.head.z = Mth.lerp(e, 0f, hand.z + 3.0f);
		model.head.xRot = Mth.lerp(e, model.head.xRot, 0.35f);
		model.head.yRot = Mth.lerp(e, model.head.yRot, 0.5f * side);
		model.head.zRot = Mth.lerp(e, model.head.zRot, -0.25f * side);
		model.hat.copyFrom(model.head);
		DISPLACED.put(model, Boolean.TRUE);
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}

	// ---------------------------------------------------------------- the raw faces while the head is off

	/** From the layer: while it is held back, a wet stump on the neck and a raw face under the held head. */
	static void layer(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s) {
		if (s.animId != UndeadHeadThrow.ANIM_AIM || s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.FLESH));
		pose.pushPose();
		model.body.translateAndRotate(pose);
		capY(vc, pose, -3.2f, 3.2f, -1.9f, 1.9f, -0.04f, light);
		pose.popPose();
		pose.pushPose();
		model.head.translateAndRotate(pose);
		capY(vc, pose, -3.4f, 3.4f, -3.4f, 3.4f, 0.04f, light);
		pose.popPose();
	}

	private static void capY(VertexConsumer vc, PoseStack pose, float x0, float x1, float z0, float z1, float y, int light) {
		Matrix4f m = pose.last().pose();
		float[][] p = { { x0, z0, 0, 0 }, { x1, z0, 1, 0 }, { x1, z1, 1, 1 }, { x0, z1, 0, 1 } };
		for (float[] v : p) {
			vc.addVertex(m, v[0] / 16f, y / 16f, v[1] / 16f).setColor(255, 255, 255, 255).setUv(v[2], v[3]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light).setNormal(pose.last(), 0, -1, 0);
		}
	}

	// ---------------------------------------------------------------- the trajectory line

	/** From the world render: the local player aiming sees the arc his head will fly, dotted, to a marker where it lands. */
	static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.level == null || context.consumers() == null || context.matrixStack() == null || !UndeadHeadThrow.aiming(p)) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 start = UndeadHeadThrow.heldHeadPos(p, partial);
		Vec3 vel = UndeadHeadThrow.throwVelocity(p, partial);
		boolean[] landed = new boolean[1];
		List<Vec3> pts = UndeadHeadThrow.predict(mc.level, start, vel, UndeadBulletConfig.HEAD_PREDICT_TICKS, landed);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = context.consumers().getBuffer(RenderType.debugQuads());
		float t = (mc.level.getGameTime() + partial) * 0.12f;
		// a dot every DOT_GAP blocks along the arc, the whole row creeping outward so it reads as a flight path
		double next = (t % 1f) * DOT_GAP;
		double total = 0;
		int n = 0;
		for (int i = 1; i < pts.size(); i++) {
			Vec3 a = pts.get(i - 1), b = pts.get(i);
			double len = a.distanceTo(b);
			while (len > 1.0e-6 && next <= total + len) {
				Vec3 at = a.add(b.subtract(a).scale((next - total) / len));
				if (at.distanceToSqr(cam) > 0.6) {
					float fade = Math.min(1f, (n + 1) / 3f);
					dot(vc, m, at, cam, 0.045f, 1f, 0.93f, 0.9f, 0.9f * fade);
				}
				n++;
				next += DOT_GAP;
			}
			total += len;
		}
		if (landed[0] && pts.size() > 1) {
			marker(vc, m, pts.get(pts.size() - 1), mc.level.getGameTime() + partial);
		}
		pose.popPose();
	}

	/** A camera-facing round dot (an octagon) with a dark red rim, so it reads against sky and ground alike. */
	private static void dot(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, float r, float red, float green, float blue, float alpha) {
		Vec3 to = cam.subtract(at).normalize();
		Vec3 up = Math.abs(to.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 u = up.cross(to).normalize();
		Vec3 v = to.cross(u).normalize();
		octagon(vc, m, at.add(to.scale(-0.01)), u, v, r * 1.55f, 0.45f, 0.02f, 0.04f, alpha * 0.85f);
		octagon(vc, m, at, u, v, r, red, green, blue, alpha);
	}

	private static void octagon(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, float red, float green, float blue, float alpha) {
		Vec3[] p = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			double a = (i + 0.5) * Math.PI / 4.0;
			p[i] = c.add(u.scale(Math.cos(a) * r)).add(v.scale(Math.sin(a) * r));
		}
		// three quads cover the octagon
		quad(vc, m, p[0], p[1], p[2], p[3], red, green, blue, alpha);
		quad(vc, m, p[0], p[3], p[4], p[7], red, green, blue, alpha);
		quad(vc, m, p[4], p[5], p[6], p[7], red, green, blue, alpha);
	}

	/** Where it will land: a pulsing ring of blood-red squares round the spot. */
	private static void marker(VertexConsumer vc, Matrix4f m, Vec3 c, float time) {
		float pulse = 0.5f + 0.5f * Mth.sin(time * 0.35f);
		double r = 0.38 + 0.06 * pulse;
		int seg = 16;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Mth.TWO_PI / seg, a1 = (i + 0.6) * Mth.TWO_PI / seg;
			Vec3 p0 = c.add(Math.cos(a0) * r, -0.2, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, -0.2, Math.sin(a1) * r);
			Vec3 q0 = c.add(Math.cos(a0) * (r + 0.09), -0.2, Math.sin(a0) * (r + 0.09)), q1 = c.add(Math.cos(a1) * (r + 0.09), -0.2, Math.sin(a1) * (r + 0.09));
			quad(vc, m, p0, p1, q1, q0, 0.9f, 0.08f, 0.1f, 0.85f);
		}
		// and the head-sized box it will end in, faintly
		double h = UndeadHeadThrow.HEAD_SIZE * 0.5;
		float a = 0.18f + 0.1f * pulse;
		Vec3 lo = c.subtract(h, h, h), hi = c.add(h, h, h);
		quad(vc, m, new Vec3(lo.x, lo.y, lo.z), new Vec3(hi.x, lo.y, lo.z), new Vec3(hi.x, hi.y, lo.z), new Vec3(lo.x, hi.y, lo.z), 1f, 0.2f, 0.2f, a);
		quad(vc, m, new Vec3(lo.x, lo.y, hi.z), new Vec3(hi.x, lo.y, hi.z), new Vec3(hi.x, hi.y, hi.z), new Vec3(lo.x, hi.y, hi.z), 1f, 0.2f, 0.2f, a);
		quad(vc, m, new Vec3(lo.x, lo.y, lo.z), new Vec3(lo.x, lo.y, hi.z), new Vec3(lo.x, hi.y, hi.z), new Vec3(lo.x, hi.y, lo.z), 1f, 0.2f, 0.2f, a);
		quad(vc, m, new Vec3(hi.x, lo.y, lo.z), new Vec3(hi.x, lo.y, hi.z), new Vec3(hi.x, hi.y, hi.z), new Vec3(hi.x, hi.y, lo.z), 1f, 0.2f, 0.2f, a);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float al) {
		vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, al);
		vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, al);
		vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, al);
		vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, al);
	}

	// ---------------------------------------------------------------- right click mid-air

	private static boolean sent;

	/** Start of every client tick: right click while the thrown head is still in the air grows the body back now. */
	static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null || mc.screen != null || !UndeadHeadThrow.awaitingRegrowth(p) || !Undead.hasPower(p)) {
			sent = false;
			return;
		}
		boolean clicked = false;
		while (mc.options.keyUse.consumeClick()) {
			clicked = true;
		}
		if (clicked && !sent && mc.getConnection() != null) {
			sent = true;
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.projecthero.mod.network.UndeadHeadThrowPayload.INSTANCE);
		}
	}
}

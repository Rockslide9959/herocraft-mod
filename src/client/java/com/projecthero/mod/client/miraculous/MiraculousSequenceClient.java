package com.projecthero.mod.client.miraculous;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.thor.ThorDraw;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the client half of every hero's long transformation ({@link MiraculousSequence}) -- every client plays it
 * from the synced {@code formChangeAt}. The frame is shared; each hero's {@link Scene} supplies its poses, its light
 * show, its world effects and its camera path:
 * <ul>
 *   <li>{@link #applyPose} -- the hero's pose wins over everything while it plays, easing back out at the end;</li>
 *   <li>{@link #tick} -- turns every player in a sequence through its spin, hides the holder's HUD, and plays the
 *   scene's particles;</li>
 *   <li>{@link #renderWorld} -- the scene's drawn effects (bubbles, portals, ribbons, butterflies);</li>
 *   <li>{@link #camera} -- the cinematic camera path for the holder ({@code MiraculousSequenceCameraMixin});</li>
 *   <li>{@link #suitCover} / {@link #age} -- for the kits' renderers (the suit follows the sequence's own reveal).</li>
 * </ul>
 */
public final class MiraculousSequenceClient {
	/** One hero's long transformation on screen. */
	public interface Scene {
		/** Pose keys at {@code t}: head x, right arm x / z, left arm x / z, right leg x / z, left leg x / z, body x. */
		float[] pose(Player p, MiraculousState s, float t);

		/** Anything past the ten keys (arm yaw, a crouch...), {@code w} = how much of the sequence's pose applies. */
		default void extraPose(Player p, HumanoidModel<?> model, float t, float w) {
		}

		/** The particles of tick {@code t}; {@code yaw} = the way they face now. */
		void fx(ClientLevel level, Player p, MiraculousState s, int t, float yaw);

		/** Camera keys: tick, distance, angle round them from the front (deg), camera height, height looked at. */
		float[][] cam();

		/** Drawn effects, after the entities (positions relative to the camera, as the context's matrix stack is). */
		default void world(WorldRenderContext ctx, Player p, MiraculousState s, float t, float partial) {
		}
	}

	public static final int EASE_OUT = 12;
	/** The HUD was hidden for the holder's sequence (and whether it was hidden before). */
	private static boolean hidHud;
	private static boolean hudWasHidden;

	private MiraculousSequenceClient() {
	}

	public static @Nullable Scene of(@Nullable Kwami k) {
		if (k == null) {
			return null;
		}
		return switch (k) {
			case TIKKI -> com.projecthero.mod.client.miraculous.ladybug.LadybugSequenceClient.SCENE;
			case PLAGG -> com.projecthero.mod.client.miraculous.catnoir.CatNoirSequenceClient.SCENE;
			case TRIXX -> com.projecthero.mod.client.miraculous.renarouge.RenaRougeSequenceClient.SCENE;
			case NOOROO -> com.projecthero.mod.client.miraculous.hawkmoth.HawkMothSequenceClient.SCENE;
			case WAYZZ -> com.projecthero.mod.client.miraculous.carapace.CarapaceSequenceClient.SCENE;
			case KAALKI -> com.projecthero.mod.client.miraculous.pegasus.PegasusSequenceClient.SCENE;
			case SASS -> com.projecthero.mod.client.miraculous.viperion.ViperionSequenceClient.SCENE;
			case MULLO -> null; // v0.17.1: Multimouse has the quick transformation for now
		};
	}

	private static float rawAge(Player p, MiraculousState s, float partial) {
		return (float) (p.level().getGameTime() - s.formChangeAt) + partial;
	}

	/**
	 * Ticks into {@code kwami}'s long transformation on {@code p} (with {@code partial}), or -1 when it isn't playing
	 * (past the end too). A client clock trailing the server's a little reads as the start.
	 */
	public static float age(Player p, Kwami kwami, float partial) {
		MiraculousState s = Miraculous.peek(p);
		if (s == null || p.level() == null || !kwami.id.equals(s.kwami) || !MiraculousSequence.isLong(s) || s.formChangeAt <= 0L) {
			return -1f;
		}
		float t = rawAge(p, s, partial);
		if (t < 0f && t > -40f) {
			t = 0f;
		}
		MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
		return t < 0f || tl == null || t >= tl.total() ? -1f : t;
	}

	/** How much of the suit the long transformation has on {@code p} now, or -1 when it isn't playing (the kit's own rule then). */
	public static float suitCover(Player p, MiraculousState s, float partial) {
		Kwami k = Kwami.byId(s.kwami);
		if (k == null) {
			return -1f;
		}
		float t = age(p, k, partial);
		return t < 0f ? -1f : MiraculousSequence.of(k).cover(t);
	}

	private static float startYaw(MiraculousState s) {
		return MiraculousSequence.startYaw(s);
	}

	/** The way they face at {@code t}: where they began, turned through the hero's spin. */
	public static float yaw(MiraculousState s, float t) {
		MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
		return startYaw(s) + (tl == null ? 0f : tl.spin(Math.min(t, tl.total())));
	}

	// ---------------------------------------------------------------- the pose

	/** From {@code MiraculousClient.applyPose}, after the shared pose and the kit's (it wins while a long sequence plays). */
	public static void applyPose(Player player, HumanoidModel<?> model) {
		MiraculousState s = Miraculous.peek(player);
		if (!MiraculousSequence.isLong(s)) {
			return;
		}
		Scene scene = of(Kwami.byId(s.kwami));
		MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
		float t = rawAge(player, s, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (scene == null || tl == null || t < 0f || t >= tl.total() + EASE_OUT) {
			return;
		}
		float w = t < tl.total() ? 1f : 1f - smooth((t - tl.total()) / EASE_OUT);
		float[] pose = scene.pose(player, s, Math.min(t, tl.total()));
		set(model.head, pose[0], 0f, w, true);
		set(model.rightArm, pose[1], pose[2], w, false);
		set(model.leftArm, pose[3], pose[4], w, false);
		set(model.rightLeg, pose[5], pose[6], w, false);
		set(model.leftLeg, pose[7], pose[8], w, false);
		model.body.xRot = Mth.lerp(w, model.body.xRot, pose[9]);
		model.head.yRot = Mth.lerp(w, model.head.yRot, 0f);
		scene.extraPose(player, model, Math.min(t, tl.total()), w);
		model.hat.copyFrom(model.head);
	}

	private static void set(ModelPart part, float x, float z, float w, boolean head) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		if (!head) {
			part.zRot = Mth.lerp(w, part.zRot, z);
			part.yRot = Mth.lerp(w, part.yRot, 0f);
		}
	}

	public static float[] blend(float[] a, float[] b, float f) {
		float[] o = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			o[i] = Mth.lerp(f, a[i], b[i]);
		}
		return o;
	}

	public static float smooth(float x) {
		return MiraculousSequence.smooth(x);
	}

	/**
	 * A pose from key poses: {@code keys[i]} is held from {@code at[i]}, and blends into {@code keys[i + 1]} over the
	 * {@code blend[i]} ticks before {@code at[i + 1]}.
	 */
	public static float[] keyed(float t, int[] at, int[] blend, float[][] keys) {
		for (int i = keys.length - 1; i >= 0; i--) {
			if (t >= at[i]) {
				if (i + 1 < keys.length && t > at[i + 1] - blend[i]) {
					return blend(keys[i], keys[i + 1], smooth((t - (at[i + 1] - blend[i])) / blend[i]));
				}
				return keys[i];
			}
		}
		return keys[0];
	}

	/** A crouch like vanilla's sneak (the body tipped, the legs back under it), {@code w} of the way. */
	public static void crouch(HumanoidModel<?> m, float w) {
		if (w <= 0f) {
			return;
		}
		m.body.y = Mth.lerp(w, m.body.y, 3.2f);
		m.head.y = Mth.lerp(w, m.head.y, 4.2f);
		m.rightArm.y = Mth.lerp(w, m.rightArm.y, 5.2f);
		m.leftArm.y = Mth.lerp(w, m.leftArm.y, 5.2f);
		m.rightLeg.y = Mth.lerp(w, m.rightLeg.y, 12.2f);
		m.leftLeg.y = Mth.lerp(w, m.leftLeg.y, 12.2f);
		m.rightLeg.z = Mth.lerp(w, m.rightLeg.z, 4.0f);
		m.leftLeg.z = Mth.lerp(w, m.leftLeg.z, 4.0f);
	}

	// ---------------------------------------------------------------- the turn and the light show

	/** Every client tick (END): turn every player in a sequence, and draw its light. */
	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || mc.isPaused()) {
			return;
		}
		// the holder watches it like a scene: no HUD and no hand in the way, given back the moment it ends
		boolean mine = camera(mc.player, 0f) != null;
		if (mine && !hidHud) {
			hudWasHidden = mc.options.hideGui;
			mc.options.hideGui = true;
			hidHud = true;
		} else if (!mine && hidHud) {
			mc.options.hideGui = hudWasHidden;
			hidHud = false;
		}
		for (Player p : level.players()) {
			MiraculousState s = Miraculous.peek(p);
			if (!MiraculousSequence.isLong(s) || p.distanceToSqr(mc.player) > 64 * 64) {
				continue;
			}
			MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
			Scene scene = of(Kwami.byId(s.kwami));
			float t = rawAge(p, s, 0f);
			if (scene == null || t < 0f || t >= tl.total()) {
				continue;
			}
			float now = yaw(s, t);
			float before = yaw(s, Math.max(0f, t - 1f));
			p.yBodyRotO = before;
			p.yBodyRot = now;
			p.yHeadRotO = before;
			p.yHeadRot = now;
			scene.fx(level, p, s, (int) t, now);
		}
	}

	/** After the entities: each scene's drawn effects. */
	public static void renderWorld(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		for (Player p : mc.level.players()) {
			MiraculousState s = Miraculous.peek(p);
			if (!MiraculousSequence.isLong(s) || p.distanceToSqr(mc.player) > 64 * 64 || p.isInvisible()) {
				continue;
			}
			MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
			Scene scene = of(Kwami.byId(s.kwami));
			float t = rawAge(p, s, partial);
			if (scene == null || t < 0f || t >= tl.total() + EASE_OUT) {
				continue;
			}
			scene.world(ctx, p, s, t, partial);
		}
	}

	// ---------------------------------------------------------------- helpers for the scenes

	/** Where they float at {@code t} (the start point, lifted). */
	public static Vec3 origin(MiraculousState s, float t) {
		MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
		return new Vec3(MiraculousSequence.startX(s), MiraculousSequence.startY(s) + (tl == null ? 0.0 : tl.lift(Math.min(t, tl.total()))),
				MiraculousSequence.startZ(s));
	}

	/** Forward (the way they face at {@code yawDeg}) on the ground plane. */
	public static Vec3 forward(float yawDeg) {
		float rad = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
	}

	/** Their right, for {@link #forward}. */
	public static Vec3 right(Vec3 fwd) {
		return new Vec3(-fwd.z, 0, fwd.x);
	}

	/** A glowing strip through world points {@code path} ({@link ThorDraw#ribbon}): a soft halo in {@code rgb} and a bright {@code core}. */
	public static void ribbon(WorldRenderContext ctx, Vec3[] path, float halfWidth, int rgb, int core, float alpha) {
		if (path.length < 2 || alpha <= 0.003f) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		Vec3[] rel = new Vec3[path.length];
		for (int i = 0; i < path.length; i++) {
			rel[i] = path[i].subtract(cam);
		}
		VertexConsumer vc = ThorDraw.buffer(ctx.consumers());
		PoseStack.Pose last = ctx.matrixStack().last();
		ThorDraw.ribbon(vc, last, rel, halfWidth, rgb, 0.45f * alpha);
		ThorDraw.ribbon(vc, last, rel, halfWidth * 0.38f, core, 0.9f * alpha);
	}

	/** A flat glowing ring round world point {@code c} ({@link ThorDraw#ring}). */
	public static void ring(WorldRenderContext ctx, Vec3 c, float inner, float outer, int rgb, float alpha) {
		if (alpha <= 0.003f) {
			return;
		}
		Vec3 rel = c.subtract(ctx.camera().getPosition());
		ThorDraw.ring(ThorDraw.buffer(ctx.consumers()), ctx.matrixStack().last(), rel, inner, outer, rgb, alpha, 40);
	}

	/** A band of light standing on a circle round world point {@code c}, bright at its foot ({@link ThorDraw#wall}). */
	public static void band(WorldRenderContext ctx, Vec3 c, float radius, float height, int rgb, float alpha) {
		if (alpha <= 0.003f) {
			return;
		}
		Vec3 rel = c.subtract(ctx.camera().getPosition());
		ThorDraw.wall(ThorDraw.buffer(ctx.consumers()), ctx.matrixStack().last(), rel, radius, height, rgb, alpha, 40);
	}

	/**
	 * A helix round the vertical line through {@code base}: {@code n + 1} points from height {@code y0} to {@code y1},
	 * angle {@code a0} to {@code a1} (radians), radius {@code r0} to {@code r1}.
	 */
	public static Vec3[] helix(Vec3 base, double y0, double y1, double a0, double a1, double r0, double r1, int n) {
		Vec3[] out = new Vec3[n + 1];
		for (int i = 0; i <= n; i++) {
			double f = i / (double) n;
			double a = a0 + (a1 - a0) * f;
			double r = r0 + (r1 - r0) * f;
			out[i] = base.add(Math.cos(a) * r, y0 + (y1 - y0) * f, Math.sin(a) * r);
		}
		return out;
	}

	// ---------------------------------------------------------------- the camera

	/** Where the holder's camera is at {@code t}, and where it looks: {pos x, y, z, yaw, pitch}; null when not playing. */
	public static double[] camera(Player p, float partial) {
		MiraculousState s = Miraculous.peek(p);
		if (!MiraculousSequence.isLong(s)) {
			return null;
		}
		MiraculousSequence.Timeline tl = MiraculousSequence.of(s);
		Scene scene = of(Kwami.byId(s.kwami));
		float t = rawAge(p, s, partial);
		if (scene == null || !(t >= 0f && t < tl.total())) {
			return null;
		}
		float[][] cam = scene.cam();
		int i = 0;
		while (i < cam.length - 2 && t > cam[i + 1][0]) {
			i++;
		}
		float[] a = cam[i];
		float[] b = cam[i + 1];
		float f = smooth((t - a[0]) / (b[0] - a[0]));
		float dist = Mth.lerp(f, a[1], b[1]);
		float ang = Mth.lerp(f, a[2], b[2]);
		float camH = Mth.lerp(f, a[3], b[3]);
		float lookH = Mth.lerp(f, a[4], b[4]);
		double cx = MiraculousSequence.startX(s);
		double cy = MiraculousSequence.startY(s) + tl.lift(t);
		double cz = MiraculousSequence.startZ(s);
		float yaw0 = startYaw(s) * Mth.DEG_TO_RAD;
		// "in front of them" is along their starting facing; the angle walks the camera round them
		double around = yaw0 + ang * Mth.DEG_TO_RAD;
		double px = cx - Math.sin(around) * dist;
		double pz = cz + Math.cos(around) * dist;
		double py = cy + camH;
		double dx = cx - px;
		double dy = cy + lookH - py;
		double dz = cz - pz;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float camYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float camPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
		return new double[] { px, py, pz, camYaw, camPitch };
	}
}

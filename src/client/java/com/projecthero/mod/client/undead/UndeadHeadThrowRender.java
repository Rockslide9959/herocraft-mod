package com.projecthero.mod.client.undead;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBulletConfig;
import com.projecthero.mod.undead.UndeadHeadThrow;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
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
	/** 0..1 through the struggle ({@code age} ticks into it): how hard he is wrenching. */
	static float ripK(float age) {
		return Mth.clamp(age / (UndeadBulletConfig.HEAD_RIP_HOLD - UndeadBulletConfig.HEAD_RIP_SHOW), 0f, 1f);
	}

	/** The head's twist (radians) at {@code age}: side-to-side jerks growing bigger and quicker. Shared by the pose and the view. */
	static float ripJerk(float age) {
		float k = ripK(age);
		float rate = 0.55f + 0.9f * k;
		return Mth.sin(age * rate) * (0.15f + 0.45f * k) + Mth.sin(age * 2.7f) * 0.05f * k;
	}

	private static void rip(HumanoidModel<?> model, float age) {
		float k = ripK(age);
		float in = Mth.clamp(age / 4f, 0f, 1f);
		float jerk = ripJerk(age);
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

	// ---------------------------------------------------------------- first person: the rip, the head's own view

	/**
	 * The vanilla bare right arm's shoulder and fist in the hand renderer's camera space ({@code renderPlayerArm} at rest; x is
	 * mirrored for the left). The rip's hands are that same arm moved as a rigid body so its fist lands where the head is gripped.
	 */
	private static final Vector3f VANILLA_SHOULDER = new Vector3f(0.504f, -0.819f, -0.663f), VANILLA_FIST = new Vector3f(0.735f, -0.479f, -1.138f);

	/**
	 * v0.17.3 (user: "in first person ... the player should see the hands and arm pulling the head off"): while he struggles to
	 * tear his head off (hold X), both hands and forearms come up from the bottom corners into view and clamp the sides of his
	 * head -- the edges of the view -- wrenching at it: they shake harder and pull up as it gives, while the view itself twists
	 * with the head ({@link #shakeAngles}; the hands are counter-turned so they stay clamped on while the world wrenches round).
	 * From {@link UndeadFirstPerson#arms}: true if this took the hands over (only the main hand's call draws both).
	 */
	static boolean firstPersonRip(ItemInHandRenderer renderer, AbstractClientPlayer player, UndeadState s, float now, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		if (s.animId != UndeadHeadThrow.ANIM_RIP) {
			return false;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return true;
		}
		float age = Math.max(0f, now - s.animStart);
		float k = ripK(age);
		float jerk = ripJerk(age);
		float in = Mth.clamp(age / 5f, 0f, 1f);
		float e = in * in * (3f - 2f * in);
		float[] sh = shakeAngles(player, s, now);
		pose.pushPose();
		// the hands hold the head the camera is in: undo the view's twist so they stay put on screen
		pose.mulPose(Axis.YP.rotationDegrees(-sh[2]));
		pose.mulPose(Axis.XP.rotationDegrees(-sh[1]));
		pose.mulPose(Axis.ZP.rotationDegrees(-sh[0]));
		for (HumanoidArm side : HumanoidArm.values()) {
			int arm = side == HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
			if (s.broken(arm)) {
				continue;
			}
			float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
			float tremble = Mth.sin(now * 5.3f + sd * 1.7f) * 0.007f * (0.3f + k);
			// rising from below the view to the sides of the head; the two hands twist it against each other (one up, one down)
			// and pull up harder as it gives
			Vector3f fist = new Vector3f(GRIP_X * sd + tremble, GRIP_Y - 0.55f * (1f - e) + 0.035f * k * k + jerk * 0.05f * sd, GRIP_Z);
			Vector3f along = new Vector3f(-0.25f * sd, 0.9f, -0.2f);
			along.rotateZ(jerk * 0.18f).normalize();
			rigidArm(renderer, pose, buffers, light, side, fist, along, GRIP_TWIST * sd + tremble * 40f);
		}
		pose.popPose();
		return true;
	}

	/** Where the rip's fists grip (camera space): out at the sides of the view at about eye height, close. */
	static final float GRIP_X = 0.17f, GRIP_Y = -0.03f, GRIP_Z = -0.36f;
	/** The fists turned about the forearm (degrees) so the palms face in, onto the head. */
	static final float GRIP_TWIST = 70f;

	/** The vanilla bare arm moved as one piece: its fist to {@code fist}, its shoulder-to-fist line along {@code along}, turned {@code twist} degrees about it. */
	private static void rigidArm(ItemInHandRenderer renderer, PoseStack pose, MultiBufferSource buffers, int light, HumanoidArm side, Vector3f fist, Vector3f along,
			float twist) {
		float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
		Vector3f vFist = new Vector3f(VANILLA_FIST.x * sd, VANILLA_FIST.y, VANILLA_FIST.z);
		Vector3f vAxis = new Vector3f(VANILLA_FIST).sub(VANILLA_SHOULDER).mul(sd, 1f, 1f).normalize();
		Quaternionf q = new Quaternionf().rotationAxis(twist * Mth.DEG_TO_RAD, along.x, along.y, along.z).mul(new Quaternionf().rotationTo(vAxis, along));
		pose.pushPose();
		pose.translate(fist.x, fist.y, fist.z);
		pose.mulPose(q);
		pose.translate(-vFist.x, -vFist.y, -vFist.z);
		((com.projecthero.mod.client.mixin.UndeadItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
		pose.popPose();
	}

	/**
	 * The view's twist this frame for the local player, {roll, pitch, yaw} in degrees: while he wrenches at his head it jerks and
	 * twists with the head (growing with the struggle); the moment it tears off a sharp jolt that dies away.
	 */
	static float[] shakeAngles(Player p, UndeadState s, float now) {
		if (s == null || !s.hasPower) {
			return new float[3];
		}
		float age = Math.max(0f, now - s.animStart);
		if (s.animId == UndeadHeadThrow.ANIM_RIP) {
			float k = ripK(age);
			float in = Mth.clamp(age / 4f, 0f, 1f);
			float jerk = ripJerk(age) * Mth.RAD_TO_DEG;
			float judder = (Mth.sin(age * 5.3f) * 0.9f + Mth.sin(age * 8.9f) * 0.5f) * (0.3f + k);
			return new float[] { (jerk * 0.3f + judder * 0.6f) * in, (-5f * k + judder * 0.7f) * in, jerk * 0.35f * in };
		}
		if (s.animId == UndeadHeadThrow.ANIM_AIM && age < TEAR_JOLT) {
			float d = 1f - age / TEAR_JOLT;
			d *= d;
			return new float[] { 9f * d * Mth.sin(age * 2.4f), -12f * d * Mth.cos(age * 1.9f), 5f * d * Mth.sin(age * 3.1f) };
		}
		return new float[3];
	}

	/** Ticks the tear-off jolt of the view lasts. */
	static final float TEAR_JOLT = 9f;

	/** From {@link UndeadFirstPerson#shake} (the world and hand views): the rip's twist and the tear-off jolt, first person only. */
	public static void shake(PoseStack pose, float partial) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.level == null || !mc.options.getCameraType().isFirstPerson() || mc.getCameraEntity() != p) {
			return;
		}
		float[] a = shakeAngles(p, Undead.peek(p), mc.level.getGameTime() + partial);
		if (a[0] == 0f && a[1] == 0f && a[2] == 0f) {
			return;
		}
		pose.mulPose(Axis.ZP.rotationDegrees(a[0]));
		pose.mulPose(Axis.XP.rotationDegrees(a[1]));
		pose.mulPose(Axis.YP.rotationDegrees(a[2]));
	}

	/** Ticks the first-person camera takes to swing from the eyes out to the held head once it is off. */
	static final float CAMERA_SWING = 6f;

	/**
	 * v0.17.3 (user: "once they do their perspective should match where the head is so their head should be slightly off center
	 * with the body"): where the local player's first-person camera goes ({@code eye}: vanilla's), or null to leave it. Tearing
	 * at it, the view lifts as the neck stretches; once it is off and held back behind the shoulder, the camera swings out to the
	 * held head ({@link UndeadHeadThrow#heldHeadPos}): an off-centre view from the head, behind and beside his own body (drawn in
	 * full meanwhile: {@link #headView}). In the air the player is the head, so vanilla's camera already follows it.
	 */
	public static Vec3 cameraPos(Player p, float partial, Vec3 eye) {
		UndeadState s = Undead.peek(p);
		if (s == null || !s.hasPower || p.level() == null) {
			return null;
		}
		float now = p.level().getGameTime() + partial;
		float age = Math.max(0f, now - s.animStart);
		if (s.animId == UndeadHeadThrow.ANIM_RIP) {
			float k = ripK(age);
			float in = Mth.clamp(age / 4f, 0f, 1f);
			return eye.add(0, in * (0.4f + 2.2f * k * k) / 16f * 0.9375f, 0); // the model's head lift, in blocks
		}
		if (s.animId != UndeadHeadThrow.ANIM_AIM || s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			return null;
		}
		float f = Mth.clamp(age / CAMERA_SWING, 0f, 1f);
		f = f * f * (3f - 2f * f);
		Vec3 lifted = eye.add(0, 2.6f / 16f * 0.9375f, 0);
		// the held head's middle, eased a touch back and up (where its eyes look out past his shoulder), so his own back,
		// shoulder and pointing arm read as a body rather than one shoulder pressed against the lens
		float yaw = Mth.rotLerp(partial, p.yRotO, p.getYRot());
		Vec3 flat = Vec3.directionFromRotation(0f, yaw);
		Vec3 head = UndeadHeadThrow.heldHeadPos(p, partial).add(flat.scale(-HEAD_VIEW_BACK)).add(0, HEAD_VIEW_UP, 0);
		return lifted.lerp(head, f);
	}

	/** How far behind and above the held head's middle the aiming camera sits (blocks): about where its eyes are, cupped back. */
	static final double HEAD_VIEW_BACK = 0.3, HEAD_VIEW_UP = 0.12;

	/**
	 * The full-body view's lens bubble (blocks) for {@code FirstPersonBody}: a thrown head grows its body back right round the
	 * camera (and leaves the old one just under it as it flies off), so everything within {@link #THROWN_LENS} of the lens is kept off
	 * it; the rest of the body -- the torso stretching away, the limbs -- shows. 0 otherwise (the usual bubble).
	 */
	public static double lensRadius(AbstractClientPlayer p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.animId != UndeadHeadThrow.ANIM_AIM && headView(p) ? THROWN_LENS : 0.0;
	}

	static final double THROWN_LENS = 0.6;

	/** The local player was thrown as a head and the body has not all grown back yet (see {@link #headView}). */
	private static boolean thrownView;

	/**
	 * v0.17.3 (user: "also let the player see their third person body while their head is disconnected please"): the local
	 * player's own body is drawn in first person (the full-body view, {@code FirstPersonBody}, in place: the camera is the head)
	 * while the head is off -- held back to throw, in the air, and after it lands until the body has grown back round it. The
	 * head itself (in his hand, or the one he is) is culled from the lens as in every full-body view.
	 */
	public static boolean headView(AbstractClientPlayer p) {
		if (p != Minecraft.getInstance().player) {
			return false;
		}
		UndeadState s = Undead.peek(p);
		if (s == null || !s.hasPower) {
			return false;
		}
		if (s.animId == UndeadHeadThrow.ANIM_AIM && !s.broken(UndeadState.HEAD) && !s.broken(UndeadState.BODY)) {
			return true;
		}
		return s.animId == UndeadHeadThrow.ANIM_THROWN || thrownView && s.anyBroken() && !s.broken(UndeadState.HEAD);
	}

	private static void watchThrown(Player p) {
		UndeadState s = p == null ? null : Undead.peek(p);
		if (s == null || !s.hasPower || s.broken(UndeadState.HEAD)) {
			thrownView = false;
		} else if (s.animId == UndeadHeadThrow.ANIM_THROWN) {
			thrownView = true;
		} else if (thrownView && !s.anyBroken()) {
			thrownView = false;
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
		watchThrown(p);
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

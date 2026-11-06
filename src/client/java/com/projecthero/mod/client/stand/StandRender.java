package com.projecthero.mod.client.stand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.network.StandFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: every summoned Stand in the world -- drawn ONLY on a Stand user's client (a non-Stand user's client never even
 * receives the state; this also checks the local player is a Stand user, so a revoked player stops seeing them at once).
 * No entity: each frame the body is placed from its user's interpolated position and the synced {@link StandState}
 * (animation id + start), like Dark Shadow.
 *
 * <ul>
 *   <li>The body is the vanilla wide player model wearing the Stand's skin (Star Platinum: the user's NovaSkin), about
 *       1.15x a player, with a faint purple glow shell, floating and bobbing behind the right shoulder at rest.</li>
 *   <li>While it fights (any attack, the rush, Guard, the charge, a held thing, the inhale) it moves in front of its user --
 *       in first person far enough out to be seen.</li>
 *   <li>Its body moves on real elbows and knees ({@link StandPoses}: keyframed clips, cross-faded every frame), and Star Finger is a
 *       stretching finger mesh ({@link StandFinger}).</li>
 *   <li>The ORA Rush draws blurred extra arm copies and (setting) floating "ORA" text; a summon throws up ゴゴゴ.</li>
 *   <li>A held block is drawn in its hand; faint violet motes drift off it (client particles: Stand users only).</li>
 * </ul>
 */
public final class StandRender {
	private static final float SCALE = 1.15f * 0.9375f;
	/** The waist, above the figure's origin (blocks): what it leans about. */
	private static final float WAIST = 0.95f;

	/** Per-Stand smoothing (where it floats, which way it faces). */
	private static final class Smooth {
		Vec3 pos;
		float yaw = Float.NaN;
		long lastNanos;
		/** The pose on screen, chasing the wanted one ({@link StandPoses#target}); null until the first frame. */
		float[] pose;
		/** The Star Finger whose fingertip flash has gone off (its animation's start). */
		long fingerFlash;
	}

	private static final Map<Integer, Smooth> SMOOTH = new ConcurrentHashMap<>();
	private static PlayerModel<Player> model;

	private StandRender() {
	}

	static void reset() {
		SMOOTH.clear();
		model = null;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Length of a one-shot animation (ticks). */
	static int animLength(int animId) {
		return StandPoses.length(animId);
	}

	/** The Star Finger clip is playing. */
	private static boolean fingerOut(StandState s, float age) {
		return s.animId == StandState.ANIM_FINGER && age >= 0f && age < animLength(StandState.ANIM_FINGER) && s.fingerEnd.size() == 3;
	}

	/** It is fighting: it stands in front of its user. */
	static boolean fighting(StandState s, float age) {
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L) {
			return true;
		}
		return switch (s.animId) {
			case StandState.ANIM_NONE, StandState.ANIM_SUMMON, StandState.ANIM_DISMISS, StandState.ANIM_LEAP -> false;
			default -> age >= 0f && age < animLength(s.animId);
		};
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		if (!Stands.isStandUser(mc.player)) {
			return; // the user's rule: only a Stand user perceives a Stand
		}
		if (model == null) {
			model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			StandState s = Stands.peek(p);
			if (s == null || !s.isUser() || p.isSpectator() || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			StandType type = StandType.byId(s.standId);
			if (type == null) {
				continue;
			}
			float age = p.level().getGameTime() - s.animStart + partial;
			boolean dismissing = !s.summoned && s.animId == StandState.ANIM_DISMISS && age >= 0f && age < animLength(StandState.ANIM_DISMISS);
			if (!s.summoned && !dismissing) {
				SMOOTH.remove(p.getId());
				continue;
			}
			try {
				renderOne(ctx.matrixStack(), ctx.consumers(), p, s, type, partial, age, cam, dismissing);
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.warn("[ProjectHero] Stand render failed", e);
				SMOOTH.remove(p.getId());
			}
		}
	}

	private static void renderOne(PoseStack pose, MultiBufferSource buffers, Player p, StandState s, StandType type, float partial, float age,
			Vec3 cam, boolean dismissing) {
		Minecraft mc = Minecraft.getInstance();
		boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
		boolean fight = fighting(s, age);
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 at = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = p.getViewYRot(partial);
		float pitch = p.getViewXRot(partial);
		Vec3 fwdBody = dir(bodyYaw);
		Vec3 fwdHead = dir(headYaw);

		Vec3 want;
		float wantYaw;
		if (fight) {
			// v0.17.2 (user: "in the bottom corner of the players screen, i want him to be in front of the player like he is in third
			// person"): in first person too it stands square in front of you, its back to you, at its full height (user: "star platinum
			// is still low down") -- far enough out that you see it from the head down, as you do in third person
			double out = firstPerson ? 2.1 : 1.05;
			want = at.add(fwdHead.scale(out)).add(0, 0.05, 0);
			wantYaw = headYaw;
			if (fingerOut(s, age)) {
				// Star Finger: squared up on the target -- and in first person a step aside, so you see the finger and what it pokes
				Vec3 tip = new Vec3(s.fingerEnd.get(0), s.fingerEnd.get(1), s.fingerEnd.get(2));
				if (firstPerson) {
					want = want.add(StarPlatinum.rightOf(fwdHead).scale(0.95)).subtract(fwdHead.scale(0.35));
				}
				Vec3 to = tip.subtract(want.add(0, 1.4, 0));
				wantYaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
				pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
			}
		} else if (s.animId == StandState.ANIM_LEAP && age >= 0f && age < animLength(StandState.ANIM_LEAP)) {
			// Stand Leap: it is underneath you, throwing you up
			want = at.add(0, -1.2, 0).add(fwdBody.scale(0.15));
			wantYaw = bodyYaw;
		} else {
			want = at.subtract(fwdBody.scale(0.5)).add(StarPlatinum.rightOf(fwdBody).scale(0.75)).add(0, 0.35 + 0.06 * Mth.sin(t * 0.1f), 0);
			wantYaw = bodyYaw;
		}
		Smooth sm = SMOOTH.computeIfAbsent(p.getId(), k -> new Smooth());
		long nanos = System.nanoTime();
		float dt = sm.lastNanos == 0L ? 1f : Mth.clamp((nanos - sm.lastNanos) / 5.0e7f, 0f, 3f);
		sm.lastNanos = nanos;
		if (sm.pos == null || sm.pos.distanceToSqr(want) > 10 * 10) {
			sm.pos = want;
		} else {
			// quick: it snaps out in front to fight, and drifts back to the shoulder -- in first person a little slower both ways
			// (user: "make him float in front of the user and then float back behind the user like how it happens in third person")
			float k = Mth.clamp((fight ? (firstPerson && !fingerOut(s, age) ? 0.32f : 0.6f) : (firstPerson ? 0.1f : 0.25f)) * dt, 0f, 1f);
			sm.pos = sm.pos.lerp(want, k);
		}
		if (Float.isNaN(sm.yaw)) {
			sm.yaw = wantYaw;
		}
		sm.yaw += Mth.wrapDegrees(wantYaw - sm.yaw) * Mth.clamp(0.35f * dt, 0f, 1f);

		// ---- the pose: chase the wanted one, fast enough that a strike still snaps (a filter on frame time, not a tick step)
		float[] wantPose = new float[StandPoses.N];
		StandPoses.target(s, p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.STAND_POSE, null), age, t, now, fight, wantPose);
		if (sm.pose == null) {
			sm.pose = wantPose.clone();
		}
		float follow = 1f - (float) Math.exp(-1.7f * dt);
		for (int i = 0; i < StandPoses.N; i++) {
			sm.pose[i] += (wantPose[i] - sm.pose[i]) * follow;
		}
		float[] pp = sm.pose;
		// it leans into its user's speed
		Vec3 vel = p.getDeltaMovement();
		float speedLean = Mth.clamp((float) vel.horizontalDistance() * 0.9f, 0f, 0.4f);
		Vec3 facing = dir(sm.yaw);
		Vec3 pos = sm.pos.add(facing.scale(pp[StandPoses.FWD])).add(0, pp[StandPoses.UP], 0)
				.add(StarPlatinum.rightOf(facing).scale(pp[StandPoses.SIDE]));

		float alpha = 1f;
		if (s.animId == StandState.ANIM_SUMMON && age < StandConfig.SUMMON_ANIM_TICKS) {
			alpha = Mth.clamp(age / StandConfig.SUMMON_ANIM_TICKS, 0f, 1f);
		}
		if (dismissing) {
			alpha = 1f - Mth.clamp(age / animLength(StandState.ANIM_DISMISS), 0f, 1f);
		}
		if (firstPerson) {
			alpha *= 0.92f; // all but solid (user: "dont make star platinum look so see through")
			// gliding past your shoulder it fades in/out rather than filling the screen; at rest behind you it is gone
			double near = pos.add(0, 1.2, 0).distanceTo(cam);
			alpha *= Mth.clamp((float) (near - 1.25) / 0.7f, 0f, 1f);
		}
		if (alpha <= 0.02f) {
			return;
		}

		// ---- pose the body
		PlayerModel<Player> m = model;
		resetPose(m);
		float lookYaw = Mth.clamp(Mth.wrapDegrees(headYaw - sm.yaw), -70f, 70f) * Mth.DEG_TO_RAD;
		float lookPitch = Mth.clamp(pitch, -60f, 60f) * Mth.DEG_TO_RAD;
		JointedLimbs.begin();
		try {
			StandPoses.apply(m, pp, lookPitch, lookYaw);
			copyLayers(m);
			drawBody(pose, buffers, m, s, type, p, sm, pos, pp, speedLean, age, t, alpha, cam);
		} finally {
			JointedLimbs.end();
		}
		int light = light(p, pos.add(0, 1.2, 0));
		if (s.grabbedBlock != 0) {
			renderHeldBlock(pose, buffers, p, partial, cam, s.grabbedBlock, light);
		}
		renderText(pose, buffers, p, s, pos, age, t, cam);
	}

	/** The figure, its rush arms, its glow shell and Star Finger, in the pose set on {@code m}. */
	private static void drawBody(PoseStack pose, MultiBufferSource buffers, PlayerModel<Player> m, StandState s, StandType type, Player p, Smooth sm,
			Vec3 pos, float[] pp, float speedLean, float age, float t, float alpha, Vec3 cam) {

		int light = light(p, pos.add(0, 1.2, 0));
		int a = Mth.clamp((int) (alpha * 255f), 0, 255);
		pose.pushPose();
		pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		pose.mulPose(Axis.YP.rotationDegrees(180f - sm.yaw + pp[StandPoses.TWIST] * Mth.RAD_TO_DEG));
		// the whole figure leans about its waist
		pose.translate(0f, WAIST, 0f);
		pose.mulPose(Axis.XP.rotation(-(pp[StandPoses.LEAN] + speedLean)));
		pose.mulPose(Axis.ZP.rotation(pp[StandPoses.ROLL]));
		pose.translate(0f, -WAIST, 0f);
		pose.scale(-SCALE, -SCALE, SCALE);
		pose.translate(0f, -1.501f, 0f);
		VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucent(type.texture()));
		m.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY, (a << 24) | 0xFFFFFF);
		// the rush's blurred extra arms
		if (s.rushing) {
			renderRushArms(pose, body, m, t, light, a);
		}
		if (s.animId == StandState.ANIM_DASH && age >= 0f && age < animLength(StandState.ANIM_DASH)) {
			// Stand Dash: after-images streaming off it
			for (int i = 1; i <= 3; i++) {
				pose.pushPose();
				// straight back along the way it is going, whatever its lean
				float back = 9f * i / 16f, lean = pp[StandPoses.LEAN] + speedLean;
				pose.translate(0f, Mth.sin(lean) * back, Mth.cos(lean) * back);
				m.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY, ((int) (a * (0.4f - 0.1f * i)) << 24) | 0xFFFFFF);
				pose.popPose();
			}
		}
		// the faint purple glow shell
		pose.pushPose();
		pose.scale(1.06f, 1.04f, 1.06f);
		VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(type.texture()));
		int ga = (int) (a * (0.16f + 0.05f * Mth.sin(t * 0.2f)));
		m.renderToBuffer(pose, glow, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, (ga << 24) | (type.colour() & 0xFFFFFF));
		pose.popPose();
		boolean finger = fingerOut(s, age);
		Vector3f hand = finger ? rightHand(pose, m) : null;
		pose.popPose();
		if (finger) {
			renderFinger(pose, buffers, s, sm, hand, age, light, a, cam);
		}
	}

	/** Where Star Platinum's right fist is right now, in the frame of the outer pose: down the arm, through the bent elbow. */
	private static Vector3f rightHand(PoseStack pose, PlayerModel<Player> m) {
		pose.pushPose();
		m.rightArm.translateAndRotate(pose);
		Quaternionf elbow = JointedLimbs.joint(JointedLimbs.RIGHT_ARM);
		float jy = JointedLimbs.jointY(JointedLimbs.RIGHT_ARM) / 16f;
		pose.translate(-1f / 16f, jy, 0f);
		if (elbow != null) {
			pose.mulPose(elbow);
		}
		pose.translate(0f, 10.5f / 16f - jy, 0f);
		Vector3f out = pose.last().pose().transformPosition(new Vector3f());
		pose.popPose();
		return out;
	}

	/** Star Finger: the fingers shoot from the fist to the synced point, hold there, trembling, then draw back in. */
	private static void renderFinger(PoseStack pose, MultiBufferSource buffers, StandState s, Smooth sm, Vector3f hand, float age, int light, int a, Vec3 cam) {
		Vector3f tip = pose.last().pose().transformPosition((float) (s.fingerEnd.get(0) - cam.x), (float) (s.fingerEnd.get(1) - cam.y),
				(float) (s.fingerEnd.get(2) - cam.z), new Vector3f());
		float out = Mth.clamp(age / 3f, 0f, 1f);
		out = 1f - (1f - out) * (1f - out) * (1f - out);
		float back = Mth.clamp((age - 10f) / 4f, 0f, 1f);
		float extend = out * (1f - back * back);
		float whip = (1f - Mth.clamp(age / 4f, 0f, 1f)) + back * 0.6f;
		if (age >= 3f && sm.fingerFlash != s.animStart) {
			// it lands: a small flash on the fingertip
			sm.fingerFlash = s.animStart;
			StandFx.local(StandFxPayload.IMPACT, new Vec3(s.fingerEnd.get(0), s.fingerEnd.get(1), s.fingerEnd.get(2)), Vec3.ZERO, 0.6f);
		}
		// the fingers sit side by side, level across the hand
		Vector3f along = new Vector3f(tip).sub(hand);
		Vector3f side = new Vector3f(along).cross(pose.last().pose().transformDirection(new Vector3f(0, 1, 0))).normalize();
		if (!side.isFinite()) {
			side.set(1, 0, 0);
		}
		StandFinger.draw(buffers, hand, tip, side, extend, whip, Math.max(light, LightTexture.pack(10, 10)), a);
	}

	/** A steady pseudo-random 0..1 from an int (the same arm keeps its spot all through one punch). */
	private static float hash(int n) {
		n = (n << 13) ^ n;
		n = n * (n * n * 15731 + 789221) + 1376312589;
		return (n & 0x7fffffff) / (float) 0x7fffffff;
	}

	private static Vec3 dir(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	private static void resetPose(PlayerModel<Player> m) {
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg, m.leftSleeve, m.rightSleeve,
				m.leftPants, m.rightPants, m.jacket }) {
			part.resetPose();
			part.visible = true;
		}
		m.crouching = false;
		m.young = false;
	}

	/** The overlay layers follow their limbs (PlayerModel#setupAnim does this; it is never called here). */
	private static void copyLayers(PlayerModel<Player> m) {
		m.hat.copyFrom(m.head);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
	}

	/** How many barrage arms are out at once, and how long each one lives (ticks). */
	private static final int BARRAGE_ARMS = 28;
	private static final float BARRAGE_LIFE = 4.5f;

	/**
	 * v0.17.2 (user: "for his barrage i want it to look like how it looks in the anime, tons of arms spawned in at once and throwing
	 * punches forward and then disappearing"): a crowd of extra arms, each popping in somewhere round the chest and shoulders, driving
	 * a straight punch forward and fading out at full reach, then reappearing somewhere else -- staggered so there are always a
	 * dozen-plus mid-punch. Each draws a fainter after-image a little behind it for the speed blur.
	 */
	private static void renderRushArms(PoseStack pose, VertexConsumer vc, PlayerModel<Player> m, float t, int light, int a) {
		var saveR = m.rightArm.storePose();
		var saveL = m.leftArm.storePose();
		Quaternionf elbowR = JointedLimbs.joint(JointedLimbs.RIGHT_ARM), elbowL = JointedLimbs.joint(JointedLimbs.LEFT_ARM);
		for (int i = 0; i < BARRAGE_ARMS; i++) {
			float phase = t / BARRAGE_LIFE + i * 0.618f;
			int cycle = Mth.floor(phase);
			float u = phase - cycle; // 0 = popping in, 1 = gone at full reach
			int seed = i * 7919 + cycle * 104729;
			boolean right = (seed & 1) == 0;
			ModelPart arm = right ? m.rightArm : m.leftArm;
			ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
			float ox = (hash(seed) - 0.5f) * 24f; // across the chest and past both shoulders
			float oy = (hash(seed + 1) - 0.6f) * 16f; // head height down to the belt
			float aimYaw = -ox * 0.022f + (hash(seed + 2) - 0.5f) * 0.12f; // fists converge a little toward the middle
			float aimPitch = (hash(seed + 3) - 0.5f) * 0.25f;
			float reach = 4f + 20f * ease(Math.min(1f, u * 1.6f)); // a quick snap out, then hangs at full reach
			float fade = u < 0.15f ? u / 0.15f : 1f - Mth.clamp((u - 0.55f) / 0.45f, 0f, 1f);
			// each one comes out of a folded elbow and locks straight at full reach
			StandPoses.joint(right ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, -1.9f * (1f - ease(Math.min(1f, u * 2f))));
			for (int ghost = 0; ghost < 2; ghost++) {
				float back = ghost * 3.5f;
				int ga = (int) (a * fade * (ghost == 0 ? 0.85f : 0.3f));
				if (ga < 4) {
					continue;
				}
				arm.loadPose(right ? saveR : saveL);
				arm.xRot = -Mth.HALF_PI + aimPitch;
				arm.yRot = aimYaw;
				arm.zRot = 0f;
				arm.x = (right ? -5f : 5f) + ox * 0.6f;
				arm.y = 2f + oy;
				arm.z = -(reach - back) + 2f;
				sleeve.copyFrom(arm);
				arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY, (ga << 24) | 0xFFFFFF);
				sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY, (ga << 24) | 0xFFFFFF);
			}
		}
		JointedLimbs.setJoint(JointedLimbs.RIGHT_ARM, elbowR);
		JointedLimbs.setJoint(JointedLimbs.LEFT_ARM, elbowL);
		m.rightArm.loadPose(saveR);
		m.leftArm.loadPose(saveL);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftSleeve.copyFrom(m.leftArm);
	}

	private static void renderHeldBlock(PoseStack pose, MultiBufferSource buffers, Player p, float partial, Vec3 cam, int blockId, int light) {
		BlockState state = Block.stateById(blockId);
		Vec3 eye = p.getEyePosition(partial);
		Vec3 at = eye.add(p.getViewVector(partial).scale(2.0)).subtract(0, 0.3, 0);
		pose.pushPose();
		pose.translate(at.x - cam.x - 0.4, at.y - cam.y - 0.4, at.z - cam.z - 0.4);
		pose.scale(0.8f, 0.8f, 0.8f);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** ゴゴゴ on a summon; floating ORA over the rush (its user's setting). */
	private static void renderText(PoseStack pose, MultiBufferSource buffers, Player p, StandState s, Vec3 pos, float age, float t, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		if (s.animId == StandState.ANIM_SUMMON && age < 34f) {
			float fade = 1f - Mth.clamp((age - 20f) / 14f, 0f, 1f);
			for (int i = 0; i < 3; i++) {
				Vec3 at = pos.add((i - 1) * 0.7, 2.0 + i * 0.25 + age * 0.02, 0).add(dir(p.getYRot() + 90f).scale((i - 1) * 0.6));
				drawText(pose, buffers, font, Component.literal("ゴ"), at, cam, 0.045f + 0.008f * Mth.sin(t * 0.8f + i), argb(fade, 0xB06CFF));
			}
		}
		if (s.rushing && s.oraText) {
			long slot = (long) (t / 3f);
			for (int i = 0; i < 4; i++) {
				long seed = (slot + i * 7919L) * 2654435761L;
				double ox = ((seed >> 8) & 255) / 255.0 - 0.5;
				double oy = ((seed >> 16) & 255) / 255.0;
				double oz = ((seed >> 24) & 255) / 255.0 - 0.5;
				Vec3 at = pos.add(ox * 2.2, 1.0 + oy * 1.6, oz * 2.2);
				drawText(pose, buffers, font, Component.literal("ORA").withStyle(net.minecraft.ChatFormatting.BOLD), at, cam, 0.035f,
						i % 2 == 0 ? 0xFFFFF2A0 : 0xFFE6D2FF);
			}
		}
	}

	private static int argb(float alpha, int rgb) {
		return (Mth.clamp((int) (alpha * 255f), 4, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void drawText(PoseStack pose, MultiBufferSource buffers, Font font, Component text, Vec3 at, Vec3 cam, float scale, int colour) {
		if (at.distanceToSqr(cam) < 2.4 * 2.4) {
			return; // never right in your face (your own rush, first person)
		}
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
		pose.scale(scale, -scale, scale);
		float w = font.width(text) / 2f;
		font.drawInBatch(text, -w, 0f, colour, true, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
		pose.popPose();
	}

	private static int light(Player p, Vec3 at) {
		int packed = LevelRenderer.getLightColor(p.level(), BlockPos.containing(at));
		// it always shows a little in the dark: a Stand is spirit, not flesh
		int block = Math.max(LightTexture.block(packed), 8);
		return LightTexture.pack(block, LightTexture.sky(packed));
	}
}

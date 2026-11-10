package com.projecthero.mod.client.stand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mixin.StandArmPoseInvoker;
import com.projecthero.mod.client.mixin.StandRotationsInvoker;
import com.projecthero.mod.client.render.JointedLimbs;
import com.projecthero.mod.stand.StandClips;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.network.StandFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
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
 *       1.15x a player, fully solid (v0.17.3, user: "make star platinum 100% visible").</li>
 *   <li>v0.17.2 (user: "make star platinum mirror the users pose but do it behind their back"): when no move is playing it stands
 *       directly behind its user, facing their way, and copies them limb for limb, right arm to right arm: {@link #poseMirror} runs
 *       the same animation code on a private model that the user's own renderer runs, so walking, crouching, jumping, swimming, an
 *       elytra, held items, the N pose and every power's body poses all come through without being listed.</li>
 *   <li>While it fights (any attack, the rush, Guard, the charge, a held thing, the inhale) it moves in front of its user --
 *       in first person far enough out to be seen.</li>
 *   <li>Its body moves on real elbows and knees ({@link StandClips}: keyframed clips, cross-faded every frame), and Star Finger is a
 *       stretching finger mesh ({@link StandFinger}).</li>
 *   <li>The ORA Rush draws blurred extra arm copies and (setting) floating "ORA" text; a summon throws up ゴゴゴ.</li>
 *   <li>A held block is drawn in its hand; faint violet motes drift off it (client particles: Stand users only).</li>
 * </ul>
 */
public final class StandRender {
	private static final float SCALE = 1.15f * 0.9375f;
	/** The waist, above the figure's origin (blocks): what it leans about. */
	private static final float WAIST = 0.95f;
	/** How far behind its user it stands when it copies them (blocks). */
	private static final double BEHIND = StarPlatinum.BEHIND;

	/** Per-Stand smoothing (where it floats, which way it faces). */
	private static final class Smooth {
		Vec3 pos;
		float yaw = Float.NaN;
		long lastNanos;
		/** The pose on screen, chasing the wanted one ({@link StandClips#target}); null until the first frame. */
		float[] pose;
		/** 0 = copying its user, 1 = playing a move; eased, so it blends one into the other. */
		float moved = 1f;
		/** The Star Finger whose fingertip flash has gone off (its animation's start). */
		long fingerFlash;
	}

	private static final Map<Integer, Smooth> SMOOTH = new ConcurrentHashMap<>();
	private static PlayerModel<Player> model;
	/** A second model that the user's pose is worked out on (the same code their own renderer runs). */
	private static PlayerModel<Player> mirror;

	private StandRender() {
	}

	static void reset() {
		SMOOTH.clear();
		model = null;
		mirror = null;
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Length of a one-shot animation (ticks). */
	static int animLength(int animId) {
		return StandClips.length(animId);
	}

	/** The Star Finger clip is playing. */
	private static boolean fingerOut(StandState s, float age) {
		return s.animId == StandState.ANIM_FINGER && age >= 0f && age < animLength(StandState.ANIM_FINGER) && s.fingerEnd.size() == 3;
	}

	/** It is fighting: it stands in front of its user. */
	static boolean fighting(StandState s, float age) {
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L || s.dashUntil != 0L) {
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
			mirror = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
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
			// v0.17.3: an empty-hand swing (a left-click on anything, or mining) plays a jab (StandSwing has the why)
			s = StandSwing.overlay(p, s, p.level().getGameTime());
			float age = p.level().getGameTime() - s.animStart + partial;
			// v0.17.3: no fade -- dismissed, it slides back into its user's body over the same 0.4 s it came out in
			boolean dismissing = !s.summoned && s.animId == StandState.ANIM_DISMISS && age >= 0f && age < StandConfig.SUMMON_ANIM_TICKS;
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
		// v0.17.3 Ora Chase: it is off on its own, flying along its user's look or holding its catch up by the neck
		boolean chaseShown = s.chasePos.size() == 3 && (s.chaseStart != 0L
				|| s.animId == StandState.ANIM_CHASE_THROW && age >= 0f && age < animLength(StandState.ANIM_CHASE_THROW));
		if (chaseShown) {
			Vec3 c = new Vec3(s.chasePos.get(0), s.chasePos.get(1), s.chasePos.get(2));
			if (s.chaseStart != 0L && s.chaseGrabAt == 0L) {
				// laid out flat along the look, the leading fist on the chase point
				want = c.subtract(fwdHead.scale(0.35)).subtract(0, 1.05, 0);
				wantYaw = headYaw;
			} else {
				// facing away from its user, its raised left hand round the victim's neck
				Vec3 facing = StarPlatinum.chaseFacing(p, c);
				want = c.subtract(facing.scale(0.7)).add(StarPlatinum.rightOf(facing).scale(0.45)).subtract(0, 0.8, 0);
				wantYaw = (float) (Mth.atan2(-facing.x, facing.z) * Mth.RAD_TO_DEG);
			}
		} else if (fight) {
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
			// Stand Jump: it is underneath you, throwing you up
			want = at.add(0, -1.2, 0).add(fwdBody.scale(0.15));
			wantYaw = bodyYaw;
		} else {
			// directly behind its user, hovering (v0.17.3, user: "star platinum shouldn't walk behind the player, star platinum should
			// float"): a slow bob, gliding after them; along the body when they lie flat (swimming, an elytra). In first person a step
			// to the right as well, so on its way out in front it passes your shoulder, not through your eyes.
			boolean flat = p.isVisuallySwimming() || p.isFallFlying();
			Vec3 back = flat ? p.getViewVector(partial).scale(BEHIND + 0.5) : fwdBody.scale(BEHIND);
			want = at.subtract(back).add(0, StarPlatinum.BEHIND_UP + 0.07 * Mth.sin(t * 0.12f), 0);
			if (firstPerson && !flat) {
				want = want.add(StarPlatinum.rightOf(fwdBody).scale(0.9));
			}
			wantYaw = bodyYaw;
		}
		// v0.17.3 (user: "when star platinum gets summoned make him come out of the player"): it starts inside its user and slides out
		// to its spot over 0.4 s (a dismissal runs it back in)
		float emerge = -1f;
		if (s.summoned && s.animId == StandState.ANIM_SUMMON && age < StandConfig.SUMMON_ANIM_TICKS && age > -10f) {
			// (a client a tick behind the server sees a slightly negative age: still inside its user, not already out)
			emerge = ease(Math.max(0f, age) / StandConfig.SUMMON_ANIM_TICKS);
		} else if (dismissing) {
			emerge = 1f - ease(age / StandConfig.SUMMON_ANIM_TICKS);
		}
		if (emerge >= 0f) {
			want = at.lerp(want, emerge);
		}
		Smooth sm = SMOOTH.computeIfAbsent(p.getId(), k -> new Smooth());
		long nanos = System.nanoTime();
		float dt = sm.lastNanos == 0L ? 1f : Mth.clamp((nanos - sm.lastNanos) / 5.0e7f, 0f, 3f);
		sm.lastNanos = nanos;
		if (sm.pos == null || emerge >= 0f || sm.pos.distanceToSqr(want) > 24 * 24) {
			sm.pos = want;
		} else {
			// quick: it snaps out in front to fight, and glides back behind -- in first person a little slower both ways
			// (user: "make him float in front of the user and then float back behind the user like how it happens in third person")
			float k = chaseShown ? 0.7f : fight ? (firstPerson && !fingerOut(s, age) ? 0.5f : 0.6f) : (firstPerson ? 0.12f : 0.3f);
			sm.pos = sm.pos.lerp(want, Mth.clamp(k * dt, 0f, 1f));
		}
		if (Float.isNaN(sm.yaw) || emerge >= 0f) {
			sm.yaw = wantYaw;
		}
		sm.yaw += Mth.wrapDegrees(wantYaw - sm.yaw) * Mth.clamp((chaseShown ? 0.6f : 0.35f) * dt, 0f, 1f);

		// ---- the pose: chase the wanted one, fast enough that a strike still snaps (a filter on frame time, not a tick step)
		float[] wantPose = new float[StandClips.N];
		StandClips.target(s, age, t, now, fight, wantPose);
		if (sm.pose == null) {
			sm.pose = wantPose.clone();
		}
		float follow = 1f - (float) Math.exp(-1.7f * dt);
		for (int i = 0; i < StandClips.N; i++) {
			sm.pose[i] += (wantPose[i] - sm.pose[i]) * follow;
		}
		float[] pp = sm.pose;
		// a move takes over fast; it settles back into copying its user more slowly
		boolean copying = StandClips.mirrors(s, age, now) && !chaseShown;
		sm.moved += ((copying ? 0f : 1f) - sm.moved) * (1f - (float) Math.exp(-(copying ? 0.3f : 1.1f) * dt));
		float moved = sm.moved;
		// playing a move it leans into its user's speed (copying them, it floats upright)
		Vec3 vel = p.getDeltaMovement();
		float speedLean = chaseShown ? 0f : Mth.clamp((float) vel.horizontalDistance() * 0.9f, 0f, 0.4f) * moved;
		Vec3 facing = dir(sm.yaw);
		Vec3 pos = sm.pos.add(facing.scale(pp[StandClips.FWD])).add(0, pp[StandClips.UP], 0)
				.add(StarPlatinum.rightOf(facing).scale(pp[StandClips.SIDE]));

		// v0.17.3 (user: "make star platinum 100% visible, remove any transparency from him"): always fully solid. The one thing kept
		// from the old fades: in first person a frame where the camera is inside the figure is skipped (it would fill the screen).
		// (gliding past your shoulder it would fill the screen for a few frames: within arm's length of the eye it is not drawn)
		if (firstPerson && (pos.add(0, 1.1, 0).distanceTo(cam) < 1.6 || pos.add(0, 1.9, 0).distanceTo(cam) < 1.3)) {
			return;
		}

		// ---- pose the body
		PlayerModel<Player> m = model;
		resetPose(m);
		float lookYaw = Mth.clamp(Mth.wrapDegrees(headYaw - sm.yaw), -70f, 70f) * Mth.DEG_TO_RAD;
		float lookPitch = Mth.clamp(pitch, -60f, 60f) * Mth.DEG_TO_RAD;
		JointedLimbs.begin();
		try {
			Quaternionf[] joints = new Quaternionf[4];
			if (moved < 0.999f) {
				poseMirror((AbstractClientPlayer) p, partial, bodyYaw, headYaw, pitch);
				for (int i = 0; i < 4; i++) {
					joints[i] = JointedLimbs.joint(i);
				}
				floatLegs(p, t, joints);
				JointedLimbs.begin(); // (the move's pose sets its own)
			}
			StandPoses.apply(m, pp, lookPitch, lookYaw);
			if (moved < 0.999f) {
				blendMirror(m, 1f - moved, joints);
			}
			copyLayers(m);
			PoseStack root = rootPose(p, sm, pp, speedLean, bodyYaw, partial, t, moved);
			drawBody(pose, buffers, m, s, type, p, sm, pos, root, age, t, cam, pp, now);
		} finally {
			JointedLimbs.end();
		}
		int light = light(p, pos.add(0, 1.2, 0));
		if (s.grabbedBlock != 0) {
			renderHeldBlock(pose, buffers, p, partial, cam, s.grabbedBlock, light);
		}
		renderText(pose, buffers, p, s, pos, age, t, cam);
	}

	/**
	 * The user's pose this frame, on {@link #mirror}: the same steps as {@code LivingEntityRenderer#render} and
	 * {@code PlayerRenderer#setModelProperties} (so every pose mixin on the humanoid model runs on it too, joints included). Their limbs,
	 * not a flip of them: the right arm follows the right arm.
	 */
	private static void poseMirror(AbstractClientPlayer p, float partial, float bodyYaw, float headYaw, float pitch) {
		PlayerModel<Player> m = mirror;
		for (ModelPart part : new ModelPart[] { m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg }) {
			part.resetPose();
		}
		HumanoidModel.ArmPose main = StandArmPoseInvoker.projecthero$armPose(p, InteractionHand.MAIN_HAND);
		HumanoidModel.ArmPose off = StandArmPoseInvoker.projecthero$armPose(p, InteractionHand.OFF_HAND);
		if (main.isTwoHanded()) {
			off = p.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
		}
		boolean rightHanded = p.getMainArm() == HumanoidArm.RIGHT;
		m.rightArmPose = rightHanded ? main : off;
		m.leftArmPose = rightHanded ? off : main;
		m.crouching = p.isCrouching();
		m.attackTime = p.getAttackAnim(partial);
		m.riding = p.isPassenger();
		m.young = false;
		// v0.17.3 (user: "when player walks star platinum shouldn't walk behind the player, star platinum should float"): no walk cycle
		// at all -- the arms copy everything else, the legs hang relaxed ({@link #floatLegs})
		float speed = 0f, swing = 0f;
		m.prepareMobModel(p, swing, speed, partial);
		m.setupAnim(p, swing, speed, p.tickCount + partial, Mth.wrapDegrees(headYaw - bodyYaw), pitch);
	}

	/**
	 * v0.17.3: the copied pose's legs replaced by floating ones -- relaxed, toes down, the knees bobbing a little out of step -- unless
	 * its user lies flat (swimming, an elytra) or rides, when it copies their legs as before.
	 */
	private static void floatLegs(Player p, float t, Quaternionf[] joints) {
		if (p.isVisuallySwimming() || p.isFallFlying() || p.isPassenger()) {
			return;
		}
		float[] f = StandClips.FLOAT_LEGS;
		float bob = Mth.sin(t * 0.12f);
		mirror.rightLeg.xRot = f[0] + 0.05f * bob;
		mirror.rightLeg.yRot = f[1];
		mirror.rightLeg.zRot = f[2];
		mirror.leftLeg.xRot = f[4] - 0.04f * bob;
		mirror.leftLeg.yRot = f[5];
		mirror.leftLeg.zRot = f[6];
		joints[JointedLimbs.RIGHT_LEG] = new Quaternionf().rotationX(f[3] + 0.08f * bob);
		joints[JointedLimbs.LEFT_LEG] = new Quaternionf().rotationX(f[7] + 0.06f * Mth.sin(t * 0.12f + 1.3f));
	}

	/** Eases the move's pose on {@code m} toward the copied one on {@link #mirror} ({@code w} 1 = all copied), joints included. */
	private static void blendMirror(PlayerModel<Player> m, float w, Quaternionf[] mirrorJoints) {
		blend(m.head, mirror.head, w);
		blend(m.body, mirror.body, w);
		blend(m.rightArm, mirror.rightArm, w);
		blend(m.leftArm, mirror.leftArm, w);
		blend(m.rightLeg, mirror.rightLeg, w);
		blend(m.leftLeg, mirror.leftLeg, w);
		for (int i = 0; i < 4; i++) {
			Quaternionf a = JointedLimbs.joint(i), b = mirrorJoints[i];
			if (a != null || b != null) {
				Quaternionf from = a == null ? new Quaternionf() : a;
				JointedLimbs.setJoint(i, new Quaternionf(from).slerp(b == null ? new Quaternionf() : b, w));
			}
		}
	}

	private static void blend(ModelPart to, ModelPart from, float w) {
		to.x = Mth.lerp(w, to.x, from.x);
		to.y = Mth.lerp(w, to.y, from.y);
		to.z = Mth.lerp(w, to.z, from.z);
		to.xRot = Mth.lerp(w, to.xRot, from.xRot);
		to.yRot = Mth.lerp(w, to.yRot, from.yRot);
		to.zRot = Mth.lerp(w, to.zRot, from.zRot);
	}

	/**
	 * The figure's turn and tilt about its feet: a move's (its facing, leaning about the waist) eased toward the one the user's own
	 * renderer gives them, which is what lays it flat when they swim or fly. {@code moved} 1 = all the move's.
	 */
	private static PoseStack rootPose(Player p, Smooth sm, float[] pp, float speedLean, float bodyYaw, float partial, float t, float moved) {
		PoseStack move = new PoseStack();
		move.mulPose(Axis.YP.rotationDegrees(180f - sm.yaw + pp[StandClips.TWIST] * Mth.RAD_TO_DEG));
		// the whole figure leans about its waist
		move.translate(0f, WAIST, 0f);
		move.mulPose(Axis.XP.rotation(-(pp[StandClips.LEAN] + speedLean)));
		move.mulPose(Axis.ZP.rotation(pp[StandClips.ROLL]));
		move.translate(0f, -WAIST, 0f);
		if (moved >= 0.999f || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p) instanceof LivingEntityRenderer<?, ?> renderer)) {
			return move;
		}
		PoseStack copied = new PoseStack();
		((StandRotationsInvoker) renderer).projecthero$setupRotations(p, copied, t, bodyYaw, partial, 1f);
		Matrix4f a = move.last().pose(), b = copied.last().pose();
		Quaternionf q = a.getNormalizedRotation(new Quaternionf()).slerp(b.getNormalizedRotation(new Quaternionf()), 1f - moved);
		Vector3f at = a.getTranslation(new Vector3f()).lerp(b.getTranslation(new Vector3f()), 1f - moved);
		PoseStack out = new PoseStack();
		out.translate(at.x, at.y, at.z);
		out.mulPose(q);
		return out;
	}

	/** The figure, its barrage arms and Star Finger, in the pose set on {@code m}, turned by {@code root}. */
	private static void drawBody(PoseStack pose, MultiBufferSource buffers, PlayerModel<Player> m, StandState s, StandType type, Player p, Smooth sm, Vec3 pos,
			PoseStack root, float age, float t, Vec3 cam, float[] pp, long now) {

		int light = light(p, pos.add(0, 1.2, 0));
		pose.pushPose();
		pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
		pose.last().pose().mul(root.last().pose());
		pose.last().normal().mul(root.last().normal());
		pose.scale(-SCALE, -SCALE, SCALE);
		pose.translate(0f, -1.501f, 0f);
		// v0.17.3 (user: "make star platinum 100% visible, remove any transparency from him"): one solid figure, cut-out like a
		// player's skin (its overlay layers keep their holes), writing depth like any other body -- no ghost pass, no glow shell
		RenderType solid = RenderType.entityCutoutNoCull(type.texture());
		VertexConsumer body = buffers.getBuffer(solid);
		m.renderToBuffer(pose, body, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		// the barrage's extra arms (the ORA Barrage, Ora Rush's fists and Ora Chase's one-handed barrage)
		if (s.rushing || s.dashUntil > now) {
			renderRushArms(pose, body, m, t, light, false);
		} else if (s.chaseStart != 0L && s.chaseGrabAt != 0L) {
			renderRushArms(pose, body, m, t, light, true);
		}
		boolean finger = fingerOut(s, age);
		Vector3f hand = finger ? rightHand(pose, m) : null;
		pose.popPose();
		if (finger) {
			renderFinger(pose, buffers, s, sm, hand, age, light, cam);
		}
		if (buffers instanceof MultiBufferSource.BufferSource batch) {
			batch.endBatch(solid); // drawn now, in this pass, so it sits in the depth buffer with the rest of the world
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
	private static void renderFinger(PoseStack pose, MultiBufferSource buffers, StandState s, Smooth sm, Vector3f hand, float age, int light, Vec3 cam) {
		Vector3f tip = pose.last().pose().transformPosition((float) (s.fingerEnd.get(0) - cam.x), (float) (s.fingerEnd.get(1) - cam.y),
				(float) (s.fingerEnd.get(2) - cam.z), new Vector3f());
		// v0.17.3 (user: "finger extends over the course of 1 second"): out at the server's pace (30 blocks in 20 ticks, cut short by a
		// wall), held, then drawn back in
		float len = Math.max(0.5f, new Vector3f(tip).sub(hand).length());
		float out = (float) Mth.clamp(StarPlatinum.fingerReach(age) / len, 0.0, 1.0);
		float hold = StandConfig.FINGER_EXTEND_TICKS + StandConfig.FINGER_HOLD_TICKS;
		float back = Mth.clamp((age - hold) / StandConfig.FINGER_RETRACT_TICKS, 0f, 1f);
		float extend = out * (1f - back * back);
		float whip = 0.35f * (1f - out) + back * 0.6f;
		float landAt = (float) (len / StandConfig.FINGER_RANGE * StandConfig.FINGER_EXTEND_TICKS);
		if (age >= landAt && sm.fingerFlash != s.animStart) {
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
		StandFinger.draw(buffers, hand, tip, side, extend, whip, Math.max(light, LightTexture.pack(10, 10)), 255);
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
	private static void renderRushArms(PoseStack pose, VertexConsumer vc, PlayerModel<Player> m, float t, int light, boolean oneHanded) {
		var saveR = m.rightArm.storePose();
		var saveL = m.leftArm.storePose();
		Quaternionf elbowR = JointedLimbs.joint(JointedLimbs.RIGHT_ARM), elbowL = JointedLimbs.joint(JointedLimbs.LEFT_ARM);
		int arms = oneHanded ? BARRAGE_ARMS / 3 : BARRAGE_ARMS;
		for (int i = 0; i < arms; i++) {
			float phase = t / BARRAGE_LIFE + i * 0.618f;
			int cycle = Mth.floor(phase);
			float u = phase - cycle; // 0 = popping in, 1 = gone at full reach
			int seed = i * 7919 + cycle * 104729;
			boolean right = oneHanded || (seed & 1) == 0;
			ModelPart arm = right ? m.rightArm : m.leftArm;
			ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
			// across the chest and past both shoulders (one-handed: round the right shoulder, aimed up at the held neck)
			float ox = oneHanded ? -4f + (hash(seed) - 0.5f) * 8f : (hash(seed) - 0.5f) * 24f;
			float oy = oneHanded ? (hash(seed + 1) - 0.7f) * 8f : (hash(seed + 1) - 0.6f) * 16f; // head height down to the belt
			float aimYaw = -ox * 0.022f + (hash(seed + 2) - 0.5f) * 0.12f; // fists converge a little toward the middle
			float aimPitch = (hash(seed + 3) - 0.5f) * 0.25f - (oneHanded ? 0.35f : 0f);
			float reach = 4f + 20f * ease(Math.min(1f, u * 1.6f)); // a quick snap out, then hangs at full reach
			// v0.17.3: solid -- each arm pops in, punches and pops out (no fading, no after-image)
			if (u < 0.08f || u > 0.8f) {
				continue;
			}
			// each one comes out of a folded elbow and locks straight at full reach
			StandPoses.joint(right ? JointedLimbs.RIGHT_ARM : JointedLimbs.LEFT_ARM, -1.9f * (1f - ease(Math.min(1f, u * 2f))));
			{
				float back = 0f;
				arm.loadPose(right ? saveR : saveL);
				arm.xRot = -Mth.HALF_PI + aimPitch;
				arm.yRot = aimYaw;
				arm.zRot = 0f;
				arm.x = (right ? -5f : 5f) + ox * 0.6f;
				// never across its own face: a fist in the middle stays at chest height
				arm.y = Math.abs(arm.x) < 9f ? Math.max(5f, 2f + oy) : 2f + oy;
				arm.z = -(reach - back) + 2f;
				sleeve.copyFrom(arm);
				arm.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
				sleeve.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
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

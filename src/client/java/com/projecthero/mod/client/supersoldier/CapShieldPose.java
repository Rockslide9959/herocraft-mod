package com.projecthero.mod.client.supersoldier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.supersoldier.CapShieldSlot;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: how the Captain America shields sit and move on a body -- strapped to the forearm, raised to block, thrown, caught
 * and bashed with (the stepped-dish model is {@link CapShieldModel}).
 *
 * <h2>Strapped, not gripped</h2>
 * In third person the shield is drawn in the <b>arm's own frame</b> ({@link #renderStrapped}): its straps rest on the
 * forearm 6 px down the arm, its painted face out from the body and turned toward the front. Raised or swung it turns on the
 * forearm (the arm's own rotation undone) until its face points straight ahead, so a single-bone arm still shows a square block.
 *
 * <h2>Arm poses ({@link #applyPose}, from {@code HumanoidModel.setupAnim} TAIL)</h2>
 * <ul>
 *   <li><b>Block</b> -- the forearm comes across the chest, the outer side (so the shield face) forward, the shoulder rolled
 *       2.5 px forward and 1 px down so the arm clears the chest. Raised over {@link #RAISE_TICKS} and lowered over {@link #LOWER_TICKS}
 *       (eased), slerped from the plain carry pose, not snapped like vanilla's.</li>
 *   <li><b>Bash</b> -- a main-hand attack swing with the shield: a fast shove of the block pose straight out (3 px further
 *       forward), peaking at a quarter of the swing, then back.</li>
 *   <li><b>Throw</b> -- read off the thrown {@link SoldierShieldEntity} appearing near its thrower ({@link #tick}): the arm
 *       snaps across the chest (the wind-up, the torso turned in), whips out forward-outward (the release, the torso
 *       turned through) and settles back.</li>
 *   <li><b>Reach and catch</b> -- while his shield flies home the empty arm comes up toward it, harder in the last blocks; when
 *       it reaches him the arm takes the hit (pushed back, then settles to the carry pose) with the shield back on it.</li>
 * </ul>
 * First person has its own placement ({@link #renderFirstPerson}): low at the side of the view with the strapped back
 * toward the eye, swung up into the middle to block, shoved forward to bash, dropping in from above on a catch.
 *
 * <p>Client state is keyed by entity id (ints only, cleared on disconnect / world change) -- no worlds or entities held.
 */
public final class CapShieldPose {
	/** Block raise / lower time (ticks). */
	public static final float RAISE_TICKS = 3.5f;
	public static final float LOWER_TICKS = 4.5f;
	/** Shield scale on the body (1 = 16 px across). */
	public static final float BODY_SCALE = 0.85f;
	/** Throw timeline (ticks): wind-up reached, release reached, back at rest. */
	private static final float T_WIND = 1.2f, T_RELEASE = 3.5f, T_SETTLE = 9.0f;
	/** Catch recoil timeline (ticks). */
	private static final float C_HIT = 2.0f, C_SETTLE = 7.0f;
	/** A returning shield closer than this starts the reach (blocks). */
	private static final double REACH_FROM = 8.0;

	private static long ticks;
	private static int levelId;
	/** entity id -> {previous, current} block blend. */
	private static final Map<Integer, float[]> BLOCK = new HashMap<>();
	/** thrower id -> {tick thrown, arm (1 right / -1 left)}. */
	private static final Map<Integer, long[]> THROWN = new HashMap<>();
	/** catcher id -> {tick caught, arm}. */
	private static final Map<Integer, long[]> CAUGHT = new HashMap<>();
	/** shield entity id -> {owner id, arm, returning (0/1), last distance to the owner * 100}. */
	private static final Map<Integer, long[]> FLYING = new HashMap<>();

	private CapShieldPose() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(CapShieldPose::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	public static void clear() {
		BLOCK.clear();
		THROWN.clear();
		CAUGHT.clear();
		FLYING.clear();
	}

	// ------------------------------------------------------------------------------------------------ state

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		int id = System.identityHashCode(level);
		if (id != levelId) {
			levelId = id;
			clear();
		}
		if (mc.isPaused()) {
			return;
		}
		ticks++;
		// block blends: every player in view
		for (Player p : level.players()) {
			float[] b = BLOCK.computeIfAbsent(p.getId(), k -> new float[2]);
			b[0] = b[1];
			boolean up = raised(p);
			b[1] = up ? Math.min(1f, b[1] + 1f / RAISE_TICKS) : Math.max(0f, b[1] - 1f / LOWER_TICKS);
		}
		BLOCK.keySet().removeIf(k -> !(level.getEntity(k) instanceof Player));
		// thrown shields: a new one near its thrower starts the throw; one that vanishes near him while coming home is a catch
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof SoldierShieldEntity s)) {
				continue;
			}
			seen.add(s.getId());
			Entity owner = level.getEntity(s.ownerEntityId());
			int arm = armSign(owner, s.thrownFromOffHand());
			long[] f = FLYING.get(s.getId());
			double dist = owner == null ? 99 : s.position().distanceTo(owner.getEyePosition().add(0, -0.4, 0));
			if (f == null) {
				FLYING.put(s.getId(), new long[] { s.ownerEntityId(), arm, s.isReturning() ? 1 : 0, Math.round(dist * 100) });
				if (owner != null && s.tickCount < 4 && dist < 3.0) {
					THROWN.put(owner.getId(), new long[] { ticks - Math.max(0, s.tickCount - 1), arm });
				}
			} else {
				f[1] = arm;
				f[2] = s.isReturning() ? 1 : 0;
				f[3] = Math.round(dist * 100);
			}
		}
		for (Iterator<Map.Entry<Integer, long[]>> it = FLYING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, long[]> en = it.next();
			if (seen.contains(en.getKey())) {
				continue;
			}
			long[] f = en.getValue();
			if (f[2] == 1 && f[3] < 520) {
				CAUGHT.put((int) f[0], new long[] { ticks, f[1] });
			}
			it.remove();
		}
		THROWN.values().removeIf(v -> ticks - v[0] > 20);
		CAUGHT.values().removeIf(v -> ticks - v[0] > 20);
	}

	/** 1 = the right arm, -1 = the left, for a hand of this entity. */
	private static int armSign(Entity owner, boolean offHand) {
		HumanoidArm main = owner instanceof Player p ? p.getMainArm() : HumanoidArm.RIGHT;
		HumanoidArm arm = offHand ? main.getOpposite() : main;
		return arm == HumanoidArm.RIGHT ? 1 : -1;
	}

	private static HumanoidArm armOf(LivingEntity e, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? e.getMainArm() : e.getMainArm().getOpposite();
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** The smoothed block amount 0..1 (eased) of this entity's raised Captain America shield. */
	public static float block(LivingEntity e, float partial) {
		float[] b = BLOCK.get(e.getId());
		float v;
		if (b == null) {
			v = raised(e) ? 1f : 0f;
		} else {
			v = Mth.lerp(partial, b[0], b[1]);
		}
		return smooth(v);
	}

	/**
	 * A Captain America shield raised right now: one in use (blocking) in a hand, or (v0.17.5) the Battle Suit's slotted shield raised
	 * with right-click ({@link CapShieldSlot#isGuarding}; it is drawn on the off-hand forearm, so the off-hand arm takes the block pose).
	 */
	private static boolean raised(LivingEntity e) {
		return (e.isUsingItem() && CapShieldModel.isCapShield(e.getUseItem())) || (e instanceof Player p && CapShieldSlot.isGuarding(p));
	}

	private static float since(long[] at, float partial) {
		return at == null ? 999f : (ticks - at[0]) + partial;
	}

	// ------------------------------------------------------------------------------------------------ third-person arm poses

	/**
	 * The arm poses (TAIL of {@code HumanoidModel.setupAnim}, players only). A no-op unless a shield is raised, swung, thrown or
	 * on its way back.
	 */
	public static void applyPose(LivingEntity e, HumanoidModel<?> m, float limbSwing, float limbAmount, float age) {
		if (!(e instanceof Player p)) {
			return;
		}
		if (SuperSoldierPose.active(p)) {
			return; // v0.17.7: a Super Soldier move clip owns the arms (the Storm's own throw and catch included)
		}
		float pt = partial();
		// throw / reach / catch first: they own the throwing arm while they run
		long[] th = THROWN.get(p.getId());
		float tt = since(th, pt);
		if (th != null && tt < T_SETTLE) {
			throwPose(p, m, (int) th[1], tt, limbSwing, limbAmount, age);
			return;
		}
		long[] ca = CAUGHT.get(p.getId());
		float ct = since(ca, pt);
		if (ca != null && ct < C_SETTLE) {
			catchPose(m, (int) ca[1], ct, limbSwing, limbAmount, age, p.isCrouching());
			return;
		}
		SoldierShieldEntity home = returning(p);
		if (home != null) {
			reachPose(p, m, home, pt, limbSwing, limbAmount, age);
		}
		// raised to block
		float b = block(p, pt);
		if (b > 0.001f) {
			InteractionHand hand = p.isUsingItem() ? p.getUsedItemHand() : lastBlockHand(p);
			int side = armOf(p, hand) == HumanoidArm.RIGHT ? 1 : -1;
			Quaternionf carry = carry(side, limbSwing, limbAmount, age, p.isCrouching());
			Quaternionf q = carry.slerp(blockQ(side), b, new Quaternionf());
			ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
			set(arm, q);
			arm.z += -2.5f * b;
			arm.y += 1.0f * b;
			return;
		}
		// a bash: the main hand swinging with a shield in it
		ItemStack main = p.getMainHandItem();
		float a = p.getAttackAnim(pt);
		if (a > 0f && p.swingingArm == InteractionHand.MAIN_HAND && CapShieldModel.isCapShield(main)) {
			int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
			float k = bashCurve(a);
			Quaternionf q = carry(side, limbSwing, limbAmount, age, p.isCrouching()).slerp(blockQ(side), k, new Quaternionf());
			ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
			set(arm, q);
			arm.z += -5.5f * k;
			arm.y += 1.0f * k;
		}
	}

	/** Which hand was raising a shield (while it lowers, after use stopped): the one holding a Captain America shield. */
	private static InteractionHand lastBlockHand(LivingEntity e) {
		return CapShieldModel.isCapShield(e.getMainHandItem()) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	/** Bash shove: up fast to a quarter of the swing, back slower. */
	public static float bashCurve(float a) {
		return a < 0.25f ? smooth(a / 0.25f) : 1f - smooth((a - 0.25f) / 0.75f);
	}

	private static SoldierShieldEntity returning(Player p) {
		for (Map.Entry<Integer, long[]> en : FLYING.entrySet()) {
			long[] f = en.getValue();
			if (f[0] == p.getId() && f[2] == 1 && f[3] < REACH_FROM * 100) {
				Entity e = p.level().getEntity(en.getKey());
				if (e instanceof SoldierShieldEntity s) {
					return s;
				}
			}
		}
		return null;
	}

	private static void reachPose(Player p, HumanoidModel<?> m, SoldierShieldEntity s, float pt, float limbSwing, float limbAmount, float age) {
		long[] f = FLYING.get(s.getId());
		int side = f == null ? 1 : (int) f[1];
		ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
		boolean mainIsThisArm = (side > 0) == (p.getMainArm() == HumanoidArm.RIGHT);
		if (!(mainIsThisArm ? p.getMainHandItem() : p.getOffhandItem()).isEmpty()) {
			return; // that hand is busy: no reach
		}
		Vec3 sp = s.getPosition(pt);
		Vec3 shoulder = p.getPosition(pt).add(0, 1.4, 0);
		double dist = sp.distanceTo(shoulder);
		float k = (float) Mth.clamp((REACH_FROM - dist) / (REACH_FROM - 2.0), 0.0, 1.0);
		k = k * k * (3 - 2 * k);
		// the shield's direction in the model's space (model: -Z forward, +X the body's left, +Y down)
		Vec3 w = sp.subtract(shoulder).normalize();
		float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		double fwd = -Math.sin(yaw) * w.x + Math.cos(yaw) * w.z; // along the body's facing
		double left = Math.cos(yaw) * w.x + Math.sin(yaw) * w.z; // to the body's left
		Vector3f dir = new Vector3f((float) left, (float) -w.y, (float) -fwd);
		// reach a little higher than the line to it, and never back behind the shoulder
		dir.y -= 0.5f;
		if (dir.z > -0.2f) {
			dir.z = -0.2f;
		}
		dir.normalize();
		Quaternionf reach = aim(dir, new Vector3f(side, 0, 0));
		Quaternionf carry = carry(side, limbSwing, limbAmount, age, p.isCrouching());
		set(arm, carry.slerp(reach, k, new Quaternionf()));
	}

	private static void throwPose(Player p, HumanoidModel<?> m, int side, float t, float limbSwing, float limbAmount, float age) {
		ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
		Quaternionf carry = carry(side, limbSwing, limbAmount, age, p.isCrouching());
		// wind-up: across the chest, the shield face (outer side) forward, low
		Quaternionf wind = aim(new Vector3f(0.80f * side, 0.05f, -0.60f), new Vector3f(0, 0, -1), side);
		// release: out forward and to his side, flat (outer side up) like the end of a backhand disc throw
		Quaternionf release = aim(new Vector3f(-0.32f * side, -0.22f, -0.92f), new Vector3f(0, -1, 0), side);
		Quaternionf q;
		float twist;
		if (t < T_WIND) {
			float k = smooth(t / T_WIND);
			q = carry.slerp(wind, k, new Quaternionf());
			twist = 0.45f * k;
		} else if (t < T_RELEASE) {
			float k = 1f - (float) Math.pow(1f - (t - T_WIND) / (T_RELEASE - T_WIND), 3); // ease-out: the whip
			q = wind.slerp(release, k, new Quaternionf());
			twist = Mth.lerp(k, 0.45f, -0.40f);
		} else {
			float k = smooth((t - T_RELEASE) / (T_SETTLE - T_RELEASE));
			q = release.slerp(carry, k, new Quaternionf());
			twist = Mth.lerp(k, -0.40f, 0f);
		}
		// the torso turns with the throw (in toward the wind-up, through on the release)
		turnBody(m, twist * side);
		set(arm, q);
	}

	private static void catchPose(HumanoidModel<?> m, int side, float t, float limbSwing, float limbAmount, float age, boolean crouch) {
		ModelPart arm = side > 0 ? m.rightArm : m.leftArm;
		Quaternionf carry = carry(side, limbSwing, limbAmount, age, crouch);
		// the catch: arm up in front, the shield face out to meet it
		Quaternionf up = aim(new Vector3f(0.25f * side, -0.75f, -0.62f), new Vector3f(0, 0, -1), side);
		// the hit drives it back and down
		Quaternionf hit = aim(new Vector3f(0.30f * side, -0.25f, -0.92f), new Vector3f(0, 0, -1), side);
		Quaternionf q;
		if (t < C_HIT) {
			q = up.slerp(hit, 1f - (1f - t / C_HIT) * (1f - t / C_HIT), new Quaternionf());
			arm.z += 1.5f * (t / C_HIT);
		} else {
			float k = smooth((t - C_HIT) / (C_SETTLE - C_HIT));
			q = hit.slerp(carry, k, new Quaternionf());
			arm.z += 1.5f * (1f - k);
		}
		set(arm, q);
	}

	/** Turns the torso by {@code yRot} and moves both shoulder pivots with it (as vanilla's attack swing does). */
	private static void turnBody(HumanoidModel<?> m, float yRot) {
		m.body.yRot = yRot;
		m.rightArm.z = Mth.sin(yRot) * 5.0f;
		m.rightArm.x = -Mth.cos(yRot) * 5.0f;
		m.leftArm.z = -Mth.sin(yRot) * 5.0f;
		m.leftArm.x = Mth.cos(yRot) * 5.0f;
	}

	/** Vanilla's arm with an item in it (walk swing, the item lift, crouch and idle bob), as a rotation. */
	private static Quaternionf carry(int side, float limbSwing, float limbAmount, float age, boolean crouch) {
		float x = Mth.cos(limbSwing * 0.6662f + (side > 0 ? Mth.PI : 0f)) * limbAmount;
		x = x * 0.5f - Mth.PI / 10f;
		if (crouch) {
			x += 0.4f;
		}
		x += Mth.sin(age * 0.067f) * 0.05f * side;
		float z = (Mth.cos(age * 0.09f) * 0.05f + 0.05f) * side;
		return new Quaternionf().rotationZYX(z, 0f, x);
	}

	/** The block pose for the right (side 1) or left (-1) arm: forearm across the chest, the shield face forward. */
	private static Quaternionf blockQ(int side) {
		return aim(new Vector3f(0.93f * side, -0.12f, -0.26f), new Vector3f(0, 0, -1), side);
	}

	/**
	 * An arm rotation that points the arm (its local +Y, shoulder to hand) along {@code dir} and turns its <b>outer</b> side
	 * (-X for the right arm, +X for the left) as near to {@code face} as it can (both model space).
	 */
	private static Quaternionf aim(Vector3f dir, Vector3f face, int side) {
		Vector3f y = new Vector3f(dir).normalize();
		Vector3f f = new Vector3f(face).sub(new Vector3f(y).mul(face.dot(y))).normalize();
		// the outer side is -X for the right arm (side 1), +X for the left: local +X maps to -side * f
		Vector3f x = new Vector3f(f).mul(-side);
		Vector3f z = new Vector3f(x).cross(y);
		Matrix3f mat = new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z);
		return new Quaternionf().setFromNormalized(mat);
	}

	/** As {@link #aim(Vector3f, Vector3f, int)} but with local +X turned toward {@code xDir} (the reach). */
	private static Quaternionf aim(Vector3f dir, Vector3f xDir) {
		Vector3f y = new Vector3f(dir).normalize();
		Vector3f x = new Vector3f(xDir).sub(new Vector3f(y).mul(xDir.dot(y))).normalize();
		Vector3f z = new Vector3f(x).cross(y);
		return new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z));
	}

	/** Writes a rotation into a part as the Z-Y-X Euler angles {@code ModelPart} composes ({@code rotationZYX(z, y, x)}). */
	private static void set(ModelPart part, Quaternionf q) {
		Vector3f c0 = q.transform(new Vector3f(1, 0, 0));
		Vector3f c1 = q.transform(new Vector3f(0, 1, 0));
		Vector3f c2 = q.transform(new Vector3f(0, 0, 1));
		part.yRot = (float) Math.asin(Mth.clamp(-c0.z, -1f, 1f));
		part.xRot = (float) Math.atan2(c1.z, c2.z);
		part.zRot = (float) Math.atan2(c0.y, c0.x);
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	// ------------------------------------------------------------------------------------------------ drawing

	/** At rest the face turns this far from straight out toward the front (degrees), so it reads from the front too. */
	private static final float REST_FORWARD = 55f;

	/**
	 * Third person: the shield strapped to the forearm of {@code arm}. {@code pose} is already in the arm's frame
	 * ({@code ArmedModel.translateToHand}: the shoulder pivot, +Y down the arm, slim arms shifted half a pixel in).
	 *
	 * <p>At rest its face points out from the body and {@link #REST_FORWARD} toward the front, star up. Raised (block) or
	 * swung (bash) it turns on the forearm -- counter-rotating against the arm's own rotation -- until its face points
	 * straight along the body's facing with the star up, so a single-bone arm still shows a square-on block. Either way its
	 * straps sit on the forearm's surface on the side it faces.
	 */
	public static void renderStrapped(LivingEntity e, ItemStack stack, HumanoidArm arm, ModelPart part, PoseStack pose, MultiBufferSource buffers,
			int light) {
		boolean slim = e instanceof AbstractClientPlayer cp && cp.getSkin().model() == PlayerSkin.Model.SLIM;
		float half = slim ? 1.5f : 2.0f;
		int side = arm == HumanoidArm.RIGHT ? -1 : 1; // the outer side's x sign in the arm frame
		// rest: out from the body, turned toward the front; star toward the shoulder (up when the arm hangs)
		float ra = REST_FORWARD * Mth.DEG_TO_RAD;
		Quaternionf rest = basis(new Vector3f(side * Mth.cos(ra), 0f, -Mth.sin(ra)), new Vector3f(0f, -1f, 0f));
		// raised: the body's forward and up, brought into the arm's frame (undo the arm's own rotation)
		float w = raise(e, arm);
		Quaternionf q = rest;
		if (w > 0.001f) {
			Quaternionf armQ = new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
			Quaternionf inv = armQ.conjugate(new Quaternionf());
			Vector3f fwd = inv.transform(new Vector3f(0f, 0f, -1f));
			Vector3f up = inv.transform(new Vector3f(0f, -1f, 0f));
			q = rest.slerp(basis(fwd, up), w, new Quaternionf());
		}
		Vector3f face = q.transform(new Vector3f(0f, 0f, 1f));
		// straps on the arm's surface on the side the face points: the arm's axis (x -1 right / +1 left, z 0) plus its half width
		float reach = half * (Math.abs(face.x) + Math.abs(face.z) > 1.2f ? 1.2f : 1.0f);
		pose.pushPose();
		pose.translate((side + face.x * reach) / 16f, (6.0f + face.y * reach) / 16f, (face.z * reach) / 16f);
		pose.mulPose(q);
		pose.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE);
		pose.translate(0f, 0f, -CapShieldModel.CONTACT_Z / 16f);
		CapShieldModel.render(pose, buffers, CapShieldModel.texture(stack), stack.hasFoil(), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** Shield rotation with its front (+Z) along {@code front} and star (+Y) as near {@code up} as it can be. */
	private static Quaternionf basis(Vector3f front, Vector3f up) {
		Vector3f z = new Vector3f(front).normalize();
		Vector3f y = new Vector3f(up).sub(new Vector3f(z).mul(up.dot(z)));
		if (y.lengthSquared() < 1.0e-4f) {
			y.set(0f, 0f, -1f);
		}
		y.normalize();
		Vector3f x = new Vector3f(y).cross(z);
		return new Quaternionf().setFromNormalized(new Matrix3f(x.x, x.y, x.z, y.x, y.y, y.z, z.x, z.y, z.z));
	}

	/** How far this arm's shield is turned to the front: the block blend, or the bash shove on a main-hand swing. */
	private static float raise(LivingEntity e, HumanoidArm arm) {
		float pt = partial();
		float w = 0f;
		boolean blockingThis = e.isUsingItem() ? armOf(e, e.getUsedItemHand()) == arm : armOf(e, lastBlockHand(e)) == arm;
		if (blockingThis) {
			w = block(e, pt);
		}
		if (e.swingingArm == InteractionHand.MAIN_HAND && e.getMainArm() == arm && CapShieldModel.isCapShield(e.getMainHandItem())) {
			float at = e.getAttackAnim(pt);
			if (at > 0f) {
				w = Math.max(w, bashCurve(at));
			}
		}
		// v0.17.7: the Shield Bash squares the lead arm's shield to the front
		w = Math.max(w, SuperSoldierPose.shieldBashWeight(e, arm, pt));
		// the catch: the shield meets the incoming throw face on, then turns back to rest
		long[] ca = CAUGHT.get(e.getId());
		float ct = since(ca, pt);
		if (ca != null && ct < C_SETTLE && (ca[1] == 1) == (arm == HumanoidArm.RIGHT)) {
			w = Math.max(w, ct < C_HIT ? 1f : 1f - smooth((ct - C_HIT) / (C_SETTLE - C_HIT)));
		}
		return w;
	}

	/**
	 * First person, in place of the vanilla item: {@code side} 1 for the right of the view, -1 the left. The pose stack is the
	 * hand's view space (camera looking down -Z).
	 */
	public static void renderFirstPerson(AbstractClientPlayer p, InteractionHand hand, ItemStack stack, float partial, float swing, float equip,
			PoseStack pose, MultiBufferSource buffers, int light) {
		HumanoidArm arm = armOf(p, hand);
		float side = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float b = p.isUsingItem() && p.getUsedItemHand() == hand ? block(p, partial)
				: (!p.isUsingItem() ? block(p, partial) : 0f);
		if (lastBlockHand(p) != hand && !(p.isUsingItem() && p.getUsedItemHand() == hand)) {
			b = 0f;
		}
		float bash = hand == InteractionHand.MAIN_HAND && swing > 0f ? bashCurve(swing) : 0f;
		// v0.17.7: the Shield Bash (Shift+X) shoves the shield out in front for the whole charge
		bash = Math.max(bash, SuperSoldierPose.shieldBashWeight(p, arm, partial));
		long[] ca = CAUGHT.get(p.getId());
		float ct = since(ca, partial);
		boolean catching = ca != null && ct < 8f && (int) ca[1] == (int) side;
		if (catching) {
			equip = 0f;
		}
		pose.pushPose();
		pose.translate(side * 0.56f, -0.52f + equip * -0.6f, -0.72f);
		// carry: low at the side, strapped back toward the eye, the face turned out
		Vector3f tCarry = new Vector3f(side * -0.02f, 0.0f, 0.0f);
		Quaternionf rCarry = new Quaternionf().rotationY(side * 104f * Mth.DEG_TO_RAD).rotateX(-12f * Mth.DEG_TO_RAD).rotateZ(side * 6f * Mth.DEG_TO_RAD);
		// block: up into the middle of the lower view, square on, the back toward the eye
		Vector3f tBlock = new Vector3f(-side * 0.50f, 0.10f, -0.10f);
		Quaternionf rBlock = new Quaternionf().rotationY(side * 176f * Mth.DEG_TO_RAD).rotateX(4f * Mth.DEG_TO_RAD);
		float k = Math.max(b, bash);
		Vector3f t = new Vector3f(tCarry).lerp(tBlock, k);
		Quaternionf r = new Quaternionf(rCarry).slerp(rBlock, k);
		// the bash shoves it out
		t.z -= 0.30f * bash;
		t.y += 0.04f * bash;
		if (catching) {
			float c = 1f - smooth(ct / 6f);
			t.y += 0.38f * c;
			t.x += -side * 0.08f * c;
			r.rotateX(-28f * c * Mth.DEG_TO_RAD);
		}
		pose.translate(t.x, t.y, t.z);
		pose.mulPose(r);
		pose.scale(0.85f, 0.85f, 0.85f);
		pose.translate(0f, 0f, -CapShieldModel.MID_Z / 16f);
		CapShieldModel.render(pose, buffers, CapShieldModel.texture(stack), stack.hasFoil(), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/**
	 * First person, the empty main hand during a throw (sweep across and out) or while the shield flies home (raised to meet
	 * it): extra transforms on top of vanilla's arm. Returns false when nothing applies.
	 */
	public static boolean firstPersonEmptyArm(AbstractClientPlayer p, float partial, PoseStack pose) {
		if (SuperSoldierPose.firstPersonWeight(p, partial) > 0.001f) {
			return false; // v0.17.7: a Super Soldier move owns the hand (ItemInHandRendererSuperSoldierMixin draws it)
		}
		int main = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
		long[] th = THROWN.get(p.getId());
		float tt = since(th, partial);
		if (th != null && tt < T_SETTLE && (int) th[1] == main) {
			float x, y, rz;
			if (tt < T_WIND) {
				float k = smooth(tt / T_WIND);
				x = -0.45f * k;
				y = 0.08f * k;
				rz = 25f * k;
			} else if (tt < T_RELEASE) {
				float k = 1f - (float) Math.pow(1f - (tt - T_WIND) / (T_RELEASE - T_WIND), 3);
				x = Mth.lerp(k, -0.45f, 0.20f);
				y = Mth.lerp(k, 0.08f, 0.12f);
				rz = Mth.lerp(k, 25f, -20f);
			} else {
				float k = smooth((tt - T_RELEASE) / (T_SETTLE - T_RELEASE));
				x = Mth.lerp(k, 0.20f, 0f);
				y = Mth.lerp(k, 0.12f, 0f);
				rz = Mth.lerp(k, -20f, 0f);
			}
			pose.translate(main * x, y, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(main * rz));
			return true;
		}
		SoldierShieldEntity home = returning(p);
		if (home != null && FLYING.get(home.getId()) != null && (int) FLYING.get(home.getId())[1] == main) {
			double d = home.getPosition(partial).distanceTo(p.getEyePosition(partial));
			float k = (float) Mth.clamp((REACH_FROM - d) / (REACH_FROM - 2.0), 0.0, 1.0);
			k = smooth(k);
			pose.translate(-main * 0.12f * k, 0.30f * k, -0.10f * k);
			pose.mulPose(Axis.XP.rotationDegrees(20f * k));
			return true;
		}
		return false;
	}
}

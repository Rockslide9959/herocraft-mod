package com.projecthero.mod.client.thor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "make player lift the correct hand when calling the weapon to their hand please"): the catch. While a Mjolnir /
 * Stormbreaker flies home, its owner raises the hand it is about to land in -- the hand the returning entity syncs
 * ({@code catchHand()}, the server's {@link com.projecthero.mod.hammer.ThorWeaponHands#plan}), or, for a scripted throw
 * ({@link ThorThrown}), the hand it left over its last {@link #STASH_LEAD} ticks out. Main or off hand, mirrored for a left-handed
 * player (the arm is the hand's {@link HumanoidArm}). The arm reaches toward the incoming weapon, then lowers over a few ticks after
 * the catch. Third person through {@link #applyPose} (after {@code ThorPose}); first person through {@link #firstPersonWeight}
 * ({@code ItemInHandRendererThorMixin} raises the empty hand).
 * <p>
 * Per player entity id, client only; cleared on a world change or disconnect. Holds no entity references.
 */
public final class ThorCatchClient {
	/** A returning weapon closer than this starts the arm up; it is fully raised by {@link #FULL_DIST}. */
	private static final double RAISE_DIST = 11.0, FULL_DIST = 3.0;
	/** A scripted throw raises the arm over its last ticks out. */
	private static final int STASH_LEAD = 7;
	/** How fast the arm goes up / comes down (weight per tick). */
	private static final float UP = 0.35f, DOWN = 0.2f;

	private static final class Catch {
		HumanoidArm arm;
		boolean main;
		float prev, cur, target;
		/** Where the weapon is (null: none known -- reach up and forward). */
		Vec3 from;
	}

	private static final Map<Integer, Catch> CATCHES = new HashMap<>();
	private static int levelId;

	private ThorCatchClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(ThorCatchClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CATCHES.clear());
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			CATCHES.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		int id = System.identityHashCode(mc.level);
		if (id != levelId) {
			levelId = id;
			CATCHES.clear();
		}
		for (Catch c : CATCHES.values()) {
			c.target = 0f;
		}
		for (Entity e : mc.level.entitiesForRendering()) {
			Player owner = null;
			InteractionHand hand = null;
			if (e instanceof MjolnirEntity m) {
				hand = m.catchHand();
				owner = hand == null ? null : m.catchOwner();
			} else if (e instanceof StormbreakerEntity s) {
				hand = s.catchHand();
				owner = hand == null ? null : s.catchOwner();
			}
			if (owner == null) {
				continue;
			}
			double d = e.distanceTo(owner);
			if (d > RAISE_DIST) {
				continue;
			}
			float w = (float) Mth.clamp((RAISE_DIST - d) / (RAISE_DIST - FULL_DIST), 0.0, 1.0);
			raise(owner, hand, w, e.position());
		}
		long now = mc.level.getGameTime();
		for (Player p : mc.level.players()) {
			ThorThrown out = ThorThrown.of(p);
			if (out.isEmpty()) {
				continue;
			}
			for (InteractionHand hand : InteractionHand.values()) {
				if (out.in(hand).isEmpty()) {
					continue;
				}
				long left = out.backAt(hand) - now;
				if (left <= STASH_LEAD) {
					raise(p, hand, Mth.clamp(1f - left / (float) STASH_LEAD, 0f, 1f), null);
				}
			}
		}
		Iterator<Catch> it = CATCHES.values().iterator();
		while (it.hasNext()) {
			Catch c = it.next();
			c.prev = c.cur;
			c.cur = c.target > c.cur ? Math.min(c.target, c.cur + UP) : Math.max(c.target, c.cur - DOWN);
			if (c.cur <= 0f && c.prev <= 0f && c.target <= 0f) {
				it.remove();
			}
		}
	}

	private static void raise(Player owner, InteractionHand hand, float w, Vec3 from) {
		Catch c = CATCHES.computeIfAbsent(owner.getId(), k -> new Catch());
		boolean main = hand == InteractionHand.MAIN_HAND;
		if (c.target > 0f && c.target >= w) {
			return; // a closer catch already owns the arm this tick
		}
		c.main = main;
		c.arm = main ? owner.getMainArm() : owner.getMainArm().getOpposite();
		c.target = w;
		c.from = from;
	}

	private static float weight(Catch c, float partial) {
		float w = Mth.lerp(partial, c.prev, c.cur);
		return w * w * (3f - 2f * w);
	}

	/** First person: how far (0..1) {@code player}'s {@code hand} is raised for a catch right now. */
	public static float firstPersonWeight(Player player, InteractionHand hand, float partial) {
		Catch c = CATCHES.get(player.getId());
		if (c == null || c.main != (hand == InteractionHand.MAIN_HAND)) {
			return 0f;
		}
		return weight(c, partial);
	}

	/** Third person: lifts the catching arm toward the incoming weapon (after every other Thor pose, so it wins on that arm). */
	public static void applyPose(Player player, HumanoidModel<?> m) {
		Catch c = CATCHES.get(player.getId());
		if (c == null || c.arm == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float w = weight(c, partial);
		if (w <= 0.001f) {
			return;
		}
		boolean right = c.arm == HumanoidArm.RIGHT;
		ModelPart arm = right ? m.rightArm : m.leftArm;
		float s = right ? 1f : -1f;
		// default: up and a little forward and out, palm open to the sky
		float x = -2.55f, y = -0.1f * s, z = 0.18f * s;
		if (c.from != null) {
			// reach toward the weapon: the bow-aim convention (xRot = -90 deg + pitch, yRot = yaw off the body), kept raised
			float bodyYaw = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot);
			Vec3 shoulder = player.getPosition(partial).add(0, player.getBbHeight() * 0.8, 0);
			Vec3 d = c.from.subtract(shoulder);
			double flat = Math.sqrt(d.x * d.x + d.z * d.z);
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float rel = Mth.wrapDegrees(yaw - bodyYaw) * Mth.DEG_TO_RAD;
			float pitch = (float) -Math.atan2(d.y, Math.max(flat, 0.01));
			x = Mth.clamp(-Mth.HALF_PI + pitch, -3.0f, -1.9f);
			y = Mth.clamp(rel, -1.1f, 1.1f);
			z = 0.1f * s;
		}
		arm.xRot = Mth.lerp(w, arm.xRot, x);
		arm.yRot = Mth.lerp(w, arm.yRot, y);
		arm.zRot = Mth.lerp(w, arm.zRot, z);
	}
}

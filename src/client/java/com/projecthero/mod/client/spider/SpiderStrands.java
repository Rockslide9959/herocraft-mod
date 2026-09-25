package com.projecthero.mod.client.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.network.SpiderWebStrandPayload;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Client-side book-keeping for web strands (v0.12.20): the fading lines Web Zip and the Combat Mode moves leave
 * between a player's hand and their target, and the fading remnant of a released Web Swing.
 *
 * <p>Also owns the hand tracker. The web has to leave the <em>real</em> hand, so for anyone drawn in third
 * person {@code SpiderHandTrackerLayer} records, every frame, where the (posed) fist actually is in world space;
 * for the local player in first person the hand is pinned to the usual first-person hand spot in view space.
 * If neither is available (player out of frame, never rendered) it falls back to a body-yaw estimate.
 */
public final class SpiderStrands {
	/** One live strand. {@code start} is in game ticks (with fraction), so fades are frame-rate independent. */
	public static final class Strand {
		public final int playerId;
		public final int slot;
		public final int targetEntityId;
		public Vec3 pos;
		public final double startTick;
		public final int hold;
		public final int fade;
		public final boolean rightHand;
		/** Where the near end was when the player let go (v0.12.21): once set, the strand never follows the hand again. */
		public Vec3 frozenHand;

		Strand(int playerId, int slot, int targetEntityId, Vec3 pos, double startTick, int hold, int fade, boolean rightHand) {
			this.playerId = playerId;
			this.slot = slot;
			this.targetEntityId = targetEntityId;
			this.pos = pos;
			this.startTick = startTick;
			this.hold = hold;
			this.fade = fade;
			this.rightHand = rightHand;
		}

		/** Opacity 0..1 at {@code now} (game ticks); {@code <= 0} once it has fully faded. */
		public float alpha(double now) {
			double age = now - startTick;
			if (age <= hold) {
				return 1.0f;
			}
			if (fade <= 0) {
				return 0.0f;
			}
			return (float) (1.0 - (age - hold) / fade);
		}
	}

	/** slot id used by the fading remnant of a released swing (never sent by the server). */
	public static final int SLOT_SWING_REMNANT = -1;
	private static final int SWING_REMNANT_FADE = 5 * 20;

	private static final List<Strand> STRANDS = new ArrayList<>();
	/** playerId -> {world x,y,z of the right fist, left fist} captured by the layer this frame. */
	private static final Map<Integer, Vec3[]> CAPTURED = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> CAPTURED_AT = new ConcurrentHashMap<>();

	private SpiderStrands() {
	}

	public static List<Strand> strands() {
		return STRANDS;
	}

	public static void reset() {
		STRANDS.clear();
		CAPTURED.clear();
		CAPTURED_AT.clear();
	}

	private static double now(float partial) {
		Minecraft mc = Minecraft.getInstance();
		return (mc.level == null ? 0L : mc.level.getGameTime()) + partial;
	}

	public static void accept(SpiderWebStrandPayload p) {
		removeSlot(p.playerId(), p.slot());
		if (p.holdTicks() <= 0 && p.fadeTicks() <= 0) {
			return;
		}
		STRANDS.add(new Strand(p.playerId(), p.slot(), p.targetEntityId(), new Vec3(p.x(), p.y(), p.z()),
				now(0.0f), p.holdTicks(), p.fadeTicks(), p.rightHand()));
	}

	public static void removeSlot(int playerId, int slot) {
		STRANDS.removeIf(s -> s.playerId == playerId && s.slot == slot);
	}

	/** A released swing does not vanish: its line stays where it was and phases out over five seconds. */
	public static void swingReleased(int playerId, Vec3 anchor, boolean rightHand, Vec3 lastHand) {
		removeSlot(playerId, SLOT_SWING_REMNANT);
		Strand s = new Strand(playerId, SLOT_SWING_REMNANT, -1, anchor, now(0.0f), 0, SWING_REMNANT_FADE, rightHand);
		s.frozenHand = lastHand; // released: the web is let go of, it does not follow the hand
		STRANDS.add(s);
	}

	/** Drop expired strands and strands of players no longer in the world. Call once per frame. */
	public static void prune(float partial) {
		Minecraft mc = Minecraft.getInstance();
		double now = now(partial);
		STRANDS.removeIf(s -> s.alpha(now) <= 0.0f || mc.level == null || mc.level.getEntity(s.playerId) == null);
	}

	/** Where a strand's far end is right now: its entity (while it exists) or the stored point. */
	public static Vec3 endPoint(Strand s, float partial) {
		if (s.targetEntityId >= 0) {
			Minecraft mc = Minecraft.getInstance();
			Entity e = mc.level == null ? null : mc.level.getEntity(s.targetEntityId);
			if (e != null && e.isAlive()) {
				Vec3 at = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()),
						Mth.lerp(partial, e.zo, e.getZ())).add(0, e.getBbHeight() * 0.5, 0);
				s.pos = at; // remember the last place it was
				return at;
			}
		}
		return s.pos;
	}

	// ---------------- hand tracking ----------------

	/** Called by {@code SpiderHandTrackerLayer} with the fist positions (world space) from this frame's pose. */
	public static void captureHands(int playerId, Vec3 right, Vec3 left) {
		CAPTURED.put(playerId, new Vec3[] {right, left});
		CAPTURED_AT.put(playerId, System.nanoTime());
	}

	/** Which players currently need their hands captured (has a swing or a strand). Cheap check for the layer. */
	public static boolean wantsCapture(int playerId) {
		for (Strand s : STRANDS) {
			if (s.playerId == playerId) {
				return true;
			}
		}
		return false;
	}

	/** True while the local camera is in first person (the local player's model is not drawn then). */
	private static boolean firstPerson(Minecraft mc) {
		return mc.options.getCameraType().isFirstPerson();
	}

	/**
	 * World position of {@code player}'s fist on the given side, this frame.
	 *
	 * @param cam the world-render camera
	 */
	public static Vec3 handPosition(Player player, boolean right, float partial, Camera cam) {
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player && firstPerson(mc)) {
			// the usual first-person hand spot: right/down/forward of the eye, in view space
			Vector3f look = cam.getLookVector();
			Vector3f up = cam.getUpVector();
			Vector3f left = cam.getLeftVector();
			double side = right ? -1.0 : 1.0; // getLeftVector points to the left of the view
			return cam.getPosition()
					.add(look.x * 0.7, look.y * 0.7, look.z * 0.7)
					.add(left.x * 0.42 * side, left.y * 0.42 * side, left.z * 0.42 * side)
					.add(-up.x * 0.34, -up.y * 0.34, -up.z * 0.34);
		}
		Long at = CAPTURED_AT.get(player.getId());
		Vec3[] hands = CAPTURED.get(player.getId());
		if (hands != null && at != null && System.nanoTime() - at < 150_000_000L) {
			return hands[right ? 0 : 1];
		}
		// Fallback: the fist of a forward-pointing arm, derived from the body yaw (never from the view).
		double px = Mth.lerp(partial, player.xo, player.getX());
		double py = Mth.lerp(partial, player.yo, player.getY());
		double pz = Mth.lerp(partial, player.zo, player.getZ());
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		Vec3 bodyRight = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 bodyForward = Vec3.directionFromRotation(0.0f, bodyYaw);
		double side = right ? 1.0 : -1.0;
		return new Vec3(px, py, pz).add(0.0, 1.25, 0.0).add(bodyRight.scale(side * 0.36)).add(bodyForward.scale(0.45));
	}
}

package com.projecthero.mod.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.power.ThorPowers;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Drives the whole flight pose, for every player the client can see. Three speed tiers:
 * <ul>
 *   <li>{@link Tier#HOVER} -- little/no horizontal movement: upright, normal grip -- standing still
 *   in the air looks exactly like standing still on the ground, arm untouched.</li>
 *   <li>{@link Tier#SLOW} -- moving but not sprinting: a slight forward lean, arms normal.</li>
 *   <li>{@link Tier#FAST} -- sprinting: flat horizontal "superman" pose, hammer arm extended
 *   straight ahead along the direction of travel.</li>
 * </ul>
 *
 * <p>Rather than reading the tier straight off the entity at render time (which snapped the model
 * between poses the instant a speed threshold was crossed), each player gets a small animation
 * state here that eases toward the current tier's target every client tick. Renderers read the
 * eased values -- {@link #lean} and {@link #armRaise} -- interpolated across the partial tick, so
 * hover/walk/sprint transitions and take-off/landing all blend smoothly.
 *
 * <p>Speed comes from the per-tick change in position rather than {@code getDeltaMovement()}: for
 * <em>other</em> players the client only receives velocity sporadically, so the delta-movement
 * vector is unreliable for remote entities, while their interpolated position is not.
 */
public final class FlightPoseHelper {
	public enum Tier { HOVER, SLOW, FAST }

	/** Blocks per tick below which the player counts as hovering rather than flying somewhere. */
	private static final double HOVER_SPEED_THRESHOLD = 0.03;

	public static final float LEAN_HOVER_DEGREES = 0.0f;
	public static final float LEAN_SLOW_DEGREES = 25.0f;
	/** Fully horizontal -- the superman pose, body laid out flat along the direction of travel. */
	public static final float LEAN_FAST_DEGREES = 90.0f;

	/** Fraction of the remaining distance to the target pose covered per tick (~0.5s to settle). */
	private static final float APPROACH_PER_TICK = 0.22f;

	private static final Map<UUID, PoseAnim> ANIMS = new HashMap<>();

	private FlightPoseHelper() {
	}

	private static final class PoseAnim {
		private double lastX;
		private double lastZ;
		private boolean hasLast;

		private Tier tier = Tier.HOVER;
		private boolean heroOnly;
		private float lean;
		private float leanPrev;
		/** 0 = whatever vanilla's animation did with the arms, 1 = fully raised/extended. */
		private float raise;
		private float raisePrev;
	}

	/** Called once per client tick (see {@code ProjectHeroModClient}) for every player in the level. */
	public static void clientTick(ClientLevel level) {
		if (level == null) {
			ANIMS.clear();
			return;
		}

		Set<UUID> present = new HashSet<>();
		for (Player player : level.players()) {
			present.add(player.getUUID());
			tickPlayer(player, ANIMS.computeIfAbsent(player.getUUID(), id -> new PoseAnim()));
		}
		ANIMS.keySet().retainAll(present);
	}

	private static void tickPlayer(Player player, PoseAnim anim) {
		double speed = anim.hasLast
				? Math.hypot(player.getX() - anim.lastX, player.getZ() - anim.lastZ)
				: 0.0;
		anim.lastX = player.getX();
		anim.lastZ = player.getZ();
		anim.hasLast = true;

		boolean thorFlying = player.getAttachedOrElse(ModAttachments.FLYING, false) && ThorPowers.isHoldingMjolnir(player);
		// Experimental hero flight (Wind Flight etc.) and Iron Man repulsor flight both get the same
		// forward-lean "superman" body pose with the arms left at the player's sides -- so no arm raise
		// for either. (Iron Man's own IRON_MAN_FLYING flag is synced to everyone, like HERO_FLYING.)
		boolean ironManFlying = player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false);
		// v0.6.17: Max Steel's Turbo Flight gets the same forward-lean "superman" body pose.
		boolean maxSteelFlying = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false);
		// v0.11.2: Green Lantern's Ring Flight gets the same pose too -- this is also what keeps the
		// skin's hat layer glued to the head while flying (see HumanoidModelMixin#levelHead), which was
		// never happening for Ring Flight before since it never drove a body lean at all.
		boolean greenLanternFlying = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
		boolean heroFlying = player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| ironManFlying || maxSteelFlying || greenLanternFlying;
		boolean flying = thorFlying || heroFlying;
		anim.heroOnly = heroFlying && !thorFlying;
		if (flying) {
			anim.tier = tierFor(player, speed);
		}

		// Mark 1 never does the sprint "superman" lean, per its own suit definition (spec "changes
		// 12") -- checked via the target-synced Tony Stark state so this reads correctly for every
		// player the client can see, not just the local one.
		boolean noLean = ironManFlying && noFlightLean(player);

		// Targets fall back to "stand normally" when not flying, so landing eases out of the pose
		// instead of snapping upright the frame flight ends. The raised/extended arm is FAST
		// (sprint) only now -- HOVER used to raise it too (an overhead whirl pose), but that read as
		// an unnatural "backwards" grip while just standing still in the air, so hovering now keeps
		// the ordinary grip untouched, same as standing on the ground.
		float targetLean = flying && !noLean ? leanDegrees(anim.tier) : 0.0f;
		float targetRaise = flying && !anim.heroOnly && anim.tier == Tier.FAST ? 1.0f : 0.0f;

		anim.leanPrev = anim.lean;
		anim.raisePrev = anim.raise;
		anim.lean = approach(anim.lean, targetLean, 0.05f);
		anim.raise = approach(anim.raise, targetRaise, 0.001f);
	}

	private static float approach(float current, float target, float snapEpsilon) {
		float next = current + (target - current) * APPROACH_PER_TICK;
		return Math.abs(target - next) < snapEpsilon ? target : next;
	}

	/**
	 * Reads the suit straight off the worn boots (equipment is ordinary synced entity data, unlike a
	 * custom attachment, so this is reliable for remote players too) rather than the synced
	 * {@code TonyStarkState.activeSuit} -- simpler, and correct even in the moment a suit is coming off.
	 */
	private static boolean noFlightLean(Player player) {
		if (!(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
				.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem piece)) {
			return false;
		}
		com.projecthero.mod.ironman.suit.IronManSuit suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(piece.suitId());
		return suit != null && suit.noFlightLean();
	}

	private static Tier tierFor(Player player, double horizontalSpeed) {
		if (horizontalSpeed < HOVER_SPEED_THRESHOLD) {
			return Tier.HOVER;
		}
		// Reuses the entity's own sprint flag (the same walking-vs-sprinting distinction ground
		// movement uses, and one that's synced for remote players) rather than a second speed cutoff.
		return player.isSprinting() ? Tier.FAST : Tier.SLOW;
	}

	public static float leanDegrees(Tier tier) {
		return switch (tier) {
			case HOVER -> LEAN_HOVER_DEGREES;
			case SLOW -> LEAN_SLOW_DEGREES;
			case FAST -> LEAN_FAST_DEGREES;
		};
	}

	/** The tier being animated toward. Only meaningful while {@link #armRaise} is non-zero. */
	public static Tier tier(Player player) {
		PoseAnim anim = ANIMS.get(player.getUUID());
		return anim == null ? Tier.HOVER : anim.tier;
	}

	/**
	 * True while the player is in experimental hero flight (Wind/rock/flame flight) with a meaningful
	 * body lean -- the pose that leans like Thor but keeps the arms pinned at the sides.
	 */
	public static boolean heroFlight(Player player, float partialTick) {
		PoseAnim anim = ANIMS.get(player.getUUID());
		return anim != null && anim.heroOnly && Math.abs(lean(player, partialTick)) > 5.0f;
	}

	/** Smoothed forward body tilt, in degrees. 0 while upright. */
	public static float lean(Player player, float partialTick) {
		PoseAnim anim = ANIMS.get(player.getUUID());
		return anim == null ? 0.0f : Mth.lerp(partialTick, anim.leanPrev, anim.lean);
	}

	/** Smoothed 0..1 blend from the vanilla arm animation into the raised/extended hammer pose. */
	public static float armRaise(Player player, float partialTick) {
		PoseAnim anim = ANIMS.get(player.getUUID());
		return anim == null ? 0.0f : Mth.lerp(partialTick, anim.raisePrev, anim.raise);
	}
}

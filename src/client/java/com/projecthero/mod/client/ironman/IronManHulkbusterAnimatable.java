package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: the Mark 8 HULKBUSTER body drawn in place of a player wearing the full Mark 8 (like the Hulk's
 * player-replacement). One singleton; GeckoLib keys each player's animation state by entity id.
 *
 * <p>Moves: the server's {@code IronManPosePayload} for a {@code HB_*} id (every Mark 8 comic move sends one, timed so its
 * strike ticks line up with the clip -- {@code scratchpad/hulkbuster_anim_timing.json}) plays that clip, restarting it
 * when a new one arrives. Otherwise locomotion: {@code jet_hover} in flight, {@code land} after a fall, {@code jump}
 * going up, {@code run} / {@code walk} by speed, {@code idle}. Transition 4 ticks (the timing table's TRANSITION).
 */
public final class IronManHulkbusterAnimatable implements GeoReplacedEntity {
	public static final IronManHulkbusterAnimatable INSTANCE = new IronManHulkbusterAnimatable();

	private static final String P = "animation.hulkbuster_body.";
	private static final Map<String, RawAnimation> MOVES = new HashMap<>();
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	private static final RawAnimation HOVER = RawAnimation.begin().thenLoop(P + "jet_hover");
	/** v0.16.7 (user) Veronica: a broken leg drags (the left / right one). */
	static final RawAnimation LIMP_L = RawAnimation.begin().thenLoop(P + "limp_l");
	static final RawAnimation LIMP_R = RawAnimation.begin().thenLoop(P + "limp_r");
	private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold(P + "jump");
	private static final RawAnimation LAND = RawAnimation.begin().thenPlay(P + "land");
	/** The land clip's length after its 4-tick transition (16 in the timing table). */
	private static final int LAND_TICKS = 16;
	static final String HEAVY_LAND = "superhero_land";
	static final String LIGHT_LAND = "touchdown_land";
	/** The usual blend between clips, and the landings' quicker one (the slam has to land on the touchdown). */
	private static final int TRANSITION = 4;
	private static final int LAND_TRANSITION = 2;
	/** After a flight / big fall ends on the ground, hold the air pose this long for the server's landing clip. */
	private static final int LAND_WAIT_TICKS = 3;

	static {
		for (String clip : new String[] { "punch_combo", "rocket_fist", "pile_driver", "earthquake_stomp", "grab_pound", "grab_throw",
				"chest_beam", "missile_swarm", "replacement_pod", "palm_cannon" }) { // v0.16.7 (user): + the Palm Cannon
			MOVES.put(clip, RawAnimation.begin().thenPlay(P + clip));
		}
		// v0.16.7 (user): the landings (HulkbusterLanding) -- the heavy superhero landing and the light touchdown
		MOVES.put(HEAVY_LAND, RawAnimation.begin().thenPlay(P + HEAVY_LAND));
		MOVES.put(LIGHT_LAND, RawAnimation.begin().thenPlay(P + LIGHT_LAND));
		MOVES.put("grab", RawAnimation.begin().thenPlayAndHold(P + "grab"));
		MOVES.put("hydraulic_brace", RawAnimation.begin().thenPlayAndHold(P + "hydraulic_brace"));
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Per player: the start tick of the move clip playing (restart on a new one), air ticks, landing end. */
	private final Map<Integer, Long> moveStart = new HashMap<>();
	private final Map<Integer, Integer> airTicks = new HashMap<>();
	private final Map<Integer, Long> landUntil = new HashMap<>();
	private final Map<Integer, Long> lastSeen = new HashMap<>();
	/** v0.16.7 (user): per player -- flying last tick, the fall's peak Y, the wait for a landing clip, a cancelled landing. */
	private final Map<Integer, Boolean> wasFlying = new HashMap<>();
	private final Map<Integer, Double> fallPeak = new HashMap<>();
	private final Map<Integer, Long> landWaitUntil = new HashMap<>();
	private static final Map<Integer, Long> CANCELLED = new HashMap<>();

	private IronManHulkbusterAnimatable() {
	}

	/** Is {@code player} drawn as the Hulkbuster body? (the full Mark 8 worn) */
	public static boolean active(Player player) {
		return player != null && "mark_8".equals(IronManArmor.wornSuitId(player)) && IronManArmor.wearingFullSuit(player, "mark_8");
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", TRANSITION, this::main));
	}

	private PlayState main(AnimationState<IronManHulkbusterAnimatable> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof Player player)) {
			return PlayState.STOP;
		}
		int id = player.getId();
		long now = player.level().getGameTime();
		// a move clip from the server
		IronManAbilityPose.Play play = IronManAbilityPose.current(player);
		String clip = play == null ? null : IronManAbilityFx.hulkbusterClip(play.anim());
		boolean landing = HEAVY_LAND.equals(clip) || LIGHT_LAND.equals(clip);
		// v0.16.7 (user): moving after the impact frame cancels the rest of a landing (for every viewer: it reads the motion)
		if (landing && landCancelled(player, play)) {
			clip = null;
			landing = false;
		}
		if (clip != null && MOVES.containsKey(clip)) {
			Long prev = moveStart.get(id);
			if (prev == null || prev != play.start()) {
				moveStart.put(id, play.start());
				// the landing slams in over 2 ticks (straight out of the hover / fall pose); everything else blends over 4
				state.getController().transitionLength(landing ? LAND_TRANSITION : TRANSITION);
				state.getController().forceAnimationReset();
			}
			landWaitUntil.remove(id);
			return state.setAndContinue(MOVES.get(clip));
		}
		if (moveStart.remove(id) != null) {
			state.getController().transitionLength(TRANSITION);
		}
		// locomotion
		boolean flying = player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false);
		if (lastSeen.getOrDefault(id, -1L) != now) {
			lastSeen.put(id, now);
			boolean flew = wasFlying.getOrDefault(id, false);
			wasFlying.put(id, flying);
			if (flew && !flying) {
				landWaitUntil.put(id, now + LAND_WAIT_TICKS); // the flight just ended: the server's landing clip is coming
			}
			if (player.onGround()) {
				Double peak = fallPeak.remove(id);
				if (peak != null && peak - player.getY() >= com.projecthero.mod.ironman.HulkbusterLanding.FALL_MIN) {
					landWaitUntil.put(id, now + LAND_WAIT_TICKS); // a big fall: so is this one
				} else if (airTicks.getOrDefault(id, 0) > 6 && !flew) {
					landUntil.put(id, now + LAND_TICKS);
				}
				airTicks.put(id, 0);
			} else {
				airTicks.merge(id, 1, Integer::sum);
				if (!flying) {
					fallPeak.merge(id, player.getY(), Math::max);
				} else {
					fallPeak.remove(id);
				}
			}
		}
		if (flying) {
			return state.setAndContinue(HOVER);
		}
		if (landWaitUntil.getOrDefault(id, 0L) > now) {
			return PlayState.CONTINUE; // hold the air pose a moment for the landing clip -- no idle flash before the slam
		}
		if (landUntil.getOrDefault(id, 0L) > now) {
			return state.setAndContinue(LAND);
		}
		if (!player.onGround() && !player.isInWater() && player.getDeltaMovement().y > 0.08) {
			return state.setAndContinue(JUMP);
		}
		if (state.getLimbSwingAmount() > 0.05f) {
			RawAnimation limp = limp(HulkbusterVeronicaClient.damageOf(player));
			if (limp != null) {
				return state.setAndContinue(limp);
			}
			return state.setAndContinue(player.isSprinting() ? RUN : WALK);
		}
		return state.setAndContinue(IDLE);
	}

	/**
	 * v0.16.7 (user): has {@code player} walked out of this landing? Past the impact frame, any real horizontal motion (or a
	 * jump) ends the clip -- remembered per landing so it does not snap back when they stop again.
	 */
	static boolean landCancelled(Player player, IronManAbilityPose.Play play) {
		Long cancelled = CANCELLED.get(player.getId());
		if (cancelled != null && cancelled == play.start()) {
			return true;
		}
		float age = IronManAbilityPose.age(play);
		if (age < com.projecthero.mod.ironman.HulkbusterLanding.IMPACT_TICKS + 1) {
			return false;
		}
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		boolean moving = dx * dx + dz * dz > 0.03 * 0.03 || player.getDeltaMovement().y > 0.1 && !player.onGround();
		if (moving) {
			CANCELLED.put(player.getId(), play.start());
			return true;
		}
		return false;
	}

	/**
	 * v0.16.7 (user): the local player's input is held while their Hulkbuster lands -- only up to the impact frame (about
	 * 0.2 s); after it, moving cancels the rest of the clip instead.
	 */
	public static boolean landingHoldsInput(Player player) {
		if (!active(player)) {
			return false;
		}
		IronManAbilityPose.Play play = IronManAbilityPose.current(player);
		if (play == null || play.anim() != IronManAbilityFx.HB_LAND_HEAVY && play.anim() != IronManAbilityFx.HB_LAND_LIGHT) {
			return false;
		}
		return IronManAbilityPose.age(play) < com.projecthero.mod.ironman.HulkbusterLanding.IMPACT_TICKS + 1;
	}

	/** v0.16.7 (user): the limp for a broken leg (the worse one when both are gone), or null. */
	static RawAnimation limp(float[] eff) {
		if (eff == null) {
			return null;
		}
		boolean l = com.projecthero.mod.ironman.HulkbusterParts.broken(eff[com.projecthero.mod.ironman.HulkbusterParts.LLEG]);
		boolean r = com.projecthero.mod.ironman.HulkbusterParts.broken(eff[com.projecthero.mod.ironman.HulkbusterParts.RLEG]);
		return l ? LIMP_L : r ? LIMP_R : null;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

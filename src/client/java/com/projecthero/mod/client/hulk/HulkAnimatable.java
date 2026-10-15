package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.data.HulkState;

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
 * v0.13.12 (Hulk Phase 3): the GeckoLib animatable drawn in place of a player while he is the Hulk. One singleton for
 * every Hulk -- GeckoLib keys each player's animation state by entity id. What plays is read from the player's synced
 * {@link HulkState}: the growth ({@code transform}), a charging / airborne Super Leap, Thunderclap ({@code clap}) and
 * Ground Smash ({@code smash}) on their impact clock, otherwise {@code run} / {@code walk} / {@code idle}; a melee
 * swing layers {@code punch} over the right arm ({@code punch_left} over the left for a left-arm swing, v0.15.5).
 *
 * <p>v0.16.4 (user): the rig is jointed (elbows / knees: {@code right_forearm}, {@code right_hand}, {@code right_shin},
 * {@code right_foot} ...), six changes ({@code Combat.transformStyle} -> {@code transform}, {@code transform_forced},
 * {@code transform_rip}, {@code transform_pound}, {@code transform_arch}, {@code transform_stagger}), the new kit's clips
 * ({@code HulkState.ANIM_SUPER_PUNCH .. ANIM_RAMPAGE}, see {@link #oneShotTicks}) and the held {@code death_fall}.
 */
public final class HulkAnimatable implements GeoReplacedEntity {
	public static final HulkAnimatable INSTANCE = new HulkAnimatable();

	private static final String P = "animation.hulk.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	private static final RawAnimation CLAP = RawAnimation.begin().thenPlay(P + "clap");
	private static final RawAnimation SMASH = RawAnimation.begin().thenPlay(P + "smash");
	private static final RawAnimation LEAP_CHARGE = RawAnimation.begin().thenPlayAndHold(P + "leap_charge");
	/** v0.16.4 (user): sitting in his cave during a takeover -- on the ground, knees bent up, hands resting on them, breathing. */
	private static final RawAnimation CAVE_SIT = RawAnimation.begin().thenLoop(P + "sit");
	private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold(P + "leap");
	private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay(P + "transform");
	/** v0.13.15: the unwilling change -- on his knees, clutching his head, then up into the roar. */
	private static final RawAnimation TRANSFORM_FORCED = RawAnimation.begin().thenPlay(P + "transform_forced");
	private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay(P + "punch");
	/** v0.15.5: the same punch thrown with the left arm (an off-hand swing -- Gladiator Hulk alternates axe / hammer). */
	private static final RawAnimation PUNCH_LEFT = RawAnimation.begin().thenPlay(P + "punch_left");
	private static final RawAnimation POWER_PUNCH = RawAnimation.begin().thenPlay(P + "power_punch");
	private static final RawAnimation HULK_SMASH_CHARGE = RawAnimation.begin().thenPlayAndHold(P + "hulk_smash_charge");
	private static final RawAnimation HULK_SMASH = RawAnimation.begin().thenPlay(P + "hulk_smash");
	private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop(P + "charge");
	private static final RawAnimation HOLD = RawAnimation.begin().thenLoop(P + "hold");
	private static final RawAnimation PICKUP = RawAnimation.begin().thenPlay(P + "pickup");
	private static final RawAnimation THROW = RawAnimation.begin().thenPlay(P + "throw");
	private static final RawAnimation CRUSH = RawAnimation.begin().thenPlay(P + "crush");
	// v0.15.3: the Gladiator Hulk's moves (axe in the left hand, hammer in the right)
	private static final RawAnimation G_AXE_CLEAVE = RawAnimation.begin().thenPlay(P + "gladiator_axe_cleave");
	private static final RawAnimation G_UPPERCUT = RawAnimation.begin().thenPlay(P + "gladiator_hammer_uppercut");
	private static final RawAnimation G_QUAKE = RawAnimation.begin().thenPlay(P + "gladiator_hammer_quake");
	private static final RawAnimation G_EARTHSPLITTER = RawAnimation.begin().thenPlay(P + "gladiator_earthsplitter");
	private static final RawAnimation G_ROAR = RawAnimation.begin().thenPlay(P + "gladiator_champions_roar");
	private static final RawAnimation G_CLASH = RawAnimation.begin().thenPlay(P + "gladiator_weapon_clash");
	private static final RawAnimation G_ARENA_LEAP = RawAnimation.begin().thenPlayAndHold(P + "gladiator_arena_leap");
	private static final RawAnimation G_METEOR = RawAnimation.begin().thenPlayAndHold(P + "gladiator_meteor_dive");
	private static final RawAnimation G_SLAM = RawAnimation.begin().thenPlay(P + "gladiator_slam");
	private static final RawAnimation G_AXE_THROW = RawAnimation.begin().thenPlay(P + "gladiator_axe_throw");
	private static final RawAnimation G_HAMMER_HURL = RawAnimation.begin().thenPlay(P + "gladiator_hammer_hurl");
	private static final RawAnimation G_RECALL = RawAnimation.begin().thenPlay(P + "gladiator_hammer_recall");
	private static final RawAnimation G_WHIRLWIND = RawAnimation.begin().thenLoop(P + "gladiator_whirlwind");
	private static final RawAnimation G_GRAPPLE = RawAnimation.begin().thenLoop(P + "gladiator_grapple");

	// v0.16.4 (user): the four new changes (all standing) -- see HulkPose for Banner's side of each
	private static final RawAnimation TRANSFORM_RIP = RawAnimation.begin().thenPlay(P + "transform_rip");
	private static final RawAnimation TRANSFORM_POUND = RawAnimation.begin().thenPlay(P + "transform_pound");
	private static final RawAnimation TRANSFORM_ARCH = RawAnimation.begin().thenPlay(P + "transform_arch");
	private static final RawAnimation TRANSFORM_STAGGER = RawAnimation.begin().thenPlay(P + "transform_stagger");
	// v0.16.4 (user): the new kit (HulkState.ANIM_SUPER_PUNCH .. ANIM_RAMPAGE)
	private static final RawAnimation SUPER_PUNCH = RawAnimation.begin().thenPlay(P + "super_punch");
	private static final RawAnimation GROUND_SMASH = RawAnimation.begin().thenPlay(P + "ground_smash");
	private static final RawAnimation THUNDERCLAP = RawAnimation.begin().thenPlay(P + "thunderclap");
	private static final RawAnimation FAULT_STOMP = RawAnimation.begin().thenPlay(P + "fault_stomp");
	private static final RawAnimation BULLDOZE = RawAnimation.begin().thenLoop(P + "bulldoze");
	private static final RawAnimation UPPERCUT = RawAnimation.begin().thenPlay(P + "uppercut");
	private static final RawAnimation GAMMA_BURST = RawAnimation.begin().thenPlay(P + "gamma_burst");
	private static final RawAnimation EARTH_WALL = RawAnimation.begin().thenPlay(P + "earth_wall");
	private static final RawAnimation GRAB = RawAnimation.begin().thenPlay(P + "grab");
	private static final RawAnimation THROW_FAR = RawAnimation.begin().thenPlay(P + "throw_far");
	private static final RawAnimation DEBRIS_RIP = RawAnimation.begin().thenPlay(P + "debris_rip");
	private static final RawAnimation DEBRIS_THROW = RawAnimation.begin().thenPlay(P + "debris_throw");
	private static final RawAnimation LOKI_SLAM = RawAnimation.begin().thenPlay(P + "loki_slam");
	private static final RawAnimation ROAR = RawAnimation.begin().thenPlay(P + "roar");
	private static final RawAnimation LEAP_LAUNCH = RawAnimation.begin().thenPlay(P + "leap_launch");
	private static final RawAnimation LEAP_LAND = RawAnimation.begin().thenPlay(P + "leap_land");
	private static final RawAnimation DEATH_FALL = RawAnimation.begin().thenPlayAndHold(P + "death_fall");
	private static final RawAnimation RAMPAGE_ROAR = RawAnimation.begin().thenPlay(P + "rampage_roar");

	/** Ticks each one-shot owns the body after it starts (a touch longer than the clip, so it finishes). */
	private static final int CLAP_TICKS = 14;
	private static final int SMASH_TICKS = 20;

	/**
	 * v0.16.4: Loki slam impact times (s into {@code loki_slam}): 0.75, 1.35, 1.95, 2.55 -- ticks 15, 27, 39, 51 after
	 * {@code animStart}; left-front, right-front, left-front, right-front of him. For MOVES / the server to sync the victim.
	 */
	public static final int[] LOKI_SLAM_IMPACT_TICKS = { 15, 27, 39, 51 };

	/**
	 * v0.16.4: ticks each new one-shot ({@code HulkState.ANIM_*}) owns the body after it starts (clip length + 1), or 0 for
	 * one that isn't a timed one-shot (bulldoze loops while charging, death_fall is held).
	 */
	public static int oneShotTicks(int animId) {
		return switch (animId) {
			case HulkState.ANIM_SUPER_PUNCH -> 16;
			case HulkState.ANIM_GROUND_SMASH -> 21;
			case HulkState.ANIM_THUNDERCLAP -> 16;
			case HulkState.ANIM_FAULT_STOMP -> 19;
			case HulkState.ANIM_UPPERCUT -> 17;
			case HulkState.ANIM_GAMMA_BURST -> 21;
			case HulkState.ANIM_EARTH_WALL -> 21;
			case HulkState.ANIM_GRAB -> 10;
			case HulkState.ANIM_THROW_FAR -> 13;
			case HulkState.ANIM_DEBRIS_RIP -> 17;
			case HulkState.ANIM_DEBRIS_THROW -> 15;
			case HulkState.ANIM_LOKI_SLAM -> 61;
			case HulkState.ANIM_ROAR -> 33;
			case HulkState.ANIM_LEAP_LAUNCH -> 8;
			case HulkState.ANIM_LEAP_LAND -> 15;
			case HulkState.ANIM_RAMPAGE -> 29;
			default -> 0;
		};
	}

	private static RawAnimation oneShot(int animId) {
		return switch (animId) {
			case HulkState.ANIM_SUPER_PUNCH -> SUPER_PUNCH;
			case HulkState.ANIM_GROUND_SMASH -> GROUND_SMASH;
			case HulkState.ANIM_THUNDERCLAP -> THUNDERCLAP;
			case HulkState.ANIM_FAULT_STOMP -> FAULT_STOMP;
			case HulkState.ANIM_UPPERCUT -> UPPERCUT;
			case HulkState.ANIM_GAMMA_BURST -> GAMMA_BURST;
			case HulkState.ANIM_EARTH_WALL -> EARTH_WALL;
			case HulkState.ANIM_GRAB -> GRAB;
			case HulkState.ANIM_THROW_FAR -> THROW_FAR;
			case HulkState.ANIM_DEBRIS_RIP -> DEBRIS_RIP;
			case HulkState.ANIM_DEBRIS_THROW -> DEBRIS_THROW;
			case HulkState.ANIM_LOKI_SLAM -> LOKI_SLAM;
			case HulkState.ANIM_ROAR -> ROAR;
			case HulkState.ANIM_LEAP_LAUNCH -> LEAP_LAUNCH;
			case HulkState.ANIM_LEAP_LAND -> LEAP_LAND;
			case HulkState.ANIM_RAMPAGE -> RAMPAGE_ROAR;
			default -> null;
		};
	}

	/** v0.16.4: the change clip for a style ({@code HulkState.TRANSFORM_*}). */
	private static RawAnimation transformClip(int style) {
		return switch (style) {
			case HulkState.TRANSFORM_FORCED -> TRANSFORM_FORCED;
			case HulkState.TRANSFORM_RIP -> TRANSFORM_RIP;
			case HulkState.TRANSFORM_POUND -> TRANSFORM_POUND;
			case HulkState.TRANSFORM_ARCH -> TRANSFORM_ARCH;
			case HulkState.TRANSFORM_STAGGER -> TRANSFORM_STAGGER;
			default -> TRANSFORM;
		};
	}

	/**
	 * v0.16.4: per player, the animStart of the one-shot last started -- the same move used twice in a row restarts its
	 * clip instead of carrying on from the end of the first. Client render thread only.
	 */
	private static final java.util.Map<Player, Long> LAST_START = new java.util.WeakHashMap<>();

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private HulkAnimatable() {
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::main));
		controllers.add(new AnimationController<>(this, "hold", 3, this::hold));
		controllers.add(new AnimationController<>(this, "swing", 1, this::swing));
	}

	private PlayState main(AnimationState<HulkAnimatable> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof Player player)) {
			return PlayState.STOP;
		}
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null) {
			return state.setAndContinue(IDLE);
		}
		long now = player.level().getGameTime();
		// v0.16.4: dying as the Hulk -- he drops onto his back and lies there (held) through the slow change back
		if (HulkPose.deathFall(player, s)) {
			return state.setAndContinue(DEATH_FALL);
		}
		if (s.hulk && now - s.formChangedAt < com.projecthero.mod.hulk.Hulk.changeTicks(s)) {
			// v0.16.4: one of six changes, picked by the server (Combat.transformStyle)
			return state.setAndContinue(transformClip(HulkPose.style(s)));
		}
		if (s.leapChargeStart > 0L) {
			return state.setAndContinue(LEAP_CHARGE);
		}
		// v0.16.4 (user): the takeover's rest in his cave -- the sit clip
		if (s.noRing(now) && s.wild.kind == HulkState.RAMPAGE_TAKEOVER && s.wild.phase == HulkState.TAKEOVER_SIT
				&& state.getLimbSwingAmount() < 0.1f && player.onGround()) {
			return state.setAndContinue(CAVE_SIT);
		}
		if (s.combat.smashChargeStart > 0L) {
			return state.setAndContinue(HULK_SMASH_CHARGE);
		}
		long since = now - s.animStart;
		if (com.projecthero.mod.hulk.gladiator.GladiatorAnims.isGladiator(s.animId)) {
			RawAnimation g = gladiator(s.animId, since, player);
			if (g != null) {
				return state.setAndContinue(g);
			}
		}
		// v0.16.4: the new kit's one-shots
		int len = oneShotTicks(s.animId);
		if (len > 0 && since >= 0L && since < len) {
			Long last = LAST_START.get(player);
			if (last != null && last != s.animStart) {
				state.getController().forceAnimationReset(); // the same move again: start its clip over
			}
			LAST_START.put(player, s.animStart);
			return state.setAndContinue(oneShot(s.animId));
		}
		if (s.combat.chargeUntil > now && s.animId == HulkState.ANIM_BULLDOZE) {
			return state.setAndContinue(BULLDOZE);
		}
		if (s.animId == HulkState.ANIM_HULK_SMASH && since < 26) {
			return state.setAndContinue(HULK_SMASH);
		}
		if (s.animId == HulkState.ANIM_PUNCH && since < 13) {
			return state.setAndContinue(POWER_PUNCH);
		}
		if (s.animId == HulkState.ANIM_THROW && since < 11) {
			return state.setAndContinue(THROW);
		}
		if (s.animId == HulkState.ANIM_CRUSH && since < 11) {
			return state.setAndContinue(CRUSH);
		}
		if (s.animId == HulkState.ANIM_PICKUP && since < 9) {
			return state.setAndContinue(PICKUP);
		}
		if (s.combat.chargeUntil > now) {
			return state.setAndContinue(CHARGE);
		}
		if (s.animId == HulkState.ANIM_CLAP && now - s.animStart < CLAP_TICKS) {
			return state.setAndContinue(CLAP);
		}
		if (s.animId == HulkState.ANIM_SMASH && now - s.animStart < SMASH_TICKS) {
			return state.setAndContinue(SMASH);
		}
		if (s.leaping && !player.onGround()) {
			return state.setAndContinue(LEAP);
		}
		if (state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(player.isSprinting() ? RUN : WALK);
		}
		return state.setAndContinue(IDLE);
	}

	/**
	 * v0.15.3: the Gladiator Hulk's twelve moves ({@link com.projecthero.mod.hulk.gladiator.GladiatorAnims}); null once
	 * the clip is over (then the usual walk / idle choice runs).
	 */
	private static RawAnimation gladiator(int id, long since, Player player) {
		int len = com.projecthero.mod.hulk.gladiator.GladiatorAnims.ticks(id);
		if (len > 0) {
			if (since >= len) {
				return null;
			}
			return switch (id) {
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.AXE_CLEAVE -> G_AXE_CLEAVE;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.HAMMER_UPPERCUT -> G_UPPERCUT;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.HAMMER_QUAKE -> G_QUAKE;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.EARTHSPLITTER -> G_EARTHSPLITTER;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.CHAMPIONS_ROAR -> G_ROAR;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.WEAPON_CLASH -> G_CLASH;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.SLAM -> G_SLAM;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.AXE_THROW -> G_AXE_THROW;
				case com.projecthero.mod.hulk.gladiator.GladiatorAnims.HAMMER_HURL -> G_HAMMER_HURL;
				default -> G_RECALL;
			};
		}
		return switch (id) {
			case com.projecthero.mod.hulk.gladiator.GladiatorAnims.ARENA_LEAP ->
					since < 240 && (since < 5 || !player.onGround()) ? G_ARENA_LEAP : null;
			case com.projecthero.mod.hulk.gladiator.GladiatorAnims.METEOR_DIVE ->
					since < 240 && (since < 5 || !player.onGround()) ? G_METEOR : null;
			case com.projecthero.mod.hulk.gladiator.GladiatorAnims.WHIRLWIND ->
					since < com.projecthero.mod.hulk.HulkConfig.gladiator().whirlwindTicks + 2 ? G_WHIRLWIND : null;
			case com.projecthero.mod.hulk.gladiator.GladiatorAnims.GRAPPLE -> since < 200 ? G_GRAPPLE : null;
			default -> null;
		};
	}

	/** Both arms overhead while V holds a mob or a boulder (arms only, so it rides on top of walk / run). */
	private PlayState hold(AnimationState<HulkAnimatable> state) {
		if (state.getData(DataTickets.ENTITY) instanceof Player player) {
			HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
			if (s != null && s.combat.holding && !(s.animId == HulkState.ANIM_PICKUP && player.level().getGameTime() - s.animStart < 9)
					&& !ownsArms(s, player.level().getGameTime() - s.animStart)) {
				return state.setAndContinue(HOLD);
			}
		}
		return PlayState.STOP;
	}

	/**
	 * v0.16.4: a one-shot that moves both arms itself (the grab / rip lifting into the hold, the throws, the Loki slam,
	 * the death fall) -- the arms-only {@code hold} and {@code punch} layers stay off it while it plays.
	 */
	private static boolean ownsArms(HulkState s, long since) {
		return switch (s.animId) {
			case HulkState.ANIM_GRAB, HulkState.ANIM_DEBRIS_RIP, HulkState.ANIM_THROW_FAR, HulkState.ANIM_DEBRIS_THROW,
					HulkState.ANIM_LOKI_SLAM -> since >= 0L && since < oneShotTicks(s.animId);
			case HulkState.ANIM_DEATH_FALL -> since >= 0L && since < HulkPose.DEATH_FALL_HOLD_TICKS;
			default -> false;
		};
	}

	private PlayState swing(AnimationState<HulkAnimatable> state) {
		if (state.getData(DataTickets.ENTITY) instanceof Player owner) {
			HulkState hs = owner.getAttachedOrElse(ModAttachments.HULK_STATE, null);
			if (hs != null && ownsArms(hs, owner.level().getGameTime() - hs.animStart)) {
				state.getController().forceAnimationReset();
				return PlayState.STOP;
			}
		}
		if (state.getData(DataTickets.ENTITY) instanceof Player player && player.swinging
				&& !player.getAttachedOrElse(ModAttachments.HULK_STATE, new HulkState()).combat.holding) {
			boolean left = player.swingingArm != null && com.projecthero.mod.hulk.gladiator.GladiatorSwing.arm(player, player.swingingArm)
					== net.minecraft.world.entity.HumanoidArm.LEFT;
			return state.setAndContinue(left ? PUNCH_LEFT : PUNCH);
		}
		state.getController().forceAnimationReset();
		return PlayState.STOP;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

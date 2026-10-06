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
 * swing layers {@code punch} over the right arm.
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
	private static final RawAnimation LEAP = RawAnimation.begin().thenPlayAndHold(P + "leap");
	private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay(P + "transform");
	/** v0.13.15: the unwilling change -- on his knees, clutching his head, then up into the roar. */
	private static final RawAnimation TRANSFORM_FORCED = RawAnimation.begin().thenPlay(P + "transform_forced");
	private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay(P + "punch");
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

	/** Ticks each one-shot owns the body after it starts (a touch longer than the clip, so it finishes). */
	private static final int CLAP_TICKS = 14;
	private static final int SMASH_TICKS = 20;

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
		if (s.hulk && now - s.formChangedAt < com.projecthero.mod.hulk.Hulk.changeTicks(s)) {
			return state.setAndContinue(s.combat.unwilling ? TRANSFORM_FORCED : TRANSFORM);
		}
		if (s.leapChargeStart > 0L) {
			return state.setAndContinue(LEAP_CHARGE);
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
			if (s != null && s.combat.holding && !(s.animId == HulkState.ANIM_PICKUP && player.level().getGameTime() - s.animStart < 9)) {
				return state.setAndContinue(HOLD);
			}
		}
		return PlayState.STOP;
	}

	private PlayState swing(AnimationState<HulkAnimatable> state) {
		if (state.getData(DataTickets.ENTITY) instanceof Player player && player.swinging
				&& !player.getAttachedOrElse(ModAttachments.HULK_STATE, new HulkState()).combat.holding) {
			return state.setAndContinue(PUNCH);
		}
		state.getController().forceAnimationReset();
		return PlayState.STOP;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

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
	private static final RawAnimation JUMP = RawAnimation.begin().thenPlayAndHold(P + "jump");
	private static final RawAnimation LAND = RawAnimation.begin().thenPlay(P + "land");
	/** The land clip's length after its 4-tick transition (16 in the timing table). */
	private static final int LAND_TICKS = 16;

	static {
		for (String clip : new String[] { "punch_combo", "rocket_fist", "pile_driver", "earthquake_stomp", "grab_pound", "grab_throw",
				"chest_beam", "missile_swarm", "replacement_pod", "palm_cannon" }) { // v0.16.7 (user): + the Palm Cannon
			MOVES.put(clip, RawAnimation.begin().thenPlay(P + clip));
		}
		MOVES.put("grab", RawAnimation.begin().thenPlayAndHold(P + "grab"));
		MOVES.put("hydraulic_brace", RawAnimation.begin().thenPlayAndHold(P + "hydraulic_brace"));
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Per player: the start tick of the move clip playing (restart on a new one), air ticks, landing end. */
	private final Map<Integer, Long> moveStart = new HashMap<>();
	private final Map<Integer, Integer> airTicks = new HashMap<>();
	private final Map<Integer, Long> landUntil = new HashMap<>();
	private final Map<Integer, Long> lastSeen = new HashMap<>();

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
		controllers.add(new AnimationController<>(this, "main", 4, this::main));
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
		if (clip != null && MOVES.containsKey(clip)) {
			Long prev = moveStart.get(id);
			if (prev == null || prev != play.start()) {
				moveStart.put(id, play.start());
				state.getController().forceAnimationReset();
			}
			return state.setAndContinue(MOVES.get(clip));
		}
		moveStart.remove(id);
		// locomotion
		if (lastSeen.getOrDefault(id, -1L) != now) {
			lastSeen.put(id, now);
			if (player.onGround()) {
				if (airTicks.getOrDefault(id, 0) > 6) {
					landUntil.put(id, now + LAND_TICKS);
				}
				airTicks.put(id, 0);
			} else {
				airTicks.merge(id, 1, Integer::sum);
			}
		}
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			return state.setAndContinue(HOVER);
		}
		if (landUntil.getOrDefault(id, 0L) > now) {
			return state.setAndContinue(LAND);
		}
		if (!player.onGround() && !player.isInWater() && player.getDeltaMovement().y > 0.08) {
			return state.setAndContinue(JUMP);
		}
		if (state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(player.isSprinting() ? RUN : WALK);
		}
		return state.setAndContinue(IDLE);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

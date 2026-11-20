package com.projecthero.mod.client.symbiote.monster;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

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
 * v0.17.5 Symbiote Monster form: the monster body drawn in place of a transformed host (the Hulk's player-replacement trick). One
 * singleton; GeckoLib keys each player's animation state by entity id. The clip is a pure function of the synced
 * {@link SymbioteMonsterState} (so every viewer sees the same thing): {@code emerge} for the first 1.6 s out of the cocoon, a move's
 * clip while it plays (restarted when a new one is stamped; the pounce holds its air pose until the landing), {@code melt} in the
 * melt, else {@code run} / {@code walk} / {@code idle}.
 */
public final class SymbioteMonsterAnimatable implements GeoReplacedEntity {
	public static final SymbioteMonsterAnimatable INSTANCE = new SymbioteMonsterAnimatable();

	private static final String P = "animation.symbiote_monster.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	private static final RawAnimation RUN = RawAnimation.begin().thenLoop(P + "run");
	private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay(P + "emerge");
	private static final RawAnimation MELT = RawAnimation.begin().thenPlayAndHold(P + "melt");
	/** Move id -> {clip, length in ticks}. */
	private static final Map<Integer, RawAnimation> MOVES = new HashMap<>();
	private static final Map<Integer, Integer> LENGTH = new HashMap<>();
	public static final int EMERGE_TICKS = 32;

	static {
		move(SymbioteMonsterState.MOVE_SWIPE, "swipe", 20, false);
		move(SymbioteMonsterState.MOVE_POUND, "pound", 24, false);
		move(SymbioteMonsterState.MOVE_LEAP, "leap", 80, true);
		move(SymbioteMonsterState.MOVE_LAND, "land", 14, false);
		move(SymbioteMonsterState.MOVE_ROAR, "roar", 30, false);
		move(SymbioteMonsterState.MOVE_DEVOUR, "devour", 30, false);
		move(SymbioteMonsterState.MOVE_PULL, "pull", 20, false);
	}

	private static void move(int id, String clip, int ticks, boolean hold) {
		MOVES.put(id, hold ? RawAnimation.begin().thenPlayAndHold(P + clip) : RawAnimation.begin().thenPlay(P + clip));
		LENGTH.put(id, ticks);
	}

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Per player: the stamp of the clip playing (phase start / move start), so a new one restarts it. */
	private final Map<Integer, Long> started = new HashMap<>();

	private SymbioteMonsterAnimatable() {
	}

	/** Is {@code player} drawn as the monster? (the monster phase, and the melt until the body has gone) */
	public static boolean drawn(Player player) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		if (s.phase() == SymbioteMonsterState.MONSTER) {
			return true;
		}
		return s.phase() == SymbioteMonsterState.MELT && player.level().getGameTime() - s.phaseStart() < SymbioteMonster.MELT_TICKS - 6;
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return EntityType.PLAYER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::main));
	}

	private PlayState main(AnimationState<SymbioteMonsterAnimatable> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof Player player)) {
			return PlayState.STOP;
		}
		SymbioteMonsterState s = SymbioteMonster.state(player);
		long now = player.level().getGameTime();
		int id = player.getId();
		RawAnimation clip;
		long stamp;
		if (s.phase() == SymbioteMonsterState.MELT) {
			clip = MELT;
			stamp = s.phaseStart();
		} else if (now - s.phaseStart() < EMERGE_TICKS && s.move() == SymbioteMonsterState.MOVE_NONE) {
			clip = EMERGE;
			stamp = s.phaseStart();
		} else if (s.move() != SymbioteMonsterState.MOVE_NONE && MOVES.containsKey(s.move())
				&& now - s.moveStart() < LENGTH.get(s.move())) {
			clip = MOVES.get(s.move());
			stamp = s.moveStart() * 16 + s.move();
		} else {
			if (started.remove(id) != null) {
				state.getController().transitionLength(4);
			}
			if (state.getLimbSwingAmount() > 0.08f || state.isMoving()) {
				return state.setAndContinue(player.isSprinting() ? RUN : WALK);
			}
			return state.setAndContinue(IDLE);
		}
		Long prev = started.get(id);
		if (prev == null || prev != stamp) {
			started.put(id, stamp);
			state.getController().transitionLength(clip == MELT || clip == EMERGE ? 2 : 3);
			state.getController().forceAnimationReset();
		}
		return state.setAndContinue(clip);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

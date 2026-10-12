package com.projecthero.mod.client.supervillain;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.SupervillainAnims;
import com.projecthero.mod.event.entity.SupervillainAnims.Clip;
import com.projecthero.mod.event.entity.SupervillainVariant;

import net.minecraft.world.entity.EntityType;

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
 * v0.16.1: the GeckoLib stand-in that draws one Supervillain look ({@link SupervillainVariant}) over the vanilla-AI
 * {@link EmpoweredZombie} ({@link SupervillainGeoRenderer} is a {@code GeoReplacedEntityRenderer}, so the boss entity
 * itself stays a plain zombie and the ordinary Zombie Raid boss never touches GeckoLib). One instance per variant; GeckoLib
 * keys the animation state by entity id.
 *
 * <p>Clip choice: death, then the server-synced move ({@link EmpoweredZombie#clientAnim}), then locomotion -- airborne,
 * run / walk by ground speed, otherwise idle with an occasional idle variation ({@link SupervillainAnims#FIDGET}).
 */
public final class SupervillainAnimatable implements GeoReplacedEntity {
	private final SupervillainVariant variant;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final Map<EmpoweredZombie, State> states = new WeakHashMap<>();

	/** Per-boss client animation bookkeeping. */
	static final class State {
		int clipStart = Integer.MIN_VALUE;
		boolean running;
		int airTicks;
		int fidgetUntil = -1;
		int nextFidget = -1;
		float headWeight = 1.0f;
	}

	public SupervillainAnimatable(SupervillainVariant variant) {
		this.variant = variant;
	}

	public SupervillainVariant variant() {
		return variant;
	}

	State state(EmpoweredZombie boss) {
		return states.computeIfAbsent(boss, b -> new State());
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return RaidEntityTypes.EMPOWERED_ZOMBIE;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", SupervillainAnims.TRANSITION, this::predicate));
	}

	private final Map<Clip, RawAnimation> raw = new java.util.HashMap<>();

	private RawAnimation raw(Clip clip) {
		return raw.computeIfAbsent(clip, c -> c.loops() ? RawAnimation.begin().thenLoop(c.id(variant))
				: RawAnimation.begin().thenPlayAndHold(c.id(variant)));
	}

	private PlayState predicate(AnimationState<SupervillainAnimatable> state) {
		if (!(state.getData(DataTickets.ENTITY) instanceof EmpoweredZombie boss)) {
			return PlayState.STOP;
		}
		State st = state(boss);
		AnimationController<SupervillainAnimatable> controller = state.getController();
		Clip clip;
		int start;
		if (boss.isDeadOrDying()) {
			clip = SupervillainAnims.DEATH;
			start = -2;
		} else if (boss.clientAnim != null && boss.tickCount - boss.clientAnimStart < boss.clientAnim.length() + 20) {
			clip = boss.clientAnim;
			start = boss.clientAnimStart;
		} else {
			clip = locomotion(boss, st);
			start = clip == SupervillainAnims.FIDGET ? st.fidgetUntil : Integer.MIN_VALUE;
		}
		if (start != Integer.MIN_VALUE && start != st.clipStart) {
			controller.forceAnimationReset();
		}
		st.clipStart = start;
		return state.setAndContinue(raw(clip));
	}

	private Clip locomotion(EmpoweredZombie boss, State st) {
		double dx = boss.getX() - boss.xo;
		double dz = boss.getZ() - boss.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		boolean air = !boss.onGround() && !boss.isInWater() && (boss.isNoGravity() || Math.abs(boss.getY() - boss.yo) > 0.04);
		st.airTicks = air ? st.airTicks + 1 : 0;
		if (st.airTicks > 5) {
			st.fidgetUntil = -1;
			return SupervillainAnims.AIR;
		}
		st.running = st.running ? speed > 0.22 : speed > 0.27;
		if (speed > 0.025) {
			st.fidgetUntil = -1;
			st.nextFidget = boss.tickCount + 160 + boss.getRandom().nextInt(200);
			return st.running ? SupervillainAnims.RUN : SupervillainAnims.WALK;
		}
		if (st.fidgetUntil > boss.tickCount) {
			return SupervillainAnims.FIDGET;
		}
		if (st.nextFidget < 0) {
			st.nextFidget = boss.tickCount + 120 + boss.getRandom().nextInt(200);
		}
		if (boss.tickCount >= st.nextFidget && !boss.isAggressive()) {
			st.fidgetUntil = boss.tickCount + SupervillainAnims.FIDGET.length();
			st.nextFidget = st.fidgetUntil + 200 + boss.getRandom().nextInt(300);
			return SupervillainAnims.FIDGET;
		}
		return SupervillainAnims.IDLE;
	}
}

package com.projecthero.mod.event.entity;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.event.entity.RaidAnims.Clip;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * v0.16.1: the clip state of a {@link RaidAnimated} mob (the Powered Zombie Boss) -- the synced "what is it doing" id, the
 * server-side timing that keeps strikes on the AI's own attack ticks, and the two GeckoLib controllers (locomotion +
 * full-body clips on {@code main}, upper-body attacks / hurt on {@code action}).
 *
 * <h2>Predictive strikes, AI untouched</h2>
 * Vanilla's melee goal hits the moment its 20-tick cooldown runs out and the target is in reach. Nothing here changes
 * that: {@link #tickMelee} only tracks the tick of the last hit (from
 * {@link #onMeleeSwing}, called by the mob's {@code swing}) and, when the next hit is exactly {@code hit} ticks away with the
 * target in reach, starts the {@code attack} clip whose strike keyframe sits on that tick. A hit that comes with no wind-up
 * in flight (the target walked into reach on a ready cooldown) plays the short {@code *_snap} clip instead, from the held
 * {@code attack_ready} pose.
 */
public final class RaidAnimator {
	/** Vanilla MeleeAttackGoal's attack interval (ticks). */
	public static final int MELEE_INTERVAL = 20;

	private final Mob mob;
	private final EntityDataAccessor<Integer> key;
	private int seq;
	// server
	private Clip serverClip;
	private int ticksLeft;
	private int clipStartTick;
	private int lastHitTick = Integer.MIN_VALUE / 2;
	// client
	private Clip clientClip;
	private int clientClipStart;
	private int mainStart = Integer.MIN_VALUE;
	private int actionStart = Integer.MIN_VALUE;
	private Clip actionDerived;
	private int derivedStart;
	private int lastHurtTime;
	private boolean running;
	/** Client: how much the head follows the look target (eased by the renderer, 0..1). */
	public float headWeight = 1.0f;

	public RaidAnimator(Mob mob, EntityDataAccessor<Integer> key) {
		this.mob = mob;
		this.key = key;
	}

	public static void define(SynchedEntityData.Builder builder, EntityDataAccessor<Integer> key) {
		builder.define(key, 0x3FF);
	}

	private String model() {
		return ((RaidAnimated) mob).raidModel();
	}

	private Clip clip(String name) {
		return RaidAnims.find(model(), name);
	}

	// ---------------------------------------------------------------- server

	/** Server: play {@code clip} (a play-once clip is dropped after its length; holds and loops stay until replaced). */
	public void set(Clip clip) {
		seq = (seq + 1) & 0x3FFFFF;
		int idx = RaidAnims.indexOf(clip);
		mob.getEntityData().set(key, (idx < 0 ? 0x3FF : idx) | (seq << 10));
		serverClip = clip;
		clipStartTick = mob.tickCount;
		ticksLeft = clip == null || clip.loops() || clip.holds() ? 0 : clip.length();
	}

	/** Server: play the clip named {@code name} of this mob's model (no-op if it has none). */
	public void set(String name) {
		Clip c = clip(name);
		if (c != null) {
			set(c);
		}
	}

	public void clear() {
		if (serverClip != null) {
			set((Clip) null);
		}
	}

	public Clip current() {
		return RaidAnims.byIndex(mob.getEntityData().get(key) & 0x3FF);
	}

	/** Server, every tick: drops a finished play-once clip. */
	public void tick() {
		if (serverClip != null && ticksLeft > 0 && --ticksLeft == 0) {
			set((Clip) null);
		}
	}

	private boolean playing(String name) {
		return serverClip != null && serverClip.name().equals(name);
	}

	/** Server: the mob just swung at its target (vanilla MeleeAttackGoal's hit tick). */
	public void onMeleeSwing() {
		lastHitTick = mob.tickCount;
		if (serverClip != null && serverClip.layer() == RaidAnims.FULL) {
			return; // mid-cast / mid-charge: the full-body clip keeps playing
		}
		boolean windupInFlight = serverClip != null && serverClip.hit() > RaidAnims.TRANSITION
				&& mob.tickCount - clipStartTick <= serverClip.hit() + 2;
		if (!windupInFlight) {
			set("attack_snap");
		}
	}

	/**
	 * Server, every tick after the AI ran: start the melee wind-up so its strike lands on the goal's next hit, hold the
	 * ready pose while the cooldown is up and the target is close, drop it when the target leaves.
	 */
	public void tickMelee() {
		LivingEntity target = mob.getTarget();
		Clip attack = clip("attack");
		if (attack == null || target == null || !target.isAlive() || !mob.isAggressive()) {
			if (playing("attack_ready")) {
				clear();
			}
			return;
		}
		int cooldown = Math.max(0, lastHitTick + MELEE_INTERVAL - mob.tickCount);
		double reach = mob.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 0.9;
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		boolean inReach = flat < reach + 0.35 && Math.abs(target.getY() - mob.getY()) < 2.5;
		boolean busy = serverClip != null && serverClip.layer() == RaidAnims.FULL; // a charge, a cast, a spit ...
		if (busy) {
			return;
		}
		if (cooldown == attack.hit() && inReach) {
			set(attack);
			return;
		}
		if (cooldown == 0 && flat < reach + 2.5) {
			if (serverClip == null) {
				set("attack_ready");
			}
		} else if (playing("attack_ready")) {
			clear();
		}
	}

	// ---------------------------------------------------------------- client

	public void onSynced(EntityDataAccessor<?> changed) {
		if (key.equals(changed) && mob.level() != null && mob.level().isClientSide()) {
			clientClip = RaidAnims.byIndex(mob.getEntityData().get(key) & 0x3FF);
			clientClipStart = mob.tickCount;
		}
	}

	public Clip clientClip() {
		return clientClip;
	}

	private static final Map<Clip, RawAnimation> RAW = new HashMap<>();

	public static RawAnimation raw(Clip clip) {
		synchronized (RAW) {
			return RAW.computeIfAbsent(clip, c -> c.loops() ? RawAnimation.begin().thenLoop(c.id()) : RawAnimation.begin().thenPlayAndHold(c.id()));
		}
	}

	/** Ground speed (blocks/tick) each locomotion clip was authored for; the cycle plays faster/slower to match. */
	private static double nominalSpeed(String loco) {
		boolean fast = !loco.equals("walk");
		return fast ? 0.14 : 0.075; // the boss's walk / chase (raid_undead_clips.js bossClips)
	}

	public void registerControllers(AnimatableManager.ControllerRegistrar controllers, RaidAnimated owner) {
		controllers.add(new AnimationController<>(owner, "main", RaidAnims.TRANSITION, this::mainPredicate));
		controllers.add(new AnimationController<>(owner, "action", RaidAnims.TRANSITION, this::actionPredicate));
	}

	private <E extends RaidAnimated> PlayState mainPredicate(AnimationState<E> state) {
		AnimationController<E> controller = state.getController();
		Clip clip = mob.isDeadOrDying() ? clip("death") : clientClip != null && clientClip.layer() == RaidAnims.FULL ? clientClip : null;
		if (clip != null) {
			int start = mob.isDeadOrDying() ? Integer.MIN_VALUE + 1 : clientClipStart;
			if (start != mainStart) {
				mainStart = start;
				controller.forceAnimationReset();
			}
			controller.setAnimationSpeed(1.0);
			return state.setAndContinue(raw(clip));
		}
		mainStart = Integer.MIN_VALUE;
		double dx = mob.getX() - mob.xo;
		double dz = mob.getZ() - mob.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);
		String model = model();
		if (model.equals(RaidAnims.BOSS) && mob.isNoGravity() && !mob.onGround()) {
			controller.setAnimationSpeed(1.0);
			return state.setAndContinue(raw(clip("hover")));
		}
		if (speed > 0.015) {
			boolean fastAvail = clip("chase") != null || clip("run") != null;
			running = fastAvail && (running ? speed > 0.06 : mob.isAggressive() && speed > 0.075 || speed > 0.16);
			String loco = running ? (clip("chase") != null ? "chase" : "run") : "walk";
			controller.setAnimationSpeed(Math.max(0.55, Math.min(2.0, speed / nominalSpeed(loco))));
			return state.setAndContinue(raw(clip(loco)));
		}
		running = false;
		controller.setAnimationSpeed(1.0);
		return state.setAndContinue(raw(clip("idle")));
	}

	private <E extends RaidAnimated> PlayState actionPredicate(AnimationState<E> state) {
		AnimationController<E> controller = state.getController();
		if (mob.isDeadOrDying()) {
			actionStart = Integer.MIN_VALUE;
			return PlayState.STOP;
		}
		if (clientClip != null && clientClip.layer() == RaidAnims.UPPER) {
			if (clientClipStart != actionStart) {
				actionStart = clientClipStart;
				controller.forceAnimationReset();
			}
			actionDerived = null;
			return state.setAndContinue(raw(clientClip));
		}
		// client-derived: the hurt flinch
		Clip derived = null;
		if (mob.hurtTime > lastHurtTime) {
			derivedStart = mob.tickCount;
		}
		lastHurtTime = mob.hurtTime;
		if (mob.hurtTime > 0 && mob.tickCount - derivedStart < 10) {
			derived = clip("hurt");
		}
		if (derived == null) {
			actionDerived = null;
			actionStart = Integer.MIN_VALUE;
			return PlayState.STOP;
		}
		int start = derived.name().equals("hurt") ? derivedStart : 0;
		if (derived != actionDerived || start != actionStart) {
			actionDerived = derived;
			actionStart = start;
			controller.forceAnimationReset();
		}
		return state.setAndContinue(raw(derived));
	}
}

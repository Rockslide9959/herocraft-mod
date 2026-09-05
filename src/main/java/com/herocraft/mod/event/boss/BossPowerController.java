package com.herocraft.mod.event.boss;

import java.util.List;
import java.util.function.Consumer;

import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss-side implementation of one Experimental Power.
 *
 * <h2>Why an abstraction rather than reusing the player handlers directly</h2>
 * The player ability system is built entirely around a {@code ServerPlayer}: {@code AbilityContext},
 * {@code ExperimentalPowers} state, cooldowns keyed to attachment data, {@code AbilityHelpers} damage
 * gated on PvP rules and the caster being a player. None of that exists for a mob, and bending it to
 * accept one would mean touching every one of the 27 player power files -- exactly what the brief
 * forbids ("do not break any existing superhero powers"). So a boss gets a <em>boss-compatible
 * version</em> of the power instead: same identity, same name, same flavour, its own combat logic.
 * The player's version is untouched, and a broken boss can never break a player's power.
 *
 * <h2>What a controller is responsible for</h2>
 * Only the power. Target selection, target switching, airborne response, ranged-attacker response and
 * approach/retreat movement are the same for every power and live once in {@link EmpoweredZombie}.
 * A controller supplies its preferred fighting distance, decides when its abilities are worth using,
 * and executes them.
 *
 * <p>Controllers must not spam. Every ability goes through {@link #ready}/{@link #startCooldown},
 * and {@link #tick} is called on the boss's own throttled cadence, not every game tick.
 */
public abstract class BossPowerController {
	/** Plenty for any one power; indexed by the controller's own private slot constants. */
	private static final int COOLDOWN_SLOTS = 6;

	protected final EmpoweredZombie boss;
	private final int[] cooldowns = new int[COOLDOWN_SLOTS];
	/**
	 * A short shared lockout after <em>any</em> ability fires. It is what makes the boss read as using
	 * one deliberate, telegraphed move at a time -- pick an ability, commit, recover -- instead of
	 * dumping every off-cooldown ability in the same cadence. Shorter when the boss is badly hurt, so a
	 * cornered boss visibly ramps up. Reactive ({@link #onDamaged}) branches use {@link #readyReactive}
	 * to bypass it.
	 */
	private int globalCd;
	/** The last slot that fired, for a light anti-repeat bias in {@link #freshChoice}. */
	private int lastSlot = -1;

	/** Telegraphed ranged cast in progress: cycles left, what to fire, and the original target. */
	private int castTicks;
	private Consumer<LivingEntity> pendingCast;
	private LivingEntity castTarget;

	protected BossPowerController(EmpoweredZombie boss) {
		this.boss = boss;
	}

	/** The Experimental Power key this controller implements, e.g. {@code power_05_geokinesis}. */
	public abstract String powerKey();

	/** Distance the boss tries to hold against its current target. */
	public abstract double preferredRange();

	/** Called on the boss's ability cadence while it has a live target. */
	public abstract void tick(ServerLevel level, LivingEntity target);

	/** Aura particle for this power. Kept to a small count per emission by the boss itself. */
	public ParticleOptions auraParticle() {
		return ParticleTypes.SOUL_FIRE_FLAME;
	}

	/** Boss bar colour. */
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.PURPLE;
	}

	/** Reaction hook: called after the boss has taken damage. Default does nothing. */
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
	}

	/** Called once when the boss enters the world. */
	public void onSpawn(ServerLevel level) {
	}

	/**
	 * Powers that cannot safely be combined with this one on a dual-power final boss. Default is
	 * "anything but myself"; override to be stricter.
	 */
	public boolean compatibleWith(String otherPowerKey) {
		return !otherPowerKey.equals(powerKey());
	}

	// ---------------- telegraphed ranged casts ----------------

	/**
	 * Commit to a telegraphed ranged attack. The boss plants itself and visibly winds up -- a heavy
	 * Slowness, a charge-up sound and a particle line drawn to the target -- for two ability cycles
	 * (~1s), then {@code fire} runs, re-aimed at wherever the target is by then. A boss that is given
	 * that second actually connects (the design's "his aim should be better, he shouldn't miss a lot"),
	 * while a player who reads the wind-up still has a full second to break line of sight or step out of
	 * it. The slot's cooldown starts now, so the wind-up itself is on the clock.
	 */
	protected final void beginRangedCast(ServerLevel level, LivingEntity target, int slot, int baseCd,
			SoundEvent chargeSound, ParticleOptions telegraph, Consumer<LivingEntity> fire) {
		boss.getNavigation().stop();
		boss.setDeltaMovement(boss.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 5, false, false));
		sound(level, chargeSound, 1.1f, 0.7f);
		particleLine(level, telegraph, boss.getEyePosition(),
				target.position().add(0, target.getBbHeight() * 0.5, 0), 1.5);
		particles(level, telegraph, boss.getEyePosition(), 12, 0.3);
		castTicks = 2;
		castTarget = target;
		pendingCast = fire;
		startCooldown(slot, baseCd);
	}

	/**
	 * Called by {@link EmpoweredZombie} before {@link #tick}. If a telegraphed cast is pending, keep the
	 * boss planted and slowed, tick the wind-up down, and fire when it resolves -- looking at and aiming
	 * at the current target so the shot is accurate. Returns true whenever a cast was in progress this
	 * cycle, so the boss does not also run {@link #tick}.
	 */
	public final boolean resolvePendingCast(ServerLevel level, LivingEntity currentTarget) {
		if (castTicks <= 0 || pendingCast == null) {
			return false;
		}
		boss.getNavigation().stop();
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 16, 5, false, false));
		castTicks--;
		if (castTicks > 0) {
			return true;
		}
		Consumer<LivingEntity> fire = pendingCast;
		LivingEntity aimAt = currentTarget != null && currentTarget.isAlive() ? currentTarget
				: (castTarget != null && castTarget.isAlive() ? castTarget : null);
		pendingCast = null;
		castTarget = null;
		if (aimAt != null) {
			boss.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, aimAt.getEyePosition());
			fire.accept(aimAt);
		}
		return true;
	}

	/** True while a telegraphed cast is winding up -- controllers can check this to stay passive. */
	protected final boolean casting() {
		return castTicks > 0;
	}

	// ---------------- cooldowns ----------------

	/** Ticked by the boss once per ability cadence, with the number of ticks that actually elapsed. */
	public final void tickCooldowns(int elapsed) {
		if (globalCd > 0) {
			globalCd -= elapsed;
		}
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i] -= elapsed;
			}
		}
	}

	/**
	 * Whether a proactive ability in {@code slot} may fire: its own cooldown is up <em>and</em> the
	 * shared post-ability lockout has expired. This is the gate every {@link #tick} branch uses.
	 */
	protected final boolean ready(int slot) {
		return cooldowns[slot] <= 0 && globalCd <= 0;
	}

	/**
	 * Cooldown check that ignores the shared lockout, for emergency reactions in {@link #onDamaged}:
	 * a Brace or a blink-out should still answer a burst of damage even if the boss attacked a moment
	 * ago.
	 */
	protected final boolean readyReactive(int slot) {
		return cooldowns[slot] <= 0;
	}

	/**
	 * Anti-repeat helper: true unless {@code slot} is the ability that fired last -- and even then true
	 * one time in three, so the boss can still repeat a move when it is clearly the right one. Use it to
	 * break ties between two abilities that are both available and both sensible right now.
	 */
	protected final boolean freshChoice(int slot) {
		return slot != lastSlot || random().nextInt(3) == 0;
	}

	/**
	 * The anchor for an area ability: the centre of a cluster of {@code clusterMin}+ nearby players if
	 * one exists, otherwise the lone {@code target} when it is within {@code radius}. This is what lets
	 * every "used when players bunch up" ability also fire in a one-on-one fight, which is where the
	 * bosses previously looked like they only had a single move.
	 */
	protected final Vec3 areaAnchor(ServerLevel level, LivingEntity target, double radius, int clusterMin) {
		Vec3 cluster = clusterCenter(level, radius, clusterMin);
		if (cluster != null) {
			return cluster;
		}
		if (target != null && target.isAlive() && boss.distanceTo(target) <= radius) {
			return target.position();
		}
		return null;
	}

	/** Boss health at or below this fraction -- controllers use it to escalate. */
	protected final boolean lowHealth() {
		return healthFraction() <= 0.4f;
	}

	/**
	 * Start an ability cooldown, scaled by the configured boss cooldown multiplier, by the group-size
	 * pressure bonus, and (for a wave-12 boss) by the final-boss multiplier. This is the single place
	 * "slightly increase ability frequency for larger groups" and "final boss has shorter cooldowns"
	 * are applied, so no individual power has to remember to do it.
	 */
	protected final void startCooldown(int slot, int baseTicks) {
		cooldowns[slot] = Math.max(5, (int) Math.round(baseTicks * boss.cooldownScale()));
		lastSlot = slot;
		// Shared recovery between abilities: ~1s normally, ~0.6s once the boss is badly hurt.
		globalCd = healthFraction() <= 0.33f ? 12 : 22;
	}

	protected final void clearCooldown(int slot) {
		cooldowns[slot] = 0;
	}

	// ---------------- shared helpers ----------------

	protected final RandomSource random() {
		return boss.getRandom();
	}

	protected final float healthFraction() {
		return boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
	}

	protected final boolean hurt(LivingEntity target, float amount) {
		// Spec section 33: a boss-scaling layer on top of the reused ability damage, so a player power
		// that would delete a player in one hit when a boss uses it stays fair. 1.0 for the Zombie Raid.
		return target.hurt(boss.damageSources().mobAttack(boss), amount * boss.abilityDamageScale());
	}

	/** Push {@code target} away from {@code origin}, horizontally, plus a little lift. */
	protected final void knockAway(LivingEntity target, Vec3 origin, double strength, double lift) {
		Vec3 away = target.position().subtract(origin);
		if (away.lengthSqr() < 1.0e-4) {
			away = new Vec3(random().nextDouble() - 0.5, 0.0, random().nextDouble() - 0.5);
		}
		away = new Vec3(away.x, 0.0, away.z).normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().add(away.x, lift, away.z));
		target.hurtMarked = true;
	}

	/**
	 * Players within {@code radius} of the boss. Bounded AABB query on the boss's own position, run
	 * only when an ability is actually considering an area effect -- never on a plain tick.
	 */
	protected final List<Player> playersNear(ServerLevel level, double radius) {
		return level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(radius),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
	}

	/**
	 * The centre of the tightest cluster of nearby players, or {@code null} if fewer than
	 * {@code minCount} are close enough together. Drives "use AoE abilities against clustered players"
	 * without needing real clustering: for a raid-sized group, the centroid of everyone within the
	 * ability's own radius is the same answer for far less work.
	 */
	protected final Vec3 clusterCenter(ServerLevel level, double radius, int minCount) {
		List<Player> nearby = playersNear(level, radius);
		if (nearby.size() < minCount) {
			return null;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		for (Player p : nearby) {
			x += p.getX();
			y += p.getY();
			z += p.getZ();
		}
		int n = nearby.size();
		return new Vec3(x / n, y / n, z / n);
	}

	protected final void sound(ServerLevel level, SoundEvent event, float volume, float pitch) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), event, SoundSource.HOSTILE, volume, pitch);
	}

	protected final void particles(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread) {
		level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
	}

	protected final void particleLine(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double perBlock) {
		double length = from.distanceTo(to);
		int steps = Math.min(60, Math.max(1, (int) (length * perBlock)));
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, (double) i / steps);
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	protected final AABB box(Vec3 center, double radius) {
		return new AABB(center.x - radius, center.y - radius, center.z - radius,
				center.x + radius, center.y + radius, center.z + radius);
	}

	/** Convenience for controllers that want to read raid tuning directly. */
	protected final EventConfig.ZombieRaid config() {
		return EventConfig.raid();
	}
}

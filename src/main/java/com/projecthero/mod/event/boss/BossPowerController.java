package com.projecthero.mod.event.boss;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.revamp.BatchBScheduler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The boss-side implementation of one Experimental Power (mutation).
 *
 * <h2>v0.14.21: the bosses fight with the revamped kits</h2>
 * Every one of the mutations has a controller, and each one runs three to five of that power's <em>current</em>
 * abilities -- the v0.14.1 / v0.14.5 eight-ability kits, named by the player ability ids in
 * {@link com.projecthero.mod.hero.PowerCatalog} ({@link #abilityIds}) -- instead of the old pre-revamp moves. The
 * player handlers themselves cannot run on a mob: every one of them is built on a {@code ServerPlayer} (the
 * {@code AbilityContext}, attachment state, key input, client-drawn animations), so each controller is a faithful mob
 * adaptation that uses the same mechanics, the same server-side visuals (particles, sounds, the Laser Vision beam
 * payload, the power's own projectile entities where they accept a mob owner) and the player's balance numbers scaled
 * for a boss.
 *
 * <h2>What a controller is responsible for</h2>
 * Only the power. Target selection, airborne / ranged responses and approach/retreat are the same for every power and
 * live once in {@link EmpoweredZombie}. A controller supplies its preferred fighting distance, decides when an ability is
 * worth using, and executes it.
 *
 * <h2>Who it may hit</h2>
 * Every damage, effect and shove a controller applies goes through {@link BossTargets#isVictim}: players and their
 * allies (pets, summons, golems), never another raid mob.
 *
 * <p>Controllers must not spam. Every ability goes through {@link #ready}/{@link #startCooldown}, and {@link #tick} is
 * called on the boss's own throttled cadence, not every game tick.
 */
public abstract class BossPowerController {
	/** Plenty for any one power; indexed by the controller's own private slot constants. */
	private static final int COOLDOWN_SLOTS = 8;

	protected final EmpoweredZombie boss;
	private final int[] cooldowns = new int[COOLDOWN_SLOTS];
	/**
	 * A short shared lockout after <em>any</em> ability fires: pick an ability, commit, recover. Shorter when the boss
	 * is badly hurt, so a cornered boss visibly ramps up. Reactive ({@link #onDamaged}) branches use
	 * {@link #readyReactive} to bypass it.
	 */
	private int globalCd;
	/** The last slot that fired, for a light anti-repeat bias in {@link #freshChoice}. */
	private int lastSlot = -1;

	/** Telegraphed ranged cast in progress: cycles left, what to fire, and the original target. */
	private int castTicks;
	private Consumer<LivingEntity> pendingCast;
	private LivingEntity castTarget;

	/** Delayed follow-ups (an eruption a beat after its telegraph), counted in ability cycles. */
	private final List<Scheduled> scheduled = new ArrayList<>();

	/** Bookkeeping for tests and the debug command: which of the power's abilities this boss has used. */
	private int abilityUses;
	private String lastAbility = "";
	private final Set<String> usedAbilities = new LinkedHashSet<>();

	protected BossPowerController(EmpoweredZombie boss) {
		this.boss = boss;
	}

	/** The Experimental Power key this controller implements, e.g. {@code power_05_geokinesis}. */
	public abstract String powerKey();

	/**
	 * The player ability ids (from {@link com.projecthero.mod.hero.PowerCatalog}) this boss uses, indexed by its own
	 * cooldown slot constants. A passive-only power (Super Regeneration) lists its passive ids instead.
	 */
	public abstract List<String> abilityIds();

	/** Distance the boss tries to hold against its current target. */
	public abstract double preferredRange();

	/** Called on the boss's ability cadence while it has a live target. */
	public abstract void tick(ServerLevel level, LivingEntity target);

	/**
	 * Called every game tick (target may be null). For the few abilities that need per-tick motion or a held beam;
	 * default does nothing. Keep it cheap.
	 */
	public void serverTick(ServerLevel level, LivingEntity target) {
	}

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

	/**
	 * Damage hook: called before the boss takes damage; return the (possibly reduced) amount. Default unchanged. Used by
	 * the defensive abilities (Earth Armor, Density, Absorption Shield ...).
	 */
	public float modifyIncomingDamage(DamageSource source, float amount) {
		return amount;
	}

	/** Called once when the boss enters the world. */
	public void onSpawn(ServerLevel level) {
	}

	/**
	 * Powers that cannot safely be combined with this one on a dual-power final boss. Default is "anything but myself";
	 * override to be stricter.
	 */
	public boolean compatibleWith(String otherPowerKey) {
		return !otherPowerKey.equals(powerKey());
	}

	// ---------------- ability bookkeeping ----------------

	/** How many abilities this controller has fired. */
	public final int abilityUses() {
		return abilityUses;
	}

	/** The id of the last ability fired, or "" if none yet. */
	public final String lastAbility() {
		return lastAbility;
	}

	/** Every distinct ability id fired so far, in order of first use. */
	public final Set<String> usedAbilities() {
		return java.util.Collections.unmodifiableSet(usedAbilities);
	}

	private void recordUse(int slot) {
		List<String> ids = abilityIds();
		String id = slot >= 0 && slot < ids.size() ? ids.get(slot) : "slot" + slot;
		abilityUses++;
		lastAbility = id;
		usedAbilities.add(id);
	}

	// ---------------- telegraphed ranged casts ----------------

	/**
	 * Commit to a telegraphed ranged attack. The boss plants itself and visibly winds up -- a heavy Slowness, a
	 * charge-up sound and a particle line drawn to the target -- for two ability cycles (~1s), then {@code fire} runs,
	 * re-aimed at wherever the target is by then. The slot's cooldown starts now, so the wind-up itself is on the clock.
	 */
	protected final void beginRangedCast(ServerLevel level, LivingEntity target, int slot, int baseCd,
			SoundEvent chargeSound, ParticleOptions telegraph, Consumer<LivingEntity> fire) {
		beginCast(level, target, slot, baseCd, 2, chargeSound, telegraph, fire);
	}

	/** {@link #beginRangedCast} with an explicit wind-up length in ability cycles (1 cycle = 0.5 s). */
	protected final void beginCast(ServerLevel level, LivingEntity target, int slot, int baseCd, int cycles,
			SoundEvent chargeSound, ParticleOptions telegraph, Consumer<LivingEntity> fire) {
		boss.getNavigation().stop();
		boss.setDeltaMovement(boss.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12 * cycles + 4, 5, false, false));
		if (chargeSound != null) {
			sound(level, chargeSound, 1.1f, 0.7f);
		}
		if (telegraph != null) {
			particleLine(level, telegraph, boss.getEyePosition(), mid(target), 1.5);
			particles(level, telegraph, boss.getEyePosition(), 12, 0.3);
		}
		castTicks = Math.max(1, cycles);
		castTarget = target;
		pendingCast = fire;
		startCooldown(slot, baseCd);
	}

	/**
	 * Called by {@link EmpoweredZombie} before {@link #tick}. If a telegraphed cast is pending, keep the boss planted,
	 * tick the wind-up down, and fire when it resolves at the current target. Returns true whenever a cast was in
	 * progress this cycle, so the boss does not also run {@link #tick}.
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

	// ---------------- scheduling ----------------

	/** Run {@code action} after {@code cycles} ability cycles (1 cycle = 10 ticks), even if the target is lost. */
	protected final void schedule(int cycles, Runnable action) {
		scheduled.add(new Scheduled(Math.max(1, cycles), action));
	}

	/** Ticked by the boss once per ability cadence, before anything else. */
	public final void tickScheduled() {
		if (scheduled.isEmpty()) {
			return;
		}
		List<Runnable> due = new ArrayList<>();
		for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext();) {
			Scheduled s = it.next();
			if (--s.cycles <= 0) {
				due.add(s.action);
				it.remove();
			}
		}
		if (boss.isAlive()) {
			due.forEach(Runnable::run);
		}
	}

	private static final class Scheduled {
		int cycles;
		final Runnable action;

		Scheduled(int cycles, Runnable action) {
			this.cycles = cycles;
			this.action = action;
		}
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

	/** Whether a proactive ability in {@code slot} may fire: its own cooldown is up and the shared lockout has expired. */
	protected final boolean ready(int slot) {
		return cooldowns[slot] <= 0 && globalCd <= 0;
	}

	/** Cooldown check that ignores the shared lockout, for emergency reactions in {@link #onDamaged}. */
	protected final boolean readyReactive(int slot) {
		return cooldowns[slot] <= 0;
	}

	/**
	 * Anti-repeat helper: true unless {@code slot} is the ability that fired last -- and even then true one time in
	 * three.
	 */
	protected final boolean freshChoice(int slot) {
		return slot != lastSlot || random().nextInt(3) == 0;
	}

	/**
	 * The anchor for an area ability: the centre of a cluster of {@code clusterMin}+ nearby victims if one exists,
	 * otherwise the lone {@code target} when it is within {@code radius}.
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
	 * Start an ability cooldown, scaled by the configured boss cooldown multiplier, by the group-size pressure bonus,
	 * and (for a wave-12 boss) by the final-boss multiplier. Also records the ability as used.
	 */
	protected final void startCooldown(int slot, int baseTicks) {
		cooldowns[slot] = Math.max(5, (int) Math.round(baseTicks * boss.cooldownScale()));
		lastSlot = slot;
		recordUse(slot);
		// Shared recovery between abilities: ~1s normally, ~0.6s once the boss is badly hurt.
		globalCd = healthFraction() <= 0.33f ? 12 : 22;
	}

	protected final void clearCooldown(int slot) {
		cooldowns[slot] = 0;
	}

	// ---------------- victims ----------------

	protected final boolean isVictim(Entity e) {
		return BossTargets.isVictim(boss, e);
	}

	/** Victims within {@code radius} of the boss (sphere). */
	protected final List<LivingEntity> victimsNear(ServerLevel level, double radius) {
		return victimsAround(level, boss.position(), radius);
	}

	/** Victims within {@code radius} of {@code center} (sphere, measured to the victim's feet-to-chest). */
	protected final List<LivingEntity> victimsAround(ServerLevel level, Vec3 center, double radius) {
		double r2 = radius * radius;
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : BossTargets.victims(level, boss, box(center, radius + 1.0))) {
			Vec3 at = e.position().add(0, Math.min(1.0, e.getBbHeight() * 0.5), 0);
			if (at.distanceToSqr(center) <= r2) {
				out.add(e);
			}
		}
		return out;
	}

	/** Victims whose body is within {@code width} of the segment {@code from}-{@code to}. */
	protected final List<LivingEntity> victimsOnSegment(ServerLevel level, Vec3 from, Vec3 to, double width) {
		Vec3 midPoint = from.add(to).scale(0.5);
		double half = from.distanceTo(to) * 0.5 + width + 1.0;
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : BossTargets.victims(level, boss, box(midPoint, half))) {
			double d = Math.min(distanceToSegment(e.getEyePosition(), from, to),
					distanceToSegment(e.position().add(0, e.getBbHeight() * 0.5, 0), from, to));
			if (d <= width + e.getBbWidth() * 0.5) {
				out.add(e);
			}
		}
		return out;
	}

	/** Victims inside a cone from {@code origin} along {@code dir} (normalised), within {@code range}. */
	protected final List<LivingEntity> victimsInCone(ServerLevel level, Vec3 origin, Vec3 dir, double range, double halfAngleDeg) {
		double minDot = Math.cos(Math.toRadians(halfAngleDeg));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : victimsAround(level, origin, range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
			double len = to.length();
			if (len < 1.5 || to.scale(1.0 / len).dot(dir) >= minDot) {
				out.add(e);
			}
		}
		return out;
	}

	/** Players among the victims near the boss -- for "is there a crowd" decisions. */
	protected final List<Player> playersNear(ServerLevel level, double radius) {
		return level.getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(radius),
				p -> BossTargets.isVictim(boss, p));
	}

	/**
	 * The centre of the nearby victims, or {@code null} if fewer than {@code minCount} are close enough. Drives "use
	 * AoE abilities against clustered players" without real clustering.
	 */
	protected final Vec3 clusterCenter(ServerLevel level, double radius, int minCount) {
		List<LivingEntity> nearby = victimsNear(level, radius);
		if (nearby.size() < minCount) {
			return null;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		for (LivingEntity p : nearby) {
			x += p.getX();
			y += p.getY();
			z += p.getZ();
		}
		int n = nearby.size();
		return new Vec3(x / n, y / n, z / n);
	}

	// ---------------- shared helpers ----------------

	protected final RandomSource random() {
		return boss.getRandom();
	}

	protected final float healthFraction() {
		return boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
	}

	/**
	 * Damage {@code target} with the boss as the attacker, scaled by {@link EmpoweredZombie#abilityDamageScale}. Does
	 * nothing (returns false) for anything that is not a {@link BossTargets#isVictim victim}.
	 */
	protected final boolean hurt(LivingEntity target, float amount) {
		if (!isVictim(target)) {
			return false;
		}
		return target.hurt(boss.damageSources().mobAttack(boss), amount * boss.abilityDamageScale());
	}

	/** {@link #hurt} that bypasses the victim's post-hit invulnerability window -- for multi-hit flurries. */
	protected final boolean hurtFresh(LivingEntity target, float amount) {
		if (!isVictim(target)) {
			return false;
		}
		target.invulnerableTime = 0;
		return hurt(target, amount);
	}

	/** Apply a status effect to a victim. */
	protected final void effect(LivingEntity target, Holder<MobEffect> effect, int ticks, int amplifier) {
		if (isVictim(target)) {
			target.addEffect(new MobEffectInstance(effect, ticks, amplifier), boss);
		}
	}

	/** Set a victim on fire for {@code seconds}. */
	protected final void ignite(LivingEntity target, int seconds) {
		if (isVictim(target)) {
			target.igniteForSeconds(seconds);
		}
	}

	/** Push {@code target} away from {@code origin}, horizontally, plus a little lift. */
	protected final void knockAway(LivingEntity target, Vec3 origin, double strength, double lift) {
		if (!isVictim(target)) {
			return;
		}
		Vec3 away = target.position().subtract(origin);
		if (away.lengthSqr() < 1.0e-4) {
			away = new Vec3(random().nextDouble() - 0.5, 0.0, random().nextDouble() - 0.5);
		}
		double kbRes = 1.0 - Mth.clamp(target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0) * 0.6;
		away = new Vec3(away.x, 0.0, away.z).normalize().scale(strength * kbRes);
		target.setDeltaMovement(target.getDeltaMovement().add(away.x, lift, away.z));
		target.hurtMarked = true;
	}

	/** Pull {@code target} toward {@code point}. */
	protected final void pullToward(LivingEntity target, Vec3 point, double strength, double lift) {
		if (!isVictim(target)) {
			return;
		}
		Vec3 to = point.subtract(target.position());
		if (to.lengthSqr() < 1.0e-4) {
			return;
		}
		to = new Vec3(to.x, 0.0, to.z).normalize().scale(strength);
		target.setDeltaMovement(to.x, Math.max(target.getDeltaMovement().y, lift), to.z);
		target.hurtMarked = true;
	}

	/** Set a victim's velocity outright (launches, slams). */
	protected final void fling(LivingEntity target, Vec3 velocity) {
		if (!isVictim(target)) {
			return;
		}
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;
	}

	/** Drive the boss itself along {@code dir} (normalised) at {@code speed} blocks/tick, keeping a little lift. */
	protected final void dash(Vec3 dir, double speed, double lift) {
		Vec3 v = new Vec3(dir.x, 0.0, dir.z);
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		v = v.normalize().scale(speed);
		boss.setDeltaMovement(v.x, lift, v.z);
		boss.hasImpulse = true;
		boss.hurtMarked = true;
		boss.getNavigation().stop();
	}

	/** Flat direction from the boss to {@code target}. */
	protected final Vec3 flatDirTo(Vec3 target) {
		Vec3 d = target.subtract(boss.position());
		d = new Vec3(d.x, 0.0, d.z);
		return d.lengthSqr() < 1.0e-6 ? boss.getLookAngle() : d.normalize();
	}

	/** Mid-body point of an entity. */
	protected static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/**
	 * A safe place for the boss to stand near {@code wanted}: the first spot (searching down then up a few blocks) with
	 * room for its hitbox and solid ground below, or {@code null}.
	 */
	protected final Vec3 safeSpotNear(ServerLevel level, Vec3 wanted) {
		BlockPos base = BlockPos.containing(wanted);
		for (int dy = 0; dy <= 6; dy++) {
			for (int sign : new int[] { -1, 1 }) {
				BlockPos pos = base.offset(0, sign * dy, 0);
				Vec3 at = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
				AABB moved = boss.getBoundingBox().move(at.subtract(boss.position()));
				if (level.noCollision(boss, moved) && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
						&& !level.containsAnyLiquid(moved)) {
					return at;
				}
				if (dy == 0) {
					break;
				}
			}
		}
		return null;
	}

	/** Teleport the boss to {@code to} with portal particles at both ends. */
	protected final void blinkTo(ServerLevel level, Vec3 to, ParticleOptions particle, SoundEvent sound) {
		Vec3 from = boss.position();
		particles(level, particle, from.add(0, 1.2, 0), 24, 0.5);
		boss.teleportTo(to.x, to.y, to.z);
		boss.getNavigation().stop();
		boss.fallDistance = 0.0f;
		particles(level, particle, to.add(0, 1.2, 0), 24, 0.5);
		if (sound != null) {
			level.playSound(null, from.x, from.y, from.z, sound, SoundSource.HOSTILE, 1.0f, 1.0f);
			level.playSound(null, to.x, to.y, to.z, sound, SoundSource.HOSTILE, 1.0f, 1.0f);
		}
	}

	protected final void sound(ServerLevel level, SoundEvent event, float volume, float pitch) {
		level.playSound(null, boss.getX(), boss.getY(), boss.getZ(), event, SoundSource.HOSTILE, volume, pitch);
	}

	protected final void sound(ServerLevel level, Holder<SoundEvent> event, float volume, float pitch) {
		sound(level, event.value(), volume, pitch);
	}

	protected final void soundAt(ServerLevel level, Vec3 at, Holder<SoundEvent> event, float volume, float pitch) {
		soundAt(level, at, event.value(), volume, pitch);
	}

	protected final void soundAt(ServerLevel level, Vec3 at, SoundEvent event, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, event, SoundSource.HOSTILE, volume, pitch);
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

	/** A flat ring of particles on the ground -- the "something lands here" telegraph. */
	protected final void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2.0 * i) / points;
			level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y + 0.15, center.z + Math.sin(a) * radius,
					1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	protected final AABB box(Vec3 center, double radius) {
		return new AABB(center.x - radius, center.y - radius, center.z - radius,
				center.x + radius, center.y + radius, center.z + radius);
	}

	protected static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSq = ab.lengthSqr();
		if (lengthSq < 1.0e-6) {
			return point.distanceTo(a);
		}
		double t = Math.max(0.0, Math.min(1.0, point.subtract(a).dot(ab) / lengthSq));
		return point.distanceTo(a.add(ab.scale(t)));
	}

	// ---------------- boss balance + strike helpers ----------------

	/**
	 * v0.14.21 balance rule: a player ability's damage number is tuned for a player hitting mobs (often 20-60). A boss
	 * uses it against players, so it deals {@value #BOSS_DAMAGE_FACTOR} of the player value, capped at
	 * {@value #BOSS_DAMAGE_CAP} for one hit (ultimates are telegraphed and fall off with distance on top of that).
	 */
	public static final float BOSS_DAMAGE_FACTOR = 0.5f;
	public static final float BOSS_DAMAGE_CAP = 20.0f;

	/** The boss version of a player ability's damage value (see {@link #BOSS_DAMAGE_FACTOR}). */
	public static float bossDamage(float playerDamage) {
		return Math.min(BOSS_DAMAGE_CAP, Math.round(playerDamage * BOSS_DAMAGE_FACTOR * 2.0f) / 2.0f);
	}

	/**
	 * Crowd control the way the player powers apply it ({@code AbilityHelpers.applyControl}): half duration (min 10
	 * ticks) on players, full on their pets.
	 */
	protected final void control(LivingEntity target, Holder<MobEffect> effect, int ticks, int amplifier) {
		effect(target, effect, target instanceof Player ? Math.max(10, ticks / 2) : ticks, amplifier);
	}

	/**
	 * Hit every victim within {@code radius} of {@code center}: damage (optionally falling off to 45% at the edge),
	 * knockback away from the centre and a lift. Returns the victims hit.
	 */
	protected final List<LivingEntity> strikeArea(ServerLevel level, Vec3 center, double radius, float damage, boolean falloff,
			double knockback, double lift) {
		List<LivingEntity> hit = victimsAround(level, center, radius);
		for (LivingEntity e : hit) {
			float dmg = damage;
			if (falloff) {
				double d = e.position().distanceTo(center);
				dmg = (float) (damage * (1.0 - Math.min(0.55, d / Math.max(1.0, radius))));
			}
			hurt(e, dmg);
			if (knockback > 0 || lift > 0) {
				knockAway(e, center, knockback, lift);
			}
		}
		return hit;
	}

	/** Hit every victim on the segment (a beam / lash / charge path). Returns the victims hit. */
	protected final List<LivingEntity> strikeLine(ServerLevel level, Vec3 from, Vec3 to, double width, float damage) {
		List<LivingEntity> hit = victimsOnSegment(level, from, to, width);
		for (LivingEntity e : hit) {
			hurt(e, damage);
		}
		return hit;
	}

	/** True when {@code target} is clearly off the ground (a flier or a jumper well above the boss). */
	protected final boolean airborne(LivingEntity target) {
		return !target.onGround() && target.getY() > boss.getY() + 2.5;
	}

	/** Line of sight from the boss to {@code target}. */
	protected final boolean sees(LivingEntity target) {
		return boss.hasLineOfSight(target);
	}

	/** Point the boss's head and body at {@code target}. */
	protected final void face(LivingEntity target) {
		boss.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		boss.setYBodyRot(boss.getYRot());
	}

	// ---------------- per-tick effects, projectiles, hitscans ----------------

	/**
	 * Run a per-tick effect on the shared revamp scheduler ({@link BatchBScheduler}, the same one the player powers'
	 * travelling waves and lashes use). It stops by itself the moment the boss is dead or removed.
	 */
	protected final void task(ServerLevel level, BatchBScheduler.Task task) {
		BatchBScheduler.schedule(level, age -> boss.isAlive() && !boss.isRemoved() && task.tick(age));
	}

	/**
	 * A temporary block for an ability (spikes, pillars, cages, leaves) through the player powers' {@link TempBlocks}
	 * (so it honours the terrain-damage config and restores itself) -- but never inside a living thing, so a pillar can
	 * neither entomb a player nor suffocate the boss's own horde.
	 */
	protected final boolean placeTemp(ServerLevel level, BlockPos pos, BlockState state, int ttlTicks) {
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
			return false;
		}
		return TempBlocks.place(level, pos, state, ttlTicks);
	}

	/** Where a straight line from {@code from} to {@code to} first hits a block (or {@code to}). */
	protected final Vec3 clipEnd(ServerLevel level, Vec3 from, Vec3 to) {
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	/** Aim point on {@code target}, led by {@code leadTicks} of its current motion. */
	protected static Vec3 lead(LivingEntity target, double leadTicks) {
		Vec3 v = target.getDeltaMovement();
		return mid(target).add(v.x * leadTicks, 0.0, v.z * leadTicks);
	}

	/** Direction from the boss's eyes to {@code point}. */
	protected final Vec3 aimFromEyes(Vec3 point) {
		Vec3 d = point.subtract(boss.getEyePosition());
		return d.lengthSqr() < 1.0e-6 ? boss.getLookAngle() : d.normalize();
	}

	/**
	 * A visible particle projectile (rock, fireball, shard, thorn, water bolt ...): it travels {@code speed} blocks a
	 * tick along {@code dir} for up to {@code maxTicks}, drawing {@code trail}, and calls {@code onImpact} once -- with the
	 * victim it touched (within {@code hitRadius}), or {@code null} when it hits a block or runs out. Only
	 * {@link BossTargets#isVictim victims} stop it, so it flies straight through the boss's own horde.
	 */
	protected final void projectile(ServerLevel level, Vec3 from, Vec3 dir, double speed, int maxTicks, double hitRadius,
			ParticleOptions trail, int trailPerTick, BiConsumer<Vec3, LivingEntity> onImpact) {
		Vec3 step = dir.normalize().scale(speed);
		Vec3[] pos = { from };
		task(level, age -> {
			Vec3 start = pos[0];
			Vec3 end = start.add(step);
			List<LivingEntity> touched = victimsOnSegment(level, start, end, hitRadius);
			Vec3 blockEnd = clipEnd(level, start, end);
			if (trail != null) {
				level.sendParticles(trail, (start.x + end.x) * 0.5, (start.y + end.y) * 0.5, (start.z + end.z) * 0.5,
						trailPerTick, speed * 0.2, 0.05, speed * 0.2, 0.0);
			}
			if (!touched.isEmpty()) {
				LivingEntity first = touched.get(0);
				double best = Double.MAX_VALUE;
				for (LivingEntity e : touched) {
					double d = e.distanceToSqr(start);
					if (d < best) {
						best = d;
						first = e;
					}
				}
				onImpact.accept(mid(first), first);
				return false;
			}
			if (blockEnd != end || age >= maxTicks) {
				onImpact.accept(blockEnd, null);
				return false;
			}
			pos[0] = end;
			return true;
		});
	}

	/** Convenience for controllers that want to read raid tuning directly. */
	protected final EventConfig.ZombieRaid config() {
		return EventConfig.raid();
	}
}

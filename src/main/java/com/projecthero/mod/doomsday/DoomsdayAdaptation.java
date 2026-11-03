package com.projecthero.mod.doomsday;

import com.projecthero.mod.kryptonian.item.KryptonianItems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.2: Doomsday's <b>reactive adaptation</b> -- "he can't be killed the same way twice". Pure bookkeeping (no world
 * access), so the gametests can drive it directly.
 *
 * <ul>
 *   <li>Every hit lands in one of seven {@link Bucket}s ({@link #classify}). Kryptonite and {@code /kill}-grade damage
 *       land in none: they are never adapted to.</li>
 *   <li><b>Learning</b>: once a bucket has dealt {@link DoomsdayConfig.Adaptation#learnFraction} of the current life's
 *       health he takes one {@link DoomsdayConfig.Adaptation#stepResistance} step less from it (to the max).</li>
 *   <li><b>Death</b>: the bucket of the killing blow becomes an immunity -- except melee, which only ever reaches the max
 *       resistance, so a fighter with nothing but a sword can still finish him.</li>
 *   <li><b>The floor</b>: at most {@link DoomsdayConfig.Adaptation#maxHardened} buckets are maxed or immune at once; a new
 *       one makes the oldest learned one decay back to nothing.</li>
 * </ul>
 */
public final class DoomsdayAdaptation {
	public enum Bucket { MELEE, PROJECTILE, FIRE, EXPLOSION, ENERGY, MAGIC, POWER }

	public static final Bucket[] BUCKETS = Bucket.values();
	/** Steps above this are "immune" in the synced mask. */
	public static final int IMMUNE = 7;

	private final float[] dealt = new float[BUCKETS.length];
	private final int[] steps = new int[BUCKETS.length];
	private final boolean[] immune = new boolean[BUCKETS.length];
	/** When each bucket last hardened (a counter, oldest first to decay); 0 = never. */
	private final long[] hardenedAt = new long[BUCKETS.length];
	private long seq;

	// ---------------------------------------------------------------- classification

	/**
	 * Which bucket a hit belongs in, or null for one he never adapts to (kryptonite in the attacker's hand, void /
	 * {@code /kill}, anything that bypasses invulnerability). {@code victim} is Doomsday (for the melee-reach test).
	 */
	public static Bucket classify(DamageSource source, LivingEntity victim) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || isKryptonite(source)) {
			return null;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return Bucket.EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return Bucket.FIRE;
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return Bucket.ENERGY;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return Bucket.PROJECTILE;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
				|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.SONIC_BOOM) || source.is(DamageTypes.THORNS)) {
			return Bucket.MAGIC;
		}
		String ns = source.typeHolder().unwrapKey().map(k -> k.location().getNamespace()).orElse("minecraft");
		if (!"minecraft".equals(ns)) {
			// this mod's own damage types (omega beams, alien strikes...) and other mods': hero powers / energy
			String path = source.typeHolder().unwrapKey().map(k -> k.location().getPath()).orElse("");
			return path.contains("beam") || path.contains("energy") || path.contains("lightning") ? Bucket.ENERGY : Bucket.POWER;
		}
		if (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)) {
			Entity attacker = source.getEntity();
			// an ability dealt as a "player attack" from well outside arm's reach is a hero power, not a punch
			if (attacker != null && victim != null) {
				double reach = 5.0 + victim.getBbWidth() * 0.5;
				if (attacker.distanceToSqr(victim.getX(), Math.max(victim.getY(), Math.min(attacker.getY(), victim.getY() + victim.getBbHeight())),
						victim.getZ()) > reach * reach) {
					return Bucket.POWER;
				}
			}
			return Bucket.MELEE;
		}
		return Bucket.POWER;
	}

	/** Kryptonite: the attacker holds a kryptonite shard (or anything with "kryptonite" in its id). */
	public static boolean isKryptonite(DamageSource source) {
		if (!(source.getEntity() instanceof LivingEntity le) || source.getDirectEntity() != le) {
			return false;
		}
		var held = le.getMainHandItem();
		if (held.isEmpty()) {
			return false;
		}
		if (KryptonianItems.KRYPTONITE_SHARD != null && held.is(KryptonianItems.KRYPTONITE_SHARD)) {
			return true;
		}
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().contains("kryptonite");
	}

	// ---------------------------------------------------------------- the numbers

	/** How much of a hit in {@code b} gets through (0 when immune). Null buckets always get full damage. */
	public float multiplier(Bucket b) {
		if (b == null) {
			return 1f;
		}
		int i = b.ordinal();
		if (immune[i]) {
			return 0f;
		}
		DoomsdayConfig.Adaptation c = DoomsdayConfig.adaptation();
		return (float) Math.max(0.0, 1.0 - Math.min(c.maxResistance, steps[i] * c.stepResistance));
	}

	public int steps(Bucket b) {
		return steps[b.ordinal()];
	}

	public boolean immune(Bucket b) {
		return immune[b.ordinal()];
	}

	public float dealt(Bucket b) {
		return dealt[b.ordinal()];
	}

	private int maxSteps() {
		DoomsdayConfig.Adaptation c = DoomsdayConfig.adaptation();
		return (int) Math.round(c.maxResistance / Math.max(0.01, c.stepResistance));
	}

	private boolean hardened(int i) {
		return immune[i] || steps[i] >= maxSteps();
	}

	/**
	 * Notes {@code amount} (after armour, before adaptation) dealt in {@code b} this life. Returns true when that taught him
	 * a new resistance step.
	 */
	public boolean record(Bucket b, float amount, double lifeHealth) {
		if (b == null || amount <= 0) {
			return false;
		}
		int i = b.ordinal();
		if (immune[i]) {
			return false;
		}
		dealt[i] += amount;
		double need = Math.max(1.0, lifeHealth * DoomsdayConfig.adaptation().learnFraction);
		boolean learned = false;
		while (dealt[i] >= need && steps[i] < maxSteps()) {
			dealt[i] -= need;
			steps[i]++;
			learned = true;
			if (hardened(i)) {
				hardenedAt[i] = ++seq;
				enforceFloor(i);
			}
		}
		if (steps[i] >= maxSteps()) {
			dealt[i] = 0;
		}
		return learned;
	}

	/** The killing blow's bucket: immune from now on (melee: maxed instead). Returns true if anything changed. */
	public boolean onDeath(Bucket killer) {
		if (killer == null) {
			return false;
		}
		int i = killer.ordinal();
		boolean was = hardened(i);
		if (killer == Bucket.MELEE) {
			steps[i] = maxSteps();
		} else {
			immune[i] = true;
			steps[i] = maxSteps();
		}
		hardenedAt[i] = ++seq;
		enforceFloor(i);
		return !was || killer != Bucket.MELEE;
	}

	/** A new life: this life's damage tally starts over (what he learned stays). */
	public void newLife() {
		java.util.Arrays.fill(dealt, 0f);
	}

	/** Keeps at most {@code maxHardened} buckets hardened: the oldest other one decays to nothing. */
	private void enforceFloor(int keep) {
		int max = Math.max(1, DoomsdayConfig.adaptation().maxHardened);
		while (true) {
			int count = 0;
			int oldest = -1;
			for (int i = 0; i < BUCKETS.length; i++) {
				if (hardened(i)) {
					count++;
					if (i != keep && (oldest < 0 || hardenedAt[i] < hardenedAt[oldest])) {
						oldest = i;
					}
				}
			}
			if (count <= max || oldest < 0) {
				return;
			}
			immune[oldest] = false;
			steps[oldest] = 0;
			dealt[oldest] = 0;
			hardenedAt[oldest] = 0;
		}
	}

	/** Buckets with any resistance (for the bar / HUD), strongest first. */
	public java.util.List<Bucket> learned() {
		java.util.List<Bucket> out = new java.util.ArrayList<>();
		for (Bucket b : BUCKETS) {
			if (steps[b.ordinal()] > 0 || immune[b.ordinal()]) {
				out.add(b);
			}
		}
		out.sort((a, b) -> Integer.compare(level(b), level(a)));
		return out;
	}

	private int level(Bucket b) {
		return immune[b.ordinal()] ? IMMUNE : steps[b.ordinal()];
	}

	/** 3 bits a bucket: 0-3 steps, {@link #IMMUNE} immune. Synced to the client for the stone flecks. */
	public int mask() {
		int m = 0;
		for (Bucket b : BUCKETS) {
			m |= Math.min(IMMUNE, level(b)) << (b.ordinal() * 3);
		}
		return m;
	}

	public static int levelIn(int mask, Bucket b) {
		return (mask >> (b.ordinal() * 3)) & 7;
	}

	public void reset() {
		java.util.Arrays.fill(dealt, 0f);
		java.util.Arrays.fill(steps, 0);
		java.util.Arrays.fill(immune, false);
		java.util.Arrays.fill(hardenedAt, 0L);
		seq = 0;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		for (Bucket b : BUCKETS) {
			int i = b.ordinal();
			CompoundTag e = new CompoundTag();
			e.putFloat("Dealt", dealt[i]);
			e.putInt("Steps", steps[i]);
			e.putBoolean("Immune", immune[i]);
			e.putLong("At", hardenedAt[i]);
			t.put(b.name(), e);
		}
		t.putLong("Seq", seq);
		return t;
	}

	public void load(CompoundTag t) {
		reset();
		for (Bucket b : BUCKETS) {
			if (t.contains(b.name())) {
				int i = b.ordinal();
				CompoundTag e = t.getCompound(b.name());
				dealt[i] = e.getFloat("Dealt");
				steps[i] = e.getInt("Steps");
				immune[i] = e.getBoolean("Immune");
				hardenedAt[i] = e.getLong("At");
			}
		}
		seq = t.getLong("Seq");
	}
}

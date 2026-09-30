package com.projecthero.mod.hero.power.p12;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.mutation.ModMobEffects;
import com.projecthero.mod.hero.revamp.batcha.BatchA;

import org.joml.Vector3f;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Power 12 -- Super Regeneration (v0.14.5 rework: <b>passive-only</b>). Key {@code power_12_super_regeneration}
 * -- Wolverine ascends from this power via the Adamantium Serum, so the key is load-bearing.
 *
 * <p>No ability keys at all. Three always-on passives while the power is owned (selected or not):
 * <ul>
 *   <li><b>Healing</b> -- 10 HP every 5 ticks while below max health, no hunger cost. Red pixel veins pulse and
 *       trickle over the whole body while it runs (flag {@link #VEINS_FLAG}, visible to everyone).</li>
 *   <li><b>Cleansing</b> -- every harmful effect is burned off once it has been on you for 2 s; it dissolves out
 *       of the body in its own colour. The unstable mutation is not a poison and is never touched.</li>
 *   <li><b>Revive charges</b> -- 3 charges. A lethal hit spends a ready one (before any Totem of Undying): back
 *       up at half health, harmful effects gone, 1 s of damage immunity. Each spent charge recharges on its own
 *       60 s timer.</li>
 * </ul>
 */
public final class SuperRegenerationHandlers {
	public static final String KEY = "power_12_super_regeneration";

	/** MutationVisuals flag for the red healing veins. */
	public static final String VEINS_FLAG = "p12.veins";

	public static final int HEAL_INTERVAL = 5;
	public static final float HEAL_AMOUNT = 10.0f;
	/** A harmful effect is removed once it has been on the player this long. */
	public static final int CLEANSE_TICKS = 40;
	public static final int REVIVE_CHARGES = 3;
	public static final int REVIVE_COOLDOWN = 1200;
	/** Charges count down in steps this size (one resource write per second, not per tick). */
	private static final int CHARGE_STEP = 20;
	private static final int REVIVE_IMMUNITY = 20;
	/** How long the veins keep showing after the last heal tick. */
	private static final int VEINS_LINGER = 30;
	private static final int DISSOLVE_TICKS = 10;

	/** Last game time the passive actually healed, per player (drives the veins flag). Transient. */
	private static final Map<UUID, Long> LAST_HEAL = new HashMap<>();
	/** First game time each harmful effect was seen on the player. Transient. */
	private static final Map<UUID, Map<Holder<MobEffect>, Long>> FIRST_SEEN = new HashMap<>();
	/** Post-revive damage immunity, game time it ends. Transient. */
	private static final Map<UUID, Long> IMMUNE_UNTIL = new HashMap<>();
	/** Running "dissolve out" particle effects. */
	private static final List<Dissolve> DISSOLVES = new ArrayList<>();

	private static final class Dissolve {
		final UUID player;
		final float r;
		final float g;
		final float b;
		int age;

		Dissolve(UUID player, int rgb) {
			this.player = player;
			this.r = ((rgb >> 16) & 255) / 255f;
			this.g = ((rgb >> 8) & 255) / 255f;
			this.b = (rgb & 255) / 255f;
		}
	}

	private SuperRegenerationHandlers() {
	}

	public static void register() {
		// No abilities: the three passives run every tick the power is owned, selected or not.
		PowerPassives.registerTick(KEY, SuperRegenerationHandlers::passiveTick);
	}

	private static void passiveTick(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (now % HEAL_INTERVAL == 0) {
			healTick(p);
		}
		tickCleanse(p, now);
		if (now % CHARGE_STEP == 0) {
			tickCharges(p, CHARGE_STEP);
		}
	}

	// ---------------------------------------------------------------- healing

	/** One heal interval: +10 HP if hurt. Returns whether it healed. */
	public static boolean healTick(ServerPlayer p) {
		if (!p.isAlive() || p.getHealth() >= p.getMaxHealth()) {
			return false;
		}
		p.heal(HEAL_AMOUNT);
		LAST_HEAL.put(p.getUUID(), p.level().getGameTime());
		return true;
	}

	/** Whether the veins should show: owns the power and healed in the last 1.5 s. */
	public static boolean regenerating(ServerPlayer p) {
		if (!ExperimentalPowers.owns(p, KEY)) {
			return false;
		}
		Long at = LAST_HEAL.get(p.getUUID());
		return at != null && p.level().getGameTime() - at <= VEINS_LINGER;
	}

	// ---------------------------------------------------------------- cleansing

	private static boolean cleansable(Holder<MobEffect> effect) {
		return effect.value().getCategory() == MobEffectCategory.HARMFUL
				&& effect.value() != ModMobEffects.UNSTABLE_MUTATION.value();
	}

	/**
	 * Stamps each harmful effect the first time it is seen and removes it once it has been on the player for
	 * {@link #CLEANSE_TICKS}. {@code now} is the game time (passed in so the gametests can drive it).
	 */
	public static void tickCleanse(ServerPlayer p, long now) {
		Map<Holder<MobEffect>, Long> seen = FIRST_SEEN.get(p.getUUID());
		boolean any = false;
		for (MobEffectInstance inst : p.getActiveEffects()) {
			if (cleansable(inst.getEffect())) {
				any = true;
				break;
			}
		}
		if (!any) {
			if (seen != null) {
				FIRST_SEEN.remove(p.getUUID());
			}
			return;
		}
		if (seen == null) {
			seen = new HashMap<>();
			FIRST_SEEN.put(p.getUUID(), seen);
		}
		List<Holder<MobEffect>> expired = new ArrayList<>();
		for (MobEffectInstance inst : p.getActiveEffects()) {
			Holder<MobEffect> effect = inst.getEffect();
			if (!cleansable(effect)) {
				continue;
			}
			long first = seen.computeIfAbsent(effect, e -> now);
			if (now - first >= CLEANSE_TICKS) {
				expired.add(effect);
			}
		}
		// forget effects that ran out (or were removed) so a fresh application restarts its timer
		seen.keySet().removeIf(e -> !p.hasEffect(e));
		for (Holder<MobEffect> effect : expired) {
			p.removeEffect(effect);
			seen.remove(effect);
			startDissolve(p, effect.value().getColor());
		}
	}

	/** Removes every harmful effect except the unstable mutation, dissolving each out. */
	public static void purgeHarmful(ServerPlayer p) {
		List<Holder<MobEffect>> harmful = new ArrayList<>();
		for (MobEffectInstance inst : p.getActiveEffects()) {
			if (cleansable(inst.getEffect())) {
				harmful.add(inst.getEffect());
			}
		}
		for (Holder<MobEffect> effect : harmful) {
			p.removeEffect(effect);
			startDissolve(p, effect.value().getColor());
		}
		FIRST_SEEN.remove(p.getUUID());
	}

	private static void startDissolve(ServerPlayer p, int rgb) {
		DISSOLVES.add(new Dissolve(p.getUUID(), rgb));
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS,
				0.35f, 1.8f);
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.3f, 0.7f);
	}

	/**
	 * Advances every running dissolve by one tick: over half a second the effect's colour breaks off the body
	 * from the feet up and drifts away (potion swirls plus square "pixel" flecks), seen by every nearby player.
	 */
	public static void tickDissolves(MinecraftServer server) {
		if (DISSOLVES.isEmpty()) {
			return;
		}
		for (Iterator<Dissolve> it = DISSOLVES.iterator(); it.hasNext();) {
			Dissolve d = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(d.player);
			if (p == null || d.age >= DISSOLVE_TICKS) {
				it.remove();
				continue;
			}
			ServerLevel level = p.serverLevel();
			float t = d.age / (float) (DISSOLVE_TICKS - 1);
			double y = p.getY() + 0.1 + t * p.getBbHeight();
			level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, d.r, d.g, d.b),
					p.getX(), y, p.getZ(), 4, 0.3, 0.15, 0.3, 0.6);
			level.sendParticles(new DustParticleOptions(new Vector3f(d.r, d.g, d.b), 0.9f),
					p.getX(), y, p.getZ(), 5, 0.32, 0.18, 0.32, 0.02);
			d.age++;
		}
	}

	// ---------------------------------------------------------------- revive charges

	private static String chargeKey(int i) {
		return "revive_cd_" + i;
	}

	/** Remaining cooldown ticks of charge {@code i} (0 = ready). Persisted in the power's resources. */
	public static float chargeCooldown(ServerPlayer p, int i) {
		return BatchA.res(p, KEY, chargeKey(i));
	}

	public static int readyCharges(ServerPlayer p) {
		int n = 0;
		for (int i = 0; i < REVIVE_CHARGES; i++) {
			if (chargeCooldown(p, i) <= 0f) {
				n++;
			}
		}
		return n;
	}

	/** Counts every recharging charge down by {@code ticks}. */
	public static void tickCharges(ServerPlayer p, int ticks) {
		for (int i = 0; i < REVIVE_CHARGES; i++) {
			float cd = chargeCooldown(p, i);
			if (cd > 0f) {
				BatchA.set(p, KEY, chargeKey(i), Math.max(0f, cd - ticks), REVIVE_COOLDOWN);
			}
		}
	}

	/**
	 * The lethal-hit hook ({@code ALLOW_DEATH}, which fires before a Totem of Undying is checked). Spends one
	 * ready charge and brings the player back; returns whether it did (the death is then cancelled).
	 */
	public static boolean tryRevive(ServerPlayer p, DamageSource source) {
		if (!ExperimentalPowers.owns(p, KEY)) {
			return false;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false; // /kill and the void still kill you, like a totem
		}
		int charge = -1;
		for (int i = 0; i < REVIVE_CHARGES; i++) {
			if (chargeCooldown(p, i) <= 0f) {
				charge = i;
				break;
			}
		}
		if (charge < 0) {
			return false;
		}
		BatchA.set(p, KEY, chargeKey(charge), REVIVE_COOLDOWN, REVIVE_COOLDOWN);
		p.setHealth(p.getMaxHealth() * 0.5f);
		purgeHarmful(p);
		p.clearFire();
		IMMUNE_UNTIL.put(p.getUUID(), p.level().getGameTime() + REVIVE_IMMUNITY);
		LAST_HEAL.put(p.getUUID(), p.level().getGameTime());
		if (p.level() instanceof ServerLevel level) {
			DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.08f), 1.6f);
			level.sendParticles(blood, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.5, 0.8, 0.5, 0.15);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1.0, p.getZ(), 12, 0.4, 0.6, 0.4, 0.2);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 1.0f);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
		return true;
	}

	/** Whether the post-revive immunity is still running. */
	public static boolean immune(ServerPlayer p) {
		Long until = IMMUNE_UNTIL.get(p.getUUID());
		if (until == null) {
			return false;
		}
		if (p.level().getGameTime() >= until) {
			IMMUNE_UNTIL.remove(p.getUUID());
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- transient state lifecycle

	public static void forget(UUID player) {
		LAST_HEAL.remove(player);
		FIRST_SEEN.remove(player);
		IMMUNE_UNTIL.remove(player);
		DISSOLVES.removeIf(d -> d.player.equals(player));
	}

	public static void clearAll() {
		LAST_HEAL.clear();
		FIRST_SEEN.clear();
		IMMUNE_UNTIL.clear();
		DISSOLVES.clear();
	}
}

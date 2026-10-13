package com.projecthero.mod.power;

import java.util.ArrayDeque;

import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * v0.16.1: the thunderstorm buff. While it is thundering where Thor stands -- {@code level.isThundering()} and the
 * biome at his feet has rain or snow (a desert or a savanna never storms) -- every hit he deals is
 * {@link ThorConfig.Storm#damageBonus} (+30%) stronger: melee, lightning, thrown weapons, area moves, the Twin Thunder
 * Strike, the Thunder Field. Open sky is <em>not</em> required by default ({@link ThorConfig.Storm#requireSky}): the
 * god of thunder feels the storm through a roof.
 *
 * <p>"Thor" is a worthy player wielding Mjolnir / Stormbreaker or bound to a hammer (the same test as his ability
 * keys, {@code AbilityRouter.hasThorContext}).
 *
 * <p><b>One hook, applied once.</b> {@code LivingEntityThorStormMixin} scales the incoming amount at the top of
 * {@code LivingEntity#hurt} when the hit's attacker ({@link DamageSource#getEntity}) is a storm-charged Thor -- the
 * single place every damage path passes through. Several systems cancel a hit and re-issue it from inside the same
 * {@code hurt} (Thor's own 80% damage reduction, Nova, Moon Knight...): a re-issued hit on a victim already being hurt
 * on this thread by a scaled hit is not scaled again ({@link #enter} / {@link #exit}). And the dual / single combo
 * finisher, which re-hurts its target with an amount that already contains the bonus, runs inside {@link #unscaled}.
 *
 * <p>The visible tell is server-driven too: {@link ThorFx#stormPct} is set while charged (the client draws the
 * crackle round him and the HUD line "Storm-Charged +30%"), and sparks are sent from here so every viewer sees them.
 */
public final class ThorStorm {
	/** Victims currently inside a {@code hurt} on this (server) thread; a scaled frame pushes its victim, others null. */
	private static final ThreadLocal<ArrayDeque<Object>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
	private static final Object UNSCALED = new Object();
	private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> false);

	private ThorStorm() {
	}

	/** Is {@code player} Thor (worthy, wielding a Thor weapon or bound to a hammer)? */
	public static boolean isThor(Player player) {
		return Worthiness.isWorthy(player)
				&& (ThorPowers.isHoldingThorWeapon(player) || ThorPassives.hasPowerOfThor(player));
	}

	/** Is it thundering at {@code pos} (rain / snow biome; open sky only if the config asks)? */
	public static boolean thunderingAt(Level level, BlockPos pos) {
		if (!level.isThundering()) {
			return false;
		}
		if (ThorConfig.get().storm.requireSky && !level.canSeeSky(pos)) {
			return false;
		}
		return level.getBiome(pos).value().getPrecipitationAt(pos) != Biome.Precipitation.NONE;
	}

	/** Is {@code player} storm-charged right now? Server truth (the client reads {@link ThorFx#stormPct}). */
	public static boolean isCharged(Player player) {
		return thunderingAt(player.level(), player.blockPosition()) && isThor(player);
	}

	/** The damage multiplier for {@code player}'s hits: 1.3 while storm-charged, else 1. */
	public static float multiplier(Player player) {
		return isCharged(player) ? 1.0f + (float) ThorConfig.get().storm.damageBonus : 1.0f;
	}

	/** The bonus in whole percent, for the HUD. */
	public static int percent() {
		return (int) Math.round(ThorConfig.get().storm.damageBonus * 100.0);
	}

	// ---------------- the hurt hook ----------------

	/** {@code LivingEntity#hurt} HEAD (server): the amount to use. Always paired with {@link #exit}. */
	public static float enter(LivingEntity victim, DamageSource source, float amount) {
		ArrayDeque<Object> frames = FRAMES.get();
		Entity attacker = source.getEntity();
		boolean scale = amount > 0.0f && !SUPPRESS.get() && attacker instanceof ServerPlayer thor && attacker != victim
				&& !frames.contains(victim) && isCharged(thor);
		frames.push(scale ? victim : UNSCALED);
		return scale ? amount * multiplier((Player) attacker) : amount;
	}

	/** {@code LivingEntity#hurt} RETURN (server). */
	public static void exit() {
		ArrayDeque<Object> frames = FRAMES.get();
		if (!frames.isEmpty()) {
			frames.pop();
		}
	}

	/** Runs {@code r} with the buff off -- for a re-hurt whose amount already carries it (the combo finishers). */
	public static void unscaled(Runnable r) {
		boolean was = SUPPRESS.get();
		SUPPRESS.set(true);
		try {
			r.run();
		} finally {
			SUPPRESS.set(was);
		}
	}

	// ---------------- per tick ----------------

	/** Every server tick for every player (from {@code ThorPowers.serverTick}): the synced flag and the sparks. */
	public static void tick(ServerPlayer player) {
		boolean charged = isCharged(player);
		ThorVisuals.storm(player, charged ? percent() : 0);
		if (charged && player.tickCount % 3 == 0 && player.level() instanceof ServerLevel level) {
			double h = player.getBbHeight();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + h * 0.55, player.getZ(), 3,
					0.35, h * 0.35, 0.35, 0.06);
			if (player.tickCount % 12 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + h * 0.6, player.getZ(), 1, 0.3,
						h * 0.3, 0.3, 0.01);
			}
		}
		// a stray frame (an exception thrown through hurt, or another mod's HEAD cancel that skips our RETURN) must
		// never outlive the tick: nothing is inside a hurt while players are ticked
		ArrayDeque<Object> frames = FRAMES.get();
		if (!frames.isEmpty()) {
			frames.clear();
		}
	}
}

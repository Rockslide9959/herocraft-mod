package com.projecthero.mod.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.4 (user): WORLD BREAKER -- "if hulk gets to 100% rage then he can activate that form and all his moves get even
 * stronger, more destructive and he starts getting glowing green veins; in this rage mode hulk grows to 10 blocks tall; his
 * damage and destruction and aoe should be doubled; make this mode last 30 seconds; after this mode ends give it a drawback
 * or some kind of debuff i dont want this power to be spammed and be too op".
 *
 * <ul>
 *   <li><b>Activation:</b> H as the Hulk at 100 rage (instead of trying to change back), in control (not changing, dying or
 *       rampaging), off cooldown. A roar, a shockwave ({@code activationRadius} / {@code activationDamage}), screen shake and
 *       a burst of gamma.</li>
 *   <li><b>While it runs</b> ({@code durationTicks}, 30 s): he grows to {@code heightBlocks} (10) -- as far as there is room
 *       ({@code Hulk#tickScale}); rage stays pinned at 100; every hit he deals x{@code damageMultiplier} on top of the rage
 *       bonus ({@link Hulk#damageMultiplier}); every move's area x{@link #aoe} and its blocks / craters x{@link #destruction}
 *       (read by HulkAbilities / HulkGrab / HulkCombat.crater / HulkEarthWall / the boulder). H does nothing.</li>
 *   <li><b>Burnout</b> when it ends ({@code burnoutTicks}, 60 s): rage drops to 0 (he shrinks back to 3.5 blocks), rage can't
 *       build, Slowness II + Weakness II + Mining Fatigue I. World Breaker is on a {@code cooldownTicks} (5 min) cooldown
 *       counted from the end -- the HUD only says "burned out", no timer.</li>
 *   <li>Death (the fall), a change back, a relog or a revoke end it cleanly: it never leaves him stuck at 10 blocks.</li>
 * </ul>
 * Everything is in the synced {@link HulkState.Moves} (wb*), so there is no static state to reset.
 */
public final class WorldBreaker {
	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 2.4f);
	private static final DustParticleOptions VEIN = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.35f), 1.2f);

	private WorldBreaker() {
	}

	// ---------------------------------------------------------------- client-safe queries

	private static HulkState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
	}

	/** World Breaker is running for this player. Client-safe. */
	public static boolean active(Player player) {
		HulkState s = state(player);
		return s != null && s.hasPower && s.worldBreaker(player.level().getGameTime());
	}

	/** In the Burnout after it. Client-safe. */
	public static boolean burnout(Player player) {
		HulkState s = state(player);
		return s != null && s.hasPower && s.burnout(player.level().getGameTime());
	}

	/** Ticks left of World Breaker (0 when it isn't running). Client-safe. */
	public static int ticksLeft(Player player) {
		HulkState s = state(player);
		return s == null || !s.worldBreaker(player.level().getGameTime()) ? 0 : (int) (s.moves.wbUntil - player.level().getGameTime());
	}

	/** World Breaker is on its cooldown (burned out). Client-safe. */
	public static boolean coolingDown(Player player) {
		HulkState s = state(player);
		return s != null && s.moves.wbReadyAt > player.level().getGameTime();
	}

	/** The Hulk could activate World Breaker right now (100 rage, in control, off cooldown). Client-safe -- the HUD prompt. */
	public static boolean available(Player player) {
		HulkState s = state(player);
		if (s == null || !s.hasPower || !s.hulk || !HulkConfig.worldBreaker().enabled) {
			return false;
		}
		long now = player.level().getGameTime();
		return s.rage >= HulkConfig.RAGE_MAX - 1.0e-3f && !s.worldBreaker(now) && !s.burnout(now) && s.moves.wbReadyAt <= now
				&& !s.dying() && !s.rampaging(now) && !Hulk.changing(s, now);
	}

	/** Every move's area is multiplied by this (2 while World Breaker runs, else 1). Client-safe. */
	public static double aoe(Player player) {
		return active(player) ? Math.max(1.0, HulkConfig.worldBreaker().aoeMultiplier) : 1.0;
	}

	/** Blocks broken / crater sizes are multiplied by this (2 while World Breaker runs, else 1). Client-safe. */
	public static double destruction(Player player) {
		return active(player) ? Math.max(1.0, HulkConfig.worldBreaker().destructionMultiplier) : 1.0;
	}

	/** Every hit he deals is multiplied by this on top of the rage bonus. Client-safe. */
	public static float damage(Player player) {
		return active(player) ? Math.max(1.0f, HulkConfig.worldBreaker().damageMultiplier) : 1.0f;
	}

	/** The SCALE bonus World Breaker grows him to (10 blocks / 1.8 - 1). */
	public static double scaleBonus() {
		return Math.max(HulkConfig.MAX_HULK_HEIGHT, HulkConfig.worldBreaker().heightBlocks) / HulkConfig.PLAYER_HEIGHT - 1.0;
	}

	// ---------------------------------------------------------------- server

	/**
	 * H as the Hulk: start World Breaker if it is available. Returns true if H was used up by it (started, or refused because
	 * it is already running) -- false means H goes on to the normal change back.
	 */
	static boolean pressH(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (s.worldBreaker(now)) {
			Hulk.say(player, "message.projecthero.hulk.world_breaker_running", ChatFormatting.GREEN);
			return true;
		}
		if (!available(player)) {
			return false;
		}
		activate(player);
		return true;
	}

	/** Starts World Breaker (no checks -- {@link #pressH} and the tests). */
	public static void activate(ServerPlayer player) {
		HulkConfig.WorldBreaker cfg = HulkConfig.worldBreaker();
		long now = player.level().getGameTime();
		HulkGrab.release(player); // both hands for this
		HulkState n = Hulk.state(player).copy();
		n.moves.wbStartedAt = now;
		n.moves.wbUntil = now + Math.max(20, cfg.durationTicks);
		n.moves.wbBurnoutUntil = 0L;
		n.rage = HulkConfig.RAGE_MAX;
		n.animId = HulkState.ANIM_ROAR;
		n.animStart = now;
		Hulk.save(player, n);
		Hulk.applyRageTier(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		double h = player.getBbHeight();
		// the roar
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.45f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 0.5f);
		// the shockwave (already at World Breaker strength: a doubled ring and doubled damage)
		HulkCombat.radial(player, at, cfg.activationRadius, new HulkCombat.Hit(cfg.activationDamage, 2.4, 0.8), true);
		GroundCracks.star(level, at, cfg.activationRadius, 14, 200);
		HulkCombat.crater(player, at.add(0, -0.5, 0), 3.0, 60);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + h * 0.5, at.z, 3, 0.5, 0.5, 0.5, 0);
		level.sendParticles(GAMMA, at.x, at.y + h * 0.5, at.z, 200, 1.5, h * 0.4, 1.5, 0.3);
		for (double r = 2.0; r <= cfg.activationRadius; r += 2.0) {
			HulkCombat.ring(level, GAMMA, at.add(0, 0.3, 0), r, (int) (r * 6));
			HulkCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), r, (int) (r * 3));
		}
		for (int y = 0; y < 30; y += 2) {
			level.sendParticles(GAMMA, at.x, at.y + y, at.z, 6, 0.6, 0.4, 0.6, 0.05); // a pillar of gamma into the sky
		}
		HulkCombat.shake(level, at, 1.4f, 40, 64.0);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.world_breaker")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	/**
	 * Ends a running World Breaker into Burnout: rage to 0 (he shrinks back), Slowness II + Weakness II + Mining Fatigue I for
	 * {@code burnoutTicks}, rage frozen, and the cooldown. Mutates and saves the state. {@code quiet}: no message (death,
	 * revoke).
	 */
	static void end(ServerPlayer player, boolean quiet, boolean debuffs) {
		HulkConfig.WorldBreaker cfg = HulkConfig.worldBreaker();
		long now = player.level().getGameTime();
		HulkState n = Hulk.state(player).copy();
		if (n.moves.wbUntil == 0L) {
			return;
		}
		n.moves.wbUntil = 0L;
		n.moves.wbBurnoutUntil = now + Math.max(0, cfg.burnoutTicks);
		n.moves.wbReadyAt = now + Math.max(0, cfg.cooldownTicks);
		if (n.hulk) {
			n.rage = 0.0f;
		}
		Hulk.save(player, n);
		Hulk.applyRageTier(player, n);
		int ticks = Math.max(0, cfg.burnoutTicks);
		if (debuffs && ticks > 0 && player.isAlive() && !n.dying()) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, Math.max(0, cfg.burnoutSlownessAmplifier), false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, Math.max(0, cfg.burnoutWeaknessAmplifier), false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, Math.max(0, cfg.burnoutMiningFatigueAmplifier), false, true, true));
		}
		if (!quiet) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 2.0f, 0.5f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 0.5f);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 40,
					player.getBbWidth() * 0.5, player.getBbHeight() * 0.3, player.getBbWidth() * 0.5, 0.02);
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.world_breaker_burnout")
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Death / revoke / a forced change back: World Breaker stops at once (Burnout and the cooldown still count). */
	static void stop(ServerPlayer player) {
		if (Hulk.state(player).moves.wbUntil != 0L) {
			end(player, true, false);
		}
	}

	/** Every Hulk tick (from {@link Hulk#tick}): time it out, pin rage at 100, the veins' glow; Burnout keeps rage at 0. */
	static void tick(ServerPlayer player, long now) {
		HulkState s = Hulk.state(player);
		if (s.moves.wbUntil != 0L) {
			if (!s.hulk || s.dying() || now >= s.moves.wbUntil || s.moves.wbUntil - now > HulkConfig.worldBreaker().durationTicks + 20L) {
				end(player, !s.hulk || s.dying(), s.hulk && !s.dying());
				return;
			}
			if (s.rage < HulkConfig.RAGE_MAX) {
				HulkState n = s.copy();
				n.rage = HulkConfig.RAGE_MAX;
				Hulk.save(player, n);
			}
			ServerLevel level = (ServerLevel) player.level();
			double h = player.getBbHeight();
			if (now % 2L == 0L) {
				level.sendParticles(VEIN, player.getX(), player.getY() + h * 0.5, player.getZ(), 4, player.getBbWidth() * 0.35, h * 0.35,
						player.getBbWidth() * 0.35, 0.0);
			}
			if (now % 20L == 0L) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.6f);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + h * 0.6, player.getZ(), 6,
						player.getBbWidth() * 0.4, h * 0.3, player.getBbWidth() * 0.4, 0.0);
			}
			if (now % 6L == 0L && player.onGround()) {
				GroundCracks.crackAt(level, player.position(), 6, 60); // the ground gives under him
			}
			return;
		}
		if (s.burnout(now) && s.rage > 0.0f) {
			HulkState n = s.copy();
			n.rage = 0.0f;
			Hulk.save(player, n);
		}
	}

	/** Join / respawn: a World Breaker from before can't come back (game time is per world); the cooldown is clamped. */
	static void sanitize(HulkState n, long now, boolean keepRunning) {
		HulkConfig.WorldBreaker cfg = HulkConfig.worldBreaker();
		if (!keepRunning || n.moves.wbUntil - now > cfg.durationTicks + 20L) {
			if (n.moves.wbUntil != 0L) {
				// it was running: it ended while he was away -- straight onto the cooldown
				n.moves.wbReadyAt = now + Math.max(0, cfg.cooldownTicks);
			}
			n.moves.wbUntil = 0L;
		}
		if (n.moves.wbReadyAt > now + cfg.cooldownTicks) {
			n.moves.wbReadyAt = now + Math.max(0, cfg.cooldownTicks);
		}
		if (n.moves.wbBurnoutUntil > now + cfg.burnoutTicks) {
			n.moves.wbBurnoutUntil = 0L;
		}
	}
}

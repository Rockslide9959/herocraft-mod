package com.projecthero.mod.moonknight;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * The one server-side API for Moon Knight (Hero-Tier power {@value #KEY}): the pact with Khonshu, the Vengeance meter,
 * the Fracture, and the charge state of Khonshu's Resurrection. Phase 1 of the build -- the suit (H), the cape and
 * the six abilities land in later phases; see {@code docs/MOONKNIGHT_REFERENCE.md}.
 *
 * <p>Everything a client needs (HUD, and later the suit / cape render) reads the synced {@link MoonKnightState}
 * through the {@code Player}-taking helpers here; every rule is decided server-side.
 */
public final class MoonKnight {
	public static final String KEY = "moon_knight";

	private MoonKnight() {
	}

	// ---------------------------------------------------------------- state

	public static MoonKnightState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.MOON_KNIGHT_STATE);
	}

	static void save(ServerPlayer player, MoonKnightState s) {
		player.setAttached(ModAttachments.MOON_KNIGHT_STATE, s);
	}

	/** Client-safe: the synced state, or null. */
	public static MoonKnightState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_STATE, null);
	}

	public static boolean hasPower(Player player) {
		MoonKnightState s = peek(player);
		return s != null && s.hasPact;
	}

	public static boolean isTransformed(Player player) {
		MoonKnightState s = peek(player);
		return s != null && s.hasPact && s.transformed;
	}

	public static MoonKnightAlter alter(Player player) {
		MoonKnightState s = peek(player);
		return s == null ? MoonKnightAlter.MARC : MoonKnightAlter.byOrdinal(s.alter);
	}

	public static boolean fractured(Player player) {
		MoonKnightState s = peek(player);
		return s != null && s.fractureUntil > player.level().getGameTime();
	}

	/** Ticks left on ability {@code id}'s cooldown (client-safe). */
	public static int cooldownRemaining(Player player, String id) {
		MoonKnightState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long readyAt = s.abilityReadyAt.get(id);
		return readyAt == null ? 0 : (int) Math.max(0L, readyAt - player.level().getGameTime());
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The pact with Khonshu (the ritual, a grant command). Returns false if the player already has it. */
	public static boolean grant(ServerPlayer player) {
		return grant(player, MoonKnightLunar.isFullMoonNight(player.level()));
	}

	/**
	 * @param fullMoon a pact sealed under a full moon starts with Vengeance at 100 instead of 50
	 */
	public static boolean grant(ServerPlayer player, boolean fullMoon) {
		if (state(player).hasPact) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		MoonKnightState s = new MoonKnightState();
		s.hasPact = true;
		s.vengeance = fullMoon ? MoonKnightConfig.VENGEANCE_START_FULL_MOON : MoonKnightConfig.VENGEANCE_START;
		s.resurrectionCharged = true;
		s.resurrectionCycle = MoonKnightLunar.moonCycle(player.level());
		s.lastHostileKill = player.level().getGameTime();
		save(player, s);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.acquired")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		clearTransient(player);
		save(player, new MoonKnightState()); // hasPact = false
	}

	/**
	 * Phase 1 test hook (the {@code /moonknight suit} command): flip the transformed flag without the suit, so the
	 * HUD and the Vengeance / Fracture rules can be checked before Phase 2 builds the real H transformation.
	 */
	public static boolean setTransformedForTesting(ServerPlayer player, boolean on) {
		MoonKnightState s = state(player);
		if (!s.hasPact) {
			return false;
		}
		MoonKnightState c = s.copy();
		c.transformed = on;
		c.gliding = false;
		save(player, c);
		return true;
	}

	// ---------------------------------------------------------------- vengeance

	public static float vengeance(ServerPlayer player) {
		return state(player).vengeance;
	}

	public static void addVengeance(ServerPlayer player, float amount) {
		setVengeance(player, state(player).vengeance + amount);
	}

	/** Set Vengeance (clamped 0..100). Hitting 0 while transformed may trigger a Fracture. */
	public static void setVengeance(ServerPlayer player, float value) {
		MoonKnightState s = state(player);
		if (!s.hasPact) {
			return;
		}
		float before = s.vengeance;
		MoonKnightState c = s.copy();
		c.vengeance = Math.max(0.0f, Math.min(MoonKnightConfig.VENGEANCE_MAX, value));
		save(player, c);
		if (before > 0.0f && c.vengeance <= 0.0f) {
			tryFracture(player);
		}
	}

	/**
	 * A mob died ({@code AFTER_DEATH}). A pact-holder who killed a hostile mob gains Vengeance: +5 if it was hunting a
	 * villager, wandering trader, iron golem or another player; otherwise +2 at night, +1 by day. Every hostile kill
	 * also resets the two-day drain timer.
	 */
	public static void onEntityKilled(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(victim instanceof Enemy) || !hasPower(killer)) {
			return;
		}
		boolean protector = false;
		if (victim instanceof Mob mob) {
			LivingEntity prey = mob.getTarget();
			protector = prey instanceof AbstractVillager || prey instanceof IronGolem
					|| (prey instanceof Player p && p != killer);
		}
		float gain = protector ? MoonKnightConfig.VENGEANCE_PROTECTOR_KILL
				: (MoonKnightLunar.isMoonNight(killer.level()) ? MoonKnightConfig.VENGEANCE_NIGHT_KILL
						: MoonKnightConfig.VENGEANCE_DAY_KILL);
		MoonKnightState c = state(killer).copy();
		c.lastHostileKill = killer.level().getGameTime();
		c.vengeance = Math.min(MoonKnightConfig.VENGEANCE_MAX, c.vengeance + gain);
		save(killer, c);
		if (protector) {
			killer.displayClientMessage(Component.translatable("message.projecthero.moon_knight.protector_kill")
					.withStyle(ChatFormatting.WHITE), true);
		}
	}

	// ---------------------------------------------------------------- fracture

	/**
	 * Vengeance has run dry while transformed: the mind fractures -- a forced switch to a random other alter for 20 s
	 * and a brief screen wobble. Deliberately rare: at most once every 10 minutes, and only on the edge of hitting 0.
	 */
	static boolean tryFracture(ServerPlayer player) {
		MoonKnightState s = state(player);
		long now = player.level().getGameTime();
		if (!s.transformed || s.fractureUntil > now || now - s.lastFracture < MoonKnightConfig.FRACTURE_MIN_GAP_TICKS) {
			return false;
		}
		MoonKnightAlter current = MoonKnightAlter.byOrdinal(s.alter);
		MoonKnightAlter forced = player.getRandom().nextBoolean() ? current.next() : current.next().next();
		MoonKnightState c = s.copy();
		c.fractureReturnAlter = s.alter;
		c.alter = forced.ordinal();
		c.fractureUntil = now + MoonKnightConfig.FRACTURE_TICKS;
		c.lastFracture = now;
		save(player, c);
		player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, MoonKnightConfig.FRACTURE_NAUSEA_TICKS, 0, false, false, false));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.35f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.fracture",
				Component.translatable(forced.nameKey())).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
		return true;
	}

	// ---------------------------------------------------------------- tick / lifecycle

	/** Per-player server tick (from {@code AbilityRouter.serverTick}). Cheap for anyone without the pact. */
	public static void tick(ServerPlayer player) {
		if (player.level().getGameTime() % 20L == 0L) {
			tickSecond(player);
		}
	}

	/** Once a second: the Fracture wearing off, the Resurrection recharging, the Vengeance drain. (Public for the gametests.) */
	public static void tickSecond(ServerPlayer player) {
		MoonKnightState s = player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_STATE, null);
		if (s == null || !s.hasPact) {
			return;
		}
		long now = player.level().getGameTime();
		MoonKnightState c = null;

		// the Fracture wears off
		if (s.fractureUntil > 0 && now >= s.fractureUntil) {
			c = s.copy();
			c.alter = s.fractureReturnAlter;
			c.fractureUntil = 0L;
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.fracture_end",
					Component.translatable(MoonKnightAlter.byOrdinal(c.alter).nameKey())).withStyle(ChatFormatting.GRAY), true);
		}

		// Khonshu's favour returns at the start of the next full moon night
		if (!s.resurrectionCharged && MoonKnightLunar.isFullMoonNight(player.level())
				&& MoonKnightLunar.moonCycle(player.level()) > s.resurrectionCycle) {
			c = c == null ? s.copy() : c;
			c.resurrectionCharged = true;
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.resurrection_recharged")
					.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), false);
		}

		// Vengeance slowly fades after two in-game days without a hostile kill
		boolean drained = false;
		if (s.vengeance > 0.0f && now - s.lastHostileKill > MoonKnightConfig.VENGEANCE_IDLE_BEFORE_DRAIN) {
			c = c == null ? s.copy() : c;
			c.vengeance = Math.max(0.0f, s.vengeance - MoonKnightConfig.VENGEANCE_DRAIN_PER_SECOND);
			drained = c.vengeance <= 0.0f;
		}
		if (c != null) {
			save(player, c);
		}
		if (drained) {
			tryFracture(player);
		}
	}

	/** Death / relog / dimension change: nothing transient to drop yet beyond the glide flag and the pose. */
	public static void clearTransient(ServerPlayer player) {
		MoonKnightState s = player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_STATE, null);
		if (s == null || (!s.gliding && s.animId == 0)) {
			return;
		}
		MoonKnightState c = s.copy();
		c.gliding = false;
		c.animId = 0;
		save(player, c);
	}

	/**
	 * Join: game time is per world, so stamps written in another world (or before a {@code /time} change) can sit far
	 * in the future. Clamp every one to what it could legitimately be from now.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		MoonKnightState s = player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_STATE, null);
		if (s == null || !s.hasPact) {
			return;
		}
		long now = player.level().getGameTime();
		MoonKnightState c = s.copy();
		boolean dirty = false;
		Map<String, Long> ready = new HashMap<>();
		for (Map.Entry<String, Long> e : s.abilityReadyAt.entrySet()) {
			if (e.getValue() > now && e.getValue() - now <= 20L * 120L) {
				ready.put(e.getKey(), e.getValue());
			} else {
				dirty = true;
			}
		}
		c.abilityReadyAt = ready;
		if (s.lastHostileKill > now) {
			c.lastHostileKill = now;
			dirty = true;
		}
		if (s.fractureUntil > now + MoonKnightConfig.FRACTURE_TICKS) {
			c.fractureUntil = now + MoonKnightConfig.FRACTURE_TICKS;
			dirty = true;
		}
		if (s.lastFracture > now) {
			c.lastFracture = now - MoonKnightConfig.FRACTURE_MIN_GAP_TICKS;
			dirty = true;
		}
		if (dirty) {
			save(player, c);
		}
	}
}

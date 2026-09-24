package com.projecthero.mod.wolverine;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.ChatFormatting;
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

/**
 * The single server-side API for the Wolverine Hero-Tier power (v0.12.1) -- an <b>ascension of Super
 * Regeneration</b>, the way Spider-Man ascends Spider Adhesion. Nothing else pokes
 * {@link WolverineState} directly; every mutator re-saves via {@link ServerPlayer#setAttached}.
 *
 * <h2>Inheriting Super Regeneration</h2>
 * The ascension consumes the mutation, so the Wolverine re-uses (never copies) what it gave him:
 * the base heal tick ({@code SuperRegenerationHandlers.tickBaseRegen}) drives his healing factor at
 * 1x / 2x / 3x by health tier, and the debuff-halving mixin covers him too (Poison / Wither harder).
 */
public final class Wolverine {
	/** The experimental power Wolverine ascends. */
	public static final String SUPER_REGENERATION_KEY = "power_12_super_regeneration";

	private Wolverine() {
	}

	// ---------------- state ----------------

	public static WolverineState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.WOLVERINE_STATE);
	}

	static void save(ServerPlayer player, WolverineState state) {
		player.setAttached(ModAttachments.WOLVERINE_STATE, state);
	}

	/** Safe on the client too (the attachment is synced); the server never trusts a client's copy. */
	public static boolean hasPower(Player player) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		return s != null && s.hasPower;
	}

	public static boolean clawsOut(Player player) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		return s != null && s.hasPower && s.clawsOut;
	}

	public static boolean raging(Player player) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		return s != null && s.hasPower && s.rageUntil > player.level().getGameTime();
	}

	public static boolean dashing(Player player) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		return s != null && s.hasPower && s.dashUntil > player.level().getGameTime();
	}

	/** Whether the player owns the Super Regeneration mutation (the ascension prerequisite). */
	public static boolean hasSuperRegeneration(ServerPlayer player) {
		return ExperimentalPowers.state(player).ownedPowers.contains(SUPER_REGENERATION_KEY);
	}

	// ---------------- the ascension ----------------

	/**
	 * Ascend Super Regeneration into Wolverine. Returns false -- consuming nothing -- when the player
	 * lacks Super Regeneration or already is Wolverine. Like every Primary power it replaces the
	 * mutation group (through {@link HeroTiers#claimPrimary}).
	 */
	public static boolean ascendFromSuperRegeneration(ServerPlayer player) {
		if (hasPower(player) || !hasSuperRegeneration(player)) {
			return false;
		}
		HeroTiers.claimPrimary(player, "wolverine");
		ExperimentalPowers.setActive(player, null);
		if (com.projecthero.mod.hero.power.HeroFlight.isFlying(player)) {
			com.projecthero.mod.hero.power.HeroFlight.setFlying(player, false);
		}
		for (Power owned : new java.util.ArrayList<>(ExperimentalPowers.ownedPowers(player))) {
			ExperimentalPowers.forget(player, owned);
		}
		PowerPassives.reconcileActive(player);

		long now = player.level().getGameTime();
		WolverineState s = state(player).copy();
		s.hasPower = true;
		s.clawsOut = false;
		s.clawsChangedAt = now;
		s.rageUntil = 0L;
		s.emergencyHealUntil = 0L;
		s.emergencyReadyAt = 0L;
		s.dashUntil = 0L;
		s.abilityReadyAt.clear();
		save(player, s);
		WolverinePassives.reconcile(player);
		transformationFx(player);
		return true;
	}

	/** Testing/admin/Power Suppressor -- see {@link com.projecthero.mod.command.WolverineCommand}. */
	public static void revoke(ServerPlayer player) {
		WolverineScheduler.clear(player.getUUID());
		WolverineState s = state(player).copy();
		s.hasPower = false;
		s.clawsOut = false;
		s.rageUntil = 0L;
		s.emergencyHealUntil = 0L;
		s.dashUntil = 0L;
		s.abilityReadyAt.clear();
		save(player, s);
		WolverinePassives.reconcile(player);
	}

	private static void transformationFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WOLF_GROWL, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 0.7f);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 60, 0.45, 0.9, 0.45, 0.14);
		level.sendParticles(ParticleTypes.HEART, c.x, c.y, c.z, 10, 0.4, 0.8, 0.4, 0.0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1, false, true, true));
		player.displayClientMessage(Component.translatable("message.projecthero.wolverine.evolved")
				.withStyle(ChatFormatting.GOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.wolverine.acquired")
				.withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
	}

	// ---------------- claws ----------------

	/** Deploy or retract the claws. Server-validated; returns the new state. */
	public static boolean setClaws(ServerPlayer player, boolean out) {
		WolverineState s = state(player);
		if (!s.hasPower || s.clawsOut == out) {
			return s.clawsOut;
		}
		WolverineState c = s.copy();
		c.clawsOut = out;
		c.clawsChangedAt = player.level().getGameTime();
		save(player, c);
		WolverinePassives.reconcile(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				out ? SoundEvents.CHAIN_PLACE : SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.9f, out ? 1.6f : 1.2f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				out ? SoundEvents.ANVIL_PLACE : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.35f, out ? 1.8f : 1.4f);
		player.displayClientMessage(Component.translatable(
				out ? "message.projecthero.wolverine.claws_deployed" : "message.projecthero.wolverine.claws_retracted")
				.withStyle(out ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
		return out;
	}

	/** H key: toggle, with a tiny anti-spam guard. */
	public static void toggleClaws(ServerPlayer player) {
		WolverineState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.clawsChangedAt < WolverineConfig.TOGGLE_COOLDOWN) {
			return;
		}
		setClaws(player, !s.clawsOut);
	}

	/** Record the last combat action so every client can flash the claws / play the swing. */
	static void markAction(ServerPlayer player, int slot) {
		WolverineState c = state(player).copy();
		c.lastAction = slot;
		c.lastActionTick = player.level().getGameTime();
		save(player, c);
	}

	// ---------------- cooldowns (absolute ready-at game time) ----------------

	public static boolean abilityReady(ServerPlayer player, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null) {
			return 0;
		}
		Long readyAt = s.abilityReadyAt.get(abilityId);
		return readyAt == null ? 0 : (int) Math.max(0L, readyAt - player.level().getGameTime());
	}

	public static void triggerCooldown(ServerPlayer player, String abilityId, int ticks) {
		if (ticks <= 0) {
			return;
		}
		WolverineState c = state(player).copy();
		c.abilityReadyAt.put(abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/** Drop every transient buff/task -- death, respawn, logout, dimension change. The power itself is kept. */
	public static void clearTransient(ServerPlayer player) {
		WolverineScheduler.clear(player.getUUID());
		WolverineState s = state(player);
		if (s.rageUntil != 0L || s.emergencyHealUntil != 0L || s.dashUntil != 0L) {
			WolverineState c = s.copy();
			c.rageUntil = 0L;
			c.emergencyHealUntil = 0L;
			c.dashUntil = 0L;
			save(player, c);
		}
		WolverinePassives.reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		clearTransient(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		clearTransient(player);
	}

	public static void initialize() {
		WolverineDamage.initialize();
	}
}

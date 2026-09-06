package com.projecthero.mod.punisher;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.punisher.data.PunisherState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The single server-side API for the Punisher Hero-Tier power -- peer of {@code Thor} / {@code TonyStark}
 * / {@code SpiderMan} / {@code MaxSteel}. Nothing else pokes {@link PunisherState} directly; every
 * mutator re-saves via {@link ServerPlayer#setAttached} so the change is persisted and synced.
 *
 * <p>{@link #initialize()} installs the {@link FirearmHooks} implementation (so a Punisher gets
 * the personal reserve + the handling passives) and the kill hook that feeds Vigilante Training.
 */
public final class Punisher {
	private Punisher() {
	}

	// ---------------- state ----------------

	public static PunisherState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.PUNISHER_STATE);
	}

	public static void save(ServerPlayer player, PunisherState state) {
		player.setAttached(ModAttachments.PUNISHER_STATE, state);
	}

	public static boolean hasPower(Player player) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.hasPower;
	}

	// ---------------- acquisition ----------------

	/** Permanently grant the Punisher power. Returns false (granting nothing) if already held. */
	public static boolean grant(ServerPlayer player) {
		if (hasPower(player)) {
			return false;
		}
		PunisherState s = state(player).copy();
		s.hasPower = true;
		s.trainingActive = false;
		save(player, s);

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.6f);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.8, 0.4, 0.1);
		player.displayClientMessage(Component.translatable("message.projecthero.punisher.unlocked")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.punisher.unlocked.sub")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/** Testing / admin / Power Suppressor -- see {@link com.projecthero.mod.command.PunisherCommand}. */
	public static void revoke(ServerPlayer player) {
		PunisherState s = state(player).copy();
		s.hasPower = false;
		s.trainingActive = false;
		s.adrenalineUntil = 0L;
		s.suppressiveUntil = 0L;
		s.rollUntil = 0L;
		s.abilityReadyAt.clear();
		save(player, s);
		PunisherPassives.reconcile(player);
	}

	// ---------------- arsenal ----------------

	public static boolean weaponUnlocked(Player player, String weaponId) {
		if (Firearms.PISTOL.equals(weaponId)) {
			return true; // the pistol is always available to a Punisher
		}
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.unlockedWeapons.contains(weaponId);
	}

	public static void unlockWeapon(ServerPlayer player, String weaponId) {
		if (weaponId == null || Firearms.get(weaponId) == null || Firearms.PISTOL.equals(weaponId)) {
			return;
		}
		PunisherState s = state(player);
		if (s.unlockedWeapons.contains(weaponId)) {
			return;
		}
		PunisherState c = s.copy();
		c.unlockedWeapons.add(weaponId);
		save(player, c);
		player.displayClientMessage(Component.translatable("message.projecthero.punisher.arsenal_unlocked",
				Component.translatable("item.projecthero." + weaponId)).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------- cooldowns ----------------

	public static boolean abilityReady(ServerPlayer player, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
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
		PunisherState c = state(player).copy();
		c.abilityReadyAt.put(abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/** Drop every transient buff/modifier -- death, respawn, logout, dimension change, power loss. */
	public static void clearTransient(ServerPlayer player) {
		PunisherState s = state(player);
		if (s.adrenalineUntil != 0L || s.suppressiveUntil != 0L || s.rollUntil != 0L || s.adrenalineCrashAt != 0L) {
			PunisherState c = s.copy();
			c.adrenalineUntil = 0L;
			c.suppressiveUntil = 0L;
			c.rollUntil = 0L;
			c.adrenalineCrashAt = 0L;
			save(player, c);
		}
		PunisherPassives.reconcile(player);
		com.projecthero.mod.punisher.ability.PunisherC4.clearFor(player.getUUID());
		com.projecthero.mod.punisher.PunisherAbilityManager.onCleanup(player.getUUID());
	}

	public static void onPlayerJoin(ServerPlayer player) {
		clearTransient(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		clearTransient(player);
	}

	// ---------------- init ----------------

	public static void initialize() {
		FirearmHooks.install(new PunisherPassives.Hooks());

		// Vigilante Training: attribute a mob death to a player who is running the training (they do
		// NOT have the power yet -- that is the whole point) or already has it.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.world.entity.player.Player) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer killer && trainingOrPowered(killer)) {
				com.projecthero.mod.punisher.VigilanteTraining.onKill(killer, entity, source);
			}
		});
	}

	// ---------------- crafting gate (Punisher tactical armour) ----------------

	private static final java.util.Map<java.util.UUID, Long> CRAFT_WARN = new java.util.concurrent.ConcurrentHashMap<>();

	/** Throttled action-bar note when a non-Punisher tries to craft the tactical armour. */
	public static void warnCraftLocked(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = CRAFT_WARN.get(player.getUUID());
		if (last != null && now - last < 60L) {
			return;
		}
		CRAFT_WARN.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable("message.projecthero.punisher.craft_locked")
				.withStyle(ChatFormatting.RED), true);
	}

	// ---------------- helpers used across the package ----------------

	public static boolean adrenalineActive(Player player) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.hasPower && s.adrenalineUntil > player.level().getGameTime();
	}

	public static boolean suppressiveActive(Player player) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.hasPower && s.suppressiveUntil > player.level().getGameTime();
	}

	public static boolean rolling(Player player) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.rollUntil > player.level().getGameTime();
	}

	static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= PunisherConfig.BOSS_MAX_HEALTH;
	}

	/** True while the player has the power OR is actively running Vigilante Training. */
	public static boolean trainingOrPowered(Player player) {
		com.projecthero.mod.punisher.data.PunisherState s =
				player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && (s.hasPower || s.trainingActive);
	}
}

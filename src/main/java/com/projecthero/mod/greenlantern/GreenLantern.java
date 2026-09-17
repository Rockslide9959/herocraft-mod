package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.data.GreenLanternState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The single server-side API for the Green Lantern Hero-Tier power. Nothing else pokes
 * {@link GreenLanternState} directly; every mutator re-saves via {@link ServerPlayer#setAttached} so
 * the change is persisted and synced. Peer to {@link com.projecthero.mod.maxsteel.MaxSteel} and
 * {@link com.projecthero.mod.punisher.Punisher}.
 */
public final class GreenLantern {
	private GreenLantern() {
	}

	// ---------------- state ----------------

	public static GreenLanternState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GREEN_LANTERN_STATE);
	}

	public static void save(ServerPlayer player, GreenLanternState state) {
		player.setAttached(ModAttachments.GREEN_LANTERN_STATE, state);
	}

	public static boolean hasPower(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s != null && s.hasPower;
	}

	public static boolean isSuited(Player player) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s != null && s.suited;
	}

	// ---------------- bonding ----------------

	/** Bind the power to a player who just completed the Will Trial (or was command-granted). */
	public static boolean bond(ServerPlayer player) {
		if (hasPower(player)) {
			return false;
		}
		GreenLanternState s = state(player).copy();
		s.hasPower = true;
		s.ringCharge = GreenLanternConfig.MAX_RING_CHARGE;
		s.suited = false;
		s.suitAnimDir = GreenLanternState.SUIT_IDLE;
		save(player, s);

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.8, 0.4, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.acquired")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.welcome")
				.withStyle(ChatFormatting.DARK_GREEN), false);
		return true;
	}

	/** Testing/admin only -- see {@link com.projecthero.mod.command.GreenLanternCommand}. */
	public static void revoke(ServerPlayer player) {
		GreenLanternState s = state(player).copy();
		s.hasPower = false;
		s.suited = false;
		s.suitAnimDir = GreenLanternState.SUIT_IDLE;
		s.abilityReadyAt.clear();
		save(player, s);
		clearTransient(player);
	}

	// ---------------- ability cooldowns (absolute ready-at game time) ----------------

	public static boolean abilityReady(ServerPlayer player, String abilityId) {
		Long readyAt = state(player).abilityReadyAt.get(abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
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
		GreenLanternState c = state(player).copy();
		c.abilityReadyAt.put(abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/**
	 * Drop every transient suit/combat state -- called on death, respawn, logout, dimension change and
	 * power removal. The power itself is never removed here.
	 */
	public static void clearTransient(ServerPlayer player) {
		com.projecthero.mod.greenlantern.GreenLanternFlight.forceStop(player, false);
		com.projecthero.mod.greenlantern.GreenLanternShield.dismissAll(player);
		com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.clearFor(player.getUUID());
		com.projecthero.mod.greenlantern.GreenLanternBattery.cancelOath(player.getUUID());
		com.projecthero.mod.greenlantern.GreenLanternAbilityManager.onCleanup(player.getUUID());
		com.projecthero.mod.greenlantern.GreenLanternEnergy.onCleanup(player.getUUID());
		com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor.strip(player);
		GreenLanternState s = state(player);
		if (!s.suited && s.suitAnimDir == GreenLanternState.SUIT_IDLE) {
			return;
		}
		GreenLanternState c = s.copy();
		c.suited = false;
		c.suitAnimDir = GreenLanternState.SUIT_IDLE;
		save(player, c);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		clearTransient(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		clearTransient(player);
	}
}

package com.projecthero.mod.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The single server-side API for the Max Steel Hero-Tier power. Nothing else pokes
 * {@link MaxSteelState} directly; every mutator re-saves via {@link ServerPlayer#setAttached} so the
 * change is persisted and synced.
 *
 * <p>Peer to {@link com.projecthero.mod.ironman.TonyStark} and {@link com.projecthero.mod.spider.SpiderMan}:
 * its own attachment, granted server-authoritatively when Steel bonds, and it survives
 * logout / restart / world reload / dimension change / death ({@code persistent} + {@code copyOnDeath}).
 * The active <em>suit</em> state does not survive death -- {@link #onPlayerRespawn} strips the player
 * back to unsuited Base Mode while keeping ownership of the power.
 */
public final class MaxSteel {
	private MaxSteel() {
	}

	// ---------------- state ----------------

	public static MaxSteelState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.MAX_STEEL_STATE);
	}

	public static void save(ServerPlayer player, MaxSteelState state) {
		player.setAttached(ModAttachments.MAX_STEEL_STATE, state);
	}

	/**
	 * Save an energy-only change, but only when it is worth a resync -- the HUD draws whole points, so
	 * a 4/sec regen would otherwise put a packet on the wire every tick for nothing. Fractional
	 * progress stays in the live object; the sync is deferred until the value crosses a point boundary
	 * or reaches either end of the range.
	 */
	public static void saveEnergy(ServerPlayer player, MaxSteelState updated, MaxSteelState previous) {
		boolean edge = updated.turboEnergy <= 0f || updated.turboEnergy >= MaxSteelConfig.MAX_TURBO_ENERGY;
		if (edge || (int) updated.turboEnergy != (int) previous.turboEnergy
				|| updated.lastHighCostTick != previous.lastHighCostTick
				|| updated.combatUntil != previous.combatUntil) {
			save(player, updated);
		} else {
			previous.turboEnergy = updated.turboEnergy;
			previous.combatUntil = updated.combatUntil;
		}
	}

	/**
	 * Whether the player has the Max Steel power. Safe on the client -- the attachment is synced -- but
	 * a modified client cannot make the <em>server</em> believe it (every gameplay check runs against
	 * the server-side attachment).
	 */
	public static boolean hasPower(Player player) {
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		return s != null && s.hasPower;
	}

	/** Whether the player is currently in the Max Steel suit (fully on, forming, or retracting). */
	public static boolean isTransformed(Player player) {
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		return s != null && s.transformed;
	}

	public static MaxSteelMode mode(Player player) {
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
		return s == null ? MaxSteelMode.BASE : s.modeEnum();
	}

	// ---------------- bonding ----------------

	/**
	 * Steel bonds with the player: permanently grant the power. Returns false (granting nothing) if the
	 * player already has it. The bonding <em>sequence</em> (cut-scene, XP/stabilizer cost, the Steel
	 * entity) is the caller's job -- this just flips the permanent flag and plays the acquisition cue.
	 */
	public static boolean bond(ServerPlayer player) {
		if (hasPower(player)) {
			return false;
		}
		MaxSteelState s = state(player).copy();
		s.hasPower = true;
		s.turboEnergy = MaxSteelConfig.MAX_TURBO_ENERGY;
		s.transformed = false;
		s.mode = MaxSteelMode.BASE.ordinal();
		s.transformDir = MaxSteelState.DIR_IDLE;
		save(player, s);

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 0.7f);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.8, 0.4, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.max_steel.acquired")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.max_steel.go_turbo")
				.withStyle(ChatFormatting.DARK_AQUA), false);
		return true;
	}

	/** Testing / admin only -- see {@link com.projecthero.mod.command.MaxSteelCommand}. */
	public static void revoke(ServerPlayer player) {
		MaxSteelState s = state(player).copy();
		s.hasPower = false;
		s.transformed = false;
		s.mode = MaxSteelMode.BASE.ordinal();
		s.transformDir = MaxSteelState.DIR_IDLE;
		s.abilityReadyAt.clear();
		s.modeEndsAt = 0L;
		s.lockoutUntil = 0L;
		save(player, s);
		MaxSteelModes.clearAll(player);
		MaxSteelSuitArmor.strip(player);
	}

	// ---------------- ability cooldowns (absolute ready-at game time) ----------------

	public static boolean abilityReady(ServerPlayer player, String abilityId) {
		// v0.9.2: an overload locks every ability, not just the specialised modes, until T.U.R.B.O.
		// Energy has regenerated to MaxSteelConfig.OVERLOAD_RECOVER_ENERGY.
		if (MaxSteelEnergy.isLockedOut(player)) {
			return false;
		}
		Long readyAt = state(player).abilityReadyAt.get(abilityId);
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		MaxSteelState s = player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
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
		MaxSteelState c = state(player).copy();
		c.abilityReadyAt.put(abilityId, player.level().getGameTime() + ticks);
		save(player, c);
	}

	// ---------------- lifecycle ----------------

	/**
	 * Drop every transient suit state -- called on death, respawn, logout, dimension change, and power
	 * removal. The power itself is never removed here. Flight / speed / stealth / strength effects are
	 * torn down through {@link MaxSteelModes} so no attribute modifier or {@code mayfly} grant can leak.
	 */
	public static void clearTransient(ServerPlayer player) {
		MaxSteelModes.clearAll(player);
		MaxSteelSuitArmor.strip(player);
		MaxSteelCannon.endFlight(player);
		MaxSteelAbilityManager.onCleanup(player.getUUID());
		player.setAttached(ModAttachments.MAX_STEEL_FACEPLATE_OPEN, false);
		MaxSteelState s = state(player);
		if (!s.transformed && s.mode == MaxSteelMode.BASE.ordinal() && s.transformDir == MaxSteelState.DIR_IDLE
				&& s.modeEndsAt == 0L && s.lockoutUntil == 0L) {
			return;
		}
		MaxSteelState c = s.copy();
		c.transformed = false;
		c.mode = MaxSteelMode.BASE.ordinal();
		c.transformDir = MaxSteelState.DIR_IDLE;
		c.modeEndsAt = 0L;
		c.lockoutUntil = 0L;
		save(player, c);
	}

	/**
	 * The "you cannot keep this" gate for the suit, called for <em>every</em> player from
	 * {@code AbilityRouter.serverTick} (the {@code Symbiote}/{@code PunisherArmorGate} pattern). Curse
	 * of Binding should make a stray Max Steel piece unreachable in ordinary play now; this is the
	 * backstop for anyone who is not transformed but somehow has one anyway (traded away, a piece that
	 * reached a chest).
	 */
	public static void enforce(ServerPlayer player) {
		if (player.isSpectator() || isTransformed(player)) {
			return;
		}
		boolean wearing = MaxSteelSuitArmor.wearing(player);
		if (!wearing && player.tickCount % 40 != 0) {
			return;
		}
		if (wearing) {
			MaxSteelSuitArmor.strip(player);
		}
		MaxSteelSuitArmor.deleteLoose(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		clearTransient(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		clearTransient(player);
	}
}

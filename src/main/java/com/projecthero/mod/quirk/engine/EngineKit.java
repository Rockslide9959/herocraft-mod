package com.projecthero.mod.quirk.engine;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Engine's six keys (Tenya Iida / Ingenium).
 * <pre>
 *   R  Recipro Kick      Shift+R  Spin Kick
 *   G  Recipro Burst     Shift+G  Gear Up
 *   X  Engine Leap       Shift+X  Air Dash
 *   Z  Recipro Extend    Shift+Z  Afterburner Kick
 *   V  Rescue Carry      Shift+V  Rescue Throw
 *   C  Recipro Turbo     Shift+C  Cool Down
 * </pre>
 * Passive: Speed I and a one-block step. The moves live in {@link EngineMoves}; every number is in {@link EngineConfig}.
 */
public final class EngineKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// G ends a running Burst early; V sets a carried creature down -- both before their cooldown check
		if (id.equals(EngineConfig.BURST) && Engine.running(player, Engine.V_BURST)) {
			EngineMoves.burst(player);
			return;
		}
		if (id.equals(EngineConfig.CARRY) && Engine.carrying(player)) {
			EngineMoves.carry(player);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case EngineConfig.KICK -> EngineMoves.kick(player);
			case EngineConfig.SPIN -> EngineMoves.spin(player);
			case EngineConfig.BURST -> EngineMoves.burst(player);
			case EngineConfig.GEAR -> EngineMoves.gearUp(player);
			case EngineConfig.LEAP -> EngineMoves.leap(player);
			case EngineConfig.AIR_DASH -> EngineMoves.airDash(player);
			case EngineConfig.EXTEND -> EngineMoves.extend(player);
			case EngineConfig.AFTERBURNER -> EngineMoves.afterburner(player);
			case EngineConfig.CARRY -> EngineMoves.carry(player);
			case EngineConfig.THROW -> EngineMoves.throwCarried(player);
			case EngineConfig.TURBO -> EngineMoves.turbo(player);
			case EngineConfig.COOL -> EngineMoves.coolDown(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		EngineMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		EngineMoves.stopAll(player);
	}

	/** The exhausts cushion the landing after a leap / air dash. */
	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && EngineMoves.softLanding(player)) {
			return 0f;
		}
		return amount;
	}

	@Override
	public void clearSessionState() {
		EngineMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		EngineMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Engine.V_KICK) > now;
			case SLOT_2 -> s.val(Engine.V_BURST) > now || s.val(Engine.V_GEAR) > now;
			case SLOT_4 -> s.val(Engine.V_EXTEND) > now;
			case SLOT_5 -> s.val(Engine.V_CARRY) != 0L;
			case SLOT_6 -> s.val(Engine.V_TURBO) > now || s.val(Engine.V_STEAM) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { EngineConfig.KICK, EngineConfig.SPIN };
			case SLOT_2 -> new String[] { EngineConfig.BURST, EngineConfig.GEAR };
			case SLOT_3 -> new String[] { EngineConfig.LEAP, EngineConfig.AIR_DASH };
			case SLOT_4 -> new String[] { EngineConfig.EXTEND, EngineConfig.AFTERBURNER };
			case SLOT_5 -> new String[] { EngineConfig.CARRY, EngineConfig.THROW };
			case SLOT_6 -> new String[] { EngineConfig.TURBO, EngineConfig.COOL };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case EngineConfig.KICK -> EngineConfig.KICK_COOLDOWN;
			case EngineConfig.SPIN -> EngineConfig.SPIN_COOLDOWN;
			case EngineConfig.BURST -> EngineConfig.BURST_COOLDOWN;
			case EngineConfig.GEAR -> EngineConfig.GEAR_COOLDOWN;
			case EngineConfig.LEAP -> EngineConfig.LEAP_COOLDOWN;
			case EngineConfig.AIR_DASH -> EngineConfig.AIR_DASH_COOLDOWN;
			case EngineConfig.EXTEND -> EngineConfig.EXTEND_COOLDOWN;
			case EngineConfig.AFTERBURNER -> EngineConfig.AFTERBURNER_COOLDOWN;
			case EngineConfig.CARRY -> EngineConfig.CARRY_COOLDOWN;
			case EngineConfig.THROW -> EngineConfig.THROW_COOLDOWN;
			case EngineConfig.TURBO -> EngineConfig.TURBO_COOLDOWN;
			case EngineConfig.COOL -> EngineConfig.COOL_COOLDOWN;
			default -> 0;
		};
	}
}

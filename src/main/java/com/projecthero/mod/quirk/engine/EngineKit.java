package com.projecthero.mod.quirk.engine;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): Engine's six keys (Tenya Iida / Ingenium). User rules: one dash, the rest attacks plus the mode; X is
 * the movement key and V the utility (the gears).
 * <pre>
 *   V  Gear Up (mode; at Gear 3: Recipro Burst, again: Extend)   Shift+V  Gear Down (ends Recipro)
 *   X  Recipro Dash (the one dash)                               Shift+X  Rising Kick
 *   R  Recipro Kick (speed-scaled)                               Shift+R  Spin Kick
 *   G  Kick Barrage                                              Shift+G  Recipro Extend Kick (charged)
 *   Z  Axe Kick                                                  Shift+Z  Sweep Kick
 *   C  Recipro Turbo (ultimate, maxes heat)                      Shift+C  Ingenium Finisher (combo)
 * </pre>
 * Passives: a one-block step; heat cools on its own while walking / standing (fastest at Gear 0). The moves live in
 * {@link EngineMoves}; every number is in {@link EngineConfig}.
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
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case EngineConfig.SHIFT_UP -> EngineMoves.shiftUp(player);
			case EngineConfig.SHIFT_DOWN -> EngineMoves.shiftDown(player);
			case EngineConfig.DASH -> EngineMoves.dash(player);
			case EngineConfig.RISING -> EngineMoves.risingKick(player);
			case EngineConfig.KICK -> EngineMoves.kick(player);
			case EngineConfig.SPIN -> EngineMoves.spin(player);
			case EngineConfig.BARRAGE -> EngineMoves.barrage(player);
			case EngineConfig.EXTEND_KICK -> EngineMoves.extendKick(player);
			case EngineConfig.AXE -> EngineMoves.axeKick(player);
			case EngineConfig.SWEEP -> EngineMoves.sweepKick(player);
			case EngineConfig.TURBO -> EngineMoves.turbo(player);
			case EngineConfig.FINISHER -> EngineMoves.finisher(player);
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
			case SLOT_5 -> s.val(Engine.V_GEAR_NO) > 0L || s.val(Engine.V_BURST) > now || s.val(Engine.V_EXTEND) > now;
			case SLOT_3 -> s.val(Engine.V_DASH) > now;
			case SLOT_2 -> s.val(Engine.V_BARRAGE) > now || s.val(Engine.V_EXTEND_KICK) > now;
			case SLOT_6 -> s.val(Engine.V_TURBO) > now || s.val(Engine.V_FINISHER) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { EngineConfig.KICK, EngineConfig.SPIN };
			case SLOT_2 -> new String[] { EngineConfig.BARRAGE, EngineConfig.EXTEND_KICK };
			case SLOT_3 -> new String[] { EngineConfig.DASH, EngineConfig.RISING };
			case SLOT_4 -> new String[] { EngineConfig.AXE, EngineConfig.SWEEP };
			case SLOT_5 -> new String[] { EngineConfig.SHIFT_UP, EngineConfig.SHIFT_DOWN };
			case SLOT_6 -> new String[] { EngineConfig.TURBO, EngineConfig.FINISHER };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case EngineConfig.SHIFT_UP, EngineConfig.SHIFT_DOWN -> EngineConfig.SHIFT_COOLDOWN;
			case EngineConfig.BURST -> EngineConfig.BURST_COOLDOWN;
			case EngineConfig.EXTEND -> EngineConfig.EXTEND_COOLDOWN;
			case EngineConfig.DASH -> EngineConfig.DASH_COOLDOWN;
			case EngineConfig.RISING -> EngineConfig.RISING_COOLDOWN;
			case EngineConfig.KICK -> EngineConfig.KICK_COOLDOWN;
			case EngineConfig.SPIN -> EngineConfig.SPIN_COOLDOWN;
			case EngineConfig.BARRAGE -> EngineConfig.BARRAGE_COOLDOWN;
			case EngineConfig.EXTEND_KICK -> EngineConfig.EXTEND_KICK_COOLDOWN;
			case EngineConfig.AXE -> EngineConfig.AXE_COOLDOWN;
			case EngineConfig.SWEEP -> EngineConfig.SWEEP_COOLDOWN;
			case EngineConfig.TURBO -> EngineConfig.TURBO_COOLDOWN;
			case EngineConfig.FINISHER -> EngineConfig.FINISHER_COOLDOWN;
			default -> 0;
		};
	}
}

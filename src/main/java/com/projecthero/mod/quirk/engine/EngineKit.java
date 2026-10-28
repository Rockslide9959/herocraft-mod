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
 * v0.16.7 (revamp): Engine's six keys (Tenya Iida / Ingenium). User rules: one dash, the rest attacks plus the mode; X is
 * the movement key and V the utility (the gears). v0.16.7 (user): Recipro Burst / Extend (V at Gear 3) are gone and Recipro
 * Turbo is the MODE -- it stays on C (V / Shift+V are the gearbox), and the old Turbo strike now rides on X while Turbo
 * runs, so no key lost an attack.
 * <pre>
 *   V  Gear Up (Gear 3: "Max gear")                             Shift+V  Gear Down
 *   X  Recipro Dash, along the aim (in Turbo: the Turbo Strike)                 Shift+X  Rising Kick
 *   R  Recipro Kick (speed-scaled)                               Shift+R  Spin Kick
 *   G  Kick Barrage                                              Shift+G  Recipro Extend Kick (charged)
 *   Z  Axe Kick                                                  Shift+Z  Sweep Kick
 *   C  Recipro Turbo (mode: 30 s 2x speed, +50% hits; C ends)    Shift+C  Ingenium Finisher (combo)
 * </pre>
 * Passives: a one-block step; 60% less fall damage in Gear 1+ (v0.17.1); heat cools on its own while walking / standing (fastest at Gear 0); the Ingenium suit halves
 * the heat build. The moves live in {@link EngineMoves}; every number is in {@link EngineConfig}.
 */
public final class EngineKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			// v0.17.1 holds: R (the Charged Kick) and X (the Engine Brake) fire / end on the release
			if (slot == AbilitySlot.SLOT_1) {
				EngineMoves.chargeRelease(player);
			} else if (slot == AbilitySlot.SLOT_3) {
				EngineMoves.xUp(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// v0.17.1 Recipro Burst -- Extend: a kick key starts the chain (once a Burst)
		if (Engine.burst(player) && (slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_4)) {
			EngineMoves.chainStart(player);
			return;
		}
		// X (no Shift): a tap is the dash -- while running it waits for the release, and held it is the Engine Brake
		if (slot == AbilitySlot.SLOT_3 && !shift) {
			EngineMoves.xDown(player);
			return;
		}
		// C while Recipro Turbo runs ends it (its own cooldown runs from the moment it was engaged)
		boolean endTurbo = id.equals(EngineConfig.TURBO) && Engine.running(player, Engine.V_TURBO);
		if (!endTurbo && !Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case EngineConfig.SHIFT_UP -> EngineMoves.shiftUp(player);
			case EngineConfig.SHIFT_DOWN -> EngineMoves.shiftDown(player);
			case EngineConfig.DASH -> EngineMoves.dash(player);
			case EngineConfig.RISING -> EngineMoves.risingKick(player);
			case EngineConfig.KICK -> EngineMoves.chargeStart(player); // the tap kick fires on the release (hold: the Charged Kick)
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

	/** v0.17.1 (user): 60% less fall damage while a gear is engaged. */
	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) && Engine.gear(player) > 0) {
			return amount * EngineConfig.FALL_DAMAGE_MULT_IN_GEAR;
		}
		return amount;
	}

	@Override
	public void tick(ServerPlayer player) {
		EngineMoves.tick(player);
	}

	/** Plus Ultra: Recipro Burst -- Extend. */
	@Override
	public void ultimate(ServerPlayer player) {
		EngineMoves.burst(player);
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
			case SLOT_5 -> s.val(Engine.V_GEAR_NO) > 0L;
			case SLOT_3 -> s.val(Engine.V_DASH) > now || s.val(Engine.V_STRIKE) > now || s.val(Engine.V_BRAKE) != 0L;
			case SLOT_2 -> s.val(Engine.V_BARRAGE) > now || s.val(Engine.V_EXTEND_KICK) > now;
			case SLOT_6 -> s.val(Engine.V_TURBO) > now || s.val(Engine.V_FINISHER) > now || s.val(Engine.V_BURST) > now;
			case SLOT_1 -> s.val(Engine.V_CHAIN) > now;
			default -> false;
		};
	}

	/** While Recipro Turbo runs its key reads ready (lit): pressing it ends Turbo. */
	@Override
	public int cooldownLeft(Player player, String abilityId) {
		if (abilityId.equals(EngineConfig.TURBO) && Engine.running(player, Engine.V_TURBO)) {
			return 0;
		}
		return Quirks.cooldownRemaining(player, abilityId);
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

	/** v0.17.1 (menus): hold R = the Charged Kick, hold X while running = the Engine Brake (display ids; the kit times the hold). */
	public static final String HOLD_CHARGED_KICK = "quirk_engine_charged_kick";
	public static final String HOLD_BRAKE = "quirk_engine_brake";

	@Override
	public String holdIdOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HOLD_CHARGED_KICK;
			case SLOT_3 -> HOLD_BRAKE;
			default -> null;
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case EngineConfig.SHIFT_UP, EngineConfig.SHIFT_DOWN -> EngineConfig.SHIFT_COOLDOWN;
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

package com.projecthero.mod.quirk.erasure;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Erasure's six keys (see {@link ErasureConfig} for the table). The moves live in {@link ErasureMoves}.
 * V (the stare) is held: it runs until V is let go (the blink) or his eyes dry out; X's swing is held too (letting go
 * releases the scarf).
 */
public final class ErasureKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			switch (slot) {
				case SLOT_5 -> ErasureMoves.eraseUp(player);
				case SLOT_3 -> ErasureMoves.swingUp(player);
				default -> {
				}
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// X again while swinging lets go, whatever its cooldown (Shift+X mid-swing reels up the line instead)
		if (slot == AbilitySlot.SLOT_3 && !shift && Quirks.state(player).val(ErasureConfig.V_LINE) != 0L) {
			ErasureMoves.detach(player, true);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ErasureConfig.BIND -> ErasureMoves.bind(player);
			case ErasureConfig.LASH -> ErasureMoves.pull(player);
			case ErasureConfig.COMBO -> ErasureMoves.combo(player);
			case ErasureConfig.COUNTER -> ErasureMoves.counter(player);
			case ErasureConfig.SWING -> ErasureMoves.swingDown(player);
			case ErasureConfig.ZIP -> ErasureMoves.zip(player);
			case ErasureConfig.SPIN -> ErasureMoves.spin(player);
			case ErasureConfig.DISARM -> ErasureMoves.disarm(player);
			case ErasureConfig.WHIP -> ErasureMoves.whip(player);
			case ErasureConfig.GOGGLES -> ErasureMoves.goggles(player);
			case ErasureConfig.ERASE -> ErasureMoves.eraseDown(player);
			case ErasureConfig.DROPS -> ErasureMoves.eyeDrops(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		ErasureMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ErasureMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return ErasureMoves.modifyIncomingDamage(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		ErasureMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		ErasureMoves.forget(id);
	}

	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		// eyes forced shut: no stare until they recover (Eye Drops still work)
		return slot == AbilitySlot.SLOT_5 && !shift && ErasureMoves.eyesShut(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(ErasureConfig.V_BIND) != 0L && s.val(ErasureConfig.V_BIND_UNTIL) > now;
			case SLOT_2 -> s.val(ErasureConfig.V_COUNTER) > now || (s.val(ErasureConfig.V_COMBO_AT) != 0L
					&& now - s.val(ErasureConfig.V_COMBO_AT) < ErasureConfig.COMBO_TICKS);
			case SLOT_3 -> s.val(ErasureConfig.V_LINE) != 0L;
			case SLOT_4 -> s.val(ErasureConfig.V_SPIN) != 0L;
			case SLOT_5 -> s.val(ErasureConfig.V_ERASE) != 0L;
			case SLOT_6 -> s.val(ErasureConfig.V_GOGGLES) != 0L;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ErasureConfig.BIND, ErasureConfig.LASH };
			case SLOT_2 -> new String[] { ErasureConfig.COMBO, ErasureConfig.COUNTER };
			case SLOT_3 -> new String[] { ErasureConfig.SWING, ErasureConfig.ZIP };
			case SLOT_4 -> new String[] { ErasureConfig.SPIN, ErasureConfig.DISARM };
			case SLOT_5 -> new String[] { ErasureConfig.ERASE, ErasureConfig.DROPS };
			case SLOT_6 -> new String[] { ErasureConfig.WHIP, ErasureConfig.GOGGLES };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ErasureConfig.BIND -> ErasureConfig.BIND_COOLDOWN;
			case ErasureConfig.LASH -> ErasureConfig.LASH_COOLDOWN;
			case ErasureConfig.COMBO -> ErasureConfig.COMBO_COOLDOWN;
			case ErasureConfig.COUNTER -> ErasureConfig.COUNTER_COOLDOWN;
			case ErasureConfig.SWING -> ErasureConfig.SWING_COOLDOWN;
			case ErasureConfig.ZIP -> ErasureConfig.ZIP_COOLDOWN;
			case ErasureConfig.SPIN -> ErasureConfig.SPIN_COOLDOWN;
			case ErasureConfig.DISARM -> ErasureConfig.DISARM_COOLDOWN;
			case ErasureConfig.WHIP -> ErasureConfig.WHIP_COOLDOWN;
			case ErasureConfig.GOGGLES -> ErasureConfig.GOGGLES_COOLDOWN;
			case ErasureConfig.ERASE -> ErasureConfig.ERASE_COOLDOWN;
			case ErasureConfig.DROPS -> ErasureConfig.DROPS_COOLDOWN;
			default -> 0;
		};
	}
}

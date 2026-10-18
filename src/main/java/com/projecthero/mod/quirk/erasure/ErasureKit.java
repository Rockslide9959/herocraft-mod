package com.projecthero.mod.quirk.erasure;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Erasure's six keys (see {@link ErasureConfig} for the table). The moves live in {@link ErasureMoves}. G is
 * held (the stare runs until G is let go or Dry Eyes runs out); X's swing is held too (letting go releases the scarf).
 */
public final class ErasureKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			switch (slot) {
				case SLOT_2 -> ErasureMoves.eraseUp(player);
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
		// X again while on the scarf lets go, whatever its cooldown
		if (slot == AbilitySlot.SLOT_3 && Quirks.state(player).val(ErasureConfig.V_LINE) != 0L) {
			ErasureMoves.detach(player, true);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ErasureConfig.LASH -> ErasureMoves.lash(player);
			case ErasureConfig.DISARM -> ErasureMoves.disarm(player);
			case ErasureConfig.ERASE -> ErasureMoves.eraseDown(player, false);
			case ErasureConfig.GLARE -> ErasureMoves.eraseDown(player, true);
			case ErasureConfig.SWING -> ErasureMoves.swingDown(player);
			case ErasureConfig.ZIP -> ErasureMoves.zip(player);
			case ErasureConfig.BIND -> ErasureMoves.bind(player, false);
			case ErasureConfig.HOIST -> ErasureMoves.bind(player, true);
			case ErasureConfig.SLAM -> ErasureMoves.slam(player);
			case ErasureConfig.DOUBLE -> ErasureMoves.doubleBind(player);
			case ErasureConfig.GOGGLES -> ErasureMoves.goggles(player);
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
	public void clearSessionState() {
		ErasureMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		ErasureMoves.forget(id);
	}

	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_6 && shift;
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> now - s.val(ErasureConfig.V_LASH_AT) < ErasureConfig.LASH_SHOW_TICKS && (s.animId == ErasureConfig.ANIM_LASH
					|| s.animId == ErasureConfig.ANIM_DISARM);
			case SLOT_2 -> s.val(ErasureConfig.V_ERASE) != 0L;
			case SLOT_3 -> s.val(ErasureConfig.V_LINE) != 0L;
			case SLOT_4 -> s.val(ErasureConfig.V_BIND) != 0L && s.val(ErasureConfig.V_BIND_UNTIL) > now;
			case SLOT_5 -> s.val(ErasureConfig.V_SLAM) != 0L || s.val(ErasureConfig.V_DB_A) != 0L;
			case SLOT_6 -> s.val(ErasureConfig.V_GOGGLES) != 0L;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ErasureConfig.LASH, ErasureConfig.DISARM };
			case SLOT_2 -> new String[] { ErasureConfig.ERASE, ErasureConfig.GLARE };
			case SLOT_3 -> new String[] { ErasureConfig.SWING, ErasureConfig.ZIP };
			case SLOT_4 -> new String[] { ErasureConfig.BIND, ErasureConfig.HOIST };
			case SLOT_5 -> new String[] { ErasureConfig.SLAM, ErasureConfig.DOUBLE };
			case SLOT_6 -> new String[] { ErasureConfig.GOGGLES, ErasureConfig.DROPS };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ErasureConfig.LASH -> ErasureConfig.LASH_COOLDOWN;
			case ErasureConfig.DISARM -> ErasureConfig.DISARM_COOLDOWN;
			case ErasureConfig.ERASE -> ErasureConfig.ERASE_COOLDOWN;
			case ErasureConfig.GLARE -> ErasureConfig.GLARE_COOLDOWN;
			case ErasureConfig.SWING -> ErasureConfig.SWING_COOLDOWN;
			case ErasureConfig.ZIP -> ErasureConfig.ZIP_COOLDOWN;
			case ErasureConfig.BIND -> ErasureConfig.BIND_COOLDOWN;
			case ErasureConfig.HOIST -> ErasureConfig.HOIST_COOLDOWN;
			case ErasureConfig.SLAM -> ErasureConfig.SLAM_COOLDOWN;
			case ErasureConfig.DOUBLE -> ErasureConfig.DOUBLE_COOLDOWN;
			case ErasureConfig.GOGGLES -> ErasureConfig.GOGGLES_COOLDOWN;
			case ErasureConfig.DROPS -> ErasureConfig.DROPS_COOLDOWN;
			default -> 0;
		};
	}
}

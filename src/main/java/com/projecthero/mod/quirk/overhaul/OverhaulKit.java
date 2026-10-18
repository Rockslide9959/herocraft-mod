package com.projecthero.mod.quirk.overhaul;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Overhaul's six keys (Kai Chisaki). Every move is a touch: an empty main hand, and Focus to spend.
 * <pre>
 *   R  Disassemble       Shift+R  Shrapnel Burst
 *   G  Spike Wave        Shift+G  Spike Field
 *   X  Reshape Wall      Shift+X  Launch Ramp
 *   Z  Restore           Shift+Z  Reassemble Armour
 *   V  Overhaul Field    Shift+V  Collapse Pit
 *   C  Fusion            Shift+C  Shard Shot
 * </pre>
 * The moves live in {@link OverhaulMoves}; every number is in {@link OverhaulConfig}.
 */
public final class OverhaulKit implements QuirkKit {
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
			case OverhaulConfig.DISASSEMBLE -> OverhaulMoves.disassemble(player);
			case OverhaulConfig.SHRAPNEL -> OverhaulMoves.shrapnel(player);
			case OverhaulConfig.SPIKE_WAVE -> OverhaulMoves.spikeWave(player);
			case OverhaulConfig.SPIKE_FIELD -> OverhaulMoves.spikeField(player);
			case OverhaulConfig.WALL -> OverhaulMoves.wall(player);
			case OverhaulConfig.RAMP -> OverhaulMoves.ramp(player);
			case OverhaulConfig.RESTORE -> OverhaulMoves.restore(player);
			case OverhaulConfig.REASSEMBLE -> OverhaulMoves.reassemble(player);
			case OverhaulConfig.FIELD -> OverhaulMoves.field(player);
			case OverhaulConfig.PIT -> OverhaulMoves.pit(player);
			case OverhaulConfig.FUSION -> OverhaulMoves.fusion(player);
			case OverhaulConfig.SHARD -> OverhaulMoves.shard(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		OverhaulMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		OverhaulMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return OverhaulMoves.modifyIncomingDamage(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		OverhaulMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		OverhaulMoves.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> Overhaul.running(player, Overhaul.V_WALL);
			case SLOT_5 -> Overhaul.running(player, Overhaul.V_PIT);
			case SLOT_6 -> Overhaul.fused(player);
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { OverhaulConfig.DISASSEMBLE, OverhaulConfig.SHRAPNEL };
			case SLOT_2 -> new String[] { OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_FIELD };
			case SLOT_3 -> new String[] { OverhaulConfig.WALL, OverhaulConfig.RAMP };
			case SLOT_4 -> new String[] { OverhaulConfig.RESTORE, OverhaulConfig.REASSEMBLE };
			case SLOT_5 -> new String[] { OverhaulConfig.FIELD, OverhaulConfig.PIT };
			case SLOT_6 -> new String[] { OverhaulConfig.FUSION, OverhaulConfig.SHARD };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case OverhaulConfig.DISASSEMBLE -> OverhaulConfig.DISASSEMBLE_COOLDOWN;
			case OverhaulConfig.SHRAPNEL -> OverhaulConfig.SHRAPNEL_COOLDOWN;
			case OverhaulConfig.SPIKE_WAVE -> OverhaulConfig.SPIKE_WAVE_COOLDOWN;
			case OverhaulConfig.SPIKE_FIELD -> OverhaulConfig.SPIKE_FIELD_COOLDOWN;
			case OverhaulConfig.WALL -> OverhaulConfig.WALL_COOLDOWN;
			case OverhaulConfig.RAMP -> OverhaulConfig.RAMP_COOLDOWN;
			case OverhaulConfig.RESTORE -> OverhaulConfig.RESTORE_COOLDOWN;
			case OverhaulConfig.REASSEMBLE -> OverhaulConfig.REASSEMBLE_COOLDOWN;
			case OverhaulConfig.FIELD -> OverhaulConfig.FIELD_COOLDOWN;
			case OverhaulConfig.PIT -> OverhaulConfig.PIT_COOLDOWN;
			case OverhaulConfig.FUSION -> OverhaulConfig.FUSION_COOLDOWN;
			case OverhaulConfig.SHARD -> OverhaulConfig.SHARD_COOLDOWN;
			default -> 0;
		};
	}
}

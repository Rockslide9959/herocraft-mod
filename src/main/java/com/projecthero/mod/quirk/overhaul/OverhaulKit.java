package com.projecthero.mod.quirk.overhaul;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 revamp: Overhaul's six keys (Kai Chisaki) -- see {@link OverhaulConfig} for the table. Every move but the
 * gloves is a bare-handed touch and spends Focus; Terrain Surf (X) is held. The moves live in {@link OverhaulMoves}.
 */
public final class OverhaulKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			// v0.16.7 (user): Terrain Surf is held -- letting X go gets off the wave (whatever Shift says now)
			if (slot == AbilitySlot.SLOT_3) {
				OverhaulMoves.endSurf(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		// V again while fused ends the form, whatever the cooldown says
		if (id.equals(OverhaulConfig.FUSION) && Overhaul.fused(player)) {
			OverhaulMoves.endFusion(player);
			return;
		}
		// X again while surfing gets off the wave too (a release that never arrived)
		if (id.equals(OverhaulConfig.SURF) && OverhaulMoves.surfing(player)) {
			OverhaulMoves.endSurf(player);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case OverhaulConfig.DISASSEMBLE -> OverhaulMoves.disassemble(player);
			case OverhaulConfig.REASSEMBLE -> OverhaulMoves.reassemble(player);
			case OverhaulConfig.SPIKE_WAVE -> OverhaulMoves.spikeWave(player);
			case OverhaulConfig.SPIKE_FIELD -> OverhaulMoves.spikeField(player);
			case OverhaulConfig.WALL -> OverhaulMoves.wall(player);
			case OverhaulConfig.PIT -> OverhaulMoves.pit(player);
			case OverhaulConfig.STRIKE -> OverhaulMoves.strike(player);
			case OverhaulConfig.DISARM -> OverhaulMoves.disarm(player);
			case OverhaulConfig.FUSION -> OverhaulMoves.fusion(player);
			case OverhaulConfig.SURF -> OverhaulMoves.surf(player);
			case OverhaulConfig.GLOVES -> OverhaulMoves.gloves(player);
			case OverhaulConfig.PILLAR -> OverhaulMoves.pillar(player);
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
	public float modifyIncomingDamage(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float amount) {
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

	/** The gloves (Shift+C) work while his focus is spent. */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_6 && shift;
	}

	@Override
	public boolean keyLocked(Player player, AbilitySlot slot, boolean shift) {
		// gloves on: every touch is held back (the HUD dims those keys)
		return !(slot == AbilitySlot.SLOT_6 && shift) && Overhaul.gloved(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_3 -> Overhaul.running(player, Overhaul.V_SURF);
			case SLOT_4 -> Overhaul.running(player, Overhaul.V_STRIKE) || Overhaul.running(player, Overhaul.V_PIT);
			case SLOT_5 -> Overhaul.fused(player);
			case SLOT_6 -> Overhaul.gloved(player) || Overhaul.running(player, Overhaul.V_WALL);
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { OverhaulConfig.DISASSEMBLE, OverhaulConfig.DISARM };
			case SLOT_2 -> new String[] { OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_FIELD };
			case SLOT_3 -> new String[] { OverhaulConfig.SURF, OverhaulConfig.PILLAR };
			case SLOT_4 -> new String[] { OverhaulConfig.STRIKE, OverhaulConfig.PIT };
			case SLOT_5 -> new String[] { OverhaulConfig.FUSION, OverhaulConfig.REASSEMBLE };
			case SLOT_6 -> new String[] { OverhaulConfig.WALL, OverhaulConfig.GLOVES };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case OverhaulConfig.DISASSEMBLE -> OverhaulConfig.DISASSEMBLE_COOLDOWN;
			case OverhaulConfig.REASSEMBLE -> OverhaulConfig.REASSEMBLE_COOLDOWN;
			case OverhaulConfig.SPIKE_WAVE -> OverhaulConfig.SPIKE_WAVE_COOLDOWN;
			case OverhaulConfig.SPIKE_FIELD -> OverhaulConfig.SPIKE_FIELD_COOLDOWN;
			case OverhaulConfig.WALL -> OverhaulConfig.WALL_COOLDOWN;
			case OverhaulConfig.PIT -> OverhaulConfig.PIT_COOLDOWN;
			case OverhaulConfig.STRIKE -> OverhaulConfig.STRIKE_COOLDOWN;
			case OverhaulConfig.DISARM -> OverhaulConfig.DISARM_COOLDOWN;
			case OverhaulConfig.FUSION -> OverhaulConfig.FUSION_COOLDOWN;
			case OverhaulConfig.SURF -> OverhaulConfig.SURF_COOLDOWN;
			case OverhaulConfig.GLOVES -> OverhaulConfig.GLOVES_COOLDOWN;
			case OverhaulConfig.PILLAR -> OverhaulConfig.PILLAR_COOLDOWN;
			default -> 0;
		};
	}
}

package com.projecthero.mod.quirk.overhaul;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Overhaul revamp: his six keys (Kai Chisaki) -- see {@link OverhaulConfig} for the table. G, X and V each also have a
 * HOLD move: their tap fires when the key comes up (before {@link OverhaulMoves#holdTicks}), the hold once it is held past it
 * (Thorn Forest, Terrain Surf, Restore). Shift versions fire at once on the press. Hold C at a full Plus Ultra gauge is the
 * framework's (Total Disassembly, {@link #ultimate}). The moves live in {@link OverhaulMoves}.
 */
public final class OverhaulKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			OverhaulMoves.holdUp(player, slot);
			return;
		}
		if (Overhaul.ultimate(player)) {
			return; // both palms on the ground until it is done
		}
		// X again while surfing gets off the wave (a release that never arrived)
		if (slot == AbilitySlot.SLOT_3 && OverhaulMoves.surfing(player)) {
			OverhaulMoves.endSurf(player);
			return;
		}
		if (!shift && OverhaulMoves.holdTicks(slot) > 0) {
			OverhaulMoves.holdDown(player, slot);
			return;
		}
		String id = moveFor(player, slot, shift);
		if (id == null) {
			return;
		}
		// Shift+V while fused ends the form, whatever the cooldown says
		if (id.equals(OverhaulConfig.FUSION) && Overhaul.fused(player)) {
			OverhaulMoves.endFusion(player);
			return;
		}
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case OverhaulConfig.DISASSEMBLE -> OverhaulMoves.disassemble(player);
			case OverhaulConfig.DISARM -> OverhaulMoves.disarm(player);
			case OverhaulConfig.SPIKE_FIELD -> OverhaulMoves.spikeField(player);
			case OverhaulConfig.PILLAR -> OverhaulMoves.pillar(player);
			case OverhaulConfig.STRIKE -> OverhaulMoves.strike(player);
			case OverhaulConfig.PIT -> OverhaulMoves.pit(player);
			case OverhaulConfig.SHRAPNEL -> OverhaulMoves.shrapnel(player);
			case OverhaulConfig.WALL -> OverhaulMoves.wall(player);
			case OverhaulConfig.FUSION -> OverhaulMoves.fusion(player);
			case OverhaulConfig.MEND -> OverhaulMoves.mend(player);
			default -> {
			}
		}
	}

	/** The move a press fires at once (holds are routed before this): Shift+V is Mend with a damaged item in the off hand. */
	private static String moveFor(ServerPlayer player, AbilitySlot slot, boolean shift) {
		if (slot == AbilitySlot.SLOT_5 && shift && !Overhaul.fused(player) && !OverhaulMoves.mendable(player).isEmpty()) {
			return OverhaulConfig.MEND;
		}
		String[] ids = OverhaulKitIds.of(slot);
		return ids.length < 2 ? null : ids[shift ? 1 : 0];
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

	@Override
	public void ultimate(ServerPlayer player) {
		OverhaulMoves.ultimate(player);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> Overhaul.thornAiming(player);
			case SLOT_3 -> Overhaul.running(player, Overhaul.V_SURF) || Overhaul.running(player, Overhaul.V_SHIFT);
			case SLOT_4 -> Overhaul.running(player, Overhaul.V_STRIKE) || Overhaul.running(player, Overhaul.V_PIT);
			case SLOT_5 -> Overhaul.fused(player);
			case SLOT_6 -> Overhaul.running(player, Overhaul.V_WALL) || Overhaul.ultimate(player);
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return OverhaulKitIds.of(slot);
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case OverhaulConfig.DISASSEMBLE -> OverhaulConfig.DISASSEMBLE_COOLDOWN;
			case OverhaulConfig.REASSEMBLE -> OverhaulConfig.REASSEMBLE_COOLDOWN;
			case OverhaulConfig.SPIKE_WAVE -> OverhaulConfig.SPIKE_WAVE_COOLDOWN;
			case OverhaulConfig.SPIKE_FIELD -> OverhaulConfig.SPIKE_FIELD_COOLDOWN;
			case OverhaulConfig.THORN -> OverhaulConfig.THORN_COOLDOWN;
			case OverhaulConfig.WALL -> OverhaulConfig.WALL_COOLDOWN;
			case OverhaulConfig.SHRAPNEL -> OverhaulConfig.SHRAPNEL_COOLDOWN;
			case OverhaulConfig.PIT -> OverhaulConfig.PIT_COOLDOWN;
			case OverhaulConfig.STRIKE -> OverhaulConfig.STRIKE_COOLDOWN;
			case OverhaulConfig.DISARM -> OverhaulConfig.DISARM_COOLDOWN;
			case OverhaulConfig.FUSION -> OverhaulConfig.FUSION_COOLDOWN;
			case OverhaulConfig.SURF -> OverhaulConfig.SURF_COOLDOWN;
			case OverhaulConfig.GROUND_SHIFT -> OverhaulConfig.GROUND_SHIFT_COOLDOWN;
			case OverhaulConfig.PILLAR -> OverhaulConfig.PILLAR_COOLDOWN;
			case OverhaulConfig.RESTORE -> OverhaulConfig.RESTORE_COOLDOWN;
			case OverhaulConfig.MEND -> OverhaulConfig.MEND_COOLDOWN;
			default -> 0;
		};
	}

	/** The [tap, Shift] ids of each key (the HUD, the guide, the move list). */
	static final class OverhaulKitIds {
		private OverhaulKitIds() {
		}

		static String[] of(AbilitySlot slot) {
			return switch (slot) {
				case SLOT_1 -> new String[] { OverhaulConfig.DISASSEMBLE, OverhaulConfig.DISARM };
				case SLOT_2 -> new String[] { OverhaulConfig.SPIKE_WAVE, OverhaulConfig.SPIKE_FIELD };
				case SLOT_3 -> new String[] { OverhaulConfig.GROUND_SHIFT, OverhaulConfig.PILLAR };
				case SLOT_4 -> new String[] { OverhaulConfig.STRIKE, OverhaulConfig.PIT };
				case SLOT_5 -> new String[] { OverhaulConfig.REASSEMBLE, OverhaulConfig.FUSION };
				case SLOT_6 -> new String[] { OverhaulConfig.SHRAPNEL, OverhaulConfig.WALL };
				default -> new String[0];
			};
		}
	}
}

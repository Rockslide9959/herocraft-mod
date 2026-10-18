package com.projecthero.mod.miraculous.catnoir;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir's six keys (Plagg's Miraculous, the Ring). The moves are in {@link CatNoirAbilities} and
 * {@link CatNoirCataclysm}; the ids in {@link CatNoir}. Only a key going DOWN does anything, except X (v0.16.6: the Pole
 * Vault is held -- the baton keeps extending until X is let go).
 */
public final class CatNoirKit implements KwamiKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			// v0.16.6 (user): X is held -- letting go flings him off the pole
			if (slot == AbilitySlot.SLOT_3) {
				CatNoirAbilities.releaseVault(player);
			}
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					CatNoirAbilities.sweep(player);
				} else {
					CatNoirAbilities.strike(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					CatNoirAbilities.throwBaton(player);
				} else {
					CatNoirAbilities.extend(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					CatNoirAbilities.rooftop(player);
				} else {
					CatNoirAbilities.vault(player);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					CatNoirCataclysm.call(player);
				} else {
					CatNoirAbilities.spin(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					CatNoirAbilities.pounce(player);
				} else {
					CatNoirAbilities.leap(player);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					CatNoirAbilities.reflexes(player);
				} else {
					CatNoirAbilities.nightVision(player);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!CatNoir.active(player)) {
			return;
		}
		CatNoirAbilities.tick(player);
		CatNoirCataclysm.tick(player);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		CatNoirAbilities.end(player);
	}

	@Override
	public void clearSessionState() {
		CatNoirAbilities.clearSessionState();
		CatNoirCataclysm.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		CatNoirAbilities.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		if (!CatNoir.active(player)) {
			return false;
		}
		MiraculousState s = Miraculous.peek(player);
		long now = player.level().getGameTime();
		boolean anim = s != null && now - s.animStart >= 0;
		return switch (slot) {
			case SLOT_1 -> anim && (s.animId == CatNoir.ANIM_STRIKE && now - s.animStart < CatNoirConfig.STRIKE_TICKS
					|| s.animId == CatNoir.ANIM_SWEEP && now - s.animStart < CatNoirConfig.SWEEP_TICKS);
			case SLOT_2 -> CatNoir.thrownId(player) >= 0 || CatNoir.val(player, CatNoir.V_EXTEND_LENGTH) > 0L;
			case SLOT_3 -> CatNoir.rooftop(player) || anim && s.animId == CatNoir.ANIM_VAULT && now - s.animStart < CatNoirConfig.VAULT_POLE_TICKS;
			case SLOT_4 -> CatNoir.charged(player) || anim && s.animId == CatNoir.ANIM_SPIN && now - s.animStart < CatNoirConfig.SPIN_TICKS;
			case SLOT_5 -> anim && (s.animId == CatNoir.ANIM_LEAP && now - s.animStart < CatNoirConfig.LEAP_TICKS
					|| s.animId == CatNoir.ANIM_POUNCE && now - s.animStart < CatNoirConfig.POUNCE_MAX_TICKS);
			case SLOT_6 -> CatNoir.nightVision(player) || CatNoir.reflexesLeft(player) > 0;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { CatNoir.STRIKE, CatNoir.SWEEP };
			case SLOT_2 -> new String[] { CatNoir.EXTEND, CatNoir.THROW };
			case SLOT_3 -> new String[] { CatNoir.VAULT, CatNoir.ROOFTOP };
			case SLOT_4 -> new String[] { CatNoir.SPIN, CatNoir.CATACLYSM };
			case SLOT_5 -> new String[] { CatNoir.LEAP, CatNoir.POUNCE };
			case SLOT_6 -> new String[] { CatNoir.NIGHT_VISION, CatNoir.REFLEXES };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case CatNoir.STRIKE -> CatNoirConfig.STRIKE_COOLDOWN;
			case CatNoir.SWEEP -> CatNoirConfig.SWEEP_COOLDOWN;
			case CatNoir.EXTEND -> CatNoirConfig.EXTEND_COOLDOWN;
			case CatNoir.THROW -> CatNoirConfig.THROW_COOLDOWN;
			case CatNoir.VAULT -> CatNoirConfig.VAULT_COOLDOWN;
			case CatNoir.ROOFTOP -> CatNoirConfig.ROOFTOP_TOGGLE_COOLDOWN;
			case CatNoir.SPIN -> CatNoirConfig.SPIN_COOLDOWN;
			case CatNoir.CATACLYSM -> CatNoirConfig.CATACLYSM_COOLDOWN;
			case CatNoir.LEAP -> CatNoirConfig.LEAP_COOLDOWN;
			case CatNoir.POUNCE -> CatNoirConfig.POUNCE_COOLDOWN;
			case CatNoir.NIGHT_VISION -> CatNoirConfig.NIGHT_VISION_TOGGLE_COOLDOWN;
			case CatNoir.REFLEXES -> CatNoirConfig.REFLEX_COOLDOWN;
			default -> 0;
		};
	}
}

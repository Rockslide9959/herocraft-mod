package com.projecthero.mod.miraculous.ladybug;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Ladybug's six keys -- the Yo-yo.
 *
 * <pre>
 *   R  Yo-yo Strike            Shift+R  Ricochet
 *   G  Yo-yo Bind (G again: yank)       Shift+G  Lasso Fling
 *   X  Yo-yo Swing (hold)      Shift+X  Zip Line
 *   Z  Spinning Shield (hold)  Shift+Z  LUCKY CHARM / (holding it) Miraculous Ladybug
 *   V  Pole Vault              Shift+V  Trip Wire
 *   C  Yo-yo Scan              Shift+C  Rescue Line
 * </pre>
 * The moves: {@link LadybugMoves}; Lucky Charm and Miraculous Ladybug: {@link LuckyCharm}; the restore memory:
 * {@link LadybugRestore}. Every number: {@link LadybugConfig}.
 */
public final class LadybugKit implements KwamiKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		switch (slot) {
			case SLOT_1 -> {
				if (pressed) {
					if (shift) {
						LadybugMoves.ricochet(player);
					} else {
						LadybugMoves.strike(player);
					}
				}
			}
			case SLOT_2 -> {
				if (pressed) {
					if (shift) {
						LadybugMoves.fling(player);
					} else {
						LadybugMoves.bindOrYank(player);
					}
				}
			}
			case SLOT_3 -> {
				if (pressed) {
					LadybugMoves.lineDown(player, shift);
				} else {
					LadybugMoves.lineUp(player);
				}
			}
			case SLOT_4 -> {
				if (pressed) {
					if (shift) {
						LuckyCharm.handle(player);
					} else {
						LadybugMoves.shieldUp(player);
					}
				} else {
					LadybugMoves.shieldDown(player);
				}
			}
			case SLOT_5 -> {
				if (pressed) {
					if (shift) {
						LadybugMoves.wire(player);
					} else {
						LadybugMoves.vault(player);
					}
				}
			}
			case SLOT_6 -> {
				if (pressed) {
					if (shift) {
						LadybugMoves.rescue(player);
					} else {
						LadybugMoves.scan(player);
					}
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		if (!Miraculous.transformedAs(player, Kwami.TIKKI)) {
			LadybugRestore.markInactive(player);
			return;
		}
		LadybugRestore.markActive(player);
		MiraculousState s = Miraculous.state(player);
		LadybugMoves.tick(player);
		LuckyCharm.tick(player, Miraculous.state(player));
		if (s.val(LadybugKeys.V_ML_AT) != 0L && player.level().getGameTime() - s.val(LadybugKeys.V_ML_AT) > LadybugConfig.SWARM_TICKS + 20) {
			Miraculous.edit(player, n -> {
				n.vals.remove(LadybugKeys.V_ML_AT);
				n.vecs.remove(LadybugKeys.P_ML);
			});
		}
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		LadybugMoves.detach(player, false);
		LadybugMoves.end(player);
		LuckyCharm.removeAll(player);
		LadybugRestore.markInactive(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return LadybugMoves.shieldDamage(player, source, amount);
	}

	@Override
	public void clearSessionState() {
		LadybugMoves.clearSessionState();
		LuckyCharm.clearSessionState();
		LadybugRestore.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		LadybugMoves.forget(id);
		LuckyCharm.forget(id);
		LadybugRestore.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = LadybugKeys.state(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_2 -> LadybugKeys.bound(player) >= 0;
			case SLOT_3 -> s.val(LadybugKeys.V_LINE) != 0L;
			case SLOT_4 -> s.val(LadybugKeys.V_SHIELD) != 0L || s.val(LadybugKeys.V_CHARM) != 0L;
			case SLOT_5 -> s.val(LadybugKeys.V_WIRE_UNTIL) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { LadybugKeys.STRIKE, LadybugKeys.RICOCHET };
			case SLOT_2 -> new String[] { LadybugKeys.BIND, LadybugKeys.FLING };
			case SLOT_3 -> new String[] { LadybugKeys.SWING, LadybugKeys.ZIP };
			case SLOT_4 -> new String[] { LadybugKeys.SHIELD, LadybugKeys.LUCKY_CHARM };
			case SLOT_5 -> new String[] { LadybugKeys.VAULT, LadybugKeys.WIRE };
			case SLOT_6 -> new String[] { LadybugKeys.SCAN, LadybugKeys.RESCUE };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case LadybugKeys.STRIKE -> LadybugConfig.STRIKE_COOLDOWN;
			case LadybugKeys.RICOCHET -> LadybugConfig.RICOCHET_COOLDOWN;
			case LadybugKeys.BIND -> LadybugConfig.BIND_COOLDOWN;
			case LadybugKeys.FLING -> LadybugConfig.FLING_COOLDOWN;
			case LadybugKeys.SWING -> LadybugConfig.SWING_COOLDOWN;
			case LadybugKeys.ZIP -> LadybugConfig.ZIP_COOLDOWN;
			case LadybugKeys.SHIELD -> LadybugConfig.SHIELD_COOLDOWN;
			case LadybugKeys.VAULT -> LadybugConfig.VAULT_COOLDOWN;
			case LadybugKeys.WIRE -> LadybugConfig.WIRE_COOLDOWN;
			case LadybugKeys.SCAN -> LadybugConfig.SCAN_COOLDOWN;
			case LadybugKeys.RESCUE -> LadybugConfig.RESCUE_COOLDOWN;
			default -> 0;
		};
	}
}

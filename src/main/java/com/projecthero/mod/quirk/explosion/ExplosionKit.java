package com.projecthero.mod.quirk.explosion;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp), v0.17.1 (Quirks revamp): Explosion's six keys (Katsuki Bakugo / Ground Zero). User rule: X is movement,
 * V utility, the rest attack; H is only the power selector and there is no N.
 * <pre>
 *   key  tap                  Shift                    hold
 *   R    AP Shot              AP Shot: Auto-Cannon     AP Shot: Pierce (charges; the release fires it)
 *   G    Explosive Punch      Blast Rush               Crush Grip
 *   X    Explosion Dash       Spin Dodge               Explosion Propulsion (flies while held)
 *   Z    Cluster              Explosive Uppercut
 *   C    Howitzer Impact      Gauntlet Grenade         Plus Ultra (full gauge): Howitzer Impact -- Cluster
 *   V    Stun Grenade         Cool Off (works while the arms are shot)
 * </pre>
 * On R, G and X a plain press waits: released within {@link ExplosionConfig#HOLD_TICKS} it is the tap move (fired on the
 * release), held past it the hold move starts. Shift moves fire on the press. The moves live in {@link ExplosionMoves};
 * every number is in {@link ExplosionConfig}.
 */
public final class ExplosionKit implements QuirkKit {
	/** The keys that have a hold move (slot ordinals): R, G, X. */
	private static boolean hasHold(AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3;
	}

	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		int i = slot.ordinal();
		if (!pressed) {
			if (!hasHold(slot)) {
				return;
			}
			boolean tap = ExplosionMoves.keyUp(player, i);
			// the hold ends on the release (whatever Shift says now)
			switch (slot) {
				case SLOT_1 -> {
					if (ExplosionMoves.piercing(player)) {
						ExplosionMoves.firePierce(player);
					}
				}
				case SLOT_3 -> ExplosionMoves.endPropel(player);
				default -> {
				}
			}
			if (tap) {
				fire(player, ids[0]);
			}
			return;
		}
		if (shift || !hasHold(slot)) {
			fire(player, ids[shift ? 1 : 0]);
			return;
		}
		ExplosionMoves.keyDown(player, i);
	}

	/** Runs one move by id when it is ready and nothing big is running. */
	static void fire(ServerPlayer player, String id) {
		if (ExplosionMoves.busy(player) || !Quirks.ready(player, id) || Quirks.overloaded(player) && !id.equals(ExplosionConfig.COOL_OFF)) {
			return;
		}
		switch (id) {
			case ExplosionConfig.PROPULSION -> ExplosionMoves.startPropel(player);
			case ExplosionConfig.DASH -> ExplosionMoves.blastDash(player);
			case ExplosionConfig.SPIN_DODGE -> ExplosionMoves.spinDodge(player);
			case ExplosionConfig.STUN_GRENADE -> ExplosionMoves.stunGrenade(player);
			case ExplosionConfig.COOL_OFF -> ExplosionMoves.coolOff(player);
			case ExplosionConfig.AP_SHOT -> ExplosionMoves.apShot(player);
			case ExplosionConfig.AUTO_CANNON -> ExplosionMoves.autoCannon(player);
			case ExplosionConfig.PIERCE -> ExplosionMoves.startPierce(player);
			case ExplosionConfig.PUNCH -> ExplosionMoves.blastPunch(player);
			case ExplosionConfig.BLAST_RUSH -> ExplosionMoves.blastRush(player);
			case ExplosionConfig.CRUSH_GRIP -> ExplosionMoves.crushGrip(player);
			case ExplosionConfig.CLUSTER -> ExplosionMoves.cluster(player);
			case ExplosionConfig.UPPERCUT -> ExplosionMoves.uppercut(player);
			case ExplosionConfig.HOWITZER -> ExplosionMoves.howitzer(player);
			case ExplosionConfig.GAUNTLET -> ExplosionMoves.gauntlet(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		// a plain press held long enough becomes the key's hold move
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3 }) {
			if (ExplosionMoves.holdDue(player, slot.ordinal())) {
				String[] ids = idsOf(slot);
				fire(player, ids[2]);
			}
		}
		ExplosionMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ExplosionMoves.stopAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return Explosion.is(player) && ExplosionMoves.cancelsDamage(player, source) ? 0f : amount;
	}

	/** Cool Off dries the sweat even with the arms shot. */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_5 && shift;
	}

	@Override
	public void clearSessionState() {
		ExplosionMoves.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		ExplosionMoves.forget(id);
	}

	// ---------------------------------------------------------------- v0.17.1 framework

	@Override
	public void ultimate(ServerPlayer player) {
		ExplosionMoves.ultimate(player);
	}

	@Override
	public void onSuitChanged(ServerPlayer player, boolean fullSet) {
		ExplosionMoves.suitChanged(player, fullSet);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Explosion.V_AUTO) > now || s.val(Explosion.V_PIERCE) != 0L;
			case SLOT_2 -> s.val(Explosion.V_BLAST_RUSH) > now || s.val(Explosion.V_GRIP) != 0L;
			case SLOT_3 -> s.val(Explosion.V_PROPEL) != 0L || s.val(Explosion.V_DODGE) > now;
			case SLOT_5 -> s.val(Explosion.V_COOL) > now;
			case SLOT_6 -> s.val(Explosion.V_HOWITZER) > now || s.val(Explosion.V_GAUNTLET) > now;
			default -> false;
		};
	}

	/** [tap, Shift, hold] -- the third (hold) entry only on R, G and X (the guide and the info screen list it). */
	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ExplosionConfig.AP_SHOT, ExplosionConfig.AUTO_CANNON, ExplosionConfig.PIERCE };
			case SLOT_2 -> new String[] { ExplosionConfig.PUNCH, ExplosionConfig.BLAST_RUSH, ExplosionConfig.CRUSH_GRIP };
			case SLOT_3 -> new String[] { ExplosionConfig.DASH, ExplosionConfig.SPIN_DODGE, ExplosionConfig.PROPULSION };
			case SLOT_4 -> new String[] { ExplosionConfig.CLUSTER, ExplosionConfig.UPPERCUT };
			case SLOT_5 -> new String[] { ExplosionConfig.STUN_GRENADE, ExplosionConfig.COOL_OFF };
			case SLOT_6 -> new String[] { ExplosionConfig.HOWITZER, ExplosionConfig.GAUNTLET };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ExplosionConfig.PROPULSION -> ExplosionConfig.PROPEL_COOLDOWN;
			case ExplosionConfig.DASH -> ExplosionConfig.DASH_COOLDOWN;
			case ExplosionConfig.SPIN_DODGE -> ExplosionConfig.DODGE_COOLDOWN;
			case ExplosionConfig.STUN_GRENADE -> ExplosionConfig.STUN_COOLDOWN;
			case ExplosionConfig.COOL_OFF -> ExplosionConfig.COOL_COOLDOWN;
			case ExplosionConfig.AP_SHOT -> ExplosionConfig.AP_COOLDOWN;
			case ExplosionConfig.AUTO_CANNON -> ExplosionConfig.AUTO_COOLDOWN;
			case ExplosionConfig.PIERCE -> ExplosionConfig.PIERCE_COOLDOWN;
			case ExplosionConfig.PUNCH -> ExplosionConfig.PUNCH_COOLDOWN;
			case ExplosionConfig.BLAST_RUSH -> ExplosionConfig.BLAST_RUSH_COOLDOWN;
			case ExplosionConfig.CRUSH_GRIP -> ExplosionConfig.GRIP_COOLDOWN;
			case ExplosionConfig.CLUSTER -> ExplosionConfig.CLUSTER_COOLDOWN;
			case ExplosionConfig.UPPERCUT -> ExplosionConfig.UPPERCUT_COOLDOWN;
			case ExplosionConfig.HOWITZER -> ExplosionConfig.HOWITZER_COOLDOWN;
			case ExplosionConfig.GAUNTLET -> ExplosionConfig.GAUNTLET_COOLDOWN;
			default -> 0;
		};
	}
}

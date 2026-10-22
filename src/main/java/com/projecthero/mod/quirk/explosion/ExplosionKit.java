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
 * v0.16.7 (revamp): Explosion's six keys (Katsuki Bakugo / Ground Zero). User rule: X is movement, V utility, the rest attack.
 * <pre>
 *   X  Explosion Propulsion (hold)   Shift+X  Explosion Dash
 *   V  Stun Grenade                  Shift+V  Cool Off (works while strained)
 *   R  AP Shot                       Shift+R  AP Shot: Auto-Cannon
 *   G  Explosive Punch               Shift+G  Blast Rush
 *   Z  Cluster                       Shift+Z  Howitzer Impact: Cluster
 *   C  Howitzer Impact               Shift+C  Gauntlet Grenade
 * </pre>
 * The moves live in {@link ExplosionMoves}; every number is in {@link ExplosionConfig}.
 */
public final class ExplosionKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			// Propulsion is held: letting X go ends it (whatever Shift says now)
			if (slot == AbilitySlot.SLOT_3) {
				ExplosionMoves.endPropel(player);
			}
			return;
		}
		String[] ids = idsOf(slot);
		if (ids.length < 2) {
			return;
		}
		String id = ids[shift ? 1 : 0];
		if (ExplosionMoves.busy(player) || !Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ExplosionConfig.PROPULSION -> ExplosionMoves.startPropel(player);
			case ExplosionConfig.DASH -> ExplosionMoves.blastDash(player);
			case ExplosionConfig.STUN_GRENADE -> ExplosionMoves.stunGrenade(player);
			case ExplosionConfig.COOL_OFF -> ExplosionMoves.coolOff(player);
			case ExplosionConfig.AP_SHOT -> ExplosionMoves.apShot(player);
			case ExplosionConfig.AUTO_CANNON -> ExplosionMoves.autoCannon(player);
			case ExplosionConfig.PUNCH -> ExplosionMoves.blastPunch(player);
			case ExplosionConfig.BLAST_RUSH -> ExplosionMoves.blastRush(player);
			case ExplosionConfig.CLUSTER -> ExplosionMoves.cluster(player);
			case ExplosionConfig.HOWITZER_CLUSTER -> ExplosionMoves.howitzerCluster(player);
			case ExplosionConfig.HOWITZER -> ExplosionMoves.howitzer(player);
			case ExplosionConfig.GAUNTLET -> ExplosionMoves.gauntlet(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
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

	/** Cool Off dries the sweat even with strained arms. */
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

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(Explosion.V_AUTO) > now;
			case SLOT_2 -> s.val(Explosion.V_BLAST_RUSH) > now;
			case SLOT_3 -> s.val(Explosion.V_PROPEL) != 0L;
			case SLOT_4 -> s.val(Explosion.V_HOWITZER) > now && s.val(Explosion.V_HOWITZER_KIND) == 2L;
			case SLOT_5 -> s.val(Explosion.V_COOL) > now;
			case SLOT_6 -> (s.val(Explosion.V_HOWITZER) > now && s.val(Explosion.V_HOWITZER_KIND) == 1L) || s.val(Explosion.V_GAUNTLET) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ExplosionConfig.AP_SHOT, ExplosionConfig.AUTO_CANNON };
			case SLOT_2 -> new String[] { ExplosionConfig.PUNCH, ExplosionConfig.BLAST_RUSH };
			case SLOT_3 -> new String[] { ExplosionConfig.PROPULSION, ExplosionConfig.DASH };
			case SLOT_4 -> new String[] { ExplosionConfig.CLUSTER, ExplosionConfig.HOWITZER_CLUSTER };
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
			case ExplosionConfig.STUN_GRENADE -> ExplosionConfig.STUN_COOLDOWN;
			case ExplosionConfig.COOL_OFF -> ExplosionConfig.COOL_COOLDOWN;
			case ExplosionConfig.AP_SHOT -> ExplosionConfig.AP_COOLDOWN;
			case ExplosionConfig.AUTO_CANNON -> ExplosionConfig.AUTO_COOLDOWN;
			case ExplosionConfig.PUNCH -> ExplosionConfig.PUNCH_COOLDOWN;
			case ExplosionConfig.BLAST_RUSH -> ExplosionConfig.BLAST_RUSH_COOLDOWN;
			case ExplosionConfig.CLUSTER -> ExplosionConfig.CLUSTER_COOLDOWN;
			case ExplosionConfig.HOWITZER_CLUSTER -> ExplosionConfig.HCLUSTER_COOLDOWN;
			case ExplosionConfig.HOWITZER -> ExplosionConfig.HOWITZER_COOLDOWN;
			case ExplosionConfig.GAUNTLET -> ExplosionConfig.GAUNTLET_COOLDOWN;
			default -> 0;
		};
	}
}

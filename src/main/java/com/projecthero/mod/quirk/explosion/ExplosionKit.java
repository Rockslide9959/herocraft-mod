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
 * v0.16.7: Explosion's six keys (Katsuki Bakugo / Ground Zero).
 * <pre>
 *   R  AP Shot             Shift+R  AP Shot Auto-Cannon
 *   G  Blast Punch         Shift+G  Explosion Uppercut
 *   X  Stun Grenade        Shift+X  Smoke Blast
 *   Z  Blast Rush (hold)   Shift+Z  Blast Dash
 *   V  Howitzer Impact     Shift+V  Max Shot
 *   C  Cluster             Shift+C  Explosive Counter
 * </pre>
 * The moves live in {@link ExplosionMoves}; every number is in {@link ExplosionConfig}.
 */
public final class ExplosionKit implements QuirkKit {
	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (!pressed) {
			// Blast Rush is held: letting Z go ends it (whatever Shift says now)
			if (slot == AbilitySlot.SLOT_4) {
				ExplosionMoves.endRush(player);
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
			case ExplosionConfig.AP_SHOT -> ExplosionMoves.apShot(player);
			case ExplosionConfig.AUTO_CANNON -> ExplosionMoves.autoCannon(player);
			case ExplosionConfig.BLAST_PUNCH -> ExplosionMoves.blastPunch(player);
			case ExplosionConfig.UPPERCUT -> ExplosionMoves.uppercut(player);
			case ExplosionConfig.STUN_GRENADE -> ExplosionMoves.stunGrenade(player);
			case ExplosionConfig.SMOKE_BLAST -> ExplosionMoves.smokeBlast(player);
			case ExplosionConfig.BLAST_RUSH -> ExplosionMoves.startRush(player);
			case ExplosionConfig.BLAST_DASH -> ExplosionMoves.blastDash(player);
			case ExplosionConfig.HOWITZER -> ExplosionMoves.howitzer(player);
			case ExplosionConfig.MAX_SHOT -> ExplosionMoves.maxShot(player);
			case ExplosionConfig.CLUSTER -> ExplosionMoves.cluster(player);
			case ExplosionConfig.COUNTER -> ExplosionMoves.counter(player);
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
			case SLOT_3 -> s.val(Explosion.V_SMOKE) > now;
			case SLOT_4 -> s.val(Explosion.V_RUSH) != 0L;
			case SLOT_5 -> s.val(Explosion.V_HOWITZER) > now || s.val(Explosion.V_MAX_SHOT) > now;
			case SLOT_6 -> s.val(Explosion.V_COUNTER) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ExplosionConfig.AP_SHOT, ExplosionConfig.AUTO_CANNON };
			case SLOT_2 -> new String[] { ExplosionConfig.BLAST_PUNCH, ExplosionConfig.UPPERCUT };
			case SLOT_3 -> new String[] { ExplosionConfig.STUN_GRENADE, ExplosionConfig.SMOKE_BLAST };
			case SLOT_4 -> new String[] { ExplosionConfig.BLAST_RUSH, ExplosionConfig.BLAST_DASH };
			case SLOT_5 -> new String[] { ExplosionConfig.HOWITZER, ExplosionConfig.MAX_SHOT };
			case SLOT_6 -> new String[] { ExplosionConfig.CLUSTER, ExplosionConfig.COUNTER };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ExplosionConfig.AP_SHOT -> ExplosionConfig.AP_COOLDOWN;
			case ExplosionConfig.AUTO_CANNON -> ExplosionConfig.AUTO_COOLDOWN;
			case ExplosionConfig.BLAST_PUNCH -> ExplosionConfig.PUNCH_COOLDOWN;
			case ExplosionConfig.UPPERCUT -> ExplosionConfig.UPPERCUT_COOLDOWN;
			case ExplosionConfig.STUN_GRENADE -> ExplosionConfig.STUN_COOLDOWN;
			case ExplosionConfig.SMOKE_BLAST -> ExplosionConfig.SMOKE_COOLDOWN;
			case ExplosionConfig.BLAST_RUSH -> ExplosionConfig.RUSH_COOLDOWN;
			case ExplosionConfig.BLAST_DASH -> ExplosionConfig.DASH_COOLDOWN;
			case ExplosionConfig.HOWITZER -> ExplosionConfig.HOWITZER_COOLDOWN;
			case ExplosionConfig.MAX_SHOT -> ExplosionConfig.MAX_SHOT_COOLDOWN;
			case ExplosionConfig.CLUSTER -> ExplosionConfig.CLUSTER_COOLDOWN;
			case ExplosionConfig.COUNTER -> ExplosionConfig.COUNTER_COOLDOWN;
			default -> 0;
		};
	}
}

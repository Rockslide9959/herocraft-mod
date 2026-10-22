package com.projecthero.mod.quirk.zerogravity;

import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.16.7 revamp: Zero Gravity's six keys (Ochaco Uraraka / Uravity). User rule (every Quirk): X is the MOVEMENT key, V the
 * UTILITY key, R / G / Z / C (and their Shift moves) mainly attacks. Touch / Release are her utility pair -- they set up and
 * finish every other move -- so they sit on V.
 * <pre>
 *   R  Meteor Shower (R again: rain)   Shift+R  Debris Shield
 *   G  Comet Toss                      Shift+G  Zero Gravity Slam
 *   X  Float Self (toggle)             Shift+X  Zero-G Leap
 *   Z  Gunhead Martial Arts            Shift+Z  Zero Gravity Field
 *   V  Zero Gravity Touch              Shift+V  Release
 *   C  Catch                           Shift+C  Settle (works while sick)
 * </pre>
 * The moves live in {@link ZeroGravityMoves}; every number is in {@link ZeroGravityConfig}. None is held. Float Self can always
 * be switched off and a hanging Meteor Shower cloud can always be rained, cooldown or not.
 */
public final class ZeroGravityKit implements QuirkKit {
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
		boolean free = (id.equals(ZeroGravityConfig.SELF) && ZeroGravityFloat.selfFloating(player))
				|| (id.equals(ZeroGravityConfig.SHOWER) && ZeroGravityDebris.cloudUp(player));
		if (!free && !Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ZeroGravityConfig.SHOWER -> ZeroGravityMoves.meteorShower(player, null);
			case ZeroGravityConfig.SHIELD -> ZeroGravityMoves.debrisShield(player, null);
			case ZeroGravityConfig.COMET -> ZeroGravityMoves.cometToss(player);
			case ZeroGravityConfig.SLAM -> ZeroGravityMoves.slam(player);
			case ZeroGravityConfig.SELF -> ZeroGravityMoves.floatSelf(player);
			case ZeroGravityConfig.LEAP -> ZeroGravityMoves.leap(player);
			case ZeroGravityConfig.GUNHEAD -> ZeroGravityMoves.gunhead(player);
			case ZeroGravityConfig.FIELD -> ZeroGravityMoves.field(player);
			case ZeroGravityConfig.TOUCH -> ZeroGravityMoves.touch(player);
			case ZeroGravityConfig.RELEASE -> ZeroGravityMoves.release(player);
			case ZeroGravityConfig.CATCH -> ZeroGravityMoves.catchFalling(player);
			case ZeroGravityConfig.SETTLE -> ZeroGravityMoves.settle(player);
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		ZeroGravityMoves.tick(player);
	}

	@Override
	public void stopAll(ServerPlayer player) {
		ZeroGravityMoves.endAll(player);
	}

	/** The Debris Shield stops projectiles (the tick normally catches them before they land). */
	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof Projectile && ZeroGravityDebris.shieldUp(player)) {
			return 0f;
		}
		return amount;
	}

	@Override
	public void clearSessionState() {
		ZeroGravityMoves.clearSessionState();
		ZeroGravityFloat.clearSessionState();
		ZeroGravityDebris.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		ZeroGravityMoves.forget(id);
		ZeroGravityFloat.forget(id);
		ZeroGravityDebris.forget(id);
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(ZeroGravity.V_CLOUD) > now || s.val(ZeroGravity.V_SHIELD) > now;
			case SLOT_3 -> s.val(ZeroGravity.V_SELF) != 0L || s.val(ZeroGravity.V_LEAP) > now;
			case SLOT_4 -> s.val(ZeroGravity.V_GUNHEAD) > now || s.val(ZeroGravity.V_FIELD) > now;
			case SLOT_5 -> s.val(ZeroGravity.V_FLOATING) > 0L || s.val(ZeroGravity.V_BLOCKS) > 0L;
			case SLOT_6 -> s.val(ZeroGravity.V_SETTLE) > now;
			default -> false;
		};
	}

	/** Settle (Shift+C) works while she is sick. */
	@Override
	public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
		return slot == AbilitySlot.SLOT_6 && shift;
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ZeroGravityConfig.SHOWER, ZeroGravityConfig.SHIELD };
			case SLOT_2 -> new String[] { ZeroGravityConfig.COMET, ZeroGravityConfig.SLAM };
			case SLOT_3 -> new String[] { ZeroGravityConfig.SELF, ZeroGravityConfig.LEAP };
			case SLOT_4 -> new String[] { ZeroGravityConfig.GUNHEAD, ZeroGravityConfig.FIELD };
			case SLOT_5 -> new String[] { ZeroGravityConfig.TOUCH, ZeroGravityConfig.RELEASE };
			case SLOT_6 -> new String[] { ZeroGravityConfig.CATCH, ZeroGravityConfig.SETTLE };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ZeroGravityConfig.SHOWER -> ZeroGravityConfig.SHOWER_COOLDOWN;
			case ZeroGravityConfig.SHIELD -> ZeroGravityConfig.SHIELD_COOLDOWN;
			case ZeroGravityConfig.COMET -> ZeroGravityConfig.COMET_COOLDOWN;
			case ZeroGravityConfig.SLAM -> ZeroGravityConfig.SLAM_COOLDOWN;
			case ZeroGravityConfig.SELF -> ZeroGravityConfig.SELF_COOLDOWN;
			case ZeroGravityConfig.LEAP -> ZeroGravityConfig.LEAP_COOLDOWN;
			case ZeroGravityConfig.GUNHEAD -> ZeroGravityConfig.GUNHEAD_COOLDOWN;
			case ZeroGravityConfig.FIELD -> ZeroGravityConfig.FIELD_COOLDOWN;
			case ZeroGravityConfig.TOUCH -> ZeroGravityConfig.TOUCH_COOLDOWN;
			case ZeroGravityConfig.RELEASE -> ZeroGravityConfig.RELEASE_COOLDOWN;
			case ZeroGravityConfig.CATCH -> ZeroGravityConfig.CATCH_COOLDOWN;
			case ZeroGravityConfig.SETTLE -> ZeroGravityConfig.SETTLE_COOLDOWN;
			default -> 0;
		};
	}
}

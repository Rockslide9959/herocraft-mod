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
 * v0.16.7: Zero Gravity's six keys (Ochaco Uraraka / Uravity). The moves live in {@link ZeroGravityMoves}; every number
 * is in {@link ZeroGravityConfig}. None is held: each fires on the key going down.
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
		if (!Quirks.ready(player, id)) {
			return;
		}
		switch (id) {
			case ZeroGravityConfig.TOUCH -> ZeroGravityMoves.floatTouch(player);
			case ZeroGravityConfig.FLING -> ZeroGravityMoves.floatFling(player);
			case ZeroGravityConfig.RELEASE -> ZeroGravityMoves.release(player);
			case ZeroGravityConfig.SLAM -> ZeroGravityMoves.meteorSlam(player);
			case ZeroGravityConfig.SHOWER -> ZeroGravityMoves.meteorShower(player, null);
			case ZeroGravityConfig.SHIELD -> ZeroGravityMoves.debrisShield(player, null);
			case ZeroGravityConfig.SELF -> ZeroGravityMoves.zeroGSelf(player);
			case ZeroGravityConfig.LEAP -> ZeroGravityMoves.floatLeap(player);
			case ZeroGravityConfig.COMET -> ZeroGravityMoves.comet(player);
			case ZeroGravityConfig.METEOR -> ZeroGravityMoves.uravityMeteor(player, null);
			case ZeroGravityConfig.THROW -> ZeroGravityMoves.gunheadThrow(player);
			case ZeroGravityConfig.COMBO -> ZeroGravityMoves.gunheadCombo(player);
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
			case SLOT_1 -> s.val(ZeroGravity.V_FLOATING) > 0L;
			case SLOT_3 -> s.val(ZeroGravity.V_SHIELD) > now || s.val(ZeroGravity.V_SHOWER) > now;
			case SLOT_4 -> s.val(ZeroGravity.V_SELF) != 0L || s.val(ZeroGravity.V_LEAP) > now;
			case SLOT_5 -> s.val(ZeroGravity.V_METEOR) > now;
			case SLOT_6 -> s.val(ZeroGravity.V_COMBO) > now;
			default -> false;
		};
	}

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { ZeroGravityConfig.TOUCH, ZeroGravityConfig.FLING };
			case SLOT_2 -> new String[] { ZeroGravityConfig.RELEASE, ZeroGravityConfig.SLAM };
			case SLOT_3 -> new String[] { ZeroGravityConfig.SHOWER, ZeroGravityConfig.SHIELD };
			case SLOT_4 -> new String[] { ZeroGravityConfig.SELF, ZeroGravityConfig.LEAP };
			case SLOT_5 -> new String[] { ZeroGravityConfig.COMET, ZeroGravityConfig.METEOR };
			case SLOT_6 -> new String[] { ZeroGravityConfig.THROW, ZeroGravityConfig.COMBO };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case ZeroGravityConfig.TOUCH -> ZeroGravityConfig.TOUCH_COOLDOWN;
			case ZeroGravityConfig.FLING -> ZeroGravityConfig.FLING_COOLDOWN;
			case ZeroGravityConfig.RELEASE -> ZeroGravityConfig.RELEASE_COOLDOWN;
			case ZeroGravityConfig.SLAM -> ZeroGravityConfig.SLAM_COOLDOWN;
			case ZeroGravityConfig.SHOWER -> ZeroGravityConfig.SHOWER_COOLDOWN;
			case ZeroGravityConfig.SHIELD -> ZeroGravityConfig.SHIELD_COOLDOWN;
			case ZeroGravityConfig.SELF -> ZeroGravityConfig.SELF_COOLDOWN;
			case ZeroGravityConfig.LEAP -> ZeroGravityConfig.LEAP_COOLDOWN;
			case ZeroGravityConfig.COMET -> ZeroGravityConfig.COMET_COOLDOWN;
			case ZeroGravityConfig.METEOR -> ZeroGravityConfig.METEOR_COOLDOWN;
			case ZeroGravityConfig.THROW -> ZeroGravityConfig.THROW_COOLDOWN;
			case ZeroGravityConfig.COMBO -> ZeroGravityConfig.COMBO_COOLDOWN;
			default -> 0;
		};
	}
}

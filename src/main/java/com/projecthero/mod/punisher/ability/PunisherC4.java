package com.projecthero.mod.punisher.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherFeedback;
import com.projecthero.mod.punisher.entity.C4ChargeEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 6 (C) -- Explosive Charge. Press C looking at a surface to place a charge (up to
 * {@link PunisherConfig#C4_MAX_ACTIVE}). Sneak + C detonates every charge <em>you</em> placed -- a
 * charge remembers its owner, so one Punisher never sets off another's (spec section 27). Stronger
 * than the Frag Grenade, moderate terrain damage.
 *
 * <p>The per-player charge list is a static map of entity ids; it is pruned when a charge is removed
 * ({@link C4ChargeEntity#remove}) and cleared on death / logout / server stop
 * ({@link com.projecthero.mod.diagnostics.ServerStateReset}).
 */
public final class PunisherC4 {
	public static final String ABILITY = "explosive_charge";

	private static final Map<UUID, List<Integer>> CHARGES = new ConcurrentHashMap<>();

	private PunisherC4() {
	}

	public static void clearSessionState() {
		CHARGES.clear();
	}

	public static void forget(UUID owner, int entityId) {
		List<Integer> list = CHARGES.get(owner);
		if (list != null) {
			list.remove(Integer.valueOf(entityId));
			if (list.isEmpty()) {
				CHARGES.remove(owner);
			}
		}
	}

	public static void clearFor(UUID owner) {
		CHARGES.remove(owner);
	}

	public static int activeCount(UUID owner) {
		List<Integer> list = CHARGES.get(owner);
		return list == null ? 0 : list.size();
	}

	public static void place(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		if (activeCount(player.getUUID()) >= PunisherConfig.C4_MAX_ACTIVE) {
			PunisherFeedback.message(player, "c4_max");
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, PunisherConfig.C4_PLACE_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			PunisherFeedback.message(player, "c4_no_surface");
			return;
		}
		Vec3 pos = hit.getLocation().add(new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(),
				hit.getDirection().getStepZ()).scale(0.06));
		ServerLevel level = player.serverLevel();
		C4ChargeEntity charge = new C4ChargeEntity(level, pos, player);
		level.addFreshEntity(charge);
		CHARGES.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(charge.getId());

		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6f, 0.8f);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.C4_COOLDOWN_TICKS);
		PunisherFeedback.message(player, "c4_placed");
	}

	public static void detonateAll(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		List<Integer> ids = CHARGES.remove(player.getUUID());
		if (ids == null || ids.isEmpty()) {
			PunisherFeedback.message(player, "c4_none");
			return;
		}
		ServerLevel level = player.serverLevel();
		int blown = 0;
		for (int id : new ArrayList<>(ids)) {
			Entity e = level.getEntity(id);
			if (e instanceof C4ChargeEntity c4 && c4.isAlive()) {
				c4.detonate();
				blown++;
			}
		}
		if (blown > 0) {
			player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		}
	}
}

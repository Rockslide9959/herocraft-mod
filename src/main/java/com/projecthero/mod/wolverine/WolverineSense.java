package com.projecthero.mod.wolverine;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.network.WolverineSensePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The Wolverine's enhanced senses: every mob hunting him glows orange (always on), and the N-key Sniff
 * highlights every living thing within 40 blocks for 20 seconds. Both are pure "tell this one client"
 * packets -- the outline is drawn by the Wolverine's own client, so nobody else can see it.
 */
public final class WolverineSense {
	private static final String SNIFF_GUARD = "sniff";

	private WolverineSense() {
	}

	/** Mobs currently targeting {@code player} within the hunter radius. */
	public static int[] hunters(ServerPlayer player) {
		double r = WolverineConfig.HUNTER_RADIUS;
		List<Mob> mobs = player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
				m -> m.isAlive() && m.getTarget() == player && m.distanceToSqr(player) <= r * r);
		int[] ids = new int[mobs.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = mobs.get(i).getId();
		}
		return ids;
	}

	/** Called every server tick for a Wolverine: refresh the hunter list on the scan interval. */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % WolverineConfig.HUNTER_SCAN_TICKS != 0) {
			return;
		}
		int[] ids = hunters(player);
		// an empty list is only worth a packet now and then (the client set expires by itself anyway)
		if (ids.length > 0 || player.tickCount % (WolverineConfig.HUNTER_SCAN_TICKS * 4) == 0) {
			ServerPlayNetworking.send(player, new WolverineSensePayload(ids, new int[0], 0));
		}
	}

	/** Right click with the claws out: strike with the off-hand claw at whatever is in reach. */
	public static void offHandStrike(ServerPlayer player) {
		if (!Wolverine.hasPower(player) || !Wolverine.clawsOut(player) || !player.getOffhandItem().isEmpty()
				|| !Wolverine.abilityReady(player, "offhand_strike")) {
			return;
		}
		Wolverine.triggerCooldown(player, "offhand_strike", WolverineConfig.OFFHAND_STRIKE_GUARD);
		player.swing(net.minecraft.world.InteractionHand.OFF_HAND, true);
		double reach = WolverineConfig.OFFHAND_STRIKE_REACH;
		net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
		net.minecraft.world.phys.Vec3 end = eye.add(player.getLookAngle().scale(reach));
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
				player, eye, end, player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0),
				e -> !e.isSpectator() && e.isPickable() && e != player, reach * reach);
		if (hit != null) {
			// Same damage as the right hand (bare-hand 1 + 3 + 8 claw bonus, +50% in Rage) at full
			// strength. The vanilla attack path scales by the swing cooldown, so a hit landed right
			// after the other hand's swing was weak, and a held item changed the number.
			if (hit.getEntity() instanceof LivingEntity target) {
				float dmg = (float) (1.0 + WolverineConfig.MELEE_BONUS_DAMAGE + WolverineConfig.clawMeleeBonus(Wolverine.clawTier(player)));
				if (Wolverine.raging(player)) {
					dmg *= 1.0f + (float) WolverineConfig.RAGE_DAMAGE_BONUS;
				}
				if (com.projecthero.mod.hero.power.AbilityHelpers.hurt(player, target, dmg)) {
					com.projecthero.mod.hero.power.AbilityHelpers.knockbackFrom(target, player.position(), 0.5);
					if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
						net.minecraft.world.phys.Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.25, 0.3, 0.25, 0.15);
					}
				}
			} else {
				player.attack(hit.getEntity());
			}
			player.resetAttackStrengthTicker();
		}
	}

	/** N key: sniff the air. Highlights every living entity within range for {@link WolverineConfig#SNIFF_TICKS}. */
	public static void sniff(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!Wolverine.abilityReady(player, SNIFF_GUARD)) {
			return; // anti-spam only -- the sniff itself has no cooldown
		}
		Wolverine.triggerCooldown(player, SNIFF_GUARD, WolverineConfig.SNIFF_SPAM_GUARD);
		double r = WolverineConfig.SNIFF_RADIUS;
		List<LivingEntity> found = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
				player.getBoundingBox().inflate(r),
				e -> e != player && e.isAlive() && !(e instanceof ArmorStand) && e.distanceToSqr(player) <= r * r);
		List<Integer> ids = new ArrayList<>(found.size());
		for (LivingEntity e : found) {
			ids.add(e.getId());
		}
		ServerPlayNetworking.send(player, new WolverineSensePayload(hunters(player),
				ids.stream().mapToInt(Integer::intValue).toArray(), WolverineConfig.SNIFF_TICKS));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SNIFFER_SNIFFING, SoundSource.PLAYERS, 1.0f, 1.0f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SNIFFER_SEARCHING, SoundSource.PLAYERS, 0.5f, 1.2f);
	}
}

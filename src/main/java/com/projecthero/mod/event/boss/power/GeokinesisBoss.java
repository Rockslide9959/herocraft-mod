package com.projecthero.mod.event.boss.power;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Geokinesis, the design's worked example: rocks at range, a barrier against ranged attackers,
 * and a ground slam when it is surrounded.
 *
 * <ul>
 *   <li><b>Boulder</b> -- a real {@link FallingBlockEntity} lobbed at a distant target. It travels
 *       visibly and lands where physics puts it, so it is dodgeable and never pinpoint.</li>
 *   <li><b>Barrier</b> -- raises a short stone wall between itself and whoever just shot it. This is
 *       the defensive/emergency branch: it fires in response to taking ranged damage, not on a
 *       timer.</li>
 *   <li><b>Earthquake</b> -- a radial ground rupture used when players surround it or cluster.</li>
 * </ul>
 *
 * <p>Every block it places goes through the mod's existing {@link TempBlocks} queue, which means the
 * barrier restores itself on a timer and is subject to the same "abilityTerrainDamage" config as every
 * player ability. A Geokinesis boss therefore cannot leave permanent stone in someone's base, and its
 * blocks cannot leak: {@code TempBlocks} owns their lifetime.
 */
public class GeokinesisBoss extends BossPowerController {
	public static final String POWER_KEY = "power_05_geokinesis";

	private static final int SLOT_BOULDER = 0;
	private static final int SLOT_BARRIER = 1;
	private static final int SLOT_QUAKE = 2;
	private static final int BARRIER_TTL = 12 * 20;

	public GeokinesisBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return 9.0;
	}

	@Override
	public ParticleOptions auraParticle() {
		return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.YELLOW;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		Vec3 anchor = areaAnchor(level, target, 6.5, 2);
		if (anchor != null && ready(SLOT_QUAKE) && freshChoice(SLOT_QUAKE)) {
			earthquake(level);
			startCooldown(SLOT_QUAKE, 240);
			return;
		}
		double distance = boss.distanceTo(target);
		if (distance > 6.0 && distance < 28.0 && ready(SLOT_BOULDER) && boss.hasLineOfSight(target)) {
			// Heave up a rock: the boss stops and strains for ~1s (telegraph), then lobs it on a lead.
			beginRangedCast(level, target, SLOT_BOULDER, 110, SoundEvents.RAVAGER_STUNNED,
					auraParticle(), t -> boulder(level, t));
			return;
		}
		// Proactively wall off a target who is keeping their distance, not only in reaction to being
		// shot -- a Geokinesis boss should shape the ground even against someone who has not fired yet.
		if (distance > 12.0 && ready(SLOT_BARRIER) && boss.hasLineOfSight(target)) {
			barrier(level, target.position());
			startCooldown(SLOT_BARRIER, 300);
		}
	}

	/** Defensive branch: a ranged hit from any distance is what makes it put a wall up. */
	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (!readyReactive(SLOT_BARRIER)) {
			return;
		}
		boolean ranged = !source.isDirect()
				|| (source.getEntity() != null && source.getEntity().distanceTo(boss) > 6.0);
		if (!ranged || source.getEntity() == null) {
			return;
		}
		barrier(level, source.getEntity().position());
		startCooldown(SLOT_BARRIER, 300);
	}

	private void boulder(ServerLevel level, LivingEntity target) {
		BlockPos from = boss.blockPosition().above(2);
		FallingBlockEntity rock = FallingBlockEntity.fall(level, from, Blocks.STONE.defaultBlockState());
		rock.setHurtsEntities(4.0f, 14);
		rock.time = 1;
		Vec3 lead = target.position().add(0, 0.5, 0).add(target.getDeltaMovement().scale(6.0));
		Vec3 to = lead.subtract(rock.position());
		double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
		rock.setDeltaMovement(to.x * 0.06, 0.28 + horizontal * 0.02, to.z * 0.06);
		sound(level, SoundEvents.STONE_BREAK, 1.2f, 0.6f);
		particles(level, auraParticle(), boss.position().add(0, 1.5, 0), 12, 0.4);
	}

	/** A three-wide, three-tall wall two blocks in front of the boss, facing the shooter. */
	private void barrier(ServerLevel level, Vec3 towards) {
		Vec3 dir = towards.subtract(boss.position());
		if (dir.lengthSqr() < 1.0e-4) {
			return;
		}
		dir = new Vec3(dir.x, 0, dir.z).normalize();
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		BlockPos base = BlockPos.containing(boss.position().add(dir.scale(2.0)));
		boolean placedAny = false;
		for (int w = -1; w <= 1; w++) {
			for (int h = 0; h < 3; h++) {
				BlockPos pos = BlockPos.containing(
						base.getX() + side.x * w, base.getY() + h, base.getZ() + side.z * w);
				placedAny |= TempBlocks.place(level, pos, Blocks.STONE.defaultBlockState(), BARRIER_TTL);
			}
		}
		if (placedAny) {
			sound(level, SoundEvents.STONE_PLACE, 1.0f, 0.6f);
		}
	}

	private void earthquake(ServerLevel level) {
		sound(level, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.5f);
		level.sendParticles(auraParticle(), boss.getX(), boss.getY() + 0.2, boss.getZ(), 60, 4.0, 0.2, 4.0, 0.1);
		for (Player player : playersNear(level, 8.0)) {
			if (!player.onGround()) {
				continue; // a rupture in the ground cannot reach someone who is not standing on it
			}
			hurt(player, 8.0f);
			player.setDeltaMovement(player.getDeltaMovement().add(0, 0.5, 0));
			player.hurtMarked = true;
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
		}
	}
}

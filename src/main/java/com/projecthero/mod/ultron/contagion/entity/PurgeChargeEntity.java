package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.Assimilation;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.ContagionState;
import com.projecthero.mod.ultron.contagion.NaniteCorruption;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** v0.16.1: a thrown Purge Charge. It bursts on whatever it hits -- see {@link #burst}. */
public class PurgeChargeEntity extends ThrowableItemProjectile {
	public PurgeChargeEntity(EntityType<? extends PurgeChargeEntity> type, Level level) {
		super(type, level);
	}

	public PurgeChargeEntity(Level level, LivingEntity owner) {
		super(ContagionEntityTypes.PURGE_CHARGE, owner, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ContagionItems.PURGE_CHARGE;
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (level() instanceof ServerLevel server) {
			burst(server, result.getLocation());
			discard();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 2 == 0) {
			server.sendParticles(UltronFx.CYAN, getX(), getY(), getZ(), 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** The EMP: revert recorded corruption in range, stun robots, free machines / mobs / golems / Legion suits. */
	public static void burst(ServerLevel level, Vec3 at) {
		double r = ContagionConfig.purge().radius;
		ContagionState st = ContagionState.get(level);
		if (level.dimension() == Level.OVERWORLD) {
			NaniteCorruption.purgeArea(level, st, at, r);
			for (BlockPos m : new java.util.ArrayList<>(st.compromised.keySet())) {
				if (m.distToCenterSqr(at) <= r * r) {
					Assimilation.uncompromise(level, st, m);
				}
			}
		}
		Assimilation.stunRobots(level, at, r, ContagionConfig.purge().stunTicks);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r))) {
			if (e.distanceToSqr(at) > r * r) {
				continue;
			}
			if (e instanceof HijackedMarkEntity mark) {
				mark.powerDown();
			} else if (e instanceof IronGolem golem) {
				Assimilation.revertGolem(golem);
			} else if (e instanceof Mob mob) {
				Assimilation.strip(mob);
			}
		}
		UltronFx.forced(level, ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		UltronFx.forced(level, UltronFx.CYAN, at.x, at.y + 0.5, at.z, 70, r * 0.35, 0.8, r * 0.35, 0.15);
		UltronFx.forced(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 50, r * 0.3, 0.6, r * 0.3, 0.4);
		UltronFx.ring(level, UltronFx.CYAN, at.add(0, 0.3, 0), r, 48);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.0f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5f, 0.7f);
	}
}

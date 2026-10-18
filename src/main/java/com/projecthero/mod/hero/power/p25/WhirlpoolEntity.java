package com.projecthero.mod.hero.power.p25;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 hold G -- Whirlpool: an {@link WaterHandlers#WHIRLPOOL_RADIUS 8-across} vortex of water opens on the ground
 * where the caster aims (up to {@link WaterHandlers#WHIRLPOOL_RANGE} blocks) and spins for
 * {@link WaterHandlers#WHIRLPOOL_TICKS} ticks: everything hostile on it is dragged round and in to its centre (bosses
 * are not dragged), and everything on it takes {@link WaterHandlers#WHIRLPOOL_DAMAGE_PER_SECOND} drowning damage a
 * second. Fire on it goes out. The vortex is this entity -- the client draws the spinning, sunken water surface with its
 * spiral arms and foaming rim ({@code WaterFxRenderer}) -- never water blocks.
 */
public class WhirlpoolEntity extends WaterFxEntity {
	/** Ticks it takes to open up to full size (and to close at the end). */
	public static final int OPEN = 8;

	public WhirlpoolEntity(EntityType<? extends WhirlpoolEntity> type, Level level) {
		super(type, level);
	}

	public static WhirlpoolEntity open(ServerPlayer owner, Vec3 at) {
		ServerLevel level = AbilityHelpers.level(owner);
		WhirlpoolEntity e = new WhirlpoolEntity(WaterEntities.WHIRLPOOL, level);
		e.setup(owner, null, (float) WaterHandlers.WHIRLPOOL_RADIUS);
		e.setPos(at.x, at.y, at.z);
		level.addFreshEntity(e);
		return e;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (tickCount % 2 == 0 && tickCount < WaterHandlers.WHIRLPOOL_TICKS) {
				double a = random.nextDouble() * Math.PI * 2;
				double r = random.nextDouble() * size();
				level().addParticle(ParticleTypes.BUBBLE_POP, getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.05, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || tickCount > WaterHandlers.WHIRLPOOL_TICKS) {
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 60, size() * 0.5, 0.3, size() * 0.5, 0.2);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0f, 0.7f);
			discard();
			return;
		}
		spin(owner);
		if (tickCount % 20 == 0) {
			drown(owner);
		}
		if (tickCount % 10 == 0) {
			WaterHandlers.douse(level, BlockPos.containing(position().add(0, 0.5, 0)), 3);
		}
		if (tickCount % 3 == 0) {
			double a = tickCount * 0.5;
			double r = size() * 0.85;
			level.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(a) * r, getY() + 0.25, getZ() + Math.sin(a) * r, 4, 0.2, 0.1, 0.2, 0.1);
		}
		if (tickCount % 16 == 1) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.PLAYERS, 1.2f, 0.8f);
		}
	}

	/** Whether {@code e} is standing in the vortex (its feet within the radius, near its height). */
	public boolean inside(LivingEntity e) {
		Vec3 rel = e.position().subtract(position());
		double r = size() + e.getBbWidth() * 0.5;
		return rel.x * rel.x + rel.z * rel.z <= r * r && rel.y > -1.5 && rel.y < 2.5;
	}

	/** One tick of the drag: everything hostile on it is swung round and pulled in. Returns how many it moved. */
	public int spin(ServerPlayer owner) {
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(owner, position().add(0, 0.5, 0), size() + 2.0)) {
			if (!inside(e) || Squads.areAllies(owner, e)) {
				continue;
			}
			e.clearFire();
			e.fallDistance = 0;
			if (!WaterHandlers.isBossLike(e)) {
				Vec3 in = position().subtract(e.position());
				Vec3 flat = new Vec3(in.x, 0, in.z);
				double d = flat.length();
				Vec3 pull = d < 0.3 ? Vec3.ZERO : flat.scale(1.0 / d);
				Vec3 round = new Vec3(-pull.z, 0, pull.x); // counter-clockwise from above
				double inward = Math.min(0.32, 0.08 + d * 0.06);
				Vec3 v = e.getDeltaMovement();
				Vec3 want = pull.scale(inward).add(round.scale(d < 0.3 ? 0.0 : 0.22));
				e.setDeltaMovement(v.x * 0.35 + want.x, Math.min(v.y, 0.0) - 0.02, v.z * 0.35 + want.z);
				e.hurtMarked = true;
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
			n++;
		}
		return n;
	}

	/** One second of drowning for everything on it. Returns how many it hurt. */
	public int drown(ServerPlayer owner) {
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(owner, position().add(0, 0.5, 0), size() + 2.0)) {
			if (!inside(e) || Squads.areAllies(owner, e)) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(owner, e, owner.level().damageSources().source(DamageTypes.DROWN, owner),
					WaterHandlers.WHIRLPOOL_DAMAGE_PER_SECOND)) {
				n++;
			}
			((ServerLevel) level()).sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.25, 0.3, 0.25, 0.1);
		}
		return n;
	}
}

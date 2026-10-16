package com.projecthero.mod.hero.power.p25;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Z -- Tidal Wave: a 7-wide wall of water rears up in front of the caster and rolls {@link #DISTANCE} blocks along his
 * (flat) aim, riding up steps and down slopes. Everything hostile it reaches takes {@link WaterHandlers#TIDAL_DAMAGE}
 * once and is swept along with the crest (dropped items too); fire is put out -- on the creatures and in the blocks it
 * rolls over. A wall too tall to ride over breaks it early. It is an entity, not water blocks: the client draws the
 * curling translucent wall ({@code WaterFxRenderer}), it collapses into spray at the end, and nothing is left behind.
 *
 * <p>Timeline: {@link #RISE} ticks rearing up, then {@link #RUN} ticks rolling, then {@link #COLLAPSE} ticks breaking.
 * The synced size is 1 while it rolls; once it breaks it carries {@code 1000 + the tick it broke at}.
 */
public class TidalWaveEntity extends WaterFxEntity {
	public static final double DISTANCE = 16.0;
	public static final double HALF_WIDTH = 3.5;
	public static final double HEIGHT = 3.0;
	public static final int RISE = 4;
	public static final int RUN = 20;
	public static final int COLLAPSE = 8;
	public static final double SPEED = DISTANCE / RUN;

	private Vec3 dir = new Vec3(0, 0, 1);
	private final Set<Integer> hit = new HashSet<>();
	private int brokeAt = -1;

	public TidalWaveEntity(EntityType<? extends TidalWaveEntity> type, Level level) {
		super(type, level);
	}

	public static TidalWaveEntity roll(ServerPlayer owner) {
		ServerLevel level = AbilityHelpers.level(owner);
		TidalWaveEntity e = new TidalWaveEntity(WaterEntities.TIDAL_WAVE, level);
		e.setup(owner, null, 1.0f);
		Vec3 look = owner.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		e.dir = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, owner.getYRot()) : flat.normalize();
		Vec3 at = owner.position().add(e.dir.scale(1.2));
		e.setPos(at.x, owner.getY(), at.z);
		e.setYRot((float) (Mth.atan2(e.dir.z, e.dir.x) * Mth.RAD_TO_DEG) - 90.0f);
		level.addFreshEntity(e);
		return e;
	}

	/** Ticks since it broke (into a wall or at the end of its run), or -1 while it still rolls. Either side. */
	public int brokenFor(float partial) {
		float s = size();
		float start = s >= 999.5f ? s - 1000.0f : (tickCount <= RISE + RUN ? -1 : RISE + RUN);
		return start < 0 ? -1 : (int) Math.max(0, tickCount - start);
	}

	public Vec3 dir() {
		return Vec3.directionFromRotation(0, getYRot());
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (brokeAt >= 0) {
			if (tickCount - brokeAt >= COLLAPSE || owner == null) {
				discard();
			} else if (tickCount - brokeAt == 1) {
				spray(level);
			}
			return;
		}
		if (owner == null) {
			discard();
			return;
		}
		if (tickCount > RISE) {
			// roll on, following the ground
			Vec3 next = position().add(dir.scale(SPEED));
			Double y = groundAt(level, next.x, getY(), next.z);
			if (y == null) {
				breakUp(); // a wall too tall to ride over
				return;
			}
			setPos(next.x, y, next.z);
		}
		sweep(level, owner);
		if (tickCount >= RISE + RUN) {
			breakUp();
		}
	}

	private void breakUp() {
		brokeAt = tickCount;
		setSize(1000.0f + tickCount);
	}

	/** Everything in the wall's slab: hostiles take 14 once; everything is carried along. Fire goes out. */
	public void sweep(ServerLevel level, ServerPlayer owner) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 c = position();
		AABB box = new AABB(c.x - 4.5, c.y - 0.5, c.z - 4.5, c.x + 4.5, c.y + HEIGHT + 0.5, c.z + 4.5);
		for (Entity e : level.getEntities(this, box, e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
			if (e == owner) {
				continue;
			}
			Vec3 rel = e.position().subtract(c);
			double along = rel.dot(dir);
			double across = rel.dot(side);
			if (along < -1.4 || along > 1.6 || Math.abs(across) > HALF_WIDTH + e.getBbWidth() * 0.5) {
				continue;
			}
			if (e instanceof LivingEntity le) {
				if (!com.projecthero.mod.combat.HeroTargets.canHarm(owner, le) || com.projecthero.mod.squad.Squads.areAllies(owner, le)) {
					continue;
				}
				if (hit.add(le.getId())) {
					AbilityHelpers.hurtBurst(owner, le, WaterHandlers.TIDAL_DAMAGE);
					le.clearFire();
				}
				le.fallDistance = 0;
			}
			// swept along with the crest
			Vec3 v = e.getDeltaMovement();
			double ahead = v.dot(dir);
			Vec3 carry = v.subtract(dir.scale(ahead)).scale(0.5).add(dir.scale(Math.max(ahead, SPEED * 1.05)));
			e.setDeltaMovement(carry.x, Math.max(v.y, 0.12), carry.z);
			e.hurtMarked = true;
		}
		// douse the cells it rolls over
		for (int w = -3; w <= 3; w++) {
			Vec3 p = c.add(side.scale(w)).add(dir.scale(0.8));
			WaterHandlers.douse(level, BlockPos.containing(p.x, c.y + 0.2, p.z), 0);
			WaterHandlers.douse(level, BlockPos.containing(p.x, c.y + 1.2, p.z), 0);
		}
		if (tickCount % 2 == 0) {
			for (int w = -3; w <= 3; w += 2) {
				Vec3 p = c.add(side.scale(w + level.random.nextDouble() - 0.5)).add(dir.scale(0.9));
				level.sendParticles(ParticleTypes.SPLASH, p.x, c.y + 2.6, p.z, 3, 0.3, 0.2, 0.3, 0.15);
			}
		}
		if (tickCount % 5 == 0) {
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4f, 0.5f);
		}
	}

	/**
	 * The ground height the wave rides at near {@code (x, z)} (from about its current height {@code y}): up to 2 blocks of
	 * step up, down slopes as far as 4 below. Null when a wall (3+ blocks) stands in the way.
	 */
	public static Double groundAt(ServerLevel level, double x, double y, double z) {
		int bx = Mth.floor(x);
		int bz = Mth.floor(z);
		int base = Mth.floor(y + 0.01);
		for (int dy = 2; dy >= -4; dy--) {
			BlockPos cell = new BlockPos(bx, base + dy, bz);
			if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
				if (dy >= 2) {
					return null;
				}
				double top = cell.getY() + level.getBlockState(cell).getCollisionShape(level, cell).max(net.minecraft.core.Direction.Axis.Y);
				// solid here: the surface is on top of it, as long as the cell above is open
				return Math.max(top, y - 1.0 > top ? y - 1.0 : top);
			}
		}
		return y - 1.0; // open drop: sink a block a tick
	}

	private void spray(ServerLevel level) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		for (int w = -3; w <= 3; w++) {
			Vec3 p = position().add(side.scale(w)).add(dir.scale(0.6));
			level.sendParticles(ParticleTypes.SPLASH, p.x, getY() + 1.5, p.z, 10, 0.4, 0.8, 0.4, 0.3);
			level.sendParticles(ParticleTypes.FALLING_WATER, p.x, getY() + 2.5, p.z, 4, 0.4, 0.3, 0.4, 0.0);
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.5f, 0.6f);
	}
}

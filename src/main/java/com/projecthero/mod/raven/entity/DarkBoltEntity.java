package com.projecthero.mod.raven.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven, R: a bolt of black energy. Straight and fast ({@link RavenConfig#BOLT_SPEED}); it passes through the first creature
 * it hits ({@link RavenConfig#BOLT_PIERCE}) and stops in the next one or the first wall. The server moves it; the client draws a black
 * core in a violet glow ({@code DarkBoltRenderer}) with a wispy trail.
 */
public class DarkBoltEntity extends Projectile {
	private Vec3 start = Vec3.ZERO;
	private float damage = RavenConfig.BOLT_DAMAGE;
	private int pierceLeft = RavenConfig.BOLT_PIERCE;
	private final Set<Integer> struck = new HashSet<>();
	/** For the tests: creatures this bolt has hit. */
	public int hits;

	public DarkBoltEntity(EntityType<? extends DarkBoltEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Fires a bolt from {@code owner}'s eyes along {@code dir}, hitting for {@code damage}. */
	public static DarkBoltEntity fire(ServerPlayer owner, Vec3 dir, float damage) {
		DarkBoltEntity bolt = new DarkBoltEntity(RavenEntities.DARK_BOLT, owner.level());
		bolt.setOwner(owner);
		Vec3 from = owner.getEyePosition().add(0, -0.15, 0).add(dir.scale(0.6));
		bolt.setPos(from.x, from.y - 0.2, from.z);
		bolt.start = bolt.position();
		bolt.damage = damage;
		bolt.setDeltaMovement(dir.normalize().scale(RavenConfig.BOLT_SPEED));
		owner.level().addFreshEntity(bolt);
		return bolt;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level().isClientSide()) {
			if (tickCount > 1) {
				level().addParticle(ParticleTypes.SMOKE, from.x, from.y + 0.2, from.z, 0, 0, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		Entity ownerEntity = getOwner();
		ServerPlayer owner = ownerEntity instanceof ServerPlayer sp ? sp : null;
		if (owner == null || tickCount > 60) {
			discard();
			return;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean wall = block.getType() != HitResult.Type.MISS;
		if (wall) {
			to = block.getLocation();
		}
		// creatures along this step, nearest first
		AABB sweep = getBoundingBox().expandTowards(to.subtract(from)).inflate(0.5);
		List<LivingEntity> along = new ArrayList<>();
		for (Entity e : level.getEntities(this, sweep, e -> e instanceof LivingEntity le && le.isAlive() && e != owner && !struck.contains(e.getId())
				&& !(e instanceof Player p && p.isSpectator()) && HeroTargets.canHarm(owner, e))) {
			if (e.getBoundingBox().inflate(0.35).clip(from, to).isPresent() || e.getBoundingBox().inflate(0.35).contains(from)) {
				along.add((LivingEntity) e);
			}
		}
		Vec3 f = from;
		along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(f)));
		for (LivingEntity t : along) {
			struck.add(t.getId());
			if (Raven.hit(owner, t, damage)) {
				hits++;
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
				level.sendParticles(Raven.dust(Raven.VOID, 1.5f), c.x, c.y, c.z, 12, 0.25, 0.3, 0.25, 0.0);
				level.sendParticles(Raven.dust(Raven.VIOLET, 1.0f), c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, c.x, c.y, c.z, RavenSounds.BOLT_HIT, SoundSource.PLAYERS, 0.45f, 0.6f);
			}
			if (pierceLeft <= 0) {
				discard();
				return;
			}
			pierceLeft--;
		}
		if (wall) {
			Vec3 c = block.getLocation();
			level.sendParticles(Raven.dust(Raven.VOID, 1.4f), c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, 0.1, 0.1, 0.1, 0.01);
			level.playSound(null, c.x, c.y, c.z, RavenSounds.BOLT_HIT, SoundSource.PLAYERS, 0.3f, 0.5f);
			discard();
			return;
		}
		setPos(to.x, to.y, to.z);
		if (to.distanceToSqr(start) > RavenConfig.BOLT_RANGE * RavenConfig.BOLT_RANGE) {
			discard();
		}
	}
}

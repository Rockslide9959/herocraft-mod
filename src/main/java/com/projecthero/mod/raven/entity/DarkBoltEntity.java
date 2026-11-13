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
 * v0.17.3 Raven, R: a bolt of black energy. Straight and fast ({@link RavenConfig#BOLT_SPEED}); it stops in the first creature or
 * wall and bursts there in a small purple pop ({@link RavenConfig#BOLT_BURST_DAMAGE} to every other creature within
 * {@link RavenConfig#BOLT_BURST_RADIUS}). The server moves it; the client draws a black core in a violet glow
 * ({@code DarkBoltRenderer}) with a wispy trail.
 */
public class DarkBoltEntity extends Projectile {
	private Vec3 start = Vec3.ZERO;
	private float damage = RavenConfig.BOLT_DAMAGE;
	private final Set<Integer> struck = new HashSet<>();
	/** For the tests: creatures this bolt has hit (the burst included). */
	public int hits;

	public DarkBoltEntity(EntityType<? extends DarkBoltEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Fires a bolt from {@code owner}'s eyes along {@code dir}, hitting for {@code damage}. */
	public static DarkBoltEntity fire(ServerPlayer owner, Vec3 dir, float damage) {
		DarkBoltEntity bolt = new DarkBoltEntity(RavenEntities.DARK_BOLT, owner.level());
		bolt.setOwner(owner);
		// from her right hand, not her eyes (the glow would fill a first-person view)
		float yaw = owner.getYRot() * ((float) Math.PI / 180f);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 from = owner.getEyePosition().add(0, -0.35, 0).add(right.scale(0.4)).add(dir.scale(0.9));
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
		if (!along.isEmpty()) {
			LivingEntity t = along.get(0);
			struck.add(t.getId());
			if (Raven.hit(owner, t, damage)) {
				hits++;
			}
			burst(level, owner, t.position().add(0, t.getBbHeight() * 0.6, 0), t);
			discard();
			return;
		}
		if (wall) {
			Vec3 c = block.getLocation();
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 4, 0.1, 0.1, 0.1, 0.01);
			burst(level, owner, c, null);
			discard();
			return;
		}
		setPos(to.x, to.y, to.z);
		if (to.distanceToSqr(start) > RavenConfig.BOLT_RANGE * RavenConfig.BOLT_RANGE) {
			discard();
		}
	}

	/** The purple pop where it lands: {@link RavenConfig#BOLT_BURST_DAMAGE} to every other creature within reach. */
	private void burst(ServerLevel level, ServerPlayer owner, Vec3 c, LivingEntity struckFirst) {
		double r = RavenConfig.BOLT_BURST_RADIUS;
		for (LivingEntity o : HeroTargets.harmable(level, owner, new net.minecraft.world.phys.AABB(c, c).inflate(r))) {
			if (o == struckFirst || o.position().add(0, o.getBbHeight() * 0.5, 0).distanceTo(c) > r + o.getBbWidth() * 0.5) {
				continue;
			}
			if (Raven.hit(owner, o, RavenConfig.BOLT_BURST_DAMAGE)) {
				hits++;
			}
		}
		level.sendParticles(Raven.dust(Raven.VOID, 1.5f), c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.0);
		level.sendParticles(Raven.dust(Raven.VIOLET, 1.3f), c.x, c.y, c.z, 18, r * 0.35, r * 0.35, r * 0.35, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.25);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.BOLT_HIT, SoundSource.PLAYERS, 0.45f, 0.6f);
		com.projecthero.mod.raven.RavenMoves.fx(owner, new com.projecthero.mod.raven.network.RavenFxPayload(
				com.projecthero.mod.raven.network.RavenFxPayload.BURST, owner.getId(), c, new Vec3(r, 0, 0), 8));
	}
}

package com.projecthero.mod.spider.entity;

import com.projecthero.mod.spider.SpiderMoves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Web Bomb (Shift+R) as a real projectile -- a big, dense ball of webbing with loose strands
 * ({@code client.spider.WebShotRenderer} at bomb size, spinning and pulsing), lobbed in an arc to the aimed point. It
 * bursts where it lands -- on a body, a block, or its target point -- into the splash: the root, the damage, the real
 * cobwebs ({@link SpiderMoves#webBombBurst}).
 */
public class SpiderWebBombEntity extends AbstractHurtingProjectile {
	public static final double GRAVITY = 0.05;
	/** Horizontal speed of the lob (blocks / tick). */
	public static final double SPEED = 1.5;
	private static final int MAX_LIFE = 80;
	private int life;
	private Vec3 target;
	private boolean burst;

	public SpiderWebBombEntity(EntityType<? extends SpiderWebBombEntity> type, Level level) {
		super(type, level);
		this.accelerationPower = 0.0;
	}

	/** Lob from {@code from} so the arc comes down on {@code target}. */
	public SpiderWebBombEntity(Level level, LivingEntity owner, Vec3 from, Vec3 target) {
		super(SpiderEntityTypes.WEB_BOMB, owner, Vec3.ZERO, level);
		this.accelerationPower = 0.0;
		setPos(from);
		this.target = target;
		setDeltaMovement(launchVelocity(from, target));
	}

	/**
	 * The velocity that lands on {@code target}: T ticks of flight with gravity taken off before each move, so the drop
	 * over T ticks is g*T*(T+1)/2.
	 */
	public static Vec3 launchVelocity(Vec3 from, Vec3 target) {
		Vec3 d = target.subtract(from);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		int t = (int) Math.max(4, Math.ceil(flat / SPEED));
		return new Vec3(d.x / t, d.y / t + GRAVITY * (t + 1) / 2.0, d.z / t);
	}

	@Override
	protected net.minecraft.core.particles.ParticleOptions getTrailParticle() {
		return null; // v0.16.1: no particle trail -- the ball is its 3D model (WebBombRenderer)
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected float getInertia() {
		return 1.0f;
	}

	@Override
	public boolean isNoGravity() {
		return true; // its own gravity, below (both sides, so the client's prediction matches)
	}

	@Override
	public void tick() {
		setDeltaMovement(getDeltaMovement().add(0.0, -GRAVITY, 0.0));
		super.tick();
		if (level().isClientSide() || !isAlive()) {
			return;
		}
		life++;
		// past the target point (it came down to it without touching anything) or out of time: burst there
		if (life > MAX_LIFE || (target != null && getDeltaMovement().y < 0 && position().y <= target.y
				&& position().distanceToSqr(target) < 4.0)) {
			burst(target != null && life <= MAX_LIFE ? target : position());
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity)
				&& (!(entity instanceof LivingEntity le) || com.projecthero.mod.combat.HeroTargets.canHarm(getOwner(), le));
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (!level().isClientSide()) {
			Entity e = hit.getEntity();
			burst(e.position().add(0, e.getBbHeight() * 0.5, 0));
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (!level().isClientSide()) {
			burst(hit.getLocation());
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() != HitResult.Type.MISS) {
			super.onHit(hit);
		}
	}

	private void burst(Vec3 at) {
		if (burst) {
			return;
		}
		burst = true;
		if (getOwner() instanceof ServerPlayer owner && owner.isAlive()) {
			SpiderMoves.webBombBurst(owner, at);
		}
		discard();
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}

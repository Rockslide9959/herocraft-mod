package com.projecthero.mod.spider.entity;

import com.projecthero.mod.spider.SpiderMoves;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Web Cocoon (Shift+V) as a thrown move -- a web ball (the Web Shot's 3D glob, drawn by
 * {@code client.spider.WebShotRenderer}) that homes in on the target picked when it was thrown and wraps it where it
 * lands ({@link SpiderMoves#cocoonLanded}). It only ever hits that target and slips past walls (the target was already
 * validated -- weakened, not a boss -- when it was thrown, so a wall in between must not waste the cast).
 */
public class WebCocoonBallEntity extends SpiderWebShotEntity {
	public static final double BALL_SPEED = 1.8;
	private static final int BALL_LIFE = 40;
	private int targetId = -1;

	public WebCocoonBallEntity(EntityType<? extends WebCocoonBallEntity> type, Level level) {
		super(type, level);
	}

	public WebCocoonBallEntity(Level level, ServerPlayer owner, LivingEntity target, Vec3 from) {
		super(SpiderEntityTypes.WEB_COCOON, level, owner, aim(from, target).scale(BALL_SPEED));
		this.targetId = target.getId();
		setPos(from.x, from.y, from.z);
	}

	private static Vec3 aim(Vec3 from, LivingEntity target) {
		Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from);
		return d.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : d.normalize();
	}

	@Override
	protected double speed() {
		return BALL_SPEED;
	}

	@Override
	protected int maxLife() {
		return BALL_LIFE;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			Entity e = level().getEntity(targetId);
			if (!(e instanceof LivingEntity target) || !target.isAlive()) {
				discard();
				return;
			}
			Vec3 centre = target.position().add(0, target.getBbHeight() * 0.5, 0);
			if (centre.distanceTo(position()) <= BALL_SPEED + target.getBbWidth() * 0.5) {
				land(target); // close enough to reach it this tick
				return;
			}
			setDeltaMovement(aim(position(), target).scale(BALL_SPEED));
		}
		super.tick();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return entity.getId() == targetId && super.canHitEntity(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (!level().isClientSide() && hit.getEntity() instanceof LivingEntity target && target.getId() == targetId) {
			land(target);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		// homes through: a block in the way does not stop it
	}

	private void land(LivingEntity target) {
		if (getOwner() instanceof ServerPlayer owner) {
			SpiderMoves.cocoonLanded(owner, target);
		}
		splat(target.position().add(0, target.getBbHeight() * 0.5, 0));
		discard();
	}
}

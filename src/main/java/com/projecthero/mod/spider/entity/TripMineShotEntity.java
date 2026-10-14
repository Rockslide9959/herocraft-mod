package com.projecthero.mod.spider.entity;

import com.projecthero.mod.spider.SpiderTripMines;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 (user): the Suit v2's Trip Mine is shot out like a Web Shot -- a glob that flies through creatures and turns
 * into a mine on the first surface it hits (wall, floor or ceiling), its beam pointing out of that face.
 */
public class TripMineShotEntity extends SpiderWebShotEntity {
	public static final double SHOT_SPEED = 2.0;
	private static final int SHOT_LIFE = 16; // ~32 blocks

	public TripMineShotEntity(EntityType<? extends TripMineShotEntity> type, Level level) {
		super(type, level);
	}

	public TripMineShotEntity(Level level, LivingEntity owner, Vec3 movement) {
		super(SpiderEntityTypes.TRIP_MINE_SHOT, level, owner, movement);
	}

	@Override
	protected double speed() {
		return SHOT_SPEED;
	}

	@Override
	protected int maxLife() {
		return SHOT_LIFE;
	}

	/** It is aimed at a surface, not a creature: it passes straight through them. */
	@Override
	protected boolean canHitEntity(Entity entity) {
		return false;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (!level().isClientSide() && getOwner() instanceof ServerPlayer shooter) {
			SpiderTripMines.placeAt(shooter, hit.getLocation(), hit.getDirection());
		}
		if (isAlive()) {
			discard();
		}
	}
}

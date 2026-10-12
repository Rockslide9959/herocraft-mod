package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.contagion.SokoviaFinale;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;

/**
 * v0.16.1: the base of the Sokovia Protocol's red marker beam, on the ground under the sky-citadel (the client draws the
 * beam up to the sky). Right-click it once for the <b>Stark Lift</b>: a blue beam carries you up to the citadel's edge
 * ({@link SokoviaFinale#starkLift}).
 */
public class CitadelMarkerEntity extends Entity {
	public CitadelMarkerEntity(EntityType<? extends CitadelMarkerEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			SokoviaFinale.starkLift(sp);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 512 * 512;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

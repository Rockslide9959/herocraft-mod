package com.projecthero.mod.silversurfer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: G Board Strike -- the Surfer's board thrown as a spinning boomerang. Server-driven: it flies to the first
 * target on his aim (or {@link SilverSurferConfig#BOARD_RANGE} out), chains through up to
 * {@link SilverSurferConfig#BOARD_CHAIN} creatures within {@link SilverSurferConfig#BOARD_CHAIN_RANGE} of each other, then
 * comes back to his hand. Passes through blocks; never saved (a reloaded board just vanishes).
 */
public class ThrownSurfboardEntity extends Entity {
	private UUID owner;
	private LivingEntity current;
	private Vec3 outPoint;
	private boolean returning;
	private int life;
	private final Set<Integer> hit = new HashSet<>();

	public ThrownSurfboardEntity(EntityType<? extends ThrownSurfboardEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public void launch(ServerPlayer thrower, LivingEntity first, Vec3 farPoint) {
		this.owner = thrower.getUUID();
		this.current = first;
		this.outPoint = farPoint;
		Vec3 start = thrower.getEyePosition().add(thrower.getLookAngle().scale(0.8)).add(0, -0.4, 0);
		moveTo(start.x, start.y, start.z, thrower.getYRot(), 0f);
	}

	public int hits() {
		return hit.size();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer thrower = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (thrower == null || thrower.level() != level || !thrower.isAlive() || ++life > SilverSurferConfig.BOARD_MAX_LIFE) {
			discard();
			return;
		}
		Vec3 to;
		if (returning) {
			to = thrower.position().add(0, 1.1, 0);
		} else if (current != null) {
			if (!current.isAlive()) {
				current = next(thrower, position());
				if (current == null) {
					returning = true;
				}
				return;
			}
			to = current.position().add(0, current.getBbHeight() * 0.55, 0);
		} else {
			to = outPoint;
		}
		Vec3 d = to.subtract(position());
		double dist = d.length();
		double step = SilverSurferConfig.BOARD_SPEED;
		if (dist <= step + 0.6) {
			setPos(to.x, to.y, to.z);
			arrive(thrower, level);
		} else {
			Vec3 v = d.scale(step / dist);
			setDeltaMovement(v);
			setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
			setYRot((float) (Math.atan2(-v.x, v.z) * 180.0 / Math.PI));
		}
		if (tickCount % 2 == 0) {
			level.sendParticles(SilverSurfer.SILVER, getX(), getY(), getZ(), 2, 0.3, 0.05, 0.3, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.2, 0.05, 0.2, 0.0);
		}
		if (tickCount % 6 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
		}
	}

	private void arrive(ServerPlayer thrower, ServerLevel level) {
		if (returning) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
			discard();
			return;
		}
		if (current != null) {
			hit.add(current.getId());
			SilverSurferCombat.strike(thrower, current, position().subtract(getDeltaMovement()), SilverSurferConfig.BOARD_DAMAGE, 0.6, 0.2);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.9f);
			current = hit.size() >= SilverSurferConfig.BOARD_CHAIN ? null : next(thrower, position());
			if (current == null) {
				returning = true;
			}
		} else {
			returning = true;
		}
	}

	private LivingEntity next(ServerPlayer thrower, Vec3 from) {
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : SilverSurferCombat.within(thrower, from, SilverSurferConfig.BOARD_CHAIN_RANGE)) {
			if (hit.contains(e.getId())) {
				continue;
			}
			double sq = e.distanceToSqr(from);
			if (sq < bestSq) {
				best = e;
				bestSq = sq;
			}
		}
		return best;
	}
}

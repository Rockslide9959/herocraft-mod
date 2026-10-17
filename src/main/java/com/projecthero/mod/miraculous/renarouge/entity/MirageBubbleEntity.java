package com.projecthero.mod.miraculous.renarouge.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.renarouge.RenaRougeCombat;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeMoves;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, Shift+Z: the Mirage's orange bubble. It floats up and out from the end of the Flute, swelling, for
 * {@link RenaRougeConfig#MIRAGE_BUBBLE_TICKS}, then pops ({@link RenaRougeMoves#miragePop}) into the big illusion.
 */
public class MirageBubbleEntity extends Entity {
	private @Nullable UUID owner;
	private Vec3 drift = Vec3.ZERO;
	private int age;

	public MirageBubbleEntity(EntityType<? extends MirageBubbleEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	public void launch(ServerPlayer p, Vec3 from, Vec3 look) {
		owner = p.getUUID();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		drift = flat.scale(0.12).add(0, 0.11, 0);
		setPos(from.x, from.y, from.z);
	}

	public @Nullable UUID ownerId() {
		return owner;
	}

	public int age() {
		return age;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		if (level().isClientSide) {
			return;
		}
		ServerPlayer p = owner != null && level() instanceof ServerLevel sl && sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
		if (p == null) {
			discard();
			return;
		}
		Vec3 n = position().add(drift).add(0, 0.02 * Math.sin(age * 0.6), 0);
		setPos(n.x, n.y, n.z);
		if (level() instanceof ServerLevel sl && age % 2 == 0) {
			sl.sendParticles(RenaRougeCombat.ORANGE, getX(), getY(), getZ(), 1, 0.15, 0.15, 0.15, 0.0);
		}
		if (age >= RenaRougeConfig.MIRAGE_BUBBLE_TICKS) {
			RenaRougeMoves.miragePop(p, position());
			discard();
		}
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

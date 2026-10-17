package com.projecthero.mod.miraculous.hawkmoth.entity;

import java.util.UUID;

import com.projecthero.mod.miraculous.hawkmoth.HawkMothAkumas;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: one <b>akuma</b> -- a small dark-purple butterfly flying from Hawk Moth's hand to the creature it will corrupt
 * (the client draws it flapping: {@code AkumaRenderer}). Both sides fly it the same way toward the synced target (so it
 * looks smooth); only the server decides the arrival, which hands over to {@link HawkMothAkumas#arrive}. Never saved.
 *
 * <ul>
 *   <li>{@link #SEND}: plain G -- the mob fights for him for {@link HawkMothConfig#AKUMA_TICKS}.</li>
 *   <li>{@link #RISE}: Shift+Z -- a Champion (a mob) or the offer (a player).</li>
 *   <li>{@link #RECALL}: Shift+G -- an akuma leaving a freed mob and flying home into Hawk Moth (only a look).</li>
 * </ul>
 */
public class AkumaEntity extends Entity {
	public static final byte SEND = 0;
	public static final byte RISE = 1;
	public static final byte RECALL = 2;

	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(AkumaEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(AkumaEntity.class, EntityDataSerializers.BYTE);

	private UUID ownerId;
	private ServerPlayer cachedOwner;
	private int life;
	/** Client: a per-butterfly phase for the wing beat and the wobble. */
	public final float phase;

	public AkumaEntity(EntityType<? extends AkumaEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.phase = (float) (Math.random() * Math.PI * 2);
	}

	/** An akuma from {@code from} to {@code target} ({@link #SEND} / {@link #RISE} / {@link #RECALL}). */
	public static AkumaEntity launch(ServerPlayer owner, Vec3 from, Entity target, byte mode) {
		AkumaEntity e = HawkMothEntityTypes.AKUMA.create(owner.level());
		if (e == null) {
			return null;
		}
		e.ownerId = owner.getUUID();
		e.cachedOwner = owner;
		e.entityData.set(TARGET, target.getId());
		e.entityData.set(MODE, mode);
		e.moveTo(from.x, from.y, from.z);
		Vec3 d = target.getEyePosition().subtract(from);
		e.face(d);
		owner.level().addFreshEntity(e);
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(TARGET, -1);
		builder.define(MODE, SEND);
	}

	public byte mode() {
		return entityData.get(MODE);
	}

	public UUID ownerId() {
		return ownerId;
	}

	public ServerPlayer owner() {
		if (cachedOwner != null && !cachedOwner.isRemoved()) {
			return cachedOwner;
		}
		if (ownerId != null && level() instanceof ServerLevel server && server.getPlayerByUUID(ownerId) instanceof ServerPlayer p) {
			cachedOwner = p;
			return p;
		}
		return null;
	}

	public Entity target() {
		return level().getEntity(entityData.get(TARGET));
	}

	/** Where the akuma is heading: the face of a creature, a player's chest (recall). */
	private Vec3 goal(Entity t) {
		if (mode() == RECALL && t instanceof Player) {
			return t.position().add(0, t.getBbHeight() * 0.62, 0);
		}
		if (t instanceof LivingEntity le) {
			return le.getEyePosition();
		}
		return t.position().add(0, t.getBbHeight() * 0.5, 0);
	}

	@Override
	public void tick() {
		super.tick();
		life++;
		Entity t = target();
		if (t == null || !t.isAlive()) {
			if (!level().isClientSide()) {
				HawkMothAkumas.flightFailed(this);
				discard();
			}
			return;
		}
		Vec3 pos = position();
		if (level().isClientSide() && tickCount % 2 == 0) {
			// a faint dark trail
			level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.3f, 0.05f, 0.42f), 0.6f),
					pos.x, pos.y + 0.1, pos.z, 0, 0, 0);
		}
		Vec3 d = goal(t).subtract(pos);
		double dist = d.length();
		double speed = HawkMothConfig.AKUMA_SPEED * (mode() == RECALL ? 1.3 : 1.0);
		if (!level().isClientSide()) {
			if (dist < speed + 0.35) {
				HawkMothAkumas.arrive(this, t);
				discard();
				return;
			}
			if (life > HawkMothConfig.AKUMA_FLIGHT_LIMIT * (mode() == RISE ? 2 : 1) || owner() == null) {
				HawkMothAkumas.flightFailed(this);
				discard();
				return;
			}
		}
		if (dist < 1.0e-4) {
			return;
		}
		Vec3 dir = d.scale(1.0 / dist);
		// a butterfly's flutter: a little bob and a sideways weave on top of the straight line
		float a = (tickCount + phase * 10f) * 0.55f;
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 wobble = side.scale(0.06 * Mth.sin(a)).add(0, 0.05 * Mth.cos(a * 1.3f), 0);
		Vec3 step = dir.scale(Math.min(speed, dist)).add(dist > 1.5 ? wobble : Vec3.ZERO);
		setPos(pos.x + step.x, pos.y + step.y, pos.z + step.z);
		face(dir);
	}

	private void face(Vec3 v) {
		setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
		setXRot((float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
		yRotO = getYRot();
		xRotO = getXRot();
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 96.0 * 96.0;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

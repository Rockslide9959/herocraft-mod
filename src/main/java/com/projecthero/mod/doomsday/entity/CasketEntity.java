package com.projecthero.mod.doomsday.entity;

import java.util.UUID;

import com.projecthero.mod.doomsday.DoomsdayEvent;
import com.projecthero.mod.doomsday.DoomsdayFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: <b>the Calatonian casket</b> -- the burial vault the people of Calaton sealed Doomsday in, cables, shackles and
 * all. In the event it either crashes down out of the sky ({@link #FALLING}, natural impact) or cracks open where the
 * Calatonian Casket block stood. Then it lies there ({@link #LANDED}) while something inside <b>pounds</b> on the lid --
 * every blow a bang, a shake and a new dent -- until he punches his way out ({@link #OPEN}) and the event takes over.
 */
public class CasketEntity extends Entity {
	public static final byte FALLING = 0, LANDED = 1, OPEN = 2;
	public static final int FALL_TICKS = 50;
	private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(CasketEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_DENTS = SynchedEntityData.defineId(CasketEntity.class, EntityDataSerializers.INT);

	private UUID eventId;
	private Vec3 landing = Vec3.ZERO;
	private Vec3 fallFrom = Vec3.ZERO;
	private int timer;
	private int openAfter = 160;

	public CasketEntity(EntityType<? extends CasketEntity> type, Level level) {
		super(type, level);
	}

	/** A casket crashing down onto {@code landing} (natural impact / the command). */
	public static CasketEntity falling(ServerLevel level, UUID eventId, Vec3 landing, int openAfter) {
		CasketEntity c = DoomsdayEntityTypes.CASKET.create(level);
		c.eventId = eventId;
		c.landing = landing;
		c.fallFrom = landing.add(-22, 64, -12);
		c.openAfter = openAfter;
		c.setPos(c.fallFrom.x, c.fallFrom.y, c.fallFrom.z);
		c.setYRot(-30f);
		c.entityData.set(DATA_STATE, FALLING);
		level.addFreshEntity(c);
		return c;
	}

	/** A casket already on the ground at {@code at} (the casket block was opened). */
	public static CasketEntity landed(ServerLevel level, UUID eventId, Vec3 at, float yaw, int openAfter) {
		CasketEntity c = DoomsdayEntityTypes.CASKET.create(level);
		c.eventId = eventId;
		c.landing = at;
		c.openAfter = openAfter;
		c.setPos(at.x, at.y, at.z);
		c.setYRot(yaw);
		c.entityData.set(DATA_STATE, LANDED);
		level.addFreshEntity(c);
		return c;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_STATE, FALLING);
		builder.define(DATA_DENTS, 0);
	}

	public byte state() {
		return entityData.get(DATA_STATE);
	}

	/** How many times he has hit the lid (the renderer bulges it). */
	public int dents() {
		return entityData.get(DATA_DENTS);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		timer++;
		DoomsdayEvent ev = DoomsdayEvent.find(level, eventId);
		if (ev == null && tickCount > 40) {
			discard();
			return;
		}
		switch (state()) {
			case FALLING -> tickFall(level);
			case LANDED -> tickLanded(level, ev);
			default -> {
				if (timer > 100) {
					level.sendParticles(DoomsdayFx.ASH, getX(), getY() + 0.5, getZ(), 20, 0.8, 0.4, 0.8, 0.02);
					discard();
				}
			}
		}
	}

	private void tickFall(ServerLevel level) {
		double f = Math.min(1.0, timer / (double) FALL_TICKS);
		double ease = f * f;
		Vec3 p = fallFrom.lerp(landing, ease);
		setPos(p.x, p.y, p.z);
		DoomsdayFx.forced(level, ParticleTypes.FLAME, p.x, p.y + 0.5, p.z, 6, 0.4, 0.4, 0.4, 0.05);
		DoomsdayFx.forced(level, ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.5, p.z, 4, 0.5, 0.5, 0.5, 0.02);
		if (timer % 10 == 0) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 4.0f, 0.4f);
		}
		if (f >= 1.0) {
			entityData.set(DATA_STATE, LANDED);
			timer = 0;
			level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0f, 0.4f);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.3f);
			DoomsdayFx.impact(level, p, 6.0);
			DoomsdayFx.forced(level, ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
			DoomsdayFx.shake(level, p, 2.0f, 40, 96);
			DamageSource src = level.damageSources().fallingBlock(this);
			for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(6), DoomsdayEntity::canTarget)) {
				t.hurt(src, 8f);
				Vec3 out = new Vec3(t.getX() - p.x, 0, t.getZ() - p.z).normalize();
				t.setDeltaMovement(out.x * 1.2, 0.6, out.z * 1.2);
				t.hurtMarked = true;
			}
		}
	}

	private void tickLanded(ServerLevel level, DoomsdayEvent ev) {
		// he pounds on the lid: slow at first, faster toward the end
		int left = openAfter - timer;
		int every = left > 80 ? 30 : left > 40 ? 18 : 9;
		if (timer % every == 0 && timer > 10) {
			entityData.set(DATA_DENTS, dents() + 1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.4f + level.random.nextFloat() * 0.15f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 3.0f, 0.5f);
			DoomsdayFx.forced(level, DoomsdayFx.STONE, getX(), getY() + 1.0, getZ(), 12, 0.6, 0.2, 0.6, 0.15);
			DoomsdayFx.shake(level, position(), 0.5f + 0.02f * dents(), 8, 48);
		}
		if (timer % 8 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.1, getZ(), 2, 0.6, 0.1, 0.9, 0.01);
		}
		if (timer >= openAfter) {
			burst(level, ev);
		}
	}

	/** He tears out: the lid flies, the event spawns Doomsday. */
	public void burst(ServerLevel level, DoomsdayEvent ev) {
		if (state() == OPEN) {
			return;
		}
		entityData.set(DATA_STATE, OPEN);
		timer = 0;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 4.0f, 0.4f);
		DoomsdayFx.impact(level, position(), 3.0);
		DoomsdayFx.forced(level, DoomsdayFx.CABLE, getX(), getY() + 1.0, getZ(), 50, 1.0, 0.6, 1.0, 0.3);
		DoomsdayFx.shake(level, position(), 1.6f, 30, 96);
		if (ev != null) {
			ev.onCasketBurst(level, this);
		}
	}

	public void debugBurst(ServerLevel level) {
		if (state() == FALLING) {
			timer = FALL_TICKS;
			tickFall(level);
		}
		burst(level, DoomsdayEvent.find(level, eventId));
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 256 * 256;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		eventId = tag.hasUUID("Event") ? tag.getUUID("Event") : null;
		landing = new Vec3(tag.getDouble("LX"), tag.getDouble("LY"), tag.getDouble("LZ"));
		openAfter = tag.getInt("OpenAfter");
		byte s = tag.getByte("State");
		// a casket saved mid-fall lands at once
		entityData.set(DATA_STATE, s == FALLING ? LANDED : s);
		if (s == FALLING) {
			setPos(landing.x, landing.y, landing.z);
		}
		timer = tag.getInt("Timer");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (eventId != null) {
			tag.putUUID("Event", eventId);
		}
		tag.putDouble("LX", landing.x);
		tag.putDouble("LY", landing.y);
		tag.putDouble("LZ", landing.z);
		tag.putInt("OpenAfter", openAfter);
		tag.putByte("State", state());
		tag.putInt("Timer", timer);
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	/** For the renderer: entities are never pushed into it. */
	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean canCollideWith(Entity other) {
		return false;
	}
}

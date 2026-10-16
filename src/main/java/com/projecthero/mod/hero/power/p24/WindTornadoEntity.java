package com.projecthero.mod.hero.power.p24;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Manipulation's tornadoes (v0.16.5 rework), drawn by {@code WindTornadoRenderer} as a funnel of stacked,
 * counter-rotating translucent rings:
 * <ul>
 * <li>{@link #TRAVEL} (Z): a tornado that travels along the aim, hugging the ground. It drags in every enemy within
 * {@link WindHandlers#TORNADO_PULL_RADIUS}, whirls each one round its funnel for {@link WindHandlers#TORNADO_SPIN_TICKS}
 * at {@link WindHandlers#TORNADO_DPS} a second, then flings it out. It blows itself out after {@link #TRAVEL_LIFE}.</li>
 * <li>{@link #RIDE} (C): a tornado under the hero, who stands on it. It goes where the rider looks and bowls through
 * whatever it meets for {@link WindHandlers#RIDE_BOWL_DAMAGE}. It lasts while the toggle is on and Gust holds out.</li>
 * </ul>
 * Never saved, never collides, cannot be hurt.
 */
public class WindTornadoEntity extends Entity {
	public static final int TRAVEL = 0;
	public static final int RIDE = 1;
	/** The travelling tornado blows out after 8 s (so a creature caught early still gets its full 6 s spin). */
	public static final int TRAVEL_LIFE = 8 * 20;
	private static final double TRAVEL_SPEED = 0.2;
	private static final double RIDE_SPEED = 0.55;

	private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(WindTornadoEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(WindTornadoEntity.class, EntityDataSerializers.INT);

	private UUID owner;
	private Vec3 drift = Vec3.ZERO;
	/** TRAVEL: caught creature id -> ticks caught. */
	private final Map<Integer, Integer> spinning = new HashMap<>();
	/** TRAVEL: creatures already flung out (never caught twice). */
	private final Set<Integer> released = new HashSet<>();
	/** RIDE: creature id -> the tick it was last bowled over. */
	private final Map<Integer, Integer> bowled = new HashMap<>();

	public WindTornadoEntity(EntityType<? extends WindTornadoEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public WindTornadoEntity(Level level, ServerPlayer owner, int mode, Vec3 at, Vec3 heading) {
		this(WindEntities.TORNADO, level);
		this.owner = owner.getUUID();
		entityData.set(MODE, mode);
		entityData.set(LIFE, mode == TRAVEL ? TRAVEL_LIFE : 0);
		Vec3 flat = new Vec3(heading.x, 0, heading.z);
		this.drift = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(mode == TRAVEL ? TRAVEL_SPEED : 0.0);
		this.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(LIFE, TRAVEL_LIFE);
		builder.define(MODE, TRAVEL);
	}

	/** TRAVEL: ticks left before it blows itself out (synced, so the renderer can fade it). RIDE: 0. */
	public int life() {
		return entityData.get(LIFE);
	}

	public int mode() {
		return entityData.get(MODE);
	}

	public UUID owner() {
		return owner;
	}

	/** TRAVEL: how many creatures it is whirling right now (gametests). */
	public int spinningCount() {
		return spinning.size();
	}

	public boolean isSpinning(Entity e) {
		return spinning.containsKey(e.getId());
	}

	/** Ends the tornado now; a travelling one flings out whatever it still holds. */
	public void dissipate() {
		if (level() instanceof ServerLevel sl) {
			ServerPlayer p = owner == null ? null : (ServerPlayer) sl.getPlayerByUUID(owner);
			for (int id : spinning.keySet().toArray(new Integer[0])) {
				if (sl.getEntity(id) instanceof LivingEntity e) {
					fling(e);
				}
			}
			spinning.clear();
			sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 2, getZ(), 12, 1.0, 1.5, 1.0, 0.05);
			sl.playSound(null, blockPosition(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.8f, 0.7f);
			if (p != null && mode() == RIDE) {
				WindHandlers.rideEnded(p, this);
			}
		}
		ejectPassengers();
		discard();
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer ownerPlayer = owner == null ? null : (ServerPlayer) level.getPlayerByUUID(owner);
		if (ownerPlayer == null || !ownerPlayer.isAlive() || ownerPlayer.level() != level) {
			dissipate();
			return;
		}
		if (mode() == TRAVEL) {
			int life = life() - 1;
			entityData.set(LIFE, life);
			if (life <= 0) {
				dissipate();
				return;
			}
		} else if (getFirstPassenger() != ownerPlayer) {
			dissipate(); // the rider got off
			return;
		}

		// steering: a travelling one keeps its heading; a ridden one follows the rider's look
		if (mode() == RIDE) {
			Vec3 look = ownerPlayer.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			Vec3 desired = flat.lengthSqr() > 1.0e-4 ? flat.normalize().scale(RIDE_SPEED) : Vec3.ZERO;
			drift = drift.scale(0.75).add(desired.scale(0.25));
			ownerPlayer.resetFallDistance();
		}
		int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(getX() + drift.x), Mth.floor(getZ() + drift.z));
		double dy = Mth.clamp(surface - getY(), -0.5, 0.6);
		Vec3 move = new Vec3(drift.x, dy, drift.z);
		setDeltaMovement(move);
		move(MoverType.SELF, move);

		if (mode() == TRAVEL) {
			travelTick(level, ownerPlayer);
		} else {
			rideTick(level, ownerPlayer);
		}

		if (tickCount % 4 == 0) { // an accent only: the funnel itself is the model
			for (int i = 0; i < 2; i++) {
				double h = (i + (tickCount % 8) / 8.0) * 1.2;
				double a = tickCount * 0.45 + i * 1.6;
				double r = 0.5 + h * 0.35;
				level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * r, getY() + h, getZ() + Math.sin(a) * r,
						0, -Math.sin(a), 0.15, Math.cos(a), 0.1);
			}
		}
		if (tickCount % 30 == 0) {
			level.playSound(null, blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
	}

	/** Z: pull in, whirl for 6 s at 4 a second, fling out. */
	private void travelTick(ServerLevel level, ServerPlayer p) {
		Vec3 axis = position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, axis.add(0, 2.0, 0), WindHandlers.TORNADO_PULL_RADIUS)) {
			int id = e.getId();
			if (e == p || released.contains(id) || spinning.containsKey(id)) {
				continue;
			}
			if (!WindHandlers.movable(e)) {
				continue;
			}
			double d = Math.hypot(e.getX() - axis.x, e.getZ() - axis.z);
			if (d < 2.4) {
				spinning.put(id, 0);
			} else {
				// the inflow: drag it toward the funnel, curling round
				Vec3 in = new Vec3(axis.x - e.getX(), 0, axis.z - e.getZ()).normalize();
				Vec3 spin = new Vec3(-in.z, 0, in.x);
				Vec3 v = in.scale(Math.min(0.55, 0.18 + 1.2 / d)).add(spin.scale(0.2));
				e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(v.x, 0.05, v.z));
				e.hurtMarked = true;
			}
		}
		for (Integer id : spinning.keySet().toArray(new Integer[0])) {
			Entity en = level.getEntity(id);
			if (!(en instanceof LivingEntity e) || !e.isAlive()) {
				spinning.remove(id);
				continue;
			}
			int t = spinning.get(id) + 1;
			spinning.put(id, t);
			if (t > WindHandlers.TORNADO_SPIN_TICKS) {
				spinning.remove(id);
				fling(e);
				continue;
			}
			// round and round, climbing the funnel
			double ang = t * 0.42 + id * 1.7;
			double h = Math.min(3.2, 0.6 + t * 0.05) + Math.sin(t * 0.2 + id) * 0.25;
			double r = 1.0 + h * 0.3;
			Vec3 slot = axis.add(Math.cos(ang) * r, h, Math.sin(ang) * r);
			Vec3 to = slot.subtract(e.position());
			e.setDeltaMovement(to.scale(0.6));
			e.resetFallDistance();
			e.hurtMarked = true;
			if (e instanceof Mob m) {
				m.getNavigation().stop();
			}
			if (t % 20 == 0) {
				AbilityHelpers.hurtBurst(p, e, WindHandlers.TORNADO_DPS);
			}
		}
	}

	/** Out of the funnel and away, high and hard. */
	private void fling(LivingEntity e) {
		released.add(e.getId());
		Vec3 out = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
		out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
		e.setDeltaMovement(out.x * 1.5, 0.9, out.z * 1.5);
		e.hurtMarked = true;
		e.hasImpulse = true;
	}

	/** C: whatever the ridden funnel runs into is bowled over for 6 (once a second each). */
	private void rideTick(ServerLevel level, ServerPlayer p) {
		Vec3 axis = position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, axis.add(0, 1.0, 0), WindHandlers.RIDE_BOWL_RADIUS)) {
			if (e == p) {
				continue;
			}
			Integer last = bowled.get(e.getId());
			if (last != null && tickCount - last < 20) {
				continue;
			}
			bowled.put(e.getId(), tickCount);
			WindHandlers.bowl(p, e, axis, drift);
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dims, float scale) {
		return new Vec3(0, 3.25, 0); // the rider stands on the top of the funnel
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (passenger instanceof ServerPlayer sp) {
			sp.resetFallDistance();
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

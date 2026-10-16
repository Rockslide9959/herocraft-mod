package com.projecthero.mod.ghostrider.entity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.GhostRiderAbilities;
import com.projecthero.mod.ghostrider.GhostRiderCombat;
import com.projecthero.mod.ghostrider.GhostRiderConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5: <b>the Hell Cycle</b> -- Ghost Rider's flaming motorbike, summoned under him with X and sent back with X.
 * Ridden like a horse (the rider's client simulates it): 15 blocks a second, 30 while the rider sprints (v0.16.5 rework),
 * a 4-block step, it <b>slides along walls</b> it meets at an angle instead of stopping dead, <b>drives up walls</b>
 * (hold forward against one), <b>skims over lava</b>, and leaves a <b>fire trail</b> that burns enemies standing in it.
 * X while riding is the Cycle Jump (a long leap that lands in a ring of fire); Shift+X (Shift doesn't dismount it)
 * sends it back. Never saved (it goes back to hell with
 * the rider), can't be hurt except by the void / {@code /kill}.
 */
public class HellCycleEntity extends Mob {
	private record TrailPoint(Vec3 pos, long at) {
	}

	private UUID owner;
	private int idle;
	private boolean leaping;
	private int leapAge;
	private final Deque<TrailPoint> trail = new ArrayDeque<>();
	/** v0.16.5: the real fire it has left behind, and when each patch goes out. */
	private final Deque<TrailPoint> fires = new ArrayDeque<>();
	/** Client: wheel spin angle (radians), advanced by the renderer from the ground speed. */
	public float wheelSpin;
	public float wheelSpinO;

	public HellCycleEntity(EntityType<? extends HellCycleEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 100.0)
				.add(Attributes.MOVEMENT_SPEED, GhostRiderConfig.CYCLE_SPEED)
				.add(Attributes.STEP_HEIGHT, GhostRiderConfig.CYCLE_STEP)
				.add(Attributes.SAFE_FALL_DISTANCE, 1000.0);
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	public UUID owner() {
		return owner;
	}

	/** Ticks without a rider (the owner's tick sends an abandoned bike back). */
	public int idleTicks() {
		return idle;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		float forward = player.zza;
		if (forward < 0f) {
			forward *= 0.4f; // reverses slowly
		}
		return new Vec3(player.xxa * 0.35, 0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		// the rider's sprint reaches both sides (the client simulates the bike, the server checks its moves)
		return (float) (player.isSprinting() ? GhostRiderConfig.cycleSpeedFor(GhostRiderConfig.CYCLE_SPRINT_BPS)
				: getAttributeValue(Attributes.MOVEMENT_SPEED));
	}

	/** The rider may sprint on it (vanilla stops a passenger sprinting unless the vehicle can). */
	@Override
	public boolean canSprint() {
		return true;
	}

	/**
	 * Slides instead of sticking: vanilla zeroes only the blocked axis of the velocity, so a bike meeting a wall at an
	 * angle crawls along it at a fraction of its speed. While ridden, the part that still runs along the wall keeps
	 * most of the speed the bike had (head-on, nothing runs along it, and the wall climb takes over).
	 */
	@Override
	public void move(net.minecraft.world.entity.MoverType type, Vec3 delta) {
		double before = getDeltaMovement().horizontalDistance();
		super.move(type, delta);
		if (type != net.minecraft.world.entity.MoverType.SELF || !horizontalCollision || getControllingPassenger() == null || before < 0.1) {
			return;
		}
		Vec3 v = getDeltaMovement();
		double along = v.horizontalDistance();
		if (along > Math.max(0.03, before * 0.25) && along < before) {
			double k = Math.min(before * 0.9, Math.max(along, before * 0.75)) / along;
			setDeltaMovement(v.x * k, v.y, v.z * k);
		}
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		// steers where the rider looks, like a horse
		setRot(player.getYRot(), 0f);
		yRotO = yBodyRot = yHeadRot = getYRot();
		Vec3 v = getDeltaMovement();
		// up walls: hold forward against one (taller than the 4-block step) and the bike climbs it
		if (horizontalCollision && input.z > 0.1 && v.horizontalDistance() < 0.2) { // head-on (a glancing wall is slid along)
			setDeltaMovement(v.x, Math.max(v.y, 0.32), v.z);
			fallDistance = 0f;
		}
		// skims over lava: buoyed to the surface instead of sinking
		if (isInLava()) {
			setDeltaMovement(v.x * 1.3, Math.max(v.y, 0.12), v.z * 1.3);
		}
	}

	@Override
	public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState fluid) {
		return fluid.is(net.minecraft.tags.FluidTags.LAVA);
	}

	@Override
	public boolean isSensitiveToWater() {
		return false;
	}

	/**
	 * Cycle Jump (X while riding): the rider's client simulates the bike, so the leap is sent to it alone. v0.16.5 fix
	 * (user: "it just looks like the player gets stuck on the floor"): it used to be set on the server bike too, with
	 * hurtMarked -- but the server zeroes a player-ridden vehicle's velocity every tick, and the entity tracker then sent
	 * that zero straight back to the rider, cancelling the jump a tick after it began. The server bike's velocity is
	 * never touched now, so the tracker has nothing to send.
	 */
	public void leap(Vec3 velocity, ServerPlayer rider) {
		leaping = true;
		leapAge = 0;
		fallDistance = 0f;
		rider.connection.send(new ClientboundSetEntityMotionPacket(getId(), velocity));
	}

	/** In a Cycle Jump (gametests: the leap itself only ever reaches the rider's client). */
	public boolean leaping() {
		return leaping;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			// the wheels roll with the ground speed (radius 5 px)
			wheelSpinO = wheelSpin;
			double dx = getX() - xo;
			double dz = getZ() - zo;
			Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
			double dist = Math.sqrt(dx * dx + dz * dz);
			double sign = dx * fwd.x + dz * fwd.z >= 0 ? 1.0 : -1.0;
			wheelSpin -= (float) (sign * dist / (5.0 / 16.0));
			return;
		}
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		boolean ridden = getFirstPassenger() != null;
		idle = ridden ? 0 : idle + 1;
		if (!ridden && idle > 400) {
			discard(); // nobody came back for it
			return;
		}
		long now = server.getGameTime();
		Vec3 v = getDeltaMovement();
		double speed = v.horizontalDistance();
		if (leaping) {
			leapAge++;
			if (leapAge % 2 == 0) {
				server.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.4, getZ(), 6, 0.3, 0.2, 0.3, 0.02);
			}
			if (leapAge > 4 && onGround()) {
				leaping = false;
				if (getFirstPassenger() instanceof ServerPlayer rider && GhostRider.transformed(rider)) {
					GhostRiderAbilities.cycleLanded(rider, position());
				}
			} else if (leapAge > 100) {
				leaping = false;
			}
		}
		// the burning tyre tracks
		if (ridden && speed > 0.08 && tickCount % 2 == 0) {
			trail.addLast(new TrailPoint(position(), now));
			// v0.16.5 (user): "leave a trail of fire wherever it rides" -- real fire just behind the back wheel
			if (getFirstPassenger() instanceof ServerPlayer rider) {
				lightTrail(server, rider, position().subtract(v.normalize().scale(1.1)), now);
			}
		}
		while (!fires.isEmpty() && now >= fires.peekFirst().at()) {
			net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(fires.pollFirst().pos());
			if (server.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FIRE)) {
				server.removeBlock(p, false);
			}
		}
		while (!trail.isEmpty() && now - trail.peekFirst().at() > GhostRiderConfig.TRAIL_LIFE) {
			trail.pollFirst();
		}
		if (tickCount % 3 == 0) {
			for (TrailPoint t : trail) {
				server.sendParticles(ParticleTypes.FLAME, t.pos().x, t.pos().y + 0.1, t.pos().z, 1, 0.15, 0.02, 0.15, 0.005);
			}
		}
		if (ridden && speed > 0.15) {
			Vec3 back = position().subtract(v.normalize().scale(0.9));
			server.sendParticles(ParticleTypes.FLAME, back.x, getY() + 0.35, back.z, 3, 0.1, 0.1, 0.1, 0.01);
			server.sendParticles(ParticleTypes.SMOKE, back.x, getY() + 0.5, back.z, 1, 0.05, 0.05, 0.05, 0.01);
		}
		if (tickCount % 20 == 0 && !trail.isEmpty() && getFirstPassenger() instanceof ServerPlayer rider) {
			burnTrail(server, rider);
		}
	}

	/** A patch of fire at {@code at} if it can burn there (air over a solid floor, not in water), going out after a while. */
	private void lightTrail(ServerLevel server, ServerPlayer rider, Vec3 at, long now) {
		net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(at.x, getY() + 0.2, at.z);
		if (!rider.mayBuild() || !server.mayInteract(rider, p) || !server.getBlockState(p).isAir() || isInWater()) {
			return;
		}
		if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(server, p, net.minecraft.core.Direction.UP)) {
			server.setBlock(p, net.minecraft.world.level.block.BaseFireBlock.getState(server, p), 11);
			fires.addLast(new TrailPoint(Vec3.atCenterOf(p), now + GhostRiderConfig.TRAIL_FIRE_TICKS));
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		// the bike leaves: its last patches go out with it
		if (level() instanceof ServerLevel server) {
			for (TrailPoint t : fires) {
				net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(t.pos());
				if (server.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FIRE)) {
					server.removeBlock(p, false);
				}
			}
			fires.clear();
		}
		super.remove(reason);
	}

	private void burnTrail(ServerLevel server, ServerPlayer rider) {
		Set<Integer> hit = new HashSet<>();
		for (TrailPoint t : trail) {
			AABB box = new AABB(t.pos(), t.pos()).inflate(0.8, 1.0, 0.8);
			for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, box,
					e -> e != rider && e != this && GhostRiderCombat.isHostile(rider, e))) {
				if (hit.add(e.getId())) {
					GhostRiderCombat.strike(rider, e, t.pos(), GhostRiderConfig.TRAIL_DAMAGE, 0.0, 0.0, 3);
				}
			}
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
		// vehicle-local, but an override is not rotated for us (the v0.15.21 Hulk-seat lesson)
		return new Vec3(0, 0.62, -0.15).yRot(-getYRot() * net.minecraft.util.Mth.DEG_TO_RAD);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}
}

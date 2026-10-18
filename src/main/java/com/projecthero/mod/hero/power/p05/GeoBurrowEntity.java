package com.projecthero.mod.hero.power.p05;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Geokinesis hold X -- <b>Burrow</b>: the hero rides this (invisible) carrier down into the ground and along under
 * it. It sits on the surface, so what you see is a mound of heaving, crumbling earth running along the ground
 * ({@code GeoFormRenderer}); the rider is held {@link #DEPTH} blocks below it, sinking there over the first
 * {@link #SINK_TICKS}. It never changes a block. It stops after {@link GeokinesisHandlers#BURROW_RANGE} blocks, at bedrock or
 * any other unbreakable block, at water or a drop it can't follow, or when the rider gets off -- and the hero bursts out
 * ({@link GeokinesisHandlers#burstOut}).
 */
public class GeoBurrowEntity extends GeoFormEntity {
	/** Ticks the hero takes to sink into the ground before travelling. */
	public static final int SINK_TICKS = 8;
	/** How far below the surface the rider's feet go (head and all under the ground). */
	public static final double DEPTH = 2.4;
	/** Blocks a tick along the ground. */
	public static final double SPEED = 0.75;
	/** A hard cap on the ride (never reached at {@link #SPEED} over the range). */
	public static final int MAX_TICKS = SINK_TICKS + 40;

	private Vec3 dir = new Vec3(0, 0, 1);
	private double travelled;

	public GeoBurrowEntity(EntityType<? extends GeoFormEntity> type, Level level) {
		super(type, level);
	}

	public static GeoBurrowEntity start(ServerLevel level, ServerPlayer rider, BlockState block, Vec3 flatDir) {
		GeoBurrowEntity b = init(new GeoBurrowEntity(GeoEntityTypes.GEO_BURROW, level), BURROW, rider, rider.position(), block, 1.0f,
				MAX_TICKS + 20);
		b.dir = flatDir.normalize();
		b.setYRot((float) (Mth.atan2(-b.dir.x, b.dir.z) * (180.0 / Math.PI)));
		return b;
	}

	/** 0..1: how far the rider has sunk (both sides, from the entity's age). */
	public float sunk(float partial) {
		float x = Mth.clamp((tickCount + partial) / SINK_TICKS, 0, 1);
		return x * x * (3 - 2 * x);
	}

	public boolean travelling() {
		return tickCount > SINK_TICKS;
	}

	public double travelled() {
		return travelled;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		return new Vec3(0, -DEPTH * sunk(0), 0);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty() && passenger instanceof Player;
	}

	@Override
	public void tick() {
		baseTick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		ServerPlayer owner = ownerPlayer(server);
		Entity rider = getFirstPassenger();
		if (owner == null || !owner.isAlive() || owner.isRemoved() || rider != owner || !GeokinesisHandlers.activeGeo(owner)) {
			if (owner != null && !owner.isRemoved()) {
				GeokinesisHandlers.burstOut(owner, this, false); // got off / lost the power: just put back on the surface
			} else {
				discard();
			}
			return;
		}
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, block());
		if (tickCount <= SINK_TICKS) {
			// sinking in: the ground crumbles in round you
			server.sendParticles(dust, getX(), getY() + 0.1, getZ(), 6, 0.5, 0.1, 0.5, 0.08);
			if (tickCount % 3 == 1) {
				server.playSound(null, getX(), getY(), getZ(), block().getSoundType().getBreakSound(), SoundSource.PLAYERS, 1.0f, 0.6f);
			}
			return;
		}
		if (tickCount > MAX_TICKS || travelled >= GeokinesisHandlers.BURROW_RANGE - 1.0e-3) {
			GeokinesisHandlers.burstOut(owner, this, true);
			return;
		}
		Vec3 next = position().add(dir.scale(Math.min(SPEED, GeokinesisHandlers.BURROW_RANGE - travelled)));
		BlockPos cell = GeokinesisHandlers.surfaceCell(server, next.x, getY(), next.z);
		if (cell == null || Math.abs(cell.getY() - getY()) > 2.01 || !passable(server, next.x, cell.getY() - DEPTH, next.z)) {
			GeokinesisHandlers.burstOut(owner, this, true);
			return;
		}
		setPos(next.x, cell.getY(), next.z);
		travelled += SPEED;
		BlockState ground = server.getBlockState(cell.below());
		BlockParticleOption trail = new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? block() : ground);
		server.sendParticles(trail, getX(), getY() + 0.15, getZ(), 5, 0.35, 0.1, 0.35, 0.1);
		if (tickCount % 4 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 0.9f, 0.5f);
		}
	}

	/**
	 * The rider's body round {@code feetY} may pass (a block's slack either way for the player's own seat offset): nothing
	 * unbreakable (bedrock, barriers...), inside the world and its border.
	 */
	static boolean passable(ServerLevel level, double x, double feetY, double z) {
		for (int dy = -1; dy <= 1; dy++) {
			BlockPos bp = BlockPos.containing(x, feetY + 0.3 + dy, z);
			if (!level.isInWorldBounds(bp) || !level.getWorldBorder().isWithinBounds(bp) || !level.hasChunkAt(bp)) {
				return false;
			}
			BlockState s = level.getBlockState(bp);
			if (s.getDestroySpeed(level, bp) < 0) {
				return false;
			}
		}
		return true;
	}
}

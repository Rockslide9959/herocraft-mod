package com.projecthero.mod.omnitrix.entity;

import org.joml.Vector3f;

import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.OmnitrixSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix's pod coming down, built on the Carnage meteor ({@code CrimsonMeteorEntity}): the same slanted
 * three-second fall, the same batched long-range trail (forced particles, one payload per viewer every other tick) and the same
 * shock on impact -- in green and white, with a real crater (it digs a bowl, lines it with scorched stone, magma and fire) and
 * the {@link OmnitrixPodEntity} left sitting at its bottom. Saved like any entity, so a
 * meteor caught mid-fall by a save keeps falling when its chunk loads again.
 */
public class OmnitrixMeteorEntity extends Entity {
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.25f), 1.6f);
	private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.9f, 1.0f, 0.9f), 1.1f);
	public static final int CRATER_RADIUS = 4;

	public OmnitrixMeteorEntity(EntityType<? extends OmnitrixMeteorEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	/** Launches a meteor that lands at {@code impact} about three seconds from now. */
	public static OmnitrixMeteorEntity launch(ServerLevel level, Vec3 impact) {
		OmnitrixMeteorEntity m = OmnitrixEntities.OMNITRIX_METEOR.create(level);
		if (m == null) {
			return null;
		}
		double a = level.random.nextDouble() * Math.PI * 2;
		Vec3 start = impact.add(Math.cos(a) * 40, 70, Math.sin(a) * 40);
		m.moveTo(start.x, start.y, start.z, 0f, 0f);
		m.setDeltaMovement(impact.subtract(start).scale(1.0 / 60.0));
		level.addFreshEntity(m);
		OmnitrixSounds.play(level, impact, OmnitrixSounds.METEOR, 4.0f, 1.0f, SoundSource.HOSTILE);
		level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0f, 0.5f);
		return m;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level() instanceof ServerLevel server) {
			BlockHitResult hit = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
			if (hit.getType() != HitResult.Type.MISS) {
				impact(server, hit.getLocation(), hit.getBlockPos());
				return;
			}
			if (tickCount % 2 == 0) {
				com.projecthero.mod.network.ParticleBatch batch = new com.projecthero.mod.network.ParticleBatch();
				for (Vec3 at : new Vec3[] { from.subtract(v), from }) {
					batch.addForced(ParticleTypes.FLAME, 192, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0.01);
					batch.addForced(GREEN, 192, at.x, at.y, at.z, 8, 0.6, 0.6, 0.6, 0.01);
					batch.addForced(WHITE, 192, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.01);
					batch.addForced(ParticleTypes.LARGE_SMOKE, 192, at.x, at.y, at.z, 1, 0.5, 0.5, 0.5, 0.01);
				}
				for (var p : server.players()) {
					if (p.position().distanceToSqr(from) < 192 * 192) {
						batch.send(server, p);
					}
				}
			}
			if (tickCount % 10 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.5f, 0.6f);
			}
			if (tickCount > 200) {
				discard();
				return;
			}
		}
		setPos(to.x, to.y, to.z);
	}

	private void impact(ServerLevel level, Vec3 at, BlockPos struck) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.7f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(GREEN, at.x, at.y + 0.5, at.z, 140, 2.5, 1.2, 2.5, 0.0);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 30, 1.5, 0.5, 1.5, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(5))) {
			Vec3 out = e.position().subtract(at);
			Vec3 h = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
			e.hurt(level.damageSources().explosion(this, null), 6.0f);
			e.push(h.x * 1.1, 0.6, h.z * 1.1);
			e.hurtMarked = true;
		}
		BlockPos bottom = crater(level, struck);
		OmnitrixPodEntity.place(level, Vec3.atBottomCenterOf(bottom));
		discard();
	}

	/**
	 * Digs a bowl around {@code struck} (radius {@link #CRATER_RADIUS}, never through bedrock or a block entity), lines it with
	 * scorched blackstone, basalt and magma, scatters fire on the rim, and returns the bowl's lowest open block.
	 */
	public static BlockPos crater(ServerLevel level, BlockPos struck) {
		int r = CRATER_RADIUS;
		BlockPos centre = struck;
		int deepest = 0;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > r + 0.3) {
					continue;
				}
				int depth = (int) Math.round(Math.sqrt(Math.max(0, r * r - d * d)) * 0.55);
				int topY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						centre.getX() + dx, centre.getZ() + dz) - 1;
				if (Math.abs(topY - centre.getY()) > 4) {
					topY = centre.getY();
				}
				for (int k = 0; k < depth; k++) {
					BlockPos pos = new BlockPos(centre.getX() + dx, topY - k, centre.getZ() + dz);
					if (diggable(level, pos)) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
				// clear anything loose above (grass tufts, flowers, snow)
				for (int k = 1; k <= 2; k++) {
					BlockPos pos = new BlockPos(centre.getX() + dx, topY + k, centre.getZ() + dz);
					BlockState above = level.getBlockState(pos);
					if (!above.isAir() && above.canBeReplaced()) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
				BlockPos floor = new BlockPos(centre.getX() + dx, topY - depth, centre.getZ() + dz);
				if (diggable(level, floor) && !level.getBlockState(floor).isAir()) {
					double n = level.random.nextDouble();
					BlockState lining = d < 1.5 ? (n < 0.5 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState())
							: n < 0.45 ? Blocks.BLACKSTONE.defaultBlockState() : n < 0.75 ? Blocks.BASALT.defaultBlockState()
									: n < 0.9 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
					level.setBlock(floor, lining, 3);
				}
				if (d > r - 1.2 && level.random.nextFloat() < 0.12) {
					BlockPos rim = new BlockPos(centre.getX() + dx, topY - depth + 1, centre.getZ() + dz);
					if (level.getBlockState(rim).isAir() && level.getBlockState(rim.below()).isFaceSturdy(level, rim.below(), net.minecraft.core.Direction.UP)) {
						level.setBlock(rim, Blocks.FIRE.defaultBlockState(), 3);
					}
				}
				if (dx == 0 && dz == 0) {
					deepest = topY - depth + 1;
				}
			}
		}
		return new BlockPos(centre.getX(), deepest == 0 ? centre.getY() : deepest, centre.getZ());
	}

	private static boolean diggable(ServerLevel level, BlockPos pos) {
		BlockState s = level.getBlockState(pos);
		return !s.is(Blocks.BEDROCK) && s.getDestroySpeed(level, pos) >= 0f && level.getBlockEntity(pos) == null
				&& s.getFluidState().isEmpty();
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256 * 256;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

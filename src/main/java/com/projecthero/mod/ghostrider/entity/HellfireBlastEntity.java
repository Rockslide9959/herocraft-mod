package com.projecthero.mod.ghostrider.entity;

import java.util.UUID;

import com.projecthero.mod.ghostrider.GhostRiderCombat;
import com.projecthero.mod.ghostrider.GhostRiderConfig;

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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 rework: one <b>Hellfire Blast</b> bolt as a real projectile (it used to be a particle line). A flaming ball of
 * skull fire flying straight from his hand at {@link GhostRiderConfig#BLAST_SPEED}; the client draws it
 * ({@code HellfireBlastRenderer}: a white-hot core, the vanilla fire sprites round it and a short fading trail from
 * {@link #trail}). The server moves it, and a bolt that meets a target (a generous 0.45-block margin round its box, as
 * before) or a block bursts. Damage, burn and Vengeance go through {@link GhostRiderCombat#strike}, so the old numbers
 * hold. Never saved, gone after {@link GhostRiderConfig#BLAST_RANGE} blocks.
 *
 * <p>A plain {@link Entity}: vanilla's {@code Projectile} constructor is package-private (the Darkseid
 * {@code EnergyProjectile} lesson).
 */
public class HellfireBlastEntity extends Entity {
	/** Client-side positions it has been at, newest first (the trail). */
	public static final int TRAIL_LENGTH = 6;
	public final Vec3[] trail = new Vec3[TRAIL_LENGTH];
	public int trailCount;

	private UUID ownerId;
	private ServerPlayer cachedOwner;
	private int life;

	public HellfireBlastEntity(EntityType<? extends HellfireBlastEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A bolt from {@code owner}'s hand at {@code from}, flying along {@code velocity} (blocks a tick). */
	public static HellfireBlastEntity fire(ServerPlayer owner, Vec3 from, Vec3 velocity) {
		HellfireBlastEntity e = GhostRiderEntityTypes.HELLFIRE_BLAST.create(owner.level());
		if (e == null) {
			return null;
		}
		e.ownerId = owner.getUUID();
		e.cachedOwner = owner;
		e.moveTo(from.x, from.y, from.z);
		e.setDeltaMovement(velocity);
		e.faceAlong(velocity);
		owner.level().addFreshEntity(e);
		return e;
	}

	private ServerPlayer owner() {
		if (cachedOwner != null && !cachedOwner.isRemoved()) {
			return cachedOwner;
		}
		if (ownerId != null && level() instanceof ServerLevel server && server.getEntity(ownerId) instanceof ServerPlayer p) {
			cachedOwner = p;
			return p;
		}
		return null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		Vec3 v = getDeltaMovement();
		if (level().isClientSide()) {
			for (int i = trail.length - 1; i > 0; i--) {
				trail[i] = trail[i - 1];
			}
			trail[0] = position();
			trailCount = Math.min(trail.length, trailCount + 1);
			// embers shed off the ball
			if (random.nextInt(2) == 0) {
				level().addParticle(ParticleTypes.FLAME, getX() - v.x * 0.3 + (random.nextDouble() - 0.5) * 0.3,
						getY() + (random.nextDouble() - 0.5) * 0.3, getZ() - v.z * 0.3 + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
			}
			if (random.nextInt(4) == 0) {
				level().addParticle(ParticleTypes.SMOKE, getX() - v.x * 0.5, getY(), getZ() - v.z * 0.5, 0, 0.01, 0);
			}
			return;
		}
		ServerLevel server = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || ++life * GhostRiderConfig.BLAST_SPEED > GhostRiderConfig.BLAST_RANGE) {
			if (owner != null) {
				burst(server, position());
			}
			discard();
			return;
		}
		Vec3 from = position();
		Vec3 next = from.add(v);
		BlockHitResult bhr = server.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? next : bhr.getLocation();
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		for (Entity e : server.getEntities(this, new AABB(from, end).inflate(0.8),
				e -> e != owner && e instanceof LivingEntity le && GhostRiderCombat.isTarget(owner, le) && e.isPickable())) {
			AABB box = e.getBoundingBox().inflate(0.45);
			Vec3 at = box.contains(from) ? from : box.clip(from, end).orElse(null);
			if (at != null && from.distanceToSqr(at) < best) {
				best = from.distanceToSqr(at);
				hit = (LivingEntity) e;
			}
		}
		if (hit != null) {
			// v0.16.5 (user): hellfire burns a symbiote -- its fire weakness, and it burns longer
			boolean symbiote = GhostRiderCombat.isSymbiote(hit);
			GhostRiderCombat.strike(owner, hit, from, GhostRiderConfig.BLAST_DAMAGE * (symbiote ? GhostRiderConfig.BLAST_SYMBIOTE_FACTOR : 1f),
					0.5, 0.15, symbiote ? GhostRiderConfig.BLAST_SYMBIOTE_BURN_SECONDS : GhostRiderConfig.BLAST_BURN_SECONDS);
			if (symbiote) {
				GhostRiderCombat.symbioteRecoil(server, hit);
			}
			burst(server, hit.position().add(0, hit.getBbHeight() * 0.5, 0));
			discard();
			return;
		}
		if (bhr.getType() != HitResult.Type.MISS) {
			burst(server, end);
			ignite(server, owner, bhr);
			discard();
			return;
		}
		setPos(end.x, end.y, end.z);
		faceAlong(v);
	}

	/** v0.16.5 (user): a blast that hits a block sets fire where it landed (as a fire charge would). */
	private static void ignite(ServerLevel level, ServerPlayer owner, BlockHitResult bhr) {
		net.minecraft.core.BlockPos pos = bhr.getBlockPos().relative(bhr.getDirection());
		if (!owner.mayBuild() || !level.mayInteract(owner, pos) || !level.getBlockState(pos).isAir()) {
			return;
		}
		if (net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(level, pos, bhr.getDirection())) {
			level.setBlock(pos, net.minecraft.world.level.block.BaseFireBlock.getState(level, pos), 11);
		}
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 18, 0.2, 0.2, 0.2, 0.09);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 4, 0.15, 0.15, 0.15, 0.04);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.01);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
	}

	private void faceAlong(Vec3 v) {
		setYRot((float) (Math.atan2(v.x, v.z) * (180.0 / Math.PI)));
		setXRot((float) (Math.atan2(v.y, v.horizontalDistance()) * (180.0 / Math.PI)));
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128.0 * 128.0;
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

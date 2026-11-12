package com.projecthero.mod.raven.entity;

import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenSounds;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * v0.17.3 Raven: a block wrapped in black energy, flying like a falling block -- Telekinesis's lifted block and the Debris Storm's
 * orbiting blocks. {@link #energyCopy()} is an energy copy (mob griefing off: the real block never moved, so the copy is drawn darker
 * and see-through); otherwise the real block was taken out and {@code RavenRestore} puts it back 10 s later wherever this lands.
 *
 * <ul>
 *   <li>{@link #MODE_HELD}: steered to a point every tick (Telekinesis).</li>
 *   <li>{@link #MODE_ORBIT}: circling its owner (Debris Storm), slot {@link #orbitSlot} of {@link RavenConfig#DEBRIS_COUNT}.</li>
 *   <li>{@link #MODE_THROWN}: flying, falling slowly; the first creature it meets takes the hit, and it shatters on a wall or the ground.</li>
 * </ul>
 */
public class RavenDebrisEntity extends Entity {
	public static final int MODE_HELD = 0;
	public static final int MODE_ORBIT = 1;
	public static final int MODE_THROWN = 2;

	private static final EntityDataAccessor<BlockState> BLOCK = SynchedEntityData.defineId(RavenDebrisEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Boolean> COPY = SynchedEntityData.defineId(RavenDebrisEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID ownerId;
	public int mode = MODE_HELD;
	public int orbitSlot;
	private Vec3 target;
	private float damage = RavenConfig.DEBRIS_DAMAGE;
	private int life;
	private int thrownTicks;
	/** For the tests: the creature this hit (or -1). */
	public int hitId = -1;

	public RavenDebrisEntity(EntityType<? extends RavenDebrisEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static RavenDebrisEntity spawn(ServerPlayer owner, BlockState state, Vec3 at, boolean copy, int mode) {
		RavenDebrisEntity d = new RavenDebrisEntity(RavenEntities.DEBRIS, owner.level());
		d.ownerId = owner.getUUID();
		d.entityData.set(BLOCK, state);
		d.entityData.set(COPY, copy);
		d.mode = mode;
		d.setPos(at.x, at.y, at.z);
		owner.level().addFreshEntity(d);
		return d;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(BLOCK, Blocks.STONE.defaultBlockState());
		builder.define(COPY, false);
	}

	public BlockState blockState() {
		return entityData.get(BLOCK);
	}

	public boolean energyCopy() {
		return entityData.get(COPY);
	}

	/** Telekinesis: where to steer it this tick (its centre). */
	public void steerTo(Vec3 centre) {
		this.target = centre;
	}

	/** Lets it fly along {@code dir} at {@code speed}, hitting for {@code damage}. */
	public void throwAlong(Vec3 dir, double speed, float damage) {
		this.mode = MODE_THROWN;
		this.damage = damage;
		this.thrownTicks = 0;
		setDeltaMovement(dir.normalize().scale(speed));
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (random.nextInt(3) == 0) {
				level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble(),
						getZ() + (random.nextDouble() - 0.5), 0, 0.01, 0);
			}
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		life++;
		if (owner == null || owner.level() != level || life > RavenConfig.DEBRIS_ORBIT_TICKS + 200) {
			shatter(level);
			return;
		}
		switch (mode) {
			case MODE_HELD -> {
				if (target != null) {
					Vec3 centre = position().add(0, 0.49, 0);
					Vec3 move = target.subtract(centre);
					double len = move.length();
					if (len > 1.2) {
						move = move.scale(1.2 / len);
					}
					Vec3 next = position().add(move.scale(0.6));
					setDeltaMovement(next.subtract(position()));
					setPos(next.x, next.y, next.z);
				}
			}
			case MODE_ORBIT -> {
				double angle = life * 0.12 + orbitSlot * (Math.PI * 2.0 / RavenConfig.DEBRIS_COUNT);
				double r = RavenConfig.DEBRIS_ORBIT_RADIUS * owner.getScale();
				Vec3 want = owner.position().add(Math.cos(angle) * r, 1.2 * owner.getScale() + Mth.sin(life * 0.1f + orbitSlot) * 0.25 - 0.49,
						Math.sin(angle) * r);
				Vec3 next = position().add(want.subtract(position()).scale(0.35));
				setDeltaMovement(next.subtract(position()));
				setPos(next.x, next.y, next.z);
			}
			default -> flyTick(level, owner);
		}
	}

	private void flyTick(ServerLevel level, ServerPlayer owner) {
		thrownTicks++;
		Vec3 v = getDeltaMovement().add(0, -0.03, 0);
		setDeltaMovement(v);
		Vec3 from = position().add(0, 0.49, 0);
		Vec3 to = from.add(v);
		for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(v).inflate(0.3),
				e -> e.isAlive() && e != owner && HeroTargets.canHarm(owner, e))) {
			if (Raven.hit(owner, t, damage)) {
				hitId = t.getId();
				if (!Raven.isBoss(t)) {
					Vec3 push = v.normalize();
					t.push(push.x * 0.6, 0.25, push.z * 0.6);
					t.hurtMarked = true;
				}
			}
			shatter(level);
			return;
		}
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (block.getType() != HitResult.Type.MISS || thrownTicks > RavenConfig.TK_THROWN_TICKS * 2) {
			shatter(level);
			return;
		}
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
	}

	/** Breaks apart where it is (the block's crumbs and a puff of shadow). */
	public void shatter(ServerLevel level) {
		Vec3 c = position().add(0, 0.5, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState()), c.x, c.y, c.z, 24, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(Raven.dust(Raven.VOID, 1.4f), c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.0);
		level.playSound(null, c.x, c.y, c.z, blockState().getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.5f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.DEBRIS, SoundSource.PLAYERS, 0.3f, 0.7f);
		discard();
	}

	public UUID ownerId() {
		return ownerId;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	/** For the tests: the owner (server side). */
	public Entity ownerEntity() {
		return ownerId == null || !(level() instanceof ServerLevel sl) ? null : sl.getServer().getPlayerList().getPlayer(ownerId);
	}
}

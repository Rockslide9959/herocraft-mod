package com.projecthero.mod.miraculous.catnoir.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.catnoir.CatNoirAbilities;
import com.projecthero.mod.miraculous.catnoir.CatNoirCombat;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
 * v0.16.6: Cat Noir's <b>thrown baton</b> (Shift+G). Flies straight out along the look at
 * {@link CatNoirConfig#THROW_SPEED}, spinning end over end (the client draws the spin), strikes the first creature it
 * meets for {@link CatNoirConfig#THROW_DAMAGE} -- or turns at a block or at {@link CatNoirConfig#THROW_RANGE} -- and
 * then homes back to his hand, where {@link CatNoirAbilities#catchBaton} takes it. Moved by the server only, never
 * saved. A plain {@link Entity}, as {@code HellfireBlastEntity} (vanilla's Projectile constructor is not public).
 */
public class CatBatonEntity extends Entity {
	private static final EntityDataAccessor<Boolean> ROTTED = SynchedEntityData.defineId(CatBatonEntity.class, EntityDataSerializers.BOOLEAN);

	private UUID ownerId;
	private ServerPlayer cachedOwner;
	private boolean returning;
	private double travelled;
	private int life;
	private final Set<Integer> hit = new HashSet<>();

	public CatBatonEntity(EntityType<? extends CatBatonEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A baton thrown by {@code owner} from {@code from} along {@code dir}. */
	public static CatBatonEntity toss(ServerPlayer owner, Vec3 from, Vec3 dir, boolean rotted) {
		CatBatonEntity e = CatNoirEntityTypes.THROWN_BATON.create(owner.level());
		if (e == null) {
			return null;
		}
		e.ownerId = owner.getUUID();
		e.cachedOwner = owner;
		e.entityData.set(ROTTED, rotted);
		e.moveTo(from.x, from.y, from.z);
		e.setDeltaMovement(dir.normalize().scale(CatNoirConfig.THROW_SPEED));
		owner.level().addFreshEntity(e);
		return e;
	}

	public boolean rotted() {
		return entityData.get(ROTTED);
	}

	public boolean returning() {
		return returning;
	}

	private ServerPlayer owner() {
		if (cachedOwner != null && !cachedOwner.isRemoved() && cachedOwner.level() == level()) {
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
		builder.define(ROTTED, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (tickCount % 3 == 0) {
				level().addParticle(ParticleTypes.CRIT, getX(), getY() + 0.25, getZ(), 0, 0, 0);
			}
			return;
		}
		ServerLevel server = (ServerLevel) level();
		ServerPlayer owner = owner();
		if (owner == null || !owner.isAlive() || ++life > CatNoirConfig.THROW_MAX_TICKS) {
			if (owner != null) {
				CatNoirAbilities.catchBaton(owner, this);
			}
			discard();
			return;
		}
		if (life % 4 == 0) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.35f, 1.8f);
		}
		Vec3 from = position();
		if (returning) {
			Vec3 hand = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
			Vec3 to = hand.subtract(from);
			double d = to.length();
			if (d <= CatNoirConfig.THROW_SPEED + 0.6) {
				CatNoirAbilities.catchBaton(owner, this);
				discard();
				return;
			}
			Vec3 v = to.scale(Math.min(1.0, (CatNoirConfig.THROW_SPEED * 1.2) / d));
			setDeltaMovement(v);
			setPos(from.x + v.x, from.y + v.y, from.z + v.z);
			return;
		}
		Vec3 v = getDeltaMovement();
		Vec3 next = from.add(v);
		BlockHitResult bhr = server.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? next : bhr.getLocation();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (Entity e : server.getEntities(this, new AABB(from, end).inflate(0.8), e -> e != owner && e.isPickable())) {
			LivingEntity le = AbilityHelpers.asLiving(e);
			if (le == null || hit.contains(le.getId()) || !CatNoirCombat.isTarget(owner, le)) {
				continue;
			}
			AABB box = e.getBoundingBox().inflate(0.35);
			Vec3 at = box.contains(from) ? from : box.clip(from, end).orElse(null);
			if (at != null && from.distanceToSqr(at) < best) {
				best = from.distanceToSqr(at);
				target = le;
			}
		}
		if (target != null) {
			hit.add(target.getId());
			CatNoirCombat.strike(owner, target, from.subtract(v), CatNoirConfig.THROW_DAMAGE, CatNoirConfig.THROW_KNOCKBACK, 0.2);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4f, 1.9f);
			returning = true;
			return;
		}
		if (bhr.getType() != HitResult.Type.MISS) {
			server.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 8, 0.1, 0.1, 0.1, 0.2);
			server.playSound(null, end.x, end.y, end.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 2.0f);
			setPos(end.x - v.x * 0.1, end.y - v.y * 0.1, end.z - v.z * 0.1);
			returning = true;
			return;
		}
		travelled += v.length();
		setPos(end.x, end.y, end.z);
		if (travelled >= CatNoirConfig.THROW_RANGE) {
			returning = true;
		}
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

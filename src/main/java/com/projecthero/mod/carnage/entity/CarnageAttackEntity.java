package com.projecthero.mod.carnage.entity;

import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.carnage.CarnageEntityTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15: the pieces of Carnage's newer moves that live out in the world, each drawn as real crimson geometry by
 * {@code CarnageRenderer.Attack} (never saved -- a reload simply ends them):
 * <ul>
 *   <li>{@link #KIND_SPIKE} -- Spike Eruption: red cracks spread on the ground for {@code delay} ticks (the warning),
 *       then a cluster of crimson spikes bursts up, hits and launches whoever stands on it, and sinks back;</li>
 *   <li>{@link #KIND_SHOCKWAVE} -- Axe-Arm Cleave's landing: a ring of jagged crimson shards racing outward along
 *       the ground to {@link #SHOCK_RADIUS} blocks, hitting each thing it passes once;</li>
 *   <li>{@link #KIND_GLOB} -- Symbiote Snare: a lobbed glob of crimson goo; where it bursts, everyone close is hit
 *       and wrapped in a {@link #KIND_SNARE};</li>
 *   <li>{@link #KIND_SNARE} -- a cocoon of tendrils round one victim that roots them in place for
 *       {@link #SNARE_TICKS} ticks.</li>
 * </ul>
 */
public class CarnageAttackEntity extends Entity {
	public static final byte KIND_SPIKE = 0, KIND_SHOCKWAVE = 1, KIND_GLOB = 2, KIND_SNARE = 3;

	public static final float SPIKE_DAMAGE = 9.0f;
	public static final int SPIKE_UP_TICKS = 4, SPIKE_HOLD_TICKS = 14, SPIKE_DOWN_TICKS = 6;
	public static final float SHOCK_DAMAGE = 6.0f;
	public static final double SHOCK_RADIUS = 7.0;
	public static final int SHOCK_TICKS = 12;
	public static final float GLOB_DAMAGE = 4.0f;
	public static final double GLOB_GRAVITY = 0.04;
	public static final int SNARE_TICKS = 40;

	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(CarnageAttackEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_DELAY = SynchedEntityData.defineId(CarnageAttackEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(CarnageAttackEntity.class, EntityDataSerializers.INT);
	private static final ResourceLocation SNARE_SPEED = ProjectHeroMod.id("carnage_snare_speed");
	private static final ResourceLocation SNARE_JUMP = ProjectHeroMod.id("carnage_snare_jump");
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.8f, 0.03f, 0.05f), 1.2f);

	private LivingEntity owner;
	private final Set<Integer> hit = new HashSet<>();

	public CarnageAttackEntity(EntityType<? extends CarnageAttackEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	// ------------------------------------------------------------------ factories

	/** One spike cluster at {@code (x, z)} (snapped to the ground), erupting {@code delay} ticks from now. */
	public static CarnageAttackEntity spike(ServerLevel level, LivingEntity owner, double x, double z, double refY, int delay) {
		CarnageAttackEntity e = create(level, owner, KIND_SPIKE);
		if (e == null) {
			return null;
		}
		e.entityData.set(DATA_DELAY, Math.max(4, delay));
		e.moveTo(x, groundY(level, x, z, refY), z, level.random.nextFloat() * 360f, 0f);
		level.addFreshEntity(e);
		return e;
	}

	public static CarnageAttackEntity shockwave(ServerLevel level, LivingEntity owner, Vec3 at) {
		CarnageAttackEntity e = create(level, owner, KIND_SHOCKWAVE);
		if (e == null) {
			return null;
		}
		e.moveTo(at.x, groundY(level, at.x, at.z, at.y), at.z, 0f, 0f);
		level.addFreshEntity(e);
		return e;
	}

	/** A glob lobbed from {@code from} so that it comes down on {@code to}. */
	public static CarnageAttackEntity glob(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 to) {
		CarnageAttackEntity e = create(level, owner, KIND_GLOB);
		if (e == null) {
			return null;
		}
		Vec3 span = to.subtract(from);
		double flat = Math.sqrt(span.x * span.x + span.z * span.z);
		double ticks = Mth.clamp(flat / 1.1, 4.0, 30.0);
		e.moveTo(from.x, from.y, from.z, 0f, 0f);
		e.setDeltaMovement(span.x / ticks, span.y / ticks + 0.5 * GLOB_GRAVITY * ticks, span.z / ticks);
		level.addFreshEntity(e);
		return e;
	}

	/** Roots {@code victim} for {@link #SNARE_TICKS} (no-op if they are already snared). */
	public static CarnageAttackEntity snare(ServerLevel level, LivingEntity owner, LivingEntity victim) {
		AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.hasModifier(SNARE_SPEED)) {
			return null;
		}
		CarnageAttackEntity e = create(level, owner, KIND_SNARE);
		if (e == null) {
			return null;
		}
		e.entityData.set(DATA_TARGET, victim.getId());
		e.moveTo(victim.getX(), victim.getY(), victim.getZ(), 0f, 0f);
		level.addFreshEntity(e);
		root(victim, true);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.4f, 0.6f);
		return e;
	}

	private static CarnageAttackEntity create(ServerLevel level, LivingEntity owner, byte kind) {
		CarnageAttackEntity e = CarnageEntityTypes.CARNAGE_ATTACK.create(level);
		if (e != null) {
			e.owner = owner;
			e.entityData.set(DATA_KIND, kind);
		}
		return e;
	}

	/** True while {@code e} is held by a snare. */
	public static boolean isSnared(LivingEntity e) {
		AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(SNARE_SPEED);
	}

	private static void root(LivingEntity victim, boolean on) {
		AttributeInstance speed = victim.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = victim.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null && !speed.hasModifier(SNARE_SPEED)) {
				speed.addTransientModifier(new AttributeModifier(SNARE_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null && !jump.hasModifier(SNARE_JUMP)) {
				jump.addTransientModifier(new AttributeModifier(SNARE_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(SNARE_SPEED);
			}
			if (jump != null) {
				jump.removeModifier(SNARE_JUMP);
			}
		}
	}

	/** The top of the ground column at {@code (x, z)}, searched a few blocks either side of {@code refY}. */
	public static double groundY(Level level, double x, double z, double refY) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(refY) + 3, Mth.floor(z));
		for (int i = 0; i < 8; i++) {
			BlockPos below = p.below();
			if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
					&& !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return p.getY();
			}
			p.move(0, -1, 0);
		}
		return refY;
	}

	// ------------------------------------------------------------------ data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, KIND_SPIKE);
		builder.define(DATA_DELAY, 15);
		builder.define(DATA_TARGET, -1);
	}

	public byte kind() {
		return entityData.get(DATA_KIND);
	}

	/** Spike: ticks of warning cracks before it erupts. */
	public int delay() {
		return entityData.get(DATA_DELAY);
	}

	/** Snare: the victim's entity id. */
	public int targetId() {
		return entityData.get(DATA_TARGET);
	}

	public int life() {
		return switch (kind()) {
			case KIND_SPIKE -> delay() + SPIKE_UP_TICKS + SPIKE_HOLD_TICKS + SPIKE_DOWN_TICKS;
			case KIND_SHOCKWAVE -> SHOCK_TICKS + 4;
			case KIND_GLOB -> 80;
			default -> SNARE_TICKS;
		};
	}

	private DamageSource source() {
		return owner != null ? damageSources().mobAttack(owner) : damageSources().generic();
	}

	private boolean victim(Entity e) {
		return e instanceof LivingEntity l && l.isAlive() && l != owner && !(l instanceof CarnageEntity) && !(l instanceof CrimsonSpawnEntity)
				&& !(l instanceof Player p && (p.isSpectator() || p.isCreative()));
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		byte kind = kind();
		if (kind == KIND_GLOB) {
			tickGlob();
			return;
		}
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (tickCount > life()) {
			discard();
			return;
		}
		switch (kind) {
			case KIND_SPIKE -> tickSpike(level);
			case KIND_SHOCKWAVE -> tickShockwave(level);
			case KIND_SNARE -> tickSnare(level);
			default -> {
			}
		}
	}

	private void tickSpike(ServerLevel level) {
		int delay = delay();
		if (tickCount < delay) {
			if (tickCount % 3 == 0) {
				level.sendParticles(RED, getX(), getY() + 0.1, getZ(), 2, 0.35, 0.02, 0.35, 0.0);
			}
			if (tickCount == 1) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 0.8f, 0.6f);
			}
			return;
		}
		if (tickCount == delay) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.4f, 0.6f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.0f, 0.5f);
			level.sendParticles(RED, getX(), getY() + 0.4, getZ(), 12, 0.4, 0.4, 0.4, 0.0);
		}
		if (tickCount <= delay + SPIKE_UP_TICKS + 1) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(getX() - 0.9, getY() - 0.2, getZ() - 0.9,
					getX() + 0.9, getY() + 2.6, getZ() + 0.9), this::victim)) {
				if (hit.add(e.getId())) {
					e.invulnerableTime = 0;
					if (e.hurt(source(), SPIKE_DAMAGE)) {
						e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.75, e.getDeltaMovement().z * 0.3);
						e.hurtMarked = true;
					}
				}
			}
		}
	}

	/** The ring's radius at {@code age} ticks. */
	public static double shockRadius(float age) {
		return SHOCK_RADIUS * Math.min(1.0, age / SHOCK_TICKS);
	}

	private void tickShockwave(ServerLevel level) {
		double r = shockRadius(tickCount);
		if (tickCount > SHOCK_TICKS) {
			return;
		}
		int n = Math.max(6, (int) (r * 5));
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			level.sendParticles(RED, getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0.0);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 0.6, 1.6, r + 0.6), this::victim)) {
			double dx = e.getX() - getX(), dz = e.getZ() - getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d <= r + 0.6 && d >= r - 1.6 && e.getY() < getY() + 1.6 && hit.add(e.getId())) {
				e.invulnerableTime = 0;
				if (e.hurt(source(), SHOCK_DAMAGE)) {
					double k = d < 1.0e-3 ? 0 : 0.9 / d;
					e.push(dx * k, 0.4, dz * k);
					e.hurtMarked = true;
				}
			}
		}
	}

	private void tickGlob() {
		Vec3 v = getDeltaMovement();
		Vec3 from = position();
		Vec3 to = from.add(v);
		if (level() instanceof ServerLevel level) {
			if (tickCount > life()) {
				discard();
				return;
			}
			BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			LivingEntity struck = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.6), this::victim)) {
				if (e.getBoundingBox().inflate(0.35).clip(from, end).isPresent() || e.getBoundingBox().inflate(0.35).contains(from)) {
					struck = e;
					break;
				}
			}
			if (struck != null) {
				burst(level, struck.position().add(0, struck.getBbHeight() * 0.5, 0));
				return;
			}
			if (block.getType() != HitResult.Type.MISS) {
				burst(level, block.getLocation());
				return;
			}
			if (tickCount % 2 == 0) {
				level.sendParticles(RED, getX(), getY(), getZ(), 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
		setDeltaMovement(v.x * 0.99, v.y - GLOB_GRAVITY, v.z * 0.99);
		setPos(to.x, to.y, to.z);
	}

	private void burst(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.6f, 0.5f);
		level.sendParticles(RED, at.x, at.y + 0.3, at.z, 30, 0.8, 0.4, 0.8, 0.0);
		level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 10, 0.6, 0.3, 0.6, 0.05);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.8, 1.8, 1.8), this::victim)) {
			e.invulnerableTime = 0;
			e.hurt(source(), GLOB_DAMAGE);
			snare(level, owner, e);
		}
		discard();
	}

	private void tickSnare(ServerLevel level) {
		Entity t = level.getEntity(targetId());
		if (!(t instanceof LivingEntity victim) || !victim.isAlive()) {
			discard();
			return;
		}
		root(victim, true);
		setPos(victim.getX(), victim.getY(), victim.getZ());
		Vec3 v = victim.getDeltaMovement();
		victim.setDeltaMovement(0, Math.min(0, v.y), 0);
		if (victim instanceof Player) {
			victim.hurtMarked = true;
		}
		if (tickCount % 5 == 0) {
			level.sendParticles(RED, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
		}
		if (tickCount >= SNARE_TICKS) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.0f, 0.8f);
			discard();
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide && kind() == KIND_SNARE && level().getEntity(targetId()) instanceof LivingEntity victim) {
			root(victim, false);
		}
		super.remove(reason);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96 * 96;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
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
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

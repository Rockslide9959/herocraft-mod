package com.projecthero.mod.omnitrix.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.alien.AlienKit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: one reusable projectile for every Omnitrix alien (Heatblast's blasts and bombs today; Diamondhead's shards,
 * Stinkfly's goo, Upgrade's beam bolts later). Set up with the builder methods, then {@link #spawn()}:
 *
 * <pre>
 *   AlienProjectileEntity.of(p, "heatblast_blast").colour(0xFF7A1A).size(0.45f).damage(6f).fire(4).speed(1.8).spawn();
 *   AlienProjectileEntity.of(p, "heatblast_bomb").gravity(0.04).blast(3.5f, 5f).speed(1.1).spawn();
 *   AlienProjectileEntity.onHit("diamondhead_shard", (proj, hit) -> { ... });   // optional extra behaviour per kind
 * </pre>
 *
 * The kind, colour and size are synced (the client draws a glowing core in the colour, or a kind's own renderer registered
 * with {@code AlienProjectileClients}); everything else is server-only. Never saved.
 */
public class AlienProjectileEntity extends Projectile {
	private static final EntityDataAccessor<String> KIND = SynchedEntityData.defineId(AlienProjectileEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Integer> COLOUR = SynchedEntityData.defineId(AlienProjectileEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(AlienProjectileEntity.class, EntityDataSerializers.FLOAT);

	private static final Map<String, BiConsumer<AlienProjectileEntity, HitResult>> ON_HIT = new HashMap<>();
	/** v0.17.1 wave A: kind -> the particle its flight trails on the client (registered once at init; never per player). */
	private static final Map<String, net.minecraft.core.particles.ParticleOptions> TRAILS = new HashMap<>();

	private float damage = 4f;
	private boolean fireDamage;
	private int fireSeconds;
	private float blastRadius;
	private float blastDamage;
	private double gravity;
	private int life = 60;
	private double speed = 1.5;

	public AlienProjectileEntity(EntityType<? extends AlienProjectileEntity> type, Level level) {
		super(type, level);
	}

	/** A projectile of {@code kind} leaving {@code owner}'s eyes along their look. */
	public static AlienProjectileEntity of(ServerPlayer owner, String kind) {
		AlienProjectileEntity e = new AlienProjectileEntity(OmnitrixEntities.ALIEN_PROJECTILE, owner.level());
		e.setOwner(owner);
		e.entityData.set(KIND, kind);
		Vec3 look = owner.getLookAngle();
		Vec3 from = owner.getEyePosition().add(look.scale(0.6)).add(0, -0.15, 0);
		e.moveTo(from.x, from.y, from.z, owner.getYRot(), owner.getXRot());
		e.setDeltaMovement(look);
		return e;
	}

	/** Adds extra behaviour to every projectile of {@code kind} when it hits something (after the damage / blast). */
	public static void onHit(String kind, BiConsumer<AlienProjectileEntity, HitResult> fn) {
		ON_HIT.put(kind, fn);
	}

	/** v0.17.1 wave A: every projectile of {@code kind} trails {@code particle} on the client (Diamondhead's shards...). */
	public static void trail(String kind, net.minecraft.core.particles.ParticleOptions particle) {
		TRAILS.put(kind, particle);
	}

	public AlienProjectileEntity colour(int rgb) {
		entityData.set(COLOUR, rgb);
		return this;
	}

	public AlienProjectileEntity size(float s) {
		entityData.set(SIZE, s);
		return this;
	}

	public AlienProjectileEntity damage(float d) {
		damage = d;
		return this;
	}

	/** Fire damage ({@code projecthero:alien_fire}) that sets the target alight for {@code seconds}. */
	public AlienProjectileEntity fire(int seconds) {
		fireDamage = true;
		fireSeconds = seconds;
		return this;
	}

	/** On any hit, a blast of {@code radius} dealing up to {@code dmg} (falls off to half at the edge). Breaks no blocks. */
	public AlienProjectileEntity blast(float radius, float dmg) {
		blastRadius = radius;
		blastDamage = dmg;
		return this;
	}

	public AlienProjectileEntity gravity(double g) {
		gravity = g;
		return this;
	}

	public AlienProjectileEntity life(int ticks) {
		life = ticks;
		return this;
	}

	public AlienProjectileEntity speed(double s) {
		speed = s;
		return this;
	}

	/** Starts it with a direction other than the owner's look. */
	public AlienProjectileEntity direction(Vec3 dir) {
		setDeltaMovement(dir.normalize());
		return this;
	}

	public AlienProjectileEntity spawn() {
		setDeltaMovement(getDeltaMovement().normalize().scale(speed));
		level().addFreshEntity(this);
		return this;
	}

	public String kind() {
		return entityData.get(KIND);
	}

	public int colour() {
		return entityData.get(COLOUR);
	}

	public float size() {
		return entityData.get(SIZE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, "");
		builder.define(COLOUR, 0xFFFFFF);
		builder.define(SIZE, 0.4f);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) {
			if (tickCount > life) {
				discard();
				return;
			}
			HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
			if (hit.getType() != HitResult.Type.MISS) {
				impact(hit);
				return;
			}
		}
		Vec3 v = getDeltaMovement();
		if (level().isClientSide() && kind().startsWith("heatblast")) {
			for (int i = 0; i < 2; i++) {
				level().addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, getX() - v.x * i * 0.5, getY() - v.y * i * 0.5, getZ() - v.z * i * 0.5,
						(random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
			}
			if (random.nextInt(3) == 0) {
				level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0.01, 0);
			}
		}
		if (level().isClientSide()) {
			net.minecraft.core.particles.ParticleOptions trail = TRAILS.get(kind());
			if (trail != null) {
				level().addParticle(trail, getX() - v.x * 0.5, getY() - v.y * 0.5, getZ() - v.z * 0.5, 0, 0, 0);
			}
		}
		setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
		if (gravity != 0) {
			setDeltaMovement(v.x, v.y - gravity, v.z);
		}
		if (isInWater() && fireDamage && !level().isClientSide()) {
			((ServerLevel) level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
			discard(); // a fire blast fizzles in water
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		return super.canHitEntity(target) && target != getOwner() && !(getOwner() != null && target.isPassengerOfSameVehicle(getOwner()));
	}

	private void impact(HitResult hit) {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Vec3 at = hit.getLocation();
		ServerPlayer owner = getOwner() instanceof ServerPlayer sp ? sp : null;
		if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity target && owner != null && damage > 0f) {
			AlienKit.hurt(owner, target, damage, fireDamage);
			if (fireSeconds > 0 && !target.fireImmune()) {
				target.igniteForSeconds(fireSeconds);
			}
		}
		if (blastRadius > 0f && owner != null) {
			for (LivingEntity e : AlienKit.around(owner, at, blastRadius)) {
				double d = Math.sqrt(e.distanceToSqr(at));
				float f = (float) (blastDamage * (1.0 - 0.5 * Math.min(1.0, d / blastRadius)));
				AlienKit.hurt(owner, e, f, fireDamage);
				AlienKit.shove(e, at, 0.6, 0.35);
				if (fireSeconds > 0 && !e.fireImmune()) {
					e.igniteForSeconds(fireSeconds);
				}
			}
		}
		BiConsumer<AlienProjectileEntity, HitResult> extra = ON_HIT.get(kind());
		if (extra != null) {
			extra.accept(this, hit);
		}
		discard();
	}

	@Override
	public boolean shouldBeSaved() {
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

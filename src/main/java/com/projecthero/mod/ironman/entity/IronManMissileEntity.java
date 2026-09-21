package com.projecthero.mod.ironman.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A single Iron Man missile-type projectile: flies mostly straight with mild homing toward the
 * nearest hostile, and on impact makes its own small no-block-damage explosion. Never destroys
 * terrain (uses {@link Level.ExplosionInteraction#NONE}).
 *
 * <p>Shared by two abilities that differ only in payload: Micro-Missiles (spec "changes 8", several
 * fired at once, {@code 8} direct / {@code 4} splash) and the Mark 1/2 Rocket ("changes 12", one
 * fired, {@code 15} direct -- see {@link #withDamage}).
 */
public class IronManMissileEntity extends AbstractHurtingProjectile {
	private int life;
	private float directDamage = 8.0f;
	private float splashDamage = 4.0f;
	private boolean homing = false; // "changes 14": rockets / micro-missiles fly where they were aimed
	private float blastRadius = 1.4f;
	/** v0.11.13: the Mark 1/2 Rocket opts into this -- Micro-Missiles never does. */
	private boolean breaksBlocks = false;

	public IronManMissileEntity(EntityType<? extends IronManMissileEntity> type, Level level) {
		super(type, level);
	}

	public IronManMissileEntity(Level level, LivingEntity owner, Vec3 movement) {
		super(IronManEntityTypes.MISSILE, owner, movement, level);
	}

	/** Set once, right after {@link #IronManMissileEntity(Level, LivingEntity, Vec3)}, before spawning. */
	public IronManMissileEntity withDamage(float direct, float splash) {
		this.directDamage = direct;
		this.splashDamage = splash;
		return this;
	}

	/** Give this missile mild in-flight homing toward the nearest hostile (off by default -- "changes 14"). */
	public IronManMissileEntity withHoming() {
		this.homing = true;
		return this;
	}

	/** Set the blast size of the on-impact AoE explosion (default 1.4). */
	public IronManMissileEntity withBlastRadius(float radius) {
		this.blastRadius = radius;
		return this;
	}

	/**
	 * "changes 14"'s class javadoc used to promise this entity type "never destroys terrain" -- v0.11.13,
	 * explicit user request, gives the Mark 1/2 Rocket an opt-in exception: a TNT-style block-breaking
	 * blast, still respecting {@code HeroConfig.abilityTerrainDamage} like every other terrain effect in
	 * the mod (see {@link #detonate}). Every other caller of this entity (Micro-Missiles) leaves this off.
	 */
	public IronManMissileEntity withBreaksBlocks() {
		this.breaksBlocks = true;
		return this;
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected float getInertia() {
		return 0.98f;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
			level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
			return;
		}
		if (++life > 100) {
			detonate();
			return;
		}
		// mild homing toward the nearest hostile within 12 blocks -- only when explicitly enabled
		// ("changes 14": rockets and micro-missiles are dumb-fire, they hit where they were aimed).
		if (homing) {
			LivingEntity target = nearestTarget();
			if (target != null) {
				Vec3 want = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(position()).normalize();
				Vec3 v = getDeltaMovement().normalize().scale(0.85).add(want.scale(0.15)).normalize()
						.scale(getDeltaMovement().length());
				setDeltaMovement(v);
			}
		}
		((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, 0.02, 0.02, 0.02, 0.0);
	}

	private LivingEntity nearestTarget() {
		LivingEntity best = null;
		double bestSq = 144.0;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12.0),
				this::canTargetLiving)) {
			double d = e.distanceToSqr(this);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * The same "who counts as an enemy" rule the rest of the mod's abilities use
	 * ({@code AbilityHelpers.enemiesAround}): never the shooter, never an armour stand, and never
	 * another player unless server PvP <em>and</em> the mod's own ability-PvP switch are both on.
	 *
	 * <p>The missile used to home onto -- and blow up -- literally any living thing, which meant a
	 * volley fired on a peaceful server would steer itself into the nearest bystander. The launch-time
	 * target list already respected this rule; only the in-flight homing and the blast did not.
	 */
	private boolean canTargetLiving(LivingEntity e) {
		if (!e.isAlive() || e == getOwner()
				|| e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return false;
		}
		if (e instanceof net.minecraft.world.entity.player.Player) {
			return com.projecthero.mod.hero.HeroConfig.get().abilityPvpDamage
					&& level().getServer() != null && level().getServer().isPvpAllowed();
		}
		return true;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (!level().isClientSide() && hit.getEntity() instanceof LivingEntity le && canTargetLiving(le)) {
			le.hurt(damageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), directDamage);
		}
		detonate();
	}

	@Override
	protected void onHitBlock(net.minecraft.world.phys.BlockHitResult hit) {
		super.onHitBlock(hit);
		detonate();
	}

	@Override
	protected void onHit(HitResult hit) {
		if (hit.getType() != HitResult.Type.MISS) {
			super.onHit(hit);
		}
	}

	private void detonate() {
		if (level().isClientSide() || !isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		Level.ExplosionInteraction interaction = breaksBlocks && com.projecthero.mod.hero.power.AbilityHelpers.canGrief()
				? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
		level.explode(this, getX(), getY(), getZ(), blastRadius, interaction);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 1.4f);
		// AoE splash damage, scaled with the blast size, falling off toward the edge
		double aoe = blastRadius + 1.0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(getX() - aoe, getY() - aoe, getZ() - aoe, getX() + aoe, getY() + aoe, getZ() + aoe),
				this::canTargetLiving)) {
			double dist = Math.sqrt(e.distanceToSqr(getX(), getY(), getZ()));
			float falloff = (float) Math.max(0.25, 1.0 - dist / (aoe + 1.0));
			e.hurt(damageSources().explosion(this, getOwner() instanceof LivingEntity o ? o : null), splashDamage * falloff);
		}
		discard();
	}

	@Override
	protected boolean canHitEntity(net.minecraft.world.entity.Entity entity) {
		return super.canHitEntity(entity)
				&& (!(entity instanceof LivingEntity le) || canTargetLiving(le));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("DirectDamage")) {
			directDamage = tag.getFloat("DirectDamage");
		}
		if (tag.contains("SplashDamage")) {
			splashDamage = tag.getFloat("SplashDamage");
		}
		homing = tag.getBoolean("Homing");
		if (tag.contains("BlastRadius")) {
			blastRadius = tag.getFloat("BlastRadius");
		}
		breaksBlocks = tag.getBoolean("BreaksBlocks");
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("DirectDamage", directDamage);
		tag.putFloat("SplashDamage", splashDamage);
		tag.putBoolean("Homing", homing);
		tag.putFloat("BlastRadius", blastRadius);
		tag.putBoolean("BreaksBlocks", breaksBlocks);
	}
}

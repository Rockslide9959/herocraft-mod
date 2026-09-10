package com.projecthero.mod.punisher.entity;

import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.item.PunisherItems;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Punisher's Frag Grenade. v0.10.10: it <em>sticks</em> to the first surface it touches rather
 * than bouncing -- a thrown grenade used to skitter away down slopes and around corners, so where it
 * ended up had very little to do with where it was aimed. It still does not detonate on contact; the
 * fixed fuse (spec section 23) is what sets it off. Cooking is handled before the throw --
 * {@link com.projecthero.mod.punisher.ability.PunisherGrenade} passes a reduced starting fuse -- and a
 * grenade held too long detonates in the thrower's hand.
 *
 * <p>Explosion damage falls off with distance, applies knockback, and only lightly damages blocks
 * ({@link PunisherConfig#GRENADE_BLOCK_POWER}) so one grenade never levels a structure.
 */
public class FragGrenadeEntity extends ThrowableItemProjectile {
	private int fuse = PunisherConfig.GRENADE_FUSE_TICKS;
	private int restTicks;
	/** v0.10.10: set the moment it touches anything -- from then on it is welded in place. */
	private boolean stuck;

	public FragGrenadeEntity(EntityType<? extends FragGrenadeEntity> type, Level level) {
		super(type, level);
	}

	public FragGrenadeEntity(Level level, LivingEntity thrower, int startingFuse) {
		super(PunisherEntityTypes.FRAG_GRENADE, thrower, level);
		this.fuse = Math.max(1, startingFuse);
	}

	@Override
	protected Item getDefaultItem() {
		return PunisherItems.FRAG_GRENADE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.045;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
			return;
		}
		if (--fuse <= 0) {
			explode();
			return;
		}
		if (fuse % 5 == 0) {
			((ServerLevel) level()).sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(),
					1, 0.02, 0.02, 0.02, 0.0);
		}
		if (stuck) {
			// welded to whatever it hit: no gravity, no drift, no sliding down a slope
			restTicks++;
			setDeltaMovement(Vec3.ZERO);
			setNoGravity(true);
			hasImpulse = false;
		} else if (getDeltaMovement().lengthSqr() < 0.0015 && onGround()) {
			restTicks++;
			setDeltaMovement(Vec3.ZERO);
		} else {
			restTicks = 0;
		}
	}

	/**
	 * v0.10.10: stick where it lands. Deliberately no {@code super()} -- a grenade still does not
	 * detonate or vanish on contact, its fuse does that. It is parked a hair off the face it struck so
	 * it is not buried inside the block, and pinned there for the rest of its fuse.
	 */
	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (stuck) {
			return;
		}
		boolean audible = getDeltaMovement().lengthSqr() > 0.02;
		Direction d = hit.getDirection();
		Vec3 at = hit.getLocation().add(d.getStepX() * 0.06, d.getStepY() * 0.06, d.getStepZ() * 0.06);
		setPos(at.x, at.y, at.z);
		stick();
		if (audible) {
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.3f, 1.5f);
		}
	}

	/**
	 * Glancing off a body still leaves the grenade at that body's feet rather than ricocheting across
	 * the room: it drops straight down from the contact point and sticks on landing.
	 */
	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (stuck) {
			return;
		}
		setDeltaMovement(0.0, -0.2, 0.0);
	}

	private void stick() {
		stuck = true;
		setDeltaMovement(Vec3.ZERO);
		setNoGravity(true);
		setDeltaMovement(Vec3.ZERO);
		hasImpulse = false;
	}

	private void explode() {
		if (level().isClientSide() || !isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		boolean grief = com.projecthero.mod.hero.power.AbilityHelpers.canGrief();
		level.explode(this, getX(), getY() + 0.2, getZ(), grief ? PunisherConfig.GRENADE_BLOCK_POWER : 0f,
				grief ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 12, 0.6, 0.4, 0.6, 0.02);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(),
				SoundSource.PLAYERS, 1.4f, 1.1f);

		LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
		double r = PunisherConfig.GRENADE_RADIUS;
		var src = owner instanceof net.minecraft.server.level.ServerPlayer sp
				? level.damageSources().playerAttack(sp)
				: level.damageSources().explosion(this, owner);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(getX() - r, getY() - r, getZ() - r, getX() + r, getY() + r, getZ() + r),
				e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			double dist = Math.sqrt(e.distanceToSqr(getX(), getY(), getZ()));
			if (dist > r) {
				continue;
			}
			if (e instanceof net.minecraft.world.entity.player.Player p && !canHurtPlayer(p, owner)) {
				continue;
			}
			float falloff = (float) Math.max(0.15, 1.0 - dist / r);
			e.hurt(src, PunisherConfig.GRENADE_DAMAGE * falloff);
			double kx = e.getX() - getX();
			double kz = e.getZ() - getZ();
			double kd = Math.max(0.1, Math.sqrt(kx * kx + kz * kz));
			e.knockback(0.5 * falloff, -kx / kd, -kz / kd);
			e.hurtMarked = true;
		}
		discard();
	}

	private boolean canHurtPlayer(net.minecraft.world.entity.player.Player target, LivingEntity owner) {
		if (target == owner) {
			return true; // your own grenade can hurt you
		}
		return getServer() != null && getServer().isPvpAllowed()
				&& com.projecthero.mod.hero.HeroConfig.get().abilityPvpDamage;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Fuse", fuse);
		tag.putBoolean("Stuck", stuck);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Fuse")) {
			fuse = tag.getInt("Fuse");
		}
		stuck = tag.getBoolean("Stuck");
	}
}

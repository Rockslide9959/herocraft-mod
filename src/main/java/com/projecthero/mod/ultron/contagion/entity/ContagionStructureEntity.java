package com.projecthero.mod.ultron.contagion.entity;

import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSkin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: what the Contagion's standing machines share -- the <b>Ultron Nexus</b>, the citadel's <b>Lift Engines</b> and
 * <b>Uplink Antennae</b>. Ultron robots (so his own never hurt them, and the electric x1.5 / heavy-hit x1.25 rules apply)
 * with no brain, no gravity and no movement: they are targets. Drawn by GeckoLib ({@link #modelName()} picks
 * {@code geo/ultron/<name>.geo.json}, its texture, glowmask and the looping {@code animation.<name>.idle}).
 *
 * <p>They carry the {@link Contagion#UNIT_TAG} tag, so a stopped Contagion removes them wherever they load.
 */
public abstract class ContagionStructureEntity extends UltronRobot implements GeoEntity {
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	protected ContagionStructureEntity(EntityType<? extends ContagionStructureEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		setPersistenceRequired();
		addTag(Contagion.UNIT_TAG);
	}

	/** The GeckoLib asset name under {@code ultron/}. */
	public abstract String modelName();

	@Override
	public UltronSkin skin() {
		return UltronSkin.DRONE; // never drawn as a player model
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public void aiStep() {
		super.aiStep();
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void knockback(double strength, double x, double z) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ANVIL_LAND;
	}

	@Override
	protected float getSoundVolume() {
		return 0.5f;
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server && !isRemoved()) {
			if (deathTime % 3 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 4, 0.5, getBbHeight() * 0.3, 0.5, 0.02);
			}
			if (deathTime >= 16) {
				UltronFx.wreck(server, position().add(0, getBbHeight() * 0.5, 0), 2.5);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		String clip = "animation." + modelName() + ".idle";
		controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(RawAnimation.begin().thenLoop(clip))));
	}
}

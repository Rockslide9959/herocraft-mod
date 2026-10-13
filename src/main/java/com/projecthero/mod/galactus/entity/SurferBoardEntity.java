package com.projecthero.mod.galactus.entity;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.item.GalactusItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: <b>the Silver Surfer's Board</b> -- the first-clear reward, a rideable flying surfboard. Summoned from its item
 * (right-click), stored back into it (sneak + right-click the board, owner only). Riding: it flies where you look while you
 * hold forward (jump climbs), at {@link GalactusConfig.Board#flySpeed}; sprint is a <b>Power Cosmic boost</b>
 * ({@link GalactusConfig.Board#boostSpeed}, a silver trail). Movement is simulated by the rider's client (a vehicle
 * controlled by its local player, like a boat or horse) and synced; the sprint key reaches it through {@link #clientBoost}.
 * Can't be hurt except by the void / {@code /kill}; never despawns.
 */
public class SurferBoardEntity extends Mob implements GeoEntity {
	/** Client hook: is the local rider holding sprint? (Set by the client; false on a dedicated server.) */
	public static BooleanSupplier clientBoost = () -> false;
	/** Client hook: is the local rider holding sneak-free "down" (unused) / jump? */
	public static BooleanSupplier clientClimb = () -> false;

	private UUID owner;
	private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

	public SurferBoardEntity(EntityType<? extends SurferBoardEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6);
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	public UUID owner() {
		return owner;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.isSecondaryUseActive()) {
			if (!level().isClientSide() && (owner == null || owner.equals(player.getUUID()))) {
				ItemStack item = new ItemStack(GalactusItems.SURFBOARD);
				if (!player.getInventory().add(item)) {
					player.drop(item, false);
				}
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
				discard();
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		if (!isVehicle() && (owner == null || owner.equals(player.getUUID()))) {
			if (!level().isClientSide()) {
				player.startRiding(this);
			}
			return InteractionResult.sidedSuccess(level().isClientSide());
		}
		return InteractionResult.PASS;
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		return new Vec3(player.xxa * 0.6, 0, player.zza);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return (float) GalactusConfig.board().flySpeed;
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		setRot(player.getYRot(), player.getXRot() * 0.5f);
		yRotO = yBodyRot = yHeadRot = getYRot();
		GalactusConfig.Board cfg = GalactusConfig.board();
		boolean boost = level().isClientSide() && clientBoost.getAsBoolean();
		double speed = boost ? cfg.boostSpeed : cfg.flySpeed;
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 want = look.scale(input.z * speed).add(side.scale(-input.x * speed * 0.6));
		if (level().isClientSide() && clientClimb.getAsBoolean()) {
			want = want.add(0, speed * 0.6, 0);
		}
		Vec3 v = getDeltaMovement();
		setDeltaMovement(v.add(want.subtract(v).scale(cfg.acceleration)));
		if (boost && level().isClientSide() && tickCount % 2 == 0) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.1, getZ(), -v.x * 0.2, -v.y * 0.2, -v.z * 0.2);
		}
	}

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance() && getControllingPassenger() != null) {
			move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.96));
			calculateEntityAnimation(false);
			return;
		}
		// unridden: hover in place, drifting to a stop
		setDeltaMovement(getDeltaMovement().scale(0.8));
		move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && tickCount % 5 == 0) {
			Vec3 v = getDeltaMovement();
			if (v.lengthSqr() > 0.6) {
				server.sendParticles(GalactusFx.SILVER, getX(), getY() + 0.1, getZ(), 2, 0.2, 0.02, 0.2, 0.0);
			}
		}
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, net.minecraft.world.entity.EntityDimensions dimensions, float scale) {
		return new Vec3(0, dimensions.height(), 0);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return geoCache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<SurferBoardEntity>(this, "main", GalactusAnims.TRANSITION,
				state -> state.setAndContinue(GalactusMob.raw(GalactusAnims.BOARD_IDLE))));
	}
}

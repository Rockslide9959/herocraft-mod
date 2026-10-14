package com.projecthero.mod.hulk.entity;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.4 (user): the Hulk in the mirror of a player's mind ({@code HulkMind}) -- the Hulk model standing behind the glass as
 * his reflection. Until the player right-clicks the mirror it mirrors him: its position is his position reflected across
 * the mirror plane ({@link #mirrorZ}, a plane of constant Z) and its body / head yaw are his yaw mirrored (180 - yaw),
 * pitch his pitch. The server moves it there every tick; the owner's client draws it at the exact reflection every frame
 * ({@code MirrorHulkRenderer}), so it never lags behind him. Once {@link #smashAt} is set it stops mirroring: it rears
 * back, punches through the glass, lunges and grabs him -- moved by the server, animated from that clock.
 *
 * <p>Never saved, never hurt, never pushed; it only exists while its owner's mind session does.
 */
public class MirrorHulkEntity extends LivingEntity implements GeoEntity {
	/** Ticks of the smash sequence (see {@code HulkMind}): walk up to the glass, rear back, punch, lunge, grab. */
	public static final int SMASH_APPROACH_END = 10;
	public static final int SMASH_PUNCH_AT = 20;
	public static final int SMASH_SHATTER_AT = 24;
	public static final int SMASH_GRAB_AT = 34;
	public static final int SMASH_RETURN_AT = 56;

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(MirrorHulkEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Float> MIRROR_Z = SynchedEntityData.defineId(MirrorHulkEntity.class, EntityDataSerializers.FLOAT);
	/** Game time the smash began; 0 while it is still mirroring. */
	private static final EntityDataAccessor<Long> SMASH_AT = SynchedEntityData.defineId(MirrorHulkEntity.class, EntityDataSerializers.LONG);

	private static final String P = "animation.hulk.";
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
	private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
	private static final RawAnimation SMASH_CHARGE = RawAnimation.begin().thenPlayAndHold(P + "hulk_smash_charge");
	private static final RawAnimation PUNCH = RawAnimation.begin().thenPlay(P + "power_punch");
	private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop(P + "charge");
	private static final RawAnimation PICKUP = RawAnimation.begin().thenPlay(P + "pickup");
	private static final RawAnimation HOLD = RawAnimation.begin().thenLoop(P + "hold");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	/** Client only: how fast the owner is walking (0..1), eased -- drives walk / idle while it mirrors him. */
	private float mirrorWalk;

	public MirrorHulkEntity(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
		setInvulnerable(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		// the Hulk's size (Banner x 1.8, as the Gamma power's SCALE_BONUS makes him); GeckoLib draws at the entity's scale
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.SCALE, 1.0 + com.projecthero.mod.hulk.HulkConfig.SCALE_BONUS);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(MIRROR_Z, 0.0f);
		builder.define(SMASH_AT, 0L);
	}

	public void setup(UUID owner, double mirrorZ) {
		this.entityData.set(OWNER, Optional.of(owner));
		this.entityData.set(MIRROR_Z, (float) mirrorZ);
	}

	public Optional<UUID> ownerId() {
		return this.entityData.get(OWNER);
	}

	public double mirrorZ() {
		return this.entityData.get(MIRROR_Z);
	}

	public long smashAt() {
		return this.entityData.get(SMASH_AT);
	}

	public void startSmash(long now) {
		this.entityData.set(SMASH_AT, now);
	}

	public boolean smashing() {
		return smashAt() != 0L;
	}

	/** Ticks since the smash began (with {@code partial}), or -1 while still mirroring. */
	public float smashTicks(float partial) {
		long at = smashAt();
		return at == 0L ? -1.0f : (level().getGameTime() - at) + partial;
	}

	/** The owner, if he is in this level (always, while the session is alive). */
	public Player owner() {
		return ownerId().map(id -> level().getPlayerByUUID(id)).orElse(null);
	}

	// ---------------------------------------------------------------- the reflection

	/** {@code p} reflected across the plane z = {@code mirrorZ}. */
	public static Vec3 reflect(Vec3 p, double mirrorZ) {
		return new Vec3(p.x, p.y, 2.0 * mirrorZ - p.z);
	}

	/**
	 * A yaw reflected in a mirror facing along Z: the look vector (-sin y, cos y) becomes (-sin y, -cos y), which is the
	 * yaw 180 - y.
	 */
	public static float reflectYaw(float yaw) {
		return Mth.wrapDegrees(180.0f - yaw);
	}

	/** Snaps this Hulk onto {@code player}'s reflection (server: every tick until the smash). */
	public void mirror(Player player) {
		double z = mirrorZ();
		Vec3 at = reflect(player.position(), z);
		float yaw = reflectYaw(player.getYRot());
		float body = reflectYaw(player.yBodyRot);
		float head = reflectYaw(player.getYHeadRot());
		setPos(at.x, at.y, at.z);
		setYRot(yaw);
		setXRot(player.getXRot());
		this.yBodyRot = body;
		this.yHeadRot = head;
	}

	public float mirrorWalk() {
		return this.mirrorWalk;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide) {
			if (this.tickCount % 5 == 0 && !com.projecthero.mod.hulk.HulkMind.hulkLive(ownerId().orElse(null), getUUID())) {
				discard(); // its owner's session is over
			}
			return;
		}
		if (!smashing()) {
			// how fast the owner is walking: the reflection walks with him
			Player owner = owner();
			float speed = 0.0f;
			if (owner != null) {
				double dx = owner.getX() - owner.xo;
				double dz = owner.getZ() - owner.zo;
				speed = (float) Math.min(1.0, Math.sqrt(dx * dx + dz * dz) * 4.0);
			}
			this.mirrorWalk += (speed - this.mirrorWalk) * 0.4f;
		}
	}

	// ---------------------------------------------------------------- inert

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(net.minecraft.world.entity.Entity entity) {
	}

	@Override
	public boolean isPickable() {
		return true; // right-clicking him through the hole works as well as the glass
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return NonNullList.withSize(4, ItemStack.EMPTY);
	}

	@Override
	public Iterable<ItemStack> getHandSlots() {
		return Collections.emptyList();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	// ---------------------------------------------------------------- animation

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::main));
	}

	private PlayState main(AnimationState<MirrorHulkEntity> state) {
		float t = smashTicks(0.0f);
		if (t < 0.0f) {
			return state.setAndContinue(this.mirrorWalk > 0.15f ? WALK : IDLE);
		}
		if (t < SMASH_APPROACH_END) {
			return state.setAndContinue(WALK);
		}
		if (t < SMASH_PUNCH_AT) {
			return state.setAndContinue(SMASH_CHARGE);
		}
		if (t < SMASH_SHATTER_AT + 2) {
			return state.setAndContinue(PUNCH);
		}
		if (t < SMASH_GRAB_AT) {
			return state.setAndContinue(CHARGE);
		}
		if (t < SMASH_GRAB_AT + 8) {
			return state.setAndContinue(PICKUP);
		}
		return state.setAndContinue(HOLD);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}

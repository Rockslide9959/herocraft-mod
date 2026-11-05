package com.projecthero.mod.omnitrix.alien.greymatter;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.mixin.LivingEntityAccessor;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 3 (user: "make it so that he builds a 1.5 block tall mech that he rides inside and uses to fight, the mech lasts 30
 * seconds or until it dies(make the hp based on the block its made from), 50 second cooldown, when player rides this mech the mech
 * takes all damage and player takes none"): Grey Matter's <b>Block Mech</b>, a real rideable creature built out of the block he
 * looked at -- its {@linkplain #material() block state} is synced and the client skins the hull, arms and fists with that block's
 * own textures; its health is {@link #hpFor}.
 *
 * <p>Movement is the Hard-Light Mech's: the pilot's client simulates it like a horse (the {@link #getControllingPassenger} rule)
 * and {@link #impulse} asks that client for a velocity. Everything else (the moves, the time limit, the damage hand-over) is
 * {@link GreyMatterKit}'s. Never saved.
 */
public class GreyMatterMechEntity extends Mob {
	public static final EntityType<GreyMatterMechEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("grey_matter_mech")),
			EntityType.Builder.of(GreyMatterMechEntity::new, MobCategory.MISC).sized(0.95f, 1.5f).fireImmune().noSave().clientTrackingRange(10).updateInterval(1)
					.build("grey_matter_mech"));

	/** The model and the hitbox are exactly this tall. */
	public static final float MECH_HEIGHT = 1.5f;
	/** The health rule: {@code HP_BASE + HP_PER_HARDNESS x the block's hardness}, at most {@link #HP_MAX} (unbreakable blocks too). */
	public static final float HP_BASE = 30f, HP_PER_HARDNESS = 20f, HP_MAX = 300f;
	/** Blocks per tick as the movement attribute (about 6 blocks a second). */
	public static final double WALK_SPEED = 0.27;
	public static final double JUMP_VELOCITY = 0.56;
	/** How high his backside sits above the mech's feet (inside the glass dome). */
	public static final float SEAT_Y = 1.21f;

	public static final int STATE_ASSEMBLING = 0, STATE_ACTIVE = 1, STATE_COLLAPSING = 2;
	public static final int ASSEMBLE_TICKS = 20, COLLAPSE_TICKS = 14;
	/** {@link #action()}: which arm move is playing. */
	public static final int ACT_NONE = 0, ACT_PUNCH_R = 1, ACT_PUNCH_L = 2, ACT_SLAM = 3, ACT_HOP = 4;

	private static final EntityDataAccessor<BlockState> MATERIAL = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.BLOCK_STATE);
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> STATE_AT = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Long> END_AT = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Long> ACTION_AT = SynchedEntityData.defineId(GreyMatterMechEntity.class, EntityDataSerializers.LONG);

	/** Which fist the next punch uses. */
	boolean leftNext;
	/** Server: the game time the Ground Slam's fists land (0 = none falling). */
	long slamAt;
	private int ownerMissing;

	public GreyMatterMechEntity(EntityType<? extends GreyMatterMechEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, HP_BASE).add(Attributes.MOVEMENT_SPEED, WALK_SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	/** v0.17.2 round 3: the mech's health for its block: 30 + 20 x hardness, capped at 300 (unbreakable blocks are the cap). */
	public static float hpFor(BlockState state, Level level, BlockPos pos) {
		float h = state.getDestroySpeed(level, pos);
		if (h < 0f) {
			return HP_MAX;
		}
		return Mth.clamp(HP_BASE + HP_PER_HARDNESS * h, HP_BASE, HP_MAX);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MATERIAL, Blocks.COBBLESTONE.defaultBlockState());
		builder.define(OWNER, Optional.empty());
		builder.define(STATE, STATE_ASSEMBLING);
		builder.define(STATE_AT, 0L);
		builder.define(END_AT, 0L);
		builder.define(ACTION, ACT_NONE);
		builder.define(ACTION_AT, -1000L);
	}

	void bind(ServerPlayer owner, BlockState material, float hp, long endAt) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		entityData.set(MATERIAL, material);
		entityData.set(END_AT, endAt);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth(hp);
		setState(STATE_ASSEMBLING);
	}

	/** The block it is built from. */
	public BlockState material() {
		return entityData.get(MATERIAL);
	}

	@Nullable
	public UUID ownerId() {
		return entityData.get(OWNER).orElse(null);
	}

	public boolean isOwner(@Nullable Entity e) {
		UUID o = ownerId();
		return o != null && e != null && o.equals(e.getUUID());
	}

	/** The mech {@code e} is piloting, or null. */
	@Nullable
	public static GreyMatterMechEntity mechOf(@Nullable Entity e) {
		return e != null && e.getVehicle() instanceof GreyMatterMechEntity m && m.isOwner(e) ? m : null;
	}

	public int state() {
		return entityData.get(STATE);
	}

	void setState(int s) {
		entityData.set(STATE, s);
		entityData.set(STATE_AT, level().getGameTime());
	}

	/** Ticks in this state (both sides read the shared game clock). */
	public long stateTicks() {
		return level().getGameTime() - entityData.get(STATE_AT);
	}

	public boolean active() {
		return state() == STATE_ACTIVE;
	}

	public long endAt() {
		return entityData.get(END_AT);
	}

	public int action() {
		return entityData.get(ACTION);
	}

	public long actionAt() {
		return entityData.get(ACTION_AT);
	}

	void act(int action) {
		entityData.set(ACTION, action);
		entityData.set(ACTION_AT, level().getGameTime());
	}

	/** A velocity for the pilot's client, which moves the mech (the server never sets a ridden vehicle's velocity itself). */
	void impulse(Vec3 velocity) {
		if (getControllingPassenger() instanceof ServerPlayer p && p.connection != null) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(getId(), velocity));
		}
	}

	// ---------------------------------------------------------------- riding

	@Override
	protected EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return EntityDimensions.fixed(0.95f, 1.5f).withEyeHeight(1.25f);
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
		return new Vec3(0.0, SEAT_Y, 0.0);
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && isOwner(passenger) && getPassengers().isEmpty();
	}

	@Override
	public LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p && isOwner(p) ? p : null;
	}

	@Override
	protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
		if (!active()) {
			return Vec3.ZERO;
		}
		float forward = player.zza;
		if (forward < 0f) {
			forward *= 0.5f;
		}
		return new Vec3(player.xxa * 0.7f, 0.0, forward);
	}

	@Override
	protected float getRiddenSpeed(Player player) {
		return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
	}

	@Override
	protected void tickRidden(Player player, Vec3 input) {
		super.tickRidden(player, input);
		setRot(player.getYRot(), 0f);
		yRotO = yBodyRot = yHeadRot = getYRot();
		if (level().isClientSide && active() && onGround() && ((LivingEntityAccessor) player).projecthero$isJumping()) {
			Vec3 v = getDeltaMovement();
			setDeltaMovement(v.x, JUMP_VELOCITY, v.z);
			this.hasImpulse = true;
		}
	}

	@Override
	public boolean canSprint() {
		return false;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || state() == STATE_COLLAPSING) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && (isOwner(attacker) || hasPassenger(attacker))) {
			return false; // never its own pilot (or anything of his)
		}
		if (attacker != null && getControllingPassenger() != null && com.projecthero.mod.squad.Squads.areAllies(getControllingPassenger(), attacker)
				&& !(getControllingPassenger() instanceof ServerPlayer sp && com.projecthero.mod.squad.Squads.friendlyFireOn(sp))) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel && !isRemoved()) {
			GreyMatterKit.mechDestroyed(this);
		}
	}

	@Override
	protected void tickDeath() {
		discard();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return material().getSoundType().getHitSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GENERIC_EXPLODE.value();
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState block) {
		var type = material().getSoundType();
		playSound(type.getStepSound(), 0.5f, 0.7f);
		playSound(SoundEvents.PISTON_CONTRACT, 0.08f, 1.8f);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		ServerLevel l = (ServerLevel) level();
		if (state() == STATE_ASSEMBLING && stateTicks() >= ASSEMBLE_TICKS) {
			setState(STATE_ACTIVE);
		}
		if (state() == STATE_COLLAPSING) {
			if (stateTicks() >= COLLAPSE_TICKS) {
				discard();
			}
			return;
		}
		if (!(getControllingPassenger() instanceof ServerPlayer)) {
			if (++ownerMissing > 10) {
				l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, material()), getX(), getY() + 0.8, getZ(), 30, 0.3, 0.4, 0.3, 0.1);
				discard();
			}
			return;
		}
		ownerMissing = 0;
	}

	/** Falls apart where it stands: the pilot is already out, the parts drop for {@link #COLLAPSE_TICKS}. */
	void collapse() {
		setState(STATE_COLLAPSING);
		noPhysics = false;
	}
}

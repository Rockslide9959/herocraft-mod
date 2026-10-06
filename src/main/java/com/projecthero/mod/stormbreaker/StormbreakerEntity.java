package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.entity.ModEntityTypes;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.hammer.MjolnirStatus;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFeedback;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorTargets;
import com.projecthero.mod.power.ThorVisuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.19: the thrown Stormbreaker. Two phases, server-authoritative and synced:
 * <ol>
 *   <li><b>Outbound</b> -- flies straight (light gravity) for up to {@link #MAX_RANGE} blocks, passing through up to
 *   {@link #MAX_PIERCE} living things for {@link #DAMAGE} each; the first one hit also gets a lightning bolt. A
 *   block, the range limit or the last pierce turns it round.</li>
 *   <li><b>Returning</b> -- like a Loyalty trident: phases through terrain straight back to the thrower, no key
 *   needed, and lands in the empty main hand, else the inventory, else at their feet.</li>
 * </ol>
 * The thrown stack rides on the entity ({@link #getItem}, saved by {@link ThrowableItemProjectile}) and the hand is
 * emptied on throw, so there is only ever one axe. Squad-safe through {@link ThorTargets#canAffect}, like Mjolnir.
 *
 * <h2>v0.15.3: resting -- Stormbreaker on the ground, like Mjolnir</h2>
 * A third, synced state ({@link #isResting}): the axe lying in the world. Every way it reaches the ground ends here --
 * a Q-drop, dropping it out of the inventory screen, a death drop, a dispenser (all promoted from the vanilla item on
 * its first tick by {@code ItemEntityMixin}, exactly as Mjolnir is), a throw whose thrower is gone (offline, dead,
 * another dimension), and a catch with nowhere to put it. Like a resting Mjolnir it never despawns or burns, falls a
 * little heavier than an item and stops dead where it lands (it floats up out of lava, so a freshly forged axe can be
 * fished out), is picked up only by right-clicking it ({@link #interact}) and only by the worthy, is tracked by
 * {@link MjolnirRegistry} as {@link MjolnirStatus#RESTING} (so R finds it and {@link #recall} lifts this very entity off
 * the ground), and a ghost a recall has already superseded deletes itself on its next tick.
 */
public class StormbreakerEntity extends ThrowableItemProjectile {
	public static final float DAMAGE = 20.0f;
	public static final int MAX_PIERCE = 4;
	public static final double MAX_RANGE = 40.0;
	public static final double THROW_SPEED = 2.4;
	private static final double OUTBOUND_GRAVITY = 0.01;
	private static final double RETURN_SPEED_MIN = 0.9;
	private static final double RETURN_SPEED_MAX = 3.2;
	private static final double RETURN_ACCELERATION = 0.12;
	private static final double CATCH_DISTANCE = 1.6;
	/** Client position updates closer than this are latency, not desync -- both sides run the same flight. */
	private static final double CORRECTION_TOLERANCE_SQR = 2.5 * 2.5;

	private static final EntityDataAccessor<Boolean> DATA_RETURNING =
			SynchedEntityData.defineId(StormbreakerEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_OWNER_ID =
			SynchedEntityData.defineId(StormbreakerEntity.class, EntityDataSerializers.INT);

	private static final String TAG_RETURNING = "Returning";
	private static final String TAG_FLOWN = "Flown";
	private static final String TAG_PIERCED = "Pierced";
	private static final String TAG_LIGHTNING = "LightningCalled";

	private static final String TAG_RECALLED = "Recalled";
	private static final String TAG_RESTING = "Resting";
	private static final String TAG_PICKUP_DELAY = "PickupDelay";

	private static final EntityDataAccessor<Boolean> DATA_RESTING =
			SynchedEntityData.defineId(StormbreakerEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.15.3: a resting axe falls like Mjolnir does -- a touch heavier than a dropped item (0.04). */
	private static final double RESTING_GRAVITY = 0.055;
	/** v0.15.3: in lava it rises (Stormbreaker is fire-proof and floats, like the item did) no faster than this. */
	private static final double LAVA_RISE = 0.04;
	/** v0.15.3: a dropped axe cannot be picked straight back up for this long (vanilla's item grace period). */
	public static final int PICKUP_DELAY_TICKS = 40;

	/** v0.15.3: ticks before a resting axe can be picked up again. Saved. */
	private int pickupDelay;

	private double flown;
	private int pierced;
	private boolean lightningCalled;
	/** v0.15.1: brought home by the call key (not a plain throw's return) -- it announces itself and takes the hand. */
	private boolean recalled;
	/** v0.15.1: whether this entity has told {@link MjolnirRegistry} where it is yet (first server tick). Not saved. */
	private boolean registered;
	private double returnSpeed = RETURN_SPEED_MIN;
	/** Everything struck on this outbound flight -- one hit each. Not saved: a reload simply allows a fresh hit. */
	private final Set<UUID> struck = new HashSet<>();

	public StormbreakerEntity(EntityType<? extends StormbreakerEntity> type, Level level) {
		super(type, level);
	}

	public StormbreakerEntity(Level level, LivingEntity owner) {
		super(ModEntityTypes.STORMBREAKER, owner, level);
		this.setItem(new ItemStack(ModItems.STORMBREAKER));
		this.setOwner(owner);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_RETURNING, false);
		builder.define(DATA_OWNER_ID, -1);
		builder.define(DATA_RESTING, false);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.STORMBREAKER;
	}

	// ---------------- throwing ----------------

	/**
	 * Throws the Stormbreaker in {@code player}'s main hand. Carries the real held stack onto the entity (never a fresh
	 * one) and empties the hand. Worthiness is the caller's check ({@link StormbreakerItem#use}).
	 */
	public static boolean throwFrom(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.STORMBREAKER)) {
			return false;
		}
		ItemStack thrown = stack.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

		StormbreakerEntity entity = new StormbreakerEntity(player.level(), player);
		entity.setItem(thrown);
		entity.launch(player, player.getLookAngle());
		player.level().addFreshEntity(entity);

		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Level level = player.level();
		level.playSound(null, x, y, z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.45f, 0.75f);
		level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.4f, 0.7f);
		level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.12f, 1.8f);
		ThorVisuals.anim(player, ThorFx.ANIM_THROW);
		ThorPowers.grantThrowFlightGrace(player);
		return true;
	}

	/** Places the axe just in front of the thrower's eyes and sends it along {@code direction}. */
	public void launch(Player player, Vec3 direction) {
		Vec3 aim = direction.normalize();
		this.setPos(player.getX() + aim.x * 0.8, player.getEyeY() - 0.15, player.getZ() + aim.z * 0.8);
		this.setDeltaMovement(aim.scale(THROW_SPEED));
		this.hasImpulse = true;
		this.flown = 0.0;
		this.pierced = 0;
		this.lightningCalled = false;
		this.struck.clear();
		setReturning(false);
	}

	// ---------------- state ----------------

	public boolean isReturning() {
		SynchedEntityData data = this.getEntityData();
		// reachable from the superclass constructor (getDefaultGravity) before the synched data exists
		return data != null && data.get(DATA_RETURNING);
	}

	private void setReturning(boolean returning) {
		if (!level().isClientSide()) {
			this.entityData.set(DATA_RETURNING, returning);
		}
	}

	public int piercedCount() {
		return pierced;
	}

	@Override
	public void setOwner(Entity owner) {
		super.setOwner(owner);
		if (!level().isClientSide()) {
			this.entityData.set(DATA_OWNER_ID, owner == null ? -1 : owner.getId());
		}
	}

	/** The thrower on either side ({@code Projectile.getOwner} only resolves on the server). */
	private Player resolveOwner() {
		if (this.getOwner() instanceof Player player) {
			return player;
		}
		int id = this.entityData.get(DATA_OWNER_ID);
		if (id >= 0 && level().getEntity(id) instanceof Player player) {
			return player;
		}
		return null;
	}

	/** Turn for home. Idempotent. */
	public void beginReturn() {
		if (isReturning()) {
			return;
		}
		setResting(false);
		setReturning(true);
		this.returnSpeed = RETURN_SPEED_MIN;
		this.hasImpulse = true;
		noteToRegistry();
	}

	public boolean wasRecalled() {
		return recalled;
	}

	/**
	 * v0.15.1: the call key (via {@link com.projecthero.mod.hammer.MjolnirRecall}) brings this axe home -- mid-throw
	 * or otherwise. The summoner becomes the thrower it homes toward, exactly like Mjolnir's recall.
	 */
	public void recall(Player summoner) {
		this.setOwner(summoner);
		this.recalled = true;
		beginReturn();
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 0.6f, 0.6f);
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.5f, 1.3f);
		}
	}

	/**
	 * v0.15.1: a Stormbreaker the call key has just pulled out of somewhere it could not fly from on its own (a
	 * chest, another player, an unloaded chunk, the ground) -- it appears at {@code pos} already on its return flight.
	 */
	public static StormbreakerEntity createReturning(Level level, Player owner, ItemStack stack, Vec3 pos) {
		StormbreakerEntity entity = new StormbreakerEntity(ModEntityTypes.STORMBREAKER, level);
		entity.setItem(stack.copy());
		entity.setPos(pos.x, pos.y, pos.z);
		entity.setOwner(owner);
		entity.recalled = true;
		entity.beginReturn();
		return entity;
	}

	// ---------------- v0.15.3: resting ----------------

	/**
	 * v0.15.3: a Stormbreaker lying in the world -- a drop of any kind (see the class javadoc). Carries the real stack
	 * (identity, owner, generation, every component) and keeps the toss velocity it was handed, so a Q-drop arcs
	 * forward like any dropped item before it lands. Starts with the pickup grace period.
	 */
	public static StormbreakerEntity createResting(Level level, Entity owner, ItemStack stack, Vec3 pos, Vec3 velocity) {
		StormbreakerEntity entity = new StormbreakerEntity(ModEntityTypes.STORMBREAKER, level);
		entity.setItem(stack.copy());
		entity.setPos(pos.x, pos.y, pos.z);
		if (owner != null) {
			entity.setOwner(owner);
		}
		// lie along the way it was tossed (or the dropper's facing) -- the renderer keeps this yaw
		double horizontal = velocity.horizontalDistance();
		float yaw = horizontal > 1.0E-3
				? (float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI))
				: owner != null ? -owner.getYRot() : level.getRandom().nextFloat() * 360.0f;
		entity.setYRot(yaw);
		entity.yRotO = yaw;
		entity.settleAsResting(velocity);
		return entity;
	}

	public boolean isResting() {
		SynchedEntityData data = this.getEntityData();
		// reachable from the superclass constructor (getDefaultGravity) before the synched data exists
		return data != null && data.get(DATA_RESTING);
	}

	private void setResting(boolean resting) {
		if (!level().isClientSide()) {
			this.entityData.set(DATA_RESTING, resting);
		}
	}

	public int pickupDelay() {
		return pickupDelay;
	}

	/** Lies down where it is (keeping {@code velocity} for the fall), the same entity -- never swapped for an item. */
	private void settleAsResting(Vec3 velocity) {
		setReturning(false);
		setResting(true);
		this.recalled = false;
		this.flown = 0.0;
		this.pierced = 0;
		this.struck.clear();
		this.pickupDelay = PICKUP_DELAY_TICKS;
		this.noPhysics = false;
		this.setDeltaMovement(velocity);
		noteToRegistry();
	}

	// ---------------- v0.15.1: ownership tracking ----------------

	/** Same as Mjolnir: a copy a recall has already superseded deletes itself rather than becoming a second axe. */
	private boolean discardIfStale() {
		if (!(level() instanceof ServerLevel serverLevel) || !MjolnirRegistry.get(serverLevel).isStale(this.getItem())) {
			return false;
		}
		this.discard();
		return true;
	}

	/** Writes through a working copy + {@link #setItem} so the synched stack is marked dirty (see MjolnirEntity). */
	private void noteToRegistry() {
		if (!registered || !(level() instanceof ServerLevel serverLevel) || this.isRemoved()) {
			return;
		}
		ItemStack working = this.getItem().copy();
		if (!working.is(ModItems.STORMBREAKER)) {
			return;
		}
		MjolnirRegistry registry = MjolnirRegistry.get(serverLevel);
		registry.identify(working);
		registry.noteEntity(working, this, isResting() ? MjolnirStatus.RESTING
				: isReturning() ? MjolnirStatus.RETURNING : MjolnirStatus.THROWN);
		this.setItem(working);
	}

	@Override
	protected double getDefaultGravity() {
		if (isResting()) {
			return isInLava() ? -RESTING_GRAVITY : RESTING_GRAVITY;
		}
		return isReturning() ? 0.0 : OUTBOUND_GRAVITY;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			// v0.15.1: staleness before registration, exactly as MjolnirEntity -- registering would stamp a ghost current
			if (discardIfStale()) {
				return;
			}
			if (!registered) {
				registered = true;
				noteToRegistry();
			}
		}
		if (isResting()) {
			tickResting();
			return;
		}
		boolean returning = isReturning();
		if (returning) {
			if (!steerHome()) {
				return;
			}
		} else if (!level().isClientSide()) {
			flown += this.getDeltaMovement().length();
			sweepPath();
			if (!isReturning() && flown >= MAX_RANGE) {
				beginReturn();
			}
		}
		// Terrain never stops the way home.
		this.noPhysics = isReturning();

		super.tick();

		spawnFlightEffects();
	}

	/** @return false if the axe is done (caught or dropped) and nothing else should run this tick. */
	private boolean steerHome() {
		Player owner = resolveOwner();
		boolean server = !level().isClientSide();
		if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != this.level()) {
			if (server) {
				// v0.15.3: it lies down right here as this same entity, still tracked and callable (it used to become an item)
				settleAsResting(Vec3.ZERO);
			}
			return !server;
		}
		Vec3 target = owner.getEyePosition().subtract(0.0, 0.4, 0.0);
		Vec3 toTarget = target.subtract(this.position());
		double distance = toTarget.length();
		if (distance < CATCH_DISTANCE) {
			if (server) {
				catchBy(owner);
				return false;
			}
			this.setDeltaMovement(Vec3.ZERO);
			return true;
		}
		returnSpeed = Math.min(RETURN_SPEED_MAX, returnSpeed + RETURN_ACCELERATION);
		double speed = distance < 4.0 ? Math.min(returnSpeed, Math.max(0.6, distance * 0.6)) : returnSpeed;
		this.setDeltaMovement(toTarget.scale(speed / distance));
		return true;
	}

	/** Lets the client keep simulating the same flight instead of snapping to every (slightly stale) update. */
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (isResting()) {
			// nothing to simulate -- take the server's word for it, rotation included
			super.lerpTo(x, y, z, yRot, xRot, steps);
			return;
		}
		if (this.distanceToSqr(x, y, z) < CORRECTION_TOLERANCE_SQR) {
			return;
		}
		super.lerpTo(x, y, z, yRot, xRot, steps);
	}

	// ---------------- hitting things ----------------

	/**
	 * Strikes every eligible living thing along this tick's path (up to the first solid block), nearest first. Done by
	 * hand because the projectile's own collision only ever reports the single nearest entity per tick, and a 2.4
	 * block/tick axe would otherwise sail straight past the second mob in a tight group.
	 */
	private void sweepPath() {
		Vec3 start = this.position();
		Vec3 velocity = this.getDeltaMovement();
		Vec3 end = start.add(velocity);
		BlockHitResult wall = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB area = this.getBoundingBox().expandTowards(velocity).inflate(1.0);
		List<Entity> candidates = new ArrayList<>(level().getEntities(this, area, this::canHitEntity));
		candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(start)));
		for (Entity candidate : candidates) {
			AABB box = candidate.getBoundingBox().inflate(0.35);
			if (box.contains(start) || box.clip(start, end).isPresent()) {
				strike(candidate);
				if (isReturning()) {
					return;
				}
			}
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		if (level().isClientSide() || isReturning() || isResting() || !(target instanceof LivingEntity)
				|| !super.canHitEntity(target)) {
			return false;
		}
		if (struck.contains(target.getUUID())) {
			return false;
		}
		Player owner = resolveOwner();
		if (target == owner) {
			return false;
		}
		// the thrower's squad and pets, players without PvP, armour stands: it flies straight past them
		return owner == null || ThorTargets.canAffect(owner, target);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		// Deliberately no super call and no stop: the axe pierces. Damage is all in strike().
		if (!level().isClientSide() && !isResting()) {
			strike(result.getEntity());
		}
	}

	/** One pierce: 20 damage, a shove, lightning on the very first victim; the fourth turns it round. */
	private void strike(Entity target) {
		if (isReturning() || !struck.add(target.getUUID())) {
			return;
		}
		Player owner = resolveOwner();
		var source = level().damageSources().trident(this, owner == null ? this : owner);
		if (target.hurt(source, DAMAGE)) {
			if (target instanceof LivingEntity living) {
				Vec3 push = this.getDeltaMovement().normalize();
				living.knockback(0.6, -push.x, -push.z);
			}
			level().playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.7f, 1.4f);
		}
		if (!lightningCalled && level() instanceof ServerLevel serverLevel) {
			lightningCalled = true;
			callLightning(serverLevel, owner, target.position());
		}
		pierced++;
		if (pierced >= MAX_PIERCE) {
			beginReturn();
		}
	}

	/** Visual-only, like every Thor bolt in the mod: the strike is the axe's 20 damage, the bolt is the spectacle. */
	private static void callLightning(ServerLevel level, Player owner, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(pos.x, pos.y, pos.z);
			bolt.setVisualOnly(true);
			if (owner instanceof ServerPlayer serverPlayer) {
				bolt.setCause(serverPlayer);
			}
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 30, 0.4, 0.8, 0.4, 0.15);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		if (isResting()) {
			landOn(result);
			return;
		}
		if (isReturning()) {
			// phasing home through terrain; never pokes buttons, targets or dripstone on the way
			return;
		}
		super.onHitBlock(result);
		if (!level().isClientSide()) {
			level().playSound(null, result.getBlockPos(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.35f, 1.6f);
			if (level() instanceof ServerLevel serverLevel) {
				Vec3 at = result.getLocation();
				serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.2, 0.2, 0.2, 0.08);
			}
			beginReturn();
		}
	}

	// ---------------- landing ----------------

	private void catchBy(Player player) {
		ItemStack stack = this.getItem().copy();
		if (recalled) {
			// v0.15.1: called while holding Mjolnir, the axe takes the hand and the hammer steps back into the pack
			MjolnirRecall.stowOtherWeapon(player, ThorWeapon.STORMBREAKER);
		}
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			if (recalled) {
				// a deliberate call: like Mjolnir, it goes into the hand anyway and what was held is dropped
				ItemStack displaced = player.getMainHandItem();
				player.setItemInHand(InteractionHand.MAIN_HAND, stack);
				player.drop(displaced, false);
			} else {
				// v0.15.3: it waits at their feet as a resting axe (still tracked and callable), not as an item
				ThorFeedback.recallInventoryFull(player, ThorWeapon.STORMBREAKER);
				this.setPos(player.getX(), player.getY(), player.getZ());
				settleAsResting(Vec3.ZERO);
				return;
			}
		}
		if (level() instanceof ServerLevel serverLevel && stack.get(ModDataComponents.HAMMER_ID) != null) {
			MjolnirRegistry.get(serverLevel).noteCarried(stack, player, player.getMainHandItem() == stack);
		}
		if (recalled) {
			ThorFeedback.recallArrived(player, ThorWeapon.STORMBREAKER);
		}
		level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.1f);
		level().playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.5f, 1.25f);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() - 0.3, player.getZ(),
					10, 0.3, 0.3, 0.3, 0.02);
		}
		this.discard();
	}

	// ---------------- v0.15.3: lying in the world ----------------

	/**
	 * Falls and settles like a resting Mjolnir: the projectile tick moves it, {@link #landOn} stops it dead on the floor,
	 * and its heading is held steady (a stopped projectile's {@code updateRotation} would otherwise swing it round to a
	 * fixed compass direction). In lava it rises gently to the surface instead.
	 */
	private void tickResting() {
		if (pickupDelay > 0) {
			pickupDelay--;
		}
		this.noPhysics = false;
		if (isInLava()) {
			Vec3 v = this.getDeltaMovement();
			this.setDeltaMovement(v.x * 0.9, Math.min(v.y, LAVA_RISE), v.z * 0.9);
		}
		float yaw = this.getYRot();
		float pitch = this.getXRot();
		super.tick();
		this.setYRot(yaw);
		this.setXRot(pitch);
		this.yRotO = yaw;
		this.xRotO = pitch;
	}

	/**
	 * A resting axe meeting a block: on a floor it is set down exactly on the surface and stops; against a wall it loses
	 * its sideways speed and keeps falling; under a ceiling it stops rising. Never a sound -- it is a drop, not a throw.
	 */
	private void landOn(BlockHitResult result) {
		Vec3 v = this.getDeltaMovement();
		switch (result.getDirection()) {
			case UP -> {
				Vec3 at = result.getLocation();
				this.setPos(at.x, at.y, at.z);
				this.setDeltaMovement(Vec3.ZERO);
			}
			case DOWN -> this.setDeltaMovement(v.x, Math.min(0.0, v.y), v.z);
			default -> this.setDeltaMovement(0.0, v.y, 0.0);
		}
	}

	/** A resting axe can be crosshair-targeted, so it can be right-clicked up. */
	@Override
	public boolean isPickable() {
		return !isRemoved() && isResting();
	}

	/** Whether {@code player} may lift a resting axe: creative, or worthy and not the Hulk -- Mjolnir's rule. */
	public static boolean canLift(Player player) {
		return com.projecthero.mod.worthiness.WorthinessEnforcer.bypassesWorthiness(player)
				|| (com.projecthero.mod.worthiness.Worthiness.isWorthy(player)
						&& !com.projecthero.mod.hulk.Hulk.isHulk(player));
	}

	/**
	 * Right-click picks a resting axe up -- like Mjolnir, it never jumps into your pack as you walk past. Only the worthy
	 * can lift it off the ground; anyone else gets Mjolnir's "it will not budge" clang. Main hand if free, else the pack;
	 * with no room at all it stays where it is.
	 */
	@Override
	public net.minecraft.world.InteractionResult interact(Player player, InteractionHand hand) {
		if (level().isClientSide()) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		if (!isResting() || pickupDelay > 0 || this.isRemoved()) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (!canLift(player)) {
			com.projecthero.mod.worthiness.WorthinessEnforcer.playRejectionFeedback(player, this.position());
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		pickUp(player);
		return net.minecraft.world.InteractionResult.SUCCESS;
	}

	/** Puts the axe in {@code player}'s empty main hand, else the pack. @return false (and nothing moves) if there is no room. */
	public boolean pickUp(Player player) {
		ItemStack stack = this.getItem().copy();
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			return false;
		}
		if (level() instanceof ServerLevel serverLevel && stack.get(ModDataComponents.HAMMER_ID) != null) {
			MjolnirRegistry.get(serverLevel).noteCarried(stack, player, player.getMainHandItem() == stack);
		}
		level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.1f);
		this.discard();
		return true;
	}

	// ---------------- presentation ----------------

	private void spawnFlightEffects() {
		if (!(level() instanceof ServerLevel serverLevel) || this.isRemoved() || isResting()) {
			return;
		}
		if (this.tickCount % 2 == 0) {
			Vec3 behind = this.position().subtract(this.getDeltaMovement().scale(0.4));
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, behind.x, behind.y, behind.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (this.tickCount % 5 == 0) {
			serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3f, 1.2f);
		}
	}

	// ---------------- persistence ----------------
	// The stack ("Item") and the thrower ("Owner") are saved by the superclasses.

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean(TAG_RETURNING, isReturning());
		tag.putDouble(TAG_FLOWN, flown);
		tag.putInt(TAG_PIERCED, pierced);
		tag.putBoolean(TAG_LIGHTNING, lightningCalled);
		tag.putBoolean(TAG_RECALLED, recalled);
		tag.putBoolean(TAG_RESTING, isResting());
		tag.putInt(TAG_PICKUP_DELAY, pickupDelay);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_RETURNING, tag.getBoolean(TAG_RETURNING));
		flown = tag.getDouble(TAG_FLOWN);
		pierced = tag.getInt(TAG_PIERCED);
		lightningCalled = tag.getBoolean(TAG_LIGHTNING);
		recalled = tag.getBoolean(TAG_RECALLED);
		this.entityData.set(DATA_RESTING, tag.getBoolean(TAG_RESTING));
		pickupDelay = tag.getInt(TAG_PICKUP_DELAY);
	}
}

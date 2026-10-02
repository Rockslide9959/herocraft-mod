package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.entity.ModEntityTypes;
import com.projecthero.mod.item.ModItems;
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
import net.minecraft.world.entity.item.ItemEntity;
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
 * emptied on throw, so there is only ever one axe. A thrower who is gone (offline, dead, another dimension) gets it
 * dropped as an item that never despawns. Squad-safe through {@link ThorTargets#canAffect}, like Mjolnir.
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

	private double flown;
	private int pierced;
	private boolean lightningCalled;
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
		setReturning(true);
		this.returnSpeed = RETURN_SPEED_MIN;
		this.hasImpulse = true;
	}

	@Override
	protected double getDefaultGravity() {
		return isReturning() ? 0.0 : OUTBOUND_GRAVITY;
	}

	// ---------------- tick ----------------

	@Override
	public void tick() {
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
				dropAsItem(this.position());
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
		if (level().isClientSide() || isReturning() || !(target instanceof LivingEntity) || !super.canHitEntity(target)) {
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
		if (!level().isClientSide()) {
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
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			ItemEntity dropped = player.drop(stack, false);
			if (dropped != null) {
				dropped.setUnlimitedLifetime();
			}
		}
		level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 1.1f);
		level().playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.5f, 1.25f);
		if (level() instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() - 0.3, player.getZ(),
					10, 0.3, 0.3, 0.3, 0.02);
		}
		this.discard();
	}

	/** The thrower is gone: leave the axe where it is, as an item that never despawns (and, being fire-resistant, never burns). */
	private void dropAsItem(Vec3 at) {
		ItemEntity item = new ItemEntity(level(), at.x, at.y, at.z, this.getItem().copy());
		item.setUnlimitedLifetime();
		item.setDefaultPickUpDelay();
		level().addFreshEntity(item);
		this.discard();
	}

	// ---------------- presentation ----------------

	private void spawnFlightEffects() {
		if (!(level() instanceof ServerLevel serverLevel) || this.isRemoved()) {
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
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_RETURNING, tag.getBoolean(TAG_RETURNING));
		flown = tag.getDouble(TAG_FLOWN);
		pierced = tag.getInt(TAG_PIERCED);
		lightningCalled = tag.getBoolean(TAG_LIGHTNING);
	}
}

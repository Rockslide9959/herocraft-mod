package com.projecthero.mod.supersoldier.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * C: the thrown shield (v0.14.8 as G; v0.14.9 it is the real shield from his hand). It flies fast at the enemy he aimed
 * at (or straight on); every enemy it strikes takes the hit and the shield ricochets into the nearest other enemy within
 * {@link SuperSoldierConfig#SHIELD_RICOCHET_RANGE} (line of sight preferred; a close one is taken even round a corner,
 * and once it has picked a target it homes on it through anything in the way) until its hits run out. Then it turns
 * round and flies back to the thrower -- straight through blocks -- and is caught.
 *
 * <h2>The item is never lost or duplicated</h2>
 * The thrown {@link ItemStack} is <em>moved</em> out of his hand into this entity (and synced, so the renderer draws the
 * real thing). It goes back on the catch -- the same stack object, durability / banner / enchantments intact -- into the
 * hand it left if that hand is free, otherwise his inventory, otherwise at his feet. If the thrower is gone (logged
 * out, dead) the shield drops as an item where it is; it is saved with the chunk (stack included) so a server stop
 * mid-flight cannot eat it, and a reloaded shield whose thrower is not back yet drops itself the same way. Killing the
 * entity (/kill) drops the stack too.
 *
 * <p>Server-authoritative: it moves itself every tick and is tracked every tick; the client only extrapolates along the
 * synced velocity between updates ({@code SoldierShieldRenderer} spins it flat like a discus).
 */
public class SoldierShieldEntity extends Entity {
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.ITEM_STACK);
	/** v0.17.5: the thrower's entity id and the hand it left (synced: the client animates his throw, reach and catch). */
	private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> OFF_HAND = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.BOOLEAN);
	/** v0.17.7: thrown by the Shield Storm ultimate (synced: the bright red / white / blue trail). */
	private static final EntityDataAccessor<Boolean> STORM = SynchedEntityData.defineId(SoldierShieldEntity.class,
			EntityDataSerializers.BOOLEAN);

	// server-only
	private UUID ownerId;
	private Vec3 origin;
	private int targetId = -1;
	private int hitsLeft = SuperSoldierConfig.SHIELD_MAX_HITS;
	private float damage = SuperSoldierConfig.SHIELD_DAMAGE;
	private double range = SuperSoldierConfig.SHIELD_RANGE;
	/** v0.17.7: how far it looks for the next ricochet, and how fast it flies (the Shield Storm raises both). */
	private double ricochetRange = SuperSoldierConfig.SHIELD_RICOCHET_RANGE;
	private double speed = SuperSoldierConfig.SHIELD_SPEED;
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	/** v0.17.5: thrown from the Battle Suit's shield slot -- it goes back into the slot (the hand only if the slot is gone). */
	private boolean toSlot;
	/** The thrown shield itself. Empty once it has been handed back or dropped. */
	private ItemStack stack = ItemStack.EMPTY;
	private final Set<Integer> hit = new HashSet<>();

	public SoldierShieldEntity(EntityType<? extends SoldierShieldEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * Throw {@code thrown} (already taken out of {@code hand}) from {@code from} along {@code dir}; {@code firstTarget}
	 * (may be null) is homed on at once. {@code adamantium} picks the full-strength numbers.
	 */
	public static SoldierShieldEntity launch(ServerPlayer owner, Vec3 from, Vec3 dir, ItemStack thrown, InteractionHand hand,
			boolean adamantium, LivingEntity firstTarget) {
		SoldierShieldEntity shield = new SoldierShieldEntity(SuperSoldierEntities.SOLDIER_SHIELD, owner.level());
		shield.ownerId = owner.getUUID();
		shield.origin = from;
		shield.hand = hand;
		shield.stack = thrown;
		shield.entityData.set(ITEM, thrown);
		shield.entityData.set(OWNER_ID, owner.getId());
		shield.entityData.set(OFF_HAND, hand == InteractionHand.OFF_HAND);
		shield.damage = adamantium ? SuperSoldierConfig.SHIELD_DAMAGE : SuperSoldierConfig.NORMAL_SHIELD_DAMAGE;
		shield.hitsLeft = adamantium ? SuperSoldierConfig.SHIELD_MAX_HITS : SuperSoldierConfig.NORMAL_SHIELD_MAX_HITS;
		shield.range = adamantium ? SuperSoldierConfig.SHIELD_RANGE : SuperSoldierConfig.NORMAL_SHIELD_RANGE;
		shield.setPos(from.x, from.y, from.z);
		Vec3 aim = firstTarget != null ? firstTarget.getBoundingBox().getCenter().subtract(from) : dir;
		if (firstTarget != null) {
			shield.targetId = firstTarget.getId();
		}
		shield.setDeltaMovement(aim.normalize().scale(SuperSoldierConfig.SHIELD_SPEED));
		owner.level().addFreshEntity(shield);
		return shield;
	}

	/** v0.17.5: this throw came from the Battle Suit's shield slot and goes home into it. */
	public void returnToSlot() {
		this.toSlot = true;
	}

	/** v0.17.5: thrown from (and going back to) the shield slot (server only). */
	public boolean returnsToSlot() {
		return toSlot;
	}

	/**
	 * v0.17.7: the Shield Storm ultimate -- its own damage, bounces, ricochet reach and speed, the storm trail, and the catch
	 * hands over to {@link SuperSoldierAbilities#stormCaught} (the dive and the slam).
	 */
	public void storm(float damage, int hits, double ricochet, double speed) {
		this.damage = damage;
		this.hitsLeft = hits;
		this.range = Math.max(this.range, ricochet);
		this.ricochetRange = ricochet;
		this.speed = speed;
		this.entityData.set(STORM, true);
		setDeltaMovement(getDeltaMovement().normalize().scale(speed));
	}

	/** v0.17.7: thrown by the Shield Storm (either side). */
	public boolean isStorm() {
		return entityData.get(STORM);
	}

	/** v0.17.7: hand it straight back to the thrower now (server; a Storm that ran too long). */
	public void recall() {
		if (!level().isClientSide && !isRemoved()) {
			ServerPlayer owner = owner();
			if (owner != null && owner.isAlive()) {
				deliver(owner);
			} else {
				dropHere();
			}
		}
	}

	/** v0.16.1: a stronger shield (the Vibranium Shield): its own damage, bounces and range. */
	public void upgrade(float damage, int hits, double range) {
		this.damage = damage;
		this.hitsLeft = hits;
		this.range = range;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(RETURNING, false);
		builder.define(ITEM, ItemStack.EMPTY);
		builder.define(OWNER_ID, -1);
		builder.define(OFF_HAND, false);
		builder.define(STORM, false);
	}

	public boolean isReturning() {
		return entityData.get(RETURNING);
	}

	/** The shield being thrown (synced: what the renderer draws). */
	public ItemStack getItem() {
		return entityData.get(ITEM);
	}

	/** v0.17.5: the thrower's entity id on either side (-1 unknown) -- for the client's throw / catch animation. */
	public int ownerEntityId() {
		return entityData.get(OWNER_ID);
	}

	/** v0.17.5: thrown from (and going back to) his off hand. */
	public boolean thrownFromOffHand() {
		return entityData.get(OFF_HAND);
	}

	/** How many more enemies this throw can still hit (tests). */
	public int hitsLeft() {
		return hitsLeft;
	}

	public int hitCount() {
		return hit.size();
	}

	public float damagePerHit() {
		return damage;
	}

	/** The thrower's UUID (server only; null on the client). */
	public UUID ownerId() {
		return ownerId;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			Vec3 v = getDeltaMovement();
			setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
			// v0.17.5: a thin streak in the shield's own colours (the renderer adds the see-through after-images)
			if (isStorm()) {
				// v0.17.7: the Shield Storm -- a bright, thick red / white / blue ribbon along the whole tick's travel, and sparkle
				for (int i = 0; i < 3; i++) {
					double k = i / 3.0;
					Vector3f col = switch ((tickCount + i) % 3) {
					case 0 -> new Vector3f(1.0f, 0.15f, 0.15f);
					case 1 -> new Vector3f(1.0f, 1.0f, 1.0f);
					default -> new Vector3f(0.2f, 0.4f, 1.0f);
					};
					level().addParticle(new DustParticleOptions(col, 1.4f), getX() - v.x * k, getY() - v.y * k, getZ() - v.z * k, 0, 0, 0);
				}
				if (tickCount % 2 == 0) {
					level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), -v.x * 0.05, -v.y * 0.05, -v.z * 0.05);
				}
			} else if (tickCount % 2 == 0) {
				level().addParticle(new DustParticleOptions(trailColour(tickCount / 2), 0.6f), getX() - v.x * 0.5, getY() - v.y * 0.5,
						getZ() - v.z * 0.5, 0, 0, 0);
			}
			return;
		}
		if (stack.isEmpty()) {
			discard(); // nothing to carry (a stale entity)
			return;
		}
		ServerPlayer owner = owner();
		if (owner != null && entityData.get(OWNER_ID) != owner.getId()) {
			entityData.set(OWNER_ID, owner.getId()); // a reloaded shield: the client learns its thrower again
		}
		if (owner == null || !owner.isAlive()) {
			dropHere(); // logged out / dead: the shield falls where it is
			return;
		}
		if (owner.level() != level() || tickCount > SuperSoldierConfig.SHIELD_MAX_LIFE || !SuperSoldier.hasPower(owner)) {
			deliver(owner); // another dimension, a lost power, or a throw that never came home: straight back to him
			return;
		}
		if (isReturning()) {
			tickReturn(owner);
			return;
		}
		Vec3 pos = position();
		Vec3 vel = getDeltaMovement();
		boolean homing = false;
		if (targetId >= 0) {
			Entity t = level().getEntity(targetId);
			if (t instanceof LivingEntity living && living.isAlive() && !hit.contains(t.getId())) {
				vel = living.getBoundingBox().getCenter().subtract(pos).normalize().scale(speed);
				homing = true;
			} else {
				targetId = -1;
				if (!pickNext(owner)) {
					startReturn();
					tickReturn(owner);
					return;
				}
				vel = getDeltaMovement();
				homing = true;
			}
		}
		Vec3 next = pos.add(vel);
		LivingEntity struck = firstEntity(owner, pos, next);
		// homing on a chosen enemy it flies through anything; flying free it glances off walls
		BlockHitResult block = homing ? null
				: level().clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		boolean blockFirst = block != null && block.getType() != HitResult.Type.MISS
				&& (struck == null || block.getLocation().distanceToSqr(pos) < struck.getBoundingBox().getCenter().distanceToSqr(pos));
		setDeltaMovement(vel);
		if (struck != null && !blockFirst) {
			Vec3 at = struck.getBoundingBox().getCenter();
			setPos(at.x, at.y, at.z);
			onStrike(owner, struck);
			return;
		}
		if (blockFirst) {
			Vec3 at = block.getLocation().subtract(vel.normalize().scale(0.2));
			setPos(at.x, at.y, at.z);
			level().playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.9f);
			((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
			((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.35); // v0.17.5: ricochet sparks
			if (!pickNext(owner)) {
				startReturn();
			}
			return;
		}
		setPos(next.x, next.y, next.z);
		if (!homing && origin != null && next.distanceTo(origin) > range) {
			startReturn();
		}
	}

	/** v0.17.5: the streak colours -- red / white / blue for the painted shields, steel for Adamantium, violet-blue for Vibranium. */
	private Vector3f trailColour(int i) {
		ItemStack s = getItem();
		if (s.getItem() instanceof com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem) {
			return i % 2 == 0 ? new Vector3f(0.45f, 0.4f, 1.0f) : new Vector3f(0.85f, 0.85f, 1.0f);
		}
		if (s.is(com.projecthero.mod.supersoldier.item.SuperSoldierItems.ADAMANTIUM_SHIELD)) {
			return i % 2 == 0 ? new Vector3f(0.75f, 0.78f, 0.82f) : new Vector3f(0.45f, 0.48f, 0.53f);
		}
		return switch (i % 3) {
		case 0 -> new Vector3f(0.75f, 0.12f, 0.15f);
		case 1 -> new Vector3f(0.92f, 0.92f, 0.9f);
		default -> new Vector3f(0.15f, 0.25f, 0.65f);
		};
	}

	private ServerPlayer owner() {
		if (ownerId == null) {
			return null;
		}
		if (level().getPlayerByUUID(ownerId) instanceof ServerPlayer sp) {
			return sp;
		}
		return level().getServer() == null ? null : level().getServer().getPlayerList().getPlayer(ownerId);
	}

	private void onStrike(ServerPlayer owner, LivingEntity target) {
		hit.add(target.getId());
		hitsLeft--;
		targetId = -1;
		Vec3 from = position().subtract(getDeltaMovement());
		SuperSoldierAbilities.strike(owner, target, damage, from, SuperSoldierConfig.SHIELD_KNOCKBACK, 0.1);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.7f);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
		((ServerLevel) level()).sendParticles(new DustParticleOptions(new Vector3f(0.3f, 0.5f, 1.0f), 1.2f),
				getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.0);
		// v0.17.5: the ricochet -- a burst of sparks and crits off the rim
		((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 14, 0.1, 0.1, 0.1, 0.45);
		((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.15, 0.15, 0.15, 0.35);
		if (hitsLeft <= 0 || !pickNext(owner)) {
			startReturn();
		}
	}

	/**
	 * The nearest enemy within ricochet range that it has not hit yet: one it can see (from its centre or from just
	 * above, to its centre or its eyes), or failing that the nearest one within the blind range. False if there is none.
	 */
	private boolean pickNext(ServerPlayer owner) {
		if (hitsLeft <= 0) {
			return false;
		}
		Vec3 pos = position();
		double r = ricochetRange;
		double blind = SuperSoldierConfig.SHIELD_RICOCHET_BLIND_RANGE;
		LivingEntity seen = null;
		double seenD = Double.MAX_VALUE;
		LivingEntity near = null;
		double nearD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(r),
				e -> !hit.contains(e.getId()) && SuperSoldierAbilities.isHostile(owner, e))) { // v0.14.20: auto ricochet, rule 2
			Vec3 c = e.getBoundingBox().getCenter();
			double d = c.distanceToSqr(pos);
			if (d > r * r) {
				continue;
			}
			if (d < seenD && canSee(pos, e)) {
				seen = e;
				seenD = d;
			} else if (d < nearD && d <= blind * blind) {
				near = e;
				nearD = d;
			}
		}
		LivingEntity best = seen != null ? seen : near;
		if (best == null) {
			return false;
		}
		targetId = best.getId();
		setDeltaMovement(best.getBoundingBox().getCenter().subtract(pos).normalize().scale(speed));
		return true;
	}

	private boolean canSee(Vec3 from, LivingEntity e) {
		Vec3[] froms = { from, from.add(0, 0.5, 0) };
		Vec3[] tos = { e.getBoundingBox().getCenter(), e.getEyePosition() };
		for (Vec3 a : froms) {
			for (Vec3 b : tos) {
				if (level().clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS) {
					return true;
				}
			}
		}
		return false;
	}

	private LivingEntity firstEntity(ServerPlayer owner, Vec3 from, Vec3 to) {
		AABB sweep = new AABB(from, to).inflate(0.6);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, sweep,
				e -> !hit.contains(e.getId()) && SuperSoldierAbilities.canTarget(owner, e))) {
			AABB box = e.getBoundingBox().inflate(0.35);
			if (!box.contains(from) && box.clip(from, to).isEmpty()) {
				continue;
			}
			double d = e.getBoundingBox().getCenter().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private void startReturn() {
		entityData.set(RETURNING, true);
		targetId = -1;
	}

	private void tickReturn(ServerPlayer owner) {
		Vec3 home = owner.getEyePosition().add(0, -0.4, 0);
		Vec3 to = home.subtract(position());
		double dist = to.length();
		if (dist < 1.6) {
			level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0f, 1.2f);
			((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, home.x, home.y, home.z, 5, 0.15, 0.15, 0.15, 0.15); // v0.17.5: the catch
			deliver(owner);
			return;
		}
		double speed = Math.max(this.speed, Math.min(3.0, dist * 0.25));
		Vec3 vel = to.normalize().scale(Math.min(speed, dist));
		setDeltaMovement(vel);
		setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
	}

	/** Hands the shield back: the hand it left if free, else the inventory, else at his feet. Then the entity goes. */
	private void deliver(ServerPlayer owner) {
		ItemStack give = takeStack();
		if (!give.isEmpty()) {
			if (toSlot && com.projecthero.mod.supersoldier.CapShieldSlot.putBack(owner, give)) {
				// v0.17.5: home into the shield slot
			} else if (owner.getItemInHand(hand).isEmpty()) {
				owner.setItemInHand(hand, give);
			} else if (!owner.getInventory().add(give) || !give.isEmpty()) {
				spawnItem((ServerLevel) owner.level(), owner.position(), give);
			}
		}
		discard();
		if (isStorm()) {
			SuperSoldierAbilities.stormCaught(owner); // v0.17.7: the Shield Storm's catch -> the dive and the slam
		}
	}

	/** Drops the shield as an item where it is, then the entity goes. */
	private void dropHere() {
		ItemStack give = takeStack();
		if (!give.isEmpty()) {
			spawnItem((ServerLevel) level(), position(), give);
		}
		discard();
	}

	private ItemStack takeStack() {
		ItemStack give = stack;
		stack = ItemStack.EMPTY;
		return give;
	}

	private static void spawnItem(ServerLevel level, Vec3 at, ItemStack give) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, give);
		item.setDeltaMovement(Vec3.ZERO);
		item.setNoPickUpDelay();
		level.addFreshEntity(item);
	}

	@Override
	public void remove(RemovalReason reason) {
		// /kill or any other destroying removal before the shield got home: the item must not vanish with it
		if (!level().isClientSide && reason.shouldDestroy() && !stack.isEmpty()) {
			ItemStack give = takeStack();
			spawnItem((ServerLevel) level(), position(), give);
		}
		super.remove(reason);
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		// a reloaded shield resumes for a thrower who is still here, or drops itself as an item on its first tick
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		hand = tag.getBoolean("OffHand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		toSlot = tag.getBoolean("ToSlot");
		hitsLeft = tag.getInt("HitsLeft");
		damage = tag.getFloat("Damage");
		range = tag.getDouble("Range");
		if (tag.contains("Item", Tag.TAG_COMPOUND)) {
			stack = ItemStack.parse(registryAccess(), tag.getCompound("Item")).orElse(ItemStack.EMPTY);
			entityData.set(ITEM, stack);
		}
		entityData.set(RETURNING, true); // whatever it was doing, it heads home
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putBoolean("OffHand", hand == InteractionHand.OFF_HAND);
		tag.putBoolean("ToSlot", toSlot);
		tag.putInt("HitsLeft", hitsLeft);
		tag.putFloat("Damage", damage);
		tag.putDouble("Range", range);
		if (!stack.isEmpty()) {
			tag.put("Item", stack.save(registryAccess()));
		}
	}
}

package com.projecthero.mod.ironman.drone;

import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManBeamPayload;
import com.projecthero.mod.network.IronManDroneInputPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 Remote Pilot (Mark 42 style): a full Iron Man suit flying on its own, piloted from a distance by its Tony
 * Stark owner. Drawn client-side as the real GeckoLib armour on an invisible armour stand
 * ({@code IronManDroneRenderer}).
 *
 * <h2>Where the suit lives</h2>
 * The four real armour stacks are taken out of the owner's pack (or off their Suit Platform) and live <b>only</b> in
 * this entity while it is deployed, together with the suit's energy and integrity. They are saved with the entity and
 * always leave it in exactly one way, all four at once: docked back into the owner's pack ({@link #dock}), stored back
 * on a platform ({@link #ownerGone}), or dropped as item entities stamped with the current state ({@link #dropPieces}).
 * Every path that removes the entity for good ({@code /kill}, falling out of the world) goes through one of those.
 *
 * <h2>States</h2>
 * {@link #PILOTED}: server-moved from the owner's {@link IronManDroneInputPayload} (WASD / jump / sneak / mouse aim /
 * fire), draining energy; ends on C, owner damage, leaving {@link IronManDrones#MAX_RANGE}, or energy reaching zero.
 * {@link #RETURNING}: flies back to the owner (climbing over what it bumps into) and docks into their pack; if it stays
 * stuck it lands where it is as pieces. Integrity reaching zero in any state drops the pieces where it is.
 */
public class IronManDroneEntity extends Entity {
	public static final int PILOTED = 0;
	public static final int RETURNING = 1;

	/** Indices of {@link #stacks}: helmet, chestplate, leggings, boots. */
	public static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	/** Link upkeep, energy per tick (3 / s). */
	public static final float UPKEEP_PER_TICK = 0.15f;
	/** Extra energy per tick while thrusting (5 / s), doubled when boosting. */
	public static final float THRUST_PER_TICK = 0.25f;
	/** Integrity lost per point of incoming damage (nobody inside to share the hit). */
	public static final float INTEGRITY_PER_DAMAGE = 2.0f;
	/** Cruise speed in blocks / tick at flightSpeed 1.0 (boost = x1.8). */
	public static final double CRUISE = 0.55;
	public static final double REPULSOR_RANGE = 50.0; // v0.14.31: repulsors reach 50 blocks
	/** Ticks without making progress on the way home before the drone gives up and lands as pieces. */
	public static final int STUCK_TICKS = 60;
	/** Hard cap on the flight home; past it the suit docks straight away. */
	public static final int RETURN_TIMEOUT = 400;
	/** Firing flag lasts this long after a shot (arm-forward pose on the client). */
	private static final int FIRE_POSE_TICKS = 10;

	private static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> CHEST = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> LEGS = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> FEET = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> INTEGRITY = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> FIRING = SynchedEntityData.defineId(IronManDroneEntity.class, EntityDataSerializers.BYTE);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<ItemStack>[] SLOTS = new EntityDataAccessor[] { HEAD, CHEST, LEGS, FEET };

	/**
	 * Client hook (set by the client's {@code IronManDroneClient}): while the local player pilots this drone its camera
	 * follows the pilot's mouse every frame instead of the 20 Hz server rotation. Null on a dedicated server.
	 */
	public interface ViewSource {
		boolean drives(IronManDroneEntity drone);

		float yaw(float partialTick);

		float pitch(float partialTick);
	}

	public static volatile ViewSource clientView;

	private final ItemStack[] stacks = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	private UUID ownerId;
	private String suitId = "";
	private float energy;
	private float integrity;
	/** The platform the suit came off (null = from the pack): where it goes if its owner logs out. */
	private GlobalPos home;

	// pilot input (server)
	private float inForward;
	private float inStrafe;
	private int inFlags;
	private int inputAge = 999;
	private int fireCooldown;
	private int firePose;

	private int stateTicks;
	private int stuckTicks;
	private int ownerMissingTicks;
	private boolean loadedFromDisk;

	public IronManDroneEntity(EntityType<? extends IronManDroneEntity> type, Level level) {
		super(type, level);
	}

	/** Server: build a deployed drone carrying {@code pieces} (helmet, chest, legs, boots -- taken over, not copied). */
	static IronManDroneEntity create(ServerLevel level, ServerPlayer owner, String suitId, ItemStack[] pieces,
			float energy, float integrity, GlobalPos home, Vec3 pos, float yaw) {
		IronManDroneEntity d = new IronManDroneEntity(IronManDrones.DRONE, level);
		d.ownerId = owner.getUUID();
		d.suitId = suitId;
		for (int i = 0; i < 4; i++) {
			d.setStack(i, pieces[i]);
		}
		d.setEnergy(energy);
		d.setIntegrity(integrity);
		d.home = home;
		d.moveTo(pos.x, pos.y, pos.z, yaw, 0f);
		d.setState(PILOTED);
		return d;
	}

	// ------------------------------------------------------------------ accessors

	public UUID ownerId() {
		return ownerId;
	}

	public String suitId() {
		if (suitId.isEmpty()) {
			for (int i = 0; i < 4; i++) {
				if (stack(i).getItem() instanceof IronManArmorItem p) {
					return p.suitId();
				}
			}
		}
		return suitId;
	}

	public IronManSuit suit() {
		return IronManSuits.byId(suitId());
	}

	/** Server: the real stack; client: the synced copy. */
	public ItemStack stack(int i) {
		return level().isClientSide() ? getEntityData().get(SLOTS[i]) : stacks[i];
	}

	private void setStack(int i, ItemStack s) {
		stacks[i] = s == null ? ItemStack.EMPTY : s;
		getEntityData().set(SLOTS[i], stacks[i].copy());
	}

	public int pieceCount() {
		int n = 0;
		for (int i = 0; i < 4; i++) {
			n += stack(i).isEmpty() ? 0 : 1;
		}
		return n;
	}

	public float energy() {
		return level().isClientSide() ? getEntityData().get(ENERGY) : energy;
	}

	public float integrity() {
		return level().isClientSide() ? getEntityData().get(INTEGRITY) : integrity;
	}

	public void setEnergy(float v) {
		energy = Math.max(0f, Math.min(IronManEnergy.capacity(suitId()), v));
		getEntityData().set(ENERGY, energy);
	}

	public void setIntegrity(float v) {
		integrity = Math.max(0f, Math.min(IronManEnergy.maxIntegrity(suitId()), v));
		getEntityData().set(INTEGRITY, integrity);
	}

	public int state() {
		return getEntityData().get(STATE);
	}

	public boolean isPiloted() {
		return state() == PILOTED;
	}

	private void setState(int s) {
		getEntityData().set(STATE, (byte) s);
		stateTicks = 0;
		stuckTicks = 0;
	}

	/** Client: a repulsor went off in the last few ticks (arm-forward pose). */
	public boolean firing() {
		return getEntityData().get(FIRING) != 0;
	}

	public GlobalPos home() {
		return home;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HEAD, ItemStack.EMPTY);
		builder.define(CHEST, ItemStack.EMPTY);
		builder.define(LEGS, ItemStack.EMPTY);
		builder.define(FEET, ItemStack.EMPTY);
		builder.define(ENERGY, 0f);
		builder.define(INTEGRITY, 0f);
		builder.define(STATE, (byte) PILOTED);
		builder.define(FIRING, (byte) 0);
	}

	// ------------------------------------------------------------------ input

	/** Server: the latest pilot input (already validated to come from the owner). */
	void acceptInput(IronManDroneInputPayload in) {
		inForward = finite(in.forward()) ? Mth.clamp(in.forward(), -1f, 1f) : 0f;
		inStrafe = finite(in.strafe()) ? Mth.clamp(in.strafe(), -1f, 1f) : 0f;
		inFlags = in.flags();
		inputAge = 0;
		if (finite(in.yaw()) && finite(in.pitch())) {
			setYRot(Mth.wrapDegrees(in.yaw()));
			setXRot(Mth.clamp(in.pitch(), -90f, 90f));
		}
	}

	private static boolean finite(float f) {
		return !Float.isNaN(f) && !Float.isInfinite(f);
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientFx();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		stateTicks++;
		if (pieceCount() == 0) {
			discard();
			return;
		}
		if (firePose > 0 && --firePose == 0) {
			getEntityData().set(FIRING, (byte) 0);
		}
		if (getY() < level.getMinBuildHeight() - 16) {
			onBelowWorld();
			return;
		}
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		boolean ownerHere = owner != null && owner.level() == level && owner.isAlive() && !owner.isRemoved();
		if (!ownerHere) {
			// logged out (normally handled by the disconnect hook), dead or in another dimension. A drone loaded from
			// disk after a restart gives its owner a little while to join before it goes home on its own.
			ownerMissingTicks++;
			if (owner != null || !loadedFromDisk || ownerMissingTicks > 200) {
				ownerGone();
			}
			return;
		}
		ownerMissingTicks = 0;
		IronManDrones.track(this);

		if (isPiloted()) {
			if (IronManDrones.linkedDrone(owner) != this) {
				setState(RETURNING); // a stale link (e.g. loaded from disk mid-flight)
			} else {
				tickPiloted(level, owner);
				return;
			}
		}
		tickReturning(level, owner);
	}

	private void tickPiloted(ServerLevel level, ServerPlayer owner) {
		noPhysics = false;
		setNoGravity(true);
		double dist = distanceTo(owner);
		if (dist > IronManDrones.effectiveRange(level)) {
			IronManDrones.endLink(owner, "message.projecthero.ironman.drone.signal_lost");
			return;
		}
		inputAge++;
		boolean live = inputAge <= 10; // input stream stalled -> hover
		float fwd = live ? inForward : 0f;
		float str = live ? inStrafe : 0f;
		int flags = live ? inFlags : 0;
		if ((flags & IronManDroneInputPayload.END) != 0) {
			IronManDrones.endLink(owner, "message.projecthero.ironman.drone.ended");
			return;
		}
		boolean boost = (flags & IronManDroneInputPayload.BOOST) != 0;
		double vert = ((flags & IronManDroneInputPayload.UP) != 0 ? 1 : 0) - ((flags & IronManDroneInputPayload.DOWN) != 0 ? 1 : 0);
		boolean thrusting = fwd != 0f || str != 0f || vert != 0;

		float drain = UPKEEP_PER_TICK + (thrusting ? THRUST_PER_TICK * (boost ? 2f : 1f) : 0f);
		setEnergy(energy - drain);
		if (energy <= 0f) {
			dropPieces(owner, "message.projecthero.ironman.drone.no_power");
			return;
		}

		IronManSuit suit = suit();
		double speed = CRUISE * (suit == null ? 1.0 : suit.flightSpeed()) * (boost ? 1.8 : 1.0);
		Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
		Vec3 flatRight = Vec3.directionFromRotation(0f, getYRot() - 90f); // left of the view (vanilla strafe sign)
		Vec3 want = look.scale(fwd).add(flatRight.scale(str)).add(0, vert, 0);
		if (want.lengthSqr() > 1.0) {
			want = want.normalize();
		}
		want = want.scale(speed);
		Vec3 vel = getDeltaMovement().lerp(want, thrusting ? 0.22 : 0.15);
		setDeltaMovement(vel);
		move(MoverType.SELF, vel);
		if (thrusting && tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.05, getZ(), 1, 0.08, 0.02, 0.08, 0.005);
		}
		if (thrusting && tickCount % 20 == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.35f, boost ? 1.3f : 1.0f);
		}

		if (fireCooldown > 0) {
			fireCooldown--;
		}
		if ((flags & IronManDroneInputPayload.FIRE) != 0 && fireCooldown == 0) {
			fireRepulsor(level, owner, suit);
		}
	}

	private void tickReturning(ServerLevel level, ServerPlayer owner) {
		noPhysics = false;
		setNoGravity(true);
		Vec3 target = owner.position().add(0, 0.3, 0);
		Vec3 to = target.subtract(position());
		double dist = to.length();
		if (dist < 1.6 || stateTicks > RETURN_TIMEOUT) {
			dock(owner);
			return;
		}
		double speed = Math.min(0.9, 0.25 + dist * 0.08);
		Vec3 want = to.normalize().scale(speed);
		if (horizontalCollision) {
			want = new Vec3(want.x * 0.3, Math.max(want.y, 0.45), want.z * 0.3); // climb over what is in the way
		}
		Vec3 vel = getDeltaMovement().lerp(want, 0.3);
		Vec3 before = position();
		setDeltaMovement(vel);
		move(MoverType.SELF, vel);
		// look where it is going
		if (vel.horizontalDistanceSqr() > 1.0e-4) {
			setYRot((float) (Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) - 90f);
		}
		setXRot(0f);
		if (position().distanceToSqr(before) < 0.0025) {
			if (++stuckTicks > STUCK_TICKS) {
				dropPieces(owner, "message.projecthero.ironman.drone.blocked");
				return;
			}
		} else {
			stuckTicks = 0;
		}
		if (tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.05, getZ(), 1, 0.08, 0.02, 0.08, 0.005);
		}
	}

	/** Called by {@link IronManDrones#endLink}: stop taking input and head home. */
	void beginReturn() {
		setState(RETURNING);
		inFlags = 0;
		inForward = 0f;
		inStrafe = 0f;
		IronManSounds.play(this, IronManSounds.THRUSTER, 0.8f, 1.1f);
	}

	// ------------------------------------------------------------------ repulsor

	private void fireRepulsor(ServerLevel level, ServerPlayer owner, IronManSuit suit) {
		float cost = suit == null ? 10f : suit.repulsorTapEnergy();
		if (energy < cost) {
			return;
		}
		setEnergy(energy - cost);
		fireCooldown = Math.max(6, suit == null ? 20 : suit.repulsorTapCooldownTicks());
		float damage = suit == null ? 10f : suit.repulsorDamage();

		Vec3 look = Vec3.directionFromRotation(getXRot(), getYRot());
		Vec3 right = Vec3.directionFromRotation(0f, getYRot() + 90f);
		Vec3 eye = getEyePosition();
		Vec3 origin = eye.add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.35, 0);
		Vec3 far = eye.add(look.scale(REPULSOR_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? far : block.getLocation();
		AABB sweep = getBoundingBox().expandTowards(look.scale(REPULSOR_RANGE)).inflate(1.0);
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level, this, eye, stop, sweep,
				e -> e instanceof LivingEntity le && le.isAlive() && e != owner && !e.isSpectator() && e.isPickable());
		LivingEntity target = ehr != null && ehr.getEntity() instanceof LivingEntity le ? le : null;
		Vec3 end = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : stop;

		IronManBeamPayload beam = new IronManBeamPayload(origin, end, 0);
		for (ServerPlayer viewer : level.players()) {
			if ((viewer.distanceToSqr(this) < 128 * 128 || viewer == owner) && ServerPlayNetworking.canSend(viewer, IronManBeamPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, beam);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f, 1.6f);
		if (target != null) {
			AbilityHelpers.hurt(owner, target, damage);
			AbilityHelpers.knockbackFrom(target, position(), 1.1);
		}
		firePose = FIRE_POSE_TICKS;
		getEntityData().set(FIRING, (byte) 1);
	}

	// ------------------------------------------------------------------ damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isRemoved() || isInvulnerableTo(source) || amount <= 0f) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && attacker.getUUID().equals(ownerId)) {
			return false; // never your own suit
		}
		setIntegrity(integrity - amount * INTEGRITY_PER_DAMAGE);
		ServerLevel level = (ServerLevel) level();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.08);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.25f, 1.8f);
		if (integrity <= 0f) {
			ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.6, 0.4, 0.03);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.7f, 1.2f);
			dropPieces(owner, "message.projecthero.ironman.drone.destroyed");
		}
		return true;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return !isRemoved() && pieceCount() > 0;
	}

	// ------------------------------------------------------------------ the only ways the suit leaves the drone

	/** Stamp every carried stack with the drone's current energy / integrity. */
	private void stampAll() {
		for (int i = 0; i < 4; i++) {
			if (!stacks[i].isEmpty()) {
				IronManEnergy.stampStack(stacks[i], energy, integrity);
			}
		}
	}

	/** Take all four stacks out (stamped). The drone is then empty and is discarded. */
	private ItemStack[] takeAll() {
		stampAll();
		ItemStack[] out = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			out[i] = stacks[i];
			setStack(i, ItemStack.EMPTY);
		}
		return out;
	}

	private void finish() {
		IronManDrones.forget(this);
		discard();
	}

	/** Dock into the owner's pack; whatever does not fit lands at their feet. */
	public void dock(ServerPlayer owner) {
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		boolean spilled = false;
		for (ItemStack s : takeAll()) {
			if (s.isEmpty()) {
				continue;
			}
			if (!owner.getInventory().add(s) || !s.isEmpty()) {
				if (!s.isEmpty()) {
					owner.drop(s, false);
					spilled = true;
				}
			}
		}
		owner.inventoryMenu.broadcastChanges();
		Component name = suitName();
		owner.displayClientMessage(Component.translatable(spilled
				? "message.projecthero.ironman.drone.docked_dropped" : "message.projecthero.ironman.drone.docked", name)
				.withStyle(ChatFormatting.AQUA), true);
		IronManSounds.play(this, IronManSounds.CLAMP, 0.9f, 1.1f);
		((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		finish();
	}

	/** Fall inert as the four pieces, right here, with their state preserved. {@code owner} may be null. */
	public void dropPieces(ServerPlayer owner, String messageKey) {
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		if (isPiloted() && owner != null) {
			IronManDrones.closeLinkSilently(owner, this);
		}
		BlockPos at = blockPosition();
		for (ItemStack s : takeAll()) {
			if (!s.isEmpty()) {
				spawnAtLocation(s, 0.5f);
			}
		}
		if (owner != null && messageKey != null) {
			owner.displayClientMessage(Component.translatable(messageKey, suitName(), at.getX(), at.getY(), at.getZ())
					.withStyle(ChatFormatting.RED), false);
		}
		finish();
	}

	/**
	 * The owner logged out / died / left the dimension: the suit goes back onto its platform (the one it came off, else
	 * the nearest of the owner's platforms with room) and anything that does not fit is dropped right here.
	 */
	public void ownerGone() {
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		IronManSuitPlatformBlockEntity dock = platformAt(level, home);
		if (dock == null && ownerId != null) {
			var entry = StarkPlatformRegistry.get(level).nearestDockFor(ownerId, level.dimension(), suitId(), blockPosition());
			if (entry.isPresent()) {
				dock = platformAt(level, GlobalPos.of(level.dimension(), entry.get().blockPos()));
			}
		}
		for (ItemStack s : takeAll()) {
			if (s.isEmpty()) {
				continue;
			}
			if (dock == null || !dock.store(s) || !s.isEmpty()) {
				if (!s.isEmpty()) {
					spawnAtLocation(s, 0.5f);
				}
			}
		}
		finish();
	}

	private IronManSuitPlatformBlockEntity platformAt(ServerLevel level, GlobalPos pos) {
		if (pos == null || pos.dimension() != level.dimension()) {
			return null;
		}
		BlockPos p = pos.pos();
		level.getChunk(p.getX() >> 4, p.getZ() >> 4); // one-off synchronous load, like an unloaded-platform call
		if (level.getBlockEntity(p) instanceof IronManSuitPlatformBlockEntity be
				&& (be.owner().isEmpty() || be.owner().get().equals(ownerId))
				&& (be.storedSuitId() == null || be.storedSuitId().equals(suitId())) && !be.sequenceRunning()) {
			return be;
		}
		return null;
	}

	private Component suitName() {
		IronManSuit s = suit();
		return s != null ? Component.translatable(s.nameKey()) : Component.literal(suitId());
	}

	@Override
	protected void onBelowWorld() {
		if (level().isClientSide()) {
			super.onBelowWorld();
			return;
		}
		ServerPlayer owner = ownerId == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(ownerId);
		if (owner != null && owner.level() == level()) {
			IronManDrones.closeLinkSilently(owner, this);
			dock(owner);
		} else {
			ownerGone();
		}
	}

	@Override
	public void kill() {
		if (!level().isClientSide() && !isRemoved()) {
			ServerPlayer owner = ownerId == null ? null : ((ServerLevel) level()).getServer().getPlayerList().getPlayer(ownerId);
			dropPieces(owner, null);
			return;
		}
		super.kill();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason.shouldDestroy()) {
			IronManDrones.forget(this);
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------ client

	private void clientFx() {
		Vec3 moved = position().subtract(xo, yo, zo);
		if (moved.lengthSqr() > 0.004 && random.nextFloat() < 0.6f) {
			level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 0.2, getY() + 0.05,
					getZ() + (random.nextDouble() - 0.5) * 0.2, 0, -0.08, 0);
		}
	}

	@Override
	public float getViewYRot(float partialTick) {
		ViewSource v = clientView;
		if (v != null && level().isClientSide() && v.drives(this)) {
			return v.yaw(partialTick);
		}
		return super.getViewYRot(partialTick);
	}

	@Override
	public float getViewXRot(float partialTick) {
		ViewSource v = clientView;
		if (v != null && level().isClientSide() && v.drives(this)) {
			return v.pitch(partialTick);
		}
		return super.getViewXRot(partialTick);
	}

	// ------------------------------------------------------------------ save / load

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		suitId = tag.getString("Suit");
		for (int i = 0; i < 4; i++) {
			String key = "Piece" + i;
			setStack(i, tag.contains(key) ? ItemStack.parseOptional(registryAccess(), tag.getCompound(key)) : ItemStack.EMPTY);
		}
		setEnergy(tag.getFloat("Energy"));
		setIntegrity(tag.contains("Integrity") ? tag.getFloat("Integrity") : IronManEnergy.maxIntegrity(suitId()));
		home = tag.contains("Home") ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get("Home")).result().orElse(null) : null;
		// a link never survives a reload: the suit flies home to its owner (or back to its platform)
		setState(RETURNING);
		loadedFromDisk = true;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putString("Suit", suitId());
		for (int i = 0; i < 4; i++) {
			if (!stacks[i].isEmpty()) {
				tag.put("Piece" + i, stacks[i].save(registryAccess()));
			}
		}
		tag.putFloat("Energy", energy);
		tag.putFloat("Integrity", integrity);
		if (home != null) {
			GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, home).result().ifPresent(t -> tag.put("Home", t));
		}
	}
}

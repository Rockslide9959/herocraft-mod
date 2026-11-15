package com.projecthero.mod.gear;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Tier I: the Grapple Line's hook, stuck in a block. While it lives it reels its owner in (server side: the
 * velocity is pushed to the client with {@code hurtMarked}, like knockback), and the client draws the line from the
 * owner's hand to it. It lets go when the owner arrives, sneaks, stops making progress, or after {@link #MAX_TICKS}.
 */
public class GrappleHookEntity extends Entity {
	public static final int MAX_TICKS = 70;
	public static final double ARRIVE_DISTANCE = 1.6;
	public static final double ARRIVE_HOP = 0.6;

	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_UPGRADED = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_RELEASED = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.BOOLEAN);
	public static final int STALL_TICKS = 6;
	public static final int MANTLE_TICKS = 12;

	private double speed = 1.1;
	private int mantleTicks;
	private int life;
	private double bestDistance = Double.MAX_VALUE;
	private int stalledTicks;

	public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public static GrappleHookEntity create(Level level, Player owner, Vec3 anchor, double speed, boolean upgraded) {
		GrappleHookEntity hook = new GrappleHookEntity(GearItems.GRAPPLE_HOOK, level);
		hook.setPos(anchor);
		hook.entityData.set(DATA_OWNER, owner.getId());
		hook.entityData.set(DATA_UPGRADED, upgraded);
		hook.speed = speed;
		return hook;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, -1);
		builder.define(DATA_UPGRADED, false);
		builder.define(DATA_RELEASED, false);
	}

	public Player owner() {
		return level().getEntity(entityData.get(DATA_OWNER)) instanceof Player p ? p : null;
	}

	public boolean isOwnedBy(Player player) {
		return entityData.get(DATA_OWNER) == player.getId();
	}

	public boolean upgraded() {
		return entityData.get(DATA_UPGRADED);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		Player owner = owner();
		if (owner == null || !owner.isAlive() || owner.level() != level() || owner.isShiftKeyDown() || owner.isPassenger()) {
			discard();
			return;
		}
		if (released()) {
			mantle(owner);
			return;
		}
		double dist = pullStep(owner, position(), speed);
		if (dist < bestDistance - 0.05) {
			bestDistance = dist;
			stalledTicks = 0;
		} else {
			stalledTicks++;
		}
		if (dist < ARRIVE_DISTANCE || stalledTicks > STALL_TICKS || ++life > MAX_TICKS) {
			// a hop at the end of the reel (about two blocks) so you can climb onto the ledge you hooked
			Vec3 v = owner.getDeltaMovement();
			owner.setDeltaMovement(v.x * 0.3, Math.max(v.y, ARRIVE_HOP), v.z * 0.3);
			owner.resetFallDistance();
			owner.hurtMarked = true;
			level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.5f, 1.4f);
			entityData.set(DATA_RELEASED, true);
			mantleTicks = MANTLE_TICKS;
		}
	}

	/**
	 * After letting go: a few ticks of a gentle push towards the hook, so once you clear the edge you land on top. (One
	 * push at release is not enough: pressed against the wall, the client's collision zeroes sideways speed at once.)
	 */
	private void mantle(Player owner) {
		if (--mantleTicks < 0 || (mantleTicks < MANTLE_TICKS - 3 && owner.onGround())) {
			discard();
			return;
		}
		Vec3 in = position().subtract(owner.position()).multiply(1, 0, 1);
		if (in.lengthSqr() > 1.0e-4 && owner.getY() < getY() + 1.5) {
			in = in.normalize().scale(0.22);
			owner.setDeltaMovement(in.x, owner.getDeltaMovement().y, in.z);
			owner.hurtMarked = true;
		}
		owner.resetFallDistance();
	}

	/** True once the reel has finished and the hook is only steadying the climb (the client stops drawing the cable). */
	public boolean released() {
		return entityData.get(DATA_RELEASED);
	}

	/**
	 * One tick of reeling: sets the player's velocity towards {@code anchor} (capped at {@code speed} blocks a tick,
	 * easing off on the last couple of blocks), clears fall distance, and returns the distance left.
	 */
	public static double pullStep(Player player, Vec3 anchor, double speed) {
		Vec3 from = player.position().add(0, player.getBbHeight() * 0.5, 0);
		Vec3 d = anchor.subtract(from);
		double dist = d.length();
		if (dist < 1.0e-4) {
			return 0;
		}
		double s = Math.min(speed, Math.max(0.35, dist * 0.4));
		Vec3 v = d.scale(s / dist);
		player.setDeltaMovement(v.x, v.y + 0.04, v.z);
		player.hurtMarked = true;
		player.resetFallDistance();
		return dist;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

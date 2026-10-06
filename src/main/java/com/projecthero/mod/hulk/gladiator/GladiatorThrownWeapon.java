package com.projecthero.mod.hulk.gladiator;

import java.util.UUID;

import com.projecthero.mod.hulk.HulkCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.3: what the Gladiator Hulk's thrown axe ({@link GladiatorAxeEntity}) and hammer ({@link GladiatorHammerEntity}) share.
 * Server-driven and never saved: the server moves it every tick ({@code noPhysics}, its own swept collision with
 * {@link Level#clip}), the client only draws it where the tracker puts it. The weapon itself never leaves the player's
 * gear -- {@link GladiatorGear#setWeaponAway} empties the hand while this is out, and {@link GladiatorAbilities} clears
 * the flag when it comes home (or on any timeout, revert, death or logout).
 *
 * <p>Never steps into a chunk that would not tick it (an entity in a frozen chunk would hang there forever): the
 * outbound flight turns round instead, the return flight lands in the hand at once.
 */
public abstract class GladiatorThrownWeapon extends Entity {
	public static final int PHASE_OUT = 0;
	public static final int PHASE_RETURN = 1;
	public static final int PHASE_STUCK = 2;
	public static final int PHASE_FALL = 3;

	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(GladiatorThrownWeapon.class,
			EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(GladiatorThrownWeapon.class,
			EntityDataSerializers.INT);

	private static final double RETURN_SPEED_MIN = 0.9;
	private static final double RETURN_SPEED_MAX = 3.0;
	private static final double RETURN_ACCEL = 0.15;
	private static final double CATCH_DISTANCE = 2.2;

	protected UUID owner;
	protected Vec3 velocity = Vec3.ZERO;
	protected int age;
	protected double returnSpeed = RETURN_SPEED_MIN;
	protected HulkCombat.Hit outHit;
	protected HulkCombat.Hit returnHit;
	private boolean home;

	protected GladiatorThrownWeapon(EntityType<? extends GladiatorThrownWeapon> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setup(ServerPlayer player, Vec3 velocity, HulkCombat.Hit outHit, HulkCombat.Hit returnHit) {
		this.owner = player.getUUID();
		this.entityData.set(DATA_OWNER, player.getId());
		this.velocity = velocity;
		this.outHit = outHit;
		this.returnHit = returnHit;
		this.outHit.hit.add(player.getId());
		this.returnHit.hit.add(player.getId());
		face(velocity);
	}

	/** Which of the two weapons this is (for the gear flag). */
	public abstract boolean isAxe();

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	protected void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
	}

	public int ownerId() {
		return this.entityData.get(DATA_OWNER);
	}

	public boolean returning() {
		return phase() == PHASE_RETURN;
	}

	/** Turn round and fly home (also the hammer's recall). */
	public void startReturn() {
		if (phase() != PHASE_RETURN) {
			setPhase(PHASE_RETURN);
			returnSpeed = RETURN_SPEED_MIN;
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_PHASE, PHASE_OUT);
		builder.define(DATA_OWNER, -1);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	protected ServerPlayer ownerPlayer() {
		if (owner == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		ServerPlayer p = sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : sl.getServer().getPlayerList().getPlayer(owner);
		return p != null && p.level() == level() && p.isAlive() ? p : null;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return;
		}
		ServerPlayer player = ownerPlayer();
		if (player == null || home) {
			discard();
			return;
		}
		age++;
		serverTick((ServerLevel) level(), player);
	}

	protected abstract void serverTick(ServerLevel level, ServerPlayer player);

	/** Points the entity along {@code v} (the renderer aligns the model to it). */
	protected void face(Vec3 v) {
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		setYRot((float) (Mth.atan2(v.x, v.z) * (180.0 / Math.PI)));
		setXRot((float) (Mth.atan2(v.y, h) * (180.0 / Math.PI)));
		this.yRotO = getYRot();
		this.xRotO = getXRot();
	}

	protected static boolean ticking(ServerLevel level, Vec3 p) {
		return level.isPositionEntityTicking(BlockPos.containing(p));
	}

	/** Swept block collision from here to {@code to}; null if the way is clear. */
	protected BlockHitResult clip(Vec3 to) {
		BlockHitResult r = level().clip(new ClipContext(position(), to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return r.getType() == HitResult.Type.MISS ? null : r;
	}

	/** Strikes everything the weapon passes through between {@code from} and {@code to}. Returns how many. */
	protected int sweepHits(ServerPlayer player, Vec3 from, Vec3 to, HulkCombat.Hit hit) {
		AABB box = new AABB(from, to).inflate(0.8);
		int n = 0;
		for (LivingEntity e : HulkCombat.targets(player, box)) {
			if (hit.hit.contains(e.getId())) {
				continue;
			}
			AABB eb = e.getBoundingBox().inflate(0.6);
			if (!eb.contains(from) && !eb.contains(to) && eb.clip(from, to).isEmpty()) {
				continue;
			}
			if (HulkCombat.strike(player, e, from, hit, 1.0f)) {
				n++;
				onStruck((ServerLevel) level(), e);
			}
		}
		return n;
	}

	protected void onStruck(ServerLevel level, LivingEntity e) {
	}

	/** Flies home; true once it is back in his hand (the caller's flag is cleared through {@link GladiatorAbilities}). */
	protected boolean flyHome(ServerLevel level, ServerPlayer player) {
		Vec3 target = player.position().add(0, player.getBbHeight() * 0.6, 0);
		Vec3 to = target.subtract(position());
		double dist = to.length();
		if (dist < CATCH_DISTANCE) {
			arriveHome(player);
			return true;
		}
		returnSpeed = Math.min(RETURN_SPEED_MAX, returnSpeed + RETURN_ACCEL);
		Vec3 step = to.scale(Math.min(returnSpeed, dist) / dist);
		Vec3 next = position().add(step);
		if (!ticking(level, next)) {
			arriveHome(player); // the way home runs through a chunk that would freeze it: it is simply back
			return true;
		}
		sweepHits(player, position(), next, returnHit);
		velocity = step;
		face(step);
		setPos(next);
		if (position().distanceTo(target) < CATCH_DISTANCE) {
			arriveHome(player);
			return true;
		}
		return false;
	}

	private void arriveHome(ServerPlayer player) {
		home = true;
		GladiatorAbilities.weaponHome(player, isAxe(), this);
		discard();
	}
}

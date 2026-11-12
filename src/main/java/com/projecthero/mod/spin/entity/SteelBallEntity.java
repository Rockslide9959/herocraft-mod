package com.projecthero.mod.spin.entity;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.spin.Spin;
import com.projecthero.mod.spin.SpinConfig;
import com.projecthero.mod.spin.SpinMarks;
import com.projecthero.mod.spin.SpinSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin: one of the two Steel Balls in flight. It always comes back: out to its range (or the first thing it hits),
 * then home to his hand, where it is caught ({@link Spin#setThrown} clears). The server moves it; the client only draws it
 * ({@code SteelBallRenderer}, which calls the shared {@code SteelBallRender}) and its spiralling trail.
 * <ul>
 *   <li>{@link #MODE_HIT}: R / Shift+R -- hits the first creature for {@link SpinConfig#BALL_DAMAGE} and spins it.</li>
 *   <li>{@link #MODE_PIERCE}: the Golden Spin's ball -- the damage was dealt along the line already; it flies through
 *   everything to the line's end, golden.</li>
 *   <li>{@link #MODE_BREAKER}: Ball Breaker's ball -- flies to the target, where the figure bursts out.</li>
 * </ul>
 * Twin Balls steer: each starts off to one side and curves back in onto the aim point.
 */
public class SteelBallEntity extends Projectile {
	public static final byte MODE_HIT = 0;
	public static final byte MODE_PIERCE = 1;
	public static final byte MODE_BREAKER = 2;

	private static final EntityDataAccessor<Byte> INDEX = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> GOLDEN = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(SteelBallEntity.class, EntityDataSerializers.BOOLEAN);

	private Vec3 start = Vec3.ZERO;
	private Vec3 aim;
	private boolean steering;
	private double range = SpinConfig.BALL_RANGE;
	private int returnTicks;
	private boolean caught;

	public SteelBallEntity(EntityType<? extends SteelBallEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * Throws ball {@code index} from {@code owner}'s hand with {@code velocity} (blocks a tick). {@code aim}: where it heads (and,
	 * with {@code steer}, curves onto); {@code range}: how far it may go before it turns back.
	 */
	public static SteelBallEntity launch(ServerPlayer owner, int index, byte mode, Vec3 velocity, Vec3 aim, boolean steer, double range,
			boolean golden) {
		SteelBallEntity ball = new SteelBallEntity(SpinEntities.STEEL_BALL, owner.level());
		ball.setOwner(owner);
		Vec3 from = Spin.handPos(owner);
		ball.moveTo(from.x, from.y - 0.15, from.z, owner.getYRot(), 0f);
		ball.start = ball.position();
		ball.aim = aim;
		ball.steering = steer;
		ball.range = range;
		ball.setDeltaMovement(velocity);
		ball.entityData.set(INDEX, (byte) index);
		ball.entityData.set(MODE, mode);
		ball.entityData.set(GOLDEN, golden);
		owner.level().addFreshEntity(ball);
		Spin.setThrown(owner, index, true);
		return ball;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(INDEX, (byte) 0);
		builder.define(MODE, MODE_HIT);
		builder.define(GOLDEN, false);
		builder.define(RETURNING, false);
	}

	public int index() {
		return entityData.get(INDEX);
	}

	public byte mode() {
		return entityData.get(MODE);
	}

	public boolean golden() {
		return entityData.get(GOLDEN);
	}

	public boolean returning() {
		return entityData.get(RETURNING);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 128 * 128;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			return; // the server moves it; positions arrive every tick
		}
		Entity owner = getOwner();
		if (!(owner instanceof ServerPlayer p) || !p.isAlive() || p.level() != level() || !Spin.hasPower(p)) {
			discard();
			return;
		}
		if (tickCount > SpinConfig.BALL_MAX_LIFE) {
			catchBall(p);
			return;
		}
		Vec3 pos = position();
		Vec3 v = getDeltaMovement();
		if (!returning()) {
			if (steering && aim != null) {
				Vec3 to = aim.subtract(pos);
				if (to.lengthSqr() < 4.0) {
					steering = false;
				} else {
					double speed = Math.max(SpinConfig.BALL_SPEED, v.length());
					v = v.add(to.normalize().scale(SpinConfig.TWIN_STEER)).normalize().scale(speed);
				}
			}
			Vec3 next = pos.add(v);
			setDeltaMovement(v);
			if (mode() == MODE_HIT) {
				HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
				if (hit.getType() == HitResult.Type.ENTITY) {
					onHitEntity((EntityHitResult) hit);
					setPos(hit.getLocation());
					startReturn();
					return;
				}
				if (hit.getType() == HitResult.Type.BLOCK) {
					onHitBlock((BlockHitResult) hit);
					Vec3 at = hit.getLocation().subtract(v.normalize().scale(0.2));
					setPos(at);
					startReturn();
					return;
				}
			}
			setDeltaMovement(v);
			setPos(next);
			boolean arrived = mode() != MODE_HIT && aim != null && next.distanceToSqr(aim) < v.lengthSqr() + 0.25;
			if (arrived || next.distanceToSqr(start) > range * range) {
				startReturn();
			}
			return;
		}
		// home to the hand, speeding up
		returnTicks++;
		Vec3 hand = Spin.handPos(p);
		Vec3 to = hand.subtract(pos);
		double dist = to.length();
		if (dist < SpinConfig.BALL_CATCH_DISTANCE) {
			catchBall(p);
			return;
		}
		double speed = Math.min(SpinConfig.BALL_RETURN_SPEED + p.getDeltaMovement().length(), 0.5 + 0.12 * returnTicks);
		Vec3 step = to.normalize().scale(Math.min(speed, dist));
		setDeltaMovement(step);
		setPos(pos.add(step));
	}

	private void startReturn() {
		entityData.set(RETURNING, true);
		steering = false;
		returnTicks = 0;
	}

	private void catchBall(ServerPlayer p) {
		caught = true;
		level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SpinSounds.BALL_CATCH, SoundSource.PLAYERS, 0.5f, 1.0f);
		discard();
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (!level().isClientSide() && getOwner() instanceof ServerPlayer p) {
			Spin.setThrown(p, index(), false);
		}
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!(e instanceof LivingEntity) || e instanceof Player pl && pl.isSpectator() || !e.isAlive() || !e.isPickable()) {
			return false;
		}
		Entity owner = getOwner();
		return e != owner && HeroTargets.canHarm(owner, e);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (level().isClientSide() || !(getOwner() instanceof ServerPlayer p) || !(hit.getEntity() instanceof LivingEntity target)) {
			return;
		}
		if (Spin.hit(p, target, SpinConfig.BALL_DAMAGE, false)) {
			SpinMarks.spin(target, SpinConfig.SPIN_TICKS);
			level().playSound(null, target.getX(), target.getY() + 1.0, target.getZ(), SpinSounds.BODY_SPIN, SoundSource.PLAYERS, 0.5f, 1.0f);
		}
		level().playSound(null, getX(), getY(), getZ(), SpinSounds.BALL_HIT, SoundSource.PLAYERS, 0.6f, 1.0f);
		Vec3 v = getDeltaMovement();
		if (v.lengthSqr() > 1.0e-4) {
			target.knockback(0.35, -v.x, -v.z);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (level() instanceof ServerLevel sl) {
			Vec3 at = hit.getLocation();
			sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.2);
			sl.playSound(null, at.x, at.y, at.z, SpinSounds.BALL_HIT, SoundSource.PLAYERS, 0.4f, 1.2f);
		}
	}

	/** For the tests: the ball was caught by its owner (not lost). */
	public boolean caught() {
		return caught;
	}
}

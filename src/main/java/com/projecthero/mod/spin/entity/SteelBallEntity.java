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
import net.minecraft.world.InteractionHand;
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
	private InteractionHand hand = InteractionHand.MAIN_HAND;
	/** R / Shift+R: the ball bends after the thrower's look on its way out (see {@link #curve}). */
	private boolean curving;
	/** Twin Balls: the distance to the aim point at the throw (they swoop in onto the crosshair line that far out). */
	private double aimDist;
	/** Degrees turned so far on the way out (capped at {@link SpinConfig#CURVE_MAX_TOTAL_DEG}). */
	private float turned;
	/** The creature it is curving onto, if any (server only). */
	private LivingEntity homing;
	// client: the flight direction last tick and this tick (the renderer blends them), and the next position from the server
	private Vec3 dirO = Vec3.ZERO;
	private Vec3 dir = Vec3.ZERO;
	private int lerpSteps;
	private double lerpX, lerpY, lerpZ;

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
		return launch(owner, index, InteractionHand.MAIN_HAND, mode, velocity, aim, steer, range, golden);
	}

	/** As above, thrown from {@code hand}: the ball item leaves that hand, and the catch puts it back there. */
	public static SteelBallEntity launch(ServerPlayer owner, int index, InteractionHand hand, byte mode, Vec3 velocity, Vec3 aim, boolean steer,
			double range, boolean golden) {
		if (com.projecthero.mod.spin.SpinBalls.inHand(owner, hand) == index) {
			// v0.17.4: out of the hand, or (Gyro's suit) off the holsters -- never what else the hand holds
			com.projecthero.mod.spin.SpinBalls.takeOut(owner, hand, index);
		}
		SteelBallEntity ball = new SteelBallEntity(SpinEntities.STEEL_BALL, owner.level());
		ball.hand = hand;
		ball.setOwner(owner);
		Vec3 from = Spin.handPos(owner);
		ball.moveTo(from.x, from.y - 0.15, from.z, owner.getYRot(), 0f);
		ball.start = ball.position();
		ball.aim = aim;
		ball.steering = steer;
		ball.curving = mode == MODE_HIT && !golden;
		ball.aimDist = aim == null ? 0.0 : aim.distanceTo(owner.getEyePosition());
		ball.range = range;
		ball.setDeltaMovement(velocity);
		ball.entityData.set(INDEX, (byte) index);
		ball.entityData.set(MODE, mode);
		ball.entityData.set(GOLDEN, golden);
		owner.level().addFreshEntity(ball);
		Spin.setThrown(owner, index, true);
		return ball;
	}

	/** The recall: ball {@code index} lifts off at {@code from} (where it lay, or was last seen) and flies home into {@code hand}. */
	public static SteelBallEntity recall(ServerPlayer owner, int index, Vec3 from, InteractionHand hand) {
		SteelBallEntity ball = new SteelBallEntity(SpinEntities.STEEL_BALL, owner.level());
		ball.hand = hand;
		ball.setOwner(owner);
		ball.moveTo(from.x, from.y, from.z, owner.getYRot(), 0f);
		ball.start = ball.position();
		ball.entityData.set(INDEX, (byte) index);
		ball.entityData.set(MODE, MODE_HIT);
		ball.entityData.set(RETURNING, true);
		ball.setDeltaMovement(Spin.handPos(owner).subtract(from).normalize().scale(0.5));
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
			// the server moves it; positions arrive every tick and are taken on the tick, so the renderer's old->new blend is even
			if (lerpSteps > 0) {
				setPos(lerpX, lerpY, lerpZ);
				lerpSteps = 0;
			}
			dirO = dir;
			Vec3 v = getDeltaMovement();
			if (v.lengthSqr() > 1.0e-5) {
				dir = v.normalize();
				if (dirO.lengthSqr() < 0.5) {
					dirO = dir;
				}
			}
			return;
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
			if (curving && v.lengthSqr() > 1.0e-6) {
				v = curve(p, pos, v);
			}
			Vec3 next = pos.add(v);
			setDeltaMovement(v);
			if (mode() == MODE_HIT) {
				HitResult hit = sweep(pos, v);
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

	// ---------------------------------------------------------------- the hit test

	/**
	 * v0.17.3 tester note ("improve the ball hitbox"): this tick's whole path, from the ball's centre at {@code pos} along
	 * {@code v}, swept against the blocks and then against every creature's box grown by {@link SpinConfig#BALL_HIT_MARGIN} -- so
	 * a fast ball can never step over a mob between two ticks, and a near miss counts. A creature the ball starts inside is hit
	 * at once. The thrower (and anyone {@link #canHitEntity} refuses) is never hit. The nearest creature before the first block
	 * wins; with none, the block hit (or a miss).
	 */
	public HitResult sweep(Vec3 pos, Vec3 v) {
		double lift = getBbHeight() * 0.5;
		Vec3 from = pos.add(0, lift, 0);
		Vec3 to = from.add(v);
		BlockHitResult block = level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		double m = SpinConfig.BALL_HIT_MARGIN;
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(from, end).inflate(m + 2.0);
		Entity best = null;
		Vec3 bestAt = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level().getEntities(this, area, this::canHitEntity)) {
			net.minecraft.world.phys.AABB box = e.getBoundingBox().inflate(m);
			Vec3 at;
			if (box.contains(from)) {
				at = from;
			} else {
				java.util.Optional<Vec3> c = box.clip(from, end);
				if (c.isEmpty()) {
					continue;
				}
				at = c.get();
			}
			double d = from.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = e;
				bestAt = at;
			}
		}
		if (best != null) {
			return new EntityHitResult(best, bestAt.subtract(0, lift, 0));
		}
		if (block.getType() != HitResult.Type.MISS) {
			return new BlockHitResult(block.getLocation().subtract(0, lift, 0), block.getDirection(), block.getBlockPos(), block.isInside());
		}
		return BlockHitResult.miss(to.subtract(0, lift, 0), net.minecraft.core.Direction.getNearest(v.x, v.y, v.z), net.minecraft.core.BlockPos.containing(to));
	}

	// ---------------------------------------------------------------- curving after the look

	/**
	 * One tick of mid-air steering for an outgoing R / Shift+R ball. It bends toward where the thrower is looking now, at most
	 * {@link SpinConfig#CURVE_LOOK_TURN_DEG} a tick, onto the crosshair line a little ahead of itself. If the crosshair is on (or
	 * very near) a creature it can still reach -- inside its turning circle at {@link SpinConfig#CURVE_HOMING_TURN_DEG} a tick,
	 * within what is left of the {@link SpinConfig#CURVE_MAX_TOTAL_DEG} budget, and not behind a block -- it homes onto it and hits.
	 * Nothing steers once the thrower looks well away from the ball ({@link SpinConfig#CURVE_LOOK_CONE_DEG}), so turning round never
	 * drags it back, and a goal more than {@link SpinConfig#CURVE_GIVE_UP_DEG} off its heading is ignored (no U-turns).
	 */
	private Vec3 curve(ServerPlayer p, Vec3 pos, Vec3 v) {
		double speed = Math.max(SpinConfig.BALL_SPEED, v.length());
		Vec3 d = v.normalize();
		double budget = (steering ? SpinConfig.CURVE_MAX_TOTAL_DEG + SpinConfig.TWIN_SWOOP_DEG : SpinConfig.CURVE_MAX_TOTAL_DEG) - turned;
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getViewVector(1f);
		Vec3 fromEye = pos.subtract(eye);
		double ballDist = fromEye.length();
		if (budget <= 0.01 || ballDist > SpinConfig.CURVE_NEAR && angleDeg(look, fromEye) > SpinConfig.CURVE_LOOK_CONE_DEG) {
			homing = null;
			return d.scale(speed);
		}
		double homeCap = steering ? SpinConfig.TWIN_TURN_DEG : SpinConfig.CURVE_HOMING_TURN_DEG;
		homing = pickTarget(eye, look, pos, d, speed, budget, homeCap);
		Vec3 want;
		double turn;
		if (homing != null) {
			want = targetPoint(homing, pos, speed).subtract(pos).normalize();
			turn = Math.min(homeCap, angleDeg(d, want));
		} else {
			// the crosshair line a little ahead of the ball (Twin Balls: no nearer than their aim point, so they still swoop in)
			double along = Math.max(ballDist + SpinConfig.CURVE_LEAD, steering ? aimDist : 0.0);
			want = eye.add(look.scale(along)).subtract(pos).normalize();
			double off = angleDeg(d, want);
			if (off > SpinConfig.CURVE_GIVE_UP_DEG) {
				return d.scale(speed);
			}
			turn = steering ? Math.min(SpinConfig.TWIN_TURN_DEG, Math.toDegrees(SpinConfig.TWIN_STEER / speed * Math.sin(Math.toRadians(off))) + off * 0.05)
					: Math.min(SpinConfig.CURVE_LOOK_TURN_DEG, off * SpinConfig.CURVE_EASE);
		}
		turn = Math.min(turn, budget);
		Vec3 nd = turnToward(d, want, Math.toRadians(turn));
		turned += (float) angleDeg(d, nd);
		return nd.scale(speed);
	}

	/** The creature under (or right by) the crosshair that this ball can still curve onto, or null. */
	private LivingEntity pickTarget(Vec3 eye, Vec3 look, Vec3 pos, Vec3 d, double speed, double budget, double capDeg) {
		double reach = SpinConfig.BALL_RANGE + 4.0;
		double radius = speed / Math.toRadians(capDeg * SpinConfig.CURVE_REACH_MARGIN);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, eye).inflate(reach),
				this::canHitEntity)) {
			Vec3 c = e.getBoundingBox().getCenter();
			Vec3 toC = c.subtract(eye);
			double dist = toC.length();
			if (dist > reach || dist < 0.5) {
				continue;
			}
			double off = angleDeg(look, toC);
			double size = Math.toDegrees(Math.atan((e.getBbWidth() * 0.5 + SpinConfig.CURVE_AIM_SLACK) / dist));
			double cone = Math.max(SpinConfig.CURVE_AIM_CONE_DEG, size) + (e == homing ? SpinConfig.CURVE_KEEP_EXTRA_DEG : 0.0);
			if (off > cone) {
				continue;
			}
			Vec3 tp = targetPoint(e, pos, speed);
			double need = turnNeeded(pos, d, tp, radius);
			if (need < 0 || Math.toDegrees(need) > Math.min(budget, SpinConfig.CURVE_MAX_HOMING_TURN_DEG)) {
				continue; // too sharp a curve: it bends after the look instead and misses
			}
			if (level().clip(new net.minecraft.world.level.ClipContext(pos, tp, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS) {
				continue; // behind a wall
			}
			double score = off - size - (e == homing ? 1.0 : 0.0);
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}

	/** Where to fly to hit {@code e}: its middle, led by its motion over the time the ball needs (at most half a second). */
	private static Vec3 targetPoint(LivingEntity e, Vec3 pos, double speed) {
		Vec3 c = e.getBoundingBox().getCenter();
		double ticks = Math.min(10.0, c.distanceTo(pos) / Math.max(0.1, speed));
		Vec3 mv = e.getDeltaMovement();
		return c.add(mv.x * ticks, 0.0, mv.z * ticks);
	}

	/**
	 * The turn (radians) a ball at {@code pos} heading {@code d} needs to fly straight at {@code t} after turning at its tightest,
	 * radius {@code r}; -1 if {@code t} lies inside its turning circle (it can't get there). In the plane of the turn, with the
	 * heading along x and the target at (x, y), y >= 0: the circle's centre is (0, r) and the turn phi solves x sin phi + (r - y)
	 * cos phi = r.
	 */
	public static double turnNeeded(Vec3 pos, Vec3 d, Vec3 t, double r) {
		Vec3 to = t.subtract(pos);
		double x = to.dot(d);
		double y = to.subtract(d.scale(x)).length();
		if (to.lengthSqr() < 1.0e-6) {
			return 0.0;
		}
		double a = x;
		double b = r - y;
		double big = Math.sqrt(a * a + b * b);
		if (big < r) {
			return -1.0;
		}
		double phi = Math.asin(Math.min(1.0, r / big)) - Math.atan2(b, a);
		if (phi < -1.0e-9) {
			phi += Math.PI * 2.0;
		}
		return Math.max(0.0, phi);
	}

	/** {@code from} rotated toward {@code to} (both unit) by at most {@code max} radians. */
	static Vec3 turnToward(Vec3 from, Vec3 to, double max) {
		double cos = net.minecraft.util.Mth.clamp(from.dot(to), -1.0, 1.0);
		double ang = Math.acos(cos);
		if (ang <= max || ang < 1.0e-6) {
			return to;
		}
		double s = Math.sin(ang);
		if (s < 1.0e-6) {
			return from; // straight behind: never U-turn
		}
		return from.scale(Math.sin(ang - max) / s).add(to.scale(Math.sin(max) / s)).normalize();
	}

	private static double angleDeg(Vec3 a, Vec3 b) {
		double l = a.length() * b.length();
		if (l < 1.0e-9) {
			return 0.0;
		}
		return Math.toDegrees(Math.acos(net.minecraft.util.Mth.clamp(a.dot(b) / l, -1.0, 1.0)));
	}

	/** For the tests: the creature it is curving onto right now (server). */
	public LivingEntity homing() {
		return homing;
	}

	/** For the tests: degrees turned so far on the way out. */
	public float turned() {
		return turned;
	}

	/** Client: the flight direction blended across the tick (the renderer spins the ball about it). */
	public Vec3 flightDir(float partial) {
		Vec3 b = dirO.add(dir.subtract(dirO).scale(partial));
		return b.lengthSqr() < 1.0e-6 ? dir : b.normalize();
	}

	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpSteps = 1;
	}

	@Override
	public double lerpTargetX() {
		return lerpSteps > 0 ? lerpX : getX();
	}

	@Override
	public double lerpTargetY() {
		return lerpSteps > 0 ? lerpY : getY();
	}

	@Override
	public double lerpTargetZ() {
		return lerpSteps > 0 ? lerpZ : getZ();
	}

	private void startReturn() {
		entityData.set(RETURNING, true);
		homing = null;
		steering = false;
		returnTicks = 0;
	}

	private void catchBall(ServerPlayer p) {
		caught = true;
		level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SpinSounds.BALL_CATCH, SoundSource.PLAYERS, 0.5f, 1.0f);
		discard();
	}

	/**
	 * However it goes (caught, recalled at a death or a world change, its chunk unloading), the ball item goes back into the hand it
	 * was thrown from (or a free slot, see {@link com.projecthero.mod.spin.SpinBalls#giveBack}).
	 */
	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		if (!level().isClientSide() && getOwner() instanceof ServerPlayer p) {
			boolean wasOut = Spin.hasPower(p) && (Spin.state(p).thrown & (1 << index())) != 0;
			Spin.setThrown(p, index(), false);
			if (wasOut && !p.isRemoved()) {
				com.projecthero.mod.spin.SpinBalls.giveBack(p, index(), hand);
			}
		}
	}

	/** The hand it was thrown from (and goes back to). */
	public InteractionHand hand() {
		return hand;
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

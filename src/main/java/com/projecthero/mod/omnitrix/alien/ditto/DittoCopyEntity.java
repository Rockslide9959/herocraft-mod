package com.projecthero.mod.omnitrix.alien.ditto;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.alien.CloneSpacing;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: one of Ditto's copies -- a small, plain (non-living) entity in Ditto's body (the client draws it in his skin, at his size,
 * badge and all: {@code DittoCopyRenderer}). It runs on its own: it falls, climbs whatever it runs into, keeps its own spot in the
 * fan behind its owner ({@link CloneSpacing}), and swarms -- it chases its target (the kit's order, or whatever Ditto last hit or was hit
 * by), takes its own place round it and punches it once a second. It is fragile ({@link #MAX_HP}) and, as in the show, what it feels
 * Ditto feels: half the damage it takes reaches him ({@link DittoKit#feedback}). Never saved; it pops the moment its owner is not a
 * transformed Ditto any more.
 * <p>
 * v0.17.2 round 3: its life is a little state machine, synced so every viewer sees the same thing ({@link #state()},
 * {@link #stateTicks}): {@link #EMERGING} (user: "make the clones almost stretch and pull themselves out of the player and separate from
 * the player"), {@link #RETURNING} then {@link #ABSORBING} (user: "one at a time only and then walk back into the player and merge with
 * them"), {@link #HOISTED} then {@link #FLYING} (the Switcheroo's throw), {@link #ARC} (a leap the kit aims) and {@link #CLINGING} (the
 * Dogpile).
 */
public class DittoCopyEntity extends Entity {
	private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	/** The game tick the state began (a clone waiting its turn in the Ditto Army: a little ahead), and how long it runs. */
	private static final EntityDataAccessor<Integer> STATE_AT = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> STATE_LEN = SynchedEntityData.defineId(DittoCopyEntity.class, EntityDataSerializers.INT);

	public static final int NORMAL = 0, EMERGING = 1, RETURNING = 2, ABSORBING = 3, HOISTED = 4, FLYING = 5, ARC = 6, CLINGING = 7;

	/** Client: the last swing's start (entity event 4), and the Copy Toss heave's (entity event 5). */
	public long swingAt = -100, heaveAt = -100;
	/** Client: the last hit's time (entity event 2), for the red flash. */
	public long hurtAt = -100;

	public static final float MAX_HP = 6f;
	/** Ticks between two punches of one copy (half that in the Ditto Army). */
	public static final int PUNCH_EVERY = 20;
	public static final double RUN = 0.34, WALK = 0.2;
	/** Ticks for a copy to pull itself out of him: a plain split, and the quick one of Split Burst and the Ditto Army. */
	public static final int EMERGE_TICKS = 18, EMERGE_FAST = 11;
	/** A walking copy starts sinking into him this close; the sinking takes {@link #ABSORB_TICKS}. */
	public static final double ABSORB_AT = 0.8;
	public static final int ABSORB_TICKS = 10;
	/** A walker that cannot get back in this time (a wall, a drop) is drawn in anyway. */
	public static final int RETURN_MAX = 120;
	/** The Switcheroo: ticks over his head before the throw, the throw's speed and the longest flight. */
	public static final int HOIST_TICKS = 7, FLY_MAX = 60;
	public static final double THROW_SPEED = 1.3, THROW_GRAVITY = 0.05;

	private float hp = MAX_HP;
	@Nullable
	private LivingEntity target;
	private long targetUntil;
	@Nullable
	private Vec3 goal;
	private int goalTicks;
	private int punchCooldown;
	/** {@link #EMERGING}: where it ends up, from its owner. {@link #ABSORBING}: where it started from him. */
	private Vec3 offset = Vec3.ZERO;
	/** {@link #ARC}: the leap, and what it does on landing. */
	private Vec3 arcFrom = Vec3.ZERO, arcTo = Vec3.ZERO;
	private double arcHeight;
	@Nullable
	private UUID clingTo;
	private int clingTicks;
	private Vec3 clingAt = Vec3.ZERO;

	public DittoCopyEntity(EntityType<? extends DittoCopyEntity> type, Level level) {
		super(type, level);
	}

	/** A new copy starts pulling itself out of {@code owner} toward {@code at}, {@code delay} ticks from now, taking {@code ticks}. */
	public static DittoCopyEntity spawn(Player owner, int slot, Vec3 at, int delay, int ticks) {
		DittoCopyEntity e = new DittoCopyEntity(DittoKit.COPY, owner.level());
		e.entityData.set(OWNER, owner.getId());
		e.entityData.set(SLOT, slot);
		e.offset = at.subtract(owner.position());
		float yaw = (float) (Mth.atan2(-e.offset.x, e.offset.z) * Mth.RAD_TO_DEG); // it pulls away from him, looking where it goes
		e.moveTo(owner.getX(), owner.getY(), owner.getZ(), yaw, 0f);
		e.yRotO = yaw;
		e.punchCooldown = slot * 3; // staggered, so a pack's punches land one after another
		e.state(EMERGING, delay, ticks);
		owner.level().addFreshEntity(e);
		return e;
	}

	public int ownerId() {
		return entityData.get(OWNER);
	}

	public int slot() {
		return entityData.get(SLOT);
	}

	public int state() {
		return entityData.get(STATE);
	}

	public int stateLength() {
		return entityData.get(STATE_LEN);
	}

	/** Ticks since the state began (negative while it waits its turn). */
	public float stateTicks(float partial) {
		return (float) (level().getGameTime() - entityData.get(STATE_AT)) + partial;
	}

	private void state(int state, int delay, int ticks) {
		entityData.set(STATE, state);
		entityData.set(STATE_AT, (int) level().getGameTime() + delay);
		entityData.set(STATE_LEN, ticks);
	}

	/** It is on its way back into him, or sinking in. */
	public boolean returning() {
		int s = state();
		return s == RETURNING || s == ABSORBING;
	}

	/** On a job that takes it out of the pack: walking back in, being thrown. */
	public boolean busy() {
		int s = state();
		return s == RETURNING || s == ABSORBING || s == HOISTED || s == FLYING;
	}

	/** Mid-air or part of him: nothing can hit it. */
	private boolean untouchable() {
		int s = state();
		return s == EMERGING || s == ABSORBING || s == HOISTED || s == FLYING;
	}

	public float hp() {
		return hp;
	}

	@Nullable
	public Player owner() {
		return level().getEntity(ownerId()) instanceof Player p ? p : null;
	}

	@Nullable
	public LivingEntity target() {
		return target;
	}

	/** Swarm {@code e} for {@code ticks}. */
	public void attack(LivingEntity e, int ticks) {
		if (busy()) {
			return;
		}
		target = e;
		targetUntil = level().getGameTime() + ticks;
		goal = null;
	}

	/** Runs (or leaps) to {@code at} for {@code ticks}, ignoring targets meanwhile. */
	public void dashTo(Vec3 at, int ticks) {
		if (busy()) {
			return;
		}
		goal = at;
		goalTicks = ticks;
	}

	/** Leaps to {@code at} in {@code ticks}, {@code height} over the straight line at the top. */
	public void leapTo(Vec3 at, int ticks, double height) {
		if (busy()) {
			return;
		}
		arcFrom = position();
		arcTo = at;
		arcHeight = height;
		clingTo = null;
		goal = null;
		state(ARC, 0, ticks);
	}

	/** Leaps onto {@code e} in {@code ticks} and hangs on for {@code hold} more (the Dogpile), {@code at} from its feet. */
	public void pounce(LivingEntity e, Vec3 at, int ticks, int hold) {
		if (busy()) {
			return;
		}
		leapTo(e.position().add(at), ticks, 1.0);
		clingTo = e.getUUID();
		clingAt = at;
		clingTicks = hold;
	}

	/** Shift+V: it stops what it is doing and walks back to him. */
	public void walkBack() {
		target = null;
		goal = null;
		clingTo = null;
		state(RETURNING, 0, RETURN_MAX);
	}

	/** Shift+C: it hops up over his head to be thrown. */
	public void hoist() {
		target = null;
		goal = null;
		clingTo = null;
		arcFrom = position();
		state(HOISTED, 0, HOIST_TICKS);
	}

	/** The spot in the fan behind its owner that is its own. */
	public Vec3 packPos(Player owner) {
		return CloneSpacing.fan(owner, slot());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(OWNER, -1);
		builder.define(SLOT, 0);
		builder.define(STATE, NORMAL);
		builder.define(STATE_AT, 0);
		builder.define(STATE_LEN, 0);
	}

	@Override
	public float maxUpStep() {
		return 0.6f;
	}

	private static float smooth(float x) {
		float c = Mth.clamp(x, 0f, 1f);
		return c * c * (3f - 2f * c);
	}

	/** How far through its current state it is, 0..1. */
	private float progress() {
		return Mth.clamp(stateTicks(0f) / Math.max(1, stateLength()), 0f, 1f);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		Player owner = owner();
		if (!(owner instanceof ServerPlayer sp) || !owner.isAlive() || !DittoKit.isDitto(owner) || owner.level() != level()) {
			pop();
			return;
		}
		int s = state();
		if (s != FLYING && s != ARC && distanceToSqr(owner) > 28 * 28) {
			if (s == EMERGING || s == NORMAL || s == CLINGING) {
				state(NORMAL, 0, 0);
				Vec3 at = packPos(owner);
				setPos(at.x, owner.getY(), at.z); // left far behind: it catches up (a quick Splixson)
				setDeltaMovement(Vec3.ZERO);
			}
		}
		switch (s) {
			case EMERGING -> tickEmerging(owner);
			case RETURNING -> tickReturning(sp);
			case ABSORBING -> tickAbsorbing(sp);
			case HOISTED -> tickHoisted(sp);
			case FLYING -> tickFlying(sp);
			case ARC -> tickArc();
			case CLINGING -> tickClinging(sp);
			default -> tickPack(sp);
		}
	}

	/** It follows him as it pulls clear, however he moves: a slow, straining pull like taffy, then a quick last step as the strand lets go. */
	private void tickEmerging(Player owner) {
		float u = progress();
		if (stateTicks(0f) < 0) {
			setPos(owner.position());
			return;
		}
		double k = u < 0.2f ? 0.14 * (u / 0.2f) : u < 0.68f ? 0.14 + 0.5 * smooth((u - 0.2f) / 0.48f) : 0.64 + 0.36 * smooth((u - 0.68f) / 0.2f);
		setPos(owner.position().add(offset.scale(k)));
		if (u >= 1f) {
			state(NORMAL, 0, 0);
		}
	}

	private void tickReturning(ServerPlayer sp) {
		double dist = Math.sqrt(distanceToSqr(sp));
		if (dist < ABSORB_AT || stateTicks(0f) >= RETURN_MAX) {
			offset = position().subtract(sp.position());
			state(ABSORBING, 0, ABSORB_TICKS);
			setDeltaMovement(Vec3.ZERO);
			DittoKit.absorbing(sp);
			return;
		}
		steer(sp, sp.position(), dist > 8 ? RUN : WALK, 0.1, Vec3.ZERO);
		face(sp.position().subtract(position()), sp);
	}

	private void tickAbsorbing(ServerPlayer sp) {
		float u = progress();
		setPos(sp.position().add(offset.scale(1f - u * u)));
		setYRot((float) (Mth.atan2(offset.x, -offset.z) * Mth.RAD_TO_DEG)); // looking at him as it goes in
		if (u >= 1f) {
			DittoKit.absorbed(sp);
			discard();
		}
	}

	/** Over his head, flat on his hands, until the throw. */
	private void tickHoisted(ServerPlayer sp) {
		float u = smooth(progress() * 1.8f);
		setPos(arcFrom.lerp(sp.position().add(0, sp.getBbHeight() + 0.1, 0), u));
		setYRot(sp.getYRot());
		if (progress() >= 1f) {
			Vec3 look = sp.getLookAngle();
			setDeltaMovement(look.x * THROW_SPEED, look.y * THROW_SPEED + 0.22, look.z * THROW_SPEED);
			state(FLYING, 0, FLY_MAX);
		}
	}

	private void tickFlying(ServerPlayer sp) {
		Vec3 v = getDeltaMovement();
		LivingEntity hit = null;
		for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().expandTowards(v).inflate(0.2))) {
			if (e != sp && DittoKit.fair(sp, e)) {
				hit = e;
				break;
			}
		}
		move(MoverType.SELF, v);
		setDeltaMovement(v.x, v.y - THROW_GRAVITY, v.z);
		setYRot((float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG));
		fallDistance = 0f;
		if (hit != null || onGround() || horizontalCollision || verticalCollision || isInWaterOrBubble()) {
			state(NORMAL, 0, 0);
			setDeltaMovement(Vec3.ZERO);
			DittoKit.thrownLanded(sp, this, hit);
		} else if (stateTicks(0f) >= FLY_MAX || getY() < level().getMinBuildHeight() + 1) {
			pop(); // thrown off the world: nothing to swap with
		}
	}

	private void tickArc() {
		float u = progress();
		Vec3 at = arcFrom.lerp(arcTo, u).add(0, 4.0 * arcHeight * u * (1f - u), 0);
		Vec3 d = arcTo.subtract(arcFrom);
		if (d.horizontalDistanceSqr() > 0.01) {
			setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
		}
		setPos(at);
		fallDistance = 0f;
		if (u >= 1f) {
			setDeltaMovement(Vec3.ZERO);
			if (clingTo != null && clingTicks > 0 && level() instanceof ServerLevel l && l.getEntity(clingTo) instanceof LivingEntity) {
				state(CLINGING, 0, clingTicks);
			} else {
				clingTo = null;
				state(NORMAL, 0, 0);
			}
		}
	}

	/** Hanging on to the creature it pounced on, its own spot on the pile, punching. */
	private void tickClinging(ServerPlayer sp) {
		LivingEntity on = clingTo != null && level() instanceof ServerLevel l && l.getEntity(clingTo) instanceof LivingEntity e ? e : null;
		if (on == null || !on.isAlive() || stateTicks(0f) >= stateLength()) {
			clingTo = null;
			state(NORMAL, 0, 0);
			return;
		}
		setPos(on.position().add(clingAt));
		Vec3 in = on.position().subtract(position());
		if (in.horizontalDistanceSqr() > 0.001) {
			setYRot((float) (Mth.atan2(-in.x, in.z) * Mth.RAD_TO_DEG));
		}
		if (punchCooldown > 0) {
			punchCooldown--;
		} else {
			punchCooldown = PUNCH_EVERY;
			punch(sp, on);
		}
	}

	private void punch(ServerPlayer sp, LivingEntity e) {
		level().broadcastEntityEvent(this, (byte) 4);
		e.invulnerableTime = 0; // a pack hits as a pack
		DittoKit.copyHit(sp, this, e);
	}

	/** Runs for {@code to}, climbing what it runs into; {@code push} keeps it off its brothers. */
	private void steer(ServerPlayer sp, Vec3 to, double speed, double stop, Vec3 push) {
		Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
		double dist = d.length();
		Vec3 v = getDeltaMovement();
		double vx = push.x, vz = push.z;
		if (dist > stop) {
			Vec3 n = d.scale(1.0 / dist);
			double s = Math.min(speed, dist - stop + 0.05);
			vx += n.x * s;
			vz += n.z * s;
		}
		double vy = v.y;
		if (horizontalCollision && dist > stop) {
			vy = 0.22; // a great climber: up whatever it runs into
		} else if (!onGround()) {
			vy = (vy - 0.08) * 0.98;
		} else if (vy < 0) {
			vy = 0;
		}
		if (goal != null && onGround() && to.y > getY() + 0.8) {
			vy = 0.5; // a hop up to the goal
		}
		setDeltaMovement(vx, vy, vz);
		move(MoverType.SELF, getDeltaMovement());
		fallDistance = 0f;
	}

	private void face(Vec3 way, Player owner) {
		float yaw = way.horizontalDistanceSqr() > 0.01 ? (float) (Mth.atan2(-way.x, way.z) * Mth.RAD_TO_DEG) : owner.getYRot();
		setYRot(Mth.rotLerp(0.5f, getYRot(), yaw));
		setXRot(0f);
	}

	private void tickPack(ServerPlayer sp) {
		long now = level().getGameTime();
		if (punchCooldown > 0) {
			punchCooldown--;
		}
		// whom to swarm: the kit's order, else whatever Ditto just fought
		if (target != null && (!target.isAlive() || target.isRemoved() || now > targetUntil || target.distanceToSqr(sp) > 24 * 24)) {
			target = null;
		}
		LivingEntity fight = target != null ? target : DittoKit.foe(sp);
		Vec3 to;
		double speed;
		if (goal != null && goalTicks > 0) {
			goalTicks--;
			to = goal;
			speed = RUN;
		} else {
			goal = null;
			if (fight != null) {
				// its own place round the creature, never the same spot as a brother
				to = CloneSpacing.ring(fight.position(), slot(), fight.getBbWidth() * 0.5 + 0.45);
				speed = RUN;
			} else {
				to = CloneSpacing.clear(level(), getType(), packPos(sp), sp.position());
				speed = distanceToSqr(to) > 9 ? RUN : WALK;
			}
		}
		var brothers = DittoKit.copies(sp);
		Vec3 away = CloneSpacing.apart(this, brothers);
		// nearly there and a brother is in the way (a wall has squeezed two spots together): it stops rather than shoulder in
		boolean give = fight == null && goal == null && CloneSpacing.crowded(this, brothers);
		steer(sp, to, speed, give ? 0.9 : 0.12, away);
		// face where it runs, or its foe, or where its owner looks
		Vec3 d = new Vec3(to.x - getX(), 0, to.z - getZ());
		boolean settled = d.length() <= 0.27;
		face(fight != null && goal == null ? fight.position().subtract(position()) : settled ? Vec3.ZERO : d, sp);
		// a punch when in reach
		if (fight != null && goal == null && punchCooldown <= 0 && reach(fight)) {
			punchCooldown = DittoKit.frenzied(sp) ? PUNCH_EVERY / 2 : PUNCH_EVERY;
			punch(sp, fight);
		}
	}

	/** Client: ticks of the red hit flash left. */
	public int hurtTime() {
		return (int) Math.max(0, 8 - (level().getGameTime() - hurtAt));
	}

	/** Its fists reach {@code e} (a small alien's lunge: the back of the ring reaches past the front). */
	public boolean reach(LivingEntity e) {
		return getBoundingBox().inflate(1.1, 0.3, 1.1).intersects(e.getBoundingBox());
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 4) {
			swingAt = level().getGameTime();
			return;
		}
		if (id == 5) {
			heaveAt = level().getGameTime();
			return;
		}
		if (id == 2) {
			hurtAt = level().getGameTime();
			return;
		}
		super.handleEntityEvent(id);
	}

	/** Gone in a puff (its owner turned back, or it broke). */
	public void pop() {
		if (level() instanceof ServerLevel l && !isRemoved()) {
			l.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.4, getZ(), 5, 0.12, 0.2, 0.12, 0.01);
		}
		discard();
	}

	@Override
	public boolean isPickable() {
		return !untouchable();
	}

	@Override
	public boolean canBeHitByProjectile() {
		return isAlive() && !untouchable();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved() || isInvulnerableTo(source) || amount <= 0f || untouchable()) {
			return false;
		}
		Entity by = source.getEntity();
		Player owner = owner();
		if (by != null && (by.getId() == ownerId() || by instanceof DittoCopyEntity)) {
			return false; // never his own fists, nor a brother's
		}
		if (owner != null && by != null && com.projecthero.mod.squad.Squads.areAllies(owner, by) && !com.projecthero.mod.squad.Squads.friendlyFireOn(owner)) {
			return false;
		}
		float taken = Math.min(amount, hp);
		level().broadcastEntityEvent(this, (byte) 2);
		hp -= amount;
		if (owner instanceof ServerPlayer sp) {
			DittoKit.feedback(sp, taken);
		}
		if (by instanceof LivingEntity le && le.isAlive() && le != owner) {
			attack(le, 100); // hit back
		}
		if (hp <= 0f) {
			pop();
		}
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}
}

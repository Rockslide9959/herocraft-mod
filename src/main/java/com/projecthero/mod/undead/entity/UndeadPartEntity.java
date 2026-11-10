package com.projecthero.mod.undead.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: a piece of Undead flying as a weapon -- a fingertip (Parts Bullet and its kin), his twisted arm (Ten Twists: Vortex
 * Bullet, a spinning drill) or his own head (Parts Bullet Head). Drawn with the owner's own skin. A finger is spent on the
 * first hit; the arm drills through up to {@link UndeadConfig#VORTEX_PIERCE} and sticks in the wall it reaches; the head knocks
 * into its target, drops and lies there. A launched arm or head lasts until the part regrows on his body (then it bursts into
 * pixels) and never more than {@link UndeadConfig#SEVERED_LIFE}. Never saved.
 *
 * <h2>Pieces left behind: {@link #leaveBehind}</h2>
 * v0.17.3 (user: "When player ejects a body part make the old limbs stay in place, 0.5 seconds later the limb falls over, 0.5
 * seconds later the part dissolves quickly over the course of 1 second"). A piece of his body that comes off and is NOT thrown --
 * the X launches' legs / body, an ejected limb, the end of a limb that bursts off in flight -- is spawned with
 * <pre>
 *   UndeadPartEntity.leaveBehind(ServerPlayer owner, int part, float fromRow, float toRow, Vec3 at)
 * </pre>
 * <ul>
 * <li>{@code part}: {@link UndeadState#HEAD}..{@link UndeadState#LEFT_LEG} (the six parts: a hand / foot is the end slice of its
 * arm / leg, rows 8..12 -- {@code UndeadState.limbOf(seg)}).</li>
 * <li>{@code fromRow}..{@code toRow}: which slice of the part, in model px rows from its joint, 0..12 for an arm / leg / the body
 * (shoulder / hip / neck down to the hand / foot / hips), 0..8 for the head (neck up). {@code 0, 12} (head {@code 0, 8}) is the
 * whole part; {@code 6, 12} half a forearm and the hand; {@code 8, 12} the hand / foot alone ({@code UndeadConfig.HAND_ROWS} = 4).
 * A cut face (a slice end that is not the part's own end) shows a raw cross-section.</li>
 * <li>{@code at}: the owner's FEET position at the moment it came off (normally {@code owner.position()}); the piece is drawn
 * exactly where that part sits on a standing body there, facing the owner's body yaw ({@code owner.yBodyRot}).</li>
 * </ul>
 * Its life is fixed ({@link #LEFT_STAND}, {@link #LEFT_FALL}, {@link #LEFT_DISSOLVE}): it stands perfectly still where it came
 * off for 0.5 s; then it tips over and drops to the ground (0.5 s, falling with gravity if it came off in the air) -- a leg splays
 * back and out, an arm falls out to its side, the body and head fall back; then it erodes away texel by texel over 1 s (each skin
 * pixel shrinking to nothing in a scattered order -- no burst, no falling pixels) and is gone. It never hits anything, is never
 * cut short by the part regrowing on his body, and doesn't burst when it goes. The caller still breaks the part on his body
 * ({@code Undead.breakPart}). The old {@link #drop} (the X launches' {@link #STYLE_LEFT_LEG} / {@link #STYLE_LEFT_BODY} /
 * {@link #STYLE_LEFT_BODY_LEGLESS}) runs the same life.
 */
public class UndeadPartEntity extends Projectile {
	public static final int STYLE_FINGER = 0;
	public static final int STYLE_CRIMSON = 1;
	public static final int STYLE_VULCAN = 2;
	public static final int STYLE_DIVISION = 3;
	public static final int STYLE_DRILL = 4;
	public static final int STYLE_HEAD = 5;
	/**
	 * v0.17.3 X launches: a part left behind where he stood, standing as it was on his body, then toppling over. A leg from the Leg
	 * Launch splays to its side; the body (and limbs) from the Head Launch fall back as one; LEGLESS draws the body sitting on
	 * the ground (the legs were already gone). Never hits anything; lies there until the part regrows, then bursts into pixels.
	 */
	public static final int STYLE_LEFT_LEG = 6;
	public static final int STYLE_LEFT_BODY = 7;
	public static final int STYLE_LEFT_BODY_LEGLESS = 8;
	/** v0.17.3: the stretch punch -- the whole arm thrown fist first off the body; it drops where it hits. */
	public static final int STYLE_PUNCH = 9;
	/** v0.17.3 Bone Sword: a bone shard of the Splinter Volley (spent on the first hit), and the sword thrown by Impale Throw. */
	public static final int STYLE_SPLINTER = 10;
	public static final int STYLE_SWORD = 11;
	/** v0.17.3: any piece (or slice) left behind where it came off ({@link #leaveBehind}). */
	public static final int STYLE_LEFT_PART = 12;

	/** A left-behind piece stands still this long where it came off (ticks: 0.5 s), ... */
	public static final int LEFT_STAND = 10;
	/** ... then tips over and drops to the ground over this long (0.5 s), ... */
	public static final int LEFT_FALL = 10;
	/** ... then erodes away pixel by pixel over this long (1 s) and is gone. */
	public static final int LEFT_DISSOLVE = 20;
	public static final int LEFT_LIFE = LEFT_STAND + LEFT_FALL + LEFT_DISSOLVE;

	/** Set by the client: bursts the piece into pixels as it disappears. */
	public static Consumer<UndeadPartEntity> clientRemoval = e -> {
	};

	private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.17.3: the slice of the part a left-behind piece is (rows from the joint; -1 = to the part's own end). */
	private static final EntityDataAccessor<Float> FROM_ROW = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> TO_ROW = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.FLOAT);

	private final Set<Integer> struck = new HashSet<>();
	private int life;
	private float damage;
	private boolean falling;

	public UndeadPartEntity(EntityType<? extends UndeadPartEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public static UndeadPartEntity shoot(ServerPlayer owner, int style, int part, Vec3 from, Vec3 velocity, float damage) {
		UndeadPartEntity e = new UndeadPartEntity(UndeadEntityTypes.PART, owner.level());
		e.setOwner(owner);
		e.entityData.set(STYLE, style);
		e.entityData.set(PART, part);
		e.damage = damage;
		// v0.17.3: {@code from} is where the piece's middle starts (it is drawn and hits round the middle of its box), so the
		// box's foot goes half its height lower -- otherwise everything flew half a box above the line it was aimed along
		e.moveTo(from.x, from.y - e.getBbHeight() * 0.5, from.z, 0f, 0f);
		e.setDeltaMovement(velocity);
		e.faceVelocity(velocity);
		owner.level().addFreshEntity(e);
		return e;
	}

	/**
	 * A part left standing at {@code at} (his feet), facing {@code bodyYaw}. A left-behind style ({@link #STYLE_LEFT_LEG} ...)
	 * runs the stand / fall / dissolve life of {@link #leaveBehind} (and ignores {@code velocity}: it stays exactly where it came
	 * off); anything else falls and rests under gravity.
	 */
	public static UndeadPartEntity drop(ServerPlayer owner, int part, int style, Vec3 at, float bodyYaw, Vec3 velocity) {
		UndeadPartEntity e = new UndeadPartEntity(UndeadEntityTypes.PART, owner.level());
		e.setOwner(owner);
		e.entityData.set(STYLE, style);
		e.entityData.set(PART, part);
		e.falling = true;
		e.moveTo(at.x, at.y, at.z, bodyYaw, 0f);
		if (e.isLeftBehind()) {
			e.setDeltaMovement(Vec3.ZERO); // v0.17.3 (user): "make the old limbs stay in place"
		} else {
			e.setNoGravity(false);
			e.setDeltaMovement(velocity);
		}
		owner.level().addFreshEntity(e);
		return e;
	}

	/**
	 * v0.17.3: a piece of {@code owner}'s body left behind where it came off -- see the class javadoc. {@code part} is
	 * {@link UndeadState#HEAD}..{@link UndeadState#LEFT_LEG}; {@code fromRow}..{@code toRow} the slice of it (rows from its joint,
	 * 0..12, the head 0..8; clamped); {@code at} the owner's feet when it came off. It stands {@link #LEFT_STAND}, falls over
	 * {@link #LEFT_FALL}, dissolves over {@link #LEFT_DISSOLVE} and is gone. Breaking the part on his body is the caller's job.
	 */
	public static UndeadPartEntity leaveBehind(ServerPlayer owner, int part, float fromRow, float toRow, Vec3 at) {
		if (part < UndeadState.HEAD || part > UndeadState.LEFT_LEG) {
			throw new IllegalArgumentException("not a part: " + part);
		}
		float h = part == UndeadState.HEAD ? 8f : 12f;
		float from = Mth.clamp(fromRow, 0f, h), to = Mth.clamp(toRow, 0f, h);
		UndeadPartEntity e = drop(owner, part, STYLE_LEFT_PART, at, owner.yBodyRot, Vec3.ZERO);
		e.entityData.set(FROM_ROW, Math.min(from, to));
		e.entityData.set(TO_ROW, Math.max(from, to));
		return e;
	}

	public boolean isLeftBehind() {
		// v0.17.3: only the pieces left where they came off (6-8, 12) -- the thrown arm (9) used to fall in here and was drawn
		// standing upright as if still on the body, so it flew through the air vertical
		int s = this.style();
		return s >= STYLE_LEFT_LEG && s <= STYLE_LEFT_BODY_LEGLESS || s == STYLE_LEFT_PART;
	}

	/** The first row of the slice a left-behind piece is (rows from the part's joint). */
	public float fromRow() {
		return this.entityData.get(FROM_ROW);
	}

	/** The row the slice ends at (the part's own end for a whole part: 12, the head 8). */
	public float toRow() {
		float to = this.entityData.get(TO_ROW);
		return to < 0f ? (this.bodyPart() == UndeadState.HEAD ? 8f : 12f) : to;
	}

	/** Ticks since a left-behind piece came off (client and server; {@code partial} for the frame). */
	public float leftAge(float partial) {
		return this.tickCount + partial;
	}

	/** 0..1 through the topple ({@link #LEFT_STAND} .. + {@link #LEFT_FALL}). */
	public static float fallProgress(float age) {
		return Mth.clamp((age - LEFT_STAND) / LEFT_FALL, 0f, 1f);
	}

	/** 0..1 through the dissolve (the last {@link #LEFT_DISSOLVE} ticks). */
	public static float dissolveProgress(float age) {
		return Mth.clamp((age - LEFT_STAND - LEFT_FALL) / LEFT_DISSOLVE, 0f, 1f);
	}

	public boolean isSplinter() {
		return this.style() == STYLE_SPLINTER;
	}

	public boolean isSword() {
		return this.style() == STYLE_SWORD;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(STYLE, STYLE_FINGER);
		builder.define(PART, -1);
		builder.define(STUCK, false);
		builder.define(FROM_ROW, 0f);
		builder.define(TO_ROW, -1f);
	}

	public int style() {
		return this.entityData.get(STYLE);
	}

	/** The body part this is ({@link UndeadState#HEAD}, an arm), or -1 for a finger. */
	public int bodyPart() {
		return this.entityData.get(PART);
	}

	public boolean stuck() {
		return this.entityData.get(STUCK);
	}

	public boolean ownerIs(Player p) {
		Entity o = this.getOwner();
		return o != null && o.getUUID().equals(p.getUUID());
	}

	public boolean isFinger() {
		return this.style() <= STYLE_DIVISION;
	}

	private void faceVelocity(Vec3 v) {
		if (v.lengthSqr() < 1.0e-6) {
			return;
		}
		double flat = Math.sqrt(v.x * v.x + v.z * v.z);
		this.setYRot((float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG));
		this.setXRot((float) (Mth.atan2(v.y, flat) * Mth.RAD_TO_DEG));
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
	}

	private int maxLife() {
		return switch (this.style()) {
			case STYLE_LEFT_LEG, STYLE_LEFT_BODY, STYLE_LEFT_BODY_LEGLESS, STYLE_LEFT_PART -> LEFT_LIFE;
			case STYLE_DRILL, STYLE_HEAD, STYLE_PUNCH -> UndeadConfig.SEVERED_LIFE;
			case STYLE_CRIMSON, STYLE_SWORD -> 60;
			default -> 40;
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			Vec3 v = this.getDeltaMovement();
			if (!this.stuck() && !this.isLeftBehind()) { // (a left-behind piece only moves by the server's updates)
				this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
			}
			return;
		}
		if (++this.life > this.maxLife() || !(this.getOwner() instanceof ServerPlayer owner) || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (this.isLeftBehind()) {
			// v0.17.3 (leaveBehind): perfectly still for LEFT_STAND, then down to the ground under gravity while it tips over
			// (the topple itself is drawn), then it dissolves where it lies; the regrowth on his body never cuts it short
			if (this.life <= LEFT_STAND) {
				this.setDeltaMovement(Vec3.ZERO);
				return;
			}
			Vec3 v = this.getDeltaMovement();
			if (!this.onGround()) {
				this.move(MoverType.SELF, new Vec3(0, Math.max(-1.5, v.y - 0.08), 0));
				this.setDeltaMovement(new Vec3(0, this.onGround() ? 0 : Math.max(-1.5, v.y - 0.08), 0));
				this.hurtMarked = true;
			}
			return;
		}
		if (this.bodyPart() >= 0 && !Undead.missing(owner, this.bodyPart())) {
			this.discard(); // regrown (or the power ended)
			return;
		}
		if (this.stuck()) {
			return;
		}
		Vec3 v = this.getDeltaMovement();
		if (this.falling) {
			// the head after its hit: a dropped ball that rolls to a stop
			this.move(MoverType.SELF, v);
			v = this.getDeltaMovement();
			if (this.onGround()) {
				v = new Vec3(v.x * 0.6, Math.abs(v.y) > 0.12 ? -v.y * 0.35 : 0, v.z * 0.6);
			} else {
				v = new Vec3(v.x * 0.97, v.y - 0.045, v.z * 0.97);
			}
			this.setDeltaMovement(v);
			this.hurtMarked = true;
			return;
		}
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
		if (hit.getType() != HitResult.Type.MISS) {
			this.onHit(hit);
			if (this.isRemoved() || this.stuck() || this.falling) {
				return;
			}
			v = this.getDeltaMovement();
		}
		if (this.style() == STYLE_DRILL && this.level() instanceof ServerLevel level && this.life % 2 == 0) {
			level.sendParticles(Undead.BLOOD, this.getX(), this.getY(), this.getZ(), 2, 0.12, 0.12, 0.12, 0.0);
		} else if (this.style() == STYLE_PUNCH && this.level() instanceof ServerLevel level) {
			// blood from the shoulder end, trailing (the arm is drawn round the middle of the box, fist leading)
			Vec3 back = v.normalize().scale(-0.4);
			double mid = this.getBbHeight() * 0.5;
			level.sendParticles(Undead.BLOOD, this.getX() + back.x, this.getY() + mid + back.y, this.getZ() + back.z, 3, 0.04, 0.04, 0.04, 0.0);
			if (this.life % 2 == 0) {
				level.sendParticles(Undead.BLOOD_BIG, this.getX() + back.x, this.getY() + mid + back.y, this.getZ() + back.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		} else if (this.style() == STYLE_SWORD && this.level() instanceof ServerLevel level && this.life % 2 == 0) {
			level.sendParticles(Undead.BLOOD, this.getX(), this.getY() + this.getBbHeight() * 0.5, this.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		} else if (this.style() == STYLE_CRIMSON && this.level() instanceof ServerLevel level) {
			level.sendParticles(Undead.BLOOD_BIG, this.getX(), this.getY(), this.getZ(), 2, 0.05, 0.05, 0.05, 0.0);
		}
		this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
		this.faceVelocity(v);
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		if (!super.canHitEntity(e) || !(e instanceof LivingEntity le) || this.struck.contains(e.getId())) {
			return false;
		}
		return !(this.getOwner() instanceof ServerPlayer p) || com.projecthero.mod.combat.HeroTargets.canHarm(p, le);
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (!(this.getOwner() instanceof ServerPlayer p) || !(result.getEntity() instanceof LivingEntity le)) {
			return;
		}
		this.struck.add(le.getId());
		Vec3 v = this.getDeltaMovement();
		switch (this.style()) {
			case STYLE_DRILL -> {
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.6, 0.15);
				this.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 0.6f);
				if (this.struck.size() >= UndeadConfig.VORTEX_PIERCE) {
					this.setDeltaMovement(v.scale(0.8));
				}
			}
			case STYLE_PUNCH -> {
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 1.5, 0.3);
				this.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
				this.falling = true;
				this.setNoGravity(false);
				this.setDeltaMovement(v.scale(-0.15).add(0, 0.2, 0));
			}
			case STYLE_HEAD -> {
				com.projecthero.mod.undead.UndeadLaunch.headHit(p, le, this.position().subtract(v), this.damage); // shared with the Head Launch (sound in it)
				this.falling = true;
				this.setNoGravity(false);
				this.setDeltaMovement(v.scale(-0.25).add(0, 0.25, 0));
			}
			case STYLE_CRIMSON -> {
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 1.6, 0.25);
				com.projecthero.mod.undead.UndeadSfx.crimsonImpact(this.level(), le.position().add(0, le.getBbHeight() * 0.5, 0)); // v0.17.3 Undead sounds
			}
			case STYLE_DIVISION -> {
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.3, 0.0);
				UndeadAbilities.divisionClone(p, this.position());
				this.discard();
			}
			case STYLE_SWORD -> {
				// Impale Throw: run through and pinned; the sword bursts into blood and bone
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.2, 0.0);
				com.projecthero.mod.undead.UndeadBoneSword.pin(p, le);
				com.projecthero.mod.undead.UndeadSfx.swordHit(this.level(), this.position());
				com.projecthero.mod.undead.UndeadSfx.bloodBurst(this.level(), this.position(), 0.5f);
				this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.9f, 1.1f);
				this.discard();
			}
			default -> {
				UndeadAbilities.strike(p, le, this.position().subtract(v), this.damage, 0.25, 0.0);
				com.projecthero.mod.undead.UndeadSfx.splat(this.level(), this.position()); // v0.17.3 Undead sounds
				this.discard();
			}
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		Vec3 at = result.getLocation();
		switch (this.style()) {
			case STYLE_DRILL -> {
				Vec3 v = this.getDeltaMovement().normalize();
				this.setPos(at.x - v.x * 0.25, at.y - v.y * 0.25, at.z - v.z * 0.25);
				this.setDeltaMovement(Vec3.ZERO);
				this.entityData.set(STUCK, true);
				this.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			case STYLE_HEAD, STYLE_PUNCH -> {
				this.falling = true;
				this.setNoGravity(false);
				Vec3 v = this.getDeltaMovement();
				this.setDeltaMovement(v.scale(-0.2).add(0, 0.15, 0));
				com.projecthero.mod.undead.UndeadSfx.headRoll(this.level(), at, (float) Math.min(1.0, v.length())); // v0.17.3 Undead sounds
			}
			case STYLE_SWORD -> {
				// stuck in the wall a moment, then it crumbles (the life runs out)
				Vec3 v = this.getDeltaMovement().normalize();
				this.setPos(at.x - v.x * 0.3, at.y - v.y * 0.3 - this.getBbHeight() * 0.5, at.z - v.z * 0.3);
				this.setDeltaMovement(Vec3.ZERO);
				this.entityData.set(STUCK, true);
				this.life = Math.max(this.life, 40);
				this.level().playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.8f, 1.2f);
			}
			case STYLE_DIVISION -> {
				if (this.getOwner() instanceof ServerPlayer p) {
					UndeadAbilities.divisionClone(p, at.add(0, 0.1, 0));
				}
				this.discard();
			}
			default -> this.discard();
		}
	}

	@Override
	public void onClientRemoval() {
		clientRemoval.accept(this);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128 * 128;
	}

	/** v0.17.3 (tester: "A way people can naturally pick up the head"): the thrown head can be clicked once it is down. */
	@Override
	public boolean isPickable() {
		return this.style() == STYLE_HEAD;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	/**
	 * v0.17.3: right-click the head lying about (the owner, or anyone helping him) and it is picked up and set back on his
	 * shoulders at once.
	 */
	@Override
	public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand) {
		if (this.style() != STYLE_HEAD) {
			return net.minecraft.world.InteractionResult.PASS;
		}
		if (this.level().isClientSide) {
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		if (this.getOwner() instanceof ServerPlayer owner && owner.level() == this.level() && Undead.missing(owner, UndeadState.HEAD)) {
			Undead.reattach(owner, UndeadState.HEAD);
			((ServerLevel) this.level()).sendParticles(Undead.BLOOD, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.15, 0.1, 0.15, 0.0);
			this.level().playSound(null, owner.getX(), owner.getEyeY(), owner.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
			this.discard();
			return net.minecraft.world.InteractionResult.CONSUME;
		}
		return net.minecraft.world.InteractionResult.PASS;
	}
}

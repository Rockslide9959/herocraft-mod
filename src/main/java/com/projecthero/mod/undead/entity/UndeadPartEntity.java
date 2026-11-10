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
	/**
	 * v0.17.3 ejection (UndeadEject): a hand with half its forearm / a foot with half its calf ({@link #bodyPart} the hand / foot
	 * segment), dropped where it came off: it tumbles to the ground and lies there; and the Combustion Punch's lower two thirds
	 * of the torso ({@link #bodyPart} one of the parts left with it), standing where he was, then falling back. Both live
	 * {@link com.projecthero.mod.undead.UndeadEjectConfig#PIECE_LIFE} and sink away at the end, whatever the regrowth does.
	 */
	public static final int STYLE_EJECT_END = 12;
	public static final int STYLE_EJECT_TORSO = 13;

	/** Set by the client: bursts the piece into pixels as it disappears. */
	public static Consumer<UndeadPartEntity> clientRemoval = e -> {
	};

	private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(UndeadPartEntity.class, EntityDataSerializers.BOOLEAN);

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

	/** A part left standing at {@code at} (his feet), facing {@code bodyYaw}; it falls and rests under gravity. */
	public static UndeadPartEntity drop(ServerPlayer owner, int part, int style, Vec3 at, float bodyYaw, Vec3 velocity) {
		UndeadPartEntity e = new UndeadPartEntity(UndeadEntityTypes.PART, owner.level());
		e.setOwner(owner);
		e.entityData.set(STYLE, style);
		e.entityData.set(PART, part);
		e.falling = true;
		e.setNoGravity(false);
		e.moveTo(at.x, at.y, at.z, bodyYaw, 0f);
		e.setDeltaMovement(velocity);
		owner.level().addFreshEntity(e);
		return e;
	}

	public boolean isLeftBehind() {
		// v0.17.3: only the X launches' pieces (6-8) -- the thrown arm (9) used to fall in here and was drawn standing upright as
		// if still on the body, so it flew through the air vertical
		int s = this.style();
		return s >= STYLE_LEFT_LEG && s <= STYLE_LEFT_BODY_LEGLESS;
	}

	/** v0.17.3: a piece an ejection left behind (a hand / foot end, the Combustion Punch's lower torso). */
	public boolean isEjectPiece() {
		int s = this.style();
		return s == STYLE_EJECT_END || s == STYLE_EJECT_TORSO;
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
			case STYLE_DRILL, STYLE_HEAD, STYLE_LEFT_LEG, STYLE_LEFT_BODY, STYLE_LEFT_BODY_LEGLESS, STYLE_PUNCH -> UndeadConfig.SEVERED_LIFE;
			case STYLE_EJECT_END, STYLE_EJECT_TORSO -> com.projecthero.mod.undead.UndeadEjectConfig.PIECE_LIFE;
			case STYLE_CRIMSON, STYLE_SWORD -> 60;
			default -> 40;
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			Vec3 v = this.getDeltaMovement();
			if (!this.stuck()) {
				this.setPos(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
			}
			return;
		}
		if (++this.life > this.maxLife() || !(this.getOwner() instanceof ServerPlayer owner) || owner.level() != this.level()) {
			this.discard();
			return;
		}
		if (this.bodyPart() >= 0 && !this.isEjectPiece() && !Undead.missing(owner, this.bodyPart())) {
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

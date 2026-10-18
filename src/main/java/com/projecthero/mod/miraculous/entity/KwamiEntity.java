package com.projecthero.mod.miraculous.entity;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.6: <b>the Kwami</b> -- Tikki, Plagg, Trixx or Nooroo floating over its holder's shoulder while they are not transformed.
 * One entity type, the variant ({@link Kwami#id}) and the owner synced. Never saved: {@code KwamiCompanion} (the core)
 * keeps exactly one per detransformed holder and re-creates it the tick it goes missing (chunk unload, dimension change,
 * logout, a stray {@code /kill}).
 *
 * <h2>Following</h2>
 * Both sides run the same follow from the owner's position ({@link #follow}), so the owner's own client sees it glide
 * smoothly without waiting for position packets (the server's are ignored while the owner is known, see {@link #lerpTo}).
 * It keeps its place beside the owner however fast they go (sprinting, elytra, riding: the owner's own movement is added
 * first, then it eases to the anchor), flies straight through blocks and water (no physics), and snaps back if it ends up
 * more than {@link MiraculousConfig#KWAMI_SNAP_DISTANCE} blocks away. Standing still for a while, it drifts round in front
 * of the owner. It looks at whatever the owner looks at (at the owner while it waves or eats).
 *
 * <h2>Can't die</h2>
 * No damage of any kind, no fire, no explosions, no portals; {@code /kill} and the void send it back to its owner instead.
 * No loot, no XP (it is not a mob).
 *
 * <h2>Moods</h2>
 * {@link #setMode} (server): pop out + wave (the intro), pop out tired (after a detransformation), eat, tired (hungry),
 * into the jewel (just before the transformation; it then vanishes), idle. The GeckoLib clips are
 * {@code animation.kwami.<id>.<clip>} in {@code animations/miraculous/kwami.animation.json}.
 */
public class KwamiEntity extends Entity implements GeoEntity {
	public static final int MODE_IDLE = 0;
	/** The intro: pop out of the jewel, then wave. */
	public static final int MODE_POP_OUT = 1;
	public static final int MODE_EAT = 2;
	/** Hungry: droopy, low, slow. */
	public static final int MODE_TIRED = 3;
	/** Spinning and shrinking into the jewel (the transformation's first beat); discarded when it ends. */
	public static final int MODE_INTO_JEWEL = 4;
	/** Out of the jewel after a detransformation: pop out, then tired. */
	public static final int MODE_POP_TIRED = 5;

	private static final EntityDataAccessor<String> DATA_VARIANT = SynchedEntityData.defineId(KwamiEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(KwamiEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(KwamiEntity.class, EntityDataSerializers.INT);
	/** Bumped on every {@link #setMode}, so the same clip twice in a row restarts on the client. */
	private static final EntityDataAccessor<Integer> DATA_SEQ = SynchedEntityData.defineId(KwamiEntity.class, EntityDataSerializers.INT);

	private static final Map<Kwami, RawAnimation[]> CLIPS = new EnumMap<>(Kwami.class);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	/** Server: game time the current temporary mode ends (0 = none, the base mood applies). */
	private long modeUntil;
	private int ownerMissing;
	private @Nullable Vec3 lastOwnerPos;
	private float anchorYaw = Float.NaN;
	private int stillTicks;
	private float orbitPhase;
	private int clientSeq = -1;
	/** Both sides: ticks since the last setMode (the pop-out in front of the face is timed by it). */
	private int modeAge;
	private int seenSeq = -1;

	public KwamiEntity(EntityType<? extends KwamiEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
		this.setInvulnerable(true);
	}

	// ---------------------------------------------------------------- data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_VARIANT, Kwami.TIKKI.id);
		builder.define(DATA_OWNER, Optional.empty());
		builder.define(DATA_MODE, MODE_IDLE);
		builder.define(DATA_SEQ, 0);
	}

	public void setup(Player owner, Kwami kwami) {
		entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
		entityData.set(DATA_VARIANT, kwami.id);
		lastOwnerPos = owner.position();
		anchorYaw = owner.getYRot();
	}

	public Kwami kwami() {
		Kwami k = Kwami.byId(entityData.get(DATA_VARIANT));
		return k == null ? Kwami.TIKKI : k;
	}

	public @Nullable UUID ownerId() {
		return entityData.get(DATA_OWNER).orElse(null);
	}

	public @Nullable Player owner() {
		UUID id = ownerId();
		return id == null ? null : level().getPlayerByUUID(id);
	}

	public int mode() {
		return entityData.get(DATA_MODE);
	}

	/** Server: play a mood for {@code ticks} (0 = until changed), then the base mood (tired if hungry, else idle). */
	public void setMode(int mode, int ticks) {
		entityData.set(DATA_MODE, mode);
		entityData.set(DATA_SEQ, entityData.get(DATA_SEQ) + 1);
		modeUntil = ticks > 0 ? level().getGameTime() + ticks : 0L;
	}

	/** Server: whether a temporary mood (intro, eating, into the jewel) is still playing. */
	public boolean busy() {
		return modeUntil > 0L && level().getGameTime() < modeUntil;
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

	// ---------------------------------------------------------------- can't die, can't be touched

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean allowPassengers) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true; // right-click to feed
	}

	/** {@code /kill}: it just comes back to its owner. */
	@Override
	public void kill() {
		Player owner = owner();
		if (owner == null) {
			discard();
			return;
		}
		snapTo(owner);
	}

	/** The void: back to the owner. */
	@Override
	protected void onBelowWorld() {
		Player owner = owner();
		if (owner == null || owner.getY() < level().getMinBuildHeight() - 32) {
			discard();
			return;
		}
		snapTo(owner);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		clearFire();
		setDeltaMovement(Vec3.ZERO);
		int seq = entityData.get(DATA_SEQ);
		if (seq != seenSeq) {
			seenSeq = seq;
			modeAge = 0;
		} else {
			modeAge++;
		}
		Player owner = owner();
		if (level().isClientSide()) {
			if (owner != null) {
				follow(owner);
			}
			return;
		}
		if (owner == null || owner.isRemoved()) {
			if (++ownerMissing > 40) {
				discard();
			}
			return;
		}
		ownerMissing = 0;
		if (!Miraculous.holds(owner, kwami()) || owner.isSpectator()) {
			discard();
			return;
		}
		long now = level().getGameTime();
		if (modeUntil > 0L && now >= modeUntil) {
			modeUntil = 0L;
			if (mode() == MODE_INTO_JEWEL) {
				discard();
				return;
			}
			if (mode() == MODE_EAT) {
				level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.PLAYER_BURP,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.5f, 1.8f);
			}
		}
		if (mode() == MODE_EAT && busy() && tickCount % 5 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl) {
			// nibbling: crumbs and little bites
			sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM,
					new ItemStack(Miraculous.foodOf(kwami()))), getX(), getY() + 0.42, getZ(), 3, 0.06, 0.04, 0.06, 0.04);
			sl.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EAT,
					net.minecraft.sounds.SoundSource.NEUTRAL, 0.35f, 1.6f + random.nextFloat() * 0.3f);
		}
		if (modeUntil == 0L) {
			var s = Miraculous.peek(owner);
			int base = s != null && s.hungry ? MODE_TIRED : MODE_IDLE;
			if (mode() != base) {
				entityData.set(DATA_MODE, base);
			}
		}
		follow(owner);
	}

	/** Client: the server's position packets are ignored while we follow the owner ourselves. */
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (level().isClientSide() && owner() != null && lastOwnerPos != null) {
			return;
		}
		super.lerpTo(x, y, z, yRot, xRot, steps);
	}

	/** Straight to its place beside the owner (spawning, {@code /kill}, the void). */
	public void snapTo(Player owner) {
		anchorYaw = owner.getYRot();
		lastOwnerPos = owner.position();
		Vec3 a = anchor(owner);
		setPos(a.x, a.y, a.z);
		xo = a.x;
		yo = a.y;
		zo = a.z;
	}

	private boolean poppingOut() {
		return modeAge < (mode() == MODE_POP_OUT ? MiraculousConfig.POP_OUT_TICKS - 4 : MiraculousConfig.POP_TIRED_TICKS);
	}

	/** Where it wants to be this tick (its feet). */
	private Vec3 anchor(Player owner) {
		float yaw = anchorYaw * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 base = owner.position();
		int mode = mode();
		if (mode == MODE_INTO_JEWEL) {
			return switch (kwami()) {
				case TIKKI -> base.add(0, owner.getEyeHeight() - 0.2, 0).add(right.scale(0.3));
				case PLAGG -> base.add(0, owner.getBbHeight() * 0.38, 0).add(right.scale(0.4)).add(fwd.scale(0.1));
				case TRIXX -> base.add(0, owner.getBbHeight() * 0.68, 0).add(fwd.scale(0.25));
				case NOOROO -> base.add(0, owner.getBbHeight() * 0.74, 0).add(fwd.scale(0.25));
			};
		}
		boolean tired = mode == MODE_TIRED || mode == MODE_POP_TIRED;
		if ((mode == MODE_POP_OUT || mode == MODE_POP_TIRED) && poppingOut()) {
			// popping out of the jewel: right in front of the owner's face (in first person you see it wave), then it
			// settles back over the shoulder
			return owner.getEyePosition().add(fwd.scale(1.6)).add(right.scale(0.25)).add(0, tired ? -0.62 : -0.42, 0);
		}
		double angle;
		double radius;
		if (stillTicks > MiraculousConfig.KWAMI_DRIFT_AFTER && !tired) {
			// standing still a while: drift round from the shoulder to in front of the owner and back
			angle = 75.0 + 48.0 * Math.sin(orbitPhase);
			radius = 0.72 + 0.1 * Math.sin(orbitPhase * 1.7);
		} else {
			angle = 90.0;
			radius = tired ? 0.52 : 0.62;
		}
		double a = Math.toRadians(angle);
		Vec3 dir = fwd.scale(Math.cos(a)).add(right.scale(Math.sin(a)));
		double up = owner.getEyeHeight() - (tired ? 0.45 : 0.3);
		double bob = Math.sin((tickCount + getId() * 7) * (tired ? 0.05 : 0.11)) * (tired ? 0.03 : 0.05);
		return base.add(dir.scale(radius)).add(0, up + bob, 0);
	}

	private void follow(Player owner) {
		Vec3 ownerPos = owner.position();
		Vec3 delta = lastOwnerPos == null ? Vec3.ZERO : ownerPos.subtract(lastOwnerPos);
		lastOwnerPos = ownerPos;
		if (delta.lengthSqr() > 64.0) {
			delta = Vec3.ZERO; // a teleport: the snap below handles it
		}
		float ownerYaw = owner.getYRot();
		if (Float.isNaN(anchorYaw)) {
			anchorYaw = ownerYaw;
		}
		anchorYaw += Mth.wrapDegrees(ownerYaw - anchorYaw) * 0.18f;
		boolean still = delta.lengthSqr() < 1.0E-4 && Math.abs(Mth.wrapDegrees(ownerYaw - anchorYaw)) < 2f;
		stillTicks = still ? stillTicks + 1 : 0;
		if (stillTicks > MiraculousConfig.KWAMI_DRIFT_AFTER) {
			orbitPhase += 0.012f;
		}
		Vec3 target = anchor(owner);
		Vec3 p = position().add(delta);
		double snap = MiraculousConfig.KWAMI_SNAP_DISTANCE;
		if (p.distanceToSqr(target) > snap * snap) {
			p = target;
		} else {
			p = p.add(target.subtract(p).scale(mode() == MODE_INTO_JEWEL ? 0.35 : 0.2));
		}
		setPos(p.x, p.y, p.z);
		look(owner);
	}

	private void look(Player owner) {
		int mode = mode();
		Vec3 eye = owner.getEyePosition();
		Vec3 point = mode == MODE_POP_OUT || mode == MODE_EAT || mode == MODE_POP_TIRED ? eye
				: eye.add(owner.getViewVector(1f).scale(6.0));
		Vec3 head = position().add(0, 0.45, 0);
		double dx = point.x - head.x;
		double dy = point.y - head.y;
		double dz = point.z - head.z;
		double h = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = mode == MODE_TIRED ? 6f : (float) -(Mth.atan2(dy, h) * Mth.RAD_TO_DEG);
		setYRot(getYRot() + Mth.clamp(Mth.wrapDegrees(yaw - getYRot()), -30f, 30f));
		setXRot(Mth.clamp(getXRot() + Mth.clamp(pitch - getXRot(), -8f, 8f), -40f, 40f));
	}

	// ---------------------------------------------------------------- feeding

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.getUUID().equals(ownerId()) || !(stack.is(Miraculous.foodOf(kwami())) || Miraculous.isSubstituteFood(stack))) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			return Miraculous.feed(sp, stack) ? InteractionResult.CONSUME : InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- GeckoLib

	private static RawAnimation[] clips(Kwami k) {
		return CLIPS.computeIfAbsent(k, kw -> {
			String p = "animation.kwami." + kw.id + ".";
			RawAnimation[] a = new RawAnimation[6];
			a[MODE_IDLE] = RawAnimation.begin().thenLoop(p + "idle");
			a[MODE_POP_OUT] = RawAnimation.begin().thenPlay(p + "pop_out").thenPlay(p + "wave").thenLoop(p + "idle");
			a[MODE_EAT] = RawAnimation.begin().thenLoop(p + "eat");
			a[MODE_TIRED] = RawAnimation.begin().thenLoop(p + "tired");
			a[MODE_INTO_JEWEL] = RawAnimation.begin().thenPlayAndHold(p + "into_jewel");
			a[MODE_POP_TIRED] = RawAnimation.begin().thenPlay(p + "pop_out").thenLoop(p + "tired");
			return a;
		});
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "mood", 4, state -> {
			int seq = entityData.get(DATA_SEQ);
			if (seq != clientSeq) {
				clientSeq = seq;
				state.getController().forceAnimationReset();
			}
			int mode = Mth.clamp(mode(), 0, 5);
			return state.setAndContinue(clips(kwami())[mode]);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

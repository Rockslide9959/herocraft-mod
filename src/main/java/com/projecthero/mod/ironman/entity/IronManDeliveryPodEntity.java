package com.projecthero.mod.ironman.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManSounds;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.14.21: the Mark VII delivery pod ({@code SuitUpType.REMOTE_AUTOMATED} / {@code SummonType.TRACKING_POD}). Carries
 * the real armour stacks; flies in (from the sky, or straight off a loaded Suit Platform), tracks and lands just behind
 * its owner (hovering at their height if they are airborne), opens its clamshell, fires the pieces out one by one as
 * {@link IronManSuitPartEntity} couriers (so they curve onto the body and lock on exactly like any other call), closes
 * and flies away.
 *
 * <p>Phases (server-authoritative, synced as one int for the GeckoLib clips): {@link #DESCEND} {@value #DESCEND_TICKS}
 * ticks -> {@link #OPEN} {@value #OPEN_TICKS} -> {@link #DEPLOY} one piece every {@value #DEPLOY_INTERVAL} ->
 * {@link #CLOSE} {@value #CLOSE_TICKS} -> {@link #ASCEND} {@value #ASCEND_TICKS}, then it is gone. Whatever it still
 * carries is dropped where it is if the owner logs out / changes dimension, or if it times out -- never destroyed. The
 * stacks are saved with the entity.
 */
public class IronManDeliveryPodEntity extends Entity implements GeoEntity {
	public static final int DESCEND = 0;
	public static final int OPEN = 1;
	public static final int DEPLOY = 2;
	public static final int CLOSE = 3;
	public static final int ASCEND = 4;

	public static final int DESCEND_TICKS = 40;
	public static final int OPEN_TICKS = 10;
	public static final int DEPLOY_INTERVAL = 8;
	public static final int CLOSE_TICKS = 10;
	public static final int ASCEND_TICKS = 30;
	public static final int MAX_LIFE = 600;
	/** How far behind the owner it lands. */
	private static final double BEHIND = 2.4;

	private static final EntityDataAccessor<Integer> PHASE =
			SynchedEntityData.defineId(IronManDeliveryPodEntity.class, EntityDataSerializers.INT);

	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.iron_man_delivery_pod.fly");
	private static final RawAnimation OPEN_ANIM = RawAnimation.begin().thenPlayAndHold("animation.iron_man_delivery_pod.open");
	private static final RawAnimation CLOSE_ANIM = RawAnimation.begin().thenPlayAndHold("animation.iron_man_delivery_pod.close");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final List<ItemStack> cargo = new ArrayList<>();
	private UUID ownerId;
	private int life;
	private int phaseTick;
	private Vec3 start;

	public IronManDeliveryPodEntity(EntityType<? extends IronManDeliveryPodEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** Launch a pod carrying {@code pieces} (taken over) toward {@code owner}; {@code from} null = out of the sky. */
	public static IronManDeliveryPodEntity spawn(ServerLevel level, ServerPlayer owner, List<ItemStack> pieces, Vec3 from) {
		IronManDeliveryPodEntity pod = new IronManDeliveryPodEntity(IronManEntityTypes.DELIVERY_POD, level);
		Vec3 land = landingPoint(owner);
		Vec3 s = from != null ? from : land.add(0, 26, 0);
		pod.setPos(s.x, s.y, s.z);
		pod.start = s;
		pod.ownerId = owner.getUUID();
		for (ItemStack p : pieces) {
			if (!p.isEmpty()) {
				pod.cargo.add(p.copy());
			}
		}
		pod.setYRot(owner.getYRot());
		level.addFreshEntity(pod);
		IronManSounds.play(pod, IronManSounds.THRUSTER, 1.2f, 0.7f);
		return pod;
	}

	/** Just behind the owner, at their height (so it also works mid-air). */
	public static Vec3 landingPoint(ServerPlayer owner) {
		Vec3 look = Vec3.directionFromRotation(0, owner.getYRot());
		return owner.position().subtract(look.scale(BEHIND));
	}

	/** Total ticks from launch until the last piece has left the pod, for {@code n} pieces. */
	public static int ticksToLastPiece(int n) {
		return DESCEND_TICKS + OPEN_TICKS + Math.max(0, n - 1) * DEPLOY_INTERVAL;
	}

	public int phase() {
		return getEntityData().get(PHASE);
	}

	public UUID ownerId() {
		return ownerId;
	}

	public List<ItemStack> cargo() {
		return cargo;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PHASE, DESCEND);
	}

	private void setPhase(int phase) {
		getEntityData().set(PHASE, phase);
		phaseTick = 0;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			if (phase() == DESCEND || phase() == ASCEND) {
				level().addParticle(ParticleTypes.FLAME, getX(), getY() - 0.1, getZ(), 0, -0.15, 0);
				level().addParticle(ParticleTypes.SMOKE, getX(), getY() - 0.2, getZ(), 0, -0.05, 0);
			}
			return;
		}
		life++;
		phaseTick++;
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : (ServerPlayer) level.getPlayerByUUID(ownerId);
		boolean ownerOk = owner != null && owner.level() == level && owner.isAlive();
		if ((!ownerOk && phase() != ASCEND) || life > MAX_LIFE) {
			dropCargo();
			discard();
			return;
		}
		if (start == null) {
			start = position();
		}
		switch (phase()) {
			case DESCEND -> {
				Vec3 land = landingPoint(owner);
				float t = Mth.clamp(phaseTick / (float) DESCEND_TICKS, 0f, 1f);
				float e = 1f - (1f - t) * (1f - t) * (1f - t); // ease-out: fast in, settles on the spot
				Vec3 control = start.add(land).scale(0.5).add(0, 6, 0);
				Vec3 p = IronManSuitPartEntity.bezier(start, control, land, e);
				setDeltaMovement(p.subtract(position()));
				setPos(p.x, p.y, p.z);
				faceOwner(owner);
				if (phaseTick % 4 == 0) {
					level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 3, 0.15, 0.05, 0.15, 0.01);
				}
				if (phaseTick >= DESCEND_TICKS) {
					IronManSounds.play(this, IronManSounds.POD_LAND, 1.0f, 0.9f);
					level.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 14, 0.6, 0.05, 0.6, 0.04);
					setPhase(OPEN);
					IronManSounds.play(this, IronManSounds.SERVO, 1.0f, 0.8f);
				}
			}
			case OPEN -> {
				faceOwner(owner);
				if (phaseTick >= OPEN_TICKS) {
					setPhase(DEPLOY);
					phaseTick = DEPLOY_INTERVAL; // first piece goes straight away
				}
			}
			case DEPLOY -> {
				faceOwner(owner);
				if (phaseTick >= DEPLOY_INTERVAL && !cargo.isEmpty()) {
					phaseTick = 0;
					ItemStack piece = cargo.remove(0);
					Vec3 door = position().add(0, 1.1, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(0.5));
					IronManSuitPartEntity.spawn(level, door, owner, piece, 0);
					IronManSounds.play(this, IronManSounds.RELEASE, 0.8f, 1.2f);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, door.x, door.y, door.z, 8, 0.2, 0.2, 0.2, 0.08);
				}
				if (cargo.isEmpty() && phaseTick >= DEPLOY_INTERVAL) {
					setPhase(CLOSE);
					IronManSounds.play(this, IronManSounds.SERVO, 0.9f, 1.0f);
				}
			}
			case CLOSE -> {
				if (phaseTick >= CLOSE_TICKS) {
					setPhase(ASCEND);
					IronManSounds.play(this, IronManSounds.THRUSTER, 1.0f, 0.8f);
				}
			}
			default -> { // ASCEND
				double vy = 0.05 + phaseTick * 0.03;
				setDeltaMovement(0, vy, 0);
				setPos(getX(), getY() + vy, getZ());
				level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 2, 0.1, 0.05, 0.1, 0.01);
				if (phaseTick >= ASCEND_TICKS) {
					dropCargo(); // nothing should be left, but never lose anything
					discard();
				}
			}
		}
	}

	private void faceOwner(ServerPlayer owner) {
		Vec3 d = owner.position().subtract(position());
		if (d.horizontalDistanceSqr() > 1.0e-4) {
			setYRot((float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f);
		}
	}

	private void dropCargo() {
		for (ItemStack s : cargo) {
			if (!s.isEmpty()) {
				spawnAtLocation(s.copy());
			}
		}
		cargo.clear();
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		life = tag.getInt("Life");
		cargo.clear();
		ListTag list = tag.getList("Cargo", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			ItemStack s = ItemStack.parseOptional(registryAccess(), list.getCompound(i));
			if (!s.isEmpty()) {
				cargo.add(s);
			}
		}
		int ph = tag.getInt("Phase");
		getEntityData().set(PHASE, ph == ASCEND ? ASCEND : (ph == DESCEND ? DESCEND : DEPLOY));
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putInt("Life", life);
		tag.putInt("Phase", phase());
		ListTag list = new ListTag();
		for (ItemStack s : cargo) {
			if (!s.isEmpty()) {
				list.add(s.save(registryAccess()));
			}
		}
		tag.put("Cargo", list);
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 2, state -> switch (phase()) {
			case OPEN, DEPLOY -> state.setAndContinue(OPEN_ANIM);
			case CLOSE -> state.setAndContinue(CLOSE_ANIM);
			default -> state.setAndContinue(FLY);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

package com.projecthero.mod.ironman.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.ArmorItem;
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
 * the real armour stacks and is saved with them.
 *
 * <h2>v0.14.29 (agent C): the orbital drop</h2>
 * A sky call now drops the pod from {@value #ORBITAL_HEIGHT} blocks up ({@value #COVERED_HEIGHT} when the owner has a
 * roof over their head), streaking down with a fire-and-smoke trail and <b>homing on the owner every tick</b> -- it aims
 * where they will be (their measured velocity is added to its own step), so a running, flying or falling owner is
 * still caught. It accelerates from {@value #START_SPEED} to {@value #MAX_SPEED} blocks per tick (about one second
 * from orbit).
 * <ul>
 *   <li><b>Owner on the ground</b>: it slams down just behind them (impact flash, dust ring, boom -- no block damage),
 *       opens its clamshell and fires the pieces out as {@link IronManSuitPartEntity} couriers exactly as before.</li>
 *   <li><b>Owner airborne / falling</b> (the film's party trick): it matches their fall alongside them, brakes the fall,
 *       and clamps the pieces straight onto the body one every {@value #CATCH_INTERVAL} ticks, boots first -- any worn
 *       Iron Man piece already makes the wearer fall-immune, so the catch is safe from the first clamp.</li>
 *   <li><b>Can't reach</b> (still descending after {@value #DESCENT_TIMEOUT} ticks, the owner changed dimension, the
 *       pod outlived {@value #MAX_LIFE} ticks): the remaining pieces go into the owner's pack and the ordinary staged
 *       suit-up starts instead ({@link #giveUp}) -- nothing is ever stranded. Only an owner who is offline / dead gets
 *       the cargo dropped where the pod is, as before.</li>
 * </ul>
 * While it still carries cargo it keeps the owner's suit-up transition from timing out before the pieces arrive.
 */
public class IronManDeliveryPodEntity extends Entity implements GeoEntity {
	public static final int DESCEND = 0;
	public static final int OPEN = 1;
	public static final int DEPLOY = 2;
	public static final int CLOSE = 3;
	public static final int ASCEND = 4;

	/** Estimated orbital descent (the real one homes, so it varies a little). */
	public static final int DESCEND_TICKS = 24;
	public static final int OPEN_TICKS = 10;
	public static final int DEPLOY_INTERVAL = 8;
	public static final int CLOSE_TICKS = 10;
	public static final int ASCEND_TICKS = 30;
	public static final int MAX_LIFE = 600;

	/** v0.14.29: how high above the owner the orbital drop starts (open sky). */
	public static final double ORBITAL_HEIGHT = 70.0;
	/** v0.14.29: ...and with a roof overhead (the old 26-block sky approach). */
	public static final double COVERED_HEIGHT = 26.0;
	public static final double START_SPEED = 1.2;
	public static final double MAX_SPEED = 5.0;
	public static final double ACCELERATION = 0.25;
	/** v0.14.29: give up homing and fall back to the staged suit-up after this long still descending. */
	public static final int DESCENT_TIMEOUT = 120;
	/** v0.14.29: catch mode -- the clamshell opens this fast, then one piece clamps on every CATCH_INTERVAL ticks. */
	public static final int CATCH_OPEN_TICKS = 3;
	public static final int CATCH_INTERVAL = 3;
	/** The arrival radius: closer than this (or than one step) and it has reached its owner. */
	private static final double ARRIVE_RADIUS = 1.5;
	/** How far behind the owner it lands. */
	private static final double BEHIND = 2.4;
	/** Catch mode: how far behind the falling owner it rides. */
	private static final double CATCH_BEHIND = 1.3;

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
	private boolean catchMode;
	private Vec3 lastOwnerPos;
	private Vec3 ownerVel = Vec3.ZERO;

	public IronManDeliveryPodEntity(EntityType<? extends IronManDeliveryPodEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * Launch a pod carrying {@code pieces} (taken over) toward {@code owner}; {@code from} null = the orbital drop out of
	 * the sky, otherwise it streaks in from there (a loaded Suit Platform).
	 */
	public static IronManDeliveryPodEntity spawn(ServerLevel level, ServerPlayer owner, List<ItemStack> pieces, Vec3 from) {
		IronManDeliveryPodEntity pod = new IronManDeliveryPodEntity(IronManEntityTypes.DELIVERY_POD, level);
		Vec3 s = from != null ? from : orbitalStart(level, owner);
		pod.setPos(s.x, s.y, s.z);
		pod.start = s;
		pod.ownerId = owner.getUUID();
		pod.lastOwnerPos = owner.position();
		for (ItemStack p : pieces) {
			if (!p.isEmpty()) {
				pod.cargo.add(p.copy());
			}
		}
		// boots first: a falling owner is fall-immune from the first clamp, and the build reads bottom-up
		pod.cargo.sort((a, b) -> Integer.compare(order(b), order(a)));
		pod.setYRot(owner.getYRot());
		level.addFreshEntity(pod);
		IronManSounds.play(pod, IronManSounds.THRUSTER, 1.2f, 0.7f);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR,
				SoundSource.PLAYERS, 0.9f, 0.6f);
		return pod;
	}

	private static int order(ItemStack s) {
		return s.getItem() instanceof IronManArmorItem a ? a.getType().ordinal() : -1; // HELMET 0 .. BOOTS 3
	}

	/** v0.14.29: where the orbital drop starts -- high over the owner, a little behind them. */
	public static Vec3 orbitalStart(ServerLevel level, ServerPlayer owner) {
		boolean open = level.canSeeSky(owner.blockPosition().above());
		Vec3 back = Vec3.directionFromRotation(0, owner.getYRot()).scale(-6.0);
		return owner.position().add(back.x, open ? ORBITAL_HEIGHT : COVERED_HEIGHT, back.z);
	}

	/** v0.14.29: an owner the pod should catch mid-air rather than land beside (airborne, not swimming or riding). */
	public static boolean airborne(ServerPlayer owner) {
		return !owner.onGround() && !owner.isInWater() && !owner.isPassenger();
	}

	/** Just behind the owner, at their height. */
	public static Vec3 landingPoint(ServerPlayer owner) {
		Vec3 look = Vec3.directionFromRotation(0, owner.getYRot());
		return owner.position().subtract(look.scale(BEHIND));
	}

	/** Total ticks from launch until the last piece has left the pod, for {@code n} pieces (the ground-landing case). */
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

	/** v0.14.29: true once it has matched an airborne owner and is clamping the pieces straight on. */
	public boolean catching() {
		return catchMode;
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
			tickClientTrail();
			return;
		}
		life++;
		phaseTick++;
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		boolean online = owner != null && owner.isAlive();
		if (!online) {
			if (phase() != ASCEND || !cargo.isEmpty()) {
				dropCargo();
				discard();
			} else if (phaseTick >= ASCEND_TICKS) {
				discard();
			} else {
				ascend(level);
			}
			return;
		}
		if (owner.level() != level || life > MAX_LIFE) {
			giveUp(owner);
			return;
		}
		if (start == null) {
			start = position();
		}
		// the owner's measured velocity (server-side delta movement is not reliable for a client-driven player)
		Vec3 here = owner.position();
		ownerVel = lastOwnerPos == null ? Vec3.ZERO : here.subtract(lastOwnerPos);
		if (ownerVel.lengthSqr() > 100.0) {
			ownerVel = Vec3.ZERO; // a teleport, not motion
		}
		lastOwnerPos = here;
		if (!cargo.isEmpty()) {
			holdTransition(owner);
		}
		switch (phase()) {
			case DESCEND -> tickDescend(level, owner);
			case OPEN -> {
				if (catchMode) {
					ride(owner);
				}
				faceOwner(owner);
				if (phaseTick >= (catchMode ? CATCH_OPEN_TICKS : OPEN_TICKS)) {
					setPhase(DEPLOY);
					phaseTick = catchMode ? CATCH_INTERVAL : DEPLOY_INTERVAL; // first piece goes straight away
				}
			}
			case DEPLOY -> {
				if (catchMode) {
					ride(owner);
				}
				faceOwner(owner);
				int interval = catchMode ? CATCH_INTERVAL : DEPLOY_INTERVAL;
				if (phaseTick >= interval && !cargo.isEmpty()) {
					phaseTick = 0;
					ItemStack piece = cargo.remove(0);
					if (catchMode) {
						clampOn(level, owner, piece);
					} else {
						Vec3 door = position().add(0, 1.1, 0).add(Vec3.directionFromRotation(0, getYRot()).scale(0.5));
						IronManSuitPartEntity.spawn(level, door, owner, piece, 0);
						IronManSounds.play(this, IronManSounds.RELEASE, 0.8f, 1.2f);
						level.sendParticles(ParticleTypes.ELECTRIC_SPARK, door.x, door.y, door.z, 8, 0.2, 0.2, 0.2, 0.08);
					}
				}
				if (cargo.isEmpty() && phaseTick >= interval) {
					setPhase(CLOSE);
					IronManSounds.play(this, IronManSounds.SERVO, 0.9f, 1.0f);
				}
			}
			case CLOSE -> {
				if (phaseTick >= (catchMode ? 4 : CLOSE_TICKS)) {
					setPhase(ASCEND);
					IronManSounds.play(this, IronManSounds.THRUSTER, 1.0f, 0.8f);
				}
			}
			default -> { // ASCEND
				ascend(level);
				if (phaseTick >= ASCEND_TICKS) {
					giveBackCargo(owner); // nothing should be left, but never lose anything
					discard();
				}
			}
		}
	}

	// ---------------------------------------------------------------- v0.14.29: the orbital drop

	private void tickDescend(ServerLevel level, ServerPlayer owner) {
		if (phaseTick > DESCENT_TIMEOUT) {
			giveUp(owner);
			return;
		}
		boolean air = airborne(owner);
		Vec3 target = air ? catchPoint(owner) : landingPoint(owner);
		double speed = Math.min(MAX_SPEED, START_SPEED + phaseTick * ACCELERATION);
		Vec3 to = target.subtract(position());
		double dist = to.length();
		faceOwner(owner);
		if (dist <= Math.max(ARRIVE_RADIUS, speed)) {
			setPos(target.x, target.y, target.z);
			setDeltaMovement(ownerVel);
			arrive(level, owner, air);
			return;
		}
		// home: keep pace with the owner, then close in at the pod's own speed
		Vec3 step = ownerVel.add(to.scale(speed / dist));
		Vec3 prev = position();
		setDeltaMovement(step);
		setPos(prev.x + step.x, prev.y + step.y, prev.z + step.z);
		// the re-entry trail every viewer sees (the client adds its own denser one between ticks)
		level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 4, 0.15, 0.15, 0.15, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, prev.x, prev.y, prev.z, 2, 0.2, 0.2, 0.2, 0.01);
		if (phaseTick % 6 == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 1.0f, 0.6f + (float) (speed / MAX_SPEED) * 0.6f);
		}
	}

	/** Beside a falling owner, a little behind and level with their chest, leading their motion by a tick. */
	private static Vec3 catchPoint(ServerPlayer owner, Vec3 vel) {
		Vec3 back = Vec3.directionFromRotation(0, owner.getYRot()).scale(-CATCH_BEHIND);
		return owner.position().add(back.x, 0.1, back.z).add(vel);
	}

	private Vec3 catchPoint(ServerPlayer owner) {
		return catchPoint(owner, ownerVel);
	}

	private void arrive(ServerLevel level, ServerPlayer owner, boolean air) {
		catchMode = air;
		if (air) {
			// caught: brake the fall (the client owns its motion, so push the new velocity to it)
			Vec3 v = owner.getDeltaMovement();
			owner.setDeltaMovement(v.x * 0.6, Math.max(v.y, -0.35), v.z * 0.6);
			owner.hurtMarked = true;
			owner.resetFallDistance();
			level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.0, getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 16, 0.6, 0.6, 0.6, 0.08);
			IronManSounds.play(this, IronManSounds.SERVO, 1.0f, 1.3f);
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.orbital_catch")
					.withStyle(ChatFormatting.AQUA), true);
		} else {
			// impact: a flash, a dust ring and a boom -- visual only, no block damage
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.1, 0.3, 0.0);
			level.sendParticles(ParticleTypes.FLASH, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
			for (int i = 0; i < 24; i++) {
				double a = i / 24.0 * Math.PI * 2;
				level.sendParticles(ParticleTypes.CLOUD, getX() + Math.cos(a) * 0.8, getY() + 0.1, getZ() + Math.sin(a) * 0.8,
						0, Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35, 1.0);
			}
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 6, 0.6, 0.1, 0.6, 0.02);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.4f);
			IronManSounds.play(this, IronManSounds.POD_LAND, 1.0f, 0.9f);
			IronManSounds.play(this, IronManSounds.SERVO, 1.0f, 0.8f);
		}
		setPhase(OPEN);
	}

	/** Catch mode: stay alongside the owner while they fall / fly. */
	private void ride(ServerPlayer owner) {
		Vec3 p = catchPoint(owner);
		setDeltaMovement(p.subtract(position()));
		setPos(p.x, p.y, p.z);
		owner.resetFallDistance();
	}

	/** Catch mode: clamp {@code piece} straight onto the owner (it goes to their pack if that slot is taken). */
	private void clampOn(ServerLevel level, ServerPlayer owner, ItemStack piece) {
		ArmorItem.Type type = piece.getItem() instanceof IronManArmorItem a ? a.getType() : ArmorItem.Type.CHESTPLATE;
		Vec3 slot = IronManSuitPartEntity.attachPoint(owner, type);
		Vec3 from = position().add(0, 1.1, 0);
		if (!IronManSuitUpManager.receivePart(owner, piece)) {
			IronManSuitUpManager.giveBack(owner, piece);
		}
		owner.resetFallDistance();
		Vec3 d = slot.subtract(from);
		for (int i = 0; i <= 6; i++) {
			Vec3 q = from.add(d.scale(i / 6.0));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.01);
		}
		level.sendParticles(ParticleTypes.END_ROD, slot.x, slot.y, slot.z, 6, 0.25, 0.2, 0.25, 0.03);
		IronManSounds.play(this, IronManSounds.RELEASE, 0.8f, 1.4f);
	}

	private void ascend(ServerLevel level) {
		double vy = (catchMode ? 0.4 : 0.05) + phaseTick * (catchMode ? 0.12 : 0.03);
		setDeltaMovement(0, vy, 0);
		setPos(getX(), getY() + vy, getZ());
		level.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 2, 0.1, 0.05, 0.1, 0.01);
	}

	/** Keep the owner's suit-up transition alive while the pieces are still on their way. */
	private void holdTransition(ServerPlayer owner) {
		String suitId = cargoSuitId();
		TonyStarkState s = TonyStark.state(owner);
		if (suitId != null && suitId.equals(s.transitionSuit) && s.transitionUp && s.transitionTicks < 10) {
			s.transitionTicks = 10;
		}
	}

	private String cargoSuitId() {
		for (ItemStack s : cargo) {
			if (s.getItem() instanceof IronManArmorItem a) {
				return a.suitId();
			}
		}
		return null;
	}

	/**
	 * v0.14.29: the pod can't reach its owner -- hand the remaining pieces over to their pack and start the ordinary
	 * staged suit-up from there (keeping the suit's charge), then leave. Public for tests.
	 */
	public void giveUp(ServerPlayer owner) {
		String suitId = cargoSuitId();
		if (suitId != null && !cargo.isEmpty()) {
			float energy = IronManEnergy.energy(owner, suitId);
			float integrity = IronManEnergy.integrity(owner, suitId);
			giveBackCargo(owner);
			TonyStarkState s = TonyStark.state(owner);
			if (suitId.equals(s.transitionSuit) && s.transitionUp) {
				// the pod's transition was only a wait for the pieces -- drop it so the staged build can start
				s.transitionSuit = "";
				s.transitionTicks = 0;
				s.transitionMask = 0;
				s.transitionReleaseMask = 0;
				IronManSuitFx.endPose(owner);
			}
			if (IronManSuitUpManager.beginSuitUp(owner, suitId)) {
				IronManEnergy.setEnergy(owner, suitId, energy);
				IronManEnergy.setIntegrity(owner, suitId, integrity);
			}
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.orbital_fallback")
					.withStyle(ChatFormatting.GOLD), true);
		}
		discard();
	}

	private void giveBackCargo(ServerPlayer owner) {
		for (ItemStack s : cargo) {
			if (!s.isEmpty()) {
				IronManSuitUpManager.giveBack(owner, s.copy());
			}
		}
		cargo.clear();
	}

	private void tickClientTrail() {
		int ph = phase();
		if (ph == DESCEND || ph == ASCEND) {
			// a dense trail between last tick's position and this one (it moves up to 5 blocks a tick)
			double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
			double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
			int n = Mth.clamp((int) (len * 2.0), 1, 12);
			for (int i = 0; i < n; i++) {
				double t = i / (double) n;
				double x = xo + dx * t, y = yo + dy * t, z = zo + dz * t;
				level().addParticle(ParticleTypes.FLAME, x, y - 0.1, z, 0, -0.05, 0);
				if (i % 2 == 0) {
					level().addParticle(ParticleTypes.SMOKE, x, y + 0.4, z, 0, 0.02, 0);
				}
				if (len > 2.0 && i % 3 == 0) {
					level().addParticle(ParticleTypes.FIREWORK, x, y + 0.6, z, 0, 0, 0);
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
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 256.0 * 256.0; // v0.14.29: visible the whole way down from orbit
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

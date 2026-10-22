package com.projecthero.mod.ironman.crate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the Hulkbuster I's <b>orbital drop crate</b> -- a red / gold pod 2.25 blocks
 * square and 3.4 tall, walls hinged at the floor, a gold lid hinged at the top of the back wall, a blue seam.
 * {@link HulkbusterCrate} has the controls; this entity is the crate and runs its whole life on one synced clock
 * ({@link #phase()} + the game tick it started), so every viewer draws the same frame. The entity itself never moves:
 * the drop / lift is a visual height ({@link #height}) over the spot it stands on.
 *
 * <h2>Frame</h2>
 * Local +z is the entity's facing. The Hulkbuster stands at the centre facing +z, into the standing BACK wall (lid
 * hinge on top of it); its open back -- the Sentry Mode step-in side -- looks out through the FRONT wall at -z, which
 * is the side facing whoever called it. The front and the two side walls fall flat outward.
 *
 * <h2>Call (C)</h2>
 * {@link #P_DROP} (closed, the suit inside, accelerating down a smoke streak) -> {@link #P_IMPACT} (dust, shake, the
 * scorched ring; the Hulkbuster stands up inside as a real {@link IronManSentryEntity} holding the four stacks) ->
 * {@link #P_OPENING} (the walls fall flat, the lid flips, its back panels open) -> {@link #P_OPEN}. Walking into its
 * open back (or right-clicking it) is the sentry's own step-in, which closes it round you. Once you are clear of the
 * walls the empty crate {@link #P_CLOSING closes} and {@link #P_LIFT lifts} back off.
 *
 * <h2>Fold (hold Shift+C)</h2>
 * {@link #P_FOLD_DROP} (the crate comes down round you open like petals, no floor, braking) -> {@link #P_FOLD_SETTLE}
 * (the walls flatten, the floor unfolds) -> {@link #P_FOLD_EJECT} (you step out backwards -- the sentry eject -- then
 * aside to a corner) -> {@link #P_CLOSING} -> {@link #P_LIFT}; at the top the four stacks go into your Uplink.
 */
public class HulkbusterCrateEntity extends Entity {
	public static final int P_DROP = 0, P_IMPACT = 1, P_OPENING = 2, P_OPEN = 3, P_CLOSING = 4, P_LIFT = 5, P_FOLD_DROP = 6,
			P_FOLD_SETTLE = 7, P_FOLD_EJECT = 8;

	public static final int DROP_TICKS = 28;
	public static final int IMPACT_TICKS = 8;
	public static final int OPEN_TICKS = 24;
	public static final int CLOSE_TICKS = 24;
	/** The lift: thrusters light for this long, then it climbs. */
	public static final int IGNITE_TICKS = 10;
	public static final int LIFT_TICKS = 42;
	public static final int FOLD_DROP_TICKS = 34;
	public static final int SETTLE_TICKS = 12;

	/** Half the wall square (18 px), the wall height (55 px), the floor plate's half width (26 px), blocks. */
	public static final float HALF = 18f / 16f;
	public static final float WALL_H = 55f / 16f;
	public static final float FLOOR_HALF = 26f / 16f;
	/** Walls splayed out like airbrakes while a fold crate comes down. */
	public static final float PETAL = 0.42f;
	/** Each wall's fall delay on the opening clock: front, left, right. */
	private static final int[] OPEN_DELAY = { 2, 5, 7 };
	private static final int WALL_FALL = 12;
	/** Each wall's rise delay on the closing clock: front, left, right (the reverse order). */
	private static final int[] CLOSE_DELAY = { 4, 2, 0 };
	private static final int WALL_RISE = 14;

	private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(HulkbusterCrateEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Long> PHASE_START = SynchedEntityData.defineId(HulkbusterCrateEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Float> DROP_HEIGHT = SynchedEntityData.defineId(HulkbusterCrateEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Long> LANDED_AT = SynchedEntityData.defineId(HulkbusterCrateEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(HulkbusterCrateEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	// server
	private UUID ownerId;
	private UUID sentryId;
	/** v0.16.7 (user): a recall crate coming down round this free-standing Hulkbuster (Sentry Mode / a wreck), or null. */
	private UUID pickupId;
	private final ItemStack[] stacks = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	private float integrity;
	private boolean recall;
	private int side = 1;
	private int ejectTicks;
	private Vec3 ejectLast;
	private int walkInCooldown;
	private int hintCooldown;
	private long loadedAge = -1;

	public HulkbusterCrateEntity(EntityType<? extends HulkbusterCrateEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	// ======================================================================== spawning (server)

	/** A called crate: lands at {@code at} from {@code height} up, facing {@code yaw}, carrying the four stacks. */
	static HulkbusterCrateEntity drop(ServerLevel level, ServerPlayer owner, Vec3 at, float yaw, float height, List<ItemStack> pieces,
			float integrity) {
		HulkbusterCrateEntity c = new HulkbusterCrateEntity(IronManEntityTypes.HULKBUSTER_CRATE, level);
		c.init(owner, at, yaw, height);
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof net.minecraft.world.item.ArmorItem a) {
				int i = com.projecthero.mod.ironman.item.SuitcaseContents.slotOf(a.getType());
				if (i >= 0 && c.stacks[i].isEmpty()) {
					c.stacks[i] = s;
				}
			}
		}
		c.integrity = integrity;
		c.setPhase(P_DROP);
		level.addFreshEntity(c);
		return c;
	}

	/** A fold crate: comes down open round {@code owner}, who is wearing the suit. */
	static HulkbusterCrateEntity foldOver(ServerLevel level, ServerPlayer owner, float height) {
		HulkbusterCrateEntity c = new HulkbusterCrateEntity(IronManEntityTypes.HULKBUSTER_CRATE, level);
		c.init(owner, owner.position(), owner.getYRot(), height);
		c.setPhase(P_FOLD_DROP);
		level.addFreshEntity(c);
		return c;
	}

	private void init(ServerPlayer owner, Vec3 at, float yaw, float height) {
		ownerId = owner.getUUID();
		entityData.set(OWNER, Optional.of(ownerId));
		entityData.set(DROP_HEIGHT, height);
		moveTo(at.x, at.y, at.z, yaw, 0f);
		setYHeadRot(yaw);
	}

	// ======================================================================== state

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(PHASE, (byte) P_DROP);
		builder.define(PHASE_START, 0L);
		builder.define(DROP_HEIGHT, 64f);
		builder.define(LANDED_AT, -1000L);
		builder.define(OWNER, Optional.empty());
	}

	public int phase() {
		return entityData.get(PHASE);
	}

	/** Ticks (plus {@code partial}) into the current phase. */
	public float phaseAge(float partial) {
		return Math.max(0f, level().getGameTime() - entityData.get(PHASE_START) + partial);
	}

	public float dropHeight() {
		return entityData.get(DROP_HEIGHT);
	}

	/** Ticks since it last hit the ground (for the scorch's embers). */
	public float sinceLanded(float partial) {
		return level().getGameTime() - entityData.get(LANDED_AT) + partial;
	}

	public UUID ownerId() {
		return level().isClientSide() ? entityData.get(OWNER).orElse(null) : ownerId;
	}

	public boolean isOwner(Player p) {
		UUID id = ownerId();
		return id != null && id.equals(p.getUUID());
	}

	private void setPhase(int p) {
		entityData.set(PHASE, (byte) p);
		entityData.set(PHASE_START, level().getGameTime());
	}

	/** v0.16.7 (user) server: the orbit slot of the suit this crate carries as stacks (in the drop or the lift), or null. */
	public UUID carriedLink() {
		for (ItemStack s : stacks) {
			UUID id = s.isEmpty() ? null : HulkbusterOrbit.linkOf(s);
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	/**
	 * v0.16.7 (user): "Calling while the suit is already down: recall it to orbit" -- a Hulkbuster standing free (Sentry
	 * Mode, or a wreck) is picked up: a crate comes down open round it (the fold's drop), the suit's back shuts, the walls
	 * close and it lifts off to orbit with the suit as it is. Null when it is coming, else why not (a lang key tail).
	 */
	public static String pickUp(ServerLevel level, ServerPlayer caller, IronManSentryEntity s) {
		if (s.phase() != IronManSentryEntity.PHASE_IDLE || s.busy() || s.isVehicle() || s.flying()) {
			return "busy";
		}
		if (HulkbusterCrate.crateOf(caller) != null) {
			return "busy";
		}
		String problem = HulkbusterCrate.landingProblem(level, caller, s.position(), false);
		if (problem != null) {
			return problem;
		}
		HulkbusterCrateEntity c = new HulkbusterCrateEntity(IronManEntityTypes.HULKBUSTER_CRATE, level);
		float h = Math.max(10f, Math.min(HulkbusterCrate.FOLD_HEIGHT, level.getMaxBuildHeight() - (float) s.getY() - 6f));
		c.init(caller, s.position(), s.getYRot(), h);
		c.pickupId = s.getUUID();
		c.setPhase(P_FOLD_DROP);
		level.addFreshEntity(c);
		s.setMode(IronManSentryEntity.REGULAR); // it stands still for the crate
		s.setCrated(true);
		return null;
	}

	/** The recall crate's drop and settle round a free-standing suit; then it is the crate's, and it shuts and lifts. */
	private void tickPickup(ServerLevel level, int age, boolean settling) {
		IronManSentryEntity s = level.getEntity(pickupId) instanceof IronManSentryEntity e && !e.isRemoved() ? e : null;
		if (s == null) {
			pickupId = null;
			setPhase(P_LIFT); // it went away meanwhile (stepped into, killed): the empty crate goes back up
			return;
		}
		if (!settling) {
			if (age == 0) {
				IronManSounds.playAt(level, getX(), getY() + 12, getZ(), IronManSounds.THRUSTER, 2.5f, 0.8f);
			}
			if (age >= FOLD_DROP_TICKS) {
				impact(level, false, null);
				setPhase(P_FOLD_SETTLE);
			}
			return;
		}
		if (age == 6) {
			for (int w = 0; w < 3; w++) {
				wallLands(level, w);
			}
		}
		if (age >= SETTLE_TICKS) {
			sentryId = pickupId;
			pickupId = null;
			s.setCrated(true);
			recall = true; // shut its back, close the walls and lift once its caller is clear
			setPhase(P_OPEN);
		}
	}

	/** Server: does the crate hold the suit as stacks (closed, in the drop or the lift)? */
	public boolean carrying() {
		for (ItemStack s : stacks) {
			if (!s.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Server: the Hulkbuster standing in it, or null. */
	public IronManSentryEntity sentry() {
		if (sentryId == null || !(level() instanceof ServerLevel sl)) {
			return null;
		}
		return sl.getEntity(sentryId) instanceof IronManSentryEntity s && !s.isRemoved() ? s : null;
	}

	/** Server: is {@code s} the suit standing in this crate? */
	public boolean holds(IronManSentryEntity s) {
		return s != null && s.getUUID().equals(sentryId);
	}

	/** Server: the suit is down here, in it or standing in it (not worn). */
	public boolean hasSuit() {
		return carrying() || sentry() != null;
	}

	public float carriedIntegrity() {
		return integrity;
	}

	/** Server (gametests): the crate is open and waiting. */
	public boolean waitingOpen() {
		return phase() == P_OPEN;
	}

	// ======================================================================== pure pose (client renderer + tests)

	/** How far wall {@code wall} (0 front, 1 left, 2 right) is open, 0 shut .. 1 flat, at {@code age} into {@code phase}. */
	public static float wallOpen(int wall, int phase, float age) {
		switch (phase) {
			case P_OPENING: {
				float d = OPEN_DELAY[wall];
				if (age < d) {
					return 0.012f * smooth(age / 2f); // unlatched: it leans out a hair first
				}
				float u = (age - d) / WALL_FALL;
				if (u <= 1f) {
					return 0.012f + 0.988f * u * u; // falls under its own weight
				}
				float b = age - d - WALL_FALL;
				return 1f - 0.06f * (float) Math.exp(-b / 2.5f) * Math.abs(Mth.sin((float) Math.PI * b / 3.5f));
			}
			case P_OPEN, P_FOLD_EJECT:
				return 1f;
			case P_CLOSING:
				return 1f - smooth((age - CLOSE_DELAY[wall]) / WALL_RISE);
			case P_FOLD_DROP:
				return PETAL + 0.025f * Mth.sin(age * 0.8f + wall * 2.1f);
			case P_FOLD_SETTLE: {
				float u = age / 6f;
				if (u <= 1f) {
					float start = PETAL + 0.025f * Mth.sin(FOLD_DROP_TICKS * 0.8f + wall * 2.1f);
					return start + (1f - start) * u * u;
				}
				float b = age - 6f;
				return 1f - 0.05f * (float) Math.exp(-b / 2f) * Math.abs(Mth.sin((float) Math.PI * b / 3f));
			}
			default:
				return 0f;
		}
	}

	/** The lid, 0 shut .. 1 open (70 degrees back). */
	public static float lidOpen(int phase, float age) {
		return switch (phase) {
			case P_OPENING -> {
				float u = Mth.clamp((age - 1f) / 13f, 0f, 1f);
				yield 1f - (1f - u) * (1f - u) * (1f - u);
			}
			case P_OPEN, P_FOLD_DROP, P_FOLD_SETTLE, P_FOLD_EJECT -> 1f;
			case P_CLOSING -> 1f - smooth((age - 12f) / 10f);
			default -> 0f;
		};
	}

	/** Height (blocks) the crate is drawn above its spot. */
	public static float height(int phase, float age, float dropHeight) {
		return switch (phase) {
			case P_DROP -> {
				float u = Mth.clamp(age / DROP_TICKS, 0f, 1f);
				yield dropHeight * (1f - u * u);
			}
			case P_LIFT -> {
				float u = Mth.clamp((age - IGNITE_TICKS) / (float) (LIFT_TICKS - IGNITE_TICKS), 0f, 1f);
				yield dropHeight * u * u;
			}
			case P_FOLD_DROP -> {
				float u = Mth.clamp(age / FOLD_DROP_TICKS, 0f, 1f);
				yield dropHeight * (1f - u) * (1f - u) * (1f - u);
			}
			default -> 0f;
		};
	}

	/** The floor plate, 0 (not there: a fold crate comes down round you) .. 1. */
	public static float floor(int phase, float age) {
		return switch (phase) {
			case P_FOLD_DROP -> 0f;
			case P_FOLD_SETTLE -> smooth((age - 3f) / 6f);
			default -> 1f;
		};
	}

	/** The landing squash (y scale). */
	public static float squash(int phase, float age) {
		if (phase == P_IMPACT) {
			return 1f - 0.075f * (float) Math.exp(-age / 2.2f) * Mth.cos(age * 0.95f);
		}
		return 1f;
	}

	static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	// ======================================================================== v0.16.7 (user): walls settle on the ground

	/** The most a wall may open: 1 = flat; less where a hill stops it, up to {@link #MAX_WALL_OPEN} down a slope. */
	public static final float MAX_WALL_OPEN = 4f / 3f;
	private final float[] wallLimit = { -1f, -1f, -1f };
	private long wallLimitAt = Long.MIN_VALUE;

	/**
	 * v0.16.7 (user): "the drop crate's walls fall flat even on uneven terrain and clip into hills". How far wall
	 * {@code wall} (0 front, 1 left, 2 right) can fall before it meets the ground -- the open fraction (1 = flat on level
	 * ground) at which the swept wall first touches a solid block: less than 1 against a rise, more (it tips on down the
	 * slope, up to {@link #MAX_WALL_OPEN} = 120 degrees) where the ground falls away. Sampled along the wall's length and
	 * width, refined by bisection; cached for 2 s (both sides; the renderer multiplies {@link #wallOpen} by it).
	 */
	public float wallLimit(int wall) {
		long now = level().getGameTime();
		if (now - wallLimitAt > 40L || wallLimit[wall] < 0f) {
			wallLimitAt = now;
			for (int w = 0; w < 3; w++) {
				wallLimit[w] = computeWallLimit(w);
			}
		}
		return wallLimit[wall];
	}

	private float computeWallLimit(int wall) {
		float step = 1f / 30f; // 3 degrees
		float safe = 0f;
		for (float f = step; f <= MAX_WALL_OPEN + 1.0e-4f; f += step) {
			if (wallHits(wall, f)) {
				// bisect between the last clear angle and this one
				float lo = safe, hi = f;
				for (int i = 0; i < 5; i++) {
					float mid = (lo + hi) * 0.5f;
					if (wallHits(wall, mid)) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				return Math.max(0.08f, lo);
			}
			safe = f;
		}
		return MAX_WALL_OPEN;
	}

	/** Does wall {@code wall}, opened to fraction {@code f} (of 90 degrees), touch a solid block? */
	private boolean wallHits(int wall, float f) {
		double th = Math.toRadians(90.0 * f);
		double sin = Math.sin(th), cos = Math.cos(th);
		// out = the way it falls, across = along its hinge (local x, z)
		double ox = wall == 0 ? 0 : wall == 1 ? -1 : 1, oz = wall == 0 ? -1 : 0;
		double ax = wall == 0 ? 1 : 0, az = wall == 0 ? 0 : 1;
		double hx = ox * HALF, hz = oz * HALF;
		for (double s = 0.35; s <= WALL_H + 1.0e-3; s += 0.3) {
			double out = s * sin, up = s * cos + 0.06; // the wall's outer face, a hair proud of it
			for (double c : new double[] { -HALF + 0.2, 0.0, HALF - 0.2 }) {
				double lx = hx + ox * out + ax * c, lz = hz + oz * out + az * c;
				Vec3 p = world(lx, lz).add(0, up, 0);
				BlockPos bp = BlockPos.containing(p);
				var shape = level().getBlockState(bp).getCollisionShape(level(), bp);
				if (shape.isEmpty()) {
					continue;
				}
				for (AABB box : shape.toAabbs()) {
					if (box.move(bp).contains(p)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ======================================================================== frame helpers

	/** Local (x right-ish, z forward) of a world point, in the crate's frame. */
	public double[] local(Vec3 p) {
		double r = Math.toRadians(getYRot());
		double dx = p.x - getX(), dz = p.z - getZ();
		return new double[] { dx * Math.cos(r) + dz * Math.sin(r), -dx * Math.sin(r) + dz * Math.cos(r) };
	}

	/** World point at local (lx, lz). */
	public Vec3 world(double lx, double lz) {
		double r = Math.toRadians(getYRot());
		return new Vec3(getX() + lx * Math.cos(r) - lz * Math.sin(r), getY(), getZ() + lx * Math.sin(r) + lz * Math.cos(r));
	}

	/** Is {@code p} out of the way of every wall's swing (and off the floor)? */
	public boolean clear(Player p) {
		if (p == null || p.level() != level()) {
			return true;
		}
		double dy = p.getY() - getY();
		if (dy > WALL_H + 0.5 || dy < -3.0) {
			return true;
		}
		double[] l = local(p.position());
		double ax = Math.abs(l[0]), az = Math.abs(l[1]);
		double pad = 0.35, r = HALF + pad;
		boolean inside = ax < r && az < r;
		boolean front = ax < r && l[1] < 0 && l[1] > -(HALF + WALL_H + pad);
		boolean sides = az < r && ax < HALF + WALL_H + pad;
		return !(inside || front || sides);
	}

	/** Is {@code p} walking into the Hulkbuster's open back (just behind it, between it and the front wall)? */
	private boolean walkingIn(ServerPlayer p) {
		if (p.level() != level() || Math.abs(p.getY() - getY()) > 0.8 || com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(p)) {
			return false;
		}
		double[] l = local(p.position());
		return Math.abs(l[0]) < 1.0 && l[1] < -0.45 && l[1] > -1.9;
	}

	// ======================================================================== tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientFx();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (loadedAge >= 0) {
			entityData.set(PHASE_START, level.getGameTime() - loadedAge);
			loadedAge = -1;
		}
		int age = (int) phaseAge(0f);
		ServerPlayer owner = owner(level);
		switch (phase()) {
			case P_DROP -> {
				if (age == 0) {
					level.playSound(null, getX(), getY() + 10, getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.NEUTRAL, 3f, 0.5f);
				}
				if (age == DROP_TICKS - 12) {
					IronManSounds.playAt(level, getX(), getY() + 8, getZ(), IronManSounds.THRUSTER, 2.5f, 0.6f);
				}
				if (age >= DROP_TICKS) {
					impact(level, true, owner);
					if (carrying()) {
						standUp(level);
					}
					setPhase(P_IMPACT);
				}
			}
			case P_IMPACT -> {
				if (age >= IMPACT_TICKS) {
					setPhase(P_OPENING);
					level.playSound(null, getX(), getY() + 2, getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.9f, 0.7f);
					IronManSounds.playAt(level, getX(), getY() + 2, getZ(), IronManSounds.RELEASE, 1.0f, 0.7f);
				}
			}
			case P_OPENING -> {
				IronManSentryEntity s = sentry();
				if (age == 8 && s != null && !s.isOpen()) {
					s.setOpen(true);
					IronManSounds.play(s, IronManSounds.RELEASE, 0.9f, 1.0f);
				}
				for (int w = 0; w < 3; w++) {
					if (age == OPEN_DELAY[w] + WALL_FALL) {
						wallLands(level, w);
					}
				}
				if (age >= OPEN_TICKS) {
					setPhase(P_OPEN);
				}
			}
			case P_OPEN -> tickOpen(level, owner);
			case P_CLOSING -> {
				if (age == 0) {
					IronManSounds.playAt(level, getX(), getY() + 1, getZ(), IronManSounds.SERVO, 1.0f, 0.7f);
				}
				if (age == CLOSE_DELAY[0] + WALL_RISE || age == 22) {
					IronManSounds.playAt(level, getX(), getY() + 2, getZ(), IronManSounds.CLAMP, 1.0f, 0.75f);
				}
				if (age >= CLOSE_TICKS) {
					IronManSentryEntity s = sentry();
					if (s != null) {
						List<ItemStack> in = s.packIntoCrate();
						integrity = s.integrity();
						for (ItemStack st : in) {
							if (st.getItem() instanceof net.minecraft.world.item.ArmorItem a) {
								int i = com.projecthero.mod.ironman.item.SuitcaseContents.slotOf(a.getType());
								if (i >= 0 && stacks[i].isEmpty()) {
									stacks[i] = st;
								} else {
									spawnAtLocation(st, 0.5f);
								}
							}
						}
						sentryId = null;
					}
					setPhase(P_LIFT);
					IronManSounds.playAt(level, getX(), getY() + 1, getZ(), IronManSounds.POWER_UP, 1.0f, 0.6f);
				}
			}
			case P_LIFT -> {
				if (age == IGNITE_TICKS) {
					IronManSounds.playAt(level, getX(), getY(), getZ(), IronManSounds.THRUSTER, 2.5f, 0.7f);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 2f, 0.5f);
					level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 24, 1.3, 0.1, 1.3, 0.08);
					shake(level, 0.35f, 10);
				}
				if (age >= LIFT_TICKS) {
					reachOrbit(level, owner);
					discard();
				}
			}
			case P_FOLD_DROP -> {
				if (pickupId != null) {
					tickPickup(level, age, false);
					return;
				}
				if (owner == null || !IronManArmor.wearingFullSuit(owner, HulkbusterCrate.SUIT_ID)) {
					abortFold(owner);
					return;
				}
				IronManSuitPlatformBlockEntity.setFrozen(owner, true);
				if (age == 0) {
					IronManSounds.playAt(level, getX(), getY() + 12, getZ(), IronManSounds.THRUSTER, 2.5f, 0.8f);
				}
				if (age >= FOLD_DROP_TICKS) {
					impact(level, false, owner);
					setPhase(P_FOLD_SETTLE);
				}
			}
			case P_FOLD_SETTLE -> {
				if (pickupId != null) {
					tickPickup(level, age, true);
					return;
				}
				if (owner == null || !IronManArmor.wearingFullSuit(owner, HulkbusterCrate.SUIT_ID)) {
					abortFold(owner);
					return;
				}
				IronManSuitPlatformBlockEntity.setFrozen(owner, true);
				if (age == 6) {
					for (int w = 0; w < 3; w++) {
						wallLands(level, w);
					}
				}
				if (age >= SETTLE_TICKS) {
					stepOut(level, owner);
				}
			}
			case P_FOLD_EJECT -> tickEject(level, owner);
			default -> {
			}
		}
	}

	private ServerPlayer owner(ServerLevel level) {
		if (ownerId == null) {
			return null;
		}
		Player p = level.getPlayerByUUID(ownerId);
		return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isRemoved() && !sp.isSpectator() ? sp : null;
	}

	/** Landed with the suit inside: it stands up in the crate as a real (open-backed once the walls are down) sentry. */
	private void standUp(ServerLevel level) {
		List<ItemStack> pieces = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (!stacks[i].isEmpty()) {
				pieces.add(stacks[i]);
				stacks[i] = ItemStack.EMPTY;
			}
		}
		IronManSentryEntity s = IronManSentryEntity.standInCrate(level, ownerId, HulkbusterCrate.SUIT_ID, pieces, position(), getYRot(),
				integrity);
		sentryId = s.getUUID();
	}

	private void tickOpen(ServerLevel level, ServerPlayer owner) {
		if (walkInCooldown > 0) {
			walkInCooldown--;
		}
		if (hintCooldown > 0) {
			hintCooldown--;
		}
		IronManSentryEntity s = sentry();
		if (s != null && (s.pieceCount() == 0 || s.distanceToSqr(position()) > 2.25)) {
			s.setCrated(false); // it walked off (or is closing round its owner): no longer the crate's
			sentryId = null;
			s = null;
		} else if (s == null) {
			sentryId = null;
		}
		if (s == null) {
			// empty: its owner stepped into the suit -- close and lift once they are out of the way
			if (owner == null || clear(owner)) {
				setPhase(P_CLOSING);
			}
			return;
		}
		if (recall) {
			if (s.isOpen() && s.phase() == IronManSentryEntity.PHASE_IDLE) {
				s.setOpen(false);
				IronManSounds.play(s, IronManSounds.CLAMP, 0.8f, 1.1f);
			}
			if (s.phase() == IronManSentryEntity.PHASE_IDLE && !s.isOpen() && (owner == null || clear(owner))) {
				setPhase(P_CLOSING);
			} else if (owner != null && !clear(owner) && hintCooldown <= 0) {
				hintCooldown = 40;
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_crate.step_clear")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return;
		}
		if (owner != null && walkInCooldown <= 0 && s.isOpen() && s.phase() == IronManSentryEntity.PHASE_IDLE && walkingIn(owner)) {
			if (!s.use(owner, true)) {
				walkInCooldown = 40;
			}
		}
	}

	/** Fold: the suit opens and its wearer steps out backwards (the sentry eject), facing the crate's way. */
	private void stepOut(ServerLevel level, ServerPlayer owner) {
		HulkbusterCrate.forget(owner.getUUID());
		IronManSuitPlatformBlockEntity.setFrozen(owner, false);
		IronManSentryEntity s = IronManSentryEntity.ejectIntoCrate(owner, getYRot());
		if (s == null) {
			setPhase(P_OPEN); // could not step out: it just waits, open and empty, until they walk off
			return;
		}
		sentryId = s.getUUID();
		recall = true;
		ejectTicks = 0;
		ejectLast = null;
		Vec3 r = world(1.85, -1.85), l = world(-1.85, -1.85);
		var dims = owner.getDimensions(owner.getPose());
		side = level.noCollision(owner, dims.makeBoundingBox(r)) || !level.noCollision(owner, dims.makeBoundingBox(l)) ? 1 : -1;
		setPhase(P_FOLD_EJECT);
	}

	/** Fold, after the step-out: walk them aside to a corner (clear of every wall), then shut and lift. */
	private void tickEject(ServerLevel level, ServerPlayer owner) {
		IronManSentryEntity s = sentry();
		if (s == null) {
			sentryId = null;
			setPhase(P_OPEN);
			return;
		}
		if (s.phase() != IronManSentryEntity.PHASE_IDLE) {
			return; // still stepping out of it
		}
		if (owner == null || clear(owner)) {
			setPhase(P_CLOSING);
			return;
		}
		ejectTicks++;
		if (ejectTicks > 30) {
			setPhase(P_OPEN); // they will not move aside: it waits (recall is set) until they do
			return;
		}
		Vec3 target = world(side * 1.85, -1.85);
		Vec3 d = target.subtract(owner.position()).multiply(1, 0, 1);
		double len = d.length();
		if (len > 2.8) { // far off (blocked, lagging): put them there
			owner.teleportTo(level, target.x, owner.getY(), target.z, owner.getYRot(), owner.getXRot());
			owner.setDeltaMovement(Vec3.ZERO);
			return;
		}
		Vec3 v = len < 1.0e-4 ? Vec3.ZERO : d.scale(Math.min(0.14, len) / len);
		boolean still = ejectLast != null && owner.position().distanceToSqr(ejectLast) < 1.0e-5;
		ejectLast = owner.position();
		if (ejectTicks > 3 && still) {
			Vec3 at = owner.position().add(v); // a player nothing simulates: carry them
			owner.teleportTo(level, at.x, at.y, at.z, owner.getYRot(), owner.getXRot());
		} else {
			owner.setDeltaMovement(v.x, owner.onGround() ? 0.0 : Math.min(0.0, owner.getDeltaMovement().y), v.z);
			owner.hurtMarked = true;
		}
	}

	/** The fold could not go on (the wearer left, died, took it off): the empty crate just goes back up. */
	private void abortFold(ServerPlayer owner) {
		if (ownerId != null) {
			HulkbusterCrate.forget(ownerId);
		}
		if (owner != null) {
			IronManSuitPlatformBlockEntity.setFrozen(owner, false);
		}
		setPhase(P_LIFT);
	}

	/** The top of the lift: a carried suit goes back up into its orbit slot, as it is (v0.16.7 (user): no healing up there). */
	private void reachOrbit(ServerLevel level, ServerPlayer owner) {
		if (!carrying()) {
			return;
		}
		List<ItemStack> pieces = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			pieces.add(stacks[i]);
			stacks[i] = ItemStack.EMPTY;
		}
		HulkbusterCrate.toOrbit(level, owner, ownerId, pieces, integrity, position());
	}

	/** Recall (C with the suit still standing in it): shut its back, then close and lift once its owner is clear. */
	public boolean recall() {
		if (phase() != P_OPEN || sentry() == null) {
			return false;
		}
		recall = true;
		return true;
	}

	// ======================================================================== effects

	private void impact(ServerLevel level, boolean heavy, ServerPlayer owner) {
		entityData.set(LANDED_AT, level.getGameTime());
		BlockPos below = BlockPos.containing(getX(), getY() - 0.2, getZ());
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		double x = getX(), y = getY(), z = getZ();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + 0.15, z, heavy ? 90 : 45, 1.7, 0.1, 1.7, 0.3);
		for (int i = 0; i < 28; i++) {
			double a = Math.PI * 2 * i / 28;
			double vx = Math.cos(a), vz = Math.sin(a);
			level.sendParticles(ParticleTypes.CLOUD, x + vx * 1.5, y + 0.2, z + vz * 1.5, 0,
					vx, 0.02, vz, heavy ? 0.22 : 0.12);
			if (heavy && i % 2 == 0) {
				level.sendParticles(ParticleTypes.POOF, x + vx * 2.0, y + 0.3, z + vz * 2.0, 0, vx, 0.05, vz, 0.35);
			}
		}
		if (heavy) {
			level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.6, z, 2, 0.6, 0.2, 0.6, 0.0);
			level.sendParticles(ParticleTypes.LAVA, x, y + 0.3, z, 10, 1.2, 0.1, 1.2, 0.0);
			level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.6f, 0.55f);
			level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1.4f, 0.45f);
			shake(level, 1.0f, 16);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.5, 0.5, 1.5))) {
				if (e instanceof Player || !e.isAlive()) {
					continue;
				}
				Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
				away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
				e.push(away.x * 1.1, 0.45, away.z * 1.1);
				e.hurtMarked = true;
				if (e instanceof Enemy) {
					e.hurt(level.damageSources().fallingBlock(this), 8f);
				}
			}
		} else {
			level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.8f, 0.6f);
			IronManSounds.playAt(level, x, y + 1, z, IronManSounds.CLAMP, 0.9f, 0.7f);
			shake(level, 0.35f, 8);
		}
	}

	/** A wall slaps flat onto the ground: a puff of dust along it and a heavy clank. */
	private void wallLands(ServerLevel level, int wall) {
		double lx = wall == 1 ? -(HALF + WALL_H * 0.5) : wall == 2 ? HALF + WALL_H * 0.5 : 0;
		double lz = wall == 0 ? -(HALF + WALL_H * 0.5) : 0;
		Vec3 at = world(lx, lz);
		double sx = wall == 0 ? HALF : WALL_H * 0.5, sz = wall == 0 ? WALL_H * 0.5 : HALF;
		double r = Math.toRadians(getYRot());
		double wx = Math.abs(sx * Math.cos(r)) + Math.abs(sz * Math.sin(r)), wz = Math.abs(sx * Math.sin(r)) + Math.abs(sz * Math.cos(r));
		level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.15, at.z, 6, wx * 0.6, 0.02, wz * 0.6, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0f, 0.55f + wall * 0.05f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.35f, 0.5f);
	}

	private void shake(ServerLevel level, float intensity, int ticks) {
		for (ServerPlayer p : level.players()) {
			double d = p.distanceTo(this);
			if (d < 28) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * (float) (1.0 - d / 28.0 * 0.8), ticks));
			}
		}
	}

	/** Client: the smoke streak, the thruster flames, steam at the seams. */
	private void clientFx() {
		int phase = phase();
		float age = phaseAge(0f);
		float h = height(phase, age, dropHeight());
		double x = getX(), y = getY() + h, z = getZ();
		var r = random;
		switch (phase) {
			case P_DROP -> {
				float hPrev = height(phase, Math.max(0f, age - 1f), dropHeight());
				// the smoke streak: always-visible particles (it is far overhead for most of the fall)
				for (int k = 0; k < 6; k++) {
					double yy = y + WALL_H + (hPrev - h) * (k / 6.0);
					level().addAlwaysVisibleParticle(k % 3 == 0 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.LARGE_SMOKE, true,
							x + (r.nextDouble() - 0.5) * 1.6, yy, z + (r.nextDouble() - 0.5) * 1.6, 0, 0.01, 0);
				}
				for (int k = 0; k < 3; k++) {
					level().addAlwaysVisibleParticle(ParticleTypes.FLAME, true, x + (r.nextDouble() - 0.5) * 2.2, y + r.nextDouble() * 0.6,
							z + (r.nextDouble() - 0.5) * 2.2, 0, 0.6, 0);
				}
			}
			case P_LIFT -> {
				if (age < IGNITE_TICKS) {
					if (age > 3) {
						for (int c = 0; c < 4; c++) {
							double cx = (c & 1) == 0 ? -0.9 : 0.9, cz = (c & 2) == 0 ? -0.9 : 0.9;
							level().addParticle(ParticleTypes.FLAME, x + cx, y + 0.05, z + cz, cx * 0.08, -0.02, cz * 0.08);
						}
						level().addParticle(ParticleTypes.CLOUD, x + (r.nextDouble() - 0.5) * 2.5, y + 0.1, z + (r.nextDouble() - 0.5) * 2.5, 0, 0.02, 0);
					}
				} else {
					for (int k = 0; k < 4; k++) {
						level().addAlwaysVisibleParticle(k == 0 && r.nextBoolean() ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.FLAME, true,
								x + (r.nextDouble() - 0.5) * 1.5, y - r.nextDouble() * 1.5, z + (r.nextDouble() - 0.5) * 1.5, 0, -0.3, 0);
					}
				}
			}
			case P_FOLD_DROP -> {
				for (int c = 0; c < 4; c++) {
					double cx = (c & 1) == 0 ? -HALF : HALF, cz = (c & 2) == 0 ? -HALF : HALF;
					level().addParticle(ParticleTypes.FLAME, x + cx, y - 0.05, z + cz, 0, -0.25, 0);
				}
				if (r.nextInt(3) == 0) {
					level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + (r.nextDouble() - 0.5) * 2, y + WALL_H + 0.5,
							z + (r.nextDouble() - 0.5) * 2, 0, 0.02, 0);
				}
			}
			case P_OPENING -> {
				if (age < 5) {
					for (int c = 0; c < 4; c++) {
						Vec3 p = world((c & 1) == 0 ? -HALF : HALF, (c & 2) == 0 ? -HALF : HALF);
						level().addParticle(ParticleTypes.CLOUD, p.x, getY() + 0.5 + r.nextDouble() * 2.8, p.z, 0, 0.04, 0);
					}
				}
			}
			default -> {
			}
		}
	}

	// ======================================================================== entity plumbing

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(HALF + WALL_H, 0.5, HALF + WALL_H).expandTowards(0, dropHeight() + 6, 0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d2) {
		return d2 < 256 * 256;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	public void kill() {
		if (!level().isClientSide() && !isRemoved() && carrying()) {
			List<ItemStack> pieces = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				pieces.add(stacks[i]);
				stacks[i] = ItemStack.EMPTY;
			}
			HulkbusterCrate.toOrbit((ServerLevel) level(), null, ownerId, pieces, integrity, position()); // back up into its slot
		}
		IronManSentryEntity s = sentry();
		if (s != null) {
			s.setCrated(false);
		}
		if (pickupId != null && level() instanceof ServerLevel sl && sl.getEntity(pickupId) instanceof IronManSentryEntity p) {
			p.setCrated(false); // a recall crate cleared away before it reached the suit: the suit is free again
		}
		super.kill();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && ownerId != null && (phase() == P_FOLD_DROP || phase() == P_FOLD_SETTLE)) {
			HulkbusterCrate.forget(ownerId);
		}
		super.remove(reason);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
			entityData.set(OWNER, Optional.of(ownerId));
		}
		sentryId = tag.hasUUID("Sentry") ? tag.getUUID("Sentry") : null;
		ListTag list = tag.getList("Pieces", Tag.TAG_COMPOUND);
		for (int i = 0; i < 4 && i < list.size(); i++) {
			stacks[i] = ItemStack.parseOptional(registryAccess(), list.getCompound(i));
		}
		integrity = tag.getFloat("Integrity");
		recall = tag.getBoolean("Recall");
		int p = tag.getInt("Phase");
		// a fold that was cut off by the save resumes as a plain open crate (its wearer is not held any more)
		if (p == P_FOLD_DROP || p == P_FOLD_SETTLE) {
			p = carrying() ? P_DROP : P_LIFT;
		} else if (p == P_FOLD_EJECT) {
			p = P_OPEN;
		}
		entityData.set(PHASE, (byte) p);
		loadedAge = Math.max(0, tag.getInt("Age"));
		if (tag.contains("DropHeight")) {
			entityData.set(DROP_HEIGHT, tag.getFloat("DropHeight"));
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		if (sentryId != null) {
			tag.putUUID("Sentry", sentryId);
		}
		ListTag list = new ListTag();
		for (int i = 0; i < 4; i++) {
			list.add(stacks[i].isEmpty() ? new CompoundTag() : stacks[i].save(registryAccess()));
		}
		tag.put("Pieces", list);
		tag.putFloat("Integrity", integrity);
		tag.putBoolean("Recall", recall);
		tag.putInt("Phase", phase());
		tag.putInt("Age", (int) phaseAge(0f));
		tag.putFloat("DropHeight", dropHeight());
	}
}

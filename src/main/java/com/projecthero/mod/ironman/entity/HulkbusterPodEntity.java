package com.projecthero.mod.ironman.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.ironman.HulkbusterInnerSuit;
import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul: the Hulkbuster's support pod (the Mark VII delivery pod's model, drawn
 * 1.6x). One entity, one synced clock ({@link #START} + {@link #MODE}), two jobs -- every viewer draws the same frame.
 *
 * <h2>Replacement Pod (REPAIR)</h2>
 * "calling in a replacement pod will call in a pod that floats 10 blocks above where the player called it and then
 * replacement parts will come and you can visually see the armour pieces get repaired, the pod being called should take 3
 * seconds and then the repair pieces fly to you and it should take about 8 seconds to actually repair you".
 * <ul>
 *   <li>0 .. {@link #ARRIVE_TICKS} (3 s): the pod streaks in from high up and settles {@link #HOVER_UP} blocks above the
 *       spot it was called to, opening.</li>
 *   <li>.. + {@link #REPAIR_TICKS} (8 s): {@link #plates} replacement plates leave it one at a time, each arcing to a
 *       damaged part (worst first, round robin), landing {@link #PLATE_FLIGHT} ticks later and welding on (sparks) until
 *       the next one lands. Each landed plate's part loses its damage gradually over its weld (the damage textures clear
 *       tier by tier), and the integrity climbs in step; at the end the suit is whole and full.</li>
 *   <li>.. + {@link #DEPART_TICKS}: it shuts and climbs away.</li>
 * </ul>
 * The pilot may keep moving and fighting (the plates home on the part wherever it is). It also works on a Sentry / a
 * wrecked suit: sneak + right-click your wrecked Hulkbuster to call it (the wreck reboots once repaired).
 *
 * <h2>Mid-air build (BUILD)</h2>
 * "if the player is wearing another armour and uses the uplink while flying the pod deploys and float above and behind the
 * player and then the hulkbuster pieces fly one by one and build itself around the players armour". The caller rides the
 * pod (held at the spot they called from, standing); the pod arrives above and behind them in {@link #BUILD_ARRIVE}
 * ticks, then the legs, torso, arms and helmet leave it {@link #PIECE_GAP} ticks apart and each flies
 * {@link #PIECE_FLIGHT} ticks onto the body (the client draws the arrived / flying pieces of the Hulkbuster body round the
 * player). At {@link #BUILD_DONE} (5.6 s) the real pieces go on (the worn armour is kept as the inner suit,
 * {@link HulkbusterInnerSuit}), the pilot is let go flying, and the pod leaves. Let go early (sneak, a teleport) and the
 * rest snaps on at once; a logout / server stop finishes it too; a death drops the pieces. Never lost.
 */
public class HulkbusterPodEntity extends Entity implements GeoEntity {
	public static final int MODE_REPAIR = 0, MODE_BUILD = 1;

	public static final int ARRIVE_TICKS = 60;
	public static final int REPAIR_TICKS = 160;
	public static final int DEPART_TICKS = 34;
	public static final int PLATE_FLIGHT = 14;
	public static final double HOVER_UP = 10.0;
	public static final double SKY_UP = 46.0;

	public static final int BUILD_ARRIVE = 30;
	public static final int PIECE_GAP = 12;
	public static final int PIECE_FLIGHT = 16;
	/** legs, torso, arms, helmet. */
	public static final int[] BUILD_ORDER = { HulkbusterParts.LLEG, HulkbusterParts.RLEG, HulkbusterParts.TORSO, HulkbusterParts.LARM,
			HulkbusterParts.RARM, HulkbusterParts.HEAD };
	public static final int BUILD_DONE = BUILD_ARRIVE + PIECE_GAP * (BUILD_ORDER.length - 1) + PIECE_FLIGHT + 6;
	/** Above / behind the held pilot, where the build pod hovers. */
	public static final double BUILD_UP = 2.8, BUILD_BACK = 2.6;

	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> FROM = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> ANCHOR = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Vector3f> HOLD = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> BUILD_YAW = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.FLOAT);
	/** REPAIR: bits 0-3 = plate count, then 3 bits per damaged part (worst first), the part count in bits 28-30. */
	private static final EntityDataAccessor<Integer> PLAN = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DONE = SynchedEntityData.defineId(HulkbusterPodEntity.class, EntityDataSerializers.BOOLEAN);

	/** Players a build is running round (their inner suit waits, they are held). Cleared in {@code ServerStateReset}. */
	private static final Set<UUID> BUILDING = ConcurrentHashMap.newKeySet();

	private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.iron_man_delivery_pod.fly");
	private static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.iron_man_delivery_pod.open");
	private static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.iron_man_delivery_pod.close");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	// server
	private UUID ownerId;
	private UUID targetUuid;
	private final ItemStack[] pieces = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	private float integrityStart = -1f;
	private float[] rawStart;
	private boolean wasFlying;
	private long loadedAge = -1;

	public HulkbusterPodEntity(EntityType<? extends HulkbusterPodEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public static void clearSessionState() {
		BUILDING.clear();
	}

	/** Is a Hulkbuster build running round {@code p}? */
	public static boolean building(Player p) {
		return BUILDING.contains(p.getUUID());
	}

	// ======================================================================== the clock (both sides)

	public int mode() {
		return entityData.get(MODE);
	}

	public float age(float partial) {
		return Math.max(0f, level().getGameTime() - entityData.get(START) + partial);
	}

	public int targetId() {
		return entityData.get(TARGET);
	}

	public Vec3 anchor() {
		Vector3f v = entityData.get(ANCHOR);
		return new Vec3(v.x, v.y, v.z);
	}

	public Vec3 hold() {
		Vector3f v = entityData.get(HOLD);
		return new Vec3(v.x, v.y, v.z);
	}

	public Vec3 from() {
		Vector3f v = entityData.get(FROM);
		return new Vec3(v.x, v.y, v.z);
	}

	public float buildYaw() {
		return entityData.get(BUILD_YAW);
	}

	public boolean done() {
		return entityData.get(DONE);
	}

	/** When it starts to leave. */
	public int departAt() {
		return mode() == MODE_BUILD ? BUILD_DONE + 4 : ARRIVE_TICKS + REPAIR_TICKS + 6;
	}

	public int arriveTicks() {
		return mode() == MODE_BUILD ? BUILD_ARRIVE : ARRIVE_TICKS;
	}

	/** Where the pod is at {@code age}: in from the sky (eased out), hovering with a slow bob, then climbing away. */
	public Vec3 podAt(float age) {
		Vec3 a = anchor();
		int arrive = arriveTicks();
		if (age < arrive) {
			float u = age / arrive;
			float e = 1f - (1f - u) * (1f - u) * (1f - u);
			Vec3 f = from();
			Vec3 p = f.add(a.subtract(f).scale(e));
			// a little overshoot below the hover point that settles back (it brakes hard)
			double settle = u > 0.75f ? -0.35 * Mth.sin((u - 0.75f) / 0.25f * Mth.PI) : 0.0;
			return p.add(0, settle, 0);
		}
		double bob = 0.12 * Mth.sin((age - arrive) * 0.12f);
		int depart = departAt();
		if (age < depart) {
			return a.add(0, bob, 0);
		}
		float u = Math.min(1f, (age - depart) / DEPART_TICKS);
		return a.add(0, bob + SKY_UP * u * u * u, 0);
	}

	/** REPAIR: the plan -- the plate count. */
	public int plates() {
		return entityData.get(PLAN) & 15;
	}

	/** REPAIR: the part plate {@code j} goes to. */
	public int plateTarget(int j) {
		int plan = entityData.get(PLAN);
		int n = Math.max(1, (plan >>> 28) & 7);
		int k = j % n;
		return (plan >>> (4 + 3 * k)) & 7;
	}

	/** REPAIR: the gap between two plates leaving (ticks). */
	public float plateInterval() {
		return (REPAIR_TICKS - PLATE_FLIGHT) / (float) Math.max(1, plates());
	}

	public float plateLaunch(int j) {
		return ARRIVE_TICKS + j * plateInterval();
	}

	/** REPAIR: how far plate {@code j}'s weld is at {@code age} (0 not landed .. 1 welded). */
	public float weld(int j, float age) {
		float land = plateLaunch(j) + PLATE_FLIGHT;
		return Mth.clamp((age - land) / plateInterval(), 0f, 1f);
	}

	/** BUILD: when piece {@code k} (of {@link #BUILD_ORDER}) leaves the pod. */
	public static int pieceLaunch(int k) {
		return BUILD_ARRIVE + k * PIECE_GAP;
	}

	/** BUILD: the order index of {@code part}. */
	public static int orderOf(int part) {
		for (int k = 0; k < BUILD_ORDER.length; k++) {
			if (BUILD_ORDER[k] == part) {
				return k;
			}
		}
		return 0;
	}

	public static int packPlan(int[] parts, int plates) {
		int plan = Math.min(15, plates);
		int n = Math.min(6, parts.length);
		for (int k = 0; k < n; k++) {
			plan |= (parts[k] & 7) << (4 + 3 * k);
		}
		return plan | (n << 28);
	}

	// ======================================================================== calling (server)

	/** {@code owner}'s repair pod already on its way / at work, or null. */
	public static HulkbusterPodEntity activeRepair(ServerPlayer owner) {
		for (HulkbusterPodEntity pod : owner.serverLevel().getEntities(IronManEntityTypes.HULKBUSTER_POD,
				p -> !p.isRemoved() && p.mode() == MODE_REPAIR && owner.getUUID().equals(p.ownerId))) {
			return pod;
		}
		return null;
	}

	/**
	 * The Replacement Pod for {@code target} -- {@code owner} wearing the Hulkbuster, or {@code owner}'s Hulkbuster Sentry
	 * (wrecked or not). Null (with a reason) when there is nothing to repair or one is already working.
	 */
	public static HulkbusterPodEntity callRepair(ServerPlayer owner, Entity target) {
		float max = IronManEnergy.maxIntegrity(HulkbusterParts.SUIT_ID);
		float integrity;
		float[] raw;
		if (target instanceof IronManSentryEntity s && s.hulkbuster()) {
			integrity = s.integrity();
			raw = HulkbusterParts.raw(s.stack(1));
		} else if (target == owner && HulkbusterParts.wearing(owner)) {
			integrity = IronManEnergy.integrity(owner, HulkbusterParts.SUIT_ID);
			raw = HulkbusterParts.raw(owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
		} else {
			return null;
		}
		float[] eff = HulkbusterParts.effective(raw, integrity / Math.max(1f, max));
		int[] order = HulkbusterParts.damagedOrder(eff);
		if (integrity >= max - 0.01f && order.length == 0) {
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.pod_not_needed"), true);
			return null;
		}
		if (activeRepair(owner) != null) {
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_pod.busy").withStyle(ChatFormatting.GRAY), true);
			return null;
		}
		if (order.length == 0) {
			order = new int[] { HulkbusterParts.TORSO, HulkbusterParts.LARM, HulkbusterParts.RARM };
		}
		int plates = Math.min(12, Math.max(6, order.length * 2));
		ServerLevel level = owner.serverLevel();
		HulkbusterPodEntity pod = new HulkbusterPodEntity(IronManEntityTypes.HULKBUSTER_POD, level);
		pod.ownerId = owner.getUUID();
		pod.targetUuid = target.getUUID();
		pod.integrityStart = integrity;
		pod.rawStart = raw;
		Vec3 call = target.position();
		Vec3 anchor = call.add(0, HOVER_UP, 0);
		double a = owner.getRandom().nextDouble() * Math.PI * 2;
		// mostly straight down out of the sky (a short slant: it stays over chunks that tick)
		Vec3 from = anchor.add(Math.cos(a) * 4, SKY_UP, Math.sin(a) * 4);
		pod.entityData.set(MODE, (byte) MODE_REPAIR);
		pod.entityData.set(TARGET, target.getId());
		pod.entityData.set(ANCHOR, new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z));
		pod.entityData.set(FROM, new Vector3f((float) from.x, (float) from.y, (float) from.z));
		pod.entityData.set(PLAN, packPlan(order, plates));
		pod.start(level, from, (float) Math.toDegrees(Math.atan2(call.z - anchor.z, call.x - anchor.x)) - 90f);
		IronManSounds.play(owner, IronManSounds.TARGET_LOCK, 1.0f, 0.6f);
		owner.displayClientMessage(Component.translatable("message.projecthero.ironman.comic.pod_inbound").withStyle(ChatFormatting.AQUA), true);
		return pod;
	}

	/**
	 * The mid-air build: {@code pieces} (the Uplink's four stacks, stamped) fly onto {@code p} out of a pod above and
	 * behind them. Null if it can't start (nothing happens then -- the caller keeps the Uplink).
	 */
	public static HulkbusterPodEntity callBuild(ServerPlayer p, List<ItemStack> stacks) {
		if (building(p) || p.isPassenger() || stacks.isEmpty()) {
			return null;
		}
		ServerLevel level = p.serverLevel();
		HulkbusterPodEntity pod = new HulkbusterPodEntity(IronManEntityTypes.HULKBUSTER_POD, level);
		pod.ownerId = p.getUUID();
		pod.targetUuid = p.getUUID();
		for (ItemStack s : stacks) {
			if (s.getItem() instanceof IronManArmorItem a) {
				int i = com.projecthero.mod.ironman.item.SuitcaseContents.slotOf(a.getType());
				if (i >= 0 && pod.pieces[i].isEmpty()) {
					pod.pieces[i] = s;
				}
			}
		}
		float yaw = p.getYRot();
		Vec3 back = Vec3.directionFromRotation(0f, yaw).scale(-BUILD_BACK);
		Vec3 hold = p.position();
		Vec3 anchor = hold.add(back).add(0, BUILD_UP, 0);
		Vec3 from = anchor.add(back.scale(1.5)).add(0, SKY_UP, 0);
		pod.entityData.set(MODE, (byte) MODE_BUILD);
		pod.entityData.set(TARGET, p.getId());
		pod.entityData.set(ANCHOR, new Vector3f((float) anchor.x, (float) anchor.y, (float) anchor.z));
		pod.entityData.set(HOLD, new Vector3f((float) hold.x, (float) hold.y, (float) hold.z));
		pod.entityData.set(FROM, new Vector3f((float) from.x, (float) from.y, (float) from.z));
		pod.entityData.set(BUILD_YAW, yaw);
		pod.wasFlying = true;
		pod.start(level, from, yaw);
		if (IronManFlight.isFlying(p)) {
			IronManFlight.setFlying(p, false); // held by the pod instead (the worn suit's flight stops, the Hulkbuster's starts after)
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		if (!p.startRiding(pod, true)) {
			java.util.Arrays.fill(pod.pieces, ItemStack.EMPTY); // v0.16.7 (user): the caller keeps the stacks (back up to orbit)
			pod.discard();
			return null;
		}
		BUILDING.add(p.getUUID());
		IronManSounds.play(p, IronManSounds.TARGET_LOCK, 1.0f, 0.7f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_pod.build_inbound").withStyle(ChatFormatting.AQUA), true);
		return pod;
	}

	private void start(ServerLevel level, Vec3 at, float yaw) {
		entityData.set(START, level.getGameTime());
		moveTo(at.x, at.y, at.z, yaw, 0f);
		level.addFreshEntity(this);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.NEUTRAL, 3f, 0.6f);
	}

	// ======================================================================== state

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(MODE, (byte) MODE_REPAIR);
		builder.define(START, 0L);
		builder.define(TARGET, -1);
		builder.define(FROM, new Vector3f());
		builder.define(ANCHOR, new Vector3f());
		builder.define(HOLD, new Vector3f());
		builder.define(BUILD_YAW, 0f);
		builder.define(PLAN, 0);
		builder.define(DONE, false);
	}

	// ======================================================================== tick

	@Override
	public void tick() {
		super.tick();
		float age = age(0f);
		Vec3 at = podAt(age);
		setPos(at.x, at.y, at.z);
		if (level().isClientSide()) {
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (loadedAge >= 0) {
			entityData.set(START, level.getGameTime() - loadedAge);
			loadedAge = -1;
		}
		int t = (int) age;
		if (t == arriveTicks() - 8) {
			IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.THRUSTER, 2.0f, 0.7f);
		}
		if (t == arriveTicks()) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 1.0f, 0.6f);
			IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.RELEASE, 1.0f, 0.8f);
		}
		if (t % 3 == 0 && age < arriveTicks()) {
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.2, at.z, 3, 0.15, 0.1, 0.15, 0.01);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 2, 0.2, 0.3, 0.2, 0.01);
		}
		if (mode() == MODE_BUILD) {
			tickBuild(level, t);
		} else {
			tickRepair(level, age);
		}
		if (t == departAt()) {
			IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.CLAMP, 1.0f, 0.8f);
			IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.THRUSTER, 2.0f, 0.8f);
		}
		if (t >= departAt() + DEPART_TICKS) {
			discard();
		}
	}

	private ServerPlayer owner(ServerLevel level) {
		if (ownerId == null) {
			return null;
		}
		Player p = level.getPlayerByUUID(ownerId);
		return p instanceof ServerPlayer sp && !sp.isRemoved() ? sp : null;
	}

	// ---------------- REPAIR

	private Entity target(ServerLevel level) {
		if (targetUuid == null) {
			return null;
		}
		Entity e = level.getEntity(targetUuid);
		if (e instanceof ServerPlayer sp) {
			return HulkbusterParts.wearing(sp) && sp.isAlive() ? sp : null;
		}
		return e instanceof IronManSentryEntity s && !s.isRemoved() && s.hulkbuster() && s.pieceCount() > 0 ? s : null;
	}

	private void tickRepair(ServerLevel level, float age) {
		if (done() || age < ARRIVE_TICKS) {
			return;
		}
		Entity target = target(level);
		if (target == null || rawStart == null || integrityStart < 0f) {
			finishRepair(level, null, false); // the suit is gone (taken off, folded away): it leaves with what it did
			return;
		}
		int n = plates();
		float max = IronManEnergy.maxIntegrity(HulkbusterParts.SUIT_ID);
		// each part heals as its plates weld; the integrity climbs with the welded share of all the plates
		float[] count = new float[HulkbusterParts.COUNT];
		float[] welded = new float[HulkbusterParts.COUNT];
		float total = 0f;
		for (int j = 0; j < n; j++) {
			int part = plateTarget(j);
			float w = weld(j, age);
			count[part] += 1f;
			welded[part] += w;
			total += w;
		}
		float[] raw = new float[HulkbusterParts.COUNT];
		for (int i = 0; i < HulkbusterParts.COUNT; i++) {
			raw[i] = count[i] <= 0f ? rawStart[i] * (1f - total / Math.max(1, n)) : rawStart[i] * (1f - welded[i] / count[i]);
		}
		float integrity = integrityStart + (max - integrityStart) * (total / Math.max(1, n));
		apply(target, raw, integrity);
		// plate landings: a clang, a burst of sparks at the part
		for (int j = 0; j < n; j++) {
			int land = Math.round(plateLaunch(j) + PLATE_FLIGHT);
			int launch = Math.round(plateLaunch(j));
			if ((int) age == launch) {
				Vec3 p = podAt(age);
				level.playSound(null, p.x, p.y, p.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.NEUTRAL, 0.8f, 0.7f);
			}
			if ((int) age == land) {
				Vec3 at = partPos(target, plateTarget(j));
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.45f, 1.3f);
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.CLAMP, 0.8f, 1.1f);
			}
			if ((int) age > land && (int) age < land + plateInterval() && ((int) age - land) % 6 == 0) {
				Vec3 at = partPos(target, plateTarget(j));
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15f, 1.9f);
			}
		}
		if (age >= ARRIVE_TICKS + REPAIR_TICKS) {
			finishRepair(level, target, true);
		}
	}

	private static void apply(Entity target, float[] raw, float integrity) {
		if (target instanceof ServerPlayer sp) {
			IronManEnergy.setIntegrity(sp, HulkbusterParts.SUIT_ID, integrity);
			HulkbusterParts.write(sp.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST), raw);
		} else if (target instanceof IronManSentryEntity s) {
			s.setIntegrity(integrity);
			s.setChestParts(raw);
		}
	}

	private void finishRepair(ServerLevel level, Entity target, boolean whole) {
		entityData.set(DONE, true);
		if (target != null && whole) {
			apply(target, new float[HulkbusterParts.COUNT], IronManEnergy.maxIntegrity(HulkbusterParts.SUIT_ID));
			if (target instanceof IronManSentryEntity s) {
				s.rebootNow();
			}
			Vec3 c = target.position().add(0, 1.5, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.6, 1.0, 0.6, 0.15);
			IronManSounds.playAt(level, c.x, c.y, c.z, IronManSounds.POWER_UP, 1.0f, 1.0f);
			ServerPlayer owner = owner(level);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_pod.repaired").withStyle(ChatFormatting.GREEN),
						true);
			}
		}
		// jump the clock to the departure (it was mid-weld if the suit went away)
		long start = level.getGameTime() - departAt();
		if (start > entityData.get(START)) {
			entityData.set(START, start);
		}
	}

	/** A rough position of {@code part} on a worn suit / a Sentry (server sounds, tests). */
	public static Vec3 partPos(Entity target, int part) {
		if (target instanceof Player p) {
			return HulkbusterParts.partPos(p, part);
		}
		double h = 3.0;
		double yaw = Math.toRadians(target.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return switch (part) {
			case HulkbusterParts.HEAD -> target.position().add(0, h * 0.9, 0);
			case HulkbusterParts.LARM -> target.position().add(right.scale(-0.75)).add(0, h * 0.6, 0);
			case HulkbusterParts.RARM -> target.position().add(right.scale(0.75)).add(0, h * 0.6, 0);
			case HulkbusterParts.LLEG -> target.position().add(right.scale(-0.3)).add(0, h * 0.2, 0);
			case HulkbusterParts.RLEG -> target.position().add(right.scale(0.3)).add(0, h * 0.2, 0);
			default -> target.position().add(0, h * 0.62, 0);
		};
	}

	// ---------------- BUILD

	private void tickBuild(ServerLevel level, int t) {
		if (done()) {
			return;
		}
		// anywhere on the server (a portal mid-build finishes it where they went; a logout finishes it first, completeFor)
		ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (p == null) {
			p = owner(level);
		}
		if (p != null && p.isAlive() && p.level() != level) {
			finishBuild(level, p);
			return;
		}
		if (p == null || !p.isAlive()) {
			// died (or gone) mid-build: v0.16.7 (user) the pod takes the rest of the suit back up to orbit -- never lost
			returnToOrbit(level);
			if (ownerId != null) {
				BUILDING.remove(ownerId);
			}
			entityData.set(DONE, true);
			return;
		}
		if (p.getVehicle() != this) {
			finishBuild(level, p); // let go early: the rest snaps on
			return;
		}
		p.resetFallDistance();
		for (int k = 0; k < BUILD_ORDER.length; k++) {
			int launch = pieceLaunch(k);
			if (t == launch) {
				Vec3 a = podAt(t);
				level.playSound(null, a.x, a.y, a.z, SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 0.8f, 0.8f + 0.05f * k);
			}
			if (t == launch + PIECE_FLIGHT) {
				Vec3 at = HulkbusterParts.partPos(p, BUILD_ORDER[k]);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.15);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.CLAMP, 1.0f, 0.9f + 0.04f * k);
			}
		}
		if (t >= BUILD_DONE) {
			finishBuild(level, p);
		}
	}

	/** The pieces go on for real now; the pilot is let go (flying) and the pod leaves. Public for the gametests. */
	public void finishBuild(ServerLevel level, ServerPlayer p) {
		if (done()) {
			return;
		}
		entityData.set(DONE, true);
		BUILDING.remove(p.getUUID());
		boolean riding = p.getVehicle() == this;
		HulkbusterInnerSuit.stash(p); // whatever they wear stays on underneath
		float integrity = -1f;
		float energy = IronManEnergy.capacity(HulkbusterParts.SUIT_ID);
		for (int i = 0; i < 4; i++) {
			ItemStack s = pieces[i];
			pieces[i] = ItemStack.EMPTY;
			if (s.isEmpty()) {
				continue;
			}
			integrity = integrity < 0f ? IronManEnergy.stackIntegrity(s, HulkbusterParts.SUIT_ID)
					: Math.min(integrity, IronManEnergy.stackIntegrity(s, HulkbusterParts.SUIT_ID));
			energy = Math.min(energy, IronManEnergy.stackEnergy(s, HulkbusterParts.SUIT_ID));
			if (!IronManSuitUpManager.receivePart(p, s, false)) {
				IronManSuitUpManager.giveBack(p, s);
			}
		}
		if (integrity >= 0f) {
			IronManEnergy.setIntegrity(p, HulkbusterParts.SUIT_ID, integrity);
			IronManEnergy.setEnergy(p, HulkbusterParts.SUIT_ID, energy);
		}
		if (riding) {
			p.stopRiding();
			Vec3 h = hold();
			p.teleportTo(level, h.x, h.y, h.z, p.getYRot(), p.getXRot());
		}
		if (p.level() != level) {
			wasFlying = false;
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		if (wasFlying && IronManArmor.wearingFullSuit(p, HulkbusterParts.SUIT_ID) && !p.onGround()) {
			IronManFlight.setFlying(p, true);
		}
		p.inventoryMenu.broadcastChanges();
		IronManSounds.play(p, IronManSounds.POWER_UP, 1.0f, 0.9f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_pod.build_done").withStyle(ChatFormatting.AQUA), true);
		long start = level.getGameTime() - departAt();
		if (start > entityData.get(START)) {
			entityData.set(START, start);
		}
	}

	/** The logout / dimension-change hooks (idempotent). */
	private static boolean hooked;

	public static void initialize() {
		if (hooked) {
			return;
		}
		hooked = true;
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> completeFor(handler.getPlayer()));
	}

	/** Logout / server stop mid-build: the suit goes on now. */
	public static void completeFor(ServerPlayer p) {
		if (!building(p)) {
			return;
		}
		if (p.getVehicle() instanceof HulkbusterPodEntity pod) {
			pod.finishBuild(p.serverLevel(), p);
			return;
		}
		for (HulkbusterPodEntity pod : p.serverLevel().getEntities(IronManEntityTypes.HULKBUSTER_POD,
				e -> !e.isRemoved() && e.mode() == MODE_BUILD && p.getUUID().equals(e.ownerId))) {
			pod.finishBuild(p.serverLevel(), p);
		}
		BUILDING.remove(p.getUUID());
	}

	// ======================================================================== riding (BUILD)

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction move) {
		if (mode() == MODE_BUILD && passenger.getId() == targetId()) {
			Vec3 h = hold();
			move.accept(passenger, h.x, h.y, h.z);
			if (passenger instanceof LivingEntity le) {
				le.setYBodyRot(buildYaw());
				le.yBodyRotO = buildYaw();
			}
			return;
		}
		super.positionRider(passenger, move);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return hold();
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return mode() == MODE_BUILD && getPassengers().isEmpty();
	}

	// ======================================================================== misc

	@Override
	public AABB getBoundingBoxForCulling() {
		return getBoundingBox().inflate(14.0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double d) {
		return d < 256 * 256;
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
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	/**
	 * v0.16.7 (user) "its only supposed to happen when the suit gets called over and builds itself onto the player while
	 * the player is wearing another iron man armour": does a pod build onto {@code p} play the gantry-style per-texel
	 * build-on? Only over another Iron Man suit (any Iron Man piece worn that isn't the Hulkbuster). A bare player gets the
	 * pieces flying on whole; stepping into the Sentry / crate is never a pod build, so it never builds on at all.
	 */
	public static boolean buildsOnOverArmour(net.minecraft.world.entity.player.Player p) {
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			if (p.getItemBySlot(slot).getItem() instanceof IronManArmorItem a
					&& !com.projecthero.mod.ironman.crate.HulkbusterCrate.SUIT_ID.equals(a.suitId())) {
				return true;
			}
		}
		return false;
	}

	/** v0.16.7 (user): a build cut short -- the pieces still aboard go back into their orbit slot (or a new one), as they are. */
	private void returnToOrbit(ServerLevel level) {
		java.util.List<ItemStack> aboard = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (!pieces[i].isEmpty()) {
				aboard.add(pieces[i]);
				pieces[i] = ItemStack.EMPTY;
			}
		}
		if (aboard.isEmpty()) {
			return;
		}
		float integrity = com.projecthero.mod.ironman.IronManEnergy.stackIntegrity(aboard.get(0),
				com.projecthero.mod.ironman.crate.HulkbusterCrate.SUIT_ID);
		for (ItemStack s : aboard) {
			integrity = Math.min(integrity, com.projecthero.mod.ironman.IronManEnergy.stackIntegrity(s,
					com.projecthero.mod.ironman.crate.HulkbusterCrate.SUIT_ID));
		}
		com.projecthero.mod.ironman.crate.HulkbusterCrate.toOrbit(level, null, ownerId, aboard, integrity, position());
	}

	/** v0.16.7 (user): the orbit slot of the suit this pod still carries (a build on its way), or null. */
	public UUID carriedLink() {
		for (ItemStack s : pieces) {
			UUID id = s.isEmpty() ? null : com.projecthero.mod.ironman.crate.HulkbusterOrbit.linkOf(s);
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason.shouldDestroy()) {
			// never strand a build: v0.16.7 (user) what is still aboard goes back up to orbit
			returnToOrbit((ServerLevel) level());
			if (ownerId != null && mode() == MODE_BUILD) {
				BUILDING.remove(ownerId);
			}
		}
		super.remove(reason);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		entityData.set(MODE, tag.getByte("Mode"));
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
		}
		if (tag.hasUUID("Target")) {
			targetUuid = tag.getUUID("Target");
		}
		loadedAge = tag.getLong("Age");
		entityData.set(DONE, tag.getBoolean("Done"));
		entityData.set(PLAN, tag.getInt("Plan"));
		entityData.set(BUILD_YAW, tag.getFloat("Yaw"));
		for (String k : new String[] { "Anchor", "From", "Hold" }) {
			ListTag l = tag.getList(k, Tag.TAG_FLOAT);
			if (l.size() == 3) {
				Vector3f v = new Vector3f(l.getFloat(0), l.getFloat(1), l.getFloat(2));
				entityData.set(k.equals("Anchor") ? ANCHOR : k.equals("From") ? FROM : HOLD, v);
			}
		}
		integrityStart = tag.contains("IntegrityStart") ? tag.getFloat("IntegrityStart") : -1f;
		if (tag.contains("RawStart")) {
			ListTag l = tag.getList("RawStart", Tag.TAG_FLOAT);
			rawStart = new float[HulkbusterParts.COUNT];
			for (int i = 0; i < Math.min(l.size(), HulkbusterParts.COUNT); i++) {
				rawStart[i] = l.getFloat(i);
			}
		}
		ListTag items = tag.getList("Pieces", Tag.TAG_COMPOUND);
		for (int i = 0; i < Math.min(4, items.size()); i++) {
			pieces[i] = ItemStack.parseOptional(registryAccess(), items.getCompound(i));
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putByte("Mode", entityData.get(MODE));
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		if (targetUuid != null) {
			tag.putUUID("Target", targetUuid);
		}
		tag.putLong("Age", (long) age(0f));
		tag.putBoolean("Done", done());
		tag.putInt("Plan", entityData.get(PLAN));
		tag.putFloat("Yaw", buildYaw());
		for (String k : new String[] { "Anchor", "From", "Hold" }) {
			Vector3f v = entityData.get(k.equals("Anchor") ? ANCHOR : k.equals("From") ? FROM : HOLD);
			ListTag l = new ListTag();
			l.add(net.minecraft.nbt.FloatTag.valueOf(v.x));
			l.add(net.minecraft.nbt.FloatTag.valueOf(v.y));
			l.add(net.minecraft.nbt.FloatTag.valueOf(v.z));
			tag.put(k, l);
		}
		if (integrityStart >= 0f) {
			tag.putFloat("IntegrityStart", integrityStart);
		}
		if (rawStart != null) {
			ListTag l = new ListTag();
			for (float f : rawStart) {
				l.add(net.minecraft.nbt.FloatTag.valueOf(f));
			}
			tag.put("RawStart", l);
		}
		ListTag items = new ListTag();
		for (ItemStack s : pieces) {
			items.add(s.isEmpty() ? new CompoundTag() : s.save(registryAccess()));
		}
		tag.put("Pieces", items);
	}

	// ======================================================================== GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, state -> {
			float a = age(state.getPartialTick());
			if (a >= arriveTicks() - 4 && a < departAt()) {
				return state.setAndContinue(OPEN);
			}
			if (a >= departAt()) {
				return state.setAndContinue(CLOSE);
			}
			return state.setAndContinue(FLY);
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/** Unused helper kept for symmetry with the crate (the build's pieces are drawn by the client). */
	public List<ItemStack> piecesForTests() {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : pieces) {
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}

	public UUID ownerUuid() {
		return ownerId;
	}

	public Optional<UUID> owner() {
		return Optional.ofNullable(ownerId);
	}

	static boolean isTony(ServerPlayer p) {
		return TonyStark.hasPower(p);
	}
}

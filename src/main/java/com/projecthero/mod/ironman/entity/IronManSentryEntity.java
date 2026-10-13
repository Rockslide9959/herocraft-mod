package com.projecthero.mod.ironman.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManBeamPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.9 Sentry Mode (the Mark 8's C, {@link com.projecthero.mod.ironman.ability.IronManMark8#SENTRY}): the empty suit
 * standing on its own. Generic over any full Iron Man suit -- it carries whatever four pieces it was deployed from.
 *
 * <h2>Where the suit lives</h2>
 * The four real armour stacks leave the owner's armour slots and live <b>only</b> in this entity (with the suit's energy
 * and integrity), saved with it. They leave it in exactly three ways: closing around the owner again ({@link #equipOnto}),
 * {@code /kill} or falling out of the world without an owner around (dropped / docked as stamped stacks). Integrity
 * reaching zero never destroys the suit: it powers down where it stands and can still be stepped into.
 *
 * <h2>Using it</h2>
 * <ul>
 *   <li><b>Deploy</b> ({@link #deploy}): the back opens, the owner steps out ~1 block forward, the back closes.</li>
 *   <li><b>Right-click</b> (owner only): open the back / close it again.</li>
 *   <li><b>Sneak + right-click on the open suit</b>: step in -- you are walked into it over {@link #STEP_TICKS} ticks
 *       (input locked, unhurtable), turned to its facing, and the back closes around you, equipping it.</li>
 *   <li><b>Sneak + right-click on the closed suit</b>: cycle the mode {@link #REGULAR} -> {@link #DEFENSIVE} ->
 *       {@link #FOLLOW}.</li>
 * </ul>
 * Hits on it take integrity 1:1 with the damage and it never repairs itself.
 * Regular stands idle. Follow walks after the owner (flies when far / blocked, teleports past
 * {@link #TELEPORT_RANGE}) and lights up the area. Defensive does that too, and shoots repulsors at the threats around
 * the owner -- whoever hurt the owner most recently first, then the {@link HeroTargets#isHostile hostile} closest to the
 * owner -- or punches one that is right next to it; every shot costs the suit's energy and at zero energy it just stands
 * there. In Defensive mode it flies to the owner and closes around them when they drop to 4 hearts or less.
 *
 * <p>The light is one vanilla {@code minecraft:light} block at the suit's chest, moved with it and removed when it
 * moves, powers down, switches to Regular or is removed for good; its position is saved so a reload can clean it up.
 *
 * <h2>v0.15.15 (user requests)</h2>
 * <ul>
 *   <li>Only the <b>back</b> opens (two panels hinged at the sides of the back, like doors); the front stays whole.</li>
 *   <li>Deploy: the back opens, the owner <b>walks backwards</b> out of it (moved by velocity, so the client plays its
 *       own walk cycle and the body is locked to the suit's facing), then it closes.</li>
 *   <li>Step-in: the owner is walked round to the spot behind the suit, turned to its facing, and walks into the open
 *       back with arms a little out -- the same arms-out pose the open suit holds -- so it closes on the right parts.</li>
 *   <li>Follow walks with a real walk cycle (the client now interpolates the entity, so it sees the motion) and takes
 *       off in the Iron Man flight pose to catch up -- far behind, a big height gap, a gap in the ground, stuck --
 *       landing near its owner and walking again.</li>
 *   <li>Follow drains {@value #FOLLOW_DRAIN_PER_SEC} and Defensive {@value #DEFENSIVE_DRAIN_PER_SEC} of capacity a
 *       second; at zero it drops to Regular and stands powered down.</li>
 *   <li>Auto-defend: the owner hurt by a mob or player -> Defensive; {@value #CALM_TICKS} ticks with no hits and no
 *       hostile within {@value #CALM_RADIUS} blocks -> back to the mode it was in.</li>
 *   <li>{@link #flyIn}: a Mark 8 call arrives whole as one flying sentry that lands 2 blocks in front of its owner,
 *       facing them, closed, in Regular ({@link #PHASE_ARRIVE}).</li>
 *   <li>{@link #sendHome}: send-home on the worn Mark 8 -- deploy, step out, close, then fly to the platform
 *       ({@link #PHASE_HOME}; the part-courier rules: unreachable / unloaded dock -> climb and the return queue).</li>
 * </ul>
 */
public class IronManSentryEntity extends Entity {
	// ---- modes (cycle order) ----
	public static final int REGULAR = 0;
	public static final int DEFENSIVE = 1;
	public static final int FOLLOW = 2;
	public static final int MODE_COUNT = 3;

	// ---- phases (server) ----
	public static final int PHASE_IDLE = 0;
	public static final int PHASE_EJECT = 1;
	public static final int PHASE_WRAP = 2;
	public static final int PHASE_RESCUE = 3;
	public static final int PHASE_STEP_IN = 4;
	/** v0.15.15: a Mark 8 call flying in whole, landing in front of its owner. */
	public static final int PHASE_ARRIVE = 5;
	/** v0.15.15: send-home -- flying back to its Suit Platform. */
	public static final int PHASE_HOME = 6;

	// ---- v0.15.15: who is being walked out of / into the suit (synced for the player's pose and body lock) ----
	public static final int STEP_NONE = 0;
	/** Walking backwards out of the open back. */
	public static final int STEP_OUT = 1;
	/** Walking round to the spot behind the suit (free body). */
	public static final int STEP_APPROACH = 2;
	/** Turned to the suit's facing, walking into the open back. */
	public static final int STEP_IN = 3;
	/** Standing in it while it closes. */
	public static final int STEP_WRAP = 4;

	/**
	 * Owners being walked into / closed into their suit right now: nothing hurts them meanwhile (as during a suit-up).
	 * Static scratch state -- cleared on server stop through {@code ServerStateReset}.
	 */
	private static final java.util.Set<UUID> STEPPING = java.util.concurrent.ConcurrentHashMap.newKeySet();

	/** True while {@code player} is being walked into / closed into a Sentry Mode suit (damage immunity). */
	public static boolean steppingIn(Player player) {
		return STEPPING.contains(player.getUUID());
	}

	/** {@code ServerStateReset}. */
	public static void clearSessionState() {
		STEPPING.clear();
	}

	/** Indices of the carried stacks: helmet, chestplate, leggings, boots. */
	public static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	public static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	// ---- tuning ----
	/** Deploy: the back opens (owner held inside) until this tick, then they start walking backwards out of it... */
	public static final int EJECT_PUSH_TICK = 8;
	/** ...over this many ticks... */
	public static final int STEP_OUT_TICKS = 22;
	/** ...this far back (blocks)... */
	public static final double STEP_OUT_DIST = 1.3;
	/** ...and the back closes again at this one. */
	public static final int EJECT_CLOSE_TICK = EJECT_PUSH_TICK + STEP_OUT_TICKS + 2;
	/** Wrap-on: the owner is held inside the open suit; it shuts at this tick... */
	public static final int WRAP_CLOSE_TICK = 4;
	/** ...and the pieces are on at this one. */
	public static final int WRAP_DONE_TICK = 14;
	/** Step-in: from the spot behind the suit, the owner walks into the open back over this many ticks. */
	public static final int STEP_TICKS = 20;
	/** Step-in: the spot behind the suit the owner is walked to first (blocks behind its back). */
	public static final double STEP_BEHIND = 1.3;
	/** Step-in: the walk round to that spot gives up (and places them there) after this long. */
	public static final int STEP_APPROACH_MAX = 40;
	/** Owner walking speed while being walked round / into / out of the suit (blocks a tick). */
	public static final double STEP_WALK_SPEED = 0.2;
	/** An owner this far off where the walk-in / walk-out wants them is put there (blocked, or a client that lags). */
	public static final double STEP_SNAP = 0.8;
	/** Step-in only starts from within this distance of the suit. */
	public static final double STEP_REACH = 5.0;
	/** Defensive rescue: the suit lands this far IN FRONT of its owner, facing their way, open, and they walk into its back. */
	public static final double RESCUE_FRONT = 1.6;
	/** v0.15.15: Follow drains this share of the suit's capacity a second... */
	public static final float FOLLOW_DRAIN_PER_SEC = 0.01f;
	/** ...and Defensive this share. */
	public static final float DEFENSIVE_DRAIN_PER_SEC = 0.02f;
	/** v0.15.15 auto-defend: the owner must be within this range of the suit for a hit on them to count. */
	public static final double AUTO_DEFEND_RANGE = 64.0;
	/** v0.15.15 auto-defend: combat is over after this long with no hit on the owner... */
	public static final int CALM_TICKS = 100;
	/** ...and no hostile within this radius of them. */
	public static final double CALM_RADIUS = 16.0;
	/** v0.15.15: a called Mark 8 lands this far in front of its owner. */
	public static final double ARRIVE_FRONT = 2.0;
	/** v0.15.15: an arriving suit that hasn't landed after this long (stuck in a cave, say) is set down on the spot. */
	public static final int ARRIVE_TIMEOUT = 400;
	/** v0.15.15 send-home: stands this long once closed, then takes off. */
	public static final int HOME_HOLD_TICKS = 10;
	/** v0.15.15 send-home: an unreachable dock -- climbs this high, then goes on the return queue. */
	public static final double HOME_SKY_CLIMB = 40.0;
	private static final int HOME_MAX_FLIGHT = 1200;
	/** Defensive: closes around the owner at or below this health (4 hearts). */
	public static final float RESCUE_HEALTH = 8.0f;
	/** Defensive: only comes to the rescue from within this range. */
	public static final double RESCUE_RANGE = 64.0;
	/** Rescue flight gives up after this long. */
	public static final int RESCUE_TIMEOUT = 200;
	/** Defensive: threats are looked for within this radius of the owner... */
	public static final double DEFEND_RADIUS = 20.0;
	/** ...and must be within this range of the suit to be engaged. */
	public static final double ENGAGE_RANGE = 28.0;
	/** A target this close is punched instead of shot. */
	public static final double MELEE_RANGE = 2.4;
	/** Shots are never closer together than this, whatever the suit's own repulsor tap cooldown is. */
	public static final int MIN_SHOT_COOLDOWN = 20;
	public static final float PUNCH_ENERGY = 3.0f;
	public static final int PUNCH_COOLDOWN = 15;
	/** Follow / Defensive: farther than this from the owner and the suit teleports beside them (like a pet). */
	public static final double TELEPORT_RANGE = 48.0;
	/** Follow / Defensive: farther than this and it flies instead of walking. */
	public static final double FLY_RANGE = 12.0;
	/** Starts walking after the owner beyond this distance... */
	public static final double FOLLOW_START = 4.0;
	/** ...and stops once this close. */
	public static final double FOLLOW_STOP = 2.5;
	public static final double WALK_SPEED = 0.24;
	/** Ticks a walking suit may make no progress before it takes off instead. */
	public static final int STUCK_TICKS = 30;
	/** Client: the back opens / closes over this many ticks. */
	public static final int OPEN_ANIM_TICKS = 8;
	private static final int FIRE_POSE_TICKS = 10;
	private static final int RETARGET_TICKS = 5;
	private static final String MODE_TAG = "ProjectHeroSentryMode";

	private static final EntityDataAccessor<ItemStack> HEAD = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> CHEST = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> LEGS = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<ItemStack> FEET = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.ITEM_STACK);
	private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> INTEGRITY = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> MODE = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FIRING = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.OPTIONAL_UUID);
	/** Entity id of the current Defensive target (-1 = none): the client turns head and arm towards it. */
	private static final EntityDataAccessor<Integer> TARGET_ID = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.INT);
	/** Shots + punches so far: each change is one recoil on the client. */
	private static final EntityDataAccessor<Integer> SHOTS = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.INT);
	/** True while the owner is being walked in / closed in (the suit holds still and faces its front). */
	private static final EntityDataAccessor<Boolean> BUSY = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.15.15: entity id of the player being walked out of / into the suit (-1 = none). */
	private static final EntityDataAccessor<Integer> STEPPER = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.INT);
	/** v0.15.15: which part of the walk ({@link #STEP_OUT} .. {@link #STEP_WRAP}). */
	private static final EntityDataAccessor<Byte> STEP_KIND = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BYTE);
	/** v0.15.15: in Defensive because its owner got hurt (it goes back to its old mode once the fight is over). */
	private static final EntityDataAccessor<Boolean> AUTO = SynchedEntityData.defineId(IronManSentryEntity.class, EntityDataSerializers.BOOLEAN);
	@SuppressWarnings("unchecked")
	private static final EntityDataAccessor<ItemStack>[] STACK_DATA = new EntityDataAccessor[] { HEAD, CHEST, LEGS, FEET };

	private final ItemStack[] stacks = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
	private UUID ownerId;
	private String suitId = "";
	private float energy;
	private float integrity;
	private BlockPos lightPos;

	// server-only bookkeeping (never saved: a reload starts the suit idle)
	private int phase = PHASE_IDLE;
	private int phaseTicks;
	private int fireCooldown;
	private int firePose;
	private Vec3 stepFrom = Vec3.ZERO;
	private float stepYawFrom;
	private int stuckTicks;
	/** After closing around its owner: ticks left before the (already empty) suit vanishes. */
	private int linger;
	private static final int LINGER_TICKS = 4;
	private boolean flying;
	private boolean walking;
	private boolean rescueArmed;
	private LivingEntity target;
	private int shotsFired;
	private int punches;
	// v0.15.15 step-in / step-out
	private int stepStage;
	private int stepStageAt;
	private Vec3 stepLastPos;
	private int stepStill;
	private boolean stepReleased;
	// v0.15.15 auto-defend
	private int prevMode = -1;
	private int lastCombatTick;
	private int seenHurtStamp = Integer.MIN_VALUE;
	// v0.15.15 call arrival / send-home
	private Vec3 arriveBest;
	private int arriveStuck;
	private BlockPos homeDock;
	private boolean homeSkyward;
	private double homeSpeed;

	// client-only interpolation (a plain Entity's lerpTo is a hard setPos: no motion between ticks, no walk cycle)
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYRot;

	// client-only animation state
	public float openAnim;
	public float openAnimO;
	public float walkPos;
	public float walkAmt;
	public float walkAmtO;

	// ---- client pose channels (degrees unless noted), eased towards a per-state target every tick ----
	public static final int P_HEAD_X = 0, P_HEAD_Y = 1, P_RARM_X = 2, P_RARM_Y = 3, P_RARM_Z = 4, P_LARM_X = 5,
			P_LARM_Y = 6, P_LARM_Z = 7, P_RLEG_X = 8, P_LLEG_X = 9, P_RLEG_Z = 10, P_LLEG_Z = 11, P_LEAN = 12,
			P_BOB = 13, P_FLASH = 14;
	public static final int POSE_CHANNELS = 15;
	/** Client: this tick's pose and last tick's (the renderer interpolates between them). */
	public final float[] pose = new float[POSE_CHANNELS];
	public final float[] poseO = new float[POSE_CHANNELS];
	private final float[] poseWant = new float[POSE_CHANNELS];
	private int lastMode = -1;
	private int lastShots = -1;
	private int flourish;
	private float recoil;
	private boolean posed;
	private static final int FLOURISH_TICKS = 16;

	public IronManSentryEntity(EntityType<? extends IronManSentryEntity> type, Level level) {
		super(type, level);
	}

	// ------------------------------------------------------------------ deploy

	/**
	 * Server: step out of the worn suit and leave it standing as a sentry. Needs the full set of one suit on, no suit-up /
	 * suit-down running and no repulsor flight. The four real stacks (charge + integrity stamped on) move into the entity;
	 * nothing is copied. Returns the sentry, or null (with a message) if refused.
	 */
	public static IronManSentryEntity deploy(ServerPlayer player) {
		return deploy(player, true);
	}

	private static IronManSentryEntity deploy(ServerPlayer player, boolean announce) {
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !TonyStark.hasPower(player)) {
			return null;
		}
		if (!IronManArmor.wearingFullSuit(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.need_full")
					.withStyle(ChatFormatting.RED), true);
			return null;
		}
		if (IronManSuitUpManager.inTransition(player)) {
			return null;
		}
		if (IronManFlight.isFlying(player) || player.isPassenger()) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.land_first")
					.withStyle(ChatFormatting.RED), true);
			return null;
		}
		ServerLevel level = player.serverLevel();
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		ItemStack[] pieces = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			ItemStack worn = player.getItemBySlot(SLOTS[i]);
			pieces[i] = worn.copy();
			IronManEnergy.stampStack(pieces[i], energy, integrity);
			player.setItemSlot(SLOTS[i], ItemStack.EMPTY);
		}
		IronManSentryEntity s = new IronManSentryEntity(IronManEntityTypes.SENTRY, level);
		s.ownerId = player.getUUID();
		s.getEntityData().set(OWNER, Optional.of(player.getUUID()));
		s.suitId = suitId;
		for (int i = 0; i < 4; i++) {
			s.setStack(i, pieces[i]);
		}
		s.setEnergy(energy);
		s.setIntegrity(integrity);
		s.setMode(rememberedMode(pieces[1]));
		float yaw = player.getYRot();
		s.moveTo(player.getX(), player.getY(), player.getZ(), yaw, 0f);
		s.setYHeadRot(yaw);
		s.setOpen(true);
		s.setPhase(PHASE_EJECT);
		level.addFreshEntity(s);
		TonyStark.setActiveSuit(player, "");
		player.inventoryMenu.broadcastChanges();
		IronManSounds.play(s, IronManSounds.RELEASE, 0.9f, 0.9f);
		IronManSounds.play(s, IronManSounds.SERVO, 0.7f, 0.8f);
		if (announce) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.deployed",
					modeName(s.mode())).withStyle(ChatFormatting.AQUA), true);
		}
		return s;
	}

	/**
	 * v0.15.15 (user): a called Mark 8 comes in WHOLE -- the four real stacks (taken off the platform / out of the pack by
	 * the caller) ride in one sentry that flies from {@code from} (null = in from the sky round the owner) and lands
	 * {@link #ARRIVE_FRONT} blocks in front of its owner, facing them, closed, in Regular mode. Returns the sentry.
	 */
	public static IronManSentryEntity flyIn(ServerPlayer owner, String suitId, List<ItemStack> pieces, Vec3 from,
			float energy, float integrity) {
		ServerLevel level = owner.serverLevel();
		IronManSentryEntity s = new IronManSentryEntity(IronManEntityTypes.SENTRY, level);
		s.ownerId = owner.getUUID();
		s.getEntityData().set(OWNER, Optional.of(owner.getUUID()));
		s.suitId = suitId;
		for (ItemStack piece : pieces) {
			if (piece.getItem() instanceof ArmorItem a) {
				for (int i = 0; i < 4; i++) {
					if (TYPES[i] == a.getType() && s.stacks[i].isEmpty()) {
						s.setStack(i, piece);
						break;
					}
				}
			}
		}
		s.setEnergy(energy);
		s.setIntegrity(integrity);
		s.setMode(REGULAR);
		s.setOpen(false);
		Vec3 start = from != null ? from : skyStart(level, owner, s);
		float yaw = (float) (Mth.atan2(owner.getZ() - start.z, owner.getX() - start.x) * (180.0 / Math.PI)) - 90f;
		s.moveTo(start.x, start.y, start.z, yaw, 0f);
		s.setYHeadRot(yaw);
		s.flying = true;
		s.setNoGravity(true);
		s.getEntityData().set(FLYING, true);
		s.setPhase(PHASE_ARRIVE);
		level.addFreshEntity(s);
		IronManSounds.play(s, IronManSounds.THRUSTER, 1.0f, 1.0f);
		return s;
	}

	/** Somewhere up in the air round the owner, in ticking, open air (falls back closer in under a roof / in a cave). */
	private static Vec3 skyStart(ServerLevel level, ServerPlayer owner, IronManSentryEntity s) {
		var dims = s.getType().getDimensions();
		for (int attempt = 0; attempt < 8; attempt++) {
			double ang = level.random.nextDouble() * Math.PI * 2;
			double r = attempt < 4 ? 22.0 : 10.0;
			double up = attempt < 4 ? 12 + level.random.nextDouble() * 4 : 4.0;
			Vec3 c = owner.position().add(Math.cos(ang) * r, up, Math.sin(ang) * r);
			if (com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, c)
					&& level.noCollision(s, dims.makeBoundingBox(c))
					&& level.clip(new ClipContext(c.add(0, 1, 0), owner.getEyePosition(), ClipContext.Block.COLLIDER,
							ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS) {
				return c;
			}
		}
		Vec3 back = Vec3.directionFromRotation(0f, owner.getYRot()).scale(-4.0);
		Vec3 c = owner.position().add(back).add(0, 1.0, 0);
		return level.noCollision(s, dims.makeBoundingBox(c)) ? c : owner.position().add(0, 0.5, 0);
	}

	/**
	 * v0.15.15 (user): send-home on the worn Mark 8 -- the suit opens, its owner steps out backwards, it closes, then it
	 * flies itself to the Suit Platform at {@code dock}. Returns the sentry, or null (nothing done) if it can't deploy.
	 */
	public static IronManSentryEntity sendHome(ServerPlayer player, BlockPos dock) {
		if (dock == null) {
			return null;
		}
		IronManSentryEntity s = deploy(player, false);
		if (s != null) {
			s.homeDock = dock.immutable();
		}
		return s;
	}

	private static int rememberedMode(ItemStack chest) {
		CustomData data = chest.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return REGULAR;
		}
		int m = data.copyTag().getInt(MODE_TAG);
		return m >= 0 && m < MODE_COUNT ? m : REGULAR;
	}

	// ------------------------------------------------------------------ accessors

	public UUID ownerId() {
		return level().isClientSide() ? getEntityData().get(OWNER).orElse(null) : ownerId;
	}

	public boolean isOwner(Player player) {
		UUID id = ownerId();
		return id != null && id.equals(player.getUUID());
	}

	public String suitId() {
		if (suitId.isEmpty()) {
			for (int i = 0; i < 4; i++) {
				if (stack(i).getItem() instanceof IronManArmorItem p) {
					return p.suitId();
				}
			}
		}
		return suitId;
	}

	public IronManSuit suit() {
		return IronManSuits.byId(suitId());
	}

	/** Server: the real stack; client: the synced copy. */
	public ItemStack stack(int i) {
		return level().isClientSide() ? getEntityData().get(STACK_DATA[i]) : stacks[i];
	}

	private void setStack(int i, ItemStack s) {
		stacks[i] = s == null ? ItemStack.EMPTY : s;
		getEntityData().set(STACK_DATA[i], stacks[i].copy());
	}

	public int pieceCount() {
		int n = 0;
		for (int i = 0; i < 4; i++) {
			n += stack(i).isEmpty() ? 0 : 1;
		}
		return n;
	}

	public float energy() {
		return level().isClientSide() ? getEntityData().get(ENERGY) : energy;
	}

	public float integrity() {
		return level().isClientSide() ? getEntityData().get(INTEGRITY) : integrity;
	}

	public void setEnergy(float v) {
		energy = Math.max(0f, Math.min(IronManEnergy.capacity(suitId()), v));
		getEntityData().set(ENERGY, energy);
	}

	public void setIntegrity(float v) {
		integrity = Math.max(0f, Math.min(IronManEnergy.maxIntegrity(suitId()), v));
		getEntityData().set(INTEGRITY, integrity);
	}

	/** Energy and integrity both above zero: it can move, light up and fight. */
	public boolean powered() {
		return energy() > 0f && integrity() > 0f;
	}

	public int mode() {
		return Mth.clamp(getEntityData().get(MODE), 0, MODE_COUNT - 1);
	}

	public void setMode(int m) {
		getEntityData().set(MODE, (byte) Math.floorMod(m, MODE_COUNT));
	}

	public boolean isOpen() {
		return getEntityData().get(OPEN);
	}

	public void setOpen(boolean open) {
		getEntityData().set(OPEN, open);
	}

	/** Client: the synced Defensive target id (-1 = none). */
	public int targetId() {
		return getEntityData().get(TARGET_ID);
	}

	/** Client: shots + punches so far (a change = recoil). */
	public int shotCounter() {
		return getEntityData().get(SHOTS);
	}

	/** Client: the owner is being walked / closed into the suit. */
	public boolean busy() {
		return getEntityData().get(BUSY);
	}

	public boolean firing() {
		return getEntityData().get(FIRING);
	}

	public boolean flying() {
		return getEntityData().get(FLYING);
	}

	public int phase() {
		return phase;
	}

	private void setPhase(int p) {
		phase = p;
		phaseTicks = 0;
	}

	/** Server: repulsor shots fired since this entity was created (gametests). */
	public int shotsFired() {
		return shotsFired;
	}

	/** Server: punches thrown since this entity was created (gametests). */
	public int punches() {
		return punches;
	}

	/** Server: the light block this suit currently placed, or null. */
	public BlockPos lightPos() {
		return lightPos;
	}

	public LivingEntity currentTarget() {
		return target;
	}

	public static Component modeName(int mode) {
		return Component.translatable("message.projecthero.ironman.sentry.mode." + switch (mode) {
			case DEFENSIVE -> "defensive";
			case FOLLOW -> "follow";
			default -> "regular";
		});
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HEAD, ItemStack.EMPTY);
		builder.define(CHEST, ItemStack.EMPTY);
		builder.define(LEGS, ItemStack.EMPTY);
		builder.define(FEET, ItemStack.EMPTY);
		builder.define(ENERGY, 0f);
		builder.define(INTEGRITY, 0f);
		builder.define(MODE, (byte) REGULAR);
		builder.define(OPEN, false);
		builder.define(FIRING, false);
		builder.define(FLYING, false);
		builder.define(OWNER, Optional.empty());
		builder.define(TARGET_ID, -1);
		builder.define(SHOTS, 0);
		builder.define(BUSY, false);
		builder.define(STEPPER, -1);
		builder.define(STEP_KIND, (byte) STEP_NONE);
		builder.define(AUTO, false);
	}

	/** v0.15.15: entity id of the player being walked out of / into this suit, or -1. */
	public int stepperId() {
		return getEntityData().get(STEPPER);
	}

	/** v0.15.15: {@link #STEP_NONE} .. {@link #STEP_WRAP}. */
	public int stepKind() {
		return getEntityData().get(STEP_KIND);
	}

	/** v0.15.15: in Defensive only because its owner got hurt. */
	public boolean autoDefending() {
		return getEntityData().get(AUTO);
	}

	/** v0.15.15 (server): the mode an auto-defend goes back to, or -1. */
	public int modeBeforeFight() {
		return prevMode;
	}

	/** v0.15.15: energy drained a second in {@code mode}, as a share of capacity (0 = none). */
	public static float drainPerSecond(int mode) {
		return mode == FOLLOW ? FOLLOW_DRAIN_PER_SEC : mode == DEFENSIVE ? DEFENSIVE_DRAIN_PER_SEC : 0f;
	}

	/** v0.15.15 (server): the dock a send-home is flying to, or null. */
	public BlockPos homeDock() {
		return homeDock;
	}

	private void setStepper(ServerPlayer p, int kind) {
		getEntityData().set(STEPPER, p == null || kind == STEP_NONE ? -1 : p.getId());
		getEntityData().set(STEP_KIND, (byte) (p == null ? STEP_NONE : kind));
	}

	private void endAuto() {
		prevMode = -1;
		getEntityData().set(AUTO, false);
	}

	// ------------------------------------------------------------------ interaction

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (level().isClientSide()) {
			return isOwner(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (!isOwner(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.not_yours")
					.withStyle(ChatFormatting.RED), true);
			return InteractionResult.CONSUME;
		}
		return use(sp, sp.isShiftKeyDown()) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	/**
	 * The owner's right-click (public for the gametests). Right-click opens / closes the back. Sneak + right-click: on the
	 * OPEN suit, step into it (the walk-in animation, then it closes around you); on the closed suit, cycle the mode.
	 * Ignored while the suit is busy (stepping out, closing around someone, flying to the rescue).
	 */
	public boolean use(ServerPlayer owner, boolean sneak) {
		if (phase != PHASE_IDLE || isRemoved()) {
			return false;
		}
		if (sneak && isOpen()) {
			if (!canWrap(owner) || owner.distanceTo(this) > STEP_REACH) {
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.cant_enter")
						.withStyle(ChatFormatting.RED), true);
				return false;
			}
			beginStepIn(owner);
			return true;
		}
		if (sneak) {
			setMode(mode() + 1);
			endAuto(); // the owner picked a mode: no going back to the pre-fight one
			target = null;
			if (mode() == DEFENSIVE) {
				rescueArmed = owner.getHealth() > RESCUE_HEALTH;
			}
			IronManSounds.play(this, IronManSounds.WEAPON_SELECT, 0.8f, 1.0f + mode() * 0.1f);
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.mode", modeName(mode()))
					.withStyle(ChatFormatting.AQUA), true);
			return true;
		}
		boolean open = !isOpen();
		setOpen(open);
		IronManSounds.play(this, open ? IronManSounds.RELEASE : IronManSounds.CLAMP, 0.8f, open ? 1.0f : 1.1f);
		IronManSounds.play(this, IronManSounds.SERVO, 0.6f, open ? 0.9f : 1.1f);
		if (open) {
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.opened")
					.withStyle(ChatFormatting.AQUA), true);
		}
		return true;
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		if (linger > 0) {
			if (--linger == 0) {
				discard();
			}
			return;
		}
		if (pieceCount() == 0) {
			discard();
			return;
		}
		if (getY() < level.getMinBuildHeight() - 16) {
			onBelowWorld();
			return;
		}
		phaseTicks++;
		if (fireCooldown > 0) {
			fireCooldown--;
		}
		if (firePose > 0 && --firePose == 0) {
			getEntityData().set(FIRING, false);
		}
		ServerPlayer owner = owner(level);
		switch (phase) {
			case PHASE_EJECT -> tickEject(level, owner);
			case PHASE_STEP_IN -> tickStepping(level, owner);
			case PHASE_WRAP -> tickWrap(level, owner);
			case PHASE_RESCUE -> tickRescue(level, owner);
			case PHASE_ARRIVE -> tickArrive(level, owner);
			case PHASE_HOME -> tickHome(level, owner);
			default -> tickIdle(level, owner);
		}
		if (!isRemoved()) {
			getEntityData().set(FLYING, flying);
		}
	}

	/** The owner if they are online, alive and in this dimension (found by level so mock players count), else null. */
	private ServerPlayer owner(ServerLevel level) {
		if (ownerId == null) {
			return null;
		}
		Player p = level.getPlayerByUUID(ownerId);
		return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isRemoved() && !sp.isSpectator() ? sp : null;
	}

	/** The suit's facing as a flat unit vector. */
	private Vec3 forward() {
		return Vec3.directionFromRotation(0f, getYRot());
	}

	/**
	 * v0.15.15: walk {@code owner} along a path -- {@code at} is where they should be this tick, {@code next} where they
	 * should be next tick. Moved by velocity (so their client plays its own walk cycle, smoothly); an owner who is
	 * {@link #STEP_SNAP} or more off the path (blocked, or not simulated at all) is put on it.
	 */
	private void guide(ServerLevel level, ServerPlayer owner, Vec3 at, Vec3 next, float yaw) {
		Vec3 off = at.subtract(owner.position());
		if (off.horizontalDistance() > STEP_SNAP || Math.abs(off.y) > 1.2) {
			owner.teleportTo(level, at.x, at.y, at.z, yaw, owner.getXRot());
			owner.setDeltaMovement(Vec3.ZERO);
			return;
		}
		Vec3 v = next.subtract(owner.position()).multiply(1, 0, 1);
		double len = v.length();
		if (len > STEP_WALK_SPEED * 1.5) {
			v = v.scale(STEP_WALK_SPEED * 1.5 / len);
		}
		owner.setDeltaMovement(v.x, owner.onGround() ? 0.0 : Math.min(0.0, owner.getDeltaMovement().y), v.z);
		owner.hurtMarked = true;
	}

	/**
	 * v0.15.15 (user): the back is open and the owner walks BACKWARDS out of it -- facing the suit's way, the body locked
	 * to it (client), {@link #STEP_OUT_DIST} blocks back over {@link #STEP_OUT_TICKS} -- then it closes. An owner who has
	 * already got well clear (thrown, teleported) is just let go.
	 */
	private void tickEject(ServerLevel level, ServerPlayer owner) {
		walk(Vec3.ZERO);
		if (owner != null && !stepReleased && owner.distanceTo(this) > 2.6) {
			stepReleased = true;
		}
		if (owner != null && !stepReleased && phaseTicks < EJECT_CLOSE_TICK) {
			Vec3 fwd = forward();
			if (phaseTicks < EJECT_PUSH_TICK) {
				setStepper(owner, STEP_WRAP); // held inside while the back swings open
				guide(level, owner, position(), position(), getYRot());
			} else {
				setStepper(owner, STEP_OUT);
				float t0 = Math.min(1f, (phaseTicks - EJECT_PUSH_TICK) / (float) STEP_OUT_TICKS);
				float t1 = Math.min(1f, (phaseTicks + 1 - EJECT_PUSH_TICK) / (float) STEP_OUT_TICKS);
				Vec3 at = position().add(fwd.scale(-STEP_OUT_DIST * smooth(t0)));
				Vec3 next = position().add(fwd.scale(-STEP_OUT_DIST * smooth(t1)));
				guide(level, owner, at, next, getYRot());
				if (t0 < 1f && phaseTicks % 7 == 0) {
					level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
							SoundSource.PLAYERS, 0.2f, 0.8f + level.random.nextFloat() * 0.2f);
				}
			}
		} else {
			setStepper(null, STEP_NONE);
		}
		if (phaseTicks >= EJECT_CLOSE_TICK) {
			setStepper(null, STEP_NONE);
			stepReleased = false;
			setOpen(false);
			IronManSounds.play(this, IronManSounds.CLAMP, 0.8f, 1.1f);
			rescueArmed = owner != null && owner.getHealth() > RESCUE_HEALTH;
			if (homeDock != null) {
				setPhase(PHASE_HOME); // send-home: closed, now it flies itself to its platform
				homeSpeed = 0.0;
				homeSkyward = false;
				clearLight();
			} else {
				setPhase(PHASE_IDLE);
			}
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private void tickIdle(ServerLevel level, ServerPlayer owner) {
		getEntityData().set(TARGET_ID, target != null && target.isAlive() ? target.getId() : -1);
		if (!powered()) {
			target = null;
			clearLight();
			flying = false;
			walk(Vec3.ZERO);
			if (energy <= 0f && mode() != REGULAR) {
				setMode(REGULAR); // v0.15.15: drained -- it drops to Regular and stands powered down
				endAuto();
			}
			return;
		}
		autoDefend(level, owner);
		int mode = mode();
		if (owner != null && mode != REGULAR) {
			// v0.15.15: Follow / Defensive cost energy every second they run
			float drain = IronManEnergy.capacity(suitId()) * drainPerSecond(mode) / 20f;
			setEnergy(energy - drain);
			if (energy <= 0f) {
				setMode(REGULAR);
				endAuto();
				target = null;
				clearLight();
				IronManSounds.play(this, IronManSounds.POWER_FAIL, 0.9f, 1.0f);
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.drained")
						.withStyle(ChatFormatting.RED), true);
				flying = false;
				walk(Vec3.ZERO);
				return;
			}
		}
		if (owner == null || mode == REGULAR) {
			target = null;
			clearLight();
			walk(Vec3.ZERO);
			return;
		}
		updateLight(level);
		if (isOpen()) {
			walk(Vec3.ZERO); // waiting for its owner to step in
			faceTowards(owner.position());
			return;
		}
		if (mode == DEFENSIVE) {
			if (owner.getHealth() > RESCUE_HEALTH) {
				rescueArmed = true;
			} else if (rescueArmed && canWrap(owner) && distanceTo(owner) <= RESCUE_RANGE) {
				target = null;
				setPhase(PHASE_RESCUE);
				IronManSounds.play(this, IronManSounds.THRUSTER, 0.9f, 1.2f);
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.rescue")
						.withStyle(ChatFormatting.GOLD), true);
				return;
			}
			if (target == null || !target.isAlive() || tickCount % RETARGET_TICKS == 0) {
				target = pickTarget(level, owner);
			}
		} else {
			target = null;
		}
		follow(level, owner);
		if (target != null) {
			engage(level, owner, target);
		}
	}

	/**
	 * v0.15.15 (user): the owner hurt by a mob or a player (a threat by {@link HeroTargets#isHostile}) switches a powered
	 * suit within {@link #AUTO_DEFEND_RANGE} to Defensive; once {@link #CALM_TICKS} pass with no hit on them and no
	 * hostile within {@link #CALM_RADIUS} blocks of them, it goes back to the mode it was in. Choosing a mode by hand in
	 * between keeps that mode.
	 */
	private void autoDefend(ServerLevel level, ServerPlayer owner) {
		if (owner == null) {
			return;
		}
		int stamp = owner.getLastHurtByMobTimestamp();
		if (seenHurtStamp == Integer.MIN_VALUE) {
			seenHurtStamp = stamp; // whatever hit them before this suit was watching does not count
		}
		LivingEntity attacker = owner.getLastHurtByMob();
		if (stamp != seenHurtStamp) {
			seenHurtStamp = stamp;
			if (attacker != null && attacker != owner && attacker.isAlive() && distanceTo(owner) <= AUTO_DEFEND_RANGE
					&& owner.tickCount - stamp <= 5 && HeroTargets.isHostile(owner, attacker)) {
				lastCombatTick = tickCount;
				if (mode() != DEFENSIVE) {
					prevMode = mode();
					setMode(DEFENSIVE);
					getEntityData().set(AUTO, true);
					rescueArmed = owner.getHealth() > RESCUE_HEALTH;
					target = validTarget(owner, attacker) ? attacker : null;
					if (isOpen() && phase == PHASE_IDLE) {
						setOpen(false);
						IronManSounds.play(this, IronManSounds.CLAMP, 0.8f, 1.1f);
					}
					IronManSounds.play(this, IronManSounds.WEAPON_SELECT, 0.9f, 1.2f);
					owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.auto_defend")
							.withStyle(ChatFormatting.GOLD), true);
				}
			}
		}
		if (prevMode < 0) {
			return;
		}
		if (mode() != DEFENSIVE) {
			endAuto();
			return;
		}
		if (!HeroTargets.hostiles(level, owner, HeroTargets.around(owner.position(), CALM_RADIUS)).isEmpty()) {
			lastCombatTick = tickCount;
		}
		if (tickCount - lastCombatTick >= CALM_TICKS) {
			int back = prevMode;
			endAuto();
			setMode(back);
			target = null;
			IronManSounds.play(this, IronManSounds.WEAPON_SELECT, 0.8f, 0.9f);
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.combat_over",
					modeName(back)).withStyle(ChatFormatting.AQUA), true);
		}
	}

	/**
	 * Start the step-in: the suit is (or opens) open and holds still. v0.15.15 (user): its owner is walked round to the
	 * spot {@link #STEP_BEHIND} behind it, turned to its facing (their body then locked to it), and walks into the open
	 * back over {@link #STEP_TICKS} with their arms a little out -- the open suit's own pose -- so it closes on the right
	 * parts ({@link #beginWrap}). They can't be hurt from here until the suit is on (or the step-in is abandoned).
	 */
	private void beginStepIn(ServerPlayer owner) {
		target = null;
		flying = false;
		setNoGravity(false);
		setDeltaMovement(Vec3.ZERO);
		setOpen(true);
		stepFrom = owner.position();
		stepYawFrom = owner.getYRot();
		stepStage = 0;
		stepStageAt = 0;
		stepLastPos = owner.position();
		stepStill = 0;
		setPhase(PHASE_STEP_IN);
		getEntityData().set(BUSY, true);
		setStepper(owner, STEP_APPROACH);
		STEPPING.add(owner.getUUID());
		IronManSounds.play(this, IronManSounds.SERVO, 0.8f, 0.9f);
	}

	/** The spot behind the open back the owner walks into the suit from. */
	public Vec3 behindSpot() {
		return position().add(forward().scale(-STEP_BEHIND));
	}

	private void tickStepping(ServerLevel level, ServerPlayer owner) {
		if (!canWrap(owner)) {
			abandonStep(owner);
			return;
		}
		walk(Vec3.ZERO);
		Vec3 fwd = forward();
		Vec3 behind = behindSpot();
		if (stepStage == 0) {
			// walk round to the spot behind the suit (past its side if they start in front of it)
			Vec3 to = behind.subtract(owner.position());
			if (to.horizontalDistance() < 0.3 || phaseTicks > STEP_APPROACH_MAX) {
				stepStage = 1;
				stepStageAt = phaseTicks;
				stepFrom = behind;
				// turned to the suit's facing on the spot (a hair's move at most), arms coming out
				owner.teleportTo(level, behind.x, behind.y, behind.z, getYRot(), 10f);
				owner.setDeltaMovement(Vec3.ZERO);
				setStepper(owner, STEP_IN);
				return;
			}
			Vec3 rel = owner.position().subtract(position());
			Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 aim = behind;
			if (rel.dot(fwd) > -0.6 && rel.horizontalDistance() < 3.0) {
				double side = rel.dot(right) >= 0 ? 1 : -1;
				aim = position().add(right.scale(side * 1.2)).add(fwd.scale(-0.7)); // round the side, clear of the suit
			}
			Vec3 d = aim.subtract(owner.position()).multiply(1, 0, 1);
			double len = d.length();
			Vec3 v = len < 1.0e-4 ? Vec3.ZERO : d.scale(Math.min(STEP_WALK_SPEED, len) / len);
			if (owner.position().distanceToSqr(stepLastPos) < 1.0e-4) {
				stepStill++;
			} else {
				stepStill = 0;
			}
			stepLastPos = owner.position();
			if (stepStill >= 3) {
				// not moving under the push (blocked, or a player nothing simulates): carry them along the way
				Vec3 at = owner.position().add(v);
				float yaw = len < 1.0e-4 ? owner.getYRot() : (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f;
				owner.teleportTo(level, at.x, at.y, at.z, yaw, owner.getXRot());
			} else {
				owner.setDeltaMovement(v.x, owner.onGround() ? 0.0 : Math.min(0.0, owner.getDeltaMovement().y), v.z);
				owner.hurtMarked = true;
			}
			return;
		}
		// into the open back, facing the suit's way
		float t0 = Math.min(1f, (phaseTicks - stepStageAt) / (float) STEP_TICKS);
		float t1 = Math.min(1f, (phaseTicks + 1 - stepStageAt) / (float) STEP_TICKS);
		Vec3 at = stepFrom.lerp(position(), smooth(t0));
		Vec3 next = stepFrom.lerp(position(), smooth(t1));
		guide(level, owner, at, next, getYRot());
		if ((phaseTicks - stepStageAt) % 7 == 0 && t0 < 1f) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
					SoundSource.PLAYERS, 0.25f, 0.8f + level.random.nextFloat() * 0.2f);
		}
		if (t0 >= 1f) {
			beginWrap(owner);
		}
	}

	/** The step-in / close-in can't finish (owner gone, died, put something else on): the suit just stays open. */
	private void abandonStep(ServerPlayer owner) {
		if (owner != null) {
			STEPPING.remove(owner.getUUID());
		} else if (ownerId != null) {
			STEPPING.remove(ownerId);
		}
		getEntityData().set(BUSY, false);
		setStepper(null, STEP_NONE);
		setPhase(PHASE_IDLE);
	}

	/** Can the suit close around {@code p} right now? (Tony Stark, no other Iron Man armour on, no suit-up running.) */
	public boolean canWrap(ServerPlayer p) {
		return p != null && p.isAlive() && p.level() == level() && TonyStark.hasPower(p)
				&& !IronManArmor.wearingAnyIronMan(p) && !IronManSuitUpManager.inTransition(p);
	}

	private void beginWrap(ServerPlayer owner) {
		target = null;
		setOpen(true);
		setPhase(PHASE_WRAP);
		getEntityData().set(BUSY, true);
		STEPPING.add(owner.getUUID());
		setStepper(owner, STEP_WRAP);
		flying = false;
		setDeltaMovement(Vec3.ZERO);
		pin(owner);
		IronManSounds.play(this, IronManSounds.SERVO, 0.8f, 1.0f);
	}

	private void pin(ServerPlayer owner) {
		Vec3 here = position();
		if (owner.position().distanceToSqr(here) > 0.0025 || Math.abs(Mth.wrapDegrees(owner.getYRot() - getYRot())) > 1f) {
			owner.teleportTo((ServerLevel) level(), here.x, here.y, here.z, getYRot(), owner.getXRot());
		}
		owner.setDeltaMovement(Vec3.ZERO);
	}

	private void tickWrap(ServerLevel level, ServerPlayer owner) {
		if (!canWrap(owner)) {
			abandonStep(owner); // they left / died / put on something else: it just stays open
			return;
		}
		pin(owner);
		if (phaseTicks == WRAP_CLOSE_TICK) {
			setOpen(false);
			IronManSounds.play(this, IronManSounds.CLAMP, 0.9f, 1.0f);
		}
		if (phaseTicks >= WRAP_DONE_TICK) {
			equipOnto(owner);
		}
	}

	private void tickRescue(ServerLevel level, ServerPlayer owner) {
		if (owner == null || !canWrap(owner) || phaseTicks > RESCUE_TIMEOUT) {
			flying = false;
			setPhase(PHASE_IDLE);
			return;
		}
		// v0.15.15: land just IN FRONT of the owner, facing the way they face, its open back to them -- then they walk
		// straight into it
		Vec3 front = Vec3.directionFromRotation(0f, owner.getYRot()).scale(RESCUE_FRONT);
		Vec3 spot = owner.position().add(front);
		if (!level.noCollision(this, getType().getDimensions().makeBoundingBox(spot))) {
			spot = owner.position();
		}
		Vec3 to = spot.subtract(position());
		double dist = to.length();
		if (dist < 0.9) {
			moveTo(spot.x, spot.y, spot.z, owner.getYRot(), 0f);
			setYHeadRot(owner.getYRot());
			flying = false;
			beginStepIn(owner);
			return;
		}
		if (dist > TELEPORT_RANGE) {
			teleportNear(level, owner);
			return;
		}
		flyTowards(spot.add(0, 0.3, 0), Math.min(1.1, 0.35 + dist * 0.08));
	}

	// ------------------------------------------------------------------ movement

	/** Ground movement: gravity, steps up a block, hops if it walks into something. */
	private void walk(Vec3 want) {
		flying = false;
		setNoGravity(false);
		Vec3 v = getDeltaMovement();
		double hx = Mth.lerp(0.5, v.x, want.x);
		double hz = Mth.lerp(0.5, v.z, want.z);
		double vy = v.y - 0.08;
		if (horizontalCollision && onGround() && want.horizontalDistanceSqr() > 1.0e-4) {
			vy = 0.42;
		}
		setDeltaMovement(hx, vy, hz);
		move(MoverType.SELF, getDeltaMovement());
		Vec3 after = getDeltaMovement();
		setDeltaMovement(after.x * 0.91, after.y * 0.98, after.z * 0.91);
	}

	/** Fly towards {@code dest} (the suit's feet), climbing over whatever is in the way. Thruster fx are client-side. */
	private void flyTowards(Vec3 dest, double speed) {
		if (!flying) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.7f, 1.1f); // take-off
		}
		flying = true;
		setNoGravity(true);
		Vec3 to = dest.subtract(position());
		Vec3 want = to.lengthSqr() < 1.0e-6 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
		if (horizontalCollision) {
			want = new Vec3(want.x * 0.3, Math.max(want.y, 0.45), want.z * 0.3); // climb over what is in the way
		}
		Vec3 vel = getDeltaMovement().lerp(want, 0.3);
		setDeltaMovement(vel);
		move(MoverType.SELF, vel);
		if (vel.horizontalDistanceSqr() > 0.0025) {
			float want2 = (float) (Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) - 90f;
			setYRot(Mth.approachDegrees(getYRot(), want2, 25f));
			setYHeadRot(getYRot());
		}
		if (tickCount % 20 == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.35f, 1.0f);
		}
	}

	/**
	 * Settle down onto the ground at {@code spot} (thrusters still on): eases over it and sinks, turning to face
	 * {@code face}. Returns true once it is standing.
	 */
	private boolean settle(Vec3 spot, Vec3 face) {
		flying = true;
		setNoGravity(true);
		Vec3 to = spot.subtract(position());
		Vec3 vel = new Vec3(to.x * 0.3, Mth.clamp(to.y * 0.3 - 0.06, -0.4, 0.2), to.z * 0.3);
		setDeltaMovement(vel);
		move(MoverType.SELF, vel);
		if (face != null) {
			faceTowards(face);
		}
		if (onGround() || verticalCollisionBelow || (Math.abs(to.y) < 0.08 && to.horizontalDistance() < 0.2)) {
			flying = false;
			setNoGravity(false);
			setDeltaMovement(Vec3.ZERO);
			if (level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.05, getZ(), 8, 0.3, 0.02, 0.3, 0.02);
			}
			IronManSounds.play(this, IronManSounds.CLAMP, 0.6f, 0.8f);
			return true;
		}
		return false;
	}

	/** v0.15.15: walking towards the owner, the ground ahead drops away (a ravine, a cliff, open water): fly instead. */
	private boolean gapAhead(ServerLevel level, Vec3 dir) {
		if (!onGround() || dir.lengthSqr() < 1.0e-4) {
			return false;
		}
		Vec3 n = dir.normalize();
		BlockPos p = BlockPos.containing(getX() + n.x * 1.1, getY() - 0.2, getZ() + n.z * 1.1);
		for (int i = 0; i < 4; i++) {
			BlockPos q = p.below(i);
			if (!level.getFluidState(q).isEmpty()) {
				return true; // water / lava: hop over it
			}
			if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Follow / Defensive movement. v0.15.15 (user): walks with a real walk cycle; takes off (Iron Man flight pose,
	 * thrusters) when the owner is far ahead, well above / below it, across a gap in the ground, or it is stuck -- and
	 * lands near them to walk again. Past {@link #TELEPORT_RANGE} it still teleports beside them.
	 */
	private void follow(ServerLevel level, ServerPlayer owner) {
		double dist = distanceTo(owner);
		if (dist > TELEPORT_RANGE) {
			teleportNear(level, owner);
			return;
		}
		Vec3 flat = owner.position().subtract(position()).multiply(1, 0, 1);
		boolean high = Math.abs(owner.getY() - getY()) > 3.5;
		boolean far = flat.length() > FLY_RANGE;
		if (!flying && (far || high || stuckTicks > STUCK_TICKS
				|| (walking && flat.length() > FOLLOW_STOP + 0.5 && gapAhead(level, flat)))) {
			flying = true;
			stuckTicks = 0;
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.8f, 1.1f);
			setDeltaMovement(getDeltaMovement().add(0, 0.35, 0)); // lift off
		}
		if (flying) {
			Vec3 spot = followLanding(level, owner, flat); // ground near them (not thin air off a ledge)
			double hd = spot.subtract(position()).horizontalDistance();
			boolean ownerAirborne = !owner.onGround() && !owner.isInWater();
			if (hd < 1.0 && !ownerAirborne && Math.abs(spot.y - getY()) < 3.0) {
				if (settle(spot, owner.position())) {
					walking = false;
					stuckTicks = 0;
				}
				return;
			}
			// cruise a little above the line to them, coming down on the last stretch
			double over = Math.min(2.0, 0.6 + hd * 0.12);
			flyTowards(spot.add(0, over, 0), Math.min(1.2, 0.35 + dist * 0.07));
			return;
		}
		if (dist > FOLLOW_START || (walking && dist > FOLLOW_STOP)) {
			walking = true;
			Vec3 dir = owner.position().subtract(position()).multiply(1, 0, 1);
			Vec3 want = dir.lengthSqr() < 1.0e-4 ? Vec3.ZERO : dir.normalize().scale(WALK_SPEED);
			Vec3 before = position();
			walk(want);
			if (target == null) {
				faceTowards(position().add(want));
			}
			if (position().distanceToSqr(before) < 0.0016) {
				stuckTicks++;
			} else {
				stuckTicks = 0;
			}
		} else {
			walking = false;
			stuckTicks = 0;
			walk(Vec3.ZERO);
			if (target == null) {
				faceTowards(owner.position());
			}
		}
	}

	private void teleportNear(ServerLevel level, ServerPlayer owner) {
		Vec3 back = Vec3.directionFromRotation(0f, owner.getYRot()).scale(-2.0);
		Vec3[] tries = { owner.position().add(back), owner.position().add(back.z, 0, -back.x),
				owner.position().add(-back.z, 0, back.x), owner.position() };
		Vec3 at = owner.position();
		for (Vec3 c : tries) {
			if (level.noCollision(this, getType().getDimensions().makeBoundingBox(c))) {
				at = c;
				break;
			}
		}
		clearLight();
		moveTo(at.x, at.y, at.z, owner.getYRot(), 0f);
		setDeltaMovement(Vec3.ZERO);
		flying = false;
		stuckTicks = 0;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 10, 0.3, 0.6, 0.3, 0.05);
	}

	// ------------------------------------------------------------------ v0.15.15: a called Mark 8 flying in

	/** Where a called suit lands: {@link #ARRIVE_FRONT} in front of the owner (else nearer / beside them), on the ground. */
	public Vec3 landingSpot(ServerLevel level, ServerPlayer owner) {
		Vec3 fwd = Vec3.directionFromRotation(0f, owner.getYRot());
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3[] tries = { fwd.scale(ARRIVE_FRONT), fwd.scale(1.4), fwd.scale(1.4).add(right.scale(1.2)),
				fwd.scale(1.4).add(right.scale(-1.2)), right.scale(1.6), right.scale(-1.6) };
		Vec3 found = groundNear(level, owner, tries);
		return found != null ? found : owner.position().add(fwd.scale(ARRIVE_FRONT));
	}

	/** v0.15.15 Follow: where to come down near the owner -- standing ground at their level, on this side of them. */
	private Vec3 followLanding(ServerLevel level, ServerPlayer owner, Vec3 flat) {
		Vec3 back = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, owner.getYRot()).scale(-1) : flat.normalize().scale(-1);
		Vec3 right = new Vec3(-back.z, 0, back.x);
		Vec3[] tries = { back.scale(FOLLOW_STOP), back.scale(1.5), right.scale(1.5), right.scale(-1.5),
				back.scale(1.0).add(right.scale(1.0)), back.scale(1.0).add(right.scale(-1.0)), back.scale(0.8) };
		Vec3 found = groundNear(level, owner, tries);
		return found != null ? found : owner.position().add(back.scale(FOLLOW_STOP));
	}

	/** The first of {@code offsets} (from the owner) with standing ground within a block up / three down of their feet. */
	private Vec3 groundNear(ServerLevel level, ServerPlayer owner, Vec3[] tries) {
		var dims = getType().getDimensions();
		for (Vec3 off : tries) {
			Vec3 c = owner.position().add(off);
			// find the ground under it (a step up or a short drop)
			for (int dy = 1; dy >= -3; dy--) {
				Vec3 at = new Vec3(c.x, Math.floor(owner.getY()) + dy, c.z);
				BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
				if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()
						&& level.noCollision(this, dims.makeBoundingBox(at))) {
					double top = below.getY() + level.getBlockState(below).getCollisionShape(level, below).max(net.minecraft.core.Direction.Axis.Y);
					Vec3 on = new Vec3(at.x, top, at.z);
					if (level.noCollision(this, dims.makeBoundingBox(on))) {
						return on;
					}
				}
			}
		}
		return null;
	}

	/**
	 * v0.15.15: the called suit's flight in -- cruise towards the landing spot in front of the owner (above the line to
	 * it, climbing over anything in the way), then sink onto it facing them, closed, Regular. Owner gone: it just comes
	 * down where it is. Stuck or too long: set down on the spot.
	 */
	private void tickArrive(ServerLevel level, ServerPlayer owner) {
		if (owner == null) {
			if (settle(position().add(0, -1.0, 0), null) || phaseTicks > ARRIVE_TIMEOUT) {
				land(null);
			}
			return;
		}
		Vec3 spot = landingSpot(level, owner);
		double hd = spot.subtract(position()).horizontalDistance();
		if (arriveBest == null || hd < arriveBest.x - 0.05) {
			arriveBest = new Vec3(hd, 0, 0);
			arriveStuck = 0;
		} else {
			arriveStuck++;
		}
		if (phaseTicks > ARRIVE_TIMEOUT || arriveStuck > 80) {
			clearLight();
			moveTo(spot.x, spot.y, spot.z, getYRot(), 0f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, spot.x, spot.y + 1.0, spot.z, 10, 0.3, 0.6, 0.3, 0.05);
			land(owner);
			return;
		}
		if (hd > 0.5) {
			double over = Math.min(3.0, 0.4 + hd * 0.15);
			flyTowards(spot.add(0, over, 0), Math.min(1.3, 0.3 + hd * 0.1));
			return;
		}
		if (settle(spot, owner.position())) {
			land(owner);
		}
	}

	/** Down and standing, closed, in Regular mode, facing its owner. */
	private void land(ServerPlayer owner) {
		flying = false;
		setNoGravity(false);
		setDeltaMovement(Vec3.ZERO);
		if (owner != null) {
			double dx = owner.getX() - getX();
			double dz = owner.getZ() - getZ();
			if (dx * dx + dz * dz > 1.0e-4) {
				float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
				setYRot(yaw);
				setYHeadRot(yaw);
			}
			owner.displayClientMessage(Component.translatable("message.projecthero.ironman.mark8.landed")
					.withStyle(ChatFormatting.AQUA), true);
		}
		setMode(REGULAR);
		setOpen(false);
		arriveBest = null;
		arriveStuck = 0;
		setPhase(PHASE_IDLE);
		IronManSounds.play(this, IronManSounds.POD_LAND, 0.5f, 1.3f);
	}

	// ------------------------------------------------------------------ v0.15.15: send-home

	/**
	 * Fly home to {@link #homeDock}, the part couriers' rules: lift off, streak to the platform and rack the four
	 * pieces. A dock that isn't somewhere entities tick (unloaded, or gone) -- or a flight path into such terrain --
	 * and it climbs out of sight and hands the stacks to the platform at once ({@link
	 * com.projecthero.mod.ironman.suit.IronManPlatformReturn#depositNow}) or the return queue. Never lost.
	 */
	private void tickHome(ServerLevel level, ServerPlayer owner) {
		if (homeDock == null) {
			setPhase(PHASE_IDLE);
			return;
		}
		if (phaseTicks < HOME_HOLD_TICKS) {
			walk(Vec3.ZERO);
			return;
		}
		int flight = phaseTicks - HOME_HOLD_TICKS;
		if (flight == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.9f, 1.15f);
		}
		flying = true;
		setNoGravity(true);
		noPhysics = true;
		if (flight > HOME_MAX_FLIGHT) {
			queueHome(level);
			return;
		}
		boolean reachable = !homeSkyward && level.isLoaded(homeDock)
				&& com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, homeDock)
				&& level.getBlockEntity(homeDock) instanceof com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
		if (!reachable) {
			homeSkyward = true;
		}
		homeSpeed = Math.min(1.6, homeSpeed + 0.06);
		Vec3 dir;
		if (homeSkyward) {
			dir = new Vec3(0, 1, 0);
			if (flight * 1.2 > HOME_SKY_CLIMB) {
				queueHome(level);
				return;
			}
		} else {
			Vec3 to = Vec3.atBottomCenterOf(homeDock.above());
			Vec3 d = to.subtract(position());
			double dist = d.length();
			if (dist <= Math.max(0.35, homeSpeed)) {
				setPos(to.x, to.y, to.z);
				arriveHome(level);
				return;
			}
			dir = d.normalize();
			if (flight < 14 && dist > 4.0) {
				dir = dir.add(0, 1.2 - flight / 12.0, 0).normalize(); // lift off first, then streak home
			}
			homeSpeed = Math.min(homeSpeed, Math.max(0.25, dist * 0.3)); // ease into the dock
		}
		Vec3 next = position().add(dir.scale(homeSpeed));
		if (!com.projecthero.mod.ironman.suit.IronManChunkTickets.entityTicking(level, next)) {
			queueHome(level); // flying into unloaded terrain: hand over instead of freezing there
			return;
		}
		if (dir.horizontalDistanceSqr() > 1.0e-4) {
			float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90f;
			setYRot(Mth.approachDegrees(getYRot(), yaw, 30f));
			setYHeadRot(getYRot());
		}
		setDeltaMovement(next.subtract(position()));
		setPos(next.x, next.y, next.z);
	}

	/** The four stacks for the platform (stamped with the charge, their home stamp dropped). */
	private List<ItemStack> homeStacks() {
		List<ItemStack> out = new java.util.ArrayList<>();
		for (ItemStack s : takeAll()) {
			if (!s.isEmpty()) {
				com.projecthero.mod.ironman.suit.IronManPlatformReturn.clearHome(s);
				out.add(s);
			}
		}
		return out;
	}

	private void arriveHome(ServerLevel level) {
		List<ItemStack> stacks = homeStacks();
		if (level.getBlockEntity(homeDock) instanceof com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity be
				&& (be.owner().isEmpty() || ownerId == null || be.owner().get().equals(ownerId))) {
			if (be.owner().isEmpty() && ownerId != null) {
				be.bindTo(ownerId);
			}
			List<ItemStack> left = new java.util.ArrayList<>();
			for (ItemStack s : stacks) {
				if (!be.store(s) && !s.isEmpty()) {
					left.add(s);
				}
			}
			Vec3 c = Vec3.atBottomCenterOf(homeDock.above());
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.8, c.z, 10, 0.3, 0.5, 0.3, 0.05);
			IronManSounds.play(this, IronManSounds.CLAMP, 0.9f, 0.8f);
			homeFallback(level, left);
			return;
		}
		homeFallback(level, stacks);
	}

	private void queueHome(ServerLevel level) {
		List<ItemStack> stacks = homeStacks();
		if (ownerId != null && !stacks.isEmpty()) {
			net.minecraft.core.GlobalPos gp = net.minecraft.core.GlobalPos.of(level.dimension(), homeDock);
			if (com.projecthero.mod.ironman.suit.IronManPlatformReturn.depositNow(level.getServer(), ownerId, gp, stacks)) {
				finish();
				return;
			}
			int mask = 0;
			for (ItemStack s : stacks) {
				if (s.getItem() instanceof ArmorItem a) {
					mask |= switch (a.getType()) {
						case HELMET -> 1;
						case CHESTPLATE -> 2;
						case LEGGINGS -> 4;
						default -> 8;
					};
				}
			}
			com.projecthero.mod.ironman.data.StarkSuitReturnQueue.get(level).enqueue(ownerId, gp, suitId(), mask,
					energy, integrity, stacks);
			finish();
			return;
		}
		homeFallback(level, stacks);
	}

	/** Last resort: back to the owner, or dropped where it is -- never destroyed. */
	private void homeFallback(ServerLevel level, List<ItemStack> stacks) {
		ServerPlayer owner = owner(level);
		for (ItemStack s : stacks) {
			if (s.isEmpty()) {
				continue;
			}
			if (owner != null) {
				IronManSuitUpManager.giveBack(owner, s);
			} else {
				spawnAtLocation(s, 0.5f);
			}
		}
		finish();
	}

	private void faceTowards(Vec3 p) {
		double dx = p.x - getX();
		double dz = p.z - getZ();
		if (dx * dx + dz * dz < 1.0e-4) {
			return;
		}
		float want = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
		setYRot(Mth.approachDegrees(getYRot(), want, 20f));
		setYHeadRot(getYRot());
	}

	@Override
	public float maxUpStep() {
		return 1.0f;
	}

	// ------------------------------------------------------------------ defending

	/** Whoever hurt the owner most recently (still a threat, in range), else the threat closest to the owner. */
	private LivingEntity pickTarget(ServerLevel level, ServerPlayer owner) {
		LivingEntity last = owner.getLastHurtByMob();
		if (last != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < HeroTargets.RECENT_ATTACK_TICKS
				&& validTarget(owner, last)) {
			return last;
		}
		List<LivingEntity> near = HeroTargets.hostiles(level, owner, HeroTargets.around(owner.position(), DEFEND_RADIUS));
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity e : near) {
			if (!validTarget(owner, e)) {
				continue;
			}
			double d = e.distanceToSqr(owner);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	private boolean validTarget(ServerPlayer owner, LivingEntity e) {
		return e.isAlive() && e != owner && e.level() == level() && distanceTo(e) <= ENGAGE_RANGE
				&& HeroTargets.isHostile(owner, e);
	}

	private void engage(ServerLevel level, ServerPlayer owner, LivingEntity t) {
		faceTowards(t.position());
		if (fireCooldown > 0 || energy <= 0f) {
			return;
		}
		if (distanceTo(t) <= MELEE_RANGE) {
			punch(level, owner, t);
		} else if (canSee(level, t)) {
			shoot(level, owner, t);
		}
	}

	private boolean canSee(ServerLevel level, LivingEntity t) {
		Vec3 from = getEyePosition();
		Vec3 to = t.getEyePosition();
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
				.getType() == HitResult.Type.MISS;
	}

	/** Pay {@code cost} (the last shot may take the suit to exactly zero). */
	private void pay(float cost) {
		setEnergy(energy - Math.min(cost, energy));
	}

	private void shoot(ServerLevel level, ServerPlayer owner, LivingEntity t) {
		IronManSuit suit = suit();
		float cost = suit == null ? 10f : suit.repulsorTapEnergy() * suit.energyCostMultiplier();
		pay(cost);
		fireCooldown = Math.max(MIN_SHOT_COOLDOWN, suit == null ? 20 : suit.repulsorTapCooldownTicks());
		float damage = suit == null ? 10f : suit.repulsorDamage();

		Vec3 end = t.position().add(0, t.getBbHeight() * 0.5, 0);
		Vec3 eye = getEyePosition();
		Vec3 look = end.subtract(eye).normalize();
		Vec3 right = Vec3.directionFromRotation(0f, getYRot() + 90f);
		Vec3 origin = eye.add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.35, 0);
		IronManBeamPayload beam = new IronManBeamPayload(origin, end, 0);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(this) < 128 * 128 && ServerPlayNetworking.canSend(viewer, IronManBeamPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, beam);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.3, 0.3, 0.3, 0.05);
		IronManSounds.play(this, IronManSounds.REPULSOR_BLAST, 0.9f, 1.05f);
		IronManSounds.playAt(level, end.x, end.y, end.z, IronManSounds.ENERGY_IMPACT, 0.5f, 1.0f);
		if (AbilityHelpers.hurt(owner, t, damage)) {
			AbilityHelpers.knockbackFrom(t, position(), 1.1);
		}
		shotsFired++;
		getEntityData().set(SHOTS, getEntityData().get(SHOTS) + 1);
		firePose = FIRE_POSE_TICKS;
		getEntityData().set(FIRING, true);
	}

	private void punch(ServerLevel level, ServerPlayer owner, LivingEntity t) {
		IronManSuit suit = suit();
		pay(PUNCH_ENERGY * (suit == null ? 1f : suit.energyCostMultiplier()));
		fireCooldown = PUNCH_COOLDOWN;
		float damage = 6.0f + (suit == null ? 0f : suit.strengthBonus());
		if (AbilityHelpers.hurt(owner, t, damage)) {
			AbilityHelpers.knockbackFrom(t, position(), 0.8);
		}
		IronManSounds.play(this, IronManSounds.PUNCH, 0.9f, 1.0f);
		level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
		punches++;
		getEntityData().set(SHOTS, getEntityData().get(SHOTS) + 1);
		firePose = FIRE_POSE_TICKS;
		getEntityData().set(FIRING, true);
	}

	// ------------------------------------------------------------------ light

	private void updateLight(ServerLevel level) {
		BlockPos want = BlockPos.containing(getX(), getY() + 1.2, getZ());
		if (want.equals(lightPos)) {
			return;
		}
		clearLight();
		for (BlockPos p : new BlockPos[] { want, want.above(), want.below() }) {
			if (level.isLoaded(p) && level.getBlockState(p).isAir()) {
				level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
				lightPos = p;
				return;
			}
		}
	}

	/** Remove the light block this suit placed (only if it is still our light block). */
	public void clearLight() {
		if (lightPos == null || !(level() instanceof ServerLevel level)) {
			return;
		}
		if (level.isLoaded(lightPos)) {
			BlockState st = level.getBlockState(lightPos);
			if (st.is(Blocks.LIGHT)) {
				level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		lightPos = null;
	}

	// ------------------------------------------------------------------ damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isRemoved() || linger > 0 || isInvulnerableTo(source) || amount <= 0f) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker != null && attacker.getUUID().equals(ownerId)) {
			return false; // never your own suit
		}
		// the worn rules: bulletproof, arrows / fire do nothing to a powered arrow-fire-immune mark,
		// otherwise INTEGRITY_PER_DAMAGE of the hit (fire at FIRE_INTEGRITY_MULTIPLIER of that)
		if (com.projecthero.mod.firearm.Gunfire.active()) {
			return false;
		}
		IronManSuit suit = suit();
		if (suit != null && suit.arrowFireImmune() && energy > 0f && IronManDamage.isArrowOrFire(source)) {
			return false;
		}
		float before = integrity;
		// v0.15.9 (user): integrity takes 100% of the damage dealt, 1:1, and never regenerates
		float bleed = amount;
		setIntegrity(integrity - bleed);
		ServerLevel level = (ServerLevel) level();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 8, 0.3, 0.5, 0.3, 0.08);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 0.25f, 1.8f);
		if (before > 0f && integrity <= 0f) {
			// powered down where it stands -- never destroyed, still equippable
			level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 20, 0.4, 0.6, 0.4, 0.03);
			IronManSounds.play(this, IronManSounds.POWER_FAIL, 1.0f, 1.0f);
			target = null;
			clearLight();
			ServerPlayer owner = owner(level);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.integrity_failed")
						.withStyle(ChatFormatting.RED), true);
			}
		}
		return true;
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isAttackable() {
		return true;
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
	public boolean shouldBeSaved() {
		return !isRemoved() && linger == 0 && pieceCount() > 0;
	}

	// ------------------------------------------------------------------ the only ways the suit leaves the sentry

	private ItemStack[] takeAll() {
		return takeAll(false);
	}

	/** {@code keepLook}: the synced copies stay, so the (now empty) suit still draws for its last few ticks. */
	private ItemStack[] takeAll(boolean keepLook) {
		ItemStack[] out = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			out[i] = stacks[i];
			if (!out[i].isEmpty()) {
				IronManEnergy.stampStack(out[i], energy, integrity);
			}
			if (keepLook) {
				stacks[i] = ItemStack.EMPTY;
			} else {
				setStack(i, ItemStack.EMPTY);
			}
		}
		return out;
	}

	/**
	 * Close around {@code owner}: the four pieces go straight into their armour slots (whatever other armour was there goes
	 * to the pack), the suit's charge and integrity carry over, and the sentry is gone. The current mode is remembered on
	 * the chestplate for the next deploy.
	 */
	public void equipOnto(ServerPlayer owner) {
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		String sid = suitId();
		float e = energy;
		float i = integrity;
		int mode = prevMode >= 0 ? prevMode : mode(); // v0.15.15: an auto-defend remembers the mode it came from
		STEPPING.remove(owner.getUUID());
		setStepper(null, STEP_NONE);
		ItemStack[] pieces = takeAll(true);
		if (!pieces[1].isEmpty()) {
			CustomData.update(DataComponents.CUSTOM_DATA, pieces[1], tag -> tag.putInt(MODE_TAG, mode));
		}
		for (ItemStack piece : pieces) {
			if (!piece.isEmpty() && !IronManSuitUpManager.receivePart(owner, piece, false) && !piece.isEmpty()) {
				IronManSuitUpManager.giveBack(owner, piece);
			}
		}
		IronManEnergy.setEnergy(owner, sid, e);
		IronManEnergy.setIntegrity(owner, sid, i);
		owner.inventoryMenu.broadcastChanges();
		IronManSounds.play(owner, IronManSounds.POWER_UP, 0.9f, 1.0f);
		((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.0, getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		// the suit stays drawn, shut around its owner, for a few ticks while the worn pieces come into view (a freshly
		// equipped piece is hidden for ~3 ticks on the client), then it is gone; the real stacks are already on them
		clearLight();
		STEPPING.remove(owner.getUUID());
		linger = LINGER_TICKS;
		getEntityData().set(BUSY, true);
	}

	/** Fall inert as the four pieces right here (stamped). Only {@code /kill} does this. */
	public void dropPieces() {
		if (level().isClientSide() || isRemoved()) {
			return;
		}
		for (ItemStack s : takeAll()) {
			if (!s.isEmpty()) {
				spawnAtLocation(s, 0.5f);
			}
		}
		finish();
	}

	private void finish() {
		clearLight();
		discard();
	}

	@Override
	protected void onBelowWorld() {
		if (level().isClientSide()) {
			super.onBelowWorld();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		ServerPlayer owner = owner(level);
		if (owner != null) {
			for (ItemStack s : takeAll()) {
				if (!s.isEmpty()) {
					IronManSuitUpManager.giveBack(owner, s);
				}
			}
			owner.inventoryMenu.broadcastChanges();
			finish();
			return;
		}
		// nobody to give it to: stand it back up at the world spawn rather than lose it
		clearLight();
		BlockPos spawn = level.getSharedSpawnPos();
		BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn);
		moveTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5, getYRot(), 0f);
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void kill() {
		if (!level().isClientSide() && !isRemoved()) {
			dropPieces();
			return;
		}
		super.kill();
	}

	@Override
	public void remove(RemovalReason reason) {
		if (!level().isClientSide() && reason.shouldDestroy()) {
			clearLight();
		}
		if (!level().isClientSide() && ownerId != null && (phase == PHASE_STEP_IN || phase == PHASE_WRAP)) {
			STEPPING.remove(ownerId);
		}
		super.remove(reason);
	}

	// ------------------------------------------------------------------ client

	/**
	 * v0.15.15: a plain {@link Entity}'s {@code lerpTo} is a hard {@code setPos}, and the client sets the old position to
	 * the new one before ticking -- so the suit hopped tick to tick and the client never saw it move (no walk cycle).
	 * Store the target and glide to it over the packet's steps instead, like a mob.
	 */
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		if (!level().isClientSide()) {
			super.lerpTo(x, y, z, yRot, xRot, steps);
			return;
		}
		if (tickCount < 2 || distanceToSqr(x, y, z) > 64.0) {
			super.lerpTo(x, y, z, yRot, xRot, steps); // first sight or a teleport: snap
			lerpSteps = 0;
			return;
		}
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYRot = yRot;
		lerpSteps = Math.max(1, steps);
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

	@Override
	public float lerpTargetYRot() {
		return lerpSteps > 0 ? lerpYRot : getYRot();
	}

	private void clientLerp() {
		if (lerpSteps <= 0) {
			return;
		}
		double k = 1.0 / lerpSteps;
		double nx = getX() + (lerpX - getX()) * k;
		double ny = getY() + (lerpY - getY()) * k;
		double nz = getZ() + (lerpZ - getZ()) * k;
		setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) * (float) k);
		setPos(nx, ny, nz);
		lerpSteps--;
	}

	private void clientTick() {
		clientLerp();
		openAnimO = openAnim;
		float want = isOpen() ? 1f : 0f;
		float step = 1f / OPEN_ANIM_TICKS;
		openAnim = openAnim < want ? Math.min(want, openAnim + step) : Math.max(want, openAnim - step);
		walkAmtO = walkAmt;
		double dx = getX() - xo;
		double dz = getZ() - zo;
		float hspeed = (float) Math.sqrt(dx * dx + dz * dz);
		float speed = flying() ? 0f : hspeed;
		// v0.15.15: a proper walk cycle -- amount from the speed (a full stride at walking pace), the legs' phase advanced
		// by the distance covered, like vanilla's limb swing
		walkAmt += (Math.min(1f, speed * 5f) - walkAmt) * 0.5f;
		walkPos += speed * 3.2f;
		if (flying()) {
			thrusterFx(hspeed);
		}
		animate(hspeed);
	}

	/** v0.15.15: boot + palm thrusters, following the flight lean (client). */
	private void thrusterFx(float hspeed) {
		float lean = (float) Math.toRadians(pose[P_LEAN]);
		Vec3 fwd = forward();
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		// the body's "down" once leant forward about the hips (pivot 1 block up)
		Vec3 down = new Vec3(0, -Mth.cos(lean), 0).subtract(fwd.scale(Mth.sin(lean)));
		Vec3 pivot = position().add(0, 1.0, 0);
		for (int side = -1; side <= 1; side += 2) {
			// boots (1 block below the pivot) and palms (~0.3 below it, out at the sides)
			Vec3 boot = pivot.add(down.scale(1.0)).add(right.scale(side * 0.12));
			Vec3 palm = pivot.add(down.scale(0.3)).add(right.scale(side * 0.4));
			Vec3 jet = down.scale(0.12);
			// v0.15.15 follow-up: short-lived jets (they used to drift down and sit on the grass as little fires); right
			// above the ground they splay out sideways instead of piling up on it
			boolean low = onGround() || boot.y - Math.floor(boot.y) < 0.35 && !level().getBlockState(
					BlockPos.containing(boot.x, boot.y - 0.5, boot.z)).isAir();
			if (low) {
				jet = new Vec3(jet.x + (random.nextDouble() - 0.5) * 0.12, 0.01, jet.z + (random.nextDouble() - 0.5) * 0.12);
			}
			int life = low ? 3 : 5;
			if (random.nextFloat() < 0.7f) {
				thruster(ParticleTypes.FLAME, boot, jet, life);
			}
			if (random.nextFloat() < 0.35f) {
				thruster(ParticleTypes.SMALL_FLAME, palm, jet.scale(0.6), life - 1);
			}
			if (random.nextFloat() < 0.1f) {
				thruster(ParticleTypes.SMOKE, boot, jet.scale(0.5), life + 3);
			}
		}
	}

	/** v0.15.15: client particle hook that can set a particle's lifetime (set by the client; null on a server). */
	public interface ThrusterSink {
		void spawn(net.minecraft.core.particles.ParticleOptions type, Vec3 at, Vec3 velocity, int lifetimeTicks);
	}

	public static ThrusterSink thrusterSink;

	private void thruster(net.minecraft.core.particles.ParticleOptions type, Vec3 at, Vec3 v, int life) {
		if (thrusterSink != null) {
			thrusterSink.spawn(type, at, v, life);
		} else {
			level().addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/**
	 * Client: ease every pose channel towards this state's target -- powered-down slump, open (arms out of the way),
	 * hover-flight, walk cycle, Regular's slow look-around, Defensive's combat stance / scanning sweep / aimed palm with
	 * recoil, and the mode-switch flourish (arms up, eyes flash). Nothing snaps: every channel moves 30% of the way a tick.
	 */
	private void animate(float hspeed) {
		System.arraycopy(pose, 0, poseO, 0, POSE_CHANNELS);
		float[] w = poseWant;
		java.util.Arrays.fill(w, 0f);
		int t = tickCount;
		int mode = mode();
		if (mode != lastMode) {
			if (lastMode >= 0) {
				flourish = FLOURISH_TICKS;
			}
			lastMode = mode;
		}
		int shots = shotCounter();
		if (shots != lastShots) {
			if (lastShots >= 0) {
				recoil = 1f;
			}
			lastShots = shots;
		}
		recoil *= 0.72f;
		float breath = Mth.sin(t * 0.09f);
		if (!powered()) {
			// slumped: head down, arms hanging forward, a forward stoop
			w[P_HEAD_X] = 30f;
			w[P_RARM_X] = 16f;
			w[P_LARM_X] = 16f;
			w[P_RARM_Z] = 3f;
			w[P_LARM_Z] = -3f;
			w[P_LEAN] = 8f;
			w[P_BOB] = -0.04f;
			flourish = 0;
		} else if (isOpen() || busy()) {
			// open back: arms eased out to the sides so the shell can part, head level
			w[P_RARM_Z] = 14f;
			w[P_LARM_Z] = -14f;
			w[P_RARM_X] = -6f + breath;
			w[P_LARM_X] = -6f + breath;
		} else if (flying()) {
			// v0.15.15: the Iron Man flight pose. Hovering / climbing: upright, arms down and out with the palms pushing
			// at the ground, a knee bent. Cruising: leant far forward into the flight, arms straight back along the
			// body (palm repulsors trailing), legs together and straight -- blended by the horizontal speed.
			float cruise = Mth.clamp((hspeed - 0.08f) / 0.5f, 0f, 1f);
			float hover = 1f - cruise;
			float wob = Mth.sin(t * 0.25f) * 1.5f * hover;
			w[P_RARM_X] = hover * (10f + wob) + cruise * 12f;
			w[P_LARM_X] = hover * (10f - wob) + cruise * 12f;
			w[P_RARM_Z] = hover * 24f + cruise * 9f;
			w[P_LARM_Z] = -hover * 24f - cruise * 9f;
			w[P_RLEG_X] = hover * -12f + cruise * 4f;
			w[P_LLEG_X] = hover * 6f + cruise * 4f;
			w[P_RLEG_Z] = hover * 3f;
			w[P_LLEG_Z] = -hover * 3f;
			w[P_HEAD_X] = -cruise * 55f; // head up, looking where it is going
			w[P_LEAN] = cruise * 72f;
			w[P_BOB] = Mth.sin(t * 0.2f) * 0.03f * hover;
		} else {
			float sw = Mth.sin(walkPos) * walkAmt;
			w[P_RARM_X] = -sw * 38f + breath * 1.5f;
			w[P_LARM_X] = sw * 38f + breath * 1.5f;
			w[P_RARM_Z] = 3f + breath * 0.8f;
			w[P_LARM_Z] = -3f - breath * 0.8f;
			w[P_RLEG_X] = sw * 42f;
			w[P_LLEG_X] = -sw * 42f;
			w[P_LEAN] = walkAmt * 5f;
			w[P_BOB] = breath * 0.006f + Math.abs(Mth.cos(walkPos)) * walkAmt * 0.05f;
			if (mode == REGULAR) {
				w[P_HEAD_Y] = Mth.sin(t * 0.02f) * 40f; // slow look-around
				w[P_HEAD_X] = 4f + Mth.sin(t * 0.013f) * 4f;
			} else if (mode == FOLLOW) {
				w[P_HEAD_Y] = Mth.sin(t * 0.03f) * 15f * (1f - walkAmt);
			} else {
				Entity tg = targetId() >= 0 ? level().getEntity(targetId()) : null;
				float still = 1f - walkAmt;
				w[P_RLEG_Z] = 5f * still; // feet apart: combat stance
				w[P_LLEG_Z] = -5f * still;
				if (tg != null) {
					Vec3 from = position().add(0, 1.4, 0);
					Vec3 to = tg.position().add(0, tg.getBbHeight() * 0.5, 0).subtract(from);
					float yawTo = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90f;
					float rel = Mth.clamp(Mth.wrapDegrees(yawTo - getYRot()), -70f, 70f);
					float pitch = Mth.clamp((float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * (180.0 / Math.PI)), -45f, 45f);
					w[P_HEAD_Y] = rel;
					w[P_HEAD_X] = pitch;
					w[P_RARM_X] = -90f + pitch - recoil * 24f; // palm aimed at the target, kicking up on each shot
					w[P_RARM_Y] = Mth.clamp(rel, -40f, 40f);
					w[P_RARM_Z] = 0f;
					w[P_LARM_X] = -32f + breath; // guard
					w[P_LARM_Y] = 12f;
					w[P_LARM_Z] = -10f;
					w[P_LEAN] = 4f - recoil * 3f;
				} else {
					w[P_HEAD_Y] = Mth.sin(t * 0.06f) * 65f; // scanning sweep
					w[P_HEAD_X] = 3f;
					w[P_RARM_X] += -22f;
					w[P_LARM_X] += -22f;
					w[P_RARM_Z] = 12f;
					w[P_LARM_Z] = -12f;
				}
			}
		}
		if (flourish > 0) {
			float env = Mth.sin((float) Math.PI * (FLOURISH_TICKS - flourish) / FLOURISH_TICKS);
			w[P_RARM_X] -= 50f * env;
			w[P_LARM_X] -= 50f * env;
			w[P_RARM_Z] += 22f * env;
			w[P_LARM_Z] -= 22f * env;
			w[P_HEAD_X] -= 12f * env;
			w[P_FLASH] = env;
			flourish--;
		}
		if (!posed) {
			posed = true;
			System.arraycopy(w, 0, pose, 0, POSE_CHANNELS); // first tick: start in pose, no swoop from zero
			System.arraycopy(w, 0, poseO, 0, POSE_CHANNELS);
		}
		// v0.15.15: the walk cycle's limbs follow almost directly (a 30% ease flattened the swing to a shuffle)
		boolean striding = walkAmt > 0.05f && !flying() && !isOpen() && !busy() && powered();
		for (int i = 0; i < POSE_CHANNELS; i++) {
			boolean limb = i == P_RARM_X || i == P_LARM_X || i == P_RLEG_X || i == P_LLEG_X || i == P_BOB;
			float k = striding && limb ? 0.75f : 0.3f;
			pose[i] = i == P_FLASH ? w[i] : pose[i] + (w[i] - pose[i]) * k;
		}
	}

	/** Client: pose channel {@code i} at {@code partialTick}. */
	public float pose(int i, float partialTick) {
		return Mth.lerp(partialTick, poseO[i], pose[i]);
	}

	/** Client: the back's opening, 0 (shut) .. 1 (open), eased. */
	public float openProgress(float partialTick) {
		float t = Mth.lerp(partialTick, openAnimO, openAnim);
		return t * t * (3f - 2f * t);
	}

	// ------------------------------------------------------------------ save / load

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.hasUUID("Owner")) {
			ownerId = tag.getUUID("Owner");
			getEntityData().set(OWNER, Optional.of(ownerId));
		}
		suitId = tag.getString("Suit");
		for (int i = 0; i < 4; i++) {
			String key = "Piece" + i;
			setStack(i, tag.contains(key) ? ItemStack.parseOptional(registryAccess(), tag.getCompound(key)) : ItemStack.EMPTY);
		}
		setEnergy(tag.getFloat("Energy"));
		setIntegrity(tag.contains("Integrity") ? tag.getFloat("Integrity") : IronManEnergy.maxIntegrity(suitId()));
		setMode(tag.getInt("Mode"));
		setOpen(tag.getBoolean("Open"));
		lightPos = tag.contains("Light") ? BlockPos.of(tag.getLong("Light")) : null;
		setPhase(PHASE_IDLE);
		if (tag.contains("HomeDock")) {
			// v0.15.15: saved mid send-home -- carry on flying home
			homeDock = BlockPos.of(tag.getLong("HomeDock"));
			setOpen(false);
			setPhase(PHASE_HOME);
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (ownerId != null) {
			tag.putUUID("Owner", ownerId);
		}
		tag.putString("Suit", suitId());
		for (int i = 0; i < 4; i++) {
			if (!stacks[i].isEmpty()) {
				tag.put("Piece" + i, stacks[i].save(registryAccess()));
			}
		}
		tag.putFloat("Energy", energy);
		tag.putFloat("Integrity", integrity);
		tag.putInt("Mode", prevMode >= 0 ? prevMode : mode()); // v0.15.15: an auto-defend saves the mode it came from
		tag.putBoolean("Open", isOpen());
		if (homeDock != null) {
			tag.putLong("HomeDock", homeDock.asLong());
		}
		if (lightPos != null) {
			tag.putLong("Light", lightPos.asLong());
		}
	}
}

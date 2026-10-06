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
	/** Deploy: the owner is nudged forward out of the open back at this tick... */
	public static final int EJECT_PUSH_TICK = 8;
	/** ...and the back closes again at this one. */
	public static final int EJECT_CLOSE_TICK = 26;
	/** Wrap-on: the owner is held inside the open suit; it shuts at this tick... */
	public static final int WRAP_CLOSE_TICK = 4;
	/** ...and the pieces are on at this one. */
	public static final int WRAP_DONE_TICK = 14;
	/** Step-in: the owner is walked into the open suit (and turned to its facing) over this many ticks. */
	public static final int STEP_TICKS = 24;
	/** Step-in only starts from within this distance of the suit. */
	public static final double STEP_REACH = 5.0;
	/** Defensive rescue: the suit lands this far behind its owner, open, and they are walked back into it. */
	public static final double RESCUE_BACK = 1.4;
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
	public static final double TELEPORT_RANGE = 32.0;
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
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.sentry.deployed",
				modeName(s.mode())).withStyle(ChatFormatting.AQUA), true);
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
			case PHASE_EJECT -> tickEject(owner);
			case PHASE_STEP_IN -> tickStepping(level, owner);
			case PHASE_WRAP -> tickWrap(level, owner);
			case PHASE_RESCUE -> tickRescue(level, owner);
			default -> tickIdle(level, owner);
		}
		getEntityData().set(FLYING, flying);
	}

	/** The owner if they are online, alive and in this dimension (found by level so mock players count), else null. */
	private ServerPlayer owner(ServerLevel level) {
		if (ownerId == null) {
			return null;
		}
		Player p = level.getPlayerByUUID(ownerId);
		return p instanceof ServerPlayer sp && sp.isAlive() && !sp.isRemoved() && !sp.isSpectator() ? sp : null;
	}

	private void tickEject(ServerPlayer owner) {
		walk(Vec3.ZERO);
		if (phaseTicks == EJECT_PUSH_TICK && owner != null) {
			// step out of the open back: a nudge forward, about a block
			Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
			owner.setDeltaMovement(fwd.x * 0.42, Math.max(0.0, owner.getDeltaMovement().y), fwd.z * 0.42);
			owner.hurtMarked = true;
		}
		if (phaseTicks >= EJECT_CLOSE_TICK) {
			setOpen(false);
			IronManSounds.play(this, IronManSounds.CLAMP, 0.8f, 1.1f);
			setPhase(PHASE_IDLE);
			rescueArmed = owner != null && owner.getHealth() > RESCUE_HEALTH;
		}
	}

	private void tickIdle(ServerLevel level, ServerPlayer owner) {
		getEntityData().set(TARGET_ID, target != null && target.isAlive() ? target.getId() : -1);
		if (!powered()) {
			target = null;
			clearLight();
			walk(Vec3.ZERO);
			return;
		}
		int mode = mode();
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
	 * Start the step-in: the suit is (or opens) open and holds still; its owner is walked from where they stand into it
	 * over {@link #STEP_TICKS}, turning to its facing, then {@link #beginWrap} closes it around them. They can't be hurt
	 * from here until the suit is on (or the step-in is abandoned).
	 */
	private void beginStepIn(ServerPlayer owner) {
		target = null;
		flying = false;
		setDeltaMovement(Vec3.ZERO);
		setOpen(true);
		stepFrom = owner.position();
		stepYawFrom = owner.getYRot();
		setPhase(PHASE_STEP_IN);
		getEntityData().set(BUSY, true);
		STEPPING.add(owner.getUUID());
		IronManSounds.play(this, IronManSounds.SERVO, 0.8f, 0.9f);
	}

	private void tickStepping(ServerLevel level, ServerPlayer owner) {
		if (!canWrap(owner)) {
			abandonStep(owner);
			return;
		}
		walk(Vec3.ZERO);
		float t = Math.min(1f, phaseTicks / (float) STEP_TICKS);
		float s = t * t * (3f - 2f * t);
		Vec3 at = stepFrom.lerp(position(), s);
		float yaw = stepYawFrom + Mth.wrapDegrees(getYRot() - stepYawFrom) * s;
		owner.teleportTo(level, at.x, at.y, at.z, yaw, owner.getXRot() * (1f - s) + 10f * s);
		owner.setDeltaMovement(Vec3.ZERO);
		if (phaseTicks % 6 == 0 && t < 1f) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
					SoundSource.PLAYERS, 0.25f, 0.8f + level.random.nextFloat() * 0.2f);
		}
		if (phaseTicks >= STEP_TICKS) {
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
		// land just behind the owner, facing the way they face, then walk them back into it
		Vec3 back = Vec3.directionFromRotation(0f, owner.getYRot()).scale(-RESCUE_BACK);
		Vec3 spot = owner.position().add(back);
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
		flyTowards(spot, Math.min(1.1, 0.35 + dist * 0.08));
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

	private void flyTowards(Vec3 dest, double speed) {
		flying = true;
		setNoGravity(true);
		Vec3 to = dest.add(0, 0.3, 0).subtract(position());
		Vec3 want = to.lengthSqr() < 1.0e-6 ? Vec3.ZERO : to.normalize().scale(Math.min(speed, to.length()));
		if (horizontalCollision) {
			want = new Vec3(want.x * 0.3, Math.max(want.y, 0.45), want.z * 0.3); // climb over what is in the way
		}
		Vec3 vel = getDeltaMovement().lerp(want, 0.3);
		setDeltaMovement(vel);
		move(MoverType.SELF, vel);
		if (vel.horizontalDistanceSqr() > 1.0e-4) {
			setYRot((float) (Mth.atan2(vel.z, vel.x) * (180.0 / Math.PI)) - 90f);
		}
		if (tickCount % 3 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.05, getZ(), 1, 0.08, 0.02, 0.08, 0.005);
		}
		if (tickCount % 20 == 0) {
			IronManSounds.play(this, IronManSounds.THRUSTER, 0.35f, 1.0f);
		}
	}

	private void follow(ServerLevel level, ServerPlayer owner) {
		double dist = distanceTo(owner);
		if (dist > TELEPORT_RANGE) {
			teleportNear(level, owner);
			return;
		}
		boolean high = Math.abs(owner.getY() - getY()) > 4.0;
		if (flying || dist > FLY_RANGE || high || stuckTicks > STUCK_TICKS) {
			if (dist < FOLLOW_START && !high) {
				flying = false; // close enough: drop down and walk again
				stuckTicks = 0;
				walk(Vec3.ZERO);
			} else {
				Vec3 dir = owner.position().subtract(position()).multiply(1, 0, 1);
				Vec3 dest = dir.lengthSqr() < 1.0e-4 ? owner.position() : owner.position().subtract(dir.normalize().scale(FOLLOW_STOP));
				flyTowards(dest, Math.min(1.0, 0.3 + dist * 0.06));
				return;
			}
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
		int mode = mode();
		STEPPING.remove(owner.getUUID());
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

	private void clientTick() {
		openAnimO = openAnim;
		float want = isOpen() ? 1f : 0f;
		float step = 1f / OPEN_ANIM_TICKS;
		openAnim = openAnim < want ? Math.min(want, openAnim + step) : Math.max(want, openAnim - step);
		walkAmtO = walkAmt;
		double dx = getX() - xo;
		double dz = getZ() - zo;
		float hspeed = (float) Math.sqrt(dx * dx + dz * dz);
		float speed = flying() ? 0f : hspeed;
		walkAmt += (Math.min(1f, speed * 4f) - walkAmt) * 0.4f;
		walkPos += speed * 2.5f;
		if (flying() && random.nextFloat() < 0.6f) {
			level().addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 0.2, getY() + 0.05,
					getZ() + (random.nextDouble() - 0.5) * 0.2, 0, -0.08, 0);
		}
		animate(hspeed);
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
			// hover-boost: arms back and out (palms down), legs trailing, leaning into the flight
			w[P_RARM_X] = 24f;
			w[P_LARM_X] = 24f;
			w[P_RARM_Z] = 16f;
			w[P_LARM_Z] = -16f;
			w[P_RLEG_X] = 14f;
			w[P_LLEG_X] = 8f;
			w[P_LEAN] = Math.min(28f, hspeed * 60f);
		} else {
			float sw = Mth.sin(walkPos) * walkAmt;
			w[P_RARM_X] = -sw * 32f + breath * 1.5f;
			w[P_LARM_X] = sw * 32f + breath * 1.5f;
			w[P_RARM_Z] = 3f + breath * 0.8f;
			w[P_LARM_Z] = -3f - breath * 0.8f;
			w[P_RLEG_X] = sw * 36f;
			w[P_LLEG_X] = -sw * 36f;
			w[P_LEAN] = walkAmt * 5f;
			w[P_BOB] = breath * 0.006f + Math.abs(sw) * 0.03f;
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
		for (int i = 0; i < POSE_CHANNELS; i++) {
			pose[i] = i == P_FLASH ? w[i] : pose[i] + (w[i] - pose[i]) * 0.3f;
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
		tag.putInt("Mode", mode());
		tag.putBoolean("Open", isOpen());
		if (lightPos != null) {
			tag.putLong("Light", lightPos.asLong());
		}
	}
}

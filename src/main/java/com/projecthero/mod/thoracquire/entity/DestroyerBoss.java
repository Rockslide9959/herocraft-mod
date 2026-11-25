package com.projecthero.mod.thoracquire.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.thoracquire.MjolnirLift;
import com.projecthero.mod.thoracquire.TrialOfAsgard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.17.7 (user: "can you make the mjolnir guardian the destroyer please, i think it would be more fitting"): <b>The
 * Destroyer</b>, the guardian of the Trial of Asgard -- the enchanted Asgardian armour from Thor (2011). About 3.6 blocks of
 * lean, dull-silver segmented plate with a thin waist and a tall flat-topped helm; its slotted faceplate parts to show the fire
 * inside, and orange seams glow in its joints. No weapon: it fights with its body and the fire in its face. Custom GeckoLib
 * body ({@code scratchpad/v0177/gen_destroyer.js}). {@link #MAX_HEALTH} health, 12 armour; tuned for a well-geared player
 * WITHOUT powers (the trial must be faced unpowered): hard but fair, a few minutes.
 *
 * <p>Every attack is telegraphed:
 * <ul>
 * <li><b>Disintegration Beam</b> (the signature): it stops and plants its feet, the faceplate grinds open in steps while the
 * fire builds ({@link #BEAM_WINDUP} ticks), then a thick molten beam roars out of its face and sweeps slowly across the arena
 * ({@link #BEAM_ARC} degrees each side of where you stood, over {@link #BEAM_SWEEP} ticks): {@link #BEAM_DAMAGE} every
 * {@link #BEAM_HIT_EVERY} ticks while you're in it, sets you alight, and leaves a burning scorch where it crosses the ground
 * (no blocks are changed). It stops at blocks: get behind cover, under it, or out of the arc.</li>
 * <li><b>Fire Blast</b> (5-16 blocks): the faceplate flashes open and a line of embers marks where it is aimed
 * ({@link #BLAST_WINDUP} ticks), then a short blast of fire down that line: {@link #BLAST_DAMAGE} and alight.</li>
 * <li><b>Armoured Slam</b> (within 7): both fists raised while a ring marks the reach ({@link #SLAM_WINDUP} ticks), then a
 * ground-pound whose shockwave rolls out to {@link #SLAM_RADIUS} blocks -- {@link #SLAM_DAMAGE} to anyone ON THE GROUND as it
 * passes (jump it).</li>
 * <li><b>Charge</b> (6-18 blocks): a crouch while a line of embers marks the path ({@link #CHARGE_WINDUP} ticks), then a
 * shoulder charge at {@link #CHARGE_SPEED} b/t -- {@link #CHARGE_DAMAGE} and thrown -- and a stagger at the end where it takes
 * +25% (the opening to punish).</li>
 * <li><b>Backhand</b> (its melee): the arm drawn across the body ({@link #BACKHAND_WINDUP} ticks), then a swat out to the side:
 * {@link #BACKHAND_DAMAGE} to everyone in front within {@link #BACKHAND_REACH}, knocked well away.</li>
 * </ul>
 * <p><b>Phase 2</b> at half health: a 2 s surge (it can't be hurt) as lightning pours in and its seams blaze, then it is
 * <b>Unleashed</b> -- faster, cooldowns cut by 30%, damage +15%, the beam sweeps wider and back again, a double Fire Blast and
 * a double slam.
 *
 * <p>Lightning-proof. Shared {@link BossThreat} table (switches to whoever hurts it most), boss bar within {@link #BAR_RANGE},
 * leashed to its crater. Only damage from unpowered players (and their arrows / pets) hurts it -- borrowed power can't win the
 * trial for you. A trial boss that loses its trial (or is loaded with none running) dissipates; one from a spawn egg is free.
 */
public class DestroyerBoss extends Monster implements GeoEntity {
	public static final double MAX_HEALTH = 420.0;
	public static final double BAR_RANGE = 56.0;
	public static final double TETHER = 28.0;

	public static final float BEAM_DAMAGE = 10.0f;
	public static final float BLAST_DAMAGE = 8.0f;
	public static final float CHARGE_DAMAGE = 12.0f;
	public static final float SLAM_DAMAGE = 10.0f;
	public static final float BACKHAND_DAMAGE = 9.0f;
	public static final double CHARGE_SPEED = 0.9;
	public static final double SLAM_RADIUS = 8.0;
	public static final double BEAM_RANGE = 22.0;
	public static final double BLAST_RANGE = 16.0;
	public static final double BACKHAND_REACH = 4.2;
	/** Half the sweep, degrees (phase 1); Unleashed sweeps {@link #BEAM_ARC_WIDE} each side and back again. */
	public static final float BEAM_ARC = 50f;
	public static final float BEAM_ARC_WIDE = 65f;
	/** The face, above the feet: where the beam leaves the helm. */
	public static final double FACE_Y = 3.15;
	/** How far in front of its feet the face is (the helm front, at 1.8x a player head). */
	public static final double FACE_FORWARD = 0.45;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_BEAM = 1;
	public static final byte MOVE_CHARGE = 2;
	public static final byte MOVE_SLAM = 3;
	public static final byte MOVE_BLAST = 4;
	public static final byte MOVE_SURGE = 5;
	public static final byte MOVE_FORM = 6;
	public static final byte MOVE_BACKHAND = 7;

	public static final int BEAM_WINDUP = 30;
	public static final int BEAM_SWEEP = 60;
	public static final int BEAM_SWEEP_WIDE = 100;
	public static final int BEAM_RECOVER = 15;
	public static final int BEAM_HIT_EVERY = 5;
	public static final int BLAST_WINDUP = 14;
	public static final int BLAST_FIRE = 10;
	public static final int BLAST_GAP = 8;
	public static final int BLAST_RECOVER = 8;
	public static final int CHARGE_WINDUP = 16;
	public static final int CHARGE_RUN = 16;
	public static final int CHARGE_STAGGER = 18;
	public static final int SLAM_WINDUP = 24;
	public static final int BACKHAND_WINDUP = 10;
	public static final int BACKHAND_TICKS = 22;
	public static final int SURGE_TICKS = 40;
	public static final int FORM_TICKS = 50;
	public static final int DEATH_TICKS = 50;
	/** Ticks into {@link #FORM_TICKS} when the faceplate cracks open on the fire (the end of the arrival). */
	public static final int FORM_GLOW_AT = 38;

	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_BEAM_ON = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_BEAM_YAW = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BEAM_PITCH = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_BEAM_LEN = SynchedEntityData.defineId(DestroyerBoss.class, EntityDataSerializers.FLOAT);
	private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(1f, 0.5f, 0.12f), 1.0f);
	private static final DustParticleOptions MARK_HOT = new DustParticleOptions(new Vector3f(1f, 0.86f, 0.5f), 1.25f);
	public static final int BEAM_RGB = 0xFF7A1E;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final BossThreat threat = new BossThreat(this).range(48).holdWhile(() -> this.move() != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.destroyer").withStyle(ChatFormatting.WHITE),
			BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private boolean trialBound;

	int moveTicks;
	int beamCd = 100;
	int blastCd = 50;
	int chargeCd = 80;
	int slamCd = 120;
	int backhandCd = 0;
	int gap = 20;
	private Vec3 chargeDir = Vec3.ZERO;
	private final Set<LivingEntity> struck = new HashSet<>();
	private int movesUsed;
	/** The beam: the bearing it is centred on, which way it sweeps, its pitch, when each victim may next be hit. */
	private float beamBase;
	private float beamPitch;
	private int beamSign = 1;
	private final Map<Integer, Integer> beamNext = new HashMap<>();
	private int beamHits;
	/** Ground the beam crossed: burning for a few seconds ({@code x, y, z, until}). Instance state, gone with the boss. */
	private final List<double[]> scorch = new ArrayList<>();

	// client: the beam's bearing last tick and this tick, for smooth drawing
	public float beamYawO;
	public float beamYawC;
	/** Client: when the current move began (for the glow building at its face). */
	public int clientMoveStart;
	private byte clientMove = MOVE_NONE;

	public DestroyerBoss(EntityType<? extends DestroyerBoss> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.ATTACK_DAMAGE, BACKHAND_DAMAGE)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, MOVE_NONE);
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_BEAM_ON, false);
		builder.define(DATA_BEAM_YAW, 0f);
		builder.define(DATA_BEAM_PITCH, 0f);
		builder.define(DATA_BEAM_LEN, 0f);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MovesGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				e -> !(e instanceof Player p) || (!p.getAbilities().instabuild && !p.isSpectator())));
	}

	// ---------------------------------------------------------------- state

	public byte move() {
		return entityData.get(DATA_MOVE);
	}

	private void setMove(byte m) {
		entityData.set(DATA_MOVE, m);
	}

	public int moveTicks() {
		return moveTicks;
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** A beam (the Disintegration Beam or a Fire Blast) is coming out of its face this tick. */
	public boolean beamOn() {
		return entityData.get(DATA_BEAM_ON);
	}

	public float beamYaw() {
		return entityData.get(DATA_BEAM_YAW);
	}

	public float beamPitch() {
		return entityData.get(DATA_BEAM_PITCH);
	}

	public float beamLength() {
		return entityData.get(DATA_BEAM_LEN);
	}

	/** Beam / blast hits landed so far (tests). */
	public int beamHits() {
		return beamHits;
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bar() {
		return bar;
	}

	public int movesUsed() {
		return movesUsed;
	}

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home != null) {
			restrictTo(home, 16);
		}
	}

	public boolean trialBound() {
		return trialBound;
	}

	/** Marks this boss as the guardian of a running trial (it dissipates if the trial stops owning it). */
	public void bindToTrial(BlockPos crater) {
		this.trialBound = true;
		setHome(crater);
		begin(MOVE_FORM);
	}

	private int cd(int base) {
		return phase() >= 2 ? (int) (base * 0.7) : base;
	}

	public int beamSweepTicks() {
		return phase() >= 2 ? BEAM_SWEEP_WIDE : BEAM_SWEEP;
	}

	public int blastShots() {
		return phase() >= 2 ? 2 : 1;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Lightning-proof: a bolt neither burns nor hurts it (the storm is its element). */
	@Override
	public void thunderHit(ServerLevel level, LightningBolt bolt) {
	}

	// ---------------------------------------------------------------- sounds: iron and fire

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 1.2f, 0.6f);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.55f;
	}

	private void sound(SoundEvent s, float volume, float pitch) {
		level().playSound(null, getX(), getY() + 2, getZ(), s, SoundSource.HOSTILE, volume, pitch);
	}

	/** The faceplate grinding open or shut: anvil, iron door and smithing table, low. */
	private void grind(boolean open) {
		sound(open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, 1.2f, 0.55f);
		sound(SoundEvents.SMITHING_TABLE_USE, 0.9f, 0.6f);
		if (open) {
			sound(SoundEvents.ANVIL_USE, 0.5f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- damage

	/** Only an unpowered player's own hits count: borrowed power can't win the trial. */
	public static boolean countsAgainst(DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof OwnableEntity pet && pet.getOwner() instanceof LivingEntity owner) {
			by = owner;
		}
		if (by instanceof ServerPlayer p) {
			return p.getAbilities().instabuild || !com.projecthero.mod.hero.HeroTiers.hasAnyPower(p);
		}
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return super.hurt(source, amount);
		}
		if (move() == MOVE_SURGE || move() == MOVE_FORM || source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
			return false;
		}
		if (!countsAgainst(source)) {
			if (source.getEntity() instanceof ServerPlayer p && tickCount % 10 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.trial.borrowed_power").withStyle(ChatFormatting.GRAY), true);
			}
			if (level() instanceof ServerLevel s) {
				s.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 2, getZ(), 6, 0.5, 0.8, 0.5, 0.02);
			}
			return false;
		}
		if (move() == MOVE_CHARGE && moveTicks >= CHARGE_WINDUP + CHARGE_RUN) {
			amount *= 1.25f; // the stagger after a charge: the opening
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server && isAlive()) {
			threat.record(source, amount);
			if (phase() == 1 && getHealth() <= getMaxHealth() * 0.5f) {
				startSurge(server);
			}
		}
		return hurt;
	}

	/** The second gate: whatever path a hit takes into the health (some power hooks bypass {@link #hurt}), borrowed power does nothing. */
	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		if (!source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && source.getEntity() != null && !countsAgainst(source)) {
			return;
		}
		super.actuallyHurt(source, amount);
	}

	/** Its melee is the telegraphed Backhand: the melee goal's "hit now" starts it, the swat lands on its own frame. */
	@Override
	public boolean doHurtTarget(Entity target) {
		if (move() == MOVE_NONE && backhandCd <= 0 && onGround()) {
			startBackhand();
			return true;
		}
		return false;
	}

	/** Half health: the surge into phase 2 (2 s, untouchable) -- then Unleashed. */
	public void startSurge(ServerLevel level) {
		entityData.set(DATA_PHASE, (byte) 2);
		begin(MOVE_SURGE);
		bar.setColor(BossEvent.BossBarColor.RED);
		bar.setName(Component.translatable("projecthero.trial.destroyer.unleashed").withStyle(ChatFormatting.RED));
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.30);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.9f, 1.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 1.4f, 0.5f);
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE)) {
			p.displayClientMessage(Component.translatable("projecthero.trial.destroyer.phase2").withStyle(ChatFormatting.RED), false);
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			byte m = move();
			if (m != clientMove) {
				clientMove = m;
				clientMoveStart = tickCount;
			}
			beamYawO = beamYawC;
			beamYawC = beamYaw();
			if (!beamOn()) {
				beamYawO = beamYawC;
			}
		}
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (trialBound && tickCount > 40 && !TrialOfAsgard.owns(this)) {
			dissipate();
			return;
		}
		threat.tick();
		if (beamCd > 0) {
			beamCd--;
		}
		if (blastCd > 0) {
			blastCd--;
		}
		if (chargeCd > 0) {
			chargeCd--;
		}
		if (slamCd > 0) {
			slamCd--;
		}
		if (backhandCd > 0) {
			backhandCd--;
		}
		if (gap > 0) {
			gap--;
		}
		if (move() == MOVE_SURGE || move() == MOVE_FORM) {
			tickMove(server); // these run without a target
		}
		tickScorch(server);
		// embers leaking from the joints; Unleashed, the seams pour them
		if (phase() >= 2 ? tickCount % 4 == 0 : tickCount % 9 == 0) {
			server.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.2 + random.nextDouble() * 2.0, getZ(),
					1, 0.35, 0.5, 0.35, 0.01);
			if (phase() >= 2 && tickCount % 10 == 0) {
				server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 2.0, getZ(), 1, 0.3, 0.6, 0.3, 0);
			}
		}
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
		}
		if (home != null && tickCount % 20 == 0) {
			leash();
		}
	}

	private void refreshViewers(ServerLevel level) {
		viewers.removeIf(p -> {
			boolean keep = p.isAlive() && !p.isRemoved() && p.level() == level && p.distanceToSqr(this) <= (BAR_RANGE + 8) * (BAR_RANGE + 8);
			if (!keep) {
				bar.removePlayer(p);
			}
			return !keep;
		});
		for (ServerPlayer p : level.players()) {
			if (!viewers.contains(p) && !p.isSpectator() && p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE) {
				viewers.add(p);
				bar.addPlayer(p);
			}
		}
	}

	private void leash() {
		Vec3 h = Vec3.atBottomCenterOf(home);
		if (position().distanceTo(h) > TETHER || getY() < home.getY() - 10) {
			moveTo(h.x + 2, h.y, h.z + 2, getYRot(), 0f);
			setDeltaMovement(Vec3.ZERO);
			getNavigation().stop();
			if (move() == MOVE_CHARGE) {
				endMove();
			}
		}
	}

	/** The guardian fades into the storm (a lost trial, or no trial owns it): the fire goes out and the armour is taken back. */
	public void dissipate() {
		if (level() instanceof ServerLevel s) {
			s.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.8, getZ(), 80, 0.8, 1.4, 0.8, 0.06);
			s.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.8, getZ(), 40, 0.6, 1.2, 0.6, 0.04);
			s.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.8, getZ(), 40, 0.8, 1.4, 0.8, 0.3);
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0f, 1.4f);
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2f, 0.5f);
		}
		bar.removeAllPlayers();
		viewers.clear();
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		entityData.set(DATA_BEAM_ON, false);
		if (level() instanceof ServerLevel s) {
			bar.removeAllPlayers();
			viewers.clear();
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.6f, 0.5f);
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.4f, 0.6f);
			grind(false);
		}
	}

	/** The armour lingers {@link #DEATH_TICKS} ticks so the collapse plays out: the knees hit at ~1.1 s, the fire smokes out. */
	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel s) {
			if (deathTime == 22) {
				s.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2f, 0.5f);
				s.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.HOSTILE, 1.6f, 0.4f);
				s.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 16, 0.8, 0.1, 0.8, 0.02);
			}
			if (deathTime % 3 == 0) {
				s.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 3, 0.4, 0.5, 0.4, 0.01);
			}
			if (deathTime >= DEATH_TICKS && !isRemoved()) {
				s.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 30, 0.6, 0.8, 0.6, 0.03);
				s.broadcastEntityEvent(this, (byte) 60);
				remove(Entity.RemovalReason.KILLED);
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		viewers.remove(player);
		bar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		bar.removeAllPlayers();
		viewers.clear();
		super.remove(reason);
	}

	// ---------------------------------------------------------------- moves

	static boolean victim(LivingEntity e, LivingEntity self) {
		return e != self && e.isAlive() && !(e instanceof DestroyerBoss)
				&& !(e instanceof Player p && (p.getAbilities().instabuild || p.isSpectator()));
	}

	private void begin(byte which) {
		setMove(which);
		moveTicks = 0;
		movesUsed++;
		entityData.set(DATA_BEAM_ON, false);
		getNavigation().stop();
	}

	void endMove() {
		setMove(MOVE_NONE);
		moveTicks = 0;
		entityData.set(DATA_BEAM_ON, false);
		beamNext.clear();
		gap = phase() >= 2 ? 14 : 22;
	}

	private float scaled(float base) {
		return phase() >= 2 ? base * 1.15f : base;
	}

	/** Its face: where the beam leaves the helm, a little in front along {@code yaw}. */
	public Vec3 face(float yaw) {
		Vec3 fwd = Vec3.directionFromRotation(0f, yaw);
		return position().add(fwd.x * FACE_FORWARD, FACE_Y, fwd.z * FACE_FORWARD);
	}

	private void setFacing(float yaw) {
		setYRot(yaw);
		setYBodyRot(yaw);
		setYHeadRot(yaw);
		yRotO = yaw;
		yBodyRotO = yaw;
	}

	/** The bearing and pitch from the face to {@code t}'s chest. */
	private float[] aimAt(LivingEntity t) {
		Vec3 from = position().add(0, FACE_Y, 0);
		Vec3 d = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) (-Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		return new float[] { yaw, Mth.clamp(pitch, -15f, 45f) };
	}

	private void faceTarget() {
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
			setFacing(aimAt(t)[0]);
		}
	}

	/** Starts the Disintegration Beam now (test hook too). */
	public void startBeam() {
		begin(MOVE_BEAM);
		beamNext.clear();
		beamSign = random.nextBoolean() ? 1 : -1;
		LivingEntity t = getTarget();
		if (t != null) {
			float[] a = aimAt(t);
			beamBase = a[0];
			beamPitch = a[1];
		} else {
			beamBase = getYRot();
			beamPitch = 15f;
		}
		beamCd = cd(240);
		grind(true);
	}

	public void startBlast() {
		begin(MOVE_BLAST);
		beamNext.clear();
		blastCd = cd(110);
		sound(SoundEvents.IRON_DOOR_OPEN, 1.0f, 0.9f);
	}

	public void startCharge() {
		begin(MOVE_CHARGE);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		to = new Vec3(to.x, 0, to.z);
		chargeDir = to.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : to.normalize();
		chargeCd = cd(110);
		sound(SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f);
		sound(SoundEvents.SMITHING_TABLE_USE, 0.8f, 0.5f);
	}

	public void startSlam() {
		begin(MOVE_SLAM);
		struck.clear();
		slamCd = cd(160);
		sound(SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.7f);
		sound(SoundEvents.ANVIL_USE, 0.6f, 0.45f);
	}

	public void startBackhand() {
		begin(MOVE_BACKHAND);
		struck.clear();
		backhandCd = cd(30);
		sound(SoundEvents.IRON_GOLEM_REPAIR, 0.8f, 0.9f);
	}

	void tickMove(ServerLevel level) {
		int t = ++moveTicks;
		switch (move()) {
			case MOVE_BEAM -> tickBeam(level, t);
			case MOVE_BLAST -> tickBlast(level, t);
			case MOVE_CHARGE -> tickCharge(level, t);
			case MOVE_SLAM -> tickSlam(level, t);
			case MOVE_BACKHAND -> tickBackhand(level, t);
			case MOVE_SURGE -> tickSurge(level, t);
			case MOVE_FORM -> tickForm(level, t);
			default -> endMove();
		}
	}

	// ---- the Disintegration Beam

	private void tickBeam(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		int sweep = beamSweepTicks();
		if (t <= BEAM_WINDUP) {
			// tracks you while the plates open, then locks where you stood
			LivingEntity tg = getTarget();
			if (tg != null && t <= BEAM_WINDUP - 6) {
				float[] a = aimAt(tg);
				beamBase = a[0];
				beamPitch = a[1];
			}
			float startYaw = beamBase - beamSign * (phase() >= 2 ? BEAM_ARC_WIDE : BEAM_ARC);
			setFacing(t <= BEAM_WINDUP - 6 ? beamBase : Mth.rotLerp((t - (BEAM_WINDUP - 6)) / 6f, beamBase, startYaw));
			// the fire building behind the plates
			Vec3 f = face(getYRot());
			float k = t / (float) BEAM_WINDUP;
			if (t % 3 == 0) {
				level.sendParticles(ParticleTypes.SMALL_FLAME, f.x, f.y, f.z, 1 + (int) (k * 2), 0.1, 0.1, 0.1, 0.005);
			}
			// the plates grinding open in steps (the clip opens at 0.5 / 0.85 / 1.2 / 1.5 s)
			if (t == 10 || t == 17 || t == 24) {
				grind(true);
			}
			if (t % 3 == 0 && t > 10) {
				// where it will start: a line of embers along the ground
				Vec3 dir = Vec3.directionFromRotation(0f, startYaw);
				for (double d = 2; d < BEAM_RANGE * 0.7; d += 1.5) {
					Vec3 p = ground(position().add(dir.scale(d)));
					level.sendParticles(t > BEAM_WINDUP - 8 ? MARK_HOT : MARK, p.x, p.y + 0.15, p.z, 1, 0.1, 0.02, 0.1, 0);
				}
			}
			if (t == BEAM_WINDUP) {
				sound(SoundEvents.BEACON_ACTIVATE, 2.0f, 0.6f);
				sound(SoundEvents.BLAZE_SHOOT, 1.6f, 0.5f);
				sound(SoundEvents.FIRECHARGE_USE, 1.4f, 0.6f);
			}
			return;
		}
		if (t <= BEAM_WINDUP + sweep) {
			float k = (t - BEAM_WINDUP) / (float) sweep;
			float off;
			if (phase() >= 2) {
				float k2 = k * 2f; // across and back again
				off = (k2 <= 1f ? -1f + 2f * k2 : 1f - 2f * (k2 - 1f)) * BEAM_ARC_WIDE;
			} else {
				off = (-1f + 2f * k) * BEAM_ARC;
			}
			float yaw = beamBase + beamSign * off;
			setFacing(yaw);
			fireBeam(level, yaw, beamPitch, BEAM_RANGE, BEAM_DAMAGE, true);
			if (t % 4 == 0) {
				sound(SoundEvents.BLAZE_BURN, 1.4f, 0.5f);
				sound(SoundEvents.FIRE_AMBIENT, 1.6f, 0.6f);
			}
			if (t % 20 == 1) {
				sound(SoundEvents.BEACON_AMBIENT, 2.0f, 0.5f);
			}
			if (BeamClash.locked(this)) {
				moveTicks--; // a clash owns the beam: the sweep holds still
			}
			return;
		}
		entityData.set(DATA_BEAM_ON, false);
		if (t == BEAM_WINDUP + sweep + 1) {
			sound(SoundEvents.BEACON_DEACTIVATE, 1.4f, 0.7f);
		}
		if (t == BEAM_WINDUP + sweep + 7) {
			grind(false);
		}
		if (t >= BEAM_WINDUP + sweep + BEAM_RECOVER) {
			endMove();
		}
	}

	/**
	 * One live tick of a face beam: reports it for clashing, stops it at blocks, burns whoever is in it (the Disintegration
	 * Beam every {@link #BEAM_HIT_EVERY} ticks; a Fire Blast once per shot) and scorches the ground it crosses.
	 */
	private void fireBeam(ServerLevel level, float yaw, float pitch, double range, float damage, boolean repeat) {
		Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
		Vec3 from = face(yaw);
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 end = hit.getType() == HitResult.Type.MISS ? from.add(dir.scale(range)) : hit.getLocation();
		entityData.set(DATA_BEAM_ON, true);
		entityData.set(DATA_BEAM_YAW, yaw);
		entityData.set(DATA_BEAM_PITCH, pitch);
		entityData.set(DATA_BEAM_LEN, (float) from.distanceTo(end));
		BeamClash.report(this, from, dir, range, 1.6f, BEAM_RGB);
		if (BeamClash.locked(this)) {
			return;
		}
		double half = repeat ? 0.5 : 0.4;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.0), e -> victim(e, this))) {
			if (e.getBoundingBox().inflate(half).clip(from, end).isEmpty()) {
				continue;
			}
			int now = tickCount;
			if (repeat ? beamNext.getOrDefault(e.getId(), 0) > now : !struck.add(e)) {
				continue;
			}
			beamNext.put(e.getId(), now + BEAM_HIT_EVERY);
			e.invulnerableTime = 0; // the beam burns four times a second, past the usual hurt cooldown
			Vec3 keep = e.getDeltaMovement();
			if (e.hurt(damageSources().mobAttack(this), scaled(damage))) {
				beamHits++;
				if (repeat) {
					// a beam burns, it doesn't shove: no knockback flinging you out past where it meets the ground
					e.setDeltaMovement(keep);
					e.hurtMarked = true;
				}
			}
			e.igniteForSeconds(repeat ? 4 : 3);
		}
		if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3 p = hit.getLocation();
			if (tickCount % 2 == 0) {
				scorch.add(new double[] { p.x, p.y, p.z, tickCount + 60 });
				if (scorch.size() > 90) {
					scorch.remove(0);
				}
			}
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.1, p.z, 4, 0.25, 0.1, 0.25, 0.04);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.2, p.z, 1, 0.2, 0.1, 0.2, 0.02);
			if (tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.LAVA, p.x, p.y + 0.1, p.z, 1, 0.1, 0.05, 0.1, 0);
			}
		}
	}

	/** The scorched ground: flames for a few seconds, setting alight anyone who stands in them. No blocks change. */
	private void tickScorch(ServerLevel level) {
		if (scorch.isEmpty()) {
			return;
		}
		Iterator<double[]> it = scorch.iterator();
		while (it.hasNext()) {
			double[] s = it.next();
			if (tickCount >= s[3]) {
				it.remove();
				continue;
			}
			if ((tickCount + (int) (s[0] * 7)) % 4 == 0) {
				level.sendParticles(ParticleTypes.FLAME, s[0], s[1] + 0.1, s[2], 1, 0.2, 0.02, 0.2, 0.01);
			}
			if (tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, s[0], s[1] + 0.2, s[2], 1, 0.15, 0.05, 0.15, 0.01);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(s[0] - 0.8, s[1] - 0.5, s[2] - 0.8, s[0] + 0.8, s[1] + 1.2, s[2] + 0.8),
						e -> victim(e, this))) {
					e.igniteForSeconds(3);
				}
			}
		}
	}

	/** Tests / films: no move of its own choosing (nor a backhand) for {@code ticks}. */
	public void restForTest(int ticks) {
		gap = ticks;
		backhandCd = ticks;
	}

	public int scorchCount() {
		return scorch.size();
	}

	// ---- Fire Blast

	private void tickBlast(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		int shot = 0;
		int s = t;
		int span = BLAST_WINDUP + BLAST_FIRE;
		if (blastShots() >= 2 && t > span) {
			shot = 1;
			s = t - span + (BLAST_WINDUP - BLAST_GAP); // the second shot's shorter re-aim
		}
		if (s <= BLAST_WINDUP) {
			LivingEntity tg = getTarget();
			if (tg != null && s <= BLAST_WINDUP - 6) {
				float[] a = aimAt(tg);
				beamBase = a[0];
				beamPitch = a[1];
				struck.clear();
			}
			setFacing(beamBase);
			entityData.set(DATA_BEAM_ON, false);
			Vec3 f = face(beamBase);
			if (s == 6 || (shot == 1 && s == BLAST_WINDUP - BLAST_GAP + 1)) {
				sound(SoundEvents.IRON_DOOR_OPEN, 1.0f, 0.9f); // the faceplate flashes open
			}
			if (s % 3 == 0) {
				level.sendParticles(ParticleTypes.SMALL_FLAME, f.x, f.y, f.z, 1, 0.08, 0.08, 0.08, 0.005);
			}
			if (s > BLAST_WINDUP - 6) {
				// the line it will fire down
				Vec3 dir = Vec3.directionFromRotation(beamPitch, beamBase);
				for (double d = 1.5; d < BLAST_RANGE; d += 1.0) {
					Vec3 p = f.add(dir.scale(d));
					level.sendParticles(MARK_HOT, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
				}
			}
			return;
		}
		if (s <= BLAST_WINDUP + BLAST_FIRE) {
			if (s == BLAST_WINDUP + 1) {
				sound(SoundEvents.BLAZE_SHOOT, 1.6f, 0.6f);
				sound(SoundEvents.FIRECHARGE_USE, 1.2f, 0.8f);
			}
			fireBeam(level, beamBase, beamPitch, BLAST_RANGE, BLAST_DAMAGE, false);
			return;
		}
		entityData.set(DATA_BEAM_ON, false);
		if (shot == blastShots() - 1 && s == BLAST_WINDUP + BLAST_FIRE + 3) {
			sound(SoundEvents.IRON_DOOR_CLOSE, 1.0f, 0.9f);
		}
		if (shot == blastShots() - 1 && s >= BLAST_WINDUP + BLAST_FIRE + BLAST_RECOVER) {
			endMove();
		}
	}

	// ---- Charge

	private void tickCharge(ServerLevel level, int t) {
		if (t <= CHARGE_WINDUP) {
			faceTarget();
			LivingEntity tg = getTarget();
			if (tg != null) {
				Vec3 to = tg.position().subtract(position());
				to = new Vec3(to.x, 0, to.z);
				if (to.lengthSqr() > 1.0e-3) {
					chargeDir = to.normalize();
				}
			}
			if (t % 2 == 0) {
				for (double d = 1.5; d < CHARGE_RUN * CHARGE_SPEED; d += 1.0) {
					Vec3 p = position().add(chargeDir.scale(d));
					level.sendParticles(t > CHARGE_WINDUP - 5 ? MARK_HOT : MARK, p.x, p.y + 0.15, p.z, 1, 0.15, 0.02, 0.15, 0);
				}
			}
			return;
		}
		if (t <= CHARGE_WINDUP + CHARGE_RUN) {
			setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
			hurtMarked = true;
			if (t % 3 == 0) {
				sound(SoundEvents.IRON_GOLEM_STEP, 1.4f, 0.7f);
			}
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 1, 0.4, 0.05, 0.4, 0.01);
			level.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 1.4, getZ(), 3, 0.5, 0.7, 0.5, 0.03);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), e -> victim(e, this))) {
				if (struck.add(e)) {
					e.hurt(damageSources().mobAttack(this), scaled(CHARGE_DAMAGE));
					e.push(chargeDir.x * 1.4, 0.45, chargeDir.z * 1.4);
					e.hurtMarked = true;
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.6f);
				}
			}
			if (horizontalCollision) {
				moveTicks = CHARGE_WINDUP + CHARGE_RUN; // hit a wall: straight into the stagger
				sound(SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
			}
			return;
		}
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t >= CHARGE_WINDUP + CHARGE_RUN + CHARGE_STAGGER) {
			endMove();
		}
	}

	// ---- Armoured Slam

	private void tickSlam(ServerLevel level, int t) {
		faceTarget();
		if (t <= SLAM_WINDUP) {
			if (t % 3 == 0) {
				ring(level, position(), SLAM_RADIUS, t > SLAM_WINDUP - 8 ? MARK_HOT : MARK, 40);
			}
			return;
		}
		int wave1 = t - SLAM_WINDUP;
		int wave2 = phase() >= 2 ? t - SLAM_WINDUP - 14 : -1;
		if (wave1 == 1 || wave2 == 1) {
			sound(SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
			sound(SoundEvents.ANVIL_LAND, 1.4f, 0.5f);
			sound(SoundEvents.IRON_GOLEM_ATTACK, 1.6f, 0.5f);
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.1, 0.6, 0);
			MjolnirLift.shake(level, this, 0.6f, 10);
			struck.clear();
		}
		for (int w : new int[] { wave1, wave2 }) {
			if (w < 1 || w > 11) {
				continue;
			}
			double r = w * (SLAM_RADIUS / 10.0);
			ring(level, position(), r, MARK_HOT, (int) (r * 6) + 6);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 2, r * 0.5, 0.05, r * 0.5, 0.01);
			level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 4, r * 0.5, 0.05, r * 0.5, 0.02);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1, 2, r + 1), e -> victim(e, this))) {
				double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
				if (d >= r - 1.2 && d <= r + 0.6 && e.onGround() && Math.abs(e.getY() - getY()) < 2.0 && struck.add(e)) {
					e.hurt(damageSources().mobAttack(this), scaled(SLAM_DAMAGE));
					Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize();
					e.push(away.x * 0.7, 0.55, away.z * 0.7);
					e.hurtMarked = true;
				}
			}
		}
		int end = SLAM_WINDUP + (phase() >= 2 ? 14 : 0) + 18;
		if (t >= end) {
			endMove();
		}
	}

	// ---- Backhand

	private void tickBackhand(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t <= BACKHAND_WINDUP) {
			faceTarget();
			return;
		}
		if (t == BACKHAND_WINDUP + 1) {
			sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
			Vec3 fwd = Vec3.directionFromRotation(0f, getYRot());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(BACKHAND_REACH, 1.0, BACKHAND_REACH), e -> victim(e, this))) {
				Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
				double d = to.length();
				if (d > BACKHAND_REACH + e.getBbWidth() * 0.5 || (d > 0.5 && to.normalize().dot(fwd) < 0.0)) {
					continue;
				}
				if (e.hurt(damageSources().mobAttack(this), scaled(BACKHAND_DAMAGE))) {
					Vec3 away = d < 1.0e-3 ? fwd : to.normalize();
					e.push(away.x * 1.6, 0.5, away.z * 1.6);
					e.hurtMarked = true;
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.HOSTILE, 1.2f, 0.7f);
				}
			}
		}
		if (t >= BACKHAND_TICKS) {
			endMove();
		}
	}

	// ---- Phase 2 surge and the arrival

	private void tickSurge(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t % 8 == 1) {
			MjolnirLift.bolt(level, position()); // the storm pours in (lightning-proof: it only feeds it)
		}
		level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.8, getZ(), t % 2 == 0 ? 2 : 0, 0.5, 1.1, 0.5, 0.02);
		if (t == 26) {
			grind(true);
			sound(SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
			Vec3 f = face(getYRot());
			level.sendParticles(ParticleTypes.FLAME, f.x, f.y, f.z, 30, 0.2, 0.2, 0.2, 0.15);
		}
		if (t >= SURGE_TICKS) {
			endMove();
		}
	}

	/** The arrival: it lands in the lightning on one knee, stands, and the faceplate cracks open on the fire (FORM_GLOW_AT). */
	private void tickForm(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t == 1) {
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 0.8, 0.1, 0.8, 0);
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 30, 1.4, 0.1, 1.4, 0.05);
			sound(SoundEvents.ANVIL_LAND, 1.6f, 0.5f);
			sound(SoundEvents.IRON_GOLEM_STEP, 2.0f, 0.4f);
			MjolnirLift.shake(level, this, 0.5f, 10);
		}
		if (t < 20 && t % 2 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4, getZ(), 6, 0.6, 1.0, 0.6, 0.2);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.3, getZ(), 1, 0.8, 0.1, 0.8, 0.01);
		}
		if (t == 12 || t == 24) {
			sound(SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f); // the plates settling as it stands
		}
		if (t == FORM_GLOW_AT) {
			grind(true);
			sound(SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		}
		if (t >= FORM_GLOW_AT && t < FORM_GLOW_AT + 8) {
			Vec3 f = face(getYRot());
			level.sendParticles(ParticleTypes.SMALL_FLAME, f.x, f.y, f.z, 1, 0.1, 0.1, 0.1, 0.005);
		}
		if (t >= FORM_TICKS) {
			endMove();
		}
	}

	private Vec3 ground(Vec3 at) {
		int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
		if (Math.abs(y - at.y) > 6) {
			y = Mth.floor(at.y);
		}
		return new Vec3(at.x, y, at.z);
	}

	/** A dust ring of {@code n} points, radius {@code r}, on the ground at {@code c}. */
	static void ring(ServerLevel level, Vec3 c, double r, DustParticleOptions dust, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("StormHome", NbtUtils.writeBlockPos(home));
		}
		tag.putBoolean("TrialBound", trialBound);
		tag.putByte("Phase", (byte) phase());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "StormHome").orElse(null));
		trialBound = tag.getBoolean("TrialBound");
		if (tag.getByte("Phase") >= 2) {
			entityData.set(DATA_PHASE, (byte) 2);
			getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.30);
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::predicate));
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop("animation.destroyer." + name);
	}

	private static RawAnimation once(String name) {
		return RawAnimation.begin().thenPlayAndHold("animation.destroyer." + name);
	}

	private PlayState predicate(AnimationState<DestroyerBoss> state) {
		if (isDeadOrDying()) {
			return state.setAndContinue(once("death"));
		}
		boolean two = phase() >= 2;
		switch (move()) {
			case MOVE_BEAM -> {
				return state.setAndContinue(once(two ? "beam_wide" : "beam"));
			}
			case MOVE_BLAST -> {
				return state.setAndContinue(once(two ? "blast_double" : "blast"));
			}
			case MOVE_CHARGE -> {
				return state.setAndContinue(once("charge"));
			}
			case MOVE_SLAM -> {
				return state.setAndContinue(once(two ? "slam_double" : "slam"));
			}
			case MOVE_BACKHAND -> {
				return state.setAndContinue(once("backhand"));
			}
			case MOVE_SURGE -> {
				return state.setAndContinue(once("surge"));
			}
			case MOVE_FORM -> {
				return state.setAndContinue(once("form"));
			}
			default -> {
			}
		}
		if (state.isMoving() || state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(loop("walk"));
		}
		return state.setAndContinue(loop("idle"));
	}

	/** Every move as one goal above the melee, so they never overlap. */
	static final class MovesGoal extends Goal {
		private final DestroyerBoss mob;

		MovesGoal(DestroyerBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move() != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.gap > 0 || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.slamCd <= 0 && d <= SLAM_RADIUS - 1) {
				mob.startSlam();
				return true;
			}
			if (mob.beamCd <= 0 && d >= 5 && d <= BEAM_RANGE - 2 && sees) {
				mob.startBeam();
				return true;
			}
			if (mob.chargeCd <= 0 && d >= 6 && d <= 18 && sees && mob.random.nextInt(2) == 0) {
				mob.startCharge();
				return true;
			}
			if (mob.blastCd <= 0 && d >= 5 && d <= BLAST_RANGE && sees) {
				mob.startBlast();
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.move() != MOVE_NONE && mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level && mob.move() != MOVE_SURGE && mob.move() != MOVE_FORM) {
				mob.tickMove(level);
			}
		}

		@Override
		public void stop() {
			if (mob.move() != MOVE_NONE && mob.move() != MOVE_SURGE && mob.move() != MOVE_FORM) {
				mob.endMove();
			}
		}
	}
}

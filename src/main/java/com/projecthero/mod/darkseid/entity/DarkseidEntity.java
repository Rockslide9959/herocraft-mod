package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidDamage;
import com.projecthero.mod.darkseid.DarkseidFx;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;
import com.projecthero.mod.event.EventBossBar;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * DARKSEID -- LORD OF APOKOLIPS. The Darkseid Raid's boss: a 4.2-block GeckoLib titan.
 *
 * <p>Responsibilities are split three ways on purpose:
 * <ul>
 *   <li>{@link DarkseidRaid} (the raid manager) owns everything about the <em>encounter</em>: when he arrives,
 *       his shield (the Mother Boxes), which phase he is in, the soft enrage, reinforcements, victory/defeat,
 *       rewards and cleanup. It tells him what phase he is in via {@link #beginPhaseTransition}.</li>
 *   <li>{@link DarkseidCombat} owns every attack: targeting, the weighted attack choice and each move's script.</li>
 *   <li>This class owns the body: lifecycle (entrance, death), incoming damage (shield, stagger), the synced
 *       state the renderer reads, the boss bar and the GeckoLib controllers.</li>
 * </ul>
 * A Darkseid with no raid (a {@code /summon}) runs his own phase thresholds and has no shield, so he can be
 * tested or used on his own. Every tunable is in {@link DarkseidConfig}; see
 * {@code docs/DARKSEID_RAID_REFERENCE.md}.
 */
public class DarkseidEntity extends Monster implements GeoEntity {
	public static final float BASE_WIDTH = 1.5f;
	public static final float BASE_HEIGHT = 4.2f;
	/** Height, in blocks, the geo model is authored at; the renderer stretches it to the real hit-box height. */
	public static final float MODEL_HEIGHT = 2.0f;

	/** 0 = shielded (Mother Box phase), 1-3 = the fight's three phases. */
	private static final EntityDataAccessor<Byte> DATA_PHASE =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.BYTE);
	/** 0..1 -- the Mother Box shield, as the fraction of boxes still powering it. */
	private static final EntityDataAccessor<Float> DATA_SHIELD =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.FLOAT);
	/** A triggered clip owns the pose (stops the idle/walk controller -- the Oathbreaker lesson). */
	private static final EntityDataAccessor<Boolean> DATA_BUSY =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.BOOLEAN);
	/** 0 = normal, 1 = Omega charging (eyes blaze, aura), 2 = Omega Annihilation charging. */
	private static final EntityDataAccessor<Byte> DATA_OMEGA =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.BYTE);
	/** Omega Beam Sweep: current yaw of the beam in degrees, or NaN while no sweep is running. */
	private static final EntityDataAccessor<Float> DATA_SWEEP_YAW =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.FLOAT);
	/** How many sweep beams (1, or 2 opposite each other in phase 3). */
	private static final EntityDataAccessor<Byte> DATA_SWEEP_BEAMS =
			SynchedEntityData.defineId(DarkseidEntity.class, EntityDataSerializers.BYTE);

	private static final DustParticleOptions OMEGA_RED = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.05f), 1.6f);
	private static final DustParticleOptions SHIELD_BLUE = new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 1.3f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final DarkseidCombat combat = new DarkseidCombat(this);

	/** The raid this Darkseid belongs to, or null for a free-standing (summoned) one. */
	private UUID raidId;
	private int entranceTicksLeft;
	private int transitionTicksLeft;
	private int pendingPhase;
	private int deathTicks = -1;
	private int shieldMessageCooldown;
	private int stepCooldown;
	private EventBossBar bossBar;

	public DarkseidEntity(EntityType<? extends DarkseidEntity> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		this.setPersistenceRequired();
		this.refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		DarkseidConfig.Boss cfg = DarkseidConfig.boss();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.baseDarkseidHealth)
				.add(Attributes.MOVEMENT_SPEED, cfg.speedPhase1)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.KNOCKBACK_RESISTANCE, cfg.knockbackResistance)
				.add(Attributes.ATTACK_DAMAGE, cfg.meleeDamage)
				.add(Attributes.STEP_HEIGHT, 1.6)
				.add(Attributes.SAFE_FALL_DISTANCE, 64.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_SHIELD, 0.0f);
		builder.define(DATA_BUSY, false);
		builder.define(DATA_OMEGA, (byte) 0);
		builder.define(DATA_SWEEP_YAW, Float.NaN);
		builder.define(DATA_SWEEP_BEAMS, (byte) 1);
	}

	// ---------------------------------------------------------------- raid link

	public void bindToRaid(UUID raidId) {
		this.raidId = raidId;
	}

	public UUID raidId() {
		return raidId;
	}

	/** The raid this Darkseid belongs to, if it is still running. */
	public DarkseidRaid raid() {
		if (raidId == null || !(level() instanceof ServerLevel server)) {
			return null;
		}
		return DarkseidRaid.find(server, raidId);
	}

	/** Scale max health to {@code participants}, keeping the current health fraction (roster growth). */
	public void scaleHealthTo(double maxHealth) {
		var attr = getAttribute(Attributes.MAX_HEALTH);
		if (attr == null) {
			return;
		}
		float fraction = getMaxHealth() > 0 ? getHealth() / getMaxHealth() : 1.0f;
		attr.setBaseValue(maxHealth);
		setHealth((float) (maxHealth * fraction));
	}

	// ---------------------------------------------------------------- phases / shield

	public int getPhase() {
		return entityData.get(DATA_PHASE);
	}

	/** Phase set without a transition (spawning shielded, reloading). */
	public void setPhaseImmediate(int phase) {
		entityData.set(DATA_PHASE, (byte) phase);
		applyPhaseStats(phase);
	}

	public float shield() {
		return entityData.get(DATA_SHIELD);
	}

	public void setShield(float fraction) {
		float f = Math.max(0.0f, Math.min(1.0f, fraction));
		if (entityData.get(DATA_SHIELD) != f) {
			entityData.set(DATA_SHIELD, f);
		}
	}

	public boolean isShielded() {
		return getPhase() == 0 && shield() > 0.0f;
	}

	private void applyPhaseStats(int phase) {
		DarkseidConfig.Boss cfg = DarkseidConfig.boss();
		var speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double base = phase >= 3 ? cfg.speedPhase3 : phase == 2 ? cfg.speedPhase2 : cfg.speedPhase1;
			DarkseidRaid raid = raid();
			int enrage = raid == null ? 0 : raid.enrageLevel();
			speed.setBaseValue(base * (1.0 + 0.06 * enrage));
		}
	}

	/** Re-read enrage into the speed attribute (called by the raid when the enrage steps up). */
	public void refreshEnrage() {
		applyPhaseStats(getPhase());
	}

	/**
	 * The cinematic step into a new phase: the {@code rage_transition} roar, invulnerable for its length, a
	 * shockwave that throws everyone back at the roar's peak. The phase itself flips at the roar.
	 */
	public void beginPhaseTransition(ServerLevel server, int newPhase) {
		if (newPhase <= getPhase() || deathTicks >= 0) {
			return;
		}
		combat.cancel(server);
		pendingPhase = newPhase;
		if (newPhase == 1) {
			// shield falling: a short stagger-roar, no invulnerability needed
			setPhaseImmediate(1);
			setShield(0.0f);
			triggerAnim("action", DarkseidAnims.RAGE);
			transitionTicksLeft = DarkseidAnims.RAGE_TICKS;
			setInvulnerable(false);
			return;
		}
		transitionTicksLeft = DarkseidAnims.RAGE_TICKS;
		setInvulnerable(true);
		getNavigation().stop();
		triggerAnim("action", DarkseidAnims.RAGE);
		server.playSound(null, blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 4.0f, newPhase == 3 ? 0.5f : 0.6f);
		syncBusy();
	}

	public boolean isTransitioning() {
		return transitionTicksLeft > 0;
	}

	private void tickTransition(ServerLevel server) {
		int elapsed = DarkseidAnims.RAGE_TICKS - transitionTicksLeft;
		transitionTicksLeft--;
		getNavigation().stop();
		if (tickCount % 3 == 0) {
			server.sendParticles(OMEGA_RED, getX(), getY() + getBbHeight() * 0.5, getZ(), 6,
					getBbWidth() * 0.6, getBbHeight() * 0.3, getBbWidth() * 0.6, 0.0);
		}
		if (elapsed == DarkseidAnims.RAGE_ROAR) {
			if (pendingPhase > getPhase()) {
				setPhaseImmediate(pendingPhase);
			}
			Vec3 c = position();
			DarkseidFx.shake(server, c, 96.0, pendingPhase >= 3 ? 1.6f : 1.0f, 30);
			DarkseidFx.ring(server, ParticleTypes.EXPLOSION, c.add(0, 0.3, 0), 4.0, 12);
			DarkseidFx.ring(server, OMEGA_RED, c.add(0, 0.5, 0), 7.0, 40);
			server.sendParticles(ParticleTypes.FLASH, getX(), getY() + getBbHeight() * 0.7, getZ(), 1, 0, 0, 0, 0);
			server.playSound(null, blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 4.0f, 0.45f);
			if (pendingPhase >= 2) {
				// the roar itself throws back anyone close -- a clear "back off" cue
				for (Player p : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(9.0, 5.0, 9.0),
						DarkseidDamage::isValidVictim)) {
					DarkseidDamage.knockAway(p, position(), 1.6, 0.6);
				}
			}
		}
		if (transitionTicksLeft <= 0) {
			transitionTicksLeft = 0;
			setInvulnerable(false);
		}
	}

	// ---------------------------------------------------------------- entrance

	/** The raid's entrance cinematic: he steps out of the Boom Tube, invulnerable and inert, then wakes. */
	public void beginEntrance() {
		entranceTicksLeft = DarkseidAnims.ENTRANCE_TICKS;
		setInvulnerable(true);
		setNoAi(true);
		triggerAnim("action", DarkseidAnims.ENTRANCE);
	}

	public boolean isEntering() {
		return entranceTicksLeft > 0;
	}

	private void tickEntrance(ServerLevel server) {
		entranceTicksLeft--;
		if (tickCount % 2 == 0) {
			server.sendParticles(OMEGA_RED, getX(), getY() + getBbHeight() * 0.4, getZ(), 4,
					getBbWidth() * 0.5, getBbHeight() * 0.35, getBbWidth() * 0.5, 0.0);
		}
		if (entranceTicksLeft == DarkseidAnims.ENTRANCE_TICKS / 2) {
			server.playSound(null, blockPosition(), DarkseidSounds.STEP, SoundSource.HOSTILE, 3.0f, 0.5f);
			DarkseidFx.shake(server, position(), 96.0, 0.6f, 12);
		}
		if (entranceTicksLeft <= 0) {
			setNoAi(false);
			setInvulnerable(false);
			server.playSound(null, blockPosition(), DarkseidSounds.PHASE, SoundSource.HOSTILE, 4.0f, 0.55f);
			DarkseidFx.shake(server, position(), 96.0, 1.2f, 24);
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void registerGoals() {
		// Movement and every attack are driven by DarkseidCombat; only keep him afloat in water. No vanilla melee
		// goal -- the Titan's v0.9.16 lesson: a leftover MeleeAttackGoal runs alongside a scripted boss's own
		// attacks and is what players end up seeing.
		this.goalSelector.addGoal(0, new FloatGoal(this));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return false; // every hit comes from DarkseidCombat
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (deathTicks >= 0) {
			return;
		}
		if (tickCount % 40 == 0 && !checkRaidAlive(server)) {
			return;
		}
		keepInsideWorld(server);
		if (clipHoldTicks > 0) {
			clipHoldTicks--;
		}
		syncBusy();
		if (shieldMessageCooldown > 0) {
			shieldMessageCooldown--;
		}
		if (entranceTicksLeft > 0) {
			tickEntrance(server);
			updateBossBar(server);
			return;
		}
		ambience(server);
		if (transitionTicksLeft > 0) {
			tickTransition(server);
			updateBossBar(server);
			return;
		}
		if (raidId == null) {
			selfPhaseCheck(server);
		}
		combat.tick(server);
		footsteps(server);
		updateBossBar(server);
	}

	/**
	 * A raid-owned Darkseid whose raid no longer exists (ended while his chunk was unloaded, cancelled by a server
	 * restart) removes himself the next time he ticks -- no orphaned boss can outlive its raid.
	 */
	private boolean checkRaidAlive(ServerLevel server) {
		if (raidId != null && raid() == null) {
			bossBar().clear(server);
			discard();
			return false;
		}
		return true;
	}

	/** A summoned Darkseid (no raid) runs his own 60% / 25% thresholds. */
	private void selfPhaseCheck(ServerLevel server) {
		float f = getHealth() / getMaxHealth();
		if (getPhase() < 2 && f <= 0.60f) {
			beginPhaseTransition(server, 2);
		} else if (getPhase() == 2 && f <= 0.25f) {
			beginPhaseTransition(server, 3);
		}
	}

	/** Never trapped: out of the void, out of a wall. */
	private void keepInsideWorld(ServerLevel server) {
		if (getY() < server.getMinBuildHeight() + 2) {
			DarkseidRaid raid = raid();
			Vec3 to = raid != null ? Vec3.atBottomCenterOf(raid.center()).add(0, 2, 0) : position().add(0, 80, 0);
			teleportTo(to.x, to.y, to.z);
			setDeltaMovement(Vec3.ZERO);
		}
		if (tickCount % 20 == 0 && isInWall()) {
			teleportTo(getX(), getY() + 3.0, getZ());
		}
	}

	private void ambience(ServerLevel server) {
		if (tickCount % 6 == 0) {
			// embers of Apokolips around him in Omega Rage (his eyes glow through the glowmask; sparks at the eyes
			// are kept for the Omega charges, where they are the tell)
			if (getPhase() >= 3) {
				server.sendParticles(ParticleTypes.LAVA, getX(), getY() + getBbHeight() * 0.5, getZ(), 1,
						getBbWidth() * 0.5, getBbHeight() * 0.3, getBbWidth() * 0.5, 0.0);
			}
		}
		// the Mother Box shield -- also (v0.13.19) the partial one that reactivated boxes give him mid-fight, one
		// orbiting mote per active box, so the damage reduction is visible on him and not only on the boss bar
		boolean partial = !isShielded() && getPhase() > 0 && shield() > 0.0f && !isDeadOrDying();
		if ((isShielded() || partial) && tickCount % 4 == 0) {
			double a = tickCount * 0.25;
			double r = getBbWidth() * 1.3;
			int motes = isShielded() ? 3 : Math.max(1, Math.round(shield() * 4));
			for (int i = 0; i < motes; i++) {
				double ang = a + i * (Math.PI * 2 / motes);
				server.sendParticles(SHIELD_BLUE, getX() + Math.cos(ang) * r, getY() + getBbHeight() * (0.2 + 0.6 * i / Math.max(1, motes - 1)),
						getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
			}
		}
	}

	private void footsteps(ServerLevel server) {
		if (stepCooldown > 0) {
			stepCooldown--;
			return;
		}
		if (onGround() && getDeltaMovement().horizontalDistanceSqr() > 0.002) {
			server.playSound(null, blockPosition(), DarkseidSounds.STEP, SoundSource.HOSTILE, 1.6f, 0.7f + random.nextFloat() * 0.1f);
			stepCooldown = getDeltaMovement().horizontalDistanceSqr() > 0.04 ? 7 : 11;
		}
	}

	public Vec3 eyePosition() {
		return position().add(forward().scale(getBbWidth() * 0.3)).add(0, getBbHeight() * 0.9, 0);
	}

	public Vec3 forward() {
		float yaw = yBodyRot * ((float) Math.PI / 180.0f);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	// ---------------------------------------------------------------- busy / omega / sweep sync

	boolean isBusyServer() {
		return combat.isAttacking() || transitionTicksLeft > 0 || entranceTicksLeft > 0 || deathTicks >= 0
				|| combat.isStaggered() || combat.isFlinching() || clipHoldTicks > 0;
	}

	/** Ticks a directly-played clip ({@link #playClip}) keeps the idle/walk controller stood down. */
	private int clipHoldTicks;

	/**
	 * Play one of his clips for {@code ticks}, outside any attack (debug tools / the visual test harness). Without
	 * holding the busy flag the looping idle controller would override it, exactly as it would any triggered clip.
	 */
	public void playClip(String clip, int ticks) {
		clipHoldTicks = ticks;
		triggerAnim("action", clip);
		syncBusy();
	}

	private void syncBusy() {
		boolean busy = isBusyServer();
		if (entityData.get(DATA_BUSY) != busy) {
			entityData.set(DATA_BUSY, busy);
		}
	}

	public int omegaState() {
		return entityData.get(DATA_OMEGA);
	}

	void setOmegaState(int state) {
		if (entityData.get(DATA_OMEGA) != (byte) state) {
			entityData.set(DATA_OMEGA, (byte) state);
		}
	}

	public float sweepYaw() {
		return entityData.get(DATA_SWEEP_YAW);
	}

	public int sweepBeams() {
		return entityData.get(DATA_SWEEP_BEAMS);
	}

	void setSweep(float yaw, int beams) {
		entityData.set(DATA_SWEEP_YAW, yaw);
		if (entityData.get(DATA_SWEEP_BEAMS) != (byte) beams) {
			entityData.set(DATA_SWEEP_BEAMS, (byte) beams);
		}
	}

	/** While the Omega Beam Sweep runs, the beam reaches far past his body -- keep him drawn whenever it could be seen. */
	@Override
	public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
		AABB box = super.getBoundingBoxForCulling();
		return Float.isNaN(sweepYaw()) ? box : box.inflate(DarkseidConfig.abilities().omegaSweepLength, 1.0,
				DarkseidConfig.abilities().omegaSweepLength);
	}

	public DarkseidCombat combat() {
		return combat;
	}

	// ---------------------------------------------------------------- incoming damage

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL)
				|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.CACTUS)
				|| source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.FREEZE)) {
			return true;
		}
		if (source.getEntity() instanceof ParademonEntity || source.getEntity() == this) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	/**
	 * The Mother Box shield (total immunity while any box still powers it in the Mother Box phase; a damage
	 * reduction per reactivated box once the soft enrage brings them back), the stagger vulnerability, and the
	 * bookkeeping {@link DarkseidCombat} needs (threat, Omega Annihilation interruption, Grip break).
	 */
	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!(level() instanceof ServerLevel server)) {
			return super.hurt(source, amount);
		}
		if (deathTicks >= 0 || isInvulnerableTo(source)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurt(source, amount);
		}
		if (isShielded()) {
			deflectOffShield(server, source);
			return false;
		}
		float dealt = amount;
		float shield = shield();
		if (shield > 0.0f) {
			dealt *= Math.max(0.0f, 1.0f - shield); // enrage-reactivated boxes
		}
		if (combat.isStaggered()) {
			dealt *= (float) DarkseidConfig.boss().staggerDamageTakenMultiplier;
		}
		float before = getHealth();
		boolean landed = super.hurt(source, dealt);
		if (landed) {
			float taken = Math.max(0.0f, before - getHealth());
			combat.noteDamageTaken(server, source, taken);
		}
		return landed;
	}

	private void deflectOffShield(ServerLevel server, DamageSource source) {
		Entity attacker = source.getEntity();
		Vec3 at = attacker != null ? attacker.position().add(0, attacker.getBbHeight() * 0.5, 0).subtract(position())
				.normalize().scale(getBbWidth() * 0.9).add(position()).add(0, getBbHeight() * 0.5, 0)
				: position().add(0, getBbHeight() * 0.5, 0);
		server.sendParticles(SHIELD_BLUE, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
		if (shieldMessageCooldown <= 0) {
			shieldMessageCooldown = 20;
			server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 0.6f);
			if (attacker instanceof ServerPlayer player) {
				player.displayClientMessage(Component.translatable("message.projecthero.darkseid.shielded",
						Math.round(shield() * 100)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		super.actuallyHurt(source, amount);
		if (level() instanceof ServerLevel server && !combat.isAttacking() && !combat.isStaggered()
				&& transitionTicksLeft <= 0 && random.nextInt(7) == 0) {
			triggerAnim("action", DarkseidAnims.HURT);
			combat.flinch(DarkseidAnims.HURT_TICKS);
			updateBossBar(server);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		float s = (float) Math.max(0.5, Math.min(3.0, DarkseidConfig.boss().modelScale));
		return EntityDimensions.scalable(BASE_WIDTH * s, BASE_HEIGHT * s);
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return DarkseidSounds.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return DarkseidSounds.DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 2.5f;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 400;
	}

	// ---------------------------------------------------------------- boss bar

	private EventBossBar bossBar() {
		if (bossBar == null) {
			bossBar = new EventBossBar(getUUID(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_20, true);
		}
		return bossBar;
	}

	private void updateBossBar(ServerLevel server) {
		if (isRemoved() || deathTicks >= 0) {
			return;
		}
		Component name;
		BossEvent.BossBarColor color;
		if (isEntering()) {
			name = Component.translatable("boss.projecthero.darkseid.arriving");
			color = BossEvent.BossBarColor.PURPLE;
		} else if (isShielded()) {
			name = Component.translatable("boss.projecthero.darkseid.shielded", Math.round(shield() * 100));
			color = BossEvent.BossBarColor.PURPLE;
		} else if (combat.isChargingAnnihilation()) {
			name = Component.translatable("boss.projecthero.darkseid.annihilation",
					Math.round(combat.annihilationInterruptProgress() * 100));
			color = BossEvent.BossBarColor.WHITE;
		} else if (combat.isStaggered()) {
			name = Component.translatable("boss.projecthero.darkseid.staggered");
			color = BossEvent.BossBarColor.YELLOW;
		} else if (shield() > 0.0f) {
			name = Component.translatable("boss.projecthero.darkseid.reshielded", Math.round(shield() * 100));
			color = BossEvent.BossBarColor.PINK;
		} else {
			name = Component.translatable("boss.projecthero.darkseid");
			color = BossEvent.BossBarColor.RED;
		}
		DarkseidRaid raid = raid();
		double radius = raid != null ? raid.radius() + 32.0 : 96.0;
		bossBar().setColor(color);
		bossBar().update(server, raid != null ? raid.center() : blockPosition(), radius,
				name.copy().withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), getHealth() / getMaxHealth());
	}

	/** Explicitly drop the bar from every client (also called by the raid on cleanup). */
	public void clearBossBar(ServerLevel server) {
		bossBar().clear(server);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server && reason.shouldDestroy()) {
			bossBar().clear(server);
			combat.cancel(server);
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------- death

	/**
	 * Keeps vanilla's death bookkeeping (kill credit, XP, the dead flag -- the Oathbreaker's v0.14.0 lesson) but
	 * replaces the 20-tick tip-over with a 5.5 s sequence: staggered, down on one knee, the Omega energy tearing
	 * loose, eyes flaring, a burst, then a Boom Tube opens behind him and he is gone. The raid is told at once so
	 * the arena calms (Parademons recalled) while the sequence plays; its rewards follow once it ends.
	 */
	@Override
	public void die(DamageSource source) {
		if (deathTicks >= 0) {
			return;
		}
		deathTicks = 0;
		if (level() instanceof ServerLevel server) {
			combat.cancel(server);
		}
		transitionTicksLeft = 0;
		super.die(source);
		setTarget(null);
		setDeltaMovement(Vec3.ZERO);
		setNoAi(true);
		triggerAnim("action", DarkseidAnims.DEATH);
		if (level() instanceof ServerLevel server) {
			bossBar().clear(server);
			server.playSound(null, blockPosition(), DarkseidSounds.DEATH, SoundSource.HOSTILE, 4.0f, 0.55f);
			DarkseidFx.shake(server, position(), 96.0, 1.0f, 20);
			DarkseidRaid raid = raid();
			if (raid != null) {
				raid.onDarkseidDefeated(server, this, source);
			}
		}
	}

	@Override
	protected void tickDeath() {
		++this.deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Vec3 mid = position().add(0, getBbHeight() * 0.45, 0);
		if (deathTime < DarkseidAnims.DEATH_BURST && deathTime > 20 && deathTime % 2 == 0) {
			// the Omega energy destabilising -- arcs and embers tearing off him, thickening toward the burst
			float k = (deathTime - 20) / (float) (DarkseidAnims.DEATH_BURST - 20);
			server.sendParticles(OMEGA_RED, mid.x, mid.y, mid.z, 2 + (int) (k * 10), getBbWidth() * 0.6, getBbHeight() * 0.35,
					getBbWidth() * 0.6, 0.0);
			if (deathTime % 6 == 0) {
				server.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 6, getBbWidth() * 0.5,
						getBbHeight() * 0.3, getBbWidth() * 0.5, 0.3);
				server.playSound(null, blockPosition(), DarkseidSounds.OMEGA_CHARGE, SoundSource.HOSTILE, 1.2f, 0.6f + k * 0.8f);
			}
		}
		if (deathTime == DarkseidAnims.DEATH_BURST - 8) {
			Vec3 eyes = eyePosition();
			server.sendParticles(ParticleTypes.FLASH, eyes.x, eyes.y, eyes.z, 1, 0, 0, 0, 0);
		}
		if (deathTime == DarkseidAnims.DEATH_BURST) {
			server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 2, 0.5, 0.5, 0.5, 0.0);
			server.sendParticles(OMEGA_RED, mid.x, mid.y, mid.z, 120, 3.0, 2.0, 3.0, 0.0);
			server.sendParticles(ParticleTypes.FLASH, mid.x, mid.y, mid.z, 2, 0.5, 0.5, 0.5, 0);
			server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.5f);
			server.playSound(null, blockPosition(), DarkseidSounds.OMEGA_FIRE, SoundSource.HOSTILE, 4.0f, 0.5f);
			DarkseidFx.shake(server, position(), 96.0, 2.0f, 30);
			DarkseidFx.ring(server, ParticleTypes.CLOUD, position().add(0, 0.3, 0), 6.0, 30);
		}
		if (deathTime == DarkseidAnims.DEATH_BOOM_TUBE) {
			Vec3 behind = position().subtract(forward().scale(2.2)).add(0, getBbHeight() * 0.5, 0);
			BoomTubeEntity.open(server, behind, BoomTubeEntity.Kind.EXIT, 4.5f, 60, raidId);
		}
		if (deathTime >= DarkseidAnims.DEATH_TICKS && !isRemoved()) {
			server.sendParticles(ParticleTypes.END_ROD, mid.x, mid.y, mid.z, 40, getBbWidth() * 0.5, getBbHeight() * 0.4,
					getBbWidth() * 0.5, 0.15);
			server.playSound(null, blockPosition(), DarkseidSounds.BOOM_TUBE, SoundSource.HOSTILE, 4.0f, 0.8f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	/** 1 while alive; fades to 0 through the Boom Tube at the end of the death sequence (client + server). */
	public float deathAlpha(float partialTick) {
		if (deathTime <= 0) {
			return 1.0f;
		}
		float t = deathTime + partialTick - DarkseidAnims.DEATH_FADE_START;
		float span = DarkseidAnims.DEATH_TICKS - DarkseidAnims.DEATH_FADE_START;
		return Math.max(0.0f, Math.min(1.0f, 1.0f - t / span));
	}

	// ---------------------------------------------------------------- save

	private static final String TAG_RAID = "DarkseidRaid";
	private static final String TAG_PHASE = "DarkseidPhase";
	private static final String TAG_SHIELD = "DarkseidShield";

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (raidId != null) {
			tag.putUUID(TAG_RAID, raidId);
		}
		tag.putByte(TAG_PHASE, (byte) getPhase());
		tag.putFloat(TAG_SHIELD, shield());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Entrance, transitions and staggers run on flags vanilla saves (NoAI, Invulnerable) while their timers are
		// not -- a reload mid-roar must not leave a frozen, invulnerable god. None of them survives a reload.
		setNoAi(false);
		setInvulnerable(false);
		setNoGravity(false);
		raidId = tag.hasUUID(TAG_RAID) ? tag.getUUID(TAG_RAID) : null;
		if (tag.contains(TAG_PHASE)) {
			setPhaseImmediate(Math.max(0, Math.min(3, tag.getByte(TAG_PHASE))));
		}
		setShield(tag.getFloat(TAG_SHIELD));
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<DarkseidEntity> action = new AnimationController<>(this, "action", 2, state -> PlayState.STOP);
		for (String name : DarkseidAnims.ACTION_CLIPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.darkseid." + name));
		}
		for (String name : DarkseidAnims.ACTION_HOLDS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.darkseid." + name));
		}
		for (String name : DarkseidAnims.ACTION_LOOPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenLoop("animation.darkseid." + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 4, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<DarkseidEntity> state) {
		if (isDeadOrDying() || entityData.get(DATA_BUSY)) {
			return PlayState.STOP;
		}
		if (state.getLimbSwingAmount() > 0.04f) {
			return state.setAndContinue(RawAnimation.begin().thenLoop(getPhase() >= 3 || state.getLimbSwingAmount() > 0.5f
					? "animation.darkseid.run" : "animation.darkseid.walk"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop("animation.darkseid.idle"));
	}

}

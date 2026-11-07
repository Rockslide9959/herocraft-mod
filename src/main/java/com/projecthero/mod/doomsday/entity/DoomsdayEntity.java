package com.projecthero.mod.doomsday.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.joml.Vector3f;

import com.projecthero.mod.doomsday.DoomsdayAdaptation;
import com.projecthero.mod.doomsday.DoomsdayAdaptation.Bucket;
import com.projecthero.mod.doomsday.DoomsdayConfig;
import com.projecthero.mod.doomsday.DoomsdayEvent;
import com.projecthero.mod.doomsday.DoomsdayFx;
import com.projecthero.mod.doomsday.DoomsdayItems;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: <b>Doomsday</b> -- the bred-to-kill monster of prehistoric Krypton, as strong as Galactus. The player rig in
 * the user's chosen HD skin (NovaSkin "Batman The Devastator"), x2.4 and growing every life, bone spikes and all.
 *
 * <h2>Lives, death and the corpse</h2>
 * He never really dies. Health reaching 0 drops him as a <b>corpse</b> ({@link #ACTION_CORPSE}): untouchable, slowly
 * reviving -- {@link DoomsdayConfig.Revival#daySeconds} in sunlight, {@link DoomsdayConfig.Revival#darkSeconds} at night
 * or under a roof (the comics: he revives slower away from the sun). He rises for the next life bigger, with longer
 * spikes and a fuller kit. After his third death the Phantom Zone Projector can banish him ({@link #banish}) while he
 * lies there; miss that window and he rises <b>enraged</b> (a 4th life, and so on).
 *
 * <h2>Adaptation</h2>
 * {@link DoomsdayAdaptation}: he learns every damage type that hurts him, and is immune to whatever landed the killing
 * blow. The synced {@link #adaptMask()} drives the stone flecks on his skin.
 *
 * <h2>The kit</h2>
 * Life 1 he is still <b>bound</b> (one arm shackled in his Calatonian burial cables): Hammer Blows, Cable Guard, the
 * Doomsday Leap, Earthshatter, Grab & Slam and the Roar. At two thirds of that life the cables tear. Life 2 adds Bone
 * Eruption, Bone Volley and Heat Vision; life 3 Super Breath and the super-speed Blitz. Aggro is the shared
 * {@link BossThreat} table; a Kryptonian-power fighter draws extra threat (he hunts Kryptonians).
 */
public class DoomsdayEntity extends Monster {
	public static final byte ACTION_NONE = 0, ACTION_HAMMER = 1, ACTION_GUARD = 2, ACTION_LEAP_CROUCH = 3, ACTION_LEAP_AIR = 4,
			ACTION_LAND = 5, ACTION_SHATTER = 6, ACTION_ERUPT = 7, ACTION_VOLLEY = 8, ACTION_HEAT = 9, ACTION_GRAB = 10,
			ACTION_BREATH = 11, ACTION_BLITZ = 12, ACTION_ROAR = 13, ACTION_BREAK_FREE = 14, ACTION_CORPSE = 15, ACTION_RISE = 16,
			ACTION_BANISH = 17, ACTION_EMERGE = 18, ACTION_STAGGER = 19,
			// v0.17.2 round 2 (user: "add more attacks and animations to doomsday please")
			ACTION_RAMPAGE = 20, ACTION_RAMPAGE_CRASH = 21, ACTION_CLAP = 22, ACTION_SKYFALL = 23, ACTION_SKYFALL_LAND = 24, ACTION_HURL = 25,
			ACTION_NOVA = 26;
	/** Round-2 move timings (ticks from the move starting) the renderer mirrors. */
	public static final int RAMPAGE_WINDUP = 18, RAMPAGE_RUN = 22, CRASH_TICKS = 34, CLAP_AT = 16, SKY_CROUCH = 18, SKY_RISE = 14, SKY_HANG = 22,
			SKY_LAND_TICKS = 26, HURL_RIP = 14, HURL_LIFT = 26, HURL_THROW = 34, NOVA_AT = 32;
	/** Move timings the renderer mirrors (ticks from the move starting). */
	public static final int LEAP_CROUCH = 16, SHATTER_SLAM = 20, ERUPT_BURST = 16, VOLLEY_THROW = 14, HEAT_CHARGE = 20, HEAT_END = 60,
			BREATH_INHALE = 20, BLITZ_GO = 10, ROAR_AT = 10, BREAK_AT = 20, RISE_TICKS = 40, BANISH_TICKS = 70, EMERGE_TICKS = 50;

	private static final EntityDataAccessor<Byte> DATA_ACTION = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_BOUND = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_ADAPT = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Vector3f> DATA_BEAM = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.VECTOR3);
	private static final EntityDataAccessor<Float> DATA_REVIVE = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.FLOAT);
	/** Adaptive Nova: the bucket he is channelling (its ordinal), -1 when none -- the renderer's glow colour. */
	private static final EntityDataAccessor<Integer> DATA_ELEMENT = SynchedEntityData.defineId(DoomsdayEntity.class, EntityDataSerializers.INT);

	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.doomsday"),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final DoomsdayAdaptation adaptation = new DoomsdayAdaptation();
	private UUID eventId;
	private int deaths;
	/** v0.17.2 (user: "i want to nerf doomsday a bit, make him slightly more aggressive but also give him a staggered stance where he takes 300% more damage, make the staggered stance last 15 seconds please"): this life's stagger has happened. */
	private boolean staggeredThisLife;
	/** Corpse: revival progress (ticks of sunlight-equivalent) and what it needs. */
	private double reviveProgress;
	private double reviveNeeded;
	/** True after the third (or a later enraged) death: the Projector works on this corpse. */
	private boolean finalDeath;
	private int stateTicks;
	private boolean pendingDeath;
	private boolean allowDeath;
	private Bucket lastBucket;
	private boolean guarding;
	private long lastProjectileHit = -1000;
	private UUID banisher;
	private int lonelyTicks;
	/** Set from registerGoals (called by the Mob constructor, so deliberately no initialiser here). */
	private Brain brain;
	private final BossThreat threat = new BossThreat(this).range(96.0).holdWhile(() -> brain != null && brain.midMove() || helpless());
	/** Client: the tickCount when the synced action last changed (drives the poses). */
	public int actionStartTick;

	public DoomsdayEntity(EntityType<? extends DoomsdayEntity> type, Level level) {
		super(type, level);
		this.xpReward = 1000;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		DoomsdayConfig.Boss b = DoomsdayConfig.boss();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, b.health)
				.add(Attributes.MOVEMENT_SPEED, b.speed)
				.add(Attributes.ATTACK_DAMAGE, DoomsdayConfig.moves().hammerDamage)
				.add(Attributes.ARMOR, b.armor)
				.add(Attributes.ARMOR_TOUGHNESS, b.toughness)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.STEP_HEIGHT, 2.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 256.0)
				.add(Attributes.SCALE, b.baseScale);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ACTION, ACTION_NONE);
		builder.define(DATA_LIFE, 1);
		builder.define(DATA_BOUND, true);
		builder.define(DATA_ADAPT, 0);
		builder.define(DATA_BEAM, new Vector3f());
		builder.define(DATA_REVIVE, 0f);
		builder.define(DATA_ELEMENT, -1);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_ACTION.equals(key)) {
			actionStartTick = tickCount;
		}
	}

	// ---------------------------------------------------------------- state

	public byte action() {
		return entityData.get(DATA_ACTION);
	}

	public void setAction(byte a) {
		if (entityData.get(DATA_ACTION) != a) {
			entityData.set(DATA_ACTION, a);
		}
		stateTicks = 0;
	}

	public int life() {
		return entityData.get(DATA_LIFE);
	}

	public boolean bound() {
		return entityData.get(DATA_BOUND);
	}

	public int adaptMask() {
		return entityData.get(DATA_ADAPT);
	}

	public Vec3 beamTarget() {
		Vector3f v = entityData.get(DATA_BEAM);
		return new Vec3(v.x, v.y, v.z);
	}

	/** The Adaptive Nova's element (null when he isn't channelling one). */
	public Bucket novaElement() {
		int i = entityData.get(DATA_ELEMENT);
		return i < 0 || i >= DoomsdayAdaptation.BUCKETS.length ? null : DoomsdayAdaptation.BUCKETS[i];
	}

	/** The bucket he has adapted to the most (immunities first), or null -- what the Adaptive Nova unleashes. */
	public Bucket strongestAdaptation() {
		List<Bucket> learned = adaptation.learned();
		return learned.isEmpty() ? null : learned.get(0);
	}

	/** Client: the adaptation mask last seen and when it changed (the shudder animation). */
	public int clientAdaptMask;
	public float clientAdaptAt = -1000f;

	/** True while he reels from a Rampage that ran into something he couldn't break. */
	public boolean crashed() {
		return action() == ACTION_RAMPAGE_CRASH;
	}

	/** 0..1 how far the corpse has come back (client: the twitching gets stronger). */
	public float reviveFraction() {
		return entityData.get(DATA_REVIVE);
	}

	public DoomsdayAdaptation adaptation() {
		return adaptation;
	}

	public BossThreat threat() {
		return threat;
	}

	public int deaths() {
		return deaths;
	}

	public boolean isCorpse() {
		return action() == ACTION_CORPSE;
	}

	public boolean finalDeath() {
		return finalDeath;
	}

	public boolean guarding() {
		return guarding;
	}

	/** Lying dead, rising, emerging or being banished: no moves, nothing hurts him. */
	public boolean helpless() {
		byte a = action();
		return a == ACTION_CORPSE || a == ACTION_RISE || a == ACTION_BANISH || a == ACTION_EMERGE;
	}

	public void bindToEvent(UUID id) {
		this.eventId = id;
	}

	public UUID eventId() {
		return eventId;
	}

	private DoomsdayEvent event(ServerLevel level) {
		return DoomsdayEvent.find(level, eventId);
	}

	/** Health for {@code fighters} at the current life (called when he arrives and on every revival). */
	public void configure(int fighters) {
		double hp = life() > 3 ? DoomsdayConfig.boss().enragedHealth : DoomsdayConfig.lifeHealth(fighters);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		super.setHealth((float) hp);
		applyScale();
	}

	private void applyScale() {
		DoomsdayConfig.Boss b = DoomsdayConfig.boss();
		getAttribute(Attributes.SCALE).setBaseValue(b.baseScale + b.scalePerLife * Math.min(4, life() - 1));
	}

	/** Called by the casket: he tears his way out (untouchable for a moment). */
	public void emerge() {
		setAction(ACTION_EMERGE);
	}

	private void syncAdapt() {
		entityData.set(DATA_ADAPT, adaptation.mask());
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide) {
			return false;
		}
		ServerLevel level = (ServerLevel) level();
		if (helpless()) {
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && action() != ACTION_BANISH) {
				allowDeath = true;
				return super.hurt(source, amount);
			}
			if (isCorpse() && tickCount % 4 == 0) {
				level.sendParticles(DoomsdayFx.ASH, getX(), getY() + 0.5, getZ(), 4, 0.6, 0.2, 0.6, 0.0);
			}
			return false;
		}
		// v0.17.2 (user: "make it deal 2 times damage to kryptonian beings(this includes doomsday please), should completely bypass their
		// defences"): a kryptonite tool's hit comes back as a kryptonite strike at x2 -- armour off, no adaptation, no Cable Guard
		if (com.projecthero.mod.kryptonian.KryptoniteStrikes.isToolHit(source)) {
			boolean landed = hurt(com.projecthero.mod.kryptonian.KryptoniteStrikes.strike(level, source.getEntity()), amount * com.projecthero.mod.kryptonian.KryptoniteStrikes.MULT);
			if (landed) {
				com.projecthero.mod.kryptonian.KryptoniteStrikes.onStrike(this, source.getEntity());
			}
			return landed;
		}
		Bucket bucket = DoomsdayAdaptation.classify(source, this);
		if (bucket == Bucket.PROJECTILE) {
			lastProjectileHit = level.getGameTime();
		}
		if (guarding && (bucket == Bucket.PROJECTILE || bucket == Bucket.ENERGY)) {
			// Cable Guard: his burial cables whirl and swat it away
			level.sendParticles(DoomsdayFx.CABLE, getX(), getY() + getBbHeight() * 0.6, getZ(), 8, 0.8, 0.8, 0.8, 0.1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_HIT, SoundSource.HOSTILE, 1.4f, 0.6f);
			return false;
		}
		if (guarding && bucket == Bucket.MELEE) {
			amount *= 0.5f;
		}
		float mult = adaptation.multiplier(bucket);
		if (mult <= 0f) {
			adaptedFeedback(level, source, bucket, true);
			return false;
		}
		amount *= mult;
		// v0.17.2: staggered, everything lands at +300% -- and none of it teaches him anything
		boolean staggered = action() == ACTION_STAGGER;
		if (staggered) {
			amount *= (float) DoomsdayConfig.boss().staggerDamageMultiplier;
		}
		if (bucket == null && DoomsdayAdaptation.isKryptonite(source) && !source.is(com.projecthero.mod.kryptonian.KryptoniteStrikes.STRIKE)) {
			amount *= (float) DoomsdayConfig.boss().kryptoniteMultiplier;
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() * 0.6, getZ(), 6,
					0.5, 0.5, 0.5, 0.0);
		}
		if (brain != null) {
			brain.noteDamage(source, amount);
		}
		float before = getHealth();
		lastBucket = bucket;
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			float dealt = pendingDeath ? before : Math.max(0f, before - getHealth());
			if (!staggered && !com.projecthero.mod.kryptonian.KryptoniteStrikes.suppressed(this) && adaptation.record(bucket, dealt, getMaxHealth())) {
				syncAdapt();
				onLearned(level, bucket);
			}
			float threatAmount = amount;
			if (source.getEntity() instanceof Player p && com.projecthero.mod.kryptonian.Kryptonian.hasPower(p)) {
				threatAmount *= (float) DoomsdayConfig.boss().kryptonianThreat;
			}
			threat.record(source, threatAmount);
			if (life() == 1 && bound() && !pendingDeath && getHealth() <= getMaxHealth() * 0.66f && brain != null) {
				brain.force(Brain.Move.BREAK_FREE);
			} else if (!staggeredThisLife && !pendingDeath && !bound() && brain != null
					&& getHealth() <= getMaxHealth() * DoomsdayConfig.boss().staggerAt && brain.force(Brain.Move.STAGGER)) {
				staggeredThisLife = true;
				announce(level, Component.translatable("message.projecthero.doomsday.staggered").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			}
		}
		if (pendingDeath) {
			pendingDeath = false;
			fallDead(level, bucket);
		}
		return hurt;
	}

	private void adaptedFeedback(ServerLevel level, DamageSource source, Bucket bucket, boolean immune) {
		if (tickCount % 3 == 0) {
			level.sendParticles(DoomsdayFx.STONE, getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.6, 0.8, 0.6, 0.1);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_HIT, SoundSource.HOSTILE, 1.4f, 0.5f);
		}
		if (source.getEntity() instanceof ServerPlayer p) {
			p.displayClientMessage(Component.translatable("message.projecthero.doomsday.immune", bucketName(bucket))
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** An action-bar line for everyone near the fight. */
	void announce(ServerLevel level, Component line) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 96 * 96) {
				p.displayClientMessage(line, true);
			}
		}
	}

	/** Gametests: the burial cables gone, as after Break Free. */
	public void debugUnbind() {
		entityData.set(DATA_BOUND, false);
	}

	public boolean staggered() {
		return action() == ACTION_STAGGER;
	}

	private void onLearned(ServerLevel level, Bucket bucket) {
		level.sendParticles(DoomsdayFx.STONE, getX(), getY() + getBbHeight() * 0.5, getZ(), 30, 0.8, 1.2, 0.8, 0.15);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.DEEPSLATE_PLACE, SoundSource.HOSTILE, 2.0f, 0.5f);
		Component line = Component.translatable("message.projecthero.doomsday.adapts", bucketName(bucket),
				(int) Math.round((1f - adaptation.multiplier(bucket)) * 100)).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 96 * 96) {
				p.displayClientMessage(line, true);
			}
		}
	}

	public static MutableComponent bucketName(Bucket b) {
		return Component.translatable("doomsday.projecthero.bucket." + (b == null ? "none" : b.name().toLowerCase(java.util.Locale.ROOT)));
	}

	@Override
	public void setHealth(float health) {
		if (health <= 0f && !allowDeath && level() != null && !level().isClientSide && !helpless()) {
			pendingDeath = true;
			health = 1f;
		}
		super.setHealth(health);
	}

	@Override
	public void kill() {
		allowDeath = true;
		super.kill();
	}

	/** Health reached 0: he drops as a corpse (adapting to what did it), and starts coming back. */
	private void fallDead(ServerLevel level, Bucket killer) {
		deaths++;
		int life = life();
		finalDeath = life >= 3;
		if (adaptation.onDeath(killer)) {
			syncAdapt();
		}
		if (brain != null) {
			brain.cancel();
		}
		getNavigation().stop();
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		setAction(ACTION_CORPSE);
		super.setHealth(1f);
		DoomsdayConfig.Revival r = DoomsdayConfig.revival();
		reviveProgress = 0;
		reviveNeeded = r.daySeconds * 20.0 * (finalDeath ? Math.max(1.0, r.banishWindowMultiplier) : 1.0);
		entityData.set(DATA_REVIVE, 0f);
		entityData.set(DATA_BEAM, new Vector3f());
		guarding = false;
		threat.clear();
		setTarget(null);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 3.0f, 0.4f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
		DoomsdayFx.impact(level, position(), 2.5);
		DoomsdayFx.shake(level, position(), 1.2f, 20, 48);
		if (life <= 3) {
			dropShard(level);
		}
		Component reason = killer == null ? Component.empty()
				: Component.translatable("message.projecthero.doomsday.killed_by", bucketName(killer)).withStyle(ChatFormatting.GRAY);
		DoomsdayEvent ev = event(level);
		if (ev != null) {
			ev.onDoomsdayDown(level, this, life, finalDeath, killer);
		} else {
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(this) < 96 * 96) {
					p.sendSystemMessage(Component.translatable(finalDeath ? "message.projecthero.doomsday.down_final" : "message.projecthero.doomsday.down",
							life).withStyle(ChatFormatting.DARK_RED));
					if (killer != null) {
						p.sendSystemMessage(reason);
					}
				}
			}
		}
	}

	private void dropShard(ServerLevel level) {
		if (DoomsdayItems.PHANTOM_SHARD == null) {
			return;
		}
		ItemEntity item = new ItemEntity(level, getX(), getY() + 1.0, getZ(), new ItemStack(DoomsdayItems.PHANTOM_SHARD));
		item.setUnlimitedLifetime();
		item.setInvulnerable(true);
		item.setDeltaMovement(random.nextGaussian() * 0.15, 0.45, random.nextGaussian() * 0.15);
		item.setGlowingTag(true);
		level.addFreshEntity(item);
		DoomsdayEvent ev = event(level);
		if (ev != null) {
			ev.notePart(item.getUUID());
		}
	}

	/** True in sunlight: daytime, with open sky over his head. */
	public boolean inSunlight() {
		return level().isDay() && level().canSeeSky(blockPosition().above(Math.max(1, (int) getBbHeight())));
	}

	private void tickCorpse(ServerLevel level) {
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
		boolean sun = inSunlight();
		DoomsdayConfig.Revival r = DoomsdayConfig.revival();
		// in the dark he comes back at daySeconds / darkSeconds the speed
		reviveProgress += sun ? 1.0 : r.daySeconds / (double) Math.max(1, r.darkSeconds);
		entityData.set(DATA_REVIVE, (float) Mth.clamp(reviveProgress / Math.max(1, reviveNeeded), 0, 1));
		if (tickCount % 10 == 0) {
			level.sendParticles(DoomsdayFx.ASH, getX(), getY() + 0.4, getZ(), 3, 0.8, 0.2, 0.8, 0.0);
			if (finalDeath) {
				level.sendParticles(DoomsdayFx.PHANTOM, getX(), getY() + 0.6, getZ(), 2, 0.8, 0.3, 0.8, 0.0);
			}
		}
		if (tickCount % 40 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f,
					0.5f + 0.4f * reviveFraction());
		}
		if (reviveProgress >= reviveNeeded) {
			rise(level);
		}
	}

	/** Test / debug hook: revival is (almost) done. */
	public void debugFinishRevival() {
		reviveProgress = Math.max(reviveProgress, reviveNeeded - 1);
	}

	/** Test hook: revival progress / need (ticks). */
	public double[] debugRevival() {
		return new double[] {reviveProgress, reviveNeeded};
	}

	private void rise(ServerLevel level) {
		int next = life() + 1;
		entityData.set(DATA_LIFE, next);
		if (next >= 2) {
			entityData.set(DATA_BOUND, false);
		}
		finalDeath = false;
		DoomsdayEvent ev = event(level);
		int fighters = ev != null ? ev.fighterCount() : Math.max(1, level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(64),
				p -> !p.isSpectator() && !p.isCreative()).size());
		configure(fighters);
		adaptation.newLife();
		staggeredThisLife = false;
		setAction(ACTION_RISE);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.5f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3.0f, 0.6f);
		DoomsdayFx.impact(level, position(), 2.0);
		DoomsdayFx.shake(level, position(), 1.0f, 30, 64);
		if (ev != null) {
			ev.onDoomsdayRevived(level, this, next);
		}
	}

	/** The Phantom Zone Projector, used on his corpse after a final death. True if it took. */
	public boolean banish(ServerLevel level, ServerPlayer by) {
		if (!isCorpse() || !finalDeath) {
			return false;
		}
		banisher = by == null ? null : by.getUUID();
		setAction(ACTION_BANISH);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 3.0f, 1.4f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.6f);
		return true;
	}

	private void tickBanish(ServerLevel level) {
		setNoGravity(true);
		setDeltaMovement(0, 0.02, 0);
		if (stateTicks % 2 == 0) {
			double a = stateTicks * 0.4;
			for (int i = 0; i < 4; i++) {
				double aa = a + i * Math.PI / 2;
				DoomsdayFx.forced(level, DoomsdayFx.PHANTOM, getX() + Math.cos(aa) * 2.5, getY() + 1.5, getZ() + Math.sin(aa) * 2.5, 2, 0.1, 0.4,
						0.1, 0.0);
			}
			DoomsdayFx.forced(level, ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1.5, getZ(), 6, 1.2, 1.2, 1.2, 0.05);
		}
		if (stateTicks == BANISH_TICKS - 10) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 3.0f, 0.4f);
			DoomsdayFx.forced(level, ParticleTypes.FLASH, getX(), getY() + 1.5, getZ(), 1, 0, 0, 0, 0);
		}
		if (stateTicks >= BANISH_TICKS) {
			DoomsdayEvent ev = event(level);
			ServerPlayer by = banisher == null ? null : level.getServer().getPlayerList().getPlayer(banisher);
			bossBar.removeAllPlayers();
			discard();
			if (ev != null) {
				ev.onBanished(level, by);
			} else {
				for (ServerPlayer p : level.players()) {
					if (p.distanceToSqr(this) < 96 * 96) {
						p.sendSystemMessage(Component.translatable("message.projecthero.doomsday.banished").withStyle(ChatFormatting.AQUA));
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide) {
			stateTicks++;
		}
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		if (pendingDeath) {
			pendingDeath = false;
			fallDead(level, lastBucket);
		}
		if (tickCount % 10 == 0) {
			updateBossBar();
		}
		switch (action()) {
			case ACTION_CORPSE -> {
				tickCorpse(level);
				return;
			}
			case ACTION_BANISH -> {
				tickBanish(level);
				return;
			}
			case ACTION_RISE -> {
				getNavigation().stop();
				if (stateTicks >= RISE_TICKS) {
					setAction(ACTION_NONE);
				}
				return;
			}
			case ACTION_EMERGE -> {
				getNavigation().stop();
				if (stateTicks % 5 == 0) {
					DoomsdayFx.impact(level, position(), 1.0);
				}
				if (stateTicks >= EMERGE_TICKS) {
					setAction(ACTION_NONE);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.45f);
				}
				return;
			}
			default -> {
			}
		}
		threat.tick();
		// v0.17.2 round 2: a heavy walk -- every footfall thuds, kicks up dust and shakes the ground a little
		Vec3 mv = getDeltaMovement();
		if (action() == ACTION_NONE && onGround() && mv.x * mv.x + mv.z * mv.z > 0.0016 && tickCount % 11 == 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.6f, 0.55f);
			level.sendParticles(DoomsdayFx.DIRT, getX(), getY() + 0.1, getZ(), 6, getBbWidth() * 0.4, 0.05, getBbWidth() * 0.4, 0.05);
			DoomsdayFx.shake(level, position(), 0.18f, 5, 18);
		}
		if (tickCount % 20 == 0 && getHealth() < getMaxHealth() && !staggered() && !com.projecthero.mod.kryptonian.KryptoniteStrikes.suppressed(this)) {
			DoomsdayConfig.Boss b = DoomsdayConfig.boss();
			heal((float) (inSunlight() ? b.regenSun : b.regenDark));
		}
		if (tickCount % 6 == 0) {
			level.sendParticles(DoomsdayFx.ASH, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, getBbWidth() * 0.4, getBbHeight() * 0.3,
					getBbWidth() * 0.4, 0.0);
		}
		if (tickCount % 20 == 0 && eventId == null) {
			Player near = level.getNearestPlayer(this, 128);
			lonelyTicks = near == null ? lonelyTicks + 20 : 0;
			if (lonelyTicks >= 12000) {
				discard();
			}
		}
	}

	private void updateBossBar() {
		MutableComponent name = Component.translatable("entity.projecthero.doomsday").copy()
				.append(Component.translatable(life() > 3 ? "boss.projecthero.doomsday.life_enraged" : "boss.projecthero.doomsday.life",
						Math.min(3, life())));
		List<Bucket> learned = adaptation.learned();
		if (!learned.isEmpty()) {
			MutableComponent list = Component.empty();
			for (int i = 0; i < Math.min(4, learned.size()); i++) {
				Bucket b = learned.get(i);
				if (i > 0) {
					list.append(", ");
				}
				list.append(bucketName(b));
				list.append(adaptation.immune(b) ? Component.literal(" ✕") : Component.literal(" " + Math.round((1f - adaptation.multiplier(b)) * 100) + "%"));
			}
			name.append(Component.translatable("boss.projecthero.doomsday.adapted", list));
		}
		bossBar.setName(name);
		bossBar.setProgress(isCorpse() ? reviveFraction() : getHealth() / Math.max(1f, getMaxHealth()));
		bossBar.setColor(isCorpse() ? BossEvent.BossBarColor.WHITE : life() > 3 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
	}

	// ---------------------------------------------------------------- shared combat helpers

	public static boolean canTarget(Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isDeadOrDying() || e.isSpectator() || e instanceof ArmorStand
				|| e instanceof DoomsdayEntity) {
			return false;
		}
		return !(e instanceof Player p) || !p.getAbilities().invulnerable;
	}

	public static boolean hit(ServerLevel level, LivingEntity attacker, LivingEntity target, float amount) {
		if (!canTarget(target)) {
			return false;
		}
		DamageSource src = attacker == null ? level.damageSources().magic() : level.damageSources().mobAttack(attacker);
		target.invulnerableTime = 0;
		return target.hurt(src, amount);
	}

	/** May his moves break blocks here? Terrain damage on ({@code abilityTerrainDamage}) and mob griefing on. */
	public static boolean griefing(ServerLevel level) {
		return AbilityHelpers.canGrief() && level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
	}

	/**
	 * Can he smash this block? Only when {@link #griefing} allows it, and only soft blocks: nothing harder than 3 (so
	 * obsidian, ancient debris and the like hold), no fluids, nothing with a block entity (chests, furnaces...).
	 */
	public static boolean mayBreak(ServerLevel level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		if (!griefing(level) || state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() || !level.isLoaded(pos)) {
			return false;
		}
		float h = state.getDestroySpeed(level, pos);
		return h >= 0f && h <= 3.0f;
	}

	private List<LivingEntity> targetsNear(Vec3 at, double radius) {
		return level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), e -> canTarget(e) && e.distanceToSqr(at) <= radius * radius);
	}

	Vec3 eyes() {
		return new Vec3(getX(), getEyeY(), getZ());
	}

	// ---------------------------------------------------------------- vanilla overrides

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		brain = new Brain(this);
		goalSelector.addGoal(2, brain);
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0f));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return false; // every blow is the brain's
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bossBar.removeAllPlayers();
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER) && !effect.is(MobEffects.MOVEMENT_SLOWDOWN)
				&& !effect.is(MobEffects.LEVITATION) && super.canBeAffected(effect);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean canChangeDimensions(Level from, Level to) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return helpless() ? null : SoundEvents.RAVAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.RAVAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WARDEN_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5f;
	}

	@Override
	protected float getSoundVolume() {
		return 2.5f;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 140;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("Life", life());
		tag.putBoolean("Bound", bound());
		tag.putInt("Deaths", deaths);
		tag.putBoolean("Staggered", staggeredThisLife);
		tag.put("Adaptation", adaptation.save());
		tag.putBoolean("Corpse", isCorpse() || action() == ACTION_BANISH);
		tag.putBoolean("Final", finalDeath);
		tag.putDouble("Revive", reviveProgress);
		tag.putDouble("ReviveNeeded", reviveNeeded);
		if (eventId != null) {
			tag.putUUID("Event", eventId);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("Life")) {
			entityData.set(DATA_LIFE, Math.max(1, tag.getInt("Life")));
		}
		entityData.set(DATA_BOUND, !tag.contains("Bound") || tag.getBoolean("Bound"));
		deaths = tag.getInt("Deaths");
		staggeredThisLife = tag.getBoolean("Staggered");
		adaptation.load(tag.getCompound("Adaptation"));
		syncAdapt();
		finalDeath = tag.getBoolean("Final");
		reviveProgress = tag.getDouble("Revive");
		reviveNeeded = tag.getDouble("ReviveNeeded");
		if (tag.getBoolean("Corpse")) {
			entityData.set(DATA_ACTION, ACTION_CORPSE);
			if (reviveNeeded <= 0) {
				reviveNeeded = DoomsdayConfig.revival().daySeconds * 20.0;
			}
		}
		eventId = tag.hasUUID("Event") ? tag.getUUID("Event") : null;
		applyScale();
	}

	// ---------------------------------------------------------------- test / harness hooks

	public boolean debugStartMove(String name) {
		try {
			return brain != null && brain.force(Brain.Move.valueOf(name));
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public void debugTickBrain() {
		if (brain != null) {
			brain.tick();
		}
	}

	public boolean debugMoveIdle() {
		return brain == null || brain.idle();
	}

	/** Test hook: set the life (1-4) without the corpse in between. */
	public void debugSetLife(int life) {
		entityData.set(DATA_LIFE, Math.max(1, life));
		if (life >= 2) {
			entityData.set(DATA_BOUND, false);
		}
		configure(1);
	}

	// ---------------------------------------------------------------- the brain

	/** Every move, as one state machine (one at a time, a breather between). */
	static final class Brain extends Goal {
		enum Move { NONE, HAMMER, GUARD, LEAP, SHATTER, ERUPT, VOLLEY, HEAT, GRAB, BREATH, BLITZ, ROAR, BREAK_FREE, STAGGER,
			// v0.17.2 round 2
			RAMPAGE, CLAP, SKYFALL, HURL, NOVA }

		private final DoomsdayEntity d;
		private Move move = Move.NONE;
		private int timer;
		private int breather = 40;
		private final Map<Move, Integer> cooldowns = new EnumMap<>(Move.class);
		private LivingEntity target;
		private Vec3 aim = Vec3.ZERO;
		private Vec3 blitzFrom = Vec3.ZERO;
		private Vec3 blitzTo = Vec3.ZERO;
		private boolean airborne;
		private LivingEntity grabbed;
		private float grabDamage;
		private final Set<UUID> struck = new HashSet<>();
		private int pathStuck;
		/** Round 2: the Rampage's line, the Thunderclaps done, Skyfall's mark and peak, the boulder in his hands, the Nova's element. */
		private Vec3 chargeDir = new Vec3(0, 0, 1);
		private int claps;
		private Vec3 skyMark = Vec3.ZERO;
		private double skyTop;
		private DoomsdayAttackEntity heldBoulder;
		private int thrown;
		private Bucket novaBucket;
		/** When the Rampage crash / the Skyfall landing began (brain ticks: the tests tick a NoAI Doomsday's brain by hand). */
		private int phaseAt;
		private Vec3 lastPos = Vec3.ZERO;

		Brain(DoomsdayEntity d) {
			this.d = d;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return !d.helpless();
		}

		@Override
		public boolean canContinueToUse() {
			return !d.helpless();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			cancel();
		}

		boolean midMove() {
			return move != Move.NONE;
		}

		boolean idle() {
			return move == Move.NONE;
		}

		void cancel() {
			releaseGrab(false);
			dropBoulder();
			d.setNoGravity(false);
			d.entityData.set(DATA_ELEMENT, -1);
			move = Move.NONE;
			timer = 0;
			airborne = false;
			d.guarding = false;
			if (!d.helpless()) {
				d.setAction(ACTION_NONE);
			}
			d.entityData.set(DATA_BEAM, new Vector3f());
		}

		void noteDamage(DamageSource source, float amount) {
			if (grabbed != null && source.getEntity() != grabbed && source.getEntity() != null) {
				grabDamage += amount;
				if (grabDamage >= DoomsdayConfig.moves().grabBreakDamage) {
					releaseGrab(true);
					finish(40);
				}
			}
		}

		boolean force(Move m) {
			if (d.helpless()) {
				return false;
			}
			if (target == null) {
				target = d.getTarget();
			}
			if (m != Move.BREAK_FREE && m != Move.ROAR && m != Move.GUARD && m != Move.SHATTER && m != Move.STAGGER && target == null) {
				return false;
			}
			cancel();
			begin(m);
			return true;
		}

		private float cdScale() {
			return d.life() > 3 ? 0.6f : d.life() == 3 ? 0.75f : 1.0f;
		}

		private boolean ready(Move m) {
			return cooldowns.getOrDefault(m, 0) <= 0;
		}

		private void finish(int extraRest) {
			int cd = switch (move) {
				case HAMMER -> 30;
				case GUARD -> 240;
				case LEAP -> 150;
				case SHATTER -> 220;
				case ERUPT -> 200;
				case VOLLEY -> 160;
				case HEAT -> 240;
				case GRAB -> 280;
				case BREATH -> 260;
				case BLITZ -> 220;
				case ROAR -> 420;
				case RAMPAGE -> 200;
				case CLAP -> 180;
				case SKYFALL -> 320;
				case HURL -> 200;
				case NOVA -> 360;
				default -> 0;
			};
			cooldowns.put(move, (int) (cd * cdScale()));
			dropBoulder();
			d.setNoGravity(false);
			d.entityData.set(DATA_ELEMENT, -1);
			move = Move.NONE;
			timer = 0;
			airborne = false;
			d.guarding = false;
			d.setAction(ACTION_NONE);
			d.entityData.set(DATA_BEAM, new Vector3f());
			breather = (int) (DoomsdayConfig.moves().breather * cdScale()) + extraRest;
		}

		@Override
		public void tick() {
			if (!(d.level() instanceof ServerLevel level) || d.helpless()) {
				return;
			}
			for (Map.Entry<Move, Integer> e : cooldowns.entrySet()) {
				if (e.getValue() > 0) {
					e.setValue(e.getValue() - 1);
				}
			}
			LivingEntity t = d.getTarget();
			if (t != null && (!t.isAlive() || !canTarget(t))) {
				t = null;
			}
			if (move == Move.NONE) {
				target = t;
				if (t == null) {
					return;
				}
				d.getLookControl().setLookAt(t, 30f, 30f);
				if (breather > 0) {
					breather--;
					approach(t);
					return;
				}
				Move pick = choose(level, t);
				if (pick == Move.NONE) {
					approach(t);
					return;
				}
				begin(pick);
			}
			timer++;
			switch (move) {
				case HAMMER -> tickHammer(level);
				case GUARD -> tickGuard(level);
				case LEAP -> tickLeap(level);
				case SHATTER -> tickShatter(level);
				case ERUPT -> tickErupt(level);
				case VOLLEY -> tickVolley(level);
				case HEAT -> tickHeat(level);
				case GRAB -> tickGrab(level);
				case BREATH -> tickBreath(level);
				case BLITZ -> tickBlitz(level);
				case ROAR -> tickRoar(level);
				case BREAK_FREE -> tickBreakFree(level);
				case STAGGER -> tickStagger(level);
				case RAMPAGE -> tickRampage(level);
				case CLAP -> tickClap(level);
				case SKYFALL -> tickSkyfall(level);
				case HURL -> tickHurl(level);
				case NOVA -> tickNova(level);
				default -> finish(0);
			}
		}

		private void approach(LivingEntity t) {
			if (d.tickCount % 10 == 0) {
				d.getNavigation().moveTo(t, 1.0);
				Vec3 p = d.position();
				pathStuck = p.distanceToSqr(lastPos) < 0.25 && d.distanceTo(t) > reach(t) + 2 ? pathStuck + 10 : 0;
				lastPos = p;
			}
		}

		private double reach(LivingEntity t) {
			return 2.6 + d.getBbWidth() * 0.5 + t.getBbWidth() * 0.5;
		}

		private double flatDist(LivingEntity t) {
			double dx = t.getX() - d.getX();
			double dz = t.getZ() - d.getZ();
			return Math.sqrt(dx * dx + dz * dz);
		}

		private boolean sees(LivingEntity t) {
			return d.hasLineOfSight(t);
		}

		private Move choose(ServerLevel level, LivingEntity t) {
			double dist = flatDist(t);
			double reach = reach(t);
			int life = d.life();
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			java.util.List<Move> c = new java.util.ArrayList<>();
			// v0.17.2: poisoned by kryptonite -- only his fists (the hammer and the grab), no powers
			boolean poisoned = com.projecthero.mod.kryptonian.KryptoniteStrikes.suppressed(d);
			if (poisoned && dist <= reach + 0.5 && ready(Move.HAMMER)) {
				return Move.HAMMER;
			}
			if (poisoned) {
				return dist <= reach + 2.0 && ready(Move.GRAB) ? Move.GRAB : Move.NONE;
			}
			if (dist <= reach + 0.5 && ready(Move.HAMMER)) {
				if (d.random.nextFloat() < 0.55f) {
					return Move.HAMMER;
				}
				c.add(Move.HAMMER);
			}
			if (dist <= reach + 2.0 && ready(Move.GRAB)) {
				c.add(Move.GRAB);
			}
			if (dist <= m.roarRadius && ready(Move.ROAR)) {
				c.add(Move.ROAR);
			}
			if (dist > 4 && dist <= m.shatterRange * 0.8 && ready(Move.SHATTER)) {
				c.add(Move.SHATTER);
			}
			boolean leapable = dist >= m.leapMin && dist <= m.leapMax;
			if (leapable && ready(Move.LEAP)) {
				c.add(Move.LEAP);
				if (pathStuck >= 40 || dist > 30) {
					return Move.LEAP;
				}
			}
			if (life == 1 && d.bound() && level.getGameTime() - d.lastProjectileHit < 60 && ready(Move.GUARD)) {
				return Move.GUARD;
			}
			if (life >= 2) {
				if (dist <= 22 && ready(Move.ERUPT)) {
					c.add(Move.ERUPT);
				}
				if (dist >= 6 && dist <= 30 && sees(t) && ready(Move.VOLLEY)) {
					c.add(Move.VOLLEY);
				}
				if (dist <= m.heatRange && sees(t) && ready(Move.HEAT)) {
					c.add(Move.HEAT);
				}
			}
			if (life >= 3) {
				if (dist <= m.breathRange && ready(Move.BREATH)) {
					c.add(Move.BREATH);
				}
				if (dist >= 8 && dist <= m.blitzLength && ready(Move.BLITZ)) {
					c.add(Move.BLITZ);
				}
			}
			// v0.17.2 round 2
			if (dist >= 7 && dist <= m.rampageLength && ready(Move.RAMPAGE)) {
				c.add(Move.RAMPAGE);
			}
			if (!d.bound() && dist <= m.clapRange * 0.9 && ready(Move.CLAP)) {
				c.add(Move.CLAP);
			}
			if (life >= 2 && dist >= 8 && dist <= 40 && ready(Move.SKYFALL)) {
				c.add(Move.SKYFALL);
			}
			if (dist >= 8 && dist <= 32 && sees(t) && ready(Move.HURL)) {
				c.add(Move.HURL);
			}
			if (life >= 3 && dist <= m.novaRadius && d.strongestAdaptation() != null && ready(Move.NOVA)) {
				c.add(Move.NOVA);
			}
			return c.isEmpty() ? Move.NONE : c.get(d.random.nextInt(c.size()));
		}

		private void begin(Move m) {
			move = m;
			timer = 0;
			struck.clear();
			d.getNavigation().stop();
			LivingEntity t = target;
			switch (m) {
				case HAMMER -> d.setAction(ACTION_HAMMER);
				case GUARD -> {
					d.setAction(ACTION_GUARD);
					d.guarding = true;
					d.level().playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				case LEAP -> {
					d.setAction(ACTION_LEAP_CROUCH);
					aim = t != null ? t.position() : d.position();
				}
				case SHATTER -> d.setAction(ACTION_SHATTER);
				case ERUPT -> d.setAction(ACTION_ERUPT);
				case VOLLEY -> d.setAction(ACTION_VOLLEY);
				case HEAT -> {
					d.setAction(ACTION_HEAT);
					aim = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0) : d.eyes();
				}
				case GRAB -> d.setAction(ACTION_GRAB);
				case BREATH -> d.setAction(ACTION_BREATH);
				case BLITZ -> {
					d.setAction(ACTION_BLITZ);
					Vec3 from = d.position();
					Vec3 to = t != null ? t.position() : from.add(d.getLookAngle().scale(10));
					Vec3 dir = new Vec3(to.x - from.x, 0, to.z - from.z);
					double len = Math.min(DoomsdayConfig.moves().blitzLength, dir.length() + 6);
					blitzFrom = from;
					blitzTo = from.add(dir.normalize().scale(len));
				}
				case ROAR -> d.setAction(ACTION_ROAR);
				case BREAK_FREE -> d.setAction(ACTION_BREAK_FREE);
				case STAGGER -> {
					d.setAction(ACTION_STAGGER);
					d.level().playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.0f, 0.5f);
					d.level().playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.5f);
				}
				case RAMPAGE -> {
					d.setAction(ACTION_RAMPAGE);
					Vec3 to = t != null ? t.position() : d.position().add(d.getLookAngle().scale(10));
					Vec3 dir = new Vec3(to.x - d.getX(), 0, to.z - d.getZ());
					chargeDir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
					d.level().playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.7f);
				}
				case CLAP -> {
					d.setAction(ACTION_CLAP);
					claps = 0;
				}
				case SKYFALL -> {
					d.setAction(ACTION_SKYFALL);
					skyMark = t != null ? t.position() : d.position();
					skyTop = 0;
				}
				case HURL -> {
					d.setAction(ACTION_HURL);
					thrown = 0;
				}
				case NOVA -> {
					Bucket b = d.strongestAdaptation();
					novaBucket = b;
					d.entityData.set(DATA_ELEMENT, b == null ? -1 : b.ordinal());
					d.setAction(ACTION_NOVA);
					if (d.level() instanceof ServerLevel level) {
						d.announce(level, Component.translatable("message.projecthero.doomsday.nova", bucketName(b)).withStyle(ChatFormatting.DARK_RED,
								ChatFormatting.BOLD));
					}
				}
				default -> d.setAction(ACTION_NONE);
			}
		}

		private void face(Vec3 at) {
			d.getLookControl().setLookAt(at.x, at.y, at.z, 30f, 30f);
			double dx = at.x - d.getX();
			double dz = at.z - d.getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			d.setYRot(yaw);
			d.yBodyRot = yaw;
			d.yHeadRot = yaw;
		}

		// ------------------------------------------------ the moves

		private void tickHammer(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			LivingEntity t = target;
			if (t != null) {
				face(t.position());
			}
			int hits = d.bound() ? 2 : 3;
			if (timer % 8 == 0 && timer / 8 <= hits && timer > 0) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.5f);
				if (t != null && flatDist(t) <= reach(t) + 1.0 && Math.abs(t.getY() - d.getY()) < d.getBbHeight()) {
					if (hit(level, d, t, m.hammerDamage)) {
						Vec3 out = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ()).normalize();
						double kb = timer / 8 == hits ? 2.2 : 0.9;
						t.setDeltaMovement(out.x * kb, 0.35 + kb * 0.15, out.z * kb);
						t.hurtMarked = true;
						DoomsdayFx.impact(level, t.position(), 0.5);
					}
				}
			}
			if (timer >= 8 * hits + 8) {
				finish(0);
			}
		}

		private void tickGuard(ServerLevel level) {
			if (target != null) {
				face(target.position());
			}
			if (timer % 4 == 0) {
				DoomsdayFx.ring(level, DoomsdayFx.CABLE, d.position().add(0, d.getBbHeight() * 0.55, 0), d.getBbWidth() * 1.2, 16);
			}
			if (timer >= 60) {
				finish(0);
			}
		}

		private void tickLeap(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (timer < LEAP_CROUCH) {
				if (target != null) {
					aim = target.position();
				}
				face(aim);
				if (timer % 4 == 0) {
					DoomsdayFx.ring(level, DoomsdayFx.RED, new Vec3(aim.x, DoomsdayAttackEntity.ground(level, aim.x, aim.y, aim.z) + 0.1, aim.z),
							m.leapRadius, 40);
				}
				if (timer == 1) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				return;
			}
			if (timer == LEAP_CROUCH) {
				Vec3 from = d.position();
				Vec3 delta = aim.subtract(from);
				double blocks = Math.max(4.0, delta.length());
				Vec3 v = AbilityHelpers.ballisticLaunch(delta.normalize(), blocks, true);
				d.setDeltaMovement(v);
				d.hasImpulse = true;
				d.setOnGround(false);
				airborne = true;
				d.setAction(ACTION_LEAP_AIR);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.6f);
				DoomsdayFx.impact(level, from, 1.5);
				return;
			}
			if (airborne) {
				if (timer % 2 == 0) {
					level.sendParticles(DoomsdayFx.ASH, d.getX(), d.getY(), d.getZ(), 2, 0.4, 0.4, 0.4, 0.0);
				}
				if ((d.onGround() && timer > LEAP_CROUCH + 4) || timer > LEAP_CROUCH + 120 || d.isInWater()) {
					airborne = false;
					land(level);
				}
				return;
			}
			if (timer >= LEAP_CROUCH + 400 || d.action() == ACTION_LAND && d.stateTicks >= 14) {
				finish(10);
			}
		}

		private void land(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			d.setAction(ACTION_LAND);
			d.setDeltaMovement(0, 0, 0);
			Vec3 at = d.position();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.45f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 2.0f, 0.4f);
			DoomsdayFx.impact(level, at, m.leapRadius);
			DoomsdayFx.ring(level, DoomsdayFx.DIRT, at.add(0, 0.2, 0), m.leapRadius, 60);
			DoomsdayFx.shake(level, at, 1.6f, 20, 48);
			for (LivingEntity t : d.targetsNear(at.add(0, 1, 0), m.leapRadius)) {
				if (hit(level, d, t, m.leapDamage)) {
					Vec3 out = new Vec3(t.getX() - at.x, 0, t.getZ() - at.z).normalize();
					t.setDeltaMovement(out.x * 1.4, 0.8, out.z * 1.4);
					t.hurtMarked = true;
				}
			}
		}

		private void tickShatter(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			if (timer == 4) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
			}
			if (timer == SHATTER_SLAM) {
				Vec3 at = d.position();
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
				DoomsdayFx.impact(level, at.add(d.getLookAngle().scale(1.5)), 2.5);
				DoomsdayFx.shake(level, at, 1.4f, 24, 56);
				DoomsdayAttackEntity.ring(level, d, at, m.shatterRange, 0.9, m.shatterDamage);
			}
			if (timer >= SHATTER_SLAM + 14) {
				finish(0);
			}
		}

		private void tickErupt(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			if (timer < ERUPT_BURST && timer % 3 == 0) {
				level.sendParticles(DoomsdayFx.BONE, d.getX(), d.getY() + d.getBbHeight() * 0.5, d.getZ(), 6, d.getBbWidth() * 0.5,
						d.getBbHeight() * 0.4, d.getBbWidth() * 0.5, 0.02);
			}
			if (timer == ERUPT_BURST) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.5f, 0.55f);
				DoomsdayFx.forced(level, DoomsdayFx.BONE, d.getX(), d.getY() + d.getBbHeight() * 0.5, d.getZ(), 60, d.getBbWidth(), d.getBbHeight() * 0.5,
						d.getBbWidth(), 0.25);
				for (LivingEntity t : d.targetsNear(d.position().add(0, d.getBbHeight() * 0.4, 0), 4.0 + d.getBbWidth() * 0.5)) {
					if (hit(level, d, t, m.eruptionDamage)) {
						Vec3 out = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ()).normalize();
						t.setDeltaMovement(out.x * 1.2, 0.5, out.z * 1.2);
						t.hurtMarked = true;
					}
				}
				Vec3 from = d.position();
				Vec3 to = target != null ? target.position() : from.add(d.getLookAngle().scale(12));
				Vec3 dir = new Vec3(to.x - from.x, 0, to.z - from.z);
				double len = Math.max(6, Math.min(22, dir.length() + 3));
				dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : dir.normalize();
				int n = (int) Math.min(14, len / 1.5);
				for (int i = 0; i < n; i++) {
					Vec3 p = from.add(dir.scale(2.0 + 1.5 * i));
					double y = DoomsdayAttackEntity.ground(level, p.x, from.y, p.z);
					DoomsdayAttackEntity.spike(level, d, new Vec3(p.x, y, p.z), 8 + 2 * i, m.spikeLineDamage);
				}
			}
			if (timer >= ERUPT_BURST + 16) {
				finish(0);
			}
		}

		private void tickVolley(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			if (timer == VOLLEY_THROW) {
				Vec3 from = d.position().add(0, d.getBbHeight() * 0.75, 0);
				Vec3 to = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : from.add(d.getLookAngle().scale(10));
				Vec3 dir = to.subtract(from).normalize();
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 2.0f, 0.5f);
				for (int i = -2; i <= 2; i++) {
					Vec3 v = dir.yRot(i * 0.2f).scale(1.6);
					DoomsdayAttackEntity.shard(level, d, from.add(v.scale(1.2)), v, m.volleyDamage);
				}
			}
			if (timer >= VOLLEY_THROW + 10) {
				finish(0);
			}
		}

		private void tickHeat(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			LivingEntity t = target;
			Vec3 want = t != null && t.isAlive() ? t.position().add(0, t.getBbHeight() * 0.5, 0) : aim;
			face(want);
			if (timer < HEAT_CHARGE) {
				if (timer == 1) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				if (timer % 4 == 0) {
					Vec3 e = d.eyes();
					level.sendParticles(DoomsdayFx.RED, e.x, e.y, e.z, 3, 0.2, 0.1, 0.2, 0.0);
				}
				return;
			}
			if (timer <= HEAT_END) {
				// the beam sweeps after the target, a little behind
				aim = aim.lerp(want, 0.22);
				Vec3 eye = d.eyes();
				Vec3 dir = aim.subtract(eye);
				Vec3 end = eye.add(dir.normalize().scale(m.heatRange));
				com.projecthero.mod.beamclash.BeamClash.report(d, eye, dir, m.heatRange, 1.6f, 0xFF2A20); // v0.17.3
				HitResult hr = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, d));
				if (hr.getType() != HitResult.Type.MISS) {
					end = hr.getLocation();
				}
				d.entityData.set(DATA_BEAM, new Vector3f((float) end.x, (float) end.y, (float) end.z));
				if (timer % 2 == 0) {
					level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.02);
					level.sendParticles(ParticleTypes.SMOKE, end.x, end.y, end.z, 2, 0.2, 0.2, 0.2, 0.02);
				}
				if (timer % 4 == 0) {
					level.playSound(null, end.x, end.y, end.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8f, 0.6f);
					AABB box = new AABB(eye, end).inflate(1.0);
					for (LivingEntity v : level.getEntitiesOfClass(LivingEntity.class, box, DoomsdayEntity::canTarget)) {
						AABB fat = v.getBoundingBox().inflate(0.8);
						if (fat.clip(eye, end).isPresent() || fat.contains(end)) {
							if (hit(level, d, v, m.heatDamage)) {
								v.igniteForSeconds(3);
							}
						}
					}
				}
				return;
			}
			if (timer >= HEAT_END + 4) {
				finish(0);
			}
		}

		private void tickGrab(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			LivingEntity t = target;
			if (grabbed == null) {
				if (t == null || timer > 12) {
					finish(0);
					return;
				}
				face(t.position());
				Vec3 to = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ());
				if (to.length() > 0.1) {
					Vec3 v = to.normalize().scale(0.55);
					d.setDeltaMovement(v.x, d.getDeltaMovement().y, v.z);
				}
				if (flatDist(t) <= reach(t) + 0.8) {
					grabbed = t;
					grabDamage = 0;
					timer = 12;
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0f, 0.5f);
				}
				return;
			}
			if (!grabbed.isAlive()) {
				releaseGrab(false);
				finish(0);
				return;
			}
			// held up in his right hand, in front of him
			Vec3 fwd = Vec3.directionFromRotation(0, d.yBodyRot);
			Vec3 hold = d.position().add(fwd.scale(d.getBbWidth() * 0.5 + 0.8)).add(0, d.getBbHeight() * 0.55, 0);
			int phase = timer - 12;
			boolean slamming = phase == 18 || phase == 38 || phase == 58;
			if (slamming) {
				double gy = DoomsdayAttackEntity.ground(level, hold.x, hold.y, hold.z);
				grabbed.teleportTo(hold.x, gy, hold.z);
				grabbed.setDeltaMovement(Vec3.ZERO);
				hit(level, d, grabbed, m.slamDamage);
				level.playSound(null, hold.x, gy, hold.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.8f, 0.6f);
				DoomsdayFx.impact(level, new Vec3(hold.x, gy, hold.z), 1.5);
				DoomsdayFx.shake(level, hold, 0.8f, 10, 24);
			} else if (phase < 66) {
				double lift = (phase % 20) < 14 ? 1.0 : 0.2;
				grabbed.teleportTo(hold.x, hold.y - 0.6 + lift, hold.z);
				grabbed.setDeltaMovement(Vec3.ZERO);
				grabbed.fallDistance = 0;
			}
			if (phase >= 66) {
				LivingEntity v = grabbed;
				releaseGrab(false);
				v.setDeltaMovement(fwd.x * 2.0, 0.8, fwd.z * 2.0);
				v.hurtMarked = true;
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2.0f, 0.5f);
				finish(0);
			}
		}

		private void releaseGrab(boolean broken) {
			if (grabbed != null) {
				if (broken && d.level() instanceof ServerLevel level) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 2.0f, 0.6f);
					grabbed.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0));
					for (ServerPlayer p : level.players()) {
						if (p.distanceToSqr(d) < 48 * 48) {
							p.displayClientMessage(Component.translatable("message.projecthero.doomsday.grip_broken").withStyle(ChatFormatting.GOLD), true);
						}
					}
				}
				grabbed.fallDistance = 0;
				grabbed = null;
			}
		}

		private void tickBreath(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			if (timer == 1) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.BREEZE_INHALE, SoundSource.HOSTILE, 3.0f, 0.4f);
			}
			if (timer >= BREATH_INHALE && timer <= BREATH_INHALE + 30) {
				Vec3 eye = d.eyes();
				Vec3 look = d.getLookAngle();
				if (timer % 2 == 0) {
					for (int i = 0; i < 4; i++) {
						Vec3 v = look.yRot((d.random.nextFloat() - 0.5f) * 0.8f).scale(0.9 + d.random.nextFloat());
						level.sendParticles(ParticleTypes.CLOUD, eye.x + look.x, eye.y + look.y, eye.z + look.z, 0, v.x, v.y * 0.3, v.z, 1.0);
					}
				}
				if (timer == BREATH_INHALE) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
				}
				if (timer % 5 == 0) {
					for (LivingEntity t : d.targetsNear(eye, m.breathRange)) {
						Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(eye);
						if (to.normalize().dot(look) < Math.cos(Math.toRadians(30))) {
							continue;
						}
						Vec3 push = new Vec3(to.x, 0, to.z).normalize().scale(1.3);
						t.setDeltaMovement(t.getDeltaMovement().add(push.x, 0.35, push.z));
						t.hurtMarked = true;
						t.clearFire();
						t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
						hit(level, d, t, 2f);
					}
				}
			}
			if (timer >= BREATH_INHALE + 34) {
				finish(0);
			}
		}

		private void tickBlitz(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			face(blitzTo);
			if (timer < BLITZ_GO) {
				if (timer % 3 == 0) {
					DoomsdayFx.line(level, DoomsdayFx.RED, blitzFrom.add(0, 0.3, 0), blitzTo.add(0, 0.3, 0), 0.8);
				}
				return;
			}
			int steps = 6;
			int i = timer - BLITZ_GO;
			if (i <= steps) {
				Vec3 a = blitzFrom.lerp(blitzTo, (i - 1) / (double) steps);
				Vec3 b = blitzFrom.lerp(blitzTo, i / (double) steps);
				double y = DoomsdayAttackEntity.ground(level, b.x, d.getY() + 1, b.z);
				d.teleportTo(b.x, y, b.z);
				DoomsdayFx.line(level, DoomsdayFx.ASH, a.add(0, 1.5, 0), b.add(0, 1.5, 0), 0.5);
				if (i == 1) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.HOSTILE, 2.5f, 0.5f);
				}
				AABB box = new AABB(a, b).inflate(d.getBbWidth() * 0.5 + 1.0, d.getBbHeight(), d.getBbWidth() * 0.5 + 1.0);
				for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, DoomsdayEntity::canTarget)) {
					if (struck.add(t.getUUID()) && hit(level, d, t, m.blitzDamage)) {
						Vec3 side = new Vec3(t.getX() - b.x, 0, t.getZ() - b.z).normalize();
						t.setDeltaMovement(side.x * 1.2, 0.6, side.z * 1.2);
						t.hurtMarked = true;
					}
				}
				return;
			}
			if (timer >= BLITZ_GO + steps + 8) {
				finish(0);
			}
		}

		private void tickRoar(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (timer == ROAR_AT) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.4f);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
				DoomsdayFx.shake(level, d.position(), 1.0f, 30, m.roarRadius * 2);
				DoomsdayFx.ring(level, DoomsdayFx.ASH, d.position().add(0, 1.0, 0), m.roarRadius * 0.5, 50);
				DoomsdayFx.ring(level, DoomsdayFx.ASH, d.position().add(0, 1.0, 0), m.roarRadius, 90);
				for (LivingEntity t : d.targetsNear(d.position(), m.roarRadius)) {
					t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
					t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
				}
			}
			if (timer >= 30) {
				finish(0);
			}
		}

		/** v0.17.2 (user: "i want to nerf doomsday a bit, make him slightly more aggressive but also give him a staggered stance where he takes 300% more damage, make the staggered stance last 15 seconds please"): slumped on one knee, reeling -- he stands still and takes it, then roars back up. */
		private void tickStagger(ServerLevel level) {
			d.getNavigation().stop();
			Vec3 v = d.getDeltaMovement();
			d.setDeltaMovement(0, v.y, 0);
			if (timer % 10 == 0) {
				level.sendParticles(DoomsdayFx.STONE, d.getX(), d.getY() + d.getBbHeight() * 0.55, d.getZ(), 6, 0.6, 0.6, 0.6, 0.05);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, d.getX(), d.getY() + d.getBbHeight() * 0.9, d.getZ(), 8, 0.6,
						0.3, 0.6, 0.1);
			}
			if (timer >= DoomsdayConfig.boss().staggerSeconds * 20) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.6f);
				d.announce(level, Component.translatable("message.projecthero.doomsday.recovered").withStyle(ChatFormatting.RED));
				finish(0);
			}
		}

		// ------------------------------------------------ v0.17.2 round 2 (user: "add more attacks and animations to doomsday please")

		/** How much harder each life hits with the round-2 moves (+15% a life). */
		private float lifeScale() {
			return 1f + 0.15f * Math.max(0, Math.min(4, d.life()) - 1);
		}

		/**
		 * <b>Rampage</b>: he paws the ground, lowers his head (a red line shows his path), then bulldozes straight down it
		 * -- through every soft block in his way when terrain damage is on -- trampling whatever he meets. Anything he
		 * can't break stops him dead: he reels, dazed, open to a beating.
		 */
		private void tickRampage(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (d.action() == ACTION_RAMPAGE_CRASH) {
				d.getNavigation().stop();
				if ((timer - phaseAt) % 6 == 0) {
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, d.getX(), d.getY() + d.getBbHeight() * 0.95, d.getZ(), 5,
							0.4, 0.15, 0.4, 0.05);
				}
				if (timer - phaseAt >= CRASH_TICKS) {
					finish(0);
				}
				return;
			}
			if (timer < RAMPAGE_WINDUP) {
				if (timer < 10 && target != null) {
					Vec3 dir = new Vec3(target.getX() - d.getX(), 0, target.getZ() - d.getZ());
					if (dir.lengthSqr() > 1.0e-4) {
						chargeDir = dir.normalize();
					}
				}
				face(d.position().add(chargeDir.scale(10)));
				if (timer % 4 == 0) {
					Vec3 a = d.position().add(0, 0.15, 0);
					DoomsdayFx.line(level, DoomsdayFx.RED, a, a.add(chargeDir.scale(m.rampageLength)), 0.7);
				}
				if (timer == 6 || timer == 12) {
					// pawing the ground like a bull
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.5f, 0.5f);
					DoomsdayFx.impact(level, d.position().add(chargeDir.scale(-0.8)), 0.6);
				}
				return;
			}
			int run = timer - RAMPAGE_WINDUP;
			if (run < RAMPAGE_RUN) {
				double speed = m.rampageSpeed * (0.85 + 0.15 * lifeScale());
				Vec3 next = d.position().add(chargeDir.scale(speed));
				if (!plough(level, next)) {
					crash(level);
					return;
				}
				double y = DoomsdayAttackEntity.ground(level, next.x, d.getY() + 1.5, next.z);
				d.teleportTo(next.x, y, next.z);
				face(next.add(chargeDir.scale(5)));
				if (run % 3 == 0) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 2.5f, 0.6f);
					DoomsdayFx.impact(level, d.position(), 0.7);
					DoomsdayFx.shake(level, d.position(), 0.35f, 6, 24);
				}
				AABB box = d.getBoundingBox().inflate(1.0, 0.2, 1.0);
				for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, box, DoomsdayEntity::canTarget)) {
					if (struck.add(t.getUUID()) && hit(level, d, t, m.rampageDamage * lifeScale())) {
						t.setDeltaMovement(chargeDir.x * 2.0, 0.9, chargeDir.z * 2.0);
						t.hurtMarked = true;
						level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 2.0f, 0.5f);
					}
				}
				return;
			}
			// skidding to a halt
			if (run == RAMPAGE_RUN) {
				DoomsdayFx.impact(level, d.position().add(chargeDir.scale(1.5)), 1.2);
			}
			if (run >= RAMPAGE_RUN + 12) {
				finish(0);
			}
		}

		/** Clears his body's path at {@code next}; false when something there won't break (he crashes). */
		private boolean plough(ServerLevel level, Vec3 next) {
			double hw = d.getBbWidth() * 0.5;
			AABB box = new AABB(next.x - hw, d.getY() + 1.05, next.z - hw, next.x + hw, d.getY() + d.getBbHeight() * 0.9, next.z + hw);
			java.util.List<net.minecraft.core.BlockPos> breakable = new java.util.ArrayList<>();
			for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.util.Mth.floor(box.minX),
					net.minecraft.util.Mth.floor(box.minY), net.minecraft.util.Mth.floor(box.minZ), net.minecraft.util.Mth.floor(box.maxX),
					net.minecraft.util.Mth.floor(box.maxY), net.minecraft.util.Mth.floor(box.maxZ))) {
				net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
				if (st.getCollisionShape(level, pos).isEmpty()) {
					continue;
				}
				if (!mayBreak(level, pos, st)) {
					return false;
				}
				breakable.add(pos.immutable());
			}
			for (net.minecraft.core.BlockPos pos : breakable) {
				net.minecraft.world.level.block.state.BlockState st = level.getBlockState(pos);
				DoomsdayFx.forced(level, new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st),
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
				level.destroyBlock(pos, false, d);
			}
			return true;
		}

		private void crash(ServerLevel level) {
			d.setAction(ACTION_RAMPAGE_CRASH);
			phaseAt = timer;
			level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.4f);
			level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.0f, 0.5f);
			DoomsdayFx.impact(level, d.position().add(chargeDir.scale(d.getBbWidth())).add(0, 1.5, 0), 1.5);
			DoomsdayFx.shake(level, d.position(), 1.0f, 14, 32);
			d.announce(level, Component.translatable("message.projecthero.doomsday.crashed").withStyle(ChatFormatting.GOLD));
		}

		/**
		 * <b>Thunderclap</b>: arms flung wide (a red arc marks the cone), then his palms meet -- a sonic shockwave that
		 * throws everyone in front of him back, dizzy and slowed. Two hands needed: never while bound. Life 3+: twice.
		 */
		private void tickClap(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			int total = d.life() >= 3 ? 2 : 1;
			int at = CLAP_AT + claps * 18;
			if (claps < total && timer < at && timer % 4 == 0) {
				Vec3 c = d.position().add(0, 0.3, 0);
				Vec3 look = Vec3.directionFromRotation(0, d.yBodyRot);
				for (int i = -6; i <= 6; i++) {
					Vec3 dir = look.yRot((float) Math.toRadians(i * 6));
					level.sendParticles(DoomsdayFx.RED, c.x + dir.x * m.clapRange, c.y, c.z + dir.z * m.clapRange, 1, 0.1, 0.05, 0.1, 0);
				}
			}
			if (claps < total && timer == at) {
				claps++;
				Vec3 eye = d.position().add(0, d.getBbHeight() * 0.6, 0);
				Vec3 look = Vec3.directionFromRotation(0, d.yBodyRot);
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 1.4f);
				level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.5f, 0.7f);
				DoomsdayFx.shake(level, eye, 1.2f, 16, 40);
				for (int s = 1; s <= 5; s++) {
					Vec3 p = eye.add(look.scale(s * m.clapRange / 5.0));
					DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
					DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.CLOUD, p.x, p.y, p.z, 6, s * 0.4, 0.4, s * 0.4, 0.05);
				}
				for (LivingEntity t : d.targetsNear(eye, m.clapRange)) {
					Vec3 to = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ());
					if (to.lengthSqr() > 4.0 && to.normalize().dot(look) < Math.cos(Math.toRadians(38))) {
						continue;
					}
					if (hit(level, d, t, m.clapDamage * lifeScale())) {
						Vec3 out = to.lengthSqr() < 1.0e-4 ? look : to.normalize();
						t.setDeltaMovement(out.x * 2.2, 0.55, out.z * 2.2);
						t.hurtMarked = true;
						t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
						t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
					}
				}
			}
			if (claps >= total && timer >= CLAP_AT + (total - 1) * 18 + 14) {
				finish(0);
			}
		}

		/**
		 * <b>Skyfall</b>: he crouches and launches himself straight up out of sight, hangs over his target (a growing red
		 * ring on the ground marks where he will come down -- run), then drops like a meteor: a huge impact, a crater
		 * (terrain damage on) and a shockwave. Bigger every life. Life 2+.
		 */
		private void tickSkyfall(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			double radius = 5.0 + d.life();
			if (d.action() == ACTION_SKYFALL_LAND) {
				if (timer - phaseAt >= SKY_LAND_TICKS) {
					finish(10);
				}
				return;
			}
			if (timer < SKY_CROUCH) {
				if (target != null) {
					skyMark = target.position();
				}
				face(skyMark);
				if (timer == 2) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0f, 0.45f);
				}
				if (timer % 3 == 0) {
					DoomsdayFx.impact(level, d.position(), 0.4);
				}
				return;
			}
			if (timer == SKY_CROUCH) {
				d.setNoGravity(true);
				d.getNavigation().stop();
				skyTop = d.getY() + m.skyHeight;
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.5f);
				DoomsdayFx.impact(level, d.position(), 3.0);
				DoomsdayFx.shake(level, d.position(), 1.2f, 16, 48);
			}
			int t = timer - SKY_CROUCH;
			if (t <= SKY_RISE) {
				double y = Math.min(skyTop, d.getY() + m.skyHeight / (double) SKY_RISE);
				d.teleportTo(d.getX(), y, d.getZ());
				d.setDeltaMovement(Vec3.ZERO);
				level.sendParticles(DoomsdayFx.ASH, d.getX(), d.getY(), d.getZ(), 4, 0.5, 0.5, 0.5, 0.02);
				return;
			}
			if (t <= SKY_RISE + SKY_HANG) {
				int h = t - SKY_RISE;
				if (h < 14 && target != null) {
					skyMark = target.position();
				}
				Vec3 over = new Vec3(skyMark.x, skyTop, skyMark.z);
				Vec3 now = d.position().lerp(over, 0.25);
				d.teleportTo(now.x, skyTop, now.z);
				d.setDeltaMovement(Vec3.ZERO);
				double gy = DoomsdayAttackEntity.ground(level, skyMark.x, skyMark.y + 2, skyMark.z);
				if (h % 3 == 0) {
					double grow = radius * Math.min(1.0, 0.35 + h / (double) SKY_HANG);
					DoomsdayFx.ring(level, DoomsdayFx.RED_BIG, new Vec3(skyMark.x, gy + 0.1, skyMark.z), grow, 60);
					DoomsdayFx.ring(level, DoomsdayFx.RED, new Vec3(skyMark.x, gy + 0.1, skyMark.z), grow * 0.5, 30);
				}
				if (h == SKY_HANG - 6) {
					level.playSound(null, skyMark.x, gy, skyMark.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3.0f, 0.5f);
				}
				return;
			}
			// the drop
			double gy = DoomsdayAttackEntity.ground(level, d.getX(), Math.max(skyMark.y + 3, d.getY() - 40), d.getZ());
			double y = d.getY() - 3.4;
			DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.FLAME, d.getX(), d.getY() + 1, d.getZ(), 6, 0.6, 0.6, 0.6, 0.02);
			DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, d.getX(), d.getY() + 1, d.getZ(), 4, 0.6, 0.6, 0.6,
					0.02);
			if (y <= gy || t > SKY_RISE + SKY_HANG + 40) {
				d.teleportTo(d.getX(), gy, d.getZ());
				d.setNoGravity(false);
				skyImpact(level, radius);
				return;
			}
			d.teleportTo(d.getX(), y, d.getZ());
			d.setDeltaMovement(Vec3.ZERO);
		}

		private void skyImpact(ServerLevel level, double radius) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			d.setAction(ACTION_SKYFALL_LAND);
			phaseAt = timer;
			Vec3 at = d.position();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0f, 0.35f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.3f);
			DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
			DoomsdayFx.impact(level, at, radius);
			DoomsdayFx.shake(level, at, 2.2f, 30, 72);
			for (LivingEntity t : d.targetsNear(at.add(0, 1, 0), radius)) {
				double f = 1.0 - Math.min(1.0, Math.sqrt(t.distanceToSqr(at)) / radius);
				if (hit(level, d, t, (float) (m.skyDamage * lifeScale() * (0.45 + 0.55 * f)))) {
					Vec3 out = new Vec3(t.getX() - at.x, 0, t.getZ() - at.z);
					out = out.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : out.normalize();
					t.setDeltaMovement(out.x * (1.0 + f), 0.9 + f * 0.5, out.z * (1.0 + f));
					t.hurtMarked = true;
				}
			}
			DoomsdayAttackEntity.ring(level, d, at, radius * 2.2, 1.0, 6f);
			crater(level, at, 2.0 + 0.5 * d.life(), 48);
		}

		/** A bowl-shaped crater (terrain damage on, mob griefing on; soft natural blocks only, nothing with a block entity). */
		private void crater(ServerLevel level, Vec3 c, double radius, int max) {
			if (!griefing(level)) {
				return;
			}
			int r = (int) Math.ceil(radius);
			net.minecraft.core.BlockPos centre = net.minecraft.core.BlockPos.containing(c);
			java.util.List<net.minecraft.core.BlockPos> hits = new java.util.ArrayList<>();
			for (int x = -r; x <= r; x++) {
				for (int y = -r; y <= 0; y++) {
					for (int z = -r; z <= r; z++) {
						double dd = (x * x + z * z) / (radius * radius) + (y * y) / (radius * radius * 0.3);
						if (dd > 1.0) {
							continue;
						}
						net.minecraft.core.BlockPos p = centre.offset(x, y - 1, z);
						if (mayBreak(level, p, level.getBlockState(p))) {
							hits.add(p);
						}
					}
				}
			}
			hits.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(centre)));
			for (int i = 0; i < Math.min(max, hits.size()); i++) {
				level.destroyBlock(hits.get(i), false, d);
			}
		}

		/**
		 * <b>Boulder Hurl</b>: fists driven into the ground, he tears up a slab of whatever he is standing on, heaves it
		 * over his head and throws it in an arc at his target -- it bursts where it lands. Life 3+: a second one.
		 */
		private void tickHurl(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			if (target != null) {
				face(target.position());
			}
			net.minecraft.core.BlockPos under = d.blockPosition().below();
			net.minecraft.world.level.block.state.BlockState st = level.getBlockState(under);
			if (timer == 0 && d.action() == ACTION_NONE) {
				d.setAction(ACTION_HURL);
			}
			if (timer < HURL_RIP) {
				if (timer % 3 == 0 && !st.isAir()) {
					DoomsdayFx.forced(level, new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, st),
							d.getX(), d.getY() + 0.2, d.getZ(), 10, 0.8, 0.1, 0.8, 0.15);
				}
				if (timer == 4) {
					level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
				}
				return;
			}
			if (timer == HURL_RIP) {
				net.minecraft.world.level.block.state.BlockState look = st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()
						? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : st;
				if (look.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
					look = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
				}
				heldBoulder = DoomsdayAttackEntity.boulder(level, d, look, m.boulderDamage * lifeScale(), m.boulderRadius);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.0f, 0.7f);
				DoomsdayFx.impact(level, d.position(), 1.6);
				// the hole he tore it out of, in front of his feet (terrain damage on)
				if (griefing(level)) {
					Vec3 fwd = Vec3.directionFromRotation(0, d.yBodyRot);
					net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(d.position().add(fwd.scale(d.getBbWidth() * 0.5 + 1.0))).below();
					for (int x = -1; x <= 1; x++) {
						for (int z = -1; z <= 1; z++) {
							net.minecraft.core.BlockPos p = c.offset(x, 0, z);
							if (mayBreak(level, p, level.getBlockState(p))) {
								level.destroyBlock(p, false, d);
							}
						}
					}
				}
			}
			if (heldBoulder != null && timer < HURL_THROW) {
				heldBoulder.holdAt(d.position().add(0, d.getBbHeight() + 0.9, 0).add(Vec3.directionFromRotation(0, d.yBodyRot).scale(0.4)));
			}
			if (timer == HURL_THROW && heldBoulder != null) {
				Vec3 from = heldBoulder.position();
				Vec3 to = target != null ? target.position().add(target.getDeltaMovement().scale(12)) : from.add(d.getLookAngle().scale(16));
				Vec3 delta = to.subtract(from);
				double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
				double ticks = Math.max(10, flat / 1.3);
				Vec3 v = new Vec3(delta.x / ticks, delta.y / ticks + 0.5 * DoomsdayAttackEntity.BOULDER_GRAVITY * ticks, delta.z / ticks);
				heldBoulder.launch(v);
				heldBoulder = null;
				thrown++;
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 3.0f, 0.4f);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.5f, 0.5f);
			}
			if (timer >= HURL_THROW + 10) {
				if (d.life() >= 3 && thrown < 2) {
					// and another: a tick standing (so the client restarts the dig pose), then from the top
					timer = -1;
					d.setAction(ACTION_NONE);
					return;
				}
				finish(0);
			}
		}

		private void dropBoulder() {
			if (heldBoulder != null) {
				heldBoulder.discard();
				heldBoulder = null;
			}
		}

		/**
		 * <b>Adaptive Nova</b>: life 3+, once he has adapted to something. He drops to one knee and channels what he has
		 * learned most -- his body glows its colour, a ring shows the blast's reach -- then unleashes it back out:
		 * fire, lightning, a storm of bone, a ring of blasts, a withering wave or a raw shockwave.
		 */
		private void tickNova(ServerLevel level) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			Bucket b = novaBucket;
			Vec3 c = d.position().add(0, d.getBbHeight() * 0.5, 0);
			if (timer < NOVA_AT) {
				if (timer == 2) {
					level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.4f);
					level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.5f, 0.6f);
				}
				if (timer % 4 == 0) {
					DoomsdayFx.ring(level, novaParticle(b), new Vec3(d.getX(), d.getY() + 0.2, d.getZ()), m.novaRadius, 70);
					double r = m.novaRadius * (1.0 - timer / (double) NOVA_AT);
					DoomsdayFx.ring(level, novaParticle(b), new Vec3(d.getX(), d.getY() + 0.4, d.getZ()), Math.max(0.8, r), 40);
				}
				if (timer % 2 == 0) {
					DoomsdayFx.forced(level, novaParticle(b), c.x, c.y, c.z, 6, d.getBbWidth() * 0.6, d.getBbHeight() * 0.4, d.getBbWidth() * 0.6, 0.02);
				}
				return;
			}
			if (timer == NOVA_AT) {
				novaBurst(level, b, c);
			}
			if (timer >= NOVA_AT + 22) {
				finish(0);
			}
		}

		private static net.minecraft.core.particles.ParticleOptions novaParticle(Bucket b) {
			if (b == null) {
				return DoomsdayFx.ASH;
			}
			return switch (b) {
				case FIRE -> net.minecraft.core.particles.ParticleTypes.FLAME;
				case ENERGY -> net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK;
				case PROJECTILE, MELEE -> DoomsdayFx.BONE;
				case EXPLOSION -> DoomsdayFx.RED;
				case MAGIC -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
				default -> DoomsdayFx.PHANTOM;
			};
		}

		private void novaBurst(ServerLevel level, Bucket b, Vec3 c) {
			DoomsdayConfig.Moves m = DoomsdayConfig.moves();
			float dmg = m.novaDamage * lifeScale();
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.6f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.4f);
			DoomsdayFx.shake(level, c, 1.6f, 24, 56);
			for (int i = 0; i < 4; i++) {
				DoomsdayFx.ring(level, novaParticle(b), new Vec3(d.getX(), d.getY() + 0.3 + i * 0.8, d.getZ()), m.novaRadius * (0.4 + i * 0.2), 80);
			}
			java.util.List<LivingEntity> near = d.targetsNear(c, m.novaRadius);
			Bucket kind = b == null ? Bucket.POWER : b;
			switch (kind) {
				case PROJECTILE, MELEE -> {
					// a storm of bone in every direction
					for (int i = 0; i < 20; i++) {
						Vec3 dir = Vec3.directionFromRotation(-5f + d.random.nextFloat() * 10f, i * 18f);
						DoomsdayAttackEntity.shard(level, d, c.add(dir.scale(d.getBbWidth())), dir.scale(1.5), m.volleyDamage + 3f);
					}
				}
				case ENERGY -> {
					int bolts = 0;
					for (LivingEntity t : near) {
						if (bolts++ >= 5) {
							break;
						}
						net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
						if (bolt != null) {
							bolt.moveTo(t.getX(), t.getY(), t.getZ());
							bolt.setVisualOnly(true);
							level.addFreshEntity(bolt);
						}
						hit(level, d, t, dmg);
					}
				}
				default -> {
					for (LivingEntity t : near) {
						if (!hit(level, d, t, kind == Bucket.MAGIC ? dmg * 0.7f : dmg)) {
							continue;
						}
						Vec3 out = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ());
						out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
						double kb = kind == Bucket.POWER ? 3.0 : 1.4;
						t.setDeltaMovement(out.x * kb, 0.6, out.z * kb);
						t.hurtMarked = true;
						switch (kind) {
							case FIRE -> t.igniteForSeconds(6);
							case MAGIC -> t.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
							case EXPLOSION -> DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1,
									t.getZ(), 2, 0.3, 0.3, 0.3, 0);
							default -> {
							}
						}
					}
					if (kind == Bucket.EXPLOSION) {
						for (int i = 0; i < 8; i++) {
							Vec3 p = c.add(Vec3.directionFromRotation(0, i * 45f).scale(m.novaRadius * 0.55));
							DoomsdayFx.forced(level, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, p.x, d.getY() + 0.5, p.z, 1, 0, 0, 0, 0);
						}
					}
				}
			}
		}

		private void tickBreakFree(ServerLevel level) {
			if (timer == 2) {
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3.0f, 0.4f);
			}
			if (timer == BREAK_AT) {
				d.entityData.set(DATA_BOUND, false);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 4.0f, 0.6f);
				level.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 4.0f, 0.45f);
				DoomsdayFx.forced(level, DoomsdayFx.CABLE, d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ(), 80, d.getBbWidth(),
						d.getBbHeight() * 0.4, d.getBbWidth(), 0.4);
				DoomsdayFx.shake(level, d.position(), 1.2f, 24, 48);
				for (LivingEntity t : d.targetsNear(d.position(), 8.0)) {
					if (hit(level, d, t, 6f)) {
						Vec3 out = new Vec3(t.getX() - d.getX(), 0, t.getZ() - d.getZ()).normalize();
						t.setDeltaMovement(out.x * 1.6, 0.6, out.z * 1.6);
						t.hurtMarked = true;
					}
				}
				DoomsdayEvent ev = d.event(level);
				if (ev != null) {
					ev.onBrokeFree(level);
				}
			}
			if (timer >= 40) {
				finish(0);
			}
		}
	}
}

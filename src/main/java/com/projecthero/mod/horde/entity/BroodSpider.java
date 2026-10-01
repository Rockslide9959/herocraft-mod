package com.projecthero.mod.horde.entity;

import java.util.Locale;
import java.util.UUID;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the Spider Horde's own spiders -- one entity type, eight {@link Variant}s, each with its own look (texture
 * and size) and its own trick. Six of them match the Zombie Horde's six zombie kinds role for role; the Broodmother's
 * spiderlings (and the Brood Queen's) are the seventh and eighth:
 * <ul>
 *   <li><b>Hunter</b> (the baby zombie's job): small, ember-orange and very fast, pounces in short hops;</li>
 *   <li><b>Ironback Brute</b> (the armoured zombie): one and a half times the size, iron-plated (14 armour), hits hard
 *       and hurls you back; its plates halve arrow damage;</li>
 *   <li><b>Venom Spitter</b> (the husk): green, keeps its distance spitting venom globs that poison;</li>
 *   <li><b>Acid Burster</b> (the acid zombie): bloated and yellow -- get close and it hisses, swells and bursts into a
 *       cloud of acid. Killed before it bursts, it still leaves a small puddle;</li>
 *   <li><b>Trapdoor Leaper</b>: purple; crouches (a clear tell) and then leaps onto you from up to 16 blocks;</li>
 *   <li><b>Broodmother</b> (the Juggernaut): huge, slow, egg-spotted; births spiderlings as she goes and bursts into a
 *       swarm of them when she dies;</li>
 *   <li><b>Shadow Stalker</b>: near-black and invisible -- only its eyes show -- until it is close or hurt; its first
 *       bite from hiding hits double and blinds;</li>
 *   <li><b>Spiderling</b>: tiny, pale and quick; never a wave mob, only born. Lives a minute (or as long as the Brood
 *       Queen that birthed it).</li>
 * </ul>
 * Like the Horde Spider it never loses interest in daylight and never despawns on its own.
 */
public class BroodSpider extends Spider {
	public enum Variant {
		//          hp    speed  dmg  armour tough  kb   scale
		HUNTER(16, 0.48, 4, 0, 0, 0.0, 0.8f),
		BRUTE(60, 0.30, 8, 14, 4, 0.8, 1.6f),
		VENOM(22, 0.34, 3, 0, 0, 0.0, 1.0f),
		BURSTER(20, 0.40, 3, 0, 0, 0.0, 1.1f),
		LEAPER(26, 0.38, 6, 2, 0, 0.0, 1.0f),
		BROODMOTHER(70, 0.27, 6, 8, 2, 0.6, 1.8f),
		STALKER(24, 0.40, 6, 0, 0, 0.0, 1.0f),
		SPIDERLING(6, 0.44, 2, 0, 0, 0.0, 0.45f);

		public final double health;
		public final double speed;
		public final double damage;
		public final double armor;
		public final double toughness;
		public final double knockbackResistance;
		public final float scale;

		Variant(double health, double speed, double damage, double armor, double toughness, double kb, float scale) {
			this.health = health;
			this.speed = speed;
			this.damage = damage;
			this.armor = armor;
			this.toughness = toughness;
			this.knockbackResistance = kb;
			this.scale = scale;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Variant byId(int id) {
			Variant[] all = values();
			return id >= 0 && id < all.length ? all[id] : HUNTER;
		}

		/** The seven a horde can send (spiderlings are only ever born). */
		public static final Variant[] WAVE_VARIANTS = { HUNTER, BRUTE, VENOM, BURSTER, LEAPER, BROODMOTHER, STALKER };
	}

	private static final EntityDataAccessor<Byte> DATA_VARIANT = SynchedEntityData.defineId(BroodSpider.class, EntityDataSerializers.BYTE);
	private static final DustParticleOptions ACID = new DustParticleOptions(new Vector3f(0.75f, 0.95f, 0.2f), 1.4f);
	/** A spiderling with no queen lives this long. */
	public static final int SPIDERLING_LIFESPAN = 60 * 20;
	public static final int BURSTER_FUSE = 30;
	public static final int LEAPER_CROUCH = 12;
	public static final double STALKER_REVEAL_RANGE = 6.0;

	private boolean variantChosen;
	private int abilityCooldown = 40;
	/** Burster: ticks left on the fuse, -1 when not lit. */
	private int fuse = -1;
	/** Leaper: ticks left crouched before the jump, -1 when not crouching. */
	private int crouch = -1;
	private boolean leaping;
	private int airTicks;
	/** Stalker: hidden right now, and its next bite is an ambush. */
	private boolean ambush;
	private int lastHurtTick = -1000;
	/** Spiderling: the queen (or Broodmother) that birthed it. */
	private UUID mother;
	private int age;
	private boolean burst;

	public BroodSpider(EntityType<? extends Spider> type, Level level) {
		super(type, level);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Spider.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.36)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.FOLLOW_RANGE, 40.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, (byte) Variant.HUNTER.ordinal());
	}

	public Variant variant() {
		return Variant.byId(this.entityData.get(DATA_VARIANT));
	}

	/** Picks the variant and sets its stats (full health). */
	public void setVariant(Variant v) {
		this.entityData.set(DATA_VARIANT, (byte) v.ordinal());
		variantChosen = true;
		applyStats(v);
		setHealth(getMaxHealth());
		this.xpReward = switch (v) {
			case BRUTE, BROODMOTHER -> 20;
			case SPIDERLING -> 1;
			default -> 8;
		};
	}

	private void applyStats(Variant v) {
		base(Attributes.MAX_HEALTH, v.health);
		base(Attributes.MOVEMENT_SPEED, v.speed);
		base(Attributes.ATTACK_DAMAGE, v.damage);
		base(Attributes.ARMOR, v.armor);
		base(Attributes.ARMOR_TOUGHNESS, v.toughness);
		base(Attributes.KNOCKBACK_RESISTANCE, v.knockbackResistance);
		base(Attributes.SCALE, v.scale);
		refreshDimensions(); // vanilla only re-reads SCALE on its next tick
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance a = getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	public void setMother(UUID mother) {
		this.mother = mother;
	}

	public boolean isFuseLit() {
		return fuse >= 0;
	}

	public boolean isCrouching() {
		return crouch >= 0;
	}

	public boolean hasAmbush() {
		return ambush;
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.projecthero.brood_spider." + variant().id());
	}

	/** Vanilla spiders lose interest in daylight; a horde's never does. */
	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, true));
		this.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this,
				net.minecraft.world.entity.player.Player.class, false));
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
			SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
		// vanilla's random hard-mode spider effects (one is invisibility) would muddle the variants' looks
		removeAllEffects();
		ejectPassengers();
		if (!variantChosen) {
			setVariant(Variant.WAVE_VARIANTS[getRandom().nextInt(Variant.WAVE_VARIANTS.length)]);
		}
		if (variant() == Variant.STALKER) {
			hide();
		}
		return out;
	}

	// ---------------------------------------------------------------- behaviour

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		age++;
		if (abilityCooldown > 0) {
			abilityCooldown--;
		}
		LivingEntity target = getTarget();
		boolean hasTarget = target != null && target.isAlive();
		switch (variant()) {
			case HUNTER -> {
				if (hasTarget && abilityCooldown <= 0 && onGround()) {
					double d = distanceTo(target);
					if (d > 3.0 && d < 7.0) {
						hop(target, 0.9, 0.35);
						abilityCooldown = 40 + getRandom().nextInt(20);
					}
				}
			}
			case VENOM -> {
				if (hasTarget && abilityCooldown <= 0) {
					double d = distanceTo(target);
					if (d > 4.0 && d < 16.0 && getSensing().hasLineOfSight(target)) {
						HordeSpider.spit(this, target, WebShotEntity.Mode.VENOM, 3.0f);
						abilityCooldown = 50 + getRandom().nextInt(30);
					}
				}
			}
			case BURSTER -> tickBurster(level, target, hasTarget);
			case LEAPER -> tickLeaper(level, target, hasTarget);
			case BROODMOTHER -> {
				if (hasTarget && abilityCooldown <= 0) {
					int nearby = level.getEntitiesOfClass(BroodSpider.class, getBoundingBox().inflate(12.0),
							s -> s.variant() == Variant.SPIDERLING && getUUID().equals(s.mother)).size();
					if (nearby < 4) {
						level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_CRACK, SoundSource.HOSTILE, 1.0f, 0.7f);
						for (int i = 0; i < 2; i++) {
							spawnSpiderling(level, position().add(getRandom().nextGaussian() * 0.6, 0.3, getRandom().nextGaussian() * 0.6),
									target, getUUID(), false);
						}
					}
					abilityCooldown = 240 + getRandom().nextInt(60);
				}
			}
			case STALKER -> tickStalker(level, target, hasTarget);
			case SPIDERLING -> {
				if (age % 20 == 0) {
					boolean motherAlive = mother != null && level.getEntity(mother) instanceof BroodQueen q && q.isAlive();
					if (!motherAlive && age > SPIDERLING_LIFESPAN) {
						level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.2, getZ(), 6, 0.2, 0.1, 0.2, 0.02);
						discard();
					}
				}
			}
			default -> {
			}
		}
	}

	private void hop(LivingEntity target, double horizontal, double up) {
		Vec3 to = target.position().subtract(position()).multiply(1, 0, 1).normalize();
		setDeltaMovement(to.x * horizontal, up, to.z * horizontal);
		hasImpulse = true;
	}

	private void tickBurster(ServerLevel level, LivingEntity target, boolean hasTarget) {
		if (fuse < 0) {
			if (hasTarget && distanceTo(target) < 2.8) {
				fuse = BURSTER_FUSE;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1.0f, 1.6f);
			}
			return;
		}
		if (!hasTarget || distanceTo(target) > 6.0) {
			fuse = -1; // it let you go: the fuse goes out
			return;
		}
		getNavigation().stop();
		setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
		if (fuse % 3 == 0) {
			level.sendParticles(ACID, getX(), getY() + getBbHeight() * 0.6, getZ(), 4, getBbWidth() * 0.4, 0.2, getBbWidth() * 0.4, 0);
		}
		if (--fuse <= 0) {
			burst(level, true);
			discard();
		}
	}

	/** The Acid Burster's pop: a blast of acid (full, when the fuse runs out) or a small puddle (killed first). */
	private void burst(ServerLevel level, boolean full) {
		if (burst) {
			return;
		}
		burst = true;
		double radius = full ? 3.5 : 2.0;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.SLIME_DEATH, SoundSource.HOSTILE, 1.5f, 0.6f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, full ? 0.8f : 0.4f, 1.6f);
		level.sendParticles(ACID, getX(), getY() + 0.5, getZ(), full ? 50 : 20, radius * 0.5, 0.5, radius * 0.5, 0);
		level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.5, getZ(), full ? 30 : 10, radius * 0.4, 0.4, radius * 0.4, 0.1);
		if (full) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), e -> BroodQueen.prey(this, e))) {
				double d = Math.max(0, e.distanceTo(this) - 0.5);
				float dmg = (float) (10.0 * Math.max(0.3, 1.0 - d / radius));
				e.hurt(damageSources().mobAttack(this), dmg);
				e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
			}
		}
		AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
		cloud.setOwner(this);
		cloud.setRadius(full ? 3.0f : 1.8f);
		cloud.setRadiusPerTick(-cloud.getRadius() / (full ? 120f : 70f));
		cloud.setDuration(full ? 120 : 70);
		cloud.setWaitTime(5);
		cloud.setParticle(ACID);
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 60, full ? 1 : 0));
		level.addFreshEntity(cloud);
	}

	private void tickLeaper(ServerLevel level, LivingEntity target, boolean hasTarget) {
		if (leaping) {
			airTicks++;
			if ((onGround() && airTicks > 3) || isInWater() || airTicks > 60) {
				leaping = false;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 1.5f, 0.6f);
				level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 8, 0.6, 0.05, 0.6, 0.02);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.6, 0.8, 1.6),
						e -> BroodQueen.prey(this, e))) {
					e.hurt(damageSources().mobAttack(this), 8.0f);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
				}
			}
			return;
		}
		if (crouch >= 0) {
			getNavigation().stop();
			setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
			if (hasTarget) {
				getLookControl().setLookAt(target, 30f, 30f);
			}
			if (crouch % 3 == 0) {
				level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 0.3, getZ(), 3, 0.4, 0.1, 0.4, 0.05);
			}
			if (--crouch < 0) {
				if (hasTarget) {
					Vec3 to = target.position().subtract(position());
					double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
					double ticks = Math.max(6.0, horizontal / 1.1);
					setDeltaMovement(to.x / ticks, Math.min(1.3, 0.04 * ticks + to.y / ticks + 0.1), to.z / ticks);
					hasImpulse = true;
					leaping = true;
					airTicks = 0;
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.4f, 1.6f);
				}
				abilityCooldown = 80 + getRandom().nextInt(30);
			}
			return;
		}
		if (hasTarget && abilityCooldown <= 0 && onGround()) {
			double d = distanceTo(target);
			if (d > 5.0 && d < 16.0 && getSensing().hasLineOfSight(target)) {
				crouch = LEAPER_CROUCH;
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.5f);
			}
		}
	}

	private void hide() {
		addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false));
		ambush = true;
	}

	private void tickStalker(ServerLevel level, LivingEntity target, boolean hasTarget) {
		boolean close = hasTarget && distanceTo(target) <= STALKER_REVEAL_RANGE;
		boolean recentlyHurt = tickCount - lastHurtTick < 60;
		if (!close && !recentlyHurt) {
			if (!hasEffect(MobEffects.INVISIBILITY) || getEffect(MobEffects.INVISIBILITY).getDuration() < 20) {
				hide();
			}
			return;
		}
		if (hasEffect(MobEffects.INVISIBILITY)) {
			removeEffect(MobEffects.INVISIBILITY);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.4f);
			level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.4, getZ(), 12, 0.5, 0.3, 0.5, 0.02);
		}
		if (recentlyHurt) {
			ambush = false;
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (!hit || !(target instanceof LivingEntity living)) {
			return hit;
		}
		switch (variant()) {
			case BRUTE -> {
				Vec3 away = living.position().subtract(position()).multiply(1, 0, 1).normalize();
				living.push(away.x * 1.2, 0.45, away.z * 1.2);
				living.hurtMarked = true;
			}
			case STALKER -> {
				if (ambush) {
					ambush = false;
					living.invulnerableTime = 0;
					living.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
					living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
				}
			}
			default -> {
			}
		}
		return hit;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (variant() == Variant.BRUTE && source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile) {
			amount *= 0.5f; // the plates turn arrows
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			lastHurtTick = tickCount;
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		switch (variant()) {
			case BURSTER -> burst(level, false);
			case BROODMOTHER -> {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
				level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.6, getZ(), 20, 0.6, 0.3, 0.6, 0.1);
				int n = 4 + getRandom().nextInt(3);
				LivingEntity target = getTarget();
				for (int i = 0; i < n; i++) {
					spawnSpiderling(level, position().add(getRandom().nextGaussian() * 0.7, 0.3, getRandom().nextGaussian() * 0.7),
							target, null, false);
				}
			}
			default -> {
			}
		}
	}

	/** One spiderling at {@code at}, hunting {@code target}; {@code mother} keeps it alive while she lives. */
	public static BroodSpider spawnSpiderling(ServerLevel level, Vec3 at, LivingEntity target, UUID mother, boolean leap) {
		BroodSpider s = HordeEntityTypes.BROOD_SPIDER.create(level);
		if (s == null) {
			return null;
		}
		s.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		s.setVariant(Variant.SPIDERLING);
		s.finalizeSpawn(level, level.getCurrentDifficultyAt(s.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
		s.setMother(mother);
		if (target != null && target.isAlive()) {
			s.setTarget(target);
		}
		if (leap) {
			s.setDeltaMovement(level.random.nextGaussian() * 0.35, 0.45, level.random.nextGaussian() * 0.35);
		}
		level.addFreshEntity(s);
		return s;
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("BroodVariant", variant().id());
		tag.putInt("BroodAge", age);
		if (mother != null) {
			tag.putUUID("BroodMother", mother);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		if (tag.contains("BroodVariant")) {
			for (Variant v : Variant.values()) {
				if (v.id().equals(tag.getString("BroodVariant"))) {
					this.entityData.set(DATA_VARIANT, (byte) v.ordinal());
					variantChosen = true;
					if (!tag.contains("attributes")) {
						// a /summon with just {BroodVariant:"brute"}: no saved stats to restore, so give it the variant's own
						applyStats(v);
						setHealth(getMaxHealth());
					}
				}
			}
		}
		age = tag.getInt("BroodAge");
		mother = tag.hasUUID("BroodMother") ? tag.getUUID("BroodMother") : null;
	}
}

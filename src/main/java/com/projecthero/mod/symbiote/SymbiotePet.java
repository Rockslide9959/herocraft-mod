package com.projecthero.mod.symbiote;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.SonicVulnerability;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.14.4: a <b>Symbiote Pet</b> -- a tamed wolf or cat carrying a Symbiote. Unlike every other creature the
 * Symbiote takes over, it keeps its loyalty: the organism fights <em>for</em> its owner.
 *
 * <h2>How a pet gets one</h2>
 * <ul>
 *   <li><b>Shared</b>: its owner, bonded and suited up, sneaks and right-clicks it with an empty hand. A Normal
 *       host pays {@value #SHARE_BIOMASS_COST} Biomass for it; Black Suit Spider-Man and Agent Venom pay
 *       nothing (they have no Biomass bar).</li>
 *   <li><b>Wild</b>: a free {@link SymbioteEntity} crawls onto a tamed wolf/cat ({@link SymbioteEntity#takeOver}),
 *       or a player tames an infested wild wolf / stray cat ({@link SymbioteHost#serverTick}).</li>
 * </ul>
 *
 * <h2>What it gets</h2>
 * Bigger (+30% scale), +{@value #BONUS_HEALTH} max health, +{@value #BONUS_DAMAGE} attack damage, +25% speed,
 * +{@value #BONUS_ARMOR} armour, knockback resistance and a taller step -- all as <em>permanent attribute
 * modifiers</em>, which vanilla saves with the entity, so a reload needs no re-apply. It regenerates
 * ({@value #REGEN_COMBAT} HP/s, {@value #REGEN_CALM} HP/s once it has not been hurt for 5 s), and it fights
 * with three powers on its current target: <b>Tendril Lash</b> (yank a target 4-14 blocks away in),
 * <b>Pounce</b> (leap onto a target 2.5-9 blocks away) and <b>Spike Burst</b> (an all-round eruption when
 * two or more foes crowd it, or when it is badly hurt). Cats get real combat AI ({@link SymbioteMobGoals}).
 *
 * <h2>Loyalty</h2>
 * It can never target (a {@code Mob#setTarget} filter, {@link #refuses}) or damage (an ALLOW_DAMAGE veto) its
 * owner, its owner's squadmates, or their pets; its Spike Burst only ever hits hostile mobs and its current
 * target.
 *
 * <h2>How it ends</h2>
 * Its owner repeats the sneak + empty-hand + suited right-click (the Symbiote flows back); a loud sound -- a
 * bell, a goat horn, a sonic boom -- shakes it loose (the Symbiote's sonic weakness); or the pet dies. Fire
 * burns it 50% harder. Only a <em>wild</em> Symbiote tears free as a new free Symbiote when it leaves; one its
 * owner shared simply dissolves, so sharing can never be used to farm free Symbiotes.
 */
public final class SymbiotePet {
	public static final int SHARED = 1;
	public static final int WILD = 2;

	public static final float SHARE_BIOMASS_COST = 40.0f;

	static final double BONUS_HEALTH = 20.0;
	static final double BONUS_DAMAGE = 4.0;
	static final double BONUS_SPEED = 0.25;
	static final double BONUS_ARMOR = 8.0;
	static final double BONUS_KNOCKBACK = 0.5;
	static final double BONUS_SCALE = 0.3;
	static final double BONUS_STEP = 0.4;

	public static final float REGEN_COMBAT = 1.0f;
	public static final float REGEN_CALM = 3.0f;
	private static final int CALM_AFTER_TICKS = 100;
	private static final float FIRE_MULTIPLIER = 1.5f;

	public static final int LASH_COOLDOWN = 160;
	public static final int POUNCE_COOLDOWN = 100;
	public static final int SPIKE_COOLDOWN = 240;
	private static final double SPIKE_RADIUS = 3.5;
	private static final int TOGGLE_GUARD_TICKS = 10;

	private static final DustParticleOptions ICHOR = new DustParticleOptions(new Vector3f(0.06f, 0.03f, 0.09f), 0.9f);
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private static final ResourceLocation MOD_HEALTH = ProjectHeroMod.id("symbiote_pet_health");
	private static final ResourceLocation MOD_DAMAGE = ProjectHeroMod.id("symbiote_pet_damage");
	private static final ResourceLocation MOD_SPEED = ProjectHeroMod.id("symbiote_pet_speed");
	private static final ResourceLocation MOD_ARMOR = ProjectHeroMod.id("symbiote_pet_armor");
	private static final ResourceLocation MOD_KNOCKBACK = ProjectHeroMod.id("symbiote_pet_knockback");
	private static final ResourceLocation MOD_SCALE = ProjectHeroMod.id("symbiote_pet_scale");
	private static final ResourceLocation MOD_STEP = ProjectHeroMod.id("symbiote_pet_step");

	private SymbiotePet() {
	}

	/** Transient per-entity combat state (a non-persistent attachment, so nothing static to leak). */
	public static final class Brain {
		public long lashReadyAt;
		public long pounceReadyAt;
		public long spikeReadyAt;
		public int pounceTicks;
		public int pounceTargetId = -1;
		public long toggledAt = -1_000_000L; // not MIN_VALUE: "now - toggledAt" would overflow
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register(SymbiotePet::onUseEntity);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SymbiotePet::onAllowDamage);
		// Goals are never saved with an entity -- put the Symbiote's back whenever a host / pet (re)enters a world.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				SymbioteMobGoals.onLoad(mob);
			}
		});
	}

	// ---------------- queries ----------------

	/** {@link #SHARED}, {@link #WILD}, or 0 when this is not a Symbiote Pet. Client-safe (synced). */
	public static int origin(Entity entity) {
		return entity instanceof LivingEntity le ? le.getAttachedOrElse(ModAttachments.SYMBIOTE_PET, 0) : 0;
	}

	public static boolean is(Entity entity) {
		return origin(entity) > 0;
	}

	public static boolean isPetSpecies(Entity entity) {
		return entity instanceof Wolf || entity instanceof Cat;
	}

	/** A tamed, owned wolf or cat that carries no Symbiote yet. */
	public static boolean canBond(Mob mob) {
		return mob instanceof TamableAnimal t && isPetSpecies(mob) && t.isTame() && t.getOwnerUUID() != null
				&& mob.isAlive() && !is(mob) && !SymbioteHost.is(mob);
	}

	/**
	 * Would a Symbiote Pet refuse to fight {@code target}? Its owner, its owner's squadmates, and any pet owned by
	 * one of them. False for anything that is not a Symbiote Pet.
	 */
	public static boolean refuses(Mob pet, LivingEntity target) {
		if (target == null || !is(pet) || !(pet instanceof OwnableEntity ownable)) {
			return false;
		}
		UUID ownerId = ownable.getOwnerUUID();
		if (ownerId == null) {
			return false;
		}
		if (ownerId.equals(target.getUUID())) {
			return true;
		}
		LivingEntity owner = ownable.getOwner();
		if (owner != null && Squads.areAllies(owner, target)) {
			return true;
		}
		if (target instanceof OwnableEntity other && other.getOwnerUUID() != null) {
			if (ownerId.equals(other.getOwnerUUID())) {
				return true;
			}
			LivingEntity otherOwner = other.getOwner();
			return owner != null && otherOwner != null && Squads.areAllies(owner, otherOwner);
		}
		return false;
	}

	/** Something the Spike Burst may hit: the pet's current target, or any hostile mob it does not refuse. */
	static boolean isFoe(Mob pet, LivingEntity e, LivingEntity target) {
		if (e == pet || !e.isAlive() || refuses(pet, e) || e instanceof Creeper && e != target) {
			return false;
		}
		return e == target || e instanceof Enemy && !(e instanceof Player);
	}

	// ---------------- bond / release ----------------

	/** Bond a Symbiote with a tamed wolf or cat. False (no change) if it cannot take one. */
	public static boolean bond(TamableAnimal pet, int origin) {
		if (!(pet.level() instanceof ServerLevel level) || !canBond(pet)) {
			return false;
		}
		pet.setAttached(ModAttachments.SYMBIOTE_PET, origin == SHARED ? SHARED : WILD);
		applyModifiers(pet);
		pet.setHealth(pet.getMaxHealth());
		pet.clearFire();
		SymbioteMobGoals.installPet(pet);
		transformFx(level, pet, true);
		if (pet.getOwner() instanceof ServerPlayer owner) {
			owner.displayClientMessage(Component.translatable(origin == SHARED
					? "message.projecthero.symbiote.pet_shared" : "message.projecthero.symbiote.pet_bonded",
					pet.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
		}
		return true;
	}

	/**
	 * The Symbiote leaves the pet: modifiers, goals and state go. With {@code tearFree}, a <em>wild</em> Symbiote
	 * crawls out as a new free Symbiote (which leaves this pet alone for a minute); a shared one just dissolves.
	 */
	public static void release(Mob pet, boolean tearFree) {
		int origin = origin(pet);
		if (origin == 0) {
			return;
		}
		pet.removeAttached(ModAttachments.SYMBIOTE_PET);
		removeModifiers(pet);
		if (pet.getHealth() > pet.getMaxHealth()) {
			pet.setHealth(pet.getMaxHealth());
		}
		SymbioteMobGoals.removeAll(pet);
		if (pet.level() instanceof ServerLevel level) {
			transformFx(level, pet, false);
			if (tearFree && origin == WILD) {
				SymbioteEntity free = SymbioteEntity.spawn(level, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5 + 0.2,
						pet.getZ());
				if (free != null) {
					free.setHuntDelay(200);
					free.ignoreHost(pet.getUUID(), 1200);
				}
			}
		}
	}

	/** Death hook (from {@link SymbioteHost#onDeath}). */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !is(mob)) {
			return;
		}
		level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.5, mob.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
		if (origin(mob) == WILD) {
			SymbioteEntity free = SymbioteEntity.spawn(level, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5 + 0.2, mob.getZ());
			if (free != null) {
				free.setHuntDelay(100);
			}
		}
	}

	private static void applyModifiers(Mob pet) {
		modifier(pet, Attributes.MAX_HEALTH, MOD_HEALTH, BONUS_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.ATTACK_DAMAGE, MOD_DAMAGE, BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.MOVEMENT_SPEED, MOD_SPEED, BONUS_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(pet, Attributes.ARMOR, MOD_ARMOR, BONUS_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, BONUS_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.SCALE, MOD_SCALE, BONUS_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(pet, Attributes.STEP_HEIGHT, MOD_STEP, BONUS_STEP, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void removeModifiers(Mob pet) {
		for (var pair : List.of(
				java.util.Map.entry(Attributes.MAX_HEALTH, MOD_HEALTH),
				java.util.Map.entry(Attributes.ATTACK_DAMAGE, MOD_DAMAGE),
				java.util.Map.entry(Attributes.MOVEMENT_SPEED, MOD_SPEED),
				java.util.Map.entry(Attributes.ARMOR, MOD_ARMOR),
				java.util.Map.entry(Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK),
				java.util.Map.entry(Attributes.SCALE, MOD_SCALE),
				java.util.Map.entry(Attributes.STEP_HEIGHT, MOD_STEP))) {
			AttributeInstance instance = pet.getAttribute(pair.getKey());
			if (instance != null) {
				instance.removeModifier(pair.getValue());
			}
		}
	}

	private static void modifier(Mob pet, Holder<Attribute> attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = pet.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
		}
	}

	// ---------------- owner interaction ----------------

	/** Sneak + empty hand + suited up, on your own wolf or cat: share your Symbiote, or call it back. */
	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity,
			EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()
				|| !player.getMainHandItem().isEmpty() || !(entity instanceof TamableAnimal pet)
				|| !isPetSpecies(pet) || !pet.isTame() || !pet.isOwnedBy(player)
				|| !Symbiote.hasSymbiote(player) || !Symbiote.isActive(player) || SymbioteHost.is(pet)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		// interactAt and interact can both reach the server for one click -- never toggle twice.
		Brain brain = pet.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN);
		long now = level.getGameTime();
		if (now - brain.toggledAt < TOGGLE_GUARD_TICKS) {
			return InteractionResult.SUCCESS;
		}
		brain.toggledAt = now;
		if (is(pet)) {
			release(pet, false);
			sp.displayClientMessage(Component.translatable("message.projecthero.symbiote.pet_recalled",
					pet.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
			return InteractionResult.SUCCESS;
		}
		if (Symbiote.isNormalHost(sp)) {
			SymbioteVitals vitals = SymbioteVitalsManager.vitals(sp);
			if (vitals.broken || vitals.hp < SHARE_BIOMASS_COST) {
				sp.displayClientMessage(Component.translatable("message.projecthero.symbiote.pet_too_weak",
						(int) SHARE_BIOMASS_COST).withStyle(ChatFormatting.RED), true);
				return InteractionResult.SUCCESS;
			}
			SymbioteVitalsManager.spendBiomass(sp, SHARE_BIOMASS_COST);
		}
		if (bond(pet, SHARED)) {
			SymbioteSounds.organic(sp, 1.0f, 0.8f);
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------- damage rules ----------------

	private static boolean onAllowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (source.getEntity() instanceof Mob attacker && refuses(attacker, victim)) {
			return false; // a Symbiote Pet never hurts its owner, their squad, or their pets
		}
		if (!REENTRANT.get() && is(victim) && source.is(DamageTypeTags.IS_FIRE) && amount > 0.0f) {
			REENTRANT.set(true);
			try {
				victim.hurt(source, amount * FIRE_MULTIPLIER);
			} finally {
				REENTRANT.set(false);
			}
			return false;
		}
		return true;
	}

	// ---------------- tick ----------------

	/** Per-tick upkeep, from the {@code Mob#aiStep} mixin. A no-op unless this mob is a Symbiote Pet. */
	public static void serverTick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level) || !is(mob) || !mob.isAlive()) {
			return;
		}
		if (!(mob instanceof TamableAnimal pet) || !pet.isTame()) {
			release(mob, true);
			return;
		}
		long now = level.getGameTime();
		if (SonicVulnerability.isDisrupted(mob, now)) {
			if (pet.getOwner() instanceof ServerPlayer owner && owner.distanceToSqr(pet) < 64.0 * 64.0) {
				owner.displayClientMessage(Component.translatable("message.projecthero.symbiote.pet_shaken",
						pet.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
			}
			level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.WARDEN_HURT, SoundSource.NEUTRAL, 0.8f, 1.6f);
			release(pet, true);
			return;
		}

		if (mob.tickCount % 20 == 0 && mob.getHealth() < mob.getMaxHealth()) {
			boolean calm = mob.tickCount - mob.getLastHurtByMobTimestamp() > CALM_AFTER_TICKS;
			mob.heal(calm ? REGEN_CALM : REGEN_COMBAT);
		}
		ambience(level, pet);

		LivingEntity target = mob.getTarget();
		if (target != null && refuses(mob, target)) {
			mob.setTarget(null);
			target = null;
		}
		Brain brain = mob.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN);
		tickPounce(level, pet, brain);
		if (target == null || !target.isAlive() || pet.isOrderedToSit() || pet.isInSittingPose()) {
			return;
		}
		double dist = mob.distanceTo(target);
		if (dist > 24.0) {
			return;
		}
		if (now >= brain.spikeReadyAt && spikeBurst(level, pet, target, brain, now)) {
			return;
		}
		if (now >= brain.lashReadyAt && dist >= 4.0 && dist <= 14.0 && mob.hasLineOfSight(target)) {
			tendrilLash(level, pet, target, brain, now);
			return;
		}
		if (now >= brain.pounceReadyAt && brain.pounceTicks <= 0 && dist >= 2.5 && dist <= 9.0 && mob.onGround()
				&& mob.hasLineOfSight(target)) {
			pounce(level, pet, target, brain, now);
		}
	}

	private static float damage(Mob pet) {
		AttributeInstance a = pet.getAttribute(Attributes.ATTACK_DAMAGE);
		return a == null ? 6.0f : (float) a.getValue();
	}

	private static Vec3 chest(Entity e) {
		return e.position().add(0.0, e.getBbHeight() * 0.55, 0.0);
	}

	/** Tendril Lash: a tendril snaps onto a distant target and yanks it in. */
	static void tendrilLash(ServerLevel level, TamableAnimal pet, LivingEntity target, Brain brain, long now) {
		brain.lashReadyAt = now + LASH_COOLDOWN;
		Vec3 from = chest(pet);
		SymbioteTendrilEntity.fromPoint(level, from, chest(target), target, 12, 4, 0.14f);
		target.hurt(pet.damageSources().mobAttack(pet), damage(pet) * 0.5f);
		Vec3 pull = from.subtract(target.position());
		double len = pull.length();
		if (len > 1.0e-3) {
			Vec3 v = pull.scale(1.0 / len).scale(Math.min(1.3, 0.35 + len * 0.08)).add(0.0, 0.3, 0.0);
			target.setDeltaMovement(v);
			target.hurtMarked = true;
			target.hasImpulse = true;
		}
		SymbioteSounds.organic(level, pet.getX(), pet.getY(), pet.getZ(), 1.0f, 1.2f);
		level.sendParticles(ICHOR, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
	}

	/** Pounce: an arcing leap onto the target; the first touch on the way down hits hard. */
	static void pounce(ServerLevel level, TamableAnimal pet, LivingEntity target, Brain brain, long now) {
		brain.pounceReadyAt = now + POUNCE_COOLDOWN;
		Vec3 d = target.position().subtract(pet.position());
		Vec3 v = AbilityHelpers.ballisticLaunch(d, d.length(), true);
		if (v.length() > 1.6) {
			v = v.normalize().scale(1.6);
		}
		pet.setDeltaMovement(v);
		pet.hasImpulse = true;
		brain.pounceTicks = 25;
		brain.pounceTargetId = target.getId();
		level.sendParticles(ParticleTypes.SQUID_INK, pet.getX(), pet.getY() + 0.3, pet.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
		SymbioteSounds.organic(level, pet.getX(), pet.getY(), pet.getZ(), 0.8f, 0.7f);
	}

	private static void tickPounce(ServerLevel level, TamableAnimal pet, Brain brain) {
		if (brain.pounceTicks <= 0) {
			return;
		}
		brain.pounceTicks--;
		if (level.getEntity(brain.pounceTargetId) instanceof LivingEntity target && target.isAlive()
				&& pet.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
			brain.pounceTicks = 0;
			if (!refuses(pet, target) && target.hurt(pet.damageSources().mobAttack(pet), damage(pet) * 1.25f)) {
				target.knockback(0.7, pet.getX() - target.getX(), pet.getZ() - target.getZ());
				pet.setLastHurtMob(target);
			}
			level.sendParticles(ICHOR, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 16, 0.4, 0.3, 0.4, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8f, 0.7f);
			return;
		}
		if (brain.pounceTicks < 18 && pet.onGround()) {
			brain.pounceTicks = 0; // landed short
		}
	}

	/**
	 * Spike Burst: spikes erupt all round it. Fires when two or more foes crowd it, or when it is under 40% health
	 * with any foe in reach. Returns whether it fired.
	 */
	static boolean spikeBurst(ServerLevel level, TamableAnimal pet, LivingEntity target, Brain brain, long now) {
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, pet.getBoundingBox().inflate(SPIKE_RADIUS),
				e -> isFoe(pet, e, target));
		boolean desperate = pet.getHealth() < pet.getMaxHealth() * 0.4f;
		if (foes.size() < 2 && !(desperate && !foes.isEmpty())) {
			return false;
		}
		brain.spikeReadyAt = now + SPIKE_COOLDOWN;
		float dmg = damage(pet) * 0.9f;
		for (LivingEntity foe : foes) {
			if (foe.hurt(pet.damageSources().mobAttack(pet), dmg)) {
				foe.knockback(0.8, pet.getX() - foe.getX(), pet.getZ() - foe.getZ());
			}
		}
		Vec3 c = chest(pet);
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2.0 * i / 8.0 + level.random.nextDouble() * 0.3;
			Vec3 end = c.add(Math.cos(a) * SPIKE_RADIUS, 0.2 + level.random.nextDouble() * 0.8, Math.sin(a) * SPIKE_RADIUS);
			SymbioteTendrilEntity.fromPoint(level, c, end, null, 10, 3, 0.09f);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 24, 0.8, 0.4, 0.8, 0.12);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 1.2, 0.5, 1.2, 0.2);
		SymbioteSounds.organic(level, c.x, c.y, c.z, 1.2f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0f, 0.6f);
		return true;
	}

	// ---------------- fx ----------------

	private static void ambience(ServerLevel level, Mob pet) {
		if (pet.tickCount % 8 == 0) {
			level.sendParticles(ICHOR, pet.getX(), pet.getY() + pet.getBbHeight() * 0.6, pet.getZ(),
					1, pet.getBbWidth() * 0.4, pet.getBbHeight() * 0.3, pet.getBbWidth() * 0.4, 0.0);
		}
		if ((pet.tickCount + pet.getId()) % 37 == 0) {
			level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5,
					pet.getZ(), 1, pet.getBbWidth() * 0.3, 0.1, pet.getBbWidth() * 0.3, 0.0);
		}
	}

	private static void transformFx(ServerLevel level, Mob pet, boolean bonding) {
		double cx = pet.getX();
		double cy = pet.getY() + pet.getBbHeight() * 0.5;
		double cz = pet.getZ();
		double w = pet.getBbWidth() * 0.6;
		double h = pet.getBbHeight() * 0.5;
		level.sendParticles(ParticleTypes.SQUID_INK, cx, cy, cz, bonding ? 40 : 25, w, h, w, 0.06);
		level.sendParticles(ICHOR, cx, cy, cz, 30, w, h, w, 0.0);
		if (bonding) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 5, w, h, w, 0.02);
			level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 1.0f, 0.9f);
		}
		SymbioteSounds.organic(level, cx, cy, cz, 1.2f, bonding ? 0.5f : 1.3f);
	}
}

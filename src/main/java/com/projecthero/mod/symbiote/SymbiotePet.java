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

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * v0.14.4: a <b>Symbiote Pet</b> -- a tamed wolf or cat that is a Symbiote's host. Unlike every other creature the
 * Symbiote takes over, it keeps its loyalty: the organism fights <em>for</em> its owner.
 *
 * <h2>How a pet gets one</h2>
 * <ul>
 *   <li><b>Wild</b>: a free {@link SymbioteEntity} crawls onto a tamed wolf/cat -- it actively seeks them out
 *       ({@link SymbioteEntity#isValidHost}) -- or a player tames an infested wild wolf / stray cat
 *       ({@link SymbioteHost#serverTick}).</li>
 *   <li><b>Shared</b>: its owner, bonded and suited up, sneaks and right-clicks it with an empty hand. A Normal
 *       host pays {@value #SHARE_BIOMASS_COST} Biomass for it; Black Suit Spider-Man and Agent Venom pay
 *       nothing (they have no Biomass bar).</li>
 * </ul>
 * Either way the owner is told, and the pet stays theirs: it sits, stands, follows and teleports exactly as before
 * (the Symbiote adds no movement goal a sit order does not beat, and none of its powers fire while it sits).
 *
 * <h2>Pet hosts: transformed only in combat (v0.14.4 rework)</h2>
 * Out of combat it looks like its normal self -- a few black blotches and the odd drip give it away. The moment it
 * is in a fight ({@link #inCombat}: it has a target, it was hurt, it bit something, or its owner is fighting or in
 * danger nearby) it <b>transforms</b>: {@link ModAttachments#SYMBIOTE_PET_FORM} records the tick,
 * every client spreads the black skin over its texture pixel by pixel from it ({@code SymbiotePetSkin}), the white
 * eyes open, it grows 30% (eased, {@link #easeScale}) and the combat modifiers switch on. {@value #COMBAT_TIMEOUT}
 * ticks after the last sign of a fight it <b>detransforms</b>: the skin recedes the same way, it shrinks back, the
 * modifiers go. The modifiers are <em>transient</em> with fixed ids, so toggling is idempotent and nothing about the
 * form is saved -- a reloaded pet comes back in its normal form and re-transforms when it next fights. Pets saved
 * before the rework carry the old <em>permanent</em> modifiers under the same ids; the first tick strips them
 * ({@link Brain#reconciled}).
 *
 * <h2>Transformed</h2>
 * +{@value #BONUS_HEALTH} max health, +{@value #BONUS_DAMAGE} attack damage, +25% speed, +{@value #BONUS_ARMOR}
 * armour, knockback resistance, a taller step, +30% size and {@value #REGEN_TRANSFORMED} HP/s regeneration
 * ({@value #REGEN_DORMANT} HP/s in its normal form). Powers, on its current target: <b>Spike Burst</b> (crowded or
 * badly hurt), <b>Latching Bite</b> (a savage bite whose tendrils pin the target), <b>Tendril Lash</b> (yank a target
 * 4-14 blocks away in), <b>Pounce</b> (leap onto a target 2.5-9 blocks away) -- and <b>Guardian Shroud</b>: when its
 * owner is close to death it wraps them in tendrils (absorption + resistance) and throws their attackers back.
 *
 * <h2>Loyalty</h2>
 * It can never target (a {@code Mob#setTarget} filter, {@link #refuses}) or damage (an ALLOW_DAMAGE veto) its
 * owner, its owner's squadmates, or their pets; its area moves only ever hit hostile mobs and its current target.
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
	public static final double BONUS_SCALE = 0.3;
	static final double BONUS_STEP = 0.4;

	/** How long the skin takes to spread over it / recede, in ticks. */
	public static final int TRANSFORM_TICKS = 30;
	public static final int DETRANSFORM_TICKS = 40;
	/** Ticks after the last sign of a fight before it goes back to its normal self (9 s). */
	public static final int COMBAT_TIMEOUT = 180;
	/** How recent a hit (given or taken, by it or its owner) must be to count as "in a fight". */
	private static final int RECENT_TICKS = 60;
	private static final double OWNER_RANGE = 20.0;

	public static final float REGEN_TRANSFORMED = 1.5f;
	public static final float REGEN_DORMANT = 0.5f;
	private static final float TRANSFORM_HEAL = 6.0f;
	private static final float FIRE_MULTIPLIER = 1.5f;

	public static final int LASH_COOLDOWN = 160;
	public static final int POUNCE_COOLDOWN = 100;
	public static final int SPIKE_COOLDOWN = 240;
	public static final int BITE_COOLDOWN = 140;
	public static final int SHROUD_COOLDOWN = 900;
	private static final double SPIKE_RADIUS = 3.5;
	private static final double BITE_REACH = 2.8;
	private static final double SHROUD_RANGE = 16.0;
	private static final float OWNER_DANGER = 0.4f;
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
		public long biteReadyAt;
		public long shroudReadyAt;
		public int pounceTicks;
		public int pounceTargetId = -1;
		public long toggledAt = -1_000_000L; // not MIN_VALUE: "now - toggledAt" would overflow
		/** The last tick anything said "fight" -- the transform lasts {@link #COMBAT_TIMEOUT} past it. */
		public long lastCombatAt = -1_000_000L;
		/** Set once this entity instance has had stale (pre-rework / pre-reload) modifiers reconciled. */
		public boolean reconciled;
	}

	/**
	 * The pet's current form, synced to every client ({@link ModAttachments#SYMBIOTE_PET_FORM}): transformed or not,
	 * and the game tick the change began (back-dated when a change interrupts the previous one, so the reveal is
	 * continuous).
	 */
	public record Form(boolean on, long since) {
		public static final StreamCodec<ByteBuf, Form> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Form::on, ByteBufCodecs.VAR_LONG, Form::since, Form::new);
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

	/** Is this Symbiote Pet in its combat form (or turning into it)? Client-safe (synced). */
	public static boolean isTransformed(Entity entity) {
		Form form = entity.getAttached(ModAttachments.SYMBIOTE_PET_FORM);
		return form != null && form.on();
	}

	/**
	 * How far the Symbiote skin covers the pet at game tick {@code now} (+{@code partial}): 0 = its normal self,
	 * 1 = fully transformed. Client-safe -- this is what the pixel reveal and the scale easing both follow.
	 */
	public static float formProgress(Entity entity, long now, float partial) {
		Form form = entity.getAttached(ModAttachments.SYMBIOTE_PET_FORM);
		if (form == null) {
			return 0.0f;
		}
		float t = (now - form.since() + partial) / (float) (form.on() ? TRANSFORM_TICKS : DETRANSFORM_TICKS);
		t = Math.max(0.0f, Math.min(1.0f, t));
		return form.on() ? t : 1.0f - t;
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

	/** Something an area move may hit: the pet's current target, or any hostile mob it does not refuse. */
	static boolean isFoe(Mob pet, LivingEntity e, LivingEntity target) {
		if (e == pet || !e.isAlive() || refuses(pet, e) || e instanceof Creeper && e != target) {
			return false;
		}
		return e == target || e instanceof Enemy && !(e instanceof Player);
	}

	static boolean sitting(TamableAnimal pet) {
		return pet.isOrderedToSit() || pet.isInSittingPose();
	}

	// ---------------- bond / release ----------------

	/**
	 * Bond a Symbiote with a tamed wolf or cat: it becomes the Symbiote's host, still its owner's pet. It announces
	 * itself with one transform (which recedes once it is clear there is no fight). False (no change) if it cannot
	 * take one.
	 */
	public static boolean bond(TamableAnimal pet, int origin) {
		if (!(pet.level() instanceof ServerLevel level) || !canBond(pet)) {
			return false;
		}
		pet.setAttached(ModAttachments.SYMBIOTE_PET, origin == SHARED ? SHARED : WILD);
		pet.clearFire();
		SymbioteMobGoals.installPet(pet);
		pet.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN).reconciled = true;
		bondFx(level, pet, true);
		enterCombat(pet);
		pet.setHealth(pet.getMaxHealth());
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
		pet.removeAttached(ModAttachments.SYMBIOTE_PET_FORM);
		removeCombatModifiers(pet);
		removeModifier(pet, Attributes.SCALE, MOD_SCALE);
		SymbioteMobGoals.removeAll(pet);
		if (pet.level() instanceof ServerLevel level) {
			bondFx(level, pet, false);
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

	// ---------------- the combat form ----------------

	/** Something says "fight": transform now (if it is not already) and hold the form for another timeout. */
	public static void enterCombat(TamableAnimal pet) {
		if (!(pet.level() instanceof ServerLevel level) || !is(pet)) {
			return;
		}
		long now = level.getGameTime();
		pet.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN).lastCombatAt = now;
		if (!isTransformed(pet)) {
			setTransformed(level, pet, true, now);
		}
	}

	/** Switch the form. The combat modifiers flip at once; the size and the skin ease over the transform. */
	static void setTransformed(ServerLevel level, TamableAnimal pet, boolean on, long now) {
		float p = formProgress(pet, now, 0.0f);
		long since = on ? now - Math.round(p * TRANSFORM_TICKS) : now - Math.round((1.0f - p) * DETRANSFORM_TICKS);
		pet.setAttached(ModAttachments.SYMBIOTE_PET_FORM, new Form(on, since));
		if (on) {
			applyCombatModifiers(pet);
			pet.heal(TRANSFORM_HEAL);
		} else {
			removeCombatModifiers(pet);
			Brain brain = pet.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN);
			brain.pounceTicks = 0;
		}
		formFx(level, pet, on);
	}

	/**
	 * Is it in a fight? It has a live target (unless it was told to sit), a mob hurt it or it bit something within
	 * {@value #RECENT_TICKS} ticks, or its owner -- within {@value #OWNER_RANGE} blocks -- is fighting or close to
	 * death. A sitting pet only reacts to being hurt itself or to its owner being in danger.
	 */
	static boolean inCombat(TamableAnimal pet, LivingEntity target, boolean sitting) {
		if (!sitting && target != null && target.isAlive()) {
			return true;
		}
		LivingEntity by = pet.getLastHurtByMob();
		if (by != null && by.isAlive() && pet.tickCount - pet.getLastHurtByMobTimestamp() < RECENT_TICKS
				&& !refuses(pet, by)) {
			return true;
		}
		LivingEntity hit = pet.getLastHurtMob();
		if (!sitting && hit != null && hit.isAlive() && pet.tickCount - pet.getLastHurtMobTimestamp() < RECENT_TICKS) {
			return true;
		}
		LivingEntity owner = pet.getOwner();
		if (owner == null || owner.level() != pet.level() || owner.distanceToSqr(pet) > OWNER_RANGE * OWNER_RANGE) {
			return false;
		}
		if (ownerInDanger(owner)) {
			return true;
		}
		if (sitting) {
			return false;
		}
		LivingEntity ownerHit = owner.getLastHurtMob();
		if (ownerHit != null && ownerHit.isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() < RECENT_TICKS
				&& !refuses(pet, ownerHit)) {
			return true;
		}
		LivingEntity ownerBy = owner.getLastHurtByMob();
		return ownerBy != null && ownerBy.isAlive() && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_TICKS
				&& !refuses(pet, ownerBy);
	}

	/** The owner is under {@value #OWNER_DANGER} of their health and has just been hurt. */
	static boolean ownerInDanger(LivingEntity owner) {
		if (!owner.isAlive() || owner instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return false;
		}
		if (owner.getHealth() >= owner.getMaxHealth() * OWNER_DANGER) {
			return false;
		}
		LivingEntity by = owner.getLastHurtByMob();
		return owner.getLastDamageSource() != null
				|| by != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_TICKS;
	}

	/** The size follows the skin: +30% scale eased in over the transform, out over the detransform. */
	static void easeScale(TamableAnimal pet, long now) {
		AttributeInstance instance = pet.getAttribute(Attributes.SCALE);
		if (instance == null) {
			return;
		}
		float p = formProgress(pet, now, 0.0f);
		double eased = p * p * (3.0f - 2.0f * p);
		double want = Math.round(BONUS_SCALE * eased * 200.0) / 200.0; // 0.005 steps: fewer attribute packets
		AttributeModifier have = instance.getModifier(MOD_SCALE);
		double current = have == null ? 0.0 : have.amount();
		if (Math.abs(current - want) < 1.0e-4) {
			return;
		}
		if (want <= 1.0e-4) {
			instance.removeModifier(MOD_SCALE);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(MOD_SCALE, want,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void applyCombatModifiers(Mob pet) {
		modifier(pet, Attributes.MAX_HEALTH, MOD_HEALTH, BONUS_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.ATTACK_DAMAGE, MOD_DAMAGE, BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.MOVEMENT_SPEED, MOD_SPEED, BONUS_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modifier(pet, Attributes.ARMOR, MOD_ARMOR, BONUS_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK, BONUS_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
		modifier(pet, Attributes.STEP_HEIGHT, MOD_STEP, BONUS_STEP, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Every combat modifier except the scale (which eases on its own). Clamps health to the lowered maximum. */
	private static void removeCombatModifiers(Mob pet) {
		removeModifier(pet, Attributes.MAX_HEALTH, MOD_HEALTH);
		removeModifier(pet, Attributes.ATTACK_DAMAGE, MOD_DAMAGE);
		removeModifier(pet, Attributes.MOVEMENT_SPEED, MOD_SPEED);
		removeModifier(pet, Attributes.ARMOR, MOD_ARMOR);
		removeModifier(pet, Attributes.KNOCKBACK_RESISTANCE, MOD_KNOCKBACK);
		removeModifier(pet, Attributes.STEP_HEIGHT, MOD_STEP);
		if (pet.getHealth() > pet.getMaxHealth()) {
			pet.setHealth(pet.getMaxHealth());
		}
	}

	private static void removeModifier(Mob pet, Holder<Attribute> attribute, ResourceLocation id) {
		AttributeInstance instance = pet.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	private static void modifier(Mob pet, Holder<Attribute> attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation operation) {
		AttributeInstance instance = pet.getAttribute(attribute);
		if (instance != null) {
			// transient: the form is never saved, so neither are its buffs (a reload starts in the normal form)
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
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
		Brain brain = mob.getAttachedOrCreate(ModAttachments.SYMBIOTE_PET_BRAIN);
		if (!brain.reconciled) {
			// A freshly loaded pet starts in its normal form: strip any modifiers a pre-rework save made permanent.
			brain.reconciled = true;
			if (!isTransformed(pet)) {
				removeCombatModifiers(pet);
				removeModifier(pet, Attributes.SCALE, MOD_SCALE);
			}
		}

		LivingEntity target = mob.getTarget();
		if (target != null && refuses(mob, target)) {
			mob.setTarget(null);
			target = null;
		}
		boolean sitting = sitting(pet);
		if (inCombat(pet, target, sitting)) {
			brain.lastCombatAt = now;
			if (!isTransformed(pet)) {
				setTransformed(level, pet, true, now);
			}
		} else if (isTransformed(pet) && now - brain.lastCombatAt > COMBAT_TIMEOUT) {
			setTransformed(level, pet, false, now);
		}
		boolean on = isTransformed(pet);
		easeScale(pet, now);

		if (mob.getHealth() < mob.getMaxHealth()) {
			if (on && mob.tickCount % 20 == 0) {
				mob.heal(REGEN_TRANSFORMED);
			} else if (!on && mob.tickCount % 40 == 0) {
				mob.heal(REGEN_DORMANT * 2.0f);
			}
		}
		ambience(level, pet, on);
		if (!on) {
			return;
		}

		tickPounce(level, pet, brain);
		LivingEntity owner = pet.getOwner();
		if (now >= brain.shroudReadyAt && owner != null && owner.level() == level
				&& owner.distanceToSqr(pet) < SHROUD_RANGE * SHROUD_RANGE && ownerInDanger(owner)) {
			guardianShroud(level, pet, owner, brain, now, sitting);
			return;
		}
		if (target == null || !target.isAlive() || sitting) {
			return;
		}
		double dist = mob.distanceTo(target);
		if (dist > 24.0) {
			return;
		}
		if (now >= brain.spikeReadyAt && spikeBurst(level, pet, target, brain, now)) {
			return;
		}
		if (now >= brain.biteReadyAt && dist <= BITE_REACH && mob.hasLineOfSight(target)) {
			latchingBite(level, pet, target, brain, now);
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

	/** Where its jaws are: a little ahead of the head, along its facing. */
	private static Vec3 mouth(Mob pet) {
		Vec3 look = Vec3.directionFromRotation(0.0f, pet.getYHeadRot());
		return pet.getEyePosition().add(look.scale(pet.getBbWidth() * 0.6)).add(0.0, -0.1, 0.0);
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

	/**
	 * Latching Bite: a lunge and a savage bite (1.1x attack) -- black tendrils burst from its jaws and wrap the
	 * target, pinning it (Slowness IV, 2.5 s) and sapping it (Weakness, 3 s); the Symbiote feeds on the wound and
	 * heals the pet for 30% of the damage.
	 */
	static void latchingBite(ServerLevel level, TamableAnimal pet, LivingEntity target, Brain brain, long now) {
		brain.biteReadyAt = now + BITE_COOLDOWN;
		pet.getLookControl().setLookAt(target, 60.0f, 60.0f);
		pet.swing(InteractionHand.MAIN_HAND);
		Vec3 lunge = target.position().subtract(pet.position()).multiply(1.0, 0.0, 1.0);
		if (lunge.lengthSqr() > 1.0e-4) {
			pet.setDeltaMovement(pet.getDeltaMovement().add(lunge.normalize().scale(0.35)).add(0.0, 0.15, 0.0));
			pet.hasImpulse = true;
		}
		float dmg = damage(pet) * 1.1f;
		if (target.hurt(pet.damageSources().mobAttack(pet), dmg)) {
			pet.heal(dmg * 0.3f);
			pet.setLastHurtMob(target);
		}
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3), pet);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), pet);
		Vec3 jaws = mouth(pet);
		Vec3 c = chest(target);
		double r = target.getBbWidth() * 0.7 + 0.15;
		SymbioteTendrilEntity.fromPoint(level, jaws, c, target, 40, 3, 0.1f);
		for (int i = 0; i < 4; i++) {
			double a = Math.PI * 0.5 * i + level.random.nextDouble() * 0.5;
			double y = target.getBbHeight() * (0.2 + 0.2 * i);
			Vec3 wrap = target.position().add(Math.cos(a) * r, y, Math.sin(a) * r);
			SymbioteTendrilEntity.fromPoint(level, jaws, wrap, null, 36 + i * 2, 4 + i, 0.06f);
		}
		level.sendParticles(ICHOR, c.x, c.y, c.z, 18, 0.35, 0.35, 0.35, 0.0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y + 0.3, c.z, 3, 0.2, 0.2, 0.2, 0.1);
		level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), pet instanceof Cat ? SoundEvents.CAT_HISS : SoundEvents.WOLF_GROWL,
				SoundSource.NEUTRAL, 1.0f, 0.55f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.NEUTRAL, 0.7f, 1.3f);
		SymbioteSounds.organic(level, c.x, c.y, c.z, 0.9f, 0.9f);
	}

	/**
	 * Guardian Shroud: its owner is under 40% health and has just been hurt -- the pet throws a tendril to them and
	 * the Symbiote wraps them (Absorption II for 10 s, Resistance I for 5 s), hurls every hostile within 4 blocks of
	 * them back, and turns the pet on whoever hurt them. A sitting pet shrouds from where it sits.
	 */
	static void guardianShroud(ServerLevel level, TamableAnimal pet, LivingEntity owner, Brain brain, long now,
			boolean sitting) {
		brain.shroudReadyAt = now + SHROUD_COOLDOWN;
		brain.lastCombatAt = now;
		LivingEntity attacker = owner.getLastHurtByMob();
		if (attacker != null && (!attacker.isAlive() || refuses(pet, attacker))) {
			attacker = null;
		}
		Vec3 oc = chest(owner);
		SymbioteTendrilEntity.fromPoint(level, chest(pet), oc, owner, 30, 5, 0.11f);
		Vec3 feet = owner.position();
		double h = owner.getBbHeight();
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2.0 * i / 6.0;
			Vec3 root = feet.add(Math.cos(a) * 1.0, 0.05, Math.sin(a) * 1.0);
			Vec3 tip = feet.add(Math.cos(a + 1.2) * 0.35, h * (0.75 + 0.05 * (i % 3)), Math.sin(a + 1.2) * 0.35);
			SymbioteTendrilEntity.fromPoint(level, root, tip, null, 50, 6 + i, 0.07f);
		}
		owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1), pet);
		owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0), pet);
		final LivingEntity marked = attacker;
		float dmg = damage(pet) * 0.5f;
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(4.0),
				e -> e != owner && isFoe(pet, e, marked))) {
			foe.hurt(pet.damageSources().mobAttack(pet), dmg);
			foe.knockback(1.1, owner.getX() - foe.getX(), owner.getZ() - foe.getZ());
			foe.hurtMarked = true;
		}
		if (attacker != null && !sitting) {
			pet.setTarget(attacker);
		}
		if (!sitting && pet.distanceToSqr(owner) > 16.0) {
			Vec3 d = owner.position().subtract(pet.position());
			pet.setDeltaMovement(d.normalize().scale(Math.min(1.4, 0.4 + d.length() * 0.08)).add(0.0, 0.35, 0.0));
			pet.hasImpulse = true;
		}
		level.sendParticles(ParticleTypes.SQUID_INK, oc.x, oc.y, oc.z, 30, 0.6, 0.6, 0.6, 0.05);
		level.sendParticles(ICHOR, oc.x, oc.y, oc.z, 40, 0.7, 0.8, 0.7, 0.0);
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2.0 * i / 24.0;
			level.sendParticles(ICHOR, feet.x + Math.cos(a) * 1.3, feet.y + 0.1, feet.z + Math.sin(a) * 1.3, 1, 0.0, 0.05, 0.0, 0.0);
		}
		SymbioteSounds.organic(level, oc.x, oc.y, oc.z, 1.3f, 0.5f);
		level.playSound(null, oc.x, oc.y, oc.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.NEUTRAL, 1.0f, 0.6f);
		level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), pet instanceof Cat ? SoundEvents.CAT_HISS : SoundEvents.WOLF_GROWL,
				SoundSource.NEUTRAL, 1.2f, 0.5f);
		if (owner instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.projecthero.symbiote.pet_shroud", pet.getDisplayName())
					.withStyle(ChatFormatting.DARK_PURPLE), true);
		}
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

	/** In combat form it oozes; in its normal form only the odd black drip gives the Symbiote away. */
	private static void ambience(ServerLevel level, Mob pet, boolean on) {
		if (on && pet.tickCount % 8 == 0) {
			level.sendParticles(ICHOR, pet.getX(), pet.getY() + pet.getBbHeight() * 0.6, pet.getZ(),
					1, pet.getBbWidth() * 0.4, pet.getBbHeight() * 0.3, pet.getBbWidth() * 0.4, 0.0);
		}
		if ((pet.tickCount + pet.getId()) % (on ? 37 : 90) == 0) {
			level.sendParticles(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pet.getX(), pet.getY() + pet.getBbHeight() * 0.5,
					pet.getZ(), 1, pet.getBbWidth() * 0.3, 0.1, pet.getBbWidth() * 0.3, 0.0);
		}
	}

	/** The transform / detransform itself: the skin does the talking (client pixel reveal); this is the snarl. */
	private static void formFx(ServerLevel level, TamableAnimal pet, boolean on) {
		double cx = pet.getX();
		double cy = pet.getY() + pet.getBbHeight() * 0.5;
		double cz = pet.getZ();
		double w = pet.getBbWidth() * 0.6;
		double h = pet.getBbHeight() * 0.5;
		if (on) {
			// no ink cloud: the pixel-by-pixel skin is the visual (as for the player suit-up), this is just a spatter
			level.sendParticles(ICHOR, cx, cy, cz, 12, w, h, w, 0.0);
			level.playSound(null, cx, cy, cz, pet instanceof Cat ? SoundEvents.CAT_HISS : SoundEvents.WOLF_GROWL,
					SoundSource.NEUTRAL, 1.0f, 0.6f);
			SymbioteSounds.organic(level, cx, cy, cz, 1.0f, 0.6f);
		} else {
			level.sendParticles(ICHOR, cx, cy, cz, 10, w, h, w, 0.0);
			SymbioteSounds.organic(level, cx, cy, cz, 0.7f, 1.3f);
		}
	}

	private static void bondFx(ServerLevel level, Mob pet, boolean bonding) {
		double cx = pet.getX();
		double cy = pet.getY() + pet.getBbHeight() * 0.5;
		double cz = pet.getZ();
		double w = pet.getBbWidth() * 0.6;
		double h = pet.getBbHeight() * 0.5;
		level.sendParticles(ParticleTypes.SQUID_INK, cx, cy, cz, bonding ? 30 : 25, w, h, w, 0.06);
		level.sendParticles(ICHOR, cx, cy, cz, 30, w, h, w, 0.0);
		if (bonding) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, cx, cy, cz, 5, w, h, w, 0.02);
			level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_HEARTBEAT, SoundSource.NEUTRAL, 1.0f, 0.9f);
		}
		SymbioteSounds.organic(level, cx, cy, cz, 1.2f, bonding ? 0.5f : 1.3f);
	}
}

package com.projecthero.mod.power;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;

/**
 * The Power of Thor: the passive half of being Mjolnir's bound owner.
 *
 * <h2>Why it does not look at your hands</h2>
 * These powers come from <em>being bound</em>, not from holding the hammer, so nothing here reads
 * {@code getMainHandItem()}. The single source of truth is
 * {@link ModAttachments#BOUND_HAMMER_ID} on the player (plus worthiness), which means the powers
 * hold while Mjolnir is in a chest, in flight, lying in a chunk nobody has loaded, or in another
 * dimension -- and end the moment the hammer is unbound or the player stops being worthy.
 *
 * <h2>Why nothing is reapplied every tick</h2>
 * The three attribute effects are <em>transient</em> attribute modifiers with fixed ids. Transient
 * means they are never written to the player's NBT, so they cannot accumulate across relogs the way
 * permanent modifiers famously do; the fixed ids mean re-adding one is idempotent. The state is
 * reconciled on the events that can actually change it (bind, unbind, join, respawn, worthiness
 * change) plus a cheap once-a-second audit, and each reconcile is a no-op unless something differs.
 *
 * <p>Damage reduction is the one thing with no attribute in 1.21.1, so it uses a single infinite,
 * hidden Resistance instance -- applied once when the powers are granted, not re-poured every tick.
 */
public final class ThorPassives {
	/** v0.6.22: a flat +10 weapon-independent melee bonus -- Thor hits like a god bare-handed. */
	private static final double STRENGTH_BONUS = 10.0;
	/** +18%: quick enough to feel Asgardian, slow enough that terrain still matters. */
	private static final double SPEED_BONUS = 0.18;
	/** Hard to shove, far from immovable. */
	private static final double KNOCKBACK_RESISTANCE = 0.35;
	/** v0.6.22: +10 hearts of extra maximum health while the Power of Thor is held. */
	private static final double MAX_HEALTH_BONUS = 20.0;

	/** How often the reconcile audit runs, in ticks. Cheap, and only a safety net. */
	private static final int AUDIT_INTERVAL_TICKS = 20;

	// ---------------- mild regeneration ----------------
	/** How long after taking damage regeneration stays switched off. */
	private static final int REGEN_COMBAT_LOCKOUT_TICKS = 160; // 8s
	/** One half-heart per this many ticks, out of combat only -- well under vanilla's well-fed rate. */
	private static final int REGEN_INTERVAL_TICKS = 100; // 5s

	private static final ResourceLocation STRENGTH_ID = ProjectHeroMod.id("power_of_thor_strength");
	private static final ResourceLocation SPEED_ID = ProjectHeroMod.id("power_of_thor_speed");
	private static final ResourceLocation KNOCKBACK_ID = ProjectHeroMod.id("power_of_thor_knockback");
	private static final ResourceLocation MAX_HEALTH_ID = ProjectHeroMod.id("power_of_thor_max_health");

	private ThorPassives() {
	}

	// ---------------- state ----------------

	/**
	 * Whether the player currently has the Power of Thor. Bound <em>and</em> worthy: the flavour text
	 * is "whosoever holds this hammer, <em>if he be worthy</em>", and the rest of the mod already
	 * strips an unworthy player of the hammer itself.
	 */
	public static boolean hasPowerOfThor(Player player) {
		return player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null) != null
				&& Worthiness.isWorthy(player);
	}

	public static UUID boundHammerId(Player player) {
		return player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null);
	}

	/** Records the binding and switches the powers on. Returns false if nothing changed. */
	public static boolean bind(ServerPlayer player, UUID hammerId) {
		UUID previous = boundHammerId(player);
		player.setAttached(ModAttachments.BOUND_HAMMER_ID, hammerId);
		reconcile(player);
		return !hammerId.equals(previous);
	}

	/** Releases the binding and switches the powers off. */
	public static void unbind(ServerPlayer player) {
		player.setAttached(ModAttachments.BOUND_HAMMER_ID, null);
		reconcile(player);
	}

	// ---------------- reconciliation ----------------

	/**
	 * Brings the player's modifiers/effects in line with whether they currently have the power. Safe
	 * to call as often as you like: every branch checks before it writes, so a no-change call touches
	 * nothing.
	 */
	public static void reconcile(ServerPlayer player) {
		boolean shouldHave = hasPowerOfThor(player);

		setModifier(player, Attributes.ATTACK_DAMAGE, STRENGTH_ID, STRENGTH_BONUS,
				AttributeModifier.Operation.ADD_VALUE, shouldHave);
		setModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, shouldHave);
		setModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE, shouldHave);
		setModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID, MAX_HEALTH_BONUS,
				AttributeModifier.Operation.ADD_VALUE, shouldHave);

		reconcileResistance(player, shouldHave);
	}

	private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
			double amount, AttributeModifier.Operation operation, boolean wanted) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (wanted) {
			if (current == null || current.amount() != amount || current.operation() != operation) {
				// Transient: never serialised, so it cannot pile up across logins.
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
			}
		} else if (current != null) {
			instance.removeModifier(id);
		}
	}

	/**
	 * Resistance IV (v0.9.3, was III in v0.9.2 / II in v0.6.22) as one infinite, invisible effect
	 * instance rather than a per-tick reapplication. Only touched when the desired state and the actual
	 * state disagree.
	 */
	private static void reconcileResistance(ServerPlayer player, boolean wanted) {
		MobEffectInstance active = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
		boolean ours = active != null && active.isInfiniteDuration() && active.getAmplifier() == 3
				&& active.isAmbient();

		if (wanted && !ours) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
					MobEffectInstance.INFINITE_DURATION, 3, true, false, false));
		} else if (!wanted && ours) {
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
		}
	}

	// ---------------- per-tick work (deliberately almost none) ----------------

	public static void serverTick(ServerPlayer player) {
		if (player.tickCount % AUDIT_INTERVAL_TICKS == 0) {
			// Catches anything that changed the player's state without going through bind/unbind --
			// most obviously a worthiness change from a command or from gameplay scoring.
			reconcile(player);
		}
		tickRegeneration(player);
	}

	/**
	 * Mild out-of-combat recovery: a single half-heart every five seconds, and only after eight
	 * quiet seconds. Deliberately far weaker than a Regeneration effect -- it takes the edge off
	 * chip damage without making fights meaningless, and it is switched off entirely the moment
	 * anything hits you.
	 */
	private static void tickRegeneration(ServerPlayer player) {
		if (!hasPowerOfThor(player) || player.tickCount % REGEN_INTERVAL_TICKS != 0) {
			return;
		}
		if (player.getHealth() >= player.getMaxHealth() || player.isDeadOrDying()) {
			return;
		}
		long lastHurt = player.getAttachedOrElse(ModAttachments.LAST_HURT_TICK, 0L);
		if (player.level().getGameTime() - lastHurt < REGEN_COMBAT_LOCKOUT_TICKS) {
			return;
		}
		player.heal(1.0f);
	}

	// ---------------- damage rules ----------------

	/**
	 * Fall and lightning immunity. Registered against {@code ServerLivingEntityEvents.ALLOW_DAMAGE}
	 * in {@link com.projecthero.mod.ProjectHeroMod}.
	 *
	 * <p>Both checks are against specific damage types rather than broad tags, so Thor keeps taking
	 * ordinary magic, potion and enchantment damage as normal -- only the two things a storm god has
	 * no business being hurt by are cancelled. The mod's own lightning abilities use vanilla's
	 * {@code lightningBolt()} source, so they are covered by the same check.
	 *
	 * @return false to cancel the damage.
	 */
	public static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !hasPowerOfThor(player)) {
			return true;
		}

		if (source.is(DamageTypes.LIGHTNING_BOLT)) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			// Works whether or not Mjolnir is in hand, and whether or not the player was flying.
			player.resetFallDistance();
			return false;
		}

		// Anything that gets through resets the regeneration lockout.
		player.setAttached(ModAttachments.LAST_HURT_TICK, player.level().getGameTime());
		return true;
	}

	// ---------------- lifecycle ----------------

	public static void onPlayerJoin(ServerPlayer player) {
		reconcile(player);
	}

	/**
	 * A respawned player is a brand-new entity with no transient modifiers and no effects, so the
	 * powers have to be re-established. The binding itself survives because
	 * {@link ModAttachments#BOUND_HAMMER_ID} is {@code copyOnDeath()} -- dying does not cost you
	 * Mjolnir, which is the whole point of being able to call it back afterwards.
	 */
	public static void onPlayerRespawn(ServerPlayer player) {
		reconcile(player);
	}
}

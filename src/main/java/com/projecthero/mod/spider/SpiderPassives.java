package com.projecthero.mod.spider;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Spider-Man's always-on physical enhancements, and his fall-damage rules.
 *
 * <p>Deliberately restrained against the rest of the roster: he hits harder and moves better than an
 * ordinary player, but nowhere near a power whose entire identity is raw strength -- Super Strength's
 * passive is +6 attack damage, and this is half of that. What Spider-Man is best at is <em>moving</em>,
 * and that is where the numbers go.
 *
 * <p>Applied as fixed-id transient attribute modifiers, the same discipline the rest of the mod uses,
 * so nothing can accumulate across a relog and {@link #reconcile} can be called as often as it likes.
 */
public final class SpiderPassives {
	private static final ResourceLocation ATTACK = ProjectHeroMod.id("spider_man_attack");
	private static final ResourceLocation SPEED = ProjectHeroMod.id("spider_man_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("spider_man_jump");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("spider_man_knockback");
	private static final ResourceLocation SAFE_FALL = ProjectHeroMod.id("spider_man_safe_fall");
	private static final ResourceLocation FALL_MULT = ProjectHeroMod.id("spider_man_fall_multiplier");
	private static final ResourceLocation STEP = ProjectHeroMod.id("spider_man_step");

	private SpiderPassives() {
	}

	/** Guards {@link #onSpiderOutgoingDamage}'s re-applied hit from recursing into itself. */
	private static final ThreadLocal<Boolean> REENTRANT_ATTACK = ThreadLocal.withInitial(() -> false);

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderPassives::onAllowDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderPassives::onSpiderOutgoingDamage);
	}

	/**
	 * v0.6.22 offensive rules for a Spider-Man player:
	 * <ul>
	 *   <li><b>+40% damage</b> to any target that is webbed, cocooned, or standing in a cobweb
	 *       ({@link SpiderWebs#isWebImpaired}).</li>
	 *   <li><b>Sprint punch (v0.9.4).</b> An unarmed hit is +9 (lands as 10); an unarmed sprinting hit
	 *       gets a flat +3 more, so it lands as 13 -- a "+12" punch.</li>
	 * </ul>
	 *
	 * <p>Fabric's {@code ALLOW_DAMAGE} is a yes/no veto with no "raise the amount", so a boosted hit
	 * is done by cancelling the original and re-applying a larger one behind a re-entrancy guard --
	 * the same trick {@code HeroDamageRules} uses for partial reductions.
	 */
	private static boolean onSpiderOutgoingDamage(LivingEntity victim, DamageSource source, float amount) {
		if (REENTRANT_ATTACK.get()) {
			return true;
		}
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim
				|| !SpiderMan.hasPower(attacker)) {
			return true;
		}
		float multiplier = SpiderWebs.isWebImpaired(victim) ? 1.4f : 1.0f;
		float addend = (source.is(DamageTypes.PLAYER_ATTACK) && attacker.isSprinting()
				&& attacker.getMainHandItem().isEmpty()) ? 3.0f : 0.0f;
		float boosted = amount * multiplier + addend;
		if (boosted <= amount + 0.001f) {
			return true;
		}
		REENTRANT_ATTACK.set(true);
		try {
			victim.hurt(source, boosted);
		} finally {
			REENTRANT_ATTACK.set(false);
		}
		return false;
	}

	/** Put the modifiers in the state the player's current power says they should be in. */
	public static void reconcile(ServerPlayer player) {
		if (SpiderMan.hasPower(player)) {
			// v0.9.4: unarmed hit is a flat 10 damage (vanilla base 1.0 + 9.0). Sprint punch adds +3 more
			// -> 13, a "+12" punch (see onSpiderOutgoingDamage).
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK, 9.0,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, 0.18,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			// v0.6.17: a plain jump now clears ~2 blocks (was ~1.75). Sneak + jump triggers the much
			// bigger ~10-block leap in SpiderInputClient / SpiderAbilities.superJump (v0.9.10).
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP, 0.30,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 0.30,
					AttributeModifier.Operation.ADD_VALUE);
			// He survives falls that would badly hurt anyone else, without being immune to them.
			PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, 9.0,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT, -0.5,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP, 0.4,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL);
			PowerToggles.clearModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP);
			player.removeEffect(MobEffects.MOVEMENT_SPEED);
			player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			player.removeEffect(MobEffects.REGENERATION);
		}
	}

	/**
	 * Cheap per-tick upkeep: keep the modifiers honest on a fresh entity, and keep the passive
	 * Speed II / Resistance II / Regeneration I effects topped up. All are hidden, ambient and
	 * effectively infinite -- refreshed well before they can lapse, no HUD icon, no particles -- the
	 * same pattern the rest of the mod uses for a "permanent effect" that a status-effect attribute
	 * cannot express. (v0.9.6: Regeneration dropped from II to I.)
	 */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % 40 == 0) {
			reconcile(player);
		}
		if (!SpiderMan.hasPower(player)) {
			return;
		}
		if (player.tickCount % 40 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, true, false, false));
		}
		// Symbiote Recovery (spec): Black Suit Spider-Man refreshes the same Regeneration I a little
		// more often than the base 40-tick cadence -- a modestly faster recovery, deliberately still
		// capped at amplifier 0 so it never becomes the constant high-level regen the spec warns against.
		if (player.tickCount % 30 == 0 && com.projecthero.mod.symbiote.Symbiote.isActive(player)
				&& com.projecthero.mod.symbiote.SymbioteHostType.of(player)
						== com.projecthero.mod.symbiote.SymbioteHostType.SPIDER_MAN) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 90, 0, true, false, false));
		}
	}

	/**
	 * Fall protection. v0.6.19: Spider-Man takes <em>no</em> fall damage at all -- his agility and his
	 * spider-sense reflexes always turn a fall into a controlled landing. (Previously only landings
	 * shortly after a web use, or off a wall/swing, were free.)
	 */
	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !SpiderMan.hasPower(player)) {
			return true;
		}
		if (!source.is(DamageTypeTags.IS_FALL)) {
			return true;
		}
		player.resetFallDistance();
		return false;
	}
}

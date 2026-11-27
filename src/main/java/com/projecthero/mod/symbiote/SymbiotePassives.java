package com.projecthero.mod.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * The Normal Symbiote Host's always-on physical enhancements -- clearly superhuman, deliberately
 * short of a dedicated super-strength power. Applied as fixed-id transient attribute modifiers (the
 * same discipline {@code SpiderPassives}/{@code PowerToggles} use elsewhere).
 *
 * <p>v0.10.1: the Symbiote is with the host whether or not the suit is worn, so a bonded-but-not-
 * suited host now gets a <b>reduced</b> always-on kit ({@link #bonded}) -- notably Regeneration, plus
 * a little speed / knockback resistance / fall protection -- and suiting up ({@link Symbiote#isActive})
 * upgrades that to the full kit ({@link #eligible}): higher values, unarmed strength, and a jump
 * boost. Previously the host got nothing at all until they pressed H.
 *
 * <p>Black Suit Spider-Man does <b>not</b> go through this class -- his stat changes are multipliers
 * on the existing Spider-Man passives, applied by {@code SpiderPassives}/{@code SpiderManAbilityManager}
 * themselves when {@link SymbioteHostType#SPIDER_MAN} is active.
 */
public final class SymbiotePassives {
	private static final ResourceLocation ATTACK = com.projecthero.mod.combat.MeleeBonus.register(ProjectHeroMod.id("symbiote_host_attack"));
	/** v0.17.7 City tier: the suit's bare fist lands 11 (vanilla 1 + this; was +5 = 6). */
	public static final double SUIT_MELEE_BONUS = 10.0;
	/**
	 * v0.17.7 City tier: the Symbiote is a Secondary stacked on a Primary, so its melee bonuses never push a punch past this
	 * ({@link #capMelee}); a punch already over it without them (the Primary's own) is never lowered.
	 */
	public static final float MAX_HIT = 30f; // v0.17.7: was PowerRank.CITY.ultimateMax (tiers removed; the user rebalances powers one by one)
	private static final ResourceLocation SPEED = ProjectHeroMod.id("symbiote_host_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("symbiote_host_jump");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("symbiote_host_knockback");
	private static final ResourceLocation FALL_MULT = ProjectHeroMod.id("symbiote_host_fall_multiplier");

	/** Regeneration is re-applied (and paid for) once a second. */
	private static final int REGEN_PULSE_TICKS = 20;
	/** Biomass one second of Symbiote healing costs -- small next to the 8/s (v0.13.21) it regenerates out of combat. */
	private static final float REGEN_BIOMASS_PER_PULSE = 2.0f;

	private SymbiotePassives() {
	}

	/** Bonded with a plain (non-Spider-Man) Symbiote -- true whether or not the suit is worn. */
	private static boolean bonded(ServerPlayer player) {
		// v0.17.5: stacked on a Primary, the bonded-only kit (Regeneration, a little speed) waits for the suit
		return SymbioteHostType.of(player) == SymbioteHostType.NORMAL && Symbiote.hasSymbiote(player) && !Symbiote.dormant(player);
	}

	/** Bonded <em>and</em> the living suit is currently worn -- the full enhancement set. */
	private static boolean eligible(ServerPlayer player) {
		return bonded(player) && Symbiote.isActive(player);
	}

	/** Put the modifiers in the state the player's current bond/suit status says they should be in. */
	public static void reconcile(ServerPlayer player) {
		if (!bonded(player)) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK);
			PowerToggles.clearModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT);
			return;
		}

		boolean suited = Symbiote.isActive(player);

		// Movement / knockback: a modest passive baseline just from being bonded, roughly doubled once
		// the suit is on.
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, suited ? 0.15 : 0.07,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// Also covers the spec's "Environmental Resistance: reduce knockback".
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, suited ? 0.20 : 0.10,
				AttributeModifier.Operation.ADD_VALUE);
		// v0.10.2: fall damage is no longer softened by an attribute here -- the Symbiote negates it
		// outright in SymbioteDamageRules (free since v0.13.21; it used to cost a little Biomass).
		// Clear any modifier a prior version left.
		PowerToggles.clearModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT);

		if (suited) {
			// Unarmed lands 11 (vanilla base 1.0 + 10.0; v0.13.19: +4 -> +5; v0.17.7 City tier: +5 -> +10) and jumps are
			// noticeably higher -- the "powered up" feel of actually wearing the suit. Kept off the bonded-only baseline on purpose.
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK, SUIT_MELEE_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP, 0.20,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
		}
	}

	/**
	 * Cheap per-tick upkeep: keep the modifiers honest on a fresh entity, and keep the Symbiote's
	 * accelerated healing topped up -- Regeneration I just from being bonded (v0.10.1), rising to
	 * Regeneration II while the suit is worn and {@link FoodData#getFoodLevel} is at least half full.
	 */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % 40 == 0) {
			reconcile(player);
		}
		if (!bonded(player)) {
			return;
		}
		// v0.11.15 (v0.15.18: Regeneration III): only while the host is actually hurt, and it is paid for -- each second
		// of healing costs the Symbiote a little Biomass. A spent (broken) or still-bonding Symbiote cannot heal.
		if (player.tickCount % REGEN_PULSE_TICKS == 0 && player.getHealth() < player.getMaxHealth()
				&& SymbioteVitalsManager.usable(player) && SymbioteVitalsManager.biomass(player) > 0.0f) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_PULSE_TICKS * 3, 2,
					true, false, false));
			SymbioteVitalsManager.spendBiomass(player, REGEN_BIOMASS_PER_PULSE);
		}
	}

	/**
	 * v0.17.7 City tier (from {@code PlayerSymbioteMeleeCapMixin}, on the damage of a real melee swing): the Symbiote's attack-damage
	 * bonuses (the suit, a weapon, the Monster's claws) never lift a punch past {@link #MAX_HIT}. {@code amount} comes back untouched
	 * when it is already at or under the cap, or when the punch without the Symbiote's bonuses (the Primary's own) is over it anyway.
	 */
	public static float capMelee(Player attacker, float amount) {
		if (amount <= MAX_HIT || attacker.level().isClientSide()) {
			return amount;
		}
		AttributeInstance inst = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) {
			return amount;
		}
		double bonus = 0.0;
		for (ResourceLocation id : new ResourceLocation[] { ATTACK, SymbioteVitalsManager.BLADE_ATTACK,
				com.projecthero.mod.symbiote.monster.SymbioteMonster.ATTACK_ID }) {
			AttributeModifier m = inst.getModifier(id);
			if (m != null && m.operation() == AttributeModifier.Operation.ADD_VALUE && m.amount() > 0.0) {
				bonus += m.amount();
			}
		}
		if (bonus <= 0.0) {
			return amount;
		}
		return Math.max(MAX_HIT, amount - (float) bonus);
	}
}

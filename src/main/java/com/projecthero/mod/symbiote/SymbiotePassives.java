package com.projecthero.mod.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
	private static final ResourceLocation ATTACK = ProjectHeroMod.id("symbiote_host_attack");
	private static final ResourceLocation SPEED = ProjectHeroMod.id("symbiote_host_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("symbiote_host_jump");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("symbiote_host_knockback");
	private static final ResourceLocation FALL_MULT = ProjectHeroMod.id("symbiote_host_fall_multiplier");

	private SymbiotePassives() {
	}

	/** Bonded with a plain (non-Spider-Man) Symbiote -- true whether or not the suit is worn. */
	private static boolean bonded(ServerPlayer player) {
		return SymbioteHostType.of(player) == SymbioteHostType.NORMAL && Symbiote.hasSymbiote(player);
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

		// Movement / knockback / fall: a modest passive baseline just from being bonded, roughly
		// doubled once the suit is on.
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, suited ? 0.15 : 0.07,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// Also covers the spec's "Environmental Resistance: reduce knockback".
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, suited ? 0.20 : 0.10,
				AttributeModifier.Operation.ADD_VALUE);
		// Also covers "Environmental Resistance: reduce fall damage".
		PowerToggles.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT, suited ? -0.80 : -0.40,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		if (suited) {
			// Unarmed lands 5 (vanilla base 1.0 + 4.0) and jumps are noticeably higher -- the "powered
			// up" feel of actually wearing the suit. Kept off the bonded-only baseline on purpose.
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK, 4.0,
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
		if (player.tickCount % 40 == 0) {
			boolean wellFedAndSuited = Symbiote.isActive(player) && player.getFoodData().getFoodLevel() >= 10;
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, wellFedAndSuited ? 1 : 0,
					true, false, false));
		}
	}
}

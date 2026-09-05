package com.herocraft.mod.symbiote;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.hero.power.PowerToggles;

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
 * same discipline {@code SpiderPassives}/{@code PowerToggles} use elsewhere), reconciled only while
 * the suit is actually worn ({@link Symbiote#isActive}) -- a bonded-but-not-suited-up host gets
 * nothing until they press H, matching the spec's "while ACTIVE" wording.
 *
 * <p>Black Suit Spider-Man does <b>not</b> go through this class -- his stat changes are multipliers
 * on the existing Spider-Man passives, applied by {@code SpiderPassives}/{@code SpiderManAbilityManager}
 * themselves when {@link SymbioteHostType#SPIDER_MAN} is active.
 */
public final class SymbiotePassives {
	private static final ResourceLocation ATTACK = HeroCraftMod.id("symbiote_host_attack");
	private static final ResourceLocation SPEED = HeroCraftMod.id("symbiote_host_speed");
	private static final ResourceLocation JUMP = HeroCraftMod.id("symbiote_host_jump");
	private static final ResourceLocation KNOCKBACK = HeroCraftMod.id("symbiote_host_knockback");
	private static final ResourceLocation FALL_MULT = HeroCraftMod.id("symbiote_host_fall_multiplier");

	private SymbiotePassives() {
	}

	private static boolean eligible(ServerPlayer player) {
		return SymbioteHostType.of(player) == SymbioteHostType.NORMAL && Symbiote.isActive(player);
	}

	/** Put the modifiers in the state the player's current bond/suit status says they should be in. */
	public static void reconcile(ServerPlayer player) {
		if (eligible(player)) {
			// Unarmed lands 5 (vanilla base 1.0 + 4.0), clearly stronger than a normal player but well
			// under a dedicated super-strength power (+6 flat on top of the same 1.0 base).
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK, 4.0,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, 0.15,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP, 0.20,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			// Also covers the spec's separately-listed "Environmental Resistance: reduce knockback".
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 0.20,
					AttributeModifier.Operation.ADD_VALUE);
			// Also covers "Environmental Resistance: reduce fall damage" -- one modifier for both.
			PowerToggles.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT, -0.30,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK);
			PowerToggles.clearModifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MULT);
		}
	}

	/**
	 * Cheap per-tick upkeep: keep the modifiers honest on a fresh entity, and keep a small "slightly
	 * improved regeneration when well fed" effect topped up -- deliberately capped at Regeneration I
	 * and only while {@link FoodData#getFoodLevel} is at least half full, per the spec's explicit
	 * "do NOT give permanent Regeneration II or anything similarly overpowered".
	 */
	public static void tick(ServerPlayer player) {
		if (player.tickCount % 40 == 0) {
			reconcile(player);
		}
		if (!eligible(player)) {
			return;
		}
		if (player.tickCount % 40 == 0 && player.getFoodData().getFoodLevel() >= 10) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, false));
		}
	}
}

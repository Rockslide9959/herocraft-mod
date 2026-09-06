package com.projecthero.mod.maxsteel;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.maxsteel.data.MaxSteelState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Every attribute modifier Max Steel applies, as fixed-id transient modifiers through the mod's
 * shared {@link PowerToggles} helper -- the same discipline Thor, Iron Man and Spider-Man use, so
 * nothing accumulates across a relog and {@link #reconcile} can be called as often as it likes.
 *
 * <p>Two layers: the always-on <b>Base Mode</b> passives that apply whenever the suit is on, and the
 * <b>Strength Mode</b> stack that adds on top while that mode is active. Speed Mode's movement is
 * handled as a client-simulated override, not an attribute, so it is not here.
 */
public final class MaxSteelAttributes {
	// base-mode passives
	private static final ResourceLocation BASE_MELEE = ProjectHeroMod.id("max_steel_base_melee");
	private static final ResourceLocation BASE_KB_RESIST = ProjectHeroMod.id("max_steel_base_kb_resist");
	private static final ResourceLocation BASE_JUMP = ProjectHeroMod.id("max_steel_base_jump");
	private static final ResourceLocation BASE_SAFE_FALL = ProjectHeroMod.id("max_steel_base_safe_fall");
	private static final ResourceLocation BASE_FALL_MULT = ProjectHeroMod.id("max_steel_base_fall_mult");
	// strength-mode stack
	private static final ResourceLocation STR_MELEE = ProjectHeroMod.id("max_steel_strength_melee");
	private static final ResourceLocation STR_KB_RESIST = ProjectHeroMod.id("max_steel_strength_kb_resist");
	private static final ResourceLocation STR_SPRINT = ProjectHeroMod.id("max_steel_strength_sprint");
	/** v0.6.21: Strength Mode also slows the player's attack speed by 20%. */
	private static final ResourceLocation STR_ATTACK_SPEED = ProjectHeroMod.id("max_steel_strength_attack_speed");
	/** v0.6.17: Strength Mode makes Max visibly bigger (+20%). */
	private static final ResourceLocation STR_SCALE = ProjectHeroMod.id("max_steel_strength_scale");
	// speed-mode step assist
	private static final ResourceLocation SPEED_STEP = ProjectHeroMod.id("max_steel_speed_step");

	private MaxSteelAttributes() {
	}

	/** Put every Max Steel attribute modifier in the state this player's suit / mode says it should be in. */
	public static void reconcile(ServerPlayer player) {
		MaxSteelState s = player.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.MAX_STEEL_STATE, null);
		boolean suited = s != null && s.hasPower && s.transformed;
		MaxSteelMode mode = suited ? s.modeEnum() : MaxSteelMode.BASE;

		if (suited) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BASE_MELEE,
					MaxSteelConfig.BASE_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, BASE_KB_RESIST,
					MaxSteelConfig.BASE_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, BASE_JUMP,
					MaxSteelConfig.BASE_JUMP_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, BASE_SAFE_FALL,
					6.0, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, BASE_FALL_MULT,
					-MaxSteelConfig.BASE_FALL_DAMAGE_REDUCTION, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			clear(player, BASE_MELEE, Attributes.ATTACK_DAMAGE);
			clear(player, BASE_KB_RESIST, Attributes.KNOCKBACK_RESISTANCE);
			clear(player, BASE_JUMP, Attributes.JUMP_STRENGTH);
			clear(player, BASE_SAFE_FALL, Attributes.SAFE_FALL_DISTANCE);
			clear(player, BASE_FALL_MULT, Attributes.FALL_DAMAGE_MULTIPLIER);
		}

		if (mode == MaxSteelMode.STRENGTH) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, STR_MELEE,
					MaxSteelConfig.STRENGTH_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, STR_KB_RESIST,
					MaxSteelConfig.STRENGTH_KNOCKBACK_RESIST_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, STR_SPRINT,
					-MaxSteelConfig.STRENGTH_SPRINT_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.ATTACK_SPEED, STR_ATTACK_SPEED,
					-MaxSteelConfig.STRENGTH_ATTACK_SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.SCALE, STR_SCALE,
					0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			clear(player, STR_MELEE, Attributes.ATTACK_DAMAGE);
			clear(player, STR_KB_RESIST, Attributes.KNOCKBACK_RESISTANCE);
			clear(player, STR_SPRINT, Attributes.MOVEMENT_SPEED);
			clear(player, STR_ATTACK_SPEED, Attributes.ATTACK_SPEED);
			clear(player, STR_SCALE, Attributes.SCALE);
		}

		if (mode == MaxSteelMode.SPEED) {
			PowerToggles.modifier(player, Attributes.STEP_HEIGHT, SPEED_STEP,
					0.6, AttributeModifier.Operation.ADD_VALUE);
		} else {
			clear(player, SPEED_STEP, Attributes.STEP_HEIGHT);
		}
	}

	/** Strip every Max Steel modifier -- suit-down, energy depletion, death, respawn, logout, revoke. */
	public static void clearAll(ServerPlayer player) {
		clear(player, BASE_MELEE, Attributes.ATTACK_DAMAGE);
		clear(player, BASE_KB_RESIST, Attributes.KNOCKBACK_RESISTANCE);
		clear(player, BASE_JUMP, Attributes.JUMP_STRENGTH);
		clear(player, BASE_SAFE_FALL, Attributes.SAFE_FALL_DISTANCE);
		clear(player, BASE_FALL_MULT, Attributes.FALL_DAMAGE_MULTIPLIER);
		clear(player, STR_MELEE, Attributes.ATTACK_DAMAGE);
		clear(player, STR_KB_RESIST, Attributes.KNOCKBACK_RESISTANCE);
		clear(player, STR_SPRINT, Attributes.MOVEMENT_SPEED);
		clear(player, STR_ATTACK_SPEED, Attributes.ATTACK_SPEED);
		clear(player, STR_SCALE, Attributes.SCALE);
		clear(player, SPEED_STEP, Attributes.STEP_HEIGHT);
	}

	private static void clear(ServerPlayer player, ResourceLocation id,
			net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr) {
		PowerToggles.clearModifier(player, attr, id);
	}
}

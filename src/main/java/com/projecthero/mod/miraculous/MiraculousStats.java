package com.projecthero.mod.miraculous;

import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** v0.16.6: the shared transformed stats (every Miraculous suit): fixed-id attribute modifiers, on while transformed. */
public final class MiraculousStats {
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("miraculous_armor");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("miraculous_toughness");
	private static final ResourceLocation HEALTH_ID = PowerToggles.id("miraculous_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("miraculous_attack");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("miraculous_speed");
	private static final ResourceLocation JUMP_ID = PowerToggles.id("miraculous_jump");

	private MiraculousStats() {
	}

	public static void reconcile(ServerPlayer player) {
		if (Miraculous.transformed(player)) {
			PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, MiraculousConfig.ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, MiraculousConfig.TOUGHNESS_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, MiraculousConfig.MAX_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, MiraculousConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, MiraculousConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, MiraculousConfig.JUMP_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}
}

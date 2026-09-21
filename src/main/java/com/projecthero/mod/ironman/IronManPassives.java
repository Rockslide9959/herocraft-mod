package com.projecthero.mod.ironman;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The "wearing the suit makes you strong" passives (spec "changes 8": <i>armours need to increase the
 * player's strength by a lot, make it make sense for the armours</i>).
 *
 * <p>Powered servos: a full suit adds its {@code strengthBonus} to {@link Attributes#ATTACK_DAMAGE}
 * (Mark III +9 … Mark 50 +20), and partial armour adds it pro-rata (one piece = a quarter). Higher
 * marks also add attack knockback, knockback resistance and a bit of reach / mining speed. All are
 * <b>transient</b> fixed-id modifiers reconciled every tick (a no-op unless something changed), like
 * {@code ThorPassives} -- so they never serialise, never stack across relogs, and vanish the instant
 * the suit comes off or the Tony Stark power is removed.
 */
public final class IronManPassives {
	private static final ResourceLocation ATK = ProjectHeroMod.id("iron_man_strength");
	private static final ResourceLocation ATK_KB = ProjectHeroMod.id("iron_man_attack_knockback");
	private static final ResourceLocation KB_RES = ProjectHeroMod.id("iron_man_knockback_resist");
	private static final ResourceLocation REACH = ProjectHeroMod.id("iron_man_block_reach");
	private static final ResourceLocation MINE = ProjectHeroMod.id("iron_man_mining");
	/** Mark 1's "25% bigger than the normal player model" (spec "changes 12"). */
	private static final ResourceLocation SCALE = ProjectHeroMod.id("iron_man_suit_scale");
	/** v0.11.13: how long a per-tick-refreshed suit Resistance effect is granted for (see below). */
	private static final int RESISTANCE_REFRESH_TICKS = 40;

	private IronManPassives() {
	}

	public static void tick(ServerPlayer player) {
		boolean powered = TonyStark.hasPower(player);
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);

		double frac = 0.0;
		if (powered && suit != null) {
			int pieces = 0;
			for (EquipmentSlot slot : new EquipmentSlot[] {
					EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
				if (IronManArmor.isPieceWorn(player, slot, suitId)) {
					pieces++;
				}
			}
			frac = pieces / 4.0;
		}

		double strength = suit == null ? 0 : suit.strengthBonus() * frac;
		double markScale = suit == null ? 0 : suit.techLevel();

		set(player, Attributes.ATTACK_DAMAGE, ATK, strength, AttributeModifier.Operation.ADD_VALUE, strength > 0);
		set(player, Attributes.ATTACK_KNOCKBACK, ATK_KB, 0.3 * markScale * frac,
				AttributeModifier.Operation.ADD_VALUE, frac >= 0.75 && markScale >= 3);
		set(player, Attributes.KNOCKBACK_RESISTANCE, KB_RES, 0.10 + 0.06 * markScale,
				AttributeModifier.Operation.ADD_VALUE, frac >= 1.0);
		set(player, Attributes.BLOCK_INTERACTION_RANGE, REACH, 1.0,
				AttributeModifier.Operation.ADD_VALUE, frac >= 1.0);
		set(player, Attributes.BLOCK_BREAK_SPEED, MINE, 0.5 + 0.4 * markScale,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, frac >= 1.0);

		double suitScale = suit == null ? 1.0 : suit.scale();
		set(player, Attributes.SCALE, SCALE, suitScale - 1.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE, frac >= 1.0 && suitScale != 1.0);

		// v0.11.13: a full, powered suit with a resistanceAmplifier (Mark 2) grants a standing Resistance
		// effect -- refreshed every tick like the helmet's Night Vision, so it disappears on its own the
		// instant the suit comes off/depowers instead of lingering.
		int resistance = suit == null ? -1 : suit.resistanceAmplifier();
		if (powered && frac >= 1.0 && resistance >= 0) {
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, RESISTANCE_REFRESH_TICKS,
					resistance, true, false, false));
		} else {
			MobEffectInstance eff = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
			if (eff != null && eff.isAmbient() && !eff.isVisible() && !eff.showIcon()
					&& eff.getDuration() <= RESISTANCE_REFRESH_TICKS) {
				player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			}
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount,
			AttributeModifier.Operation op, boolean wanted) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier current = instance.getModifier(id);
		if (wanted) {
			if (current == null || current.amount() != amount || current.operation() != op) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
			}
		} else if (current != null) {
			instance.removeModifier(id);
		}
	}
}

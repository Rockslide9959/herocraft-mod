package com.herocraft.mod.punisher;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.punisher.item.PunisherArmorItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The Punisher tactical armour set bonus (spec section 30). Only in effect while the wearer holds
 * the Punisher power AND wears all three pieces (chest / legs / boots):
 *
 * <ul>
 *   <li>20% projectile-damage reduction ({@link com.herocraft.mod.punisher.PunisherDamage});</li>
 *   <li>10% knockback resistance (the transient attribute modifier below);</li>
 *   <li>a small extra reduction to firearm recoil ({@link PunisherPassives.Hooks#recoilFactor}).</li>
 * </ul>
 *
 * <p>Deliberately never stronger than powered armour such as Iron Man -- the Punisher is human.
 * The knockback modifier is transient + fixed-id + reconciled on change, so it cannot leak.
 */
public final class PunisherArmorSet {
	public static final float PROJECTILE_REDUCTION = 0.20f;
	public static final float SET_RECOIL_FACTOR = 0.9f;
	private static final double KNOCKBACK_RESIST = 0.10;
	private static final ResourceLocation KB_ID = HeroCraftMod.id("punisher_set_knockback");

	private PunisherArmorSet() {
	}

	public static boolean fullSet(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof PunisherArmorItem
				&& player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof PunisherArmorItem
				&& player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof PunisherArmorItem;
	}

	/** True when the set bonus should be active for this player. */
	public static boolean active(Player player) {
		return Punisher.hasPower(player) && fullSet(player);
	}

	/** Reconcile the knockback-resistance modifier. Called from {@link PunisherPassives#reconcile}. */
	public static void reconcile(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (inst == null) {
			return;
		}
		boolean wanted = active(player);
		AttributeModifier current = inst.getModifier(KB_ID);
		if (wanted) {
			if (current == null || current.amount() != KNOCKBACK_RESIST) {
				inst.addOrUpdateTransientModifier(
						new AttributeModifier(KB_ID, KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE));
			}
		} else if (current != null) {
			inst.removeModifier(KB_ID);
		}
	}
}

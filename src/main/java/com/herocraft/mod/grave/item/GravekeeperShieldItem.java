package com.herocraft.mod.grave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;

/**
 * A shield that specialises in the Zombie Raid rather than beating every other shield.
 *
 * <p>It blocks exactly like a vanilla shield -- same use animation, same timing, same arc, because it
 * <em>is</em> a {@link ShieldItem}. What it adds applies only against the raid's own threats and lives
 * in the damage listener ({@code GraveboundEvents}): a flat reduction against undead attackers,
 * a larger one against Acid Zombie globs and pools, and near-total resistance to a Juggernaut's charge
 * knockback.
 *
 * <p>Deliberately no general damage bonus, no extra durability over a vanilla shield in ordinary
 * combat, and nothing at all against players, creepers, the Warden or anything else -- carrying it
 * into a non-undead fight should feel like carrying a normal shield, which is what "should not be
 * universally better than every shield" means in practice.
 */
public class GravekeeperShieldItem extends ShieldItem {
	/** Fraction of incoming undead damage removed while this shield is merely held. */
	public static final float UNDEAD_REDUCTION = 0.25f;
	/** Fraction of Acid Zombie damage removed. */
	public static final float ACID_REDUCTION = 0.55f;
	/** Fraction of a Juggernaut charge's knockback removed. */
	public static final float CHARGE_KNOCKBACK_REDUCTION = 0.85f;

	public GravekeeperShieldItem(Properties properties) {
		super(properties);
	}

	@Override
	public String getDescriptionId(ItemStack stack) {
		// ShieldItem appends banner colour to the description id; this shield has no banner variants.
		return this.getDescriptionId();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.herocraft.gravekeeper_shield.undead",
				Math.round(UNDEAD_REDUCTION * 100)).withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("item.herocraft.gravekeeper_shield.acid",
				Math.round(ACID_REDUCTION * 100)).withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.translatable("item.herocraft.gravekeeper_shield.charge")
				.withStyle(ChatFormatting.GREEN));
	}
}

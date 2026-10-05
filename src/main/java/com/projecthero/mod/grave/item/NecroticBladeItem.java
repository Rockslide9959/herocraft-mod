package com.projecthero.mod.grave.item;

import java.util.List;

import com.projecthero.mod.event.EventConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

/**
 * The raid's melee weapon: base damage a little above a netherite sword, a chance to inflict Wither,
 * and a temporary damage bonus that builds as it kills undead.
 *
 * <h2>The bonus cannot run away</h2>
 * Two independent bounds, both on the stack itself:
 * <ul>
 *   <li>{@link GraveComponents#BLADE_STACKS} is clamped to
 *       {@link EventConfig.ZombieRaid#necroticMaxStacks}, so the bonus has a hard ceiling;</li>
 *   <li>{@link GraveComponents#BLADE_EXPIRES_AT} is an absolute game time; once it passes, the whole
 *       stack count is read as zero regardless of what the number says.</li>
 * </ul>
 * There is no path that increments without clamping and no decay timer that can be missed, so infinite
 * stacking is impossible rather than merely unlikely. Because the expiry is absolute game time, it
 * also cannot be extended by logging out.
 */
public class NecroticBladeItem extends SwordItem {
	public NecroticBladeItem(Tier tier, Properties properties) {
		super(tier, properties);
	}

	public static int maxStacks() {
		return Math.max(1, EventConfig.raid().necroticMaxStacks);
	}

	// ---------------------------------------------------------------- v0.14.23: Oathbound upgrade

	/** Extra kill stacks an Oathbound blade can hold. */
	public static final int OATHBOUND_EXTRA_STACKS = 3;
	/** Extra Wither chance on an Oathbound blade. */
	public static final double OATHBOUND_EXTRA_WITHER = 0.15;
	/** Bonus attack damage over the base Netherite tier (the plain blade's is 5). */
	public static final int OATHBOUND_ATTACK_BONUS = 8;

	public static boolean isOathbound(ItemStack stack) {
		return stack.has(GraveComponents.OATHBOUND);
	}

	/** Turns {@code stack} into an Oathbound blade: the marker, and the higher attack damage. */
	public static void makeOathbound(ItemStack stack) {
		stack.set(GraveComponents.OATHBOUND, net.minecraft.util.Unit.INSTANCE);
		stack.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
				SwordItem.createAttributes(net.minecraft.world.item.Tiers.NETHERITE, OATHBOUND_ATTACK_BONUS, -2.4f));
	}

	public static int maxStacks(ItemStack stack) {
		return maxStacks() + (isOathbound(stack) ? OATHBOUND_EXTRA_STACKS : 0);
	}

	public static double witherChance(ItemStack stack) {
		return EventConfig.raid().necroticWitherChance + (isOathbound(stack) ? OATHBOUND_EXTRA_WITHER : 0.0);
	}

	@Override
	public Component getName(ItemStack stack) {
		return isOathbound(stack) ? Component.translatable("item.projecthero.necrotic_blade.oathbound")
				: super.getName(stack);
	}

	/** Live stack count -- zero once the window has passed, whatever the stored number is. */
	public static int stacks(ItemStack stack, long gameTime) {
		Long expires = stack.get(GraveComponents.BLADE_EXPIRES_AT);
		if (expires == null || gameTime >= expires) {
			return 0;
		}
		Integer count = stack.get(GraveComponents.BLADE_STACKS);
		return count == null ? 0 : Math.max(0, Math.min(maxStacks(stack), count));
	}

	/** Bonus damage from the current stacks. */
	public static float bonusDamage(ItemStack stack, long gameTime) {
		return (float) (stacks(stack, gameTime) * EventConfig.raid().necroticDamagePerStack);
	}

	/** Add one stack (clamped) and refresh the shared expiry. Called when the blade kills an undead. */
	public static void addStack(ItemStack stack, long gameTime) {
		int next = Math.min(maxStacks(stack), stacks(stack, gameTime) + 1);
		stack.set(GraveComponents.BLADE_STACKS, next);
		stack.set(GraveComponents.BLADE_EXPIRES_AT, gameTime + EventConfig.raid().necroticStackTicks);
	}

	/** Wither chance per hit, from config (higher on an Oathbound blade). */
	public static void maybeWither(ItemStack blade, LivingEntity target, net.minecraft.util.RandomSource random) {
		if (random.nextDouble() < witherChance(blade)) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.necrotic_blade.wither",
				Math.round(witherChance(stack) * 100)).withStyle(ChatFormatting.DARK_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.necrotic_blade.stacks", maxStacks(stack))
				.withStyle(ChatFormatting.GRAY));
		if (isOathbound(stack)) {
			tooltip.add(Component.translatable("item.projecthero.necrotic_blade.oathbound.tip").withStyle(ChatFormatting.DARK_RED));
		} else {
			tooltip.add(Component.translatable("item.projecthero.necrotic_blade.upgrade_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

}

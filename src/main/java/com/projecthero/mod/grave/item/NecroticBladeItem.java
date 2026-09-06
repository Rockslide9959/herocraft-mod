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

	/** Live stack count -- zero once the window has passed, whatever the stored number is. */
	public static int stacks(ItemStack stack, long gameTime) {
		Long expires = stack.get(GraveComponents.BLADE_EXPIRES_AT);
		if (expires == null || gameTime >= expires) {
			return 0;
		}
		Integer count = stack.get(GraveComponents.BLADE_STACKS);
		return count == null ? 0 : Math.max(0, Math.min(maxStacks(), count));
	}

	/** Bonus damage from the current stacks. */
	public static float bonusDamage(ItemStack stack, long gameTime) {
		return (float) (stacks(stack, gameTime) * EventConfig.raid().necroticDamagePerStack);
	}

	/** Add one stack (clamped) and refresh the shared expiry. Called when the blade kills an undead. */
	public static void addStack(ItemStack stack, long gameTime) {
		int next = Math.min(maxStacks(), stacks(stack, gameTime) + 1);
		stack.set(GraveComponents.BLADE_STACKS, next);
		stack.set(GraveComponents.BLADE_EXPIRES_AT, gameTime + EventConfig.raid().necroticStackTicks);
	}

	/** Wither chance per hit, from config. */
	public static void maybeWither(LivingEntity target, net.minecraft.util.RandomSource random) {
		if (random.nextDouble() < EventConfig.raid().necroticWitherChance) {
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0));
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.necrotic_blade.wither",
				Math.round(EventConfig.raid().necroticWitherChance * 100)).withStyle(ChatFormatting.DARK_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.necrotic_blade.stacks", maxStacks())
				.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

}

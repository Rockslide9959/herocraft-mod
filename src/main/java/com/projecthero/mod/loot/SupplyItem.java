package com.projecthero.mod.loot;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * v0.17.5: the four hero supplies -- Energy Cell, Cooldown Stim, Suit Repair Kit, Med Kit. Hold right-click for a short
 * use, then the effect lands; a supply that would do nothing is refused with a message and is not used up. Each kind
 * puts the item on a short cooldown after a successful use.
 */
public class SupplyItem extends Item {
	/** Energy Cell refill: 40% of the meter's max. */
	public static final float ENERGY_FRACTION = 0.4f;
	/** Cooldown Stim: every remaining cooldown x 0.5. */
	public static final double STIM_FACTOR = 0.5;
	/** Suit Repair Kit: 50% of each piece's max durability / of the suit's integrity. */
	public static final float REPAIR_FRACTION = 0.5f;

	public enum Kind {
		ENERGY_CELL("energy_cell", 20, 20 * 20, UseAnim.BOW),
		COOLDOWN_STIM("cooldown_stim", 16, 30 * 20, UseAnim.SPEAR),
		SUIT_REPAIR_KIT("suit_repair_kit", 32, 5 * 20, UseAnim.BRUSH),
		MED_KIT("med_kit", 24, 10 * 20, UseAnim.BOW);

		public final String id;
		public final int useTicks;
		public final int cooldownTicks;
		public final UseAnim anim;

		Kind(String id, int useTicks, int cooldownTicks, UseAnim anim) {
			this.id = id;
			this.useTicks = useTicks;
			this.cooldownTicks = cooldownTicks;
			this.anim = anim;
		}
	}

	private final Kind kind;

	public SupplyItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return kind.useTicks;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return kind.anim;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof ServerPlayer sp) {
			Component why = refusalAtStart(sp);
			if (why != null) {
				sp.displayClientMessage(why, true);
				return InteractionResultHolder.fail(stack);
			}
		} else if (kind == Kind.MED_KIT && !needsMedKit(player)) {
			return InteractionResultHolder.fail(stack); // the client knows health and effects: don't even start the animation
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player)) {
			return stack;
		}
		Component refusal = applyEffect(player);
		if (refusal != null) {
			player.displayClientMessage(refusal, true);
			level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.2f);
			return stack;
		}
		level.playSound(null, player.blockPosition(), sound(), SoundSource.PLAYERS, 0.9f, 1.0f);
		player.displayClientMessage(Component.translatable("item.projecthero." + kind.id + ".used").withStyle(ChatFormatting.GREEN), true);
		player.getCooldowns().addCooldown(this, kind.cooldownTicks);
		player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
		stack.consume(1, player);
		return stack;
	}

	/** A cheap check before the animation starts (only where the answer is known up front). */
	private Component refusalAtStart(ServerPlayer player) {
		return switch (kind) {
			case ENERGY_CELL -> EnergyCellTargets.first(player) == null ? noMeter() : null;
			case MED_KIT -> needsMedKit(player) ? null : Component.translatable("item.projecthero.med_kit.refused").withStyle(ChatFormatting.RED);
			default -> null;
		};
	}

	/** Applies the supply; returns a refusal message (and changes nothing) when it would do nothing. */
	public Component applyEffect(ServerPlayer player) {
		switch (kind) {
			case ENERGY_CELL -> {
				EnergyCellTargets.Result r = EnergyCellTargets.apply(player, ENERGY_FRACTION);
				if (r == EnergyCellTargets.Result.NO_METER) {
					return noMeter();
				}
				if (r == EnergyCellTargets.Result.ALREADY_FULL) {
					return Component.translatable("item.projecthero.energy_cell.full").withStyle(ChatFormatting.RED);
				}
				return null;
			}
			case COOLDOWN_STIM -> {
				return CooldownTargets.apply(player, STIM_FACTOR) > 0 ? null
						: Component.translatable("item.projecthero.cooldown_stim.refused").withStyle(ChatFormatting.RED);
			}
			case SUIT_REPAIR_KIT -> {
				return SuitRepairTargets.apply(player, REPAIR_FRACTION) ? null
						: Component.translatable("item.projecthero.suit_repair_kit.refused").withStyle(ChatFormatting.RED);
			}
			case MED_KIT -> {
				if (!needsMedKit(player)) {
					return Component.translatable("item.projecthero.med_kit.refused").withStyle(ChatFormatting.RED);
				}
				MedKitHealing.start(player);
				return null;
			}
			default -> {
				return null;
			}
		}
	}

	private static Component noMeter() {
		return Component.translatable("item.projecthero.energy_cell.no_meter").withStyle(ChatFormatting.RED);
	}

	private static boolean needsMedKit(Player player) {
		return player.getHealth() < player.getMaxHealth() - 0.01f || player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER);
	}

	private SoundEvent sound() {
		return switch (kind) {
			case ENERGY_CELL -> SoundEvents.BEACON_POWER_SELECT;
			case COOLDOWN_STIM -> SoundEvents.BREWING_STAND_BREW;
			case SUIT_REPAIR_KIT -> SoundEvents.SMITHING_TABLE_USE;
			case MED_KIT -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
		};
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero." + kind.id + ".hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.loot.supply_use").withStyle(ChatFormatting.DARK_GRAY));
	}
}

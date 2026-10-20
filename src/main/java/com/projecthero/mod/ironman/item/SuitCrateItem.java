package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.era1.EraOneGantry;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.16.7 (user) ERA I: the <b>Suit Crate</b> an Era I suit is packed into by the Shift+C emergency eject
 * ({@code ability.era1.EraOneEject}). It holds the four real armour stacks (the vanilla {@code minecraft:container}
 * component, slot 0 helmet .. 3 boots -- {@link SuitcaseContents}), each stamped with the suit's integrity and energy.
 * <ul>
 *   <li>Heavy: Slowness I while it is anywhere in your inventory.</li>
 *   <li>Used while standing on a complete Stark Gantry floor: the Gantry's arms put the suit back on you
 *       ({@code StarkGantry.beginEquipFromCrate}, the ordinary gantry build).</li>
 *   <li>Used on a Suit Platform within range of a complete Gantry: the suit is racked there
 *       ({@link #rackOn}, from {@code IronManSuitPlatformBlock}).</li>
 *   <li>Nowhere else: it cannot be worn or opened.</li>
 * </ul>
 */
public class SuitCrateItem extends Item {
	/** Ticks of the carried Slowness, refreshed while the crate is in the inventory (it fades soon after it leaves). */
	private static final int SLOW_TICKS = 30;

	public SuitCrateItem(Properties properties) {
		super(properties);
	}

	/** A new crate holding {@code slots} (slot 0 helmet .. 3 boots). */
	public static ItemStack of(NonNullList<ItemStack> slots) {
		ItemStack crate = new ItemStack(IronManItems.SUIT_CRATE);
		SuitcaseContents.write(crate, slots);
		return crate;
	}

	/** The suit id of the first piece in the crate, or null if it is empty. */
	public static String suitId(ItemStack crate) {
		for (ItemStack s : SuitcaseContents.read(crate)) {
			if (s.getItem() instanceof IronManArmorItem a) {
				return a.suitId();
			}
		}
		return null;
	}

	/** The crate's stacks by rack slot (0 HEAD .. 3 FEET), EMPTY where none -- copies. */
	public static ItemStack[] pieces(ItemStack crate) {
		NonNullList<ItemStack> slots = SuitcaseContents.read(crate);
		ItemStack[] out = new ItemStack[4];
		for (int i = 0; i < 4; i++) {
			out[i] = i < slots.size() ? slots.get(i).copy() : ItemStack.EMPTY;
		}
		return out;
	}

	/** Rack-slot mask of the pieces in the crate. */
	public static int mask(ItemStack crate) {
		ItemStack[] p = pieces(crate);
		int mask = 0;
		for (int i = 0; i < 4; i++) {
			mask |= p[i].getItem() instanceof IronManArmorItem ? 1 << i : 0;
		}
		return mask;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, level, entity, slot, selected);
		if (!level.isClientSide() && entity instanceof ServerPlayer p && !p.getAbilities().instabuild && level.getGameTime() % 10L == 0L) {
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0, true, false, true));
		}
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		if (!EraOneGantry.onGantryFloor(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_where").withStyle(ChatFormatting.GOLD), true);
			return InteractionResultHolder.fail(stack);
		}
		return com.projecthero.mod.ironman.gantry.StarkGantry.beginEquipFromCrate(sp, hand)
				? InteractionResultHolder.success(sp.getItemInHand(hand)) : InteractionResultHolder.fail(stack);
	}

	/**
	 * Rack the crate's suit on {@code be} -- only within range of a complete Stark Gantry, on a rack this player may use
	 * with room for every piece (all-or-nothing). Uses up the crate. Returns whether it racked.
	 */
	public static boolean rackOn(ServerPlayer player, IronManSuitPlatformBlockEntity be, ItemStack crate) {
		String suitId = suitId(crate);
		if (suitId == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_empty").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (!EraOneGantry.platformInRange(be.getLevel(), be.getBlockPos())) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_needs_gantry").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (be.owner().isPresent() && !be.owner().get().equals(player.getUUID()) || !be.canAccept(suitId, mask(crate))) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.case_platform_full").withStyle(ChatFormatting.RED), true);
			return false;
		}
		for (ItemStack piece : pieces(crate)) {
			if (piece.getItem() instanceof IronManArmorItem) {
				be.store(piece);
			}
		}
		if (be.owner().isEmpty()) {
			be.bindTo(player.getUUID());
		}
		crate.shrink(1);
		if (be.getLevel() instanceof ServerLevel sl) {
			sl.playSound(null, be.getBlockPos(), SoundEvents.NETHERITE_BLOCK_PLACE, SoundSource.BLOCKS, 0.9f, 0.8f);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.era1.crate_racked").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String suitId = suitId(stack);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit != null) {
			ItemStack ref = pieces(stack)[1].isEmpty() ? null : pieces(stack)[1];
			for (ItemStack p : pieces(stack)) {
				if (ref == null && !p.isEmpty()) {
					ref = p;
				}
			}
			tooltip.add(Component.translatable("item.projecthero.suit_crate.holds", Component.translatable(suit.nameKey()),
					Integer.bitCount(mask(stack))).withStyle(ChatFormatting.GRAY));
			if (ref != null) {
				int integ = Math.round(100f * IronManEnergy.stackIntegrity(ref, suitId) / Math.max(1f, suit.maxIntegrity()));
				int en = Math.round(100f * IronManEnergy.stackEnergy(ref, suitId) / Math.max(1f, suit.energyCapacity()));
				tooltip.add(Component.translatable("item.projecthero.suit_crate.status", integ, en).withStyle(ChatFormatting.DARK_AQUA));
			}
		}
		tooltip.add(Component.translatable("item.projecthero.suit_crate.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}

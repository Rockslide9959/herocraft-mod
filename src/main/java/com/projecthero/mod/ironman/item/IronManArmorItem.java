package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * One piece of an Iron Man suit. Carries its suit id and slot type so the restriction system
 * ({@link com.projecthero.mod.ironman.IronManArmor}), the ability system and the suit-summon system can
 * all identify it from the {@link ItemStack} alone.
 *
 * <p>The item deliberately does <em>not</em> grant abilities. Wearing it does nothing on its own:
 * every Iron Man capability is gated server-side on {@code player.hasPower(TONY_STARK) &&
 * isWearingValidIronManSuit()}. A player without the Tony Stark power who somehow gets a piece into
 * an armor slot has it ejected on the next server tick with "Stark armor rejects unauthorized user."
 * (see {@link com.projecthero.mod.ironman.IronManArmor#enforce}); the item is never destroyed.
 */
public class IronManArmorItem extends SuperheroArmorItem {
	private final String suitId;

	public IronManArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String suitId) {
		super(material, type, properties);
		this.suitId = suitId;
	}

	public String suitId() {
		return suitId;
	}

	// v0.14.21 self-assembly: no extra GeckoLib controller any more. The per-piece lock-on / release and the H
	// faceplate lift are procedural per-bone offsets from the synced clocks (client IronManAssemblyClient, applied in
	// SuperheroArmorRenderer#renderRecursively), so the old suit_lock_on / suit_release / helmet_open / helmet_close
	// clip triggers that fought them are gone -- one system.
	/** The armour set id IS the suit id ({@code mark_iii}, ...) -- see {@link com.projecthero.mod.armor.SuperheroArmorVisuals}. */
	@Override
	public String armorSetId() {
		return suitId;
	}

	/**
	 * "changes 21": the Mark 1 is the only suit built at a normal crafting table -- record each piece
	 * as it is crafted so a full Mark 1 unlocks the Mark 2 blueprint in the Blank Blueprint picker.
	 * Every other mark is Fabricator-built and records its pieces from
	 * {@code StarkFabricatorBlockEntity}; this hook only ever fires for the table recipes.
	 */
	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		super.onCraftedBy(stack, level, player);
		if (player instanceof ServerPlayer serverPlayer
				&& com.projecthero.mod.ironman.TonyStark.hasPower(serverPlayer)) {
			com.projecthero.mod.ironman.TonyStark.recordSuitPiece(serverPlayer, suitId, getType());
		}
	}

	/**
	 * v0.14.29: the Mark 5 only ever deploys from its suitcase, so right-clicking any loose Mark 5 piece while all four
	 * are in your inventory packs them into a fresh Mark 5 Suitcase (hand, hotbar, inventory, else at your feet)
	 * instead of strapping on one piece. v0.14.30: otherwise right-click does nothing (no hand-equipping).
	 */
	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
		if ("mark_v".equals(suitId) && player instanceof ServerPlayer sp && packLooseMarkV(sp)) {
			return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
		}
		if ("mark_v".equals(suitId) && level.isClientSide() && hasAllLooseMarkV(player)) {
			return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
		}
		// v0.14.30, explicit user request: no right-click equipping -- Iron Man armour goes on with C or a suit deploy
		if (player instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("mark_v".equals(suitId)
					? "message.projecthero.ironman.no_manual_equip_mk5" : "message.projecthero.ironman.no_manual_equip")
					.withStyle(ChatFormatting.GOLD), true);
		}
		return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
	}

	private static boolean hasAllLooseMarkV(Player player) {
		boolean[] have = new boolean[4];
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && "mark_v".equals(a.suitId)) {
				have[SuitcaseContents.slotOf(a.getType())] = true;
			}
		}
		return have[0] && have[1] && have[2] && have[3];
	}

	/** Moves one of each loose Mark 5 piece out of the inventory into a new case. Returns false (no change) unless all four are there. */
	public static boolean packLooseMarkV(ServerPlayer player) {
		if (!hasAllLooseMarkV(player)) {
			return false;
		}
		var inv = player.getInventory();
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack s = inv.items.get(i);
			if (s.getItem() instanceof IronManArmorItem a && "mark_v".equals(a.suitId)) {
				int idx = SuitcaseContents.slotOf(a.getType());
				if (slots.get(idx).isEmpty()) {
					slots.set(idx, s.split(1));
				}
			}
		}
		ItemStack caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		SuitcaseContents.write(caseStack, slots);
		com.projecthero.mod.ironman.suit.IronManSuitUpManager.placeSuitcase(player, caseStack);
		inv.setChanged();
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk5_packed").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.ironman.suit_piece",
				Component.translatable("projecthero.ironman.suit." + suitId + ".name")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
		com.projecthero.mod.ultron.item.VibraniumPlating.appendTooltip(stack, tooltip); // v0.15.12
	}
}

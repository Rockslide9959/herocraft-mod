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
		// v0.16.7 (user) Era II fold polish: EVERY suit with a portable form packs this way -- the Mk VI into its briefcase,
		// the Mk VII / Mk VIII into their packs, the Mk IX into its heat case, the Hulkbuster into its Uplink
		if (com.projecthero.mod.ironman.suit.PortableForms.byId(suitId) != null) {
			if (player instanceof ServerPlayer sp && packLoose(sp, suitId)) {
				return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
			}
			if (level.isClientSide() && hasAllLoose(player, suitId)) {
				return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
			}
		}
		// v0.14.30, explicit user request: no right-click equipping -- Iron Man armour goes on with C or a suit deploy
		if (player instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable(com.projecthero.mod.ironman.suit.IronManSuitUpManager.BRIEFCASE_SUIT.equals(suitId)
					? "message.projecthero.ironman.no_manual_equip_mk5" : "message.projecthero.ironman.no_manual_equip")
					.withStyle(ChatFormatting.GOLD), true);
		}
		return net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
	}

	/**
	 * Moves one of each loose briefcase-suit (Mk VI) piece out of the inventory into its case. Returns false (no change)
	 * unless all four are there. v0.16.7 (user): the shared {@link #packLoose(ServerPlayer, String)}.
	 */
	public static boolean packLooseMarkV(ServerPlayer player) {
		return packLoose(player, com.projecthero.mod.ironman.suit.IronManSuitUpManager.BRIEFCASE_SUIT);
	}

	/**
	 * v0.16.7 (user) Era II fold polish: right-click a loose piece of any suit with a portable form, with all four in your
	 * inventory, to pack them into that form's packed item (the action-bar line names it). False (no change) unless the
	 * suit has a form and all four pieces are there.
	 */
	public static boolean packLoose(ServerPlayer player, String suit) {
		com.projecthero.mod.ironman.suit.PortableForm form = com.projecthero.mod.ironman.suit.PortableForms.byId(suit);
		if (form == null) {
			return false;
		}
		String message = switch (form.rest()) {
			case BACK -> "message.projecthero.ironman.pack_packed";
			case BLOCK -> "message.projecthero.ironman.uplink_packed";
			default -> "message.projecthero.ironman.case_packed";
		};
		return packLoose(player, suit, form.packedItem().get(), message);
	}

	/**
	 * v0.16.7 (user) Era II fold polish: an EMPTIED packed item of {@code packedItem} in the inventory (one crafted empty,
	 * or an Uplink whose suit is down) to fill first, so packing never strands an empty one -- the selected slot first.
	 * Index, or -1.
	 */
	static int emptiedPacked(Player player, net.minecraft.world.item.Item packedItem) {
		var inv = player.getInventory();
		if (inv.selected >= 0 && inv.selected < 9) {
			ItemStack sel = inv.items.get(inv.selected);
			if (sel.is(packedItem) && !SuitcaseContents.isLegacyEmpty(sel) && SuitcaseContents.isEmpty(sel)) {
				return inv.selected;
			}
		}
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack st = inv.items.get(i);
			if (st.is(packedItem) && !SuitcaseContents.isLegacyEmpty(st) && SuitcaseContents.isEmpty(st)) {
				return i;
			}
		}
		return -1;
	}

	/** v0.16.7 (user) Era II: are all four loose pieces of {@code suit} in the main inventory? */
	static boolean hasAllLoose(Player player, String suit) {
		boolean[] have = new boolean[4];
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && suit.equals(a.suitId)) {
				have[SuitcaseContents.slotOf(a.getType())] = true;
			}
		}
		return have[0] && have[1] && have[2] && have[3];
	}

	/**
	 * v0.16.7 (user) Era II: {@link #packLooseMarkV} for any suit with a packed item -- one of each loose piece of
	 * {@code suit} leaves the inventory for a fresh {@code packedItem} (hand, hotbar, inventory, else at your feet). False
	 * (no change) unless all four are there. v0.16.7 (user) fold polish: an emptied {@code packedItem} already carried is
	 * filled instead of making a new one, and a backpack goes on the back (never into the hand, {@code placePack}).
	 */
	public static boolean packLoose(ServerPlayer player, String suit, net.minecraft.world.item.Item packedItem, String message) {
		if (!hasAllLoose(player, suit)) {
			return false;
		}
		var inv = player.getInventory();
		int emptied = emptiedPacked(player, packedItem);
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack s = inv.items.get(i);
			if (s.getItem() instanceof IronManArmorItem a && suit.equals(a.suitId)) {
				int idx = SuitcaseContents.slotOf(a.getType());
				if (slots.get(idx).isEmpty()) {
					slots.set(idx, s.split(1));
				}
			}
		}
		ItemStack packed = emptied >= 0 ? inv.items.get(emptied) : new ItemStack(packedItem);
		SuitcaseContents.write(packed, slots);
		// the case carries the suit's integrity: the most worn piece's (the deploy loads it from the case)
		float integrity = Float.MAX_VALUE;
		float energy = Float.MAX_VALUE; // v0.16.7 (user) Era II: and its reactor charge (the emptiest piece's)
		for (ItemStack piece : slots) {
			integrity = Math.min(integrity, com.projecthero.mod.ironman.IronManEnergy.stackIntegrity(piece, suit));
			if (!piece.isEmpty()) {
				energy = Math.min(energy, com.projecthero.mod.ironman.IronManEnergy.stackEnergy(piece, suit));
			}
		}
		com.projecthero.mod.ironman.IronManEnergy.stampStack(packed,
				energy == Float.MAX_VALUE ? com.projecthero.mod.ironman.IronManEnergy.capacity(suit) : energy, integrity);
		if (emptied < 0) {
			com.projecthero.mod.ironman.suit.PortableForm form = com.projecthero.mod.ironman.suit.PortableForms.byItem(packedItem);
			if (form != null && form.rest() == com.projecthero.mod.ironman.suit.PortableForm.Place.BACK) {
				com.projecthero.mod.ironman.suit.PortableDeploy.placePack(player, packed);
			} else {
				com.projecthero.mod.ironman.suit.IronManSuitUpManager.placeSuitcase(player, packed);
			}
		}
		inv.setChanged();
		player.displayClientMessage(Component.translatable(message, packed.getHoverName()).withStyle(ChatFormatting.AQUA), true);
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

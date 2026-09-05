package com.herocraft.mod.ironman.item;

import java.util.List;

import com.herocraft.mod.armor.SuperheroArmorItem;

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
 * ({@link com.herocraft.mod.ironman.IronManArmor}), the ability system and the suit-summon system can
 * all identify it from the {@link ItemStack} alone.
 *
 * <p>The item deliberately does <em>not</em> grant abilities. Wearing it does nothing on its own:
 * every Iron Man capability is gated server-side on {@code player.hasPower(TONY_STARK) &&
 * isWearingValidIronManSuit()}. A player without the Tony Stark power who somehow gets a piece into
 * an armor slot has it ejected on the next server tick with "Stark armor rejects unauthorized user."
 * (see {@link com.herocraft.mod.ironman.IronManArmor#enforce}); the item is never destroyed.
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

	/** The armour set id IS the suit id ({@code mark_iii}, ...) -- see {@link com.herocraft.mod.armor.SuperheroArmorVisuals}. */
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
				&& com.herocraft.mod.ironman.TonyStark.hasPower(serverPlayer)) {
			com.herocraft.mod.ironman.TonyStark.recordSuitPiece(serverPlayer, suitId, getType());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.herocraft.ironman.suit_piece",
				Component.translatable("herocraft.ironman.suit." + suitId + ".name")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.herocraft.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}

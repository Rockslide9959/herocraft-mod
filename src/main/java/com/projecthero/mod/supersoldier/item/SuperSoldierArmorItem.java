package com.projecthero.mod.supersoldier.item;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.SuperSoldierArmorGate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * One piece of the craftable Captain America suit (v0.14.9). Only a Super Soldier can wear it: a right-click equip is refused here,
 * every other route is undone by {@link SuperSoldierArmorGate} on the next tick.
 *
 * <p>v0.17.7 (user: "make this the new captain America armour please: https://www.planetminecraft.com/skin/captain-america-classic-6692155/
 * Remove the shield from the back of the suit and dont make the armour bulky please ..., armour should be diamond level"): no longer a
 * bulky GeckoLib model. The suit is a skin costume ({@link #texture}, 64x64 wide / slim) drawn skin-tight over the wearer by the client's
 * {@code QuirkCostume} (the Battle Suit / Undead / Gyro path: base layer a hair over the body, outer layer at vanilla's jacket / sleeve
 * offsets, each piece only its own parts); {@code QuirkSuitArmourMixin} skips the vanilla armour model. Diamond stats (3 / 8 / 6 / 3,
 * toughness 2). Borrowed skin: "Captain America | Classic" on PlanetMinecraft (PMC file 19513766), the painted shield on its back
 * removed by {@code scratchpad/v0177/make_cap_suit.js}.
 */
public class SuperSoldierArmorItem extends ArmorItem {
	public static final String SET_ID = "captain_america";
	/** Where the suit's skin comes from (PlanetMinecraft, ships publicly). */
	public static final String SOURCE = "https://www.planetminecraft.com/skin/captain-america-classic-6692155/";
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/supersoldier/captain_america_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/supersoldier/captain_america_slim.png");

	public SuperSoldierArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	/** The suit's costume skin for the wearer's arm width. */
	public static ResourceLocation texture(boolean slim) {
		return slim ? SLIM : WIDE;
	}

	/** A Captain America suit piece is worn in this slot. */
	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e.getItemBySlot(slot).getItem() instanceof SuperSoldierArmorItem a && a.getEquipmentSlot() == slot;
	}

	public static boolean anyWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.HEAD) || wornIn(e, EquipmentSlot.CHEST) || wornIn(e, EquipmentSlot.LEGS) || wornIn(e, EquipmentSlot.FEET);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		if (!SuperSoldierArmorGate.mayWear(player)) {
			if (!level.isClientSide) {
				player.displayClientMessage(SuperSoldierArmorGate.lockedMessage(), true);
			}
			return InteractionResultHolder.fail(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.captain_america_suit.desc").withStyle(ChatFormatting.BLUE));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}

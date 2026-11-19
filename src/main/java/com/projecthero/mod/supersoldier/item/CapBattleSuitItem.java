package com.projecthero.mod.supersoldier.item;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.CapShieldSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * v0.17.5 (user: "Super Soldier: add this captain america suit to the game please: https://namemc.com/skin/63b230543038f21b . when players
 * with super soldier wear this they can press N and put the shield into a shield slot. the shield will always count as "held" when this
 * happens"): the <b>Captain America Battle Suit</b> -- cowl, top, trousers and boots. Anyone can wear it (it is armour); only a Super
 * Soldier wearing the <b>top</b> gets the shield slot ({@link CapShieldSlot}).
 *
 * <ul>
 *   <li><b>Protection</b>: 3 / 7 / 6 / 3 (19, between iron and diamond), toughness 1.5, durability multiplier 30, repaired with leather.</li>
 *   <li><b>The look</b> is the costume skin ({@link #texture}, 64x64 wide / slim) drawn over the wearer by the skin-costume path
 *   ({@code QuirkCostume}); the vanilla armour model is never drawn ({@code QuirkSuitArmourMixin}). The cowl keeps the face open: the
 *   wearer's own eyes, nose and mouth show.</li>
 *   <li><b>Held back</b>: the textures made from the user's NameMC skin ({@code cap_battle_suit_wide/_slim.png}, by
 *   {@code scratchpad/supersoldier/make_cap_suit_textures.js}) are {@code .gitignore}d -- the skin's author and licence are unknown -- so a
 *   public build has no such file and the client falls back to the mod's own original suit ({@code cap_battle_suit_original_*.png}).</li>
 *   <li><b>The shield slot</b> lives on the top as the {@link CapShieldSlot#SLOT} component: it survives death and relogs and drops with
 *   the top.</li>
 * </ul>
 */
public class CapBattleSuitItem extends ArmorItem {
	/** The borrowed skin's page (the user's pick), for the credits / licence check before a public release. */
	public static final String SOURCE = "https://namemc.com/skin/63b230543038f21b";
	public static final ResourceLocation WIDE = ProjectHeroMod.id("textures/entity/supersoldier/cap_battle_suit_wide.png");
	public static final ResourceLocation SLIM = ProjectHeroMod.id("textures/entity/supersoldier/cap_battle_suit_slim.png");
	public static final ResourceLocation ORIGINAL_WIDE = ProjectHeroMod.id("textures/entity/supersoldier/cap_battle_suit_original_wide.png");
	public static final ResourceLocation ORIGINAL_SLIM = ProjectHeroMod.id("textures/entity/supersoldier/cap_battle_suit_original_slim.png");

	public static Holder<ArmorMaterial> MATERIAL;
	public static CapBattleSuitItem COWL, TOP, TROUSERS, BOOTS;

	public CapBattleSuitItem(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	static void register() {
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("cap_battle_suit")),
				new ArmorMaterial(Map.of(Type.BOOTS, 3, Type.LEGGINGS, 6, Type.CHESTPLATE, 7, Type.HELMET, 3, Type.BODY, 7), 12,
						SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER),
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("cap_battle_suit"))), 1.5f, 0f));
		COWL = piece("cap_battle_cowl", Type.HELMET);
		TOP = piece("cap_battle_suit_top", Type.CHESTPLATE);
		TROUSERS = piece("cap_battle_suit_trousers", Type.LEGGINGS);
		BOOTS = piece("cap_battle_suit_boots", Type.BOOTS);
	}

	private static CapBattleSuitItem piece(String path, Type type) {
		return Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new CapBattleSuitItem(MATERIAL, type, new Properties().rarity(Rarity.RARE).durability(type.getDurability(30))));
	}

	/** The borrowed costume skin for the wearer's arm width (absent from public builds: see {@link #originalTexture}). */
	public static ResourceLocation texture(boolean slim) {
		return slim ? SLIM : WIDE;
	}

	/** The mod's own original suit, drawn when the borrowed texture isn't in the build. */
	public static ResourceLocation originalTexture(boolean slim) {
		return slim ? ORIGINAL_SLIM : ORIGINAL_WIDE;
	}

	public static boolean wornIn(LivingEntity e, EquipmentSlot slot) {
		return e != null && e.getItemBySlot(slot).getItem() instanceof CapBattleSuitItem;
	}

	public static boolean anyWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.HEAD) || wornIn(e, EquipmentSlot.CHEST) || wornIn(e, EquipmentSlot.LEGS) || wornIn(e, EquipmentSlot.FEET);
	}

	/** The top (the harness with the shield clamp) is on. Client-safe: armour is synced to every viewer. */
	public static boolean topWorn(LivingEntity e) {
		return wornIn(e, EquipmentSlot.CHEST);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.cap_battle_suit.desc").withStyle(ChatFormatting.GRAY));
		if (getType() == Type.CHESTPLATE) {
			tooltip.add(Component.translatable("item.projecthero.cap_battle_suit.slot_hint").withStyle(ChatFormatting.BLUE));
			ItemStack held = CapShieldSlot.get(stack);
			if (!held.isEmpty()) {
				tooltip.add(Component.translatable("item.projecthero.cap_battle_suit.slotted", held.getHoverName()).withStyle(ChatFormatting.AQUA));
			}
		}
	}
}

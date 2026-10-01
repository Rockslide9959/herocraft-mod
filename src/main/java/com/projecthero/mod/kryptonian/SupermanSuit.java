package com.projecthero.mod.kryptonian;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.kryptonian.item.SupermanSuitItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.9: the Superman Suit -- a craftable armour set (the user's {@code superman.bbmodel}) that only a
 * Kryptonian can wear. v0.14.10: no helmet any more -- chestplate, leggings and boots only, so the wearer's own face and hat layer
 * always show. Netherite-strength (8/6/3, toughness 3, 10% knockback resistance), fire-resistant like
 * netherite; the chestplate brings the red cloth cape ({@code SupermanCapeLayer}, client). No powers of its own.
 *
 * <h2>Only Kryptonians</h2>
 * <ul>
 *   <li><b>Inventory armour slots, shift-click, hotbar swap, the creative screen</b>: {@code LivingEntitySupermanSuitMixin}
 *       tells the game a non-Kryptonian player's "slot for this item" is the main hand, so {@code ArmorSlot.mayPlace}
 *       refuses it and shift-click puts it in the inventory instead (client and server agree: the power is synced);</li>
 *   <li><b>right-click to wear</b>: {@link SupermanSuitItem#use} refuses with an action-bar line;</li>
 *   <li><b>dispensers</b>: {@link #DISPENSE} only fits it onto a Kryptonian (or an armour stand), otherwise it is shot
 *       out like any item -- vanilla's armour behaviour would have pushed it into a non-Kryptonian's hand;</li>
 *   <li><b>anything else</b> (commands, a power lost while wearing it): {@link #tick} pops a worn piece off into the
 *       inventory (dropped at his feet if it is full) with an action-bar line.</li>
 * </ul>
 */
public final class SupermanSuit {
	public static final String SET_ID = "superman";

	public static Holder<ArmorMaterial> MATERIAL;
	public static SupermanSuitItem CHESTPLATE;
	public static SupermanSuitItem LEGGINGS;
	public static SupermanSuitItem BOOTS;

	private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private SupermanSuit() {
	}

	/** Called from {@code KryptonianItems.initialize}. */
	public static void initialize() {
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("superman")),
				new ArmorMaterial(
						Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8,
								ArmorItem.Type.HELMET, 3, ArmorItem.Type.BODY, 11),
						15,
						SoundEvents.ARMOR_EQUIP_LEATHER,
						() -> Ingredient.of(Items.DIAMOND),
						// the flat vanilla layer is never seen (GeckoLib draws the suit); Thor's, like every other set
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("thor"))),
						3.0f,
						0.1f));
		CHESTPLATE = piece("superman_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = piece("superman_suit_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = piece("superman_suit_boots", ArmorItem.Type.BOOTS);
		SuperheroArmorVisuals.register(SET_ID, new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/superman.geo.json"),
				ProjectHeroMod.id("textures/armor/superman.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
	}

	private static SupermanSuitItem piece(String path, ArmorItem.Type type) {
		SupermanSuitItem item = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new SupermanSuitItem(MATERIAL, type, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
						.durability(type.getDurability(37))));
		DispenserBlock.registerBehavior(item, DISPENSE); // replaces vanilla's armour behaviour (registered by ArmorItem)
		return item;
	}

	// ---------------------------------------------------------------- the rule

	/** Whether {@code player} may wear the suit: only a Kryptonian. Client-safe (the power is synced). */
	public static boolean mayWear(Player player) {
		return Kryptonian.hasPower(player);
	}

	public static boolean isSuit(ItemStack stack) {
		return stack.getItem() instanceof SupermanSuitItem;
	}

	/** Wearing the Superman chestplate -- the cape shows. */
	public static boolean wearsCape(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SupermanSuitItem;
	}

	public static void refuse(Player player) {
		player.displayClientMessage(Component.translatable("message.projecthero.superman_suit.refused")
				.withStyle(ChatFormatting.RED), true);
	}

	/** v0.14.14: in direct sunlight the worn suit mends this much per piece, every {@link #SUN_REPAIR_EVERY} ticks. */
	public static final int SUN_REPAIR_AMOUNT = 1;
	public static final int SUN_REPAIR_EVERY = 20;

	/**
	 * Every tick, for every player (cheap): a piece worn by someone who is not a Kryptonian comes off. v0.14.14: a
	 * Kryptonian standing in direct sunlight slowly mends every Superman Suit piece they wear -- the suit soaks up the sun
	 * the way he does.
	 */
	public static void tick(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		if (mayWear(player)) {
			if (player.tickCount % SUN_REPAIR_EVERY == 0 && Kryptonian.sun(player) == Kryptonian.Sun.DIRECT) {
				sunRepair(player);
			}
			return;
		}
		boolean popped = false;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!isSuit(worn)) {
				continue;
			}
			player.setItemSlot(slot, ItemStack.EMPTY);
			if (!player.getInventory().add(worn)) {
				player.drop(worn, false);
			}
			popped = true;
		}
		if (popped) {
			player.displayClientMessage(Component.translatable("message.projecthero.superman_suit.popped")
					.withStyle(ChatFormatting.RED), true);
		}
	}

	/** One step of sunlight mending on every damaged Superman Suit piece worn. */
	public static void sunRepair(ServerPlayer player) {
		for (EquipmentSlot slot : ARMOR) {
			ItemStack worn = player.getItemBySlot(slot);
			if (isSuit(worn) && worn.isDamaged()) {
				worn.setDamageValue(Math.max(0, worn.getDamageValue() - SUN_REPAIR_AMOUNT));
			}
		}
	}

	// ---------------------------------------------------------------- dispensers

	/** Fits the piece onto a Kryptonian (or an armour stand) in front, with that slot free; otherwise shoots it out. */
	static final DefaultDispenseItemBehavior DISPENSE = new DefaultDispenseItemBehavior() {
		@Override
		protected ItemStack execute(BlockSource source, ItemStack stack) {
			if (!(stack.getItem() instanceof SupermanSuitItem piece)) {
				return super.execute(source, stack);
			}
			EquipmentSlot slot = piece.getEquipmentSlot();
			BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
			List<LivingEntity> targets = source.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos),
					e -> e.isAlive() && !e.isSpectator() && e.getItemBySlot(slot).isEmpty()
							&& (e instanceof Player p ? mayWear(p) : e instanceof ArmorStand stand && stand.canTakeItem(stack)));
			if (targets.isEmpty()) {
				return super.execute(source, stack);
			}
			LivingEntity target = targets.get(0);
			target.setItemSlot(slot, stack.split(1));
			return stack;
		}
	};
}

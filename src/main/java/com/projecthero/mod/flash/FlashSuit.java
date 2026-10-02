package com.projecthero.mod.flash;

import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.ArmorVisualDefinition;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flash.item.FlashRingItem;
import com.projecthero.mod.flash.item.FlashSuitItem;
import com.projecthero.mod.hero.data.ExperimentalState;

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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.11: the Flash Suit -- a craftable four-piece armour set (the user's {@code flash.bbmodel}) that only a speedster
 * (anyone with Super Speed) can wear, and its ring ({@link FlashRing}): H packs the worn suit into a gold ring on the
 * finger, H again lets it out and the speedster suits up. Diamond-strength (3/8/6/3, toughness 2); no powers of its own.
 *
 * <p>Speedsters only, enforced the same way as the Superman Suit: {@code LivingEntitySupermanSuitMixin} (armour slots,
 * shift-click, hotbar swap), {@link FlashSuitItem#use} (right-click), {@link #DISPENSE} (dispensers) and {@link #tick}
 * (anything else -- a worn piece pops off into the inventory).
 */
public final class FlashSuit {
	public static final String SET_ID = "flash";

	public static Holder<ArmorMaterial> MATERIAL;
	public static FlashSuitItem HELMET;
	public static FlashSuitItem CHESTPLATE;
	public static FlashSuitItem LEGGINGS;
	public static FlashSuitItem BOOTS;
	public static FlashRingItem RING;

	static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private FlashSuit() {
	}

	public static void initialize() {
		MATERIAL = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL,
				ResourceKey.create(Registries.ARMOR_MATERIAL, ProjectHeroMod.id("flash")),
				new ArmorMaterial(
						Map.of(ArmorItem.Type.BOOTS, 3, ArmorItem.Type.LEGGINGS, 6, ArmorItem.Type.CHESTPLATE, 8,
								ArmorItem.Type.HELMET, 3, ArmorItem.Type.BODY, 11),
						15,
						SoundEvents.ARMOR_EQUIP_LEATHER,
						() -> Ingredient.of(Items.GOLD_INGOT),
						// the flat vanilla layer is never seen (GeckoLib draws the suit)
						List.of(new ArmorMaterial.Layer(ProjectHeroMod.id("thor"))),
						2.0f,
						0.0f));
		HELMET = piece("flash_suit_helmet", ArmorItem.Type.HELMET);
		CHESTPLATE = piece("flash_suit_chestplate", ArmorItem.Type.CHESTPLATE);
		LEGGINGS = piece("flash_suit_leggings", ArmorItem.Type.LEGGINGS);
		BOOTS = piece("flash_suit_boots", ArmorItem.Type.BOOTS);
		RING = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("flash_ring"),
				new FlashRingItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()));
		SuperheroArmorVisuals.register(SET_ID, new ArmorVisualDefinition(
				ProjectHeroMod.id("geo/flash.geo.json"),
				ProjectHeroMod.id("textures/armor/flash.png"),
				SuperheroArmorVisuals.SHARED_ANIMATION));
		FlashRing.initialize();
		SpeedForce.initialize(); // v0.14.13: Super Speed's Hero-Tier origin
	}

	private static FlashSuitItem piece(String path, ArmorItem.Type type) {
		FlashSuitItem item = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(path),
				new FlashSuitItem(MATERIAL, type, new Item.Properties().rarity(Rarity.EPIC)
						.durability(type.getDurability(33))));
		DispenserBlock.registerBehavior(item, DISPENSE);
		return item;
	}

	/** Appended to the {@code projecthero:superheroes} creative tab. */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(HELMET);
		output.accept(CHESTPLATE);
		output.accept(LEGGINGS);
		output.accept(BOOTS);
	}

	// ---------------------------------------------------------------- the rule

	/** Whether {@code player} may wear the suit (and use the ring): a speedster. Client-safe (the state is synced to its owner). */
	public static boolean mayWear(Player player) {
		ExperimentalState s = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return s != null && s.ownedPowers.contains(com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.KEY);
	}

	public static boolean isSuit(ItemStack stack) {
		return stack.getItem() instanceof FlashSuitItem;
	}

	/** Whether any Flash piece is worn. */
	public static boolean wearsAny(Player player) {
		for (EquipmentSlot slot : ARMOR) {
			if (isSuit(player.getItemBySlot(slot))) {
				return true;
			}
		}
		return false;
	}

	public static void refuse(Player player) {
		player.displayClientMessage(Component.translatable("message.projecthero.flash_suit.refused")
				.withStyle(ChatFormatting.RED), true);
	}

	/** Every tick, for every player: a piece worn by someone who is not a speedster comes off; a suited speedster runs faster. */
	public static void tick(ServerPlayer player) {
		speedBonus(player);
		if (mayWear(player) || player.isSpectator()) {
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
			player.displayClientMessage(Component.translatable("message.projecthero.flash_suit.popped")
					.withStyle(ChatFormatting.RED), true);
		}
	}

	/** v0.14.11: +50% movement speed for a speedster in the full suit -- v0.14.17: only while sprinting in a mode. */
	public static final double SPEED_BONUS = 0.5;
	private static final net.minecraft.resources.ResourceLocation SPEED_ID = ProjectHeroMod.id("flash_suit_speed");

	/** Whether all four Flash pieces are worn. */
	public static boolean wearsFull(Player player) {
		for (EquipmentSlot slot : ARMOR) {
			if (!isSuit(player.getItemBySlot(slot))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * The full suit on a speedster: +50% movement speed on top of Speed Mode / Overdrive. v0.14.17: only while SPRINTING
	 * in one of them -- the suit never makes plain walking (or a mode's walk) faster, so moving around stays easy to
	 * control. Off, like Super Speed's own boosts, while Time Slow runs or the speedster is exhausted.
	 */
	private static void speedBonus(ServerPlayer player) {
		boolean on = wearsFull(player) && mayWear(player) && player.isSprinting()
				&& (com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.speedMode(player)
						|| com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.overdrive(player))
				&& !com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(player)
				&& !com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.exhausted(player);
		if (on) {
			com.projecthero.mod.hero.power.PowerToggles.modifier(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
					SPEED_ID, SPEED_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(player, net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, SPEED_ID);
		}
	}

	// ---------------------------------------------------------------- dispensers

	/** Fits the piece onto a speedster (or an armour stand) in front, with that slot free; otherwise shoots it out. */
	static final DefaultDispenseItemBehavior DISPENSE = new DefaultDispenseItemBehavior() {
		@Override
		protected ItemStack execute(BlockSource source, ItemStack stack) {
			if (!(stack.getItem() instanceof FlashSuitItem piece)) {
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
			targets.get(0).setItemSlot(slot, stack.split(1));
			return stack;
		}
	};
}

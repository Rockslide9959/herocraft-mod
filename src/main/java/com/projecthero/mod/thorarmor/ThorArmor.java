package com.projecthero.mod.thorarmor;

import com.projecthero.mod.power.ThorPassives;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;

/**
 * Thor's Armour (v0.12.32): H (while bound to Mjolnir) calls down lightning on the wearer and the armour forms
 * on them; H again dismisses it.
 *
 * <p>The armour is conjured, never crafted, so it must never become a real, storable item:
 * <ul>
 *   <li>if it <em>falls out of the inventory</em> (drop key, a full-inventory swap, a dispenser ...) the
 *       {@code ENTITY_LOAD} hook deletes the item entity the moment it appears;</li>
 *   <li>if the wearer <em>dies</em>, {@link #onDeath} strips it before vanilla drops the equipment (and, with
 *       keepInventory, before it could be carried into the next life);</li>
 *   <li>if the wearer stops being Thor (unbound / no longer worthy) the once-a-second {@link #audit} removes
 *       every piece.</li>
 * </ul>
 * Deliberately NOT Curse-of-Binding locked -- unlike the Green Lantern/Max Steel suits it may be taken off, it
 * just cannot leave the inventory.
 */
public final class ThorArmor {
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	/** Debounce so a held / double-tapped H cannot machine-gun lightning. */
	private static final int TOGGLE_COOLDOWN_TICKS = 20;

	private ThorArmor() {
	}

	public static void initialize() {
		// A dropped / ejected / death-dropped piece never becomes an item in the world.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof ThorArmorItem) {
				item.discard();
			}
		});
	}

	public static boolean isPiece(ItemStack stack) {
		return stack.getItem() instanceof ThorArmorItem;
	}

	/** True if any piece is worn or carried. */
	public static boolean hasAnyPiece(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isPiece(player.getItemBySlot(slot))) {
				return true;
			}
		}
		Inventory inv = player.getInventory();
		for (ItemStack stack : inv.items) {
			if (isPiece(stack)) {
				return true;
			}
		}
		for (ItemStack stack : inv.offhand) {
			if (isPiece(stack)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isWearing(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			if (isPiece(player.getItemBySlot(slot))) {
				return true;
			}
		}
		return false;
	}

	// ---------------- H ----------------

	/** The H key for a bound Thor: armour on (with a lightning strike) or, if already out, armour off. */
	public static void toggle(ServerPlayer player) {
		if (!ThorPassives.hasPowerOfThor(player)) {
			return;
		}
		if (player.getCooldowns().isOnCooldown(ThorArmorItems.CHESTPLATE)) {
			return;
		}
		player.getCooldowns().addCooldown(ThorArmorItems.CHESTPLATE, TOGGLE_COOLDOWN_TICKS);
		if (hasAnyPiece(player)) {
			strip(player);
			ServerLevel level = player.serverLevel();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 0.8f, 1.4f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 30,
					0.4, 0.8, 0.4, 0.2);
			player.displayClientMessage(Component.translatable("message.projecthero.thor_armor.dismissed")
					.withStyle(ChatFormatting.AQUA), true);
			return;
		}
		equip(player);
	}

	private static void equip(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true); // pure spectacle: no fire, no damage, no mob conversion
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 60,
				0.5, 1.0, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 0.9f, 1.5f);

		for (EquipmentSlot slot : SLOTS) {
			ItemStack current = player.getItemBySlot(slot);
			if (isPiece(current)) {
				continue;
			}
			if (!current.isEmpty()) {
				ItemStack displaced = current.copy();
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(displaced)) {
					player.drop(displaced, false);
				}
			}
			player.setItemSlot(slot, freshPiece(slot));
		}
		player.displayClientMessage(Component.translatable("message.projecthero.thor_armor.equipped")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	private static ItemStack freshPiece(EquipmentSlot slot) {
		ItemStack stack = new ItemStack(switch (slot) {
			case CHEST -> ThorArmorItems.CHESTPLATE;
			case LEGS -> ThorArmorItems.LEGGINGS;
			default -> ThorArmorItems.BOOTS;
		});
		stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
		return stack;
	}

	// ---------------- removal ----------------

	/** Deletes every piece the player wears or carries. Returns whether anything was removed. */
	public static boolean strip(Player player) {
		boolean removed = false;
		for (EquipmentSlot slot : SLOTS) {
			if (isPiece(player.getItemBySlot(slot))) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				removed = true;
			}
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			if (isPiece(inv.items.get(i))) {
				inv.items.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isPiece(inv.offhand.get(i))) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		if (isPiece(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed = true;
		}
		return removed;
	}

	/** Dying takes the armour with it -- called before vanilla drops the equipment. */
	public static void onDeath(ServerPlayer player) {
		strip(player);
	}

	/** Once a second: a player who is no longer Thor (and is not in Creative, testing) keeps no armour. */
	public static void audit(ServerPlayer player) {
		// (the game mode itself, not Player#isCreative(): GameTest mock players override that to always be true)
		if (player.tickCount % 20 != 0 || player.gameMode.getGameModeForPlayer().isCreative()) {
			return;
		}
		if (!ThorPassives.hasPowerOfThor(player) && hasAnyPiece(player)) {
			strip(player);
		}
	}
}

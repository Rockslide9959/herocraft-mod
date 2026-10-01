package com.projecthero.mod.flash;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flash.item.FlashRingItem;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;

/**
 * v0.14.11: the Flash Ring. A speedster wearing any Flash Suit piece presses H: the suit is pulled back into a gold
 * ring on the finger ({@link FlashFx#DOWN}, {@link #SUIT_DOWN_TICKS}, then {@link #pack}). H again: the ring snaps open,
 * the compressed suit shoots out and grows, and the speedster blurs into it ({@link FlashFx#UP}, {@link #SUIT_UP_TICKS})
 * -- the pieces go on at once (any other armour in those slots moves to the inventory) and the client reveals them
 * texel by texel outward from the ring hand behind a lightning edge, with a speed-force spin.
 *
 * <h2>State</h2>
 * <ul>
 *   <li>{@code FLASH_RING} (persistent, kept through death, synced to everyone): the worn ring as an item stack, the suit
 *       pieces in its {@link DataComponents#CONTAINER}; empty stack = no ring on the finger. Once the suit has been
 *       packed the ring stays on, empty while the suit is out.</li>
 *   <li>{@code FLASH_FX} (transient, synced to everyone): the transition clock ({@link FlashFx}).</li>
 * </ul>
 * Death without keepInventory drops a ring that holds the suit (as a {@link FlashRingItem}); losing Super Speed hands it
 * back to the inventory. Right-clicking the item puts it back on.
 *
 * <p>v0.14.14: a ring that HOLDS the suit is worn in the CHESTPLATE slot (the Flash chestplate's own slot, free once the
 * suit is packed) as a real equipped item -- so death, keepInventory, graves and every inventory mod treat it like
 * armour and the suit is never lost with a dead player's attachment. A dropped ring holding the suit never despawns.
 * The {@code FLASH_RING} attachment only carries the empty ring while the suit is out (and, as a fallback, a full ring
 * when the chest slot was taken by other armour).
 */
public final class FlashRing {
	public static final int SUIT_UP_TICKS = 30;
	public static final int SUIT_DOWN_TICKS = 16;
	/** When, into the suit-up, the suit has covered the whole body -- the crack of lightning that ends it. */
	public static final int SUIT_UP_SNAP_TICK = 24;
	/** v0.14.13: a suit packed in the worn ring mends itself -- this much durability per piece, every {@link #REPAIR_EVERY} ticks. */
	public static final int REPAIR_AMOUNT = 1;
	public static final int REPAIR_EVERY = 20;

	private FlashRing() {
	}

	/** Client -> server: H as a speedster with a Flash Suit or ring. Re-validated here. */
	public record TogglePayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TogglePayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("flash_ring_toggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC = StreamCodec.unit(new TogglePayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	static void initialize() {
		PayloadTypeRegistry.playC2S().register(TogglePayload.TYPE, TogglePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TogglePayload.TYPE, (payload, context) -> toggle(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(FlashRing::serverTick);
		// v0.14.14: a dropped ring holding the suit never despawns
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && item.getItem().is(FlashSuit.RING) && FlashRingItem.pieces(item.getItem()) > 0) {
				item.setUnlimitedLifetime();
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				onDeath(p);
			}
		});
	}

	// ---------------------------------------------------------------- state

	/** The ring on the finger (empty = none). Never mutate the returned stack. */
	public static ItemStack worn(Player player) {
		return player.getAttachedOrElse(ModAttachments.FLASH_RING, ItemStack.EMPTY);
	}

	public static FlashFx fx(Player player) {
		return player.getAttachedOrElse(ModAttachments.FLASH_FX, FlashFx.EMPTY);
	}

	/** v0.14.14: the ring holding the suit -- in the chestplate slot, else on the finger (attachment); may be empty. */
	public static ItemStack storedRing(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (chest.is(FlashSuit.RING) && FlashRingItem.pieces(chest) > 0) {
			return chest;
		}
		return worn(player);
	}

	/** Whether a ring (chestplate slot or finger) holds at least one suit piece. */
	public static boolean holdsSuit(Player player) {
		ItemStack ring = storedRing(player);
		return !ring.isEmpty() && FlashRingItem.pieces(ring) > 0;
	}

	/** v0.14.14: puts a ring holding the suit into the chestplate slot if it is free, else onto the finger. */
	private static void store(ServerPlayer player, ItemStack ring) {
		if (FlashRingItem.pieces(ring) > 0 && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			player.setItemSlot(EquipmentSlot.CHEST, ring.copyWithCount(1));
			player.removeAttached(ModAttachments.FLASH_RING);
		} else {
			setWorn(player, ring);
		}
	}

	private static void setWorn(ServerPlayer player, ItemStack ring) {
		if (ring.isEmpty()) {
			player.removeAttached(ModAttachments.FLASH_RING);
		} else {
			player.setAttached(ModAttachments.FLASH_RING, ring.copyWithCount(1));
		}
	}

	private static List<ItemStack> contents(ItemStack ring) {
		List<ItemStack> out = new ArrayList<>();
		ring.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemsCopy().forEach(out::add);
		return out;
	}

	private static ItemStack ringWith(ItemStack base, List<ItemStack> pieces) {
		ItemStack ring = base.isEmpty() ? new ItemStack(FlashSuit.RING) : base.copyWithCount(1);
		ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pieces));
		return ring;
	}

	// ---------------------------------------------------------------- H

	/** H: suit -> ring, or ring -> suit. */
	public static void toggle(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (!FlashSuit.mayWear(player)) {
			FlashSuit.refuse(player);
			return;
		}
		long now = player.level().getGameTime();
		if (fx(player).running(now)) {
			return; // one transition at a time
		}
		if (FlashSuit.wearsAny(player)) {
			startSuitDown(player, now);
			return;
		}
		if (!holdsSuit(player) && !equipFromInventory(player)) {
			player.displayClientMessage(Component.translatable(worn(player).isEmpty()
					? "message.projecthero.flash_ring.no_suit" : "message.projecthero.flash_ring.empty")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		suitUp(player, now);
	}

	/** A Flash Ring holding the suit, found in the inventory, goes onto the finger (an empty worn one is replaced). */
	private static boolean equipFromInventory(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(FlashSuit.RING) && FlashRingItem.pieces(s) > 0) {
				setWorn(player, s);
				inv.setItem(i, ItemStack.EMPTY);
				return true;
			}
		}
		return false;
	}

	/** Right-click with the ring item: put it on (a worn ring that already holds the suit is never replaced). */
	public static boolean putOn(ServerPlayer player, ItemStack ring) {
		if (holdsSuit(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.flash_ring.already")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		store(player, ring);
		sound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 1.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.flash_ring.on")
				.withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	private static void startSuitDown(ServerPlayer player, long now) {
		player.setAttached(ModAttachments.FLASH_FX, new FlashFx(now, FlashFx.DOWN));
		sound(player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 1.9f);
		sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 2.0f);
	}

	/** End of the suit-down: every worn Flash piece goes into the ring (a new ring if none is worn yet). */
	static void pack(ServerPlayer player) {
		List<ItemStack> pieces = new ArrayList<>(contents(worn(player)));
		boolean any = false;
		for (EquipmentSlot slot : FlashSuit.ARMOR) {
			ItemStack s = player.getItemBySlot(slot);
			if (FlashSuit.isSuit(s)) {
				pieces.add(s.copy());
				player.setItemSlot(slot, ItemStack.EMPTY);
				any = true;
			}
		}
		player.removeAttached(ModAttachments.FLASH_FX);
		if (!any) {
			return; // took it all off by hand mid-way
		}
		store(player, ringWith(worn(player), pieces)); // v0.14.14: into the chestplate slot the suit just left
		sound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 1.8f);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
	}

	/** The suit comes out: pieces on at once (the client reveals them), the ring stays on the finger, empty. */
	static void suitUp(ServerPlayer player, long now) {
		ItemStack ring = storedRing(player).copy();
		if (player.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.RING)) {
			player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); // v0.14.14: the ring leaves the chest slot for the finger
		}
		List<ItemStack> leftover = new ArrayList<>();
		for (ItemStack piece : contents(ring)) {
			if (!(piece.getItem() instanceof ArmorItem armor) || FlashSuit.isSuit(player.getItemBySlot(armor.getEquipmentSlot()))) {
				leftover.add(piece); // never happens through H (it packs first); keep it safe in the ring
				continue;
			}
			EquipmentSlot slot = armor.getEquipmentSlot();
			ItemStack old = player.getItemBySlot(slot);
			if (!old.isEmpty() && !player.getInventory().add(old)) {
				player.drop(old, false);
			}
			player.setItemSlot(slot, piece);
		}
		setWorn(player, ringWith(ring, leftover));
		player.setAttached(ModAttachments.FLASH_FX, new FlashFx(now, FlashFx.UP));
		sound(player, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1.0f, 2.0f);   // a thumb on the ring's catch
		sound(player, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.9f, 1.7f);   // the suit shooting out
		sound(player, SoundEvents.BEACON_POWER_SELECT, 0.7f, 2.0f);
	}

	private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	// ---------------------------------------------------------------- lifecycle

	private static void serverTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tick(player);
		}
	}

	public static void tick(ServerPlayer player) {
		FlashSuit.tick(player);
		FlashFx fx = fx(player);
		if (fx.dir() != FlashFx.NONE) {
			long age = player.level().getGameTime() - fx.start();
			if (age < 0) {
				player.removeAttached(ModAttachments.FLASH_FX); // a clock from another dimension's time
			} else if (fx.dir() == FlashFx.DOWN && age >= SUIT_DOWN_TICKS) {
				pack(player);
			} else if (fx.dir() == FlashFx.UP) {
				if (age == SUIT_UP_SNAP_TICK) {
					sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.45f, 1.9f);
				}
				if (age >= SUIT_UP_TICKS) {
					player.removeAttached(ModAttachments.FLASH_FX);
				}
			}
		}
		ItemStack ring = worn(player);
		if (!ring.isEmpty() && !FlashSuit.mayWear(player)) {
			// lost Super Speed: the ring comes off (one holding the suit goes back to the inventory)
			player.removeAttached(ModAttachments.FLASH_RING);
			if (FlashRingItem.pieces(ring) > 0 && !player.getInventory().add(ring.copy())) {
				player.drop(ring.copy(), false);
			}
		} else if (holdsSuit(player)) {
			// v0.14.14: a full ring left on the finger by an older version moves into the free chestplate slot
			if (!FlashRing.worn(player).isEmpty() && FlashRingItem.pieces(worn(player)) > 0
					&& player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && fx.dir() == FlashFx.NONE) {
				store(player, worn(player));
			}
			if (player.tickCount % REPAIR_EVERY == 0) {
				repairStored(player);
			}
		}
	}

	/** v0.14.13: one mending step for every damaged suit piece stored in the worn ring (written back only on a change). */
	public static void repairStored(ServerPlayer player) {
		boolean inChest = player.getItemBySlot(EquipmentSlot.CHEST).is(FlashSuit.RING)
				&& FlashRingItem.pieces(player.getItemBySlot(EquipmentSlot.CHEST)) > 0;
		ItemStack ring = storedRing(player);
		List<ItemStack> pieces = contents(ring);
		boolean changed = false;
		for (ItemStack piece : pieces) {
			if (piece.isDamageableItem() && piece.isDamaged()) {
				piece.setDamageValue(Math.max(0, piece.getDamageValue() - REPAIR_AMOUNT));
				changed = true;
			}
		}
		if (changed) {
			if (inChest) {
				// v0.14.15: mend the worn ring IN PLACE -- setItemSlot counts as re-equipping it and played the equip sound
				// every second
				player.getItemBySlot(EquipmentSlot.CHEST).set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pieces));
			} else {
				setWorn(player, ringWith(ring, pieces));
			}
		}
	}

	private static void onDeath(ServerPlayer player) {
		player.removeAttached(ModAttachments.FLASH_FX);
		ItemStack ring = worn(player);
		if (ring.isEmpty() || FlashRingItem.pieces(ring) == 0
				|| player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
			return; // an empty ring (or keepInventory) stays on through death
		}
		player.removeAttached(ModAttachments.FLASH_RING);
		ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), ring.copy());
		drop.setDefaultPickUpDelay();
		player.level().addFreshEntity(drop);
	}
}

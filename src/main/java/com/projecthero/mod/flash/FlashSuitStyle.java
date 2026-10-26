package com.projecthero.mod.flash;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flash.item.FlashRingItem;
import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * v0.17.1 (user: "add all these flash suits please"): the looks a Flash Suit can wear. Every style is a 64x64 player skin
 * on the same {@code geo/flash.geo.json} rig as the original (made by {@code scratchpad/flash_suits_v0171/gen_flash_styles.js}
 * from the user's skins), so the suit-up reveal, the ring and everything else work unchanged -- only the texture differs.
 *
 * <p>The style is kept on each piece ({@link ModDataComponents#FLASH_SUIT_STYLE}), so it travels with the armour and
 * through the Flash Ring (which carries the pieces). Picked by Shift + right-click on the Flash Ring ({@link #cycle}) or on
 * the Speed Force Speedster's Shift+N menu ({@link #select}, through {@code SpeedsterPrefsPayload}); both restyle every
 * piece of the suit at once.
 *
 * <p>Append only: the menu sends ordinals.
 */
public enum FlashSuitStyle {
	ORIGINAL("original", "textures/armor/flash.png"),
	/** Zoom (CW's The Flash, season 2) -- the user asked for grey eyes instead of blue. */
	ZOOM("zoom", "textures/armor/flash_zoom.png"),
	/** Reverse-Flash (Eobard Thawne): the yellow suit, red lenses. */
	REVERSE_FLASH("reverse_flash", "textures/armor/flash_reverse_flash.png"),
	/** The classic comic-book Flash: winged cowl, yellow belt and boots. */
	CLASSIC("classic", "textures/armor/flash_classic.png"),
	/** A crimson suit with pale silver-blue lightning trim. */
	SCARLET("scarlet", "textures/armor/flash_scarlet.png");

	private final String id;
	private final ResourceLocation texture;

	FlashSuitStyle(String id, String texture) {
		this.id = id;
		this.texture = ProjectHeroMod.id(texture);
	}

	public String id() {
		return id;
	}

	public ResourceLocation texture() {
		return texture;
	}

	/** {@code flash_suit_style.projecthero.<id>} -- the style's display name. */
	public String nameKey() {
		return "flash_suit_style.projecthero." + id;
	}

	public Component displayName() {
		return Component.translatable(nameKey());
	}

	public FlashSuitStyle next() {
		FlashSuitStyle[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static FlashSuitStyle byId(String id) {
		for (FlashSuitStyle s : values()) {
			if (s.id.equals(id)) {
				return s;
			}
		}
		return ORIGINAL;
	}

	/** Out-of-range ordinals (an old client or a tampered packet) fall back to the original suit. */
	public static FlashSuitStyle byOrdinal(int ordinal) {
		FlashSuitStyle[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ORIGINAL;
	}

	// ---------------------------------------------------------------- on the items

	/** The style of a Flash Suit piece (anything without one is the original). */
	public static FlashSuitStyle of(ItemStack stack) {
		String id = stack.get(ModDataComponents.FLASH_SUIT_STYLE);
		return id == null ? ORIGINAL : byId(id);
	}

	/** Stamps {@code style} on a piece; the original suit is stored as "no component", so crafted and restyled match. */
	public static void apply(ItemStack piece, FlashSuitStyle style) {
		if (style == ORIGINAL) {
			piece.remove(ModDataComponents.FLASH_SUIT_STYLE);
		} else {
			piece.set(ModDataComponents.FLASH_SUIT_STYLE, style.id);
		}
	}

	/** The style of the suit packed in {@code ring} (its first piece), or null if it holds none. */
	public static FlashSuitStyle ofRing(ItemStack ring) {
		if (ring.isEmpty() || FlashRingItem.pieces(ring) == 0) {
			return null;
		}
		ItemStack first = ring.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems().iterator().next();
		return of(first);
	}

	/** Restyles every piece packed in {@code ring}, in place. */
	public static void applyToRing(ItemStack ring, FlashSuitStyle style) {
		List<ItemStack> pieces = new ArrayList<>();
		ring.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemsCopy().forEach(pieces::add);
		for (ItemStack piece : pieces) {
			if (FlashSuit.isSuit(piece)) {
				apply(piece, style);
			}
		}
		ring.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(pieces));
	}

	// ---------------------------------------------------------------- on a player

	/**
	 * The style of this player's suit: the worn chestplate (else any worn piece), else the suit in the ring they wear, else
	 * the suit in a Flash Ring in either hand. Null when they have no suit at all. Client-safe (worn armour, the chestplate
	 * slot ring and the FLASH_RING attachment are all synced).
	 */
	public static FlashSuitStyle current(Player player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (FlashSuit.isSuit(chest)) {
			return of(chest);
		}
		for (EquipmentSlot slot : FlashSuit.ARMOR) {
			if (FlashSuit.isSuit(player.getItemBySlot(slot))) {
				return of(player.getItemBySlot(slot));
			}
		}
		FlashSuitStyle stored = ofRing(FlashRing.storedRing(player));
		if (stored != null) {
			return stored;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = player.getItemInHand(hand);
			if (held.is(FlashSuit.RING)) {
				FlashSuitStyle h = ofRing(held);
				if (h != null) {
					return h;
				}
			}
		}
		return null;
	}

	/**
	 * Restyles the player's suit: the worn pieces if any are on, else the suit in the ring they wear, else the one in
	 * {@code held} (a Flash Ring in hand; may be empty). False -- and the player is told -- when there is no suit to restyle.
	 */
	public static boolean select(ServerPlayer player, FlashSuitStyle style, ItemStack held) {
		boolean done = false;
		if (FlashSuit.wearsAny(player)) {
			for (EquipmentSlot slot : FlashSuit.ARMOR) {
				ItemStack worn = player.getItemBySlot(slot);
				if (FlashSuit.isSuit(worn)) {
					apply(worn, style); // in place: the equipment change is noticed and sent to everyone
				}
			}
			done = true;
		} else if (FlashRing.holdsSuit(player)) {
			ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.is(FlashSuit.RING) && FlashRingItem.pieces(chest) > 0) {
				applyToRing(chest, style);
			} else {
				ItemStack ring = FlashRing.worn(player).copy();
				applyToRing(ring, style);
				player.setAttached(ModAttachments.FLASH_RING, ring);
			}
			done = true;
		} else if (held != null && held.is(FlashSuit.RING) && FlashRingItem.pieces(held) > 0) {
			applyToRing(held, style);
			done = true;
		}
		if (!done) {
			player.displayClientMessage(Component.translatable("message.projecthero.flash_suit.style.no_suit")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.flash_suit.style", style.displayName())
				.withStyle(ChatFormatting.GOLD), true);
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ARMOR_EQUIP_GOLD.value(),
				SoundSource.PLAYERS, 0.8f, 1.6f + 0.08f * style.ordinal());
		return true;
	}

	/** Shift + right-click with a Flash Ring: the suit (worn, in the worn ring, or in this ring) moves to the next style. */
	public static boolean cycle(ServerPlayer player, ItemStack held) {
		FlashSuitStyle now = currentFor(player, held);
		if (now == null) {
			return select(player, ORIGINAL, held); // tells them there is no suit
		}
		return select(player, now.next(), held);
	}

	/** {@link #current}, preferring the same target order as {@link #select} (worn, worn ring, then {@code held}). */
	private static FlashSuitStyle currentFor(Player player, ItemStack held) {
		if (FlashSuit.wearsAny(player) || FlashRing.holdsSuit(player)) {
			return current(player);
		}
		return held == null ? null : ofRing(held);
	}
}

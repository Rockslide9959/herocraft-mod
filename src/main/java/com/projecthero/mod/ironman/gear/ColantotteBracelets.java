package com.projecthero.mod.ironman.gear;

import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * v0.15.4: the <b>Colantotte Bracelets</b> -- the Mark 7's homing bracelets. Handed out by the Suit Platform's
 * <i>Bracelets</i> button while a Mark 7 is stored on it ({@link #claim}); worn in the Stark Gear slot (Shift + N, the same
 * slot as the Stark Glasses -- one or the other). Wearing them:
 * <ul>
 *   <li>suits can be called, exactly as with the glasses ({@link StarkGear#canCall});</li>
 *   <li>a called Mark 7 reaches you twice as fast (the delivery pod flies at double speed, a far platform's travel wait is
 *       halved, see {@code IronManSuitCall} / {@code IronManDeliveryPodEntity});</li>
 *   <li>the Mark 7 goes on with the quick 4 s bracelet wrap-on ({@code SuitUpType.BRACELET_QUICK}).</li>
 * </ul>
 *
 * <h2>No spare copies</h2>
 * The button refuses while you already have a working pair (pack, Stark Gear slot, held on the cursor). Every pair is
 * bound to the player it was issued to, with an issue number ({@link ModAttachments#COLANTOTTE_SERIAL}); claiming a new
 * pair (say the old one was lost, or left in a chest) retires every older one -- an older pair crumbles the moment it is
 * in a player's inventory or Stark Gear slot while its owner is online. So there is only ever one working pair per player.
 */
public final class ColantotteBracelets {
	private static final String OWNER = "ColantotteOwner";
	private static final String SERIAL = "ColantotteSerial";

	private ColantotteBracelets() {
	}

	/** Result of pressing the platform's Bracelets button. */
	public enum Claim {
		GIVEN, NO_MARK_7, ALREADY_HAVE, NOT_TONY
	}

	/** Is the Bracelets button offered for this platform (a Mark 7 is stored on it)? */
	public static boolean offered(IronManSuitPlatformBlockEntity be) {
		return be != null && IronManSuitUpManager.BRACELET_SUIT.equals(be.storedSuitId());
	}

	/**
	 * The platform's Bracelets button: hand {@code player} one pair, bound to them -- only while a Mark 7 is stored on
	 * {@code be} and only if they do not already have a working pair. Tells the player either way.
	 */
	public static Claim claim(ServerPlayer player, IronManSuitPlatformBlockEntity be) {
		if (!TonyStark.hasPower(player)) {
			return Claim.NOT_TONY;
		}
		if (!offered(be)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_need_mk7")
					.withStyle(ChatFormatting.RED), true);
			return Claim.NO_MARK_7;
		}
		if (hasPair(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_already")
					.withStyle(ChatFormatting.GOLD), true);
			return Claim.ALREADY_HAVE;
		}
		int serial = player.getAttachedOrElse(ModAttachments.COLANTOTTE_SERIAL, 0) + 1;
		player.setAttached(ModAttachments.COLANTOTTE_SERIAL, serial);
		ItemStack pair = bind(new ItemStack(IronManItems.COLANTOTTE_BRACELETS), player.getUUID(), serial);
		if (!player.getInventory().add(pair)) {
			player.drop(pair, false);
		}
		IronManSounds.play(player, IronManSounds.CLAMP, 0.8f, 1.3f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_given")
				.withStyle(ChatFormatting.AQUA), true);
		return Claim.GIVEN;
	}

	/** Stamp {@code stack} as pair number {@code serial} of {@code owner}. Returns the stack. */
	public static ItemStack bind(ItemStack stack, UUID owner, int serial) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putUUID(OWNER, owner);
			tag.putInt(SERIAL, serial);
		});
		return stack;
	}

	/** The player a pair was issued to, or null (a creative-menu pair is unbound and never retires). */
	public static UUID owner(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return tag.hasUUID(OWNER) ? tag.getUUID(OWNER) : null;
	}

	public static int serial(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(SERIAL);
	}

	/** Has this pair been replaced by a newer one issued to its (online) owner? */
	public static boolean retired(ItemStack stack, MinecraftServer server) {
		if (!(stack.getItem() instanceof ColantotteBraceletsItem) || server == null) {
			return false;
		}
		UUID owner = owner(stack);
		if (owner == null) {
			return false;
		}
		Player p = server.getPlayerList().getPlayer(owner);
		if (p == null) {
			for (var level : server.getAllLevels()) {
				p = level.getPlayerByUUID(owner);
				if (p != null) {
					break;
				}
			}
		}
		return p != null && serial(stack) < p.getAttachedOrElse(ModAttachments.COLANTOTTE_SERIAL, 0);
	}

	/** Does {@code player} already have a working pair -- worn, in the inventory or held on the cursor? */
	public static boolean hasPair(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (working(StarkGear.glasses(player), server)) {
			return true;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (working(inv.getItem(i), server)) {
				return true;
			}
		}
		return player.containerMenu != null && working(player.containerMenu.getCarried(), server);
	}

	private static boolean working(ItemStack stack, MinecraftServer server) {
		return stack.getItem() instanceof ColantotteBraceletsItem && !retired(stack, server);
	}

	/** Count every pair a player carries (worn + inventory), for the tests. */
	public static int count(Player player) {
		int n = StarkGear.glasses(player).getItem() instanceof ColantotteBraceletsItem ? 1 : 0;
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() instanceof ColantotteBraceletsItem) {
				n += inv.getItem(i).getCount();
			}
		}
		return n;
	}

	/** Tell a holder their retired pair crumbled. */
	static void crumbled(Player player) {
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_retired")
				.withStyle(ChatFormatting.GRAY), true);
	}
}

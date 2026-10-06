package com.projecthero.mod.hulk.gladiator;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.3: <b>Gladiator Hulk</b> -- the Sakaar arena kit. Seven craftable pieces ({@link GladiatorItems}) go in seven
 * Gladiator Gear slots, the {@link ModAttachments#GLADIATOR_GEAR} attachment (persistent, synced to everyone, kept
 * through death). Tap N as Banner to open the Gladiator Gear screen ({@link GladiatorGearMenu}); the gear is locked on
 * while you are the Hulk (the screen refuses to open and closes if you change with it open).
 *
 * <p>With all seven in, the Hulk comes out as Gladiator Hulk ({@link #isGladiator}): helmet, war paint, pauldron, harness,
 * bracers, war kilt, the hammer in his right hand and the axe in his left (drawn by the client's Hulk renderer), and he
 * takes {@link #DAMAGE_FACTOR 10% less damage} ({@code HulkDamage}). The gladiator moves throw the weapons -- while one is
 * out of his hand {@link #weaponAway} is set and the hand bone hides. Both come back to his hands when he changes back,
 * dies or logs in.
 */
public final class GladiatorGear {
	public static final int HELMET = 0;
	public static final int PAULDRON = 1;
	public static final int HARNESS = 2;
	public static final int BRACERS = 3;
	public static final int KILT = 4;
	public static final int HAMMER = 5;
	public static final int AXE = 6;
	public static final int SLOTS = 7;
	/** Short names, also the lang suffix of each slot's label ({@code screen.projecthero.gladiator_gear.slot.<name>}). */
	public static final String[] SLOT_NAMES = { "helmet", "pauldron", "harness", "bracers", "kilt", "hammer", "axe" };
	/** Gladiator Hulk takes this fraction of the damage that gets past the Hulk's own rules. */
	public static final float DAMAGE_FACTOR = 0.9f;

	private static final int AWAY_HAMMER = 1;
	private static final int AWAY_AXE = 2;

	public static final MenuType<GladiatorGearMenu> MENU = Registry.register(BuiltInRegistries.MENU,
			ProjectHeroMod.id("gladiator_gear"), new MenuType<>(GladiatorGearMenu::new, FeatureFlags.VANILLA_SET));

	private GladiatorGear() {
	}

	/** Client -> server: a tap of N as Banner -- open the Gladiator Gear screen. Re-validated here. */
	public record OpenPayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("gladiator_gear_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.unit(new OpenPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(OpenPayload.TYPE, OpenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, context) -> openMenu(context.player()));
		// the weapons come back to his hands: on death, on login, and the tick he is no longer the Hulk
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				clearWeapons(p);
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> clearWeapons(handler.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				tick(p);
			}
		});
	}

	// ---------------------------------------------------------------- the kit

	/** The seven slots (always {@link #SLOTS} long; empty stacks for empty slots). Never mutate the result. */
	public static List<ItemStack> kit(Player player) {
		List<ItemStack> stored = player.getAttachedOrElse(ModAttachments.GLADIATOR_GEAR, List.of());
		if (stored.size() == SLOTS) {
			return stored;
		}
		List<ItemStack> out = new ArrayList<>(SLOTS);
		for (int i = 0; i < SLOTS; i++) {
			out.add(i < stored.size() ? stored.get(i) : ItemStack.EMPTY);
		}
		return out;
	}

	/** What is in one slot (empty if nothing). Never mutate the result. */
	public static ItemStack get(Player player, int slot) {
		return slot < 0 || slot >= SLOTS ? ItemStack.EMPTY : kit(player).get(slot);
	}

	/** Whether {@code stack} belongs in {@code slot} (each slot takes only its own piece). */
	public static boolean accepts(int slot, ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof GladiatorGearItem piece && piece.slot() == slot;
	}

	/**
	 * Put {@code stack} (one of it; empty clears) in {@code slot}. Server side -- syncs to every client. A stack that does
	 * not belong in the slot is ignored. No Hulk check here: the menu does that (this is also the test / command hook).
	 */
	public static void set(Player player, int slot, ItemStack stack) {
		if (slot < 0 || slot >= SLOTS || (!stack.isEmpty() && !accepts(slot, stack))) {
			return;
		}
		List<ItemStack> next = new ArrayList<>(kit(player));
		next.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
		boolean any = false;
		for (ItemStack s : next) {
			any |= !s.isEmpty();
		}
		if (any) {
			player.setAttached(ModAttachments.GLADIATOR_GEAR, List.copyOf(next));
		} else {
			player.removeAttached(ModAttachments.GLADIATOR_GEAR);
		}
	}

	/** How many of the seven slots hold their piece. Client-safe (synced attachment). */
	public static int equippedCount(Player p) {
		int n = 0;
		List<ItemStack> kit = kit(p);
		for (int i = 0; i < SLOTS; i++) {
			if (accepts(i, kit.get(i))) {
				n++;
			}
		}
		return n;
	}

	/** All seven slots filled. Client-safe. */
	public static boolean hasFullKit(Player p) {
		return equippedCount(p) == SLOTS;
	}

	/** Gladiator Hulk right now: the full kit AND out as the Hulk. Client-safe. */
	public static boolean isGladiator(Player p) {
		return Hulk.isHulk(p) && hasFullKit(p);
	}

	// ---------------------------------------------------------------- thrown weapons

	/** True while the hammer ({@code axe = false}) or the axe is out of his hand (thrown). Client-safe. */
	public static boolean weaponAway(Player p, boolean axe) {
		int bits = p.getAttachedOrElse(ModAttachments.GLADIATOR_WEAPONS_AWAY, 0);
		return (bits & (axe ? AWAY_AXE : AWAY_HAMMER)) != 0;
	}

	/** Mark a weapon as thrown (or back in hand). Syncs to everyone. */
	public static void setWeaponAway(ServerPlayer p, boolean axe, boolean away) {
		int bits = p.getAttachedOrElse(ModAttachments.GLADIATOR_WEAPONS_AWAY, 0);
		int bit = axe ? AWAY_AXE : AWAY_HAMMER;
		int next = away ? bits | bit : bits & ~bit;
		if (next == bits) {
			return;
		}
		if (next == 0) {
			p.removeAttached(ModAttachments.GLADIATOR_WEAPONS_AWAY);
		} else {
			p.setAttached(ModAttachments.GLADIATOR_WEAPONS_AWAY, next);
		}
	}

	/** Both weapons back in his hands. */
	public static void clearWeapons(ServerPlayer p) {
		if (p.hasAttached(ModAttachments.GLADIATOR_WEAPONS_AWAY)) {
			p.removeAttached(ModAttachments.GLADIATOR_WEAPONS_AWAY);
		}
	}

	/** Per server tick: a weapon cannot stay "thrown" once he is no longer the Hulk. */
	public static void tick(ServerPlayer p) {
		if (p.hasAttached(ModAttachments.GLADIATOR_WEAPONS_AWAY) && !Hulk.isHulk(p)) {
			clearWeapons(p);
		}
	}

	// ---------------------------------------------------------------- damage

	/** Damage after Gladiator Hulk's 10% cut ({@code amount} unchanged for anyone else). */
	public static float reduce(Player p, float amount) {
		return isGladiator(p) ? amount * DAMAGE_FACTOR : amount;
	}

	// ---------------------------------------------------------------- the screen

	/**
	 * Why this player may not open the Gladiator Gear screen right now (a lang key), or null if he may: only a Gamma
	 * player, and only as Banner -- the gear is locked on while he is the Hulk.
	 */
	public static String refusal(Player player) {
		if (!Hulk.hasPower(player)) {
			return "message.projecthero.hulk.gladiator.no_power";
		}
		if (Hulk.isHulk(player)) {
			return "message.projecthero.hulk.gladiator.locked";
		}
		return null;
	}

	/** Open the Gladiator Gear screen if allowed (else tell him why). Returns whether it opened. */
	public static boolean openMenu(ServerPlayer player) {
		String refusal = refusal(player);
		if (refusal != null) {
			player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED), true);
			return false;
		}
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GladiatorGearMenu(id, inv),
				Component.translatable("screen.projecthero.gladiator_gear.title")));
		return true;
	}
}

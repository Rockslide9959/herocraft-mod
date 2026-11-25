package com.projecthero.mod.thorarmor;

import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorPassives;
import com.projecthero.mod.power.ThorVisuals;

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

	// ---------------- v0.14.4: the piece-by-piece suit-up; v0.17.7: under a lightning storm ----------------
	// Every piece is worn from the first tick (so the protection is instant and nothing can be interrupted half-way); what
	// arrives piece by piece is the look. v0.17.7 (user: "make lightning call down on you", "no i still want the suit and armour
	// i was just saying remove thors actual skin form the suit and keep the helmet"): on H, visual-only lightning bolts call down
	// round the wearer again and again while the suit locks on one piece at a time -- boots, greaves, the chestplate and arm
	// plates, the winged helmet -- each piece appearing whole as a bolt lands and settling onto the body (client ThorSuitReveal,
	// SuperheroArmorRenderer), with a clink as it locks; then the cape unrolls down from the collar (ThorCapeLayer) and a last
	// big bolt and a flash finish it. Suit-down plays it backwards, cape first, and only then removes the pieces.

	/** The visual parts of the suit-up, in build order. */
	public static final int PART_BOOTS = 0;
	public static final int PART_GREAVES = 1;
	/** The chestplate: the tunic and the arm plates / bracers. */
	public static final int PART_CHEST = 2;
	/** The winged helmet (drawn with the chestplate, there is no helmet item). */
	public static final int PART_HELMET = 3;
	public static final int PART_CAPE = 4;
	/** How long the whole suit-up takes. */
	public static final int SUIT_UP_TICKS = 60;
	/** How long the reverse takes before the pieces are actually removed. */
	public static final int SUIT_DOWN_TICKS = 26;
	/** Per part (BOOTS, GREAVES, CHEST, HELMET, CAPE): when its build starts and ends, in ticks from the suit-up's start. */
	private static final int[][] UP_WINDOWS = { { 6, 12 }, { 14, 20 }, { 22, 30 }, { 32, 38 }, { 38, 52 } };
	/** Per part: when it comes off, in ticks from the suit-down's start -- the build backwards, the cape first. */
	private static final int[][] DOWN_WINDOWS = { { 21, 25 }, { 17, 21 }, { 12, 17 }, { 8, 12 }, { 0, 8 } };
	/** The visual-only lightning bolts called down round the wearer during the build (ticks from the start). */
	private static final int[] BOLT_TICKS = { 6, 10, 14, 18, 22, 26, 32, 36, 42, 48 };
	/** The last, big bolt straight onto the wearer once everything is on. */
	public static final int FINAL_BOLT_TICK = 54;
	/** The heights (blocks above the feet) each part locks on at, for its clink. */
	private static final double[] PART_HEIGHT = { 0.15, 0.7, 1.25, 1.75, 1.3 };

	/** When {@code part} starts building, in ticks after the suit-up began. */
	public static int partStart(int part) {
		return UP_WINDOWS[part][0];
	}

	/** When {@code part} has finished building (locks on), in ticks after the suit-up began. */
	public static int partEnd(int part) {
		return UP_WINDOWS[part][1];
	}

	/** When {@code part} starts coming off after H, in ticks after the suit-down began. */
	public static int partScatterStart(int part) {
		return DOWN_WINDOWS[part][0];
	}

	/** How long {@code part} takes to come off. */
	public static int partScatterTicks(int part) {
		return DOWN_WINDOWS[part][1] - DOWN_WINDOWS[part][0];
	}

	/**
	 * How much of {@code part} is showing (0..1), {@code elapsed} ticks into the suit clock {@code dir} ({@code ThorFx.SUIT_*}).
	 * Outside a transition it is fully shown. Shared by the client and the gametests, so both agree on the timeline.
	 */
	public static float partProgress(int dir, int part, float elapsed) {
		if (dir == com.projecthero.mod.power.ThorFx.SUIT_UP) {
			int[] w = UP_WINDOWS[part];
			return clamp((elapsed - w[0]) / (float) (w[1] - w[0]));
		}
		if (dir == com.projecthero.mod.power.ThorFx.SUIT_DOWN) {
			int[] w = DOWN_WINDOWS[part];
			return 1.0f - clamp((elapsed - w[0]) / (float) (w[1] - w[0]));
		}
		return 1.0f;
	}

	/** The part a worn piece's look belongs to (the chestplate's own look, without its helmet and cape). */
	private static int partOf(EquipmentSlot slot) {
		return switch (slot) {
			case FEET -> PART_BOOTS;
			case LEGS -> PART_GREAVES;
			case HEAD -> PART_HELMET;
			default -> PART_CHEST;
		};
	}

	/** When {@code slot}'s part starts forming, in ticks after the suit-up began. */
	public static int arrivalTick(EquipmentSlot slot) {
		return partStart(partOf(slot));
	}

	/** {@link #partProgress} for the part {@code slot}'s piece belongs to (kept for older callers). */
	public static float pieceProgress(int dir, EquipmentSlot slot, float elapsed) {
		return partProgress(dir, partOf(slot), elapsed);
	}

	private static float clamp(float v) {
		return Math.max(0.0f, Math.min(1.0f, v));
	}

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
		if (hasAnyPiece(player)) {
			if (isDissolving(player)) {
				return;
			}
			player.getCooldowns().addCooldown(ThorArmorItems.CHESTPLATE, TOGGLE_COOLDOWN_TICKS);
			// v0.14.4: the suit crackles away chest-first (client ThorSuitReveal); tick() removes it when that is done
			ThorVisuals.suit(player, ThorFx.SUIT_DOWN);
			ServerLevel level = player.serverLevel();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 0.8f, 1.4f);
			// v0.17.7: a crackle as it comes apart
			level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
					SoundSource.PLAYERS, 0.6f, 1.9f);
			level.playSound(null, player.getX(), player.getY() + 1.4, player.getZ(), SoundEvents.FIRE_EXTINGUISH,
					SoundSource.PLAYERS, 0.5f, 1.6f);
			player.displayClientMessage(Component.translatable("message.projecthero.thor_armor.dismissed")
					.withStyle(ChatFormatting.AQUA), true);
			return;
		}
		// the whole suit-up plays out before H can dismiss it again
		player.getCooldowns().addCooldown(ThorArmorItems.CHESTPLATE, SUIT_UP_TICKS + 4);
		equip(player);
	}

	private static void equip(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		// the suit clock goes out before the equipment does, so no viewer ever sees the whole set pop in for a frame
		ThorVisuals.suit(player, ThorFx.SUIT_UP);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
				SoundSource.PLAYERS, 0.9f, 0.8f);
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true); // pure spectacle: no fire, no damage, no mob conversion
			level.addFreshEntity(bolt);
		}
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
		ThorVisuals.suit(player, ThorFx.SUIT_NONE);
	}

	/** Once a second: a player who is no longer Thor (and is not in Creative, testing) keeps no armour. */
	public static void audit(ServerPlayer player) {
		tick(player);
		// (the game mode itself, not Player#isCreative(): GameTest mock players override that to always be true)
		if (player.tickCount % 20 != 0 || player.gameMode.getGameModeForPlayer().isCreative()) {
			return;
		}
		if (!ThorPassives.hasPowerOfThor(player) && hasAnyPiece(player)) {
			strip(player);
			ThorVisuals.suit(player, ThorFx.SUIT_NONE);
		}
	}

	/**
	 * v0.14.4, every tick: drives the suit clock -- each later piece's lightning flash as it arrives, the end of the
	 * suit-up, and removing the pieces once a suit-down's dissolve has played out.
	 */
	private static void tick(ServerPlayer player) {
		ThorFx fx = ThorVisuals.fx(player);
		if (fx.suitDir() == ThorFx.SUIT_NONE) {
			return;
		}
		long elapsed = player.level().getGameTime() - fx.suitStart();
		if (fx.suitDir() == ThorFx.SUIT_UP) {
			for (int b : BOLT_TICKS) {
				if (elapsed == b) {
					boltNear(player, 0.7 + player.getRandom().nextDouble() * 1.1);
				}
			}
			// a rising crackle under the build
			if (elapsed >= 6 && elapsed < FINAL_BOLT_TICK && elapsed % 3 == 0) {
				float rise = (elapsed - 6) / (float) (FINAL_BOLT_TICK - 6);
				player.serverLevel().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
						SoundSource.PLAYERS, 0.18f + 0.25f * rise, 1.4f + 0.6f * rise);
			}
			// each piece locks on with a clink (the cape too, once it has unrolled)
			for (int part = PART_BOOTS; part <= PART_CAPE; part++) {
				if (elapsed == partEnd(part)) {
					pieceArrives(player, PART_HEIGHT[part]);
				}
			}
			if (elapsed == FINAL_BOLT_TICK) {
				finalBolt(player);
			}
			if (elapsed >= SUIT_UP_TICKS || elapsed < 0) {
				ThorVisuals.suit(player, ThorFx.SUIT_NONE);
			}
			return;
		}
		// v0.17.7: coming off, piece by piece -- a crackle and an unclasping clink as each one lets go
		for (int part = PART_BOOTS; part < PART_CAPE; part++) {
			if (elapsed == partScatterStart(part)) {
				pieceLeaves(player, PART_HEIGHT[part]);
			}
		}
		if (elapsed >= SUIT_DOWN_TICKS || elapsed < 0) {
			strip(player);
		}
		// The clock is only cleared a second later: cleared at once, the "no transition = fully shown" state could reach
		// a viewer a frame before the removed equipment does and flash the whole suit back on.
		if (elapsed >= SUIT_DOWN_TICKS + 20 || elapsed < 0) {
			ThorVisuals.suit(player, ThorFx.SUIT_NONE);
		}
	}

	/** True while the suit is dissolving after H (it is removed when that finishes). */
	public static boolean isDissolving(Player player) {
		ThorFx fx = ThorVisuals.fx(player);
		return fx.suitDir() == ThorFx.SUIT_DOWN && player.level().getGameTime() - fx.suitStart() < SUIT_DOWN_TICKS;
	}

	/** v0.17.7: a piece comes off: a crackle and a higher, unclasping clink at its height. */
	private static void pieceLeaves(ServerPlayer player, double height) {
		ServerLevel level = player.serverLevel();
		double y = player.getY() + height;
		level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 1.5f);
		level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.3f, 1.9f);
	}

	/** v0.17.7: a piece locks on (boots, greaves, chestplate, helmet; the cape at the shoulders): a metallic clink at its height. */
	private static void pieceArrives(ServerPlayer player, double height) {
		ServerLevel level = player.serverLevel();
		double y = player.getY() + height;
		level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f, 1.9f);
		level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.9f,
				0.9f + (float) height * 0.1f);
	}

	/** v0.17.7: one of the build's bolts, {@code radius} blocks from the wearer -- visual only (no fire, no damage). */
	private static void boltNear(ServerPlayer player, double radius) {
		ServerLevel level = player.serverLevel();
		double a = player.getRandom().nextDouble() * Math.PI * 2.0;
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(player.getX() + Math.cos(a) * radius, player.getY(), player.getZ() + Math.sin(a) * radius);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	/** v0.17.7: the finish -- a big bolt straight onto the wearer, a flash and a layered thunderclap. */
	private static void finalBolt(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(player.getX(), player.getY(), player.getZ());
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.1, 0.2, 0.1, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
				1.2f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.0f,
				1.0f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f,
				1.6f);
	}
}

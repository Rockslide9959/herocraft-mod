package com.projecthero.mod.ironman.ability.era1;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManSuitTicker;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.crate.SuitCrateFoldEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) ERA I: the <b>emergency eject</b>. Hold Shift+C for {@link #HOLD_TICKS} (3 s; a bar fills on the HUD --
 * {@code IronManHud}) in a worn Era I suit and the wearer SHOOTS OUT OF THE BACK of the armour ({@link #launch}: backwards
 * and a little up, fall-safe until they land), while the empty suit stands there on its own for a second and then folds
 * itself into a Suit Crate block ({@link SuitCrateFoldEntity} -> {@code ironman.crate.SuitCrateBlock}) holding the real
 * armour stacks, stamped with the suit's integrity and energy. It works on a wrecked (0 integrity), browned-out or
 * locked-out suit -- that is what it is for.
 *
 * <p>Server-authoritative: the Shift+C press stamps the start, the release (or letting go of Shift) cancels, and
 * {@link #tick} ejects once the hold has lasted. The press and launch maps are static per-world state -- cleared in
 * {@code ServerStateReset} and on disconnect.
 */
public final class EraOneEject {
	/** v0.16.7 (user): hold Shift+C this long. */
	public static final int HOLD_TICKS = 60;
	/** The launch out of the back: horizontal speed (backwards) and lift. */
	public static final double LAUNCH_BACK = 0.85;
	public static final double LAUNCH_UP = 0.55;
	/** The launch stays fall-safe at most this long (it ends on landing). */
	public static final int LAUNCH_SAFE_TICKS = 200;

	private static final Map<UUID, Long> HOLD_START = new ConcurrentHashMap<>();
	/** Players shot out of a suit, by launch tick: no fall damage until they land. */
	private static final Map<UUID, Long> LAUNCHED = new ConcurrentHashMap<>();
	private static boolean hooked;

	private EraOneEject() {
	}

	/** The fall-safety hook (no fall damage while {@link #launched}). Idempotent. */
	public static void initialize() {
		if (hooked) {
			return;
		}
		hooked = true;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && source.is(DamageTypeTags.IS_FALL) && launched(p)));
	}

	public static void clearSessionState() {
		HOLD_START.clear();
		LAUNCHED.clear();
	}

	public static void forget(UUID player) {
		HOLD_START.remove(player);
		LAUNCHED.remove(player);
	}

	/** Is this player holding Shift+C on an Era I suit right now (server)? */
	public static boolean holding(ServerPlayer player) {
		return HOLD_START.containsKey(player.getUUID());
	}

	/** Is this player still in the air from an eject (fall-safe)? */
	public static boolean launched(ServerPlayer player) {
		return LAUNCHED.containsKey(player.getUUID());
	}

	/** 0..1 of the hold done (server; for the gametests and JARVIS-style readouts). */
	public static float progress(ServerPlayer player) {
		Long start = HOLD_START.get(player.getUUID());
		return start == null ? 0f : Math.min(1f, (player.level().getGameTime() - start) / (float) HOLD_TICKS);
	}

	/** A Shift+C edge on a worn Era I suit ({@link IronManEraOne#ejectKey}). */
	public static void key(ServerPlayer player, IronManSuit suit, boolean pressed) {
		if (!pressed) {
			HOLD_START.remove(player.getUUID());
			return;
		}
		if (suit == null || !suit.eraOne() || IronManSuitUpManager.inTransition(player) || HOLD_START.containsKey(player.getUUID())) {
			return;
		}
		HOLD_START.put(player.getUUID(), player.level().getGameTime());
		// v0.16.7 (user): no text above the hotbar -- the C box in the HUD fills while the hold runs
	}

	/** Per player tick (from {@code IronManSuitTicker}): the launch's fall safety, then finish (or drop) a running hold. */
	public static void tick(ServerPlayer player) {
		tickLaunch(player);
		Long start = HOLD_START.get(player.getUUID());
		if (start == null) {
			return;
		}
		String worn = IronManArmor.wornSuitId(player);
		if (!player.isAlive() || !player.isShiftKeyDown() || !IronManEraOne.isEraOne(worn) || IronManSuitUpManager.inTransition(player)) {
			HOLD_START.remove(player.getUUID());
			return;
		}
		if (player.level().getGameTime() - start >= HOLD_TICKS) {
			HOLD_START.remove(player.getUUID());
			eject(player);
		}
	}

	private static void tickLaunch(ServerPlayer player) {
		Long at = LAUNCHED.get(player.getUUID());
		if (at == null) {
			return;
		}
		long since = player.level().getGameTime() - at;
		player.resetFallDistance();
		if (!player.isAlive() || since > LAUNCH_SAFE_TICKS || since > 5 && (player.onGround() || player.isInWater())) {
			LAUNCHED.remove(player.getUUID());
		}
	}

	/**
	 * Take the worn Era I suit off, now: the pieces leave the body (stamped with the suit's integrity and energy), the
	 * player is shot out of the back, and the empty suit is left standing to fold into its crate. Public for the gametests.
	 * Returns the standing suit, or null if no Era I suit was worn.
	 */
	public static SuitCrateFoldEntity eject(ServerPlayer player) {
		float yaw = player.getYRot();
		Vec3 at = player.position();
		NonNullList<ItemStack> slots = packSuit(player);
		if (slots == null) {
			return null;
		}
		ServerLevel level = player.serverLevel();
		SuitCrateFoldEntity suit = SuitCrateFoldEntity.spawn(level, at, yaw, slots);
		launch(player, yaw);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 14, 0.3, 0.5, 0.3, 0.06);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.2, at.z, 14, 0.3, 0.5, 0.3, 0.14);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 1.2, at.z, 8, 0.2, 0.4, 0.2, 0.03);
		IronManSounds.play(player, IronManSounds.RELEASE, 1.0f, 0.8f);
		IronManSounds.play(player, IronManSounds.CLAMP, 0.8f, 0.6f);
		IronManSounds.play(player, IronManSounds.FACEPLATE_OPEN, 0.8f, 0.7f);
		return suit;
	}

	/**
	 * Strip the worn Era I suit off the body into four stacks (slot 0 helmet .. 3 boots), each stamped with the suit's
	 * integrity and energy; every running system is shut down first. Null if no Era I suit is worn.
	 */
	public static NonNullList<ItemStack> packSuit(ServerPlayer player) {
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null || !suit.eraOne()) {
			return null;
		}
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		IronManSuitTicker.shutDownAllSystems(player, TonyStark.state(player), suit); // flight, holds, running moves
		NonNullList<ItemStack> slots = NonNullList.withSize(4, ItemStack.EMPTY);
		EquipmentSlot[] equip = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		for (int i = 0; i < 4; i++) {
			ItemStack worn = player.getItemBySlot(equip[i]);
			if (worn.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId)) {
				ItemStack copy = worn.copy();
				IronManEnergy.stampStack(copy, energy, integrity);
				player.setItemSlot(equip[i], ItemStack.EMPTY);
				slots.set(i, copy);
			}
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		if (!IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, "");
		}
		return slots;
	}

	/** Shoot the player out of the back of the suit: backwards from {@code yaw} and a little up, fall-safe until they land. */
	public static void launch(ServerPlayer player, float yaw) {
		double rad = Math.toRadians(yaw);
		// facing is (-sin, cos); out of the back is the opposite
		Vec3 back = new Vec3(Math.sin(rad), 0.0, -Math.cos(rad));
		player.setDeltaMovement(back.x * LAUNCH_BACK, LAUNCH_UP, back.z * LAUNCH_BACK);
		player.hurtMarked = true; // send the new velocity to the client (movement is client-driven)
		player.resetFallDistance();
		LAUNCHED.put(player.getUUID(), player.level().getGameTime());
	}
}

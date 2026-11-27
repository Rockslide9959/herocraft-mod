package com.projecthero.mod.combat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 (user: "their melee should deal 5 damage and should be +5 on top of tools and weapons and stuff, please do this for
 * black panthers and other powers that have melee bonuses aswell"): the one melee rule every power shares.
 *
 * <p>A power's melee value <b>N</b> is its bare punch: empty-handed, or holding something that is not a weapon (a block, a
 * gun, a shield), a punch deals exactly N. Holding a weapon or tool -- anything whose main-hand attribute modifiers include
 * attack damage: swords, axes, tridents, maces, pickaxes, modded weapons -- the hit deals the weapon's own damage + N.
 *
 * <p>How: each power keeps its fixed-id {@code ATTACK_DAMAGE} modifier of N - 1 (the bare fist's 1 is already there), and
 * registers that id here ({@link #register}, or {@link #fist}, which sets the modifier from N directly). A weapon's damage
 * already includes the fist's 1, so a weapon hit would come out at weapon + N - 1; this class adds one shared
 * {@link #WEAPON_ID} modifier of +{@value #WEAPON_TOP_UP} whenever a player with any registered melee bonus has a weapon in
 * the main hand, so it comes out at weapon + N. Several powers' bonuses stack as before and the top-up is added once, so a
 * weapon hit is always the weapon's damage + the whole bare punch. It is an ordinary ADD_VALUE modifier, so crits, sweeps,
 * Strength, enchantments and every power's own caps (MAX_HIT, boss caps) work on it as they do on the rest.
 *
 * <p>{@link #reconcile} runs for every player at the end of each server tick (after vanilla has swapped the held item's own
 * modifiers in), and straight away from {@link #fist} / {@link #clear}, so a slot change or item swap is followed within the
 * tick, just as vanilla follows the weapon's own damage.
 *
 * <ul>
 *   <li>{@link #exact}: a modifier that already tunes a weapon hit to an exact total (Thor's Mjolnir / Stormbreaker state
 *       melee) -- while it is on, no top-up.</li>
 *   <li>{@link #virtualWeapon}: a modifier that stands for a weapon held without one in the hand (Undead's shoulder sword,
 *       which copies a stored sword's damage onto the empty hand) -- it counts as holding a weapon.</li>
 * </ul>
 * The id sets hold ResourceLocations only (no worlds or entities), so nothing needs clearing in {@code ServerStateReset}.
 */
public final class MeleeBonus {
	/** The shared weapon top-up modifier. */
	public static final ResourceLocation WEAPON_ID = ProjectHeroMod.id("melee_bonus_weapon");
	/** A weapon's damage already carries the fist's 1: this makes a weapon hit weapon + N instead of weapon + N - 1. */
	public static final double WEAPON_TOP_UP = 1.0;

	private static final Set<ResourceLocation> MELEE = ConcurrentHashMap.newKeySet();
	private static final Set<ResourceLocation> EXACT = ConcurrentHashMap.newKeySet();
	private static final Set<ResourceLocation> VIRTUAL_WEAPON = ConcurrentHashMap.newKeySet();

	private MeleeBonus() {
	}

	/** Hooks the per-tick reconcile. From {@code ProjectHeroMod.onInitialize}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				reconcile(p);
			}
		});
	}

	/** Marks {@code id} as a power's melee bonus (an ADD_VALUE attack-damage modifier of N - 1). Returns it for field initialisers. */
	public static ResourceLocation register(ResourceLocation id) {
		MELEE.add(id);
		return id;
	}

	/** Marks {@code id} as a modifier that tunes a weapon hit to an exact total: no top-up while it is on. */
	public static ResourceLocation exact(ResourceLocation id) {
		EXACT.add(id);
		return id;
	}

	/** Marks {@code id} as a weapon held without an item in the hand: while it is on, the hand counts as holding a weapon. */
	public static ResourceLocation virtualWeapon(ResourceLocation id) {
		VIRTUAL_WEAPON.add(id);
		return id;
	}

	public static boolean isRegistered(ResourceLocation id) {
		return MELEE.contains(id);
	}

	/**
	 * Gives {@code player} the melee bonus {@code id} so a bare punch deals exactly {@code n} (and a weapon hit the weapon + n).
	 * Idempotent: safe to call every tick.
	 */
	public static void fist(ServerPlayer player, ResourceLocation id, double n) {
		register(id);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, id, n - 1.0, AttributeModifier.Operation.ADD_VALUE);
		reconcile(player);
	}

	/** Takes the melee bonus {@code id} off (and the top-up with it if nothing else needs it). */
	public static void clear(ServerPlayer player, ResourceLocation id) {
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, id);
		reconcile(player);
	}

	/** True for a weapon or tool: the stack's main-hand modifiers (item, components, enchantments) include attack damage. */
	public static boolean isWeapon(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		boolean[] found = { false };
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attr, mod) -> {
			if (attr.is(Attributes.ATTACK_DAMAGE)) {
				found[0] = true;
			}
		});
		return found[0];
	}

	/** True while {@code player} has any registered, positive melee bonus on. */
	public static boolean hasMeleeBonus(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.ATTACK_DAMAGE);
		return inst != null && any(inst, MELEE, true);
	}

	/** The top-up {@code player} should have right now (0 or {@link #WEAPON_TOP_UP}). */
	public static double wantedTopUp(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (inst == null) {
			return 0.0;
		}
		boolean weapon = isWeapon(player.getMainHandItem()) || any(inst, VIRTUAL_WEAPON, true);
		if (!weapon || !any(inst, MELEE, true) || any(inst, EXACT, false)) {
			return 0.0;
		}
		return WEAPON_TOP_UP;
	}

	/** Brings the top-up in line with the main hand and the bonuses on. Idempotent and cheap: runs every tick for every player. */
	public static void reconcile(ServerPlayer player) {
		if (wantedTopUp(player) > 0.0) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, WEAPON_ID, WEAPON_TOP_UP, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, WEAPON_ID);
		}
	}

	private static boolean any(AttributeInstance inst, Set<ResourceLocation> ids, boolean positiveAdd) {
		for (ResourceLocation id : ids) {
			AttributeModifier m = inst.getModifier(id);
			if (m != null && (!positiveAdd || (m.amount() > 0.0 && m.operation() == AttributeModifier.Operation.ADD_VALUE))) {
				return true;
			}
		}
		return false;
	}
}

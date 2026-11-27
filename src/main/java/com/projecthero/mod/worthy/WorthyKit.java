package com.projecthero.mod.worthy;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.supersoldier.CapShieldSlot;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;

/**
 * v0.17.7 Thor x Super Soldier (user: "When super solider teammate has stormbreaker or Mjolnir they should have 2 movesets with the
 * weapon, a moveset with just Mjolnir or stormbreaker and a moveset for dual wielding their shield and Mjolnir or stormbreaker"):
 * which kit a Super Soldier with a lent weapon has, the key tables (HUD, Alt list, Guidebook), and the routing.
 * <p>
 * The kit comes from his hands: a lent weapon in either hand (or out on his throw) and nothing else = the weapon-only kit for that
 * weapon; a shield in the other hand, in the Battle Suit's slot, or out on a throw of his = the shield + weapon kit. While he has a
 * kit it owns R / G / Z / X / V / C ({@code SuperSoldierAbilityManager.handle} asks {@link #handle} first); H and N stay as they are.
 * The weapon's own right click goes through {@link #use}: the throw in the weapon-only kits, the shield's block in the dual kit.
 * v0.17.7: with a lent weapon that is NOT in his hands (no kit) plain R is the call ({@link #CALL_WEAPON},
 * {@code WorthyLending.callLent}); every other key is his own Super Soldier kit.
 *
 * <pre>
 *            Mjolnir only           Stormbreaker only         Shield + weapon
 *   R        Hammer Combo           Axe Cleaves               Shield and Weapon
 *   Shift+R  Thunder Uppercut       Rising Cleave             Bash Launcher
 *   G        Call Lightning         Lightning Cleave          Charged Shield Throw
 *   Shift+G  Thunderclap            Executioner's Chop        Twin Throw
 *   Z        Thunder Slam           Storm Crash               Lightning Shield Slam
 *   Shift+Z  Worthy Storm (ult)     Stormbreaker's Verdict    Thunder Clap (ult, "Endgame")
 *   X        Hammer Dash / Boost    Axe Dash / Boost          (the weapon's Dash / Boost)
 *   Shift+X  Hammer Launch          Storm Leap                (the weapon's Launch)
 *   V        Whirl Guard            Axe Guard                 Lightning Guard
 *   Shift+V  Rally Bolt             Rally Bolt                Rally Bolt
 *   C        Hammer Throw (+ RMB)   Axe Throw (+ RMB)         weapon throw; Shift+C the shield throw; RMB blocks
 * </pre>
 */
public final class WorthyKit {
	public enum Kit {
		NONE("none", null, false),
		MJOLNIR("mjolnir", ThorWeapon.MJOLNIR, false),
		STORMBREAKER("stormbreaker", ThorWeapon.STORMBREAKER, false),
		SHIELD_MJOLNIR("shield_mjolnir", ThorWeapon.MJOLNIR, true),
		SHIELD_STORMBREAKER("shield_stormbreaker", ThorWeapon.STORMBREAKER, true);

		public final String id;
		@Nullable
		public final ThorWeapon weapon;
		public final boolean dual;

		Kit(String id, @Nullable ThorWeapon weapon, boolean dual) {
			this.id = id;
			this.weapon = weapon;
			this.dual = dual;
		}

		public boolean axe() {
			return weapon == ThorWeapon.STORMBREAKER;
		}
	}

	// ---- ability ids (lang: projecthero.worthy.ability.<id> / .desc)
	public static final String HAMMER_COMBO = "hammer_combo";
	public static final String THUNDER_UPPERCUT = "thunder_uppercut";
	public static final String CALL_LIGHTNING = "call_lightning";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String THUNDER_SLAM = "thunder_slam";
	public static final String WORTHY_STORM = "worthy_storm";
	public static final String HAMMER_DASH = "hammer_dash";
	public static final String HAMMER_LAUNCH = "hammer_launch";
	public static final String WHIRL_GUARD = "whirl_guard";
	public static final String RALLY_BOLT = "rally_bolt";
	public static final String HAMMER_THROW = "hammer_throw";

	public static final String AXE_CLEAVES = "axe_cleaves";
	public static final String RISING_CLEAVE = "rising_cleave";
	public static final String LIGHTNING_CLEAVE = "lightning_cleave";
	public static final String EXECUTIONERS_CHOP = "executioners_chop";
	public static final String STORM_CRASH = "storm_crash";
	public static final String VERDICT = "stormbreakers_verdict";
	public static final String AXE_DASH = "axe_dash";
	public static final String STORM_LEAP = "storm_leap";
	public static final String AXE_GUARD = "axe_guard";
	public static final String AXE_THROW = "axe_throw";

	public static final String SHIELD_AND_WEAPON = "shield_and_weapon";
	public static final String BASH_LAUNCHER = "bash_launcher";
	public static final String CHARGED_SHIELD_THROW = "charged_shield_throw";
	public static final String TWIN_THROW = "twin_throw";
	public static final String LIGHTNING_SHIELD_SLAM = "lightning_shield_slam";
	public static final String THUNDER_CLAP = "thunder_clap";
	public static final String LIGHTNING_GUARD = "lightning_guard";
	/**
	 * v0.17.7: plain R while a lent weapon is not in his hands (his normal kit): the call. One cooldown key; the HUD / Alt list name it
	 * {@code projecthero.worthy.ability.call_mjolnir} / {@code call_stormbreaker} ({@link #callNameId}).
	 */
	public static final String CALL_WEAPON = "call_weapon";
	/** The Super Soldier's own shield throw (Shift+C in the dual kit), shown with his own name. */
	public static final String SHIELD_THROW = SuperSoldierAbilities.SHIELD_THROW;

	/** Shared cooldowns: one ultimate box whichever kit fired it (swapping can never chain two), one dash, one launch, one guard. */
	public static String cooldownKey(String id) {
		return switch (id) {
			case WORTHY_STORM, VERDICT, THUNDER_CLAP -> "worthy_ultimate";
			case HAMMER_DASH, AXE_DASH -> "worthy_dash";
			case HAMMER_LAUNCH, STORM_LEAP -> "worthy_launch";
			case WHIRL_GUARD, AXE_GUARD, LIGHTNING_GUARD -> "worthy_guard";
			default -> id;
		};
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case HAMMER_COMBO -> WorthyConfig.HAMMER_COMBO_CD;
			case THUNDER_UPPERCUT -> WorthyConfig.UPPERCUT_CD;
			case CALL_LIGHTNING -> WorthyConfig.CALL_LIGHTNING_CD;
			case THUNDERCLAP -> WorthyConfig.THUNDERCLAP_CD;
			case THUNDER_SLAM -> WorthyConfig.SLAM_CD;
			case WORTHY_STORM, VERDICT, THUNDER_CLAP -> WorthyConfig.ULTIMATE_CD;
			case HAMMER_DASH, AXE_DASH -> WorthyConfig.DASH_CD;
			case HAMMER_LAUNCH, STORM_LEAP -> WorthyConfig.LAUNCH_CD;
			case WHIRL_GUARD, AXE_GUARD -> WorthyConfig.GUARD_CD;
			case LIGHTNING_GUARD -> WorthyConfig.LIGHTNING_GUARD_CD;
			case RALLY_BOLT -> WorthyConfig.RALLY_CD;
			case HAMMER_THROW -> WorthyConfig.HAMMER_THROW_CD;
			case AXE_CLEAVES -> WorthyConfig.AXE_CLEAVES_CD;
			case RISING_CLEAVE -> WorthyConfig.RISING_CLEAVE_CD;
			case LIGHTNING_CLEAVE -> WorthyConfig.LIGHTNING_CLEAVE_CD;
			case EXECUTIONERS_CHOP -> WorthyConfig.EXEC_CHOP_CD;
			case STORM_CRASH -> WorthyConfig.CRASH_CD;
			case AXE_THROW -> WorthyConfig.AXE_THROW_CD;
			case SHIELD_AND_WEAPON -> WorthyConfig.SHIELD_STRIKE_CD;
			case BASH_LAUNCHER -> WorthyConfig.BASH_LAUNCH_CD;
			case CHARGED_SHIELD_THROW -> WorthyConfig.CHARGED_THROW_CD;
			case TWIN_THROW -> WorthyConfig.TWIN_THROW_CD;
			case LIGHTNING_SHIELD_SLAM -> WorthyConfig.SHIELD_SLAM_CD;
			case CALL_WEAPON -> WorthyConfig.CALL_WEAPON_CD;
			default -> 0;
		};
	}

	private WorthyKit() {
	}

	/** The lang id of the call for {@code weapon}: {@code call_mjolnir} / {@code call_stormbreaker}. */
	public static String callNameId(ThorWeapon weapon) {
		return weapon == ThorWeapon.STORMBREAKER ? "call_stormbreaker" : "call_mjolnir";
	}

	// ================================================================== which kit

	/** The kit {@code player}'s hands give him right now (both sides: the loan view list is synced to everyone). */
	public static Kit kit(Player player) {
		InteractionHand hand = WorthyLending.lentHand(player);
		if (hand == null) {
			return Kit.NONE;
		}
		ThorWeapon weapon = ThorWeapon.of(com.projecthero.mod.power.ThorThrown.inHand(player, hand));
		if (weapon == null) {
			return Kit.NONE;
		}
		boolean shield = hasShield(player, hand);
		return weapon == ThorWeapon.STORMBREAKER ? (shield ? Kit.SHIELD_STORMBREAKER : Kit.STORMBREAKER)
				: (shield ? Kit.SHIELD_MJOLNIR : Kit.MJOLNIR);
	}

	/** A shield in the hand the weapon is not in, in the Battle Suit's slot, or out on a throw of his own (so the kit holds while it flies). */
	static boolean hasShield(Player player, InteractionHand weaponHand) {
		InteractionHand other = weaponHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		if (player.getItemInHand(other).getItem() instanceof ShieldItem || CapShieldSlot.hasThrowable(player)) {
			return true;
		}
		if (player.level() == null) {
			return false;
		}
		return !player.level().getEntitiesOfClass(SoldierShieldEntity.class, player.getBoundingBox().inflate(48.0),
				e -> e.ownerEntityId() == player.getId()).isEmpty();
	}

	/** The ability ids on {@code slot} for {@code kit}: {plain, shift}, or {plain} when Shift does the same, or empty. */
	public static String[] idsOf(Kit kit, AbilitySlot slot) {
		boolean axe = kit.axe();
		if (kit == Kit.NONE) {
			return new String[0];
		}
		if (kit.dual) {
			return switch (slot) {
				case SLOT_1 -> new String[] { SHIELD_AND_WEAPON, BASH_LAUNCHER };
				case SLOT_2 -> new String[] { CHARGED_SHIELD_THROW, TWIN_THROW };
				case SLOT_3 -> new String[] { axe ? AXE_DASH : HAMMER_DASH, axe ? STORM_LEAP : HAMMER_LAUNCH };
				case SLOT_4 -> new String[] { LIGHTNING_SHIELD_SLAM, THUNDER_CLAP };
				case SLOT_5 -> new String[] { LIGHTNING_GUARD, RALLY_BOLT };
				case SLOT_6 -> new String[] { axe ? AXE_THROW : HAMMER_THROW, SHIELD_THROW };
				default -> new String[0];
			};
		}
		if (axe) {
			return switch (slot) {
				case SLOT_1 -> new String[] { AXE_CLEAVES, RISING_CLEAVE };
				case SLOT_2 -> new String[] { LIGHTNING_CLEAVE, EXECUTIONERS_CHOP };
				case SLOT_3 -> new String[] { AXE_DASH, STORM_LEAP };
				case SLOT_4 -> new String[] { STORM_CRASH, VERDICT };
				case SLOT_5 -> new String[] { AXE_GUARD, RALLY_BOLT };
				case SLOT_6 -> new String[] { AXE_THROW };
				default -> new String[0];
			};
		}
		return switch (slot) {
			case SLOT_1 -> new String[] { HAMMER_COMBO, THUNDER_UPPERCUT };
			case SLOT_2 -> new String[] { CALL_LIGHTNING, THUNDERCLAP };
			case SLOT_3 -> new String[] { HAMMER_DASH, HAMMER_LAUNCH };
			case SLOT_4 -> new String[] { THUNDER_SLAM, WORTHY_STORM };
			case SLOT_5 -> new String[] { WHIRL_GUARD, RALLY_BOLT };
			case SLOT_6 -> new String[] { HAMMER_THROW };
			default -> new String[0];
		};
	}

	/** Guidebook rows per kit: {key label, ability id}. */
	public static String[][] guideRows(Kit kit) {
		java.util.List<String[]> rows = new java.util.ArrayList<>();
		String[] labels = { "R", "G", "X", "Z", "V", "C" };
		AbilitySlot[] slots = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 };
		for (int i = 0; i < slots.length; i++) {
			String[] ids = idsOf(kit, slots[i]);
			if (ids.length > 0) {
				rows.add(new String[] { labels[i], ids[0] });
			}
			if (ids.length > 1) {
				rows.add(new String[] { "Shift+" + labels[i], ids[1] });
			}
		}
		return rows.toArray(new String[0][]);
	}

	// ================================================================== routing

	/**
	 * {@code SuperSoldierAbilityManager.handle} asks this first: while he has a kit every key is the kit's.
	 *
	 * @return true if the key was taken (even a key the kit leaves empty, so his bare-handed move never fires mid-kit)
	 */
	public static boolean handle(ServerPlayer p, AbilitySlot slot, boolean pressed) {
		Kit kit = kit(p);
		if (kit == Kit.NONE) {
			// v0.17.7: a lent weapon not in his hands -- plain R is "Call Mjolnir" / "Call Stormbreaker" (Shift+R stays his own move)
			if (slot == AbilitySlot.SLOT_1 && pressed && !p.isShiftKeyDown() && WorthyLending.callTarget(p) != null) {
				WorthyLending.callLent(p);
				return true;
			}
			return false;
		}
		if (!pressed) {
			return true;
		}
		boolean shift = p.isShiftKeyDown();
		String[] ids = idsOf(kit, slot);
		if (ids.length == 0) {
			return true;
		}
		String id = shift && ids.length > 1 ? ids[1] : ids[0];
		WorthyMoves.fire(p, kit, id);
		return true;
	}

	/**
	 * The lent weapon's own right click (from {@code MjolnirItem.use} / {@code StormbreakerItem.use}, before their worthiness check).
	 * Null = not a lent weapon in his hand, the item's own rules apply. Weapon-only kit: the throw. Shield + weapon: PASS, so the
	 * other hand's shield blocks.
	 */
	@Nullable
	public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!WorthyLending.isLent(player, stack) || !com.projecthero.mod.supersoldier.SuperSoldier.hasPower(player)) {
			return null;
		}
		Kit kit = kit(player);
		if (kit == Kit.NONE || kit.dual || hand != InteractionHand.MAIN_HAND) {
			return InteractionResultHolder.pass(stack);
		}
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			WorthyMoves.fire(sp, kit, kit.axe() ? AXE_THROW : HAMMER_THROW);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}
}

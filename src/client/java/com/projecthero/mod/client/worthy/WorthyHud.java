package com.projecthero.mod.client.worthy;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.client.gui.MoveListOverlay;
import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.worthy.WorthyFlight;
import com.projecthero.mod.worthy.WorthyKit;
import com.projecthero.mod.worthy.WorthyLending;
import com.projecthero.mod.worthy.WorthyMoves;
import com.projecthero.mod.worthy.WorthyState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Thor x Super Soldier: the Super Soldier HUD while he wields a lent weapon -- the same mono strip ({@link VanillaHud.Stack},
 * bottom right, R G Z X C V) showing the active kit's moves: the title names the kit ("Super Soldier -- Mjolnir" / "-- Shield +
 * Stormbreaker"), the tags say whose weapon it is and Flying / Boost / Guard, Shift swaps to the Shift moves, Left-Alt lists the
 * kit's move names. {@code SuperSoldierHud.render} hands over to {@link #render} while {@link #owns} is true. No H / N boxes.
 */
public final class WorthyHud {
	private static final int NAME = 0xFFE2E2E2;
	private static final int GOLD = 0xFFE8C766;
	private static final int SKY = 0xFF8FC8FF;

	private WorthyHud() {
	}

	public static boolean owns(Player p) {
		return SuperSoldier.hasPower(p) && WorthyKit.kit(p) != WorthyKit.Kit.NONE;
	}

	// ---- v0.17.7: R as the call ("Call Mjolnir" / "Call Stormbreaker") while a lent weapon is not in his hands -- the normal Super
	// Soldier HUD (SuperSoldierHud) asks these for its R box, a tag and the Alt list's R row

	/** The weapon his R calls right now, or null (R is his own move). */
	@org.jetbrains.annotations.Nullable
	public static ThorWeapon callWeapon(Player p) {
		WorthyState.Borrowed b = WorthyLending.callTarget(p);
		return b == null ? null : WorthyLending.weaponOf(b);
	}

	public static int callCooldown(Player p) {
		return WorthyMoves.cooldownRemaining(p, WorthyKit.CALL_WEAPON);
	}

	public static float callFrac(Player p) {
		return Math.min(1f, callCooldown(p) / (float) Math.max(1, WorthyKit.maxCooldown(WorthyKit.CALL_WEAPON)));
	}

	public static Component callName(ThorWeapon weapon) {
		return Component.translatable("projecthero.worthy.ability." + WorthyKit.callNameId(weapon));
	}

	/** The gold "R: Call Mjolnir" tag. */
	public static VanillaHud.Tag callTag(ThorWeapon weapon) {
		return new VanillaHud.Tag(Component.translatable("hud.projecthero.worthy.call_tag", callName(weapon)).getString(), GOLD);
	}

	public static void render(GuiGraphics g, Player p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		WorthyKit.Kit kit = WorthyKit.kit(p);
		if (kit == WorthyKit.Kit.NONE) {
			return;
		}
		WorthyState s = WorthyState.of(p);
		long now = mc.level.getGameTime();
		boolean shift = p.isShiftKeyDown();
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, VanillaHud.ORDER.length).keys(VanillaHud.ORDER.length);
		for (int i = 0; i < VanillaHud.ORDER.length; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			String[] ids = WorthyKit.idsOf(kit, slot);
			if (ids.length == 0) {
				continue;
			}
			String shown = ids[shift && ids.length > 1 ? 1 : 0];
			String other = ids.length > 1 ? ids[shift ? 0 : 1] : null;
			int cd = cooldown(p, shown);
			float frac = cd / (float) Math.max(1, maxCooldown(shown));
			float otherFrac = other == null ? 0f : cooldown(p, other) / (float) Math.max(1, maxCooldown(other));
			boolean active = (slot == AbilitySlot.SLOT_5 && s.guardUntil > now)
					|| (slot == AbilitySlot.SLOT_3 && s.boostUntil > now)
					|| (slot == AbilitySlot.SLOT_6 && s.throwWeapon >= 0)
					|| (slot == AbilitySlot.SLOT_4 && (s.seq == WorthyState.SEQ_WORTHY_STORM || s.seq == WorthyState.SEQ_VERDICT
							|| s.seq == WorthyState.SEQ_CLAP));
			st.slot(i, VanillaHud.keyLabel(slot), true, shift, Math.min(1f, frac), VanillaHud.seconds(cd), Math.min(1f, otherFrac), active);
		}
		st.title(Component.translatable("hud.projecthero.worthy.title." + kit.id), NAME);
		List<VanillaHud.Tag> tags = new ArrayList<>();
		String owner = ownerOf(s, kit);
		if (owner != null) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.worthy.lent_by", owner).getString(), GOLD));
		}
		if (WorthyFlight.isFlying(p)) {
			tags.add(new VanillaHud.Tag(Component.translatable(s.boostUntil > now ? "hud.projecthero.worthy.boost"
					: "hud.projecthero.worthy.flying").getString(), SKY));
		}
		if (s.chargedUntil > now) {
			tags.add(new VanillaHud.Tag(Component.translatable("hud.projecthero.worthy.charged").getString(), SKY));
		}
		st.tags(tags);

		if (MoveListOverlay.held()) {
			List<MoveListOverlay.Row> rows = new ArrayList<>();
			for (AbilitySlot slotKey : VanillaHud.ORDER) {
				String[] ids = WorthyKit.idsOf(kit, slotKey);
				if (ids.length > 0) {
					rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slotKey), name(ids[0]), ids.length > 1 ? name(ids[1]) : null));
				}
			}
			rows.add(MoveListOverlay.inline("2x Space", Component.translatable("hud.projecthero.worthy.move_list_flight")));
			if (!kit.dual) {
				rows.add(MoveListOverlay.inline("RMB", Component.translatable("hud.projecthero.worthy.move_list_rmb")));
			}
			st.moveList(rows);
		}
	}

	private static Component name(String id) {
		return id.equals(WorthyKit.SHIELD_THROW) ? Component.translatable("projecthero.super_soldier.ability." + id)
				: Component.translatable("projecthero.worthy.ability." + id);
	}

	private static int cooldown(Player p, String id) {
		return id.equals(WorthyKit.SHIELD_THROW) ? SuperSoldier.cooldownRemaining(p, id) : WorthyMoves.cooldownRemaining(p, id);
	}

	private static int maxCooldown(String id) {
		return id.equals(WorthyKit.SHIELD_THROW) ? SuperSoldierAbilityManager.maxCooldown(id) : WorthyKit.maxCooldown(id);
	}

	private static String ownerOf(WorthyState s, WorthyKit.Kit kit) {
		for (WorthyState.Borrowed b : s.borrowed) {
			if (kit.weapon != null && b.weapon() == kit.weapon.ordinal()) {
				return b.owner();
			}
		}
		return null;
	}
}

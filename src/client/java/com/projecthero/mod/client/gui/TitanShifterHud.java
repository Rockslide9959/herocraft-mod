package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * The Titan Shifter HUD. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), bottom-right like the newest powers:
 * <pre>
 *   Titan form:  Revert Form [H]                    tag
 *                Attack Titan                       title (the shifter's Titan)
 *                (limb silhouette) NAPE EXPOSED!    the limbs + the nape status (flashes with a hostile behind)
 *                Emergency [=====-----]   12s       only in an emergency form
 *                Steam    [=========-]    82%
 *                Titan HP [=======---] 412/500      orange / red as it falls, blue hardened, green regenerating
 *                +--+--+--+--+--+--+                R G Z X C V: the tap move (Shift: the Shift move); the other move's
 *                                                   recharge is the durability bar; a running move lights its key
 *   Human form:  Titan Shift Ready [H] ...          status (and "Passive Regen: OFF")
 *                Attack Titan                       title
 *                Steam / Healing pool meters
 *                +--+--+--+                         the three Partial Shifting keys (R G X)
 * </pre>
 * Hold Left-Alt for the full move list (every key, tap and Shift, with names).
 */
public final class TitanShifterHud {
	private static final int SIL_H = 22;
	private static final int COLOR_ENERGY = 0xFFFFD53A; // v0.12.35: yellow, and it stays yellow when full
	private static final int COLOR_EMERGENCY = 0xFFB8E4FF; // v0.12.39: pale electric blue
	private static final int COLOR_POOL = 0xFF5ED36A;
	private static final int COLOR_LIMB = 0xFFD8C8B0;
	private static final int COLOR_LIMB_GONE = 0xFF7A1010;
	/** The human kit's keys (R G X), in the strip's R G Z X C V order. */
	private static final AbilitySlot[] HUMAN_KEYS = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3 };

	private TitanShifterHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null || !TitanShifter.isShifter(mc.player)) {
			return;
		}
		TitanShifterState s = mc.player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null) {
			return;
		}
		long now = mc.level.getGameTime();
		TitanPhase phase = s.phase();
		TitanType type = TitanType.byId(s.typeId);
		TitanFormEntity form = mc.player.getVehicle() instanceof TitanFormEntity f ? f : null;
		boolean inside = phase == TitanPhase.TITAN && form != null;
		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		boolean shift = mc.player.isShiftKeyDown();
		// v0.16.7: the title is the shifter's Titan ("Attack Titan", "Armoured Titan" ...)
		Component title = Component.translatable(type.nameKey());

		if (inside) {
			VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(6);
			for (int i = 0; i < VanillaHud.ORDER.length; i++) {
				AbilitySlot slot = VanillaHud.ORDER[i];
				TitanMove tap = TitanMove.of(type, slot.number(), false);
				TitanMove alt = TitanMove.of(type, slot.number(), true);
				boolean active = (tap == TitanMove.REGEN && s.regenUntil > now) || (alt == TitanMove.HARDEN && s.hardenUntil > now)
						|| (alt == TitanMove.signature(type) && signatureActive(form, type))
						|| (alt == TitanMove.NAPE_GUARD && form.isNapeGuarded());
				slot(st, mc, i, slot, tap, alt, shift, active);
			}
			// Titan HP
			float hp = form.getHealth();
			float max = form.getMaxHealth();
			float frac = max > 0f ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
			boolean hard = s.hardenUntil > now;
			boolean regen = s.regenUntil > now;
			int fill = hard ? 0xFF5AB0FF : regen ? 0xFF40E060 : frac > 0.5f ? 0xFFE0A020 : frac > 0.25f ? 0xFFE06020 : 0xFFD02020;
			st.meter(Component.translatable("hud.projecthero.titan_shifter.hp"), frac, fill, (int) Math.ceil(hp) + "/" + (int) Math.ceil(max),
					VanillaHud.WHITE);
			steam(st, s);
			if (s.inEmergency()) {
				int leftTicks = (int) Math.max(0L, s.emergencyUntil - now);
				float left = Math.max(0f, Math.min(1f, leftTicks / (float) TitanShifterConfig.emergency().durationTicks));
				st.meter(Component.translatable("hud.projecthero.titan_shifter.emergency_label"), left, COLOR_EMERGENCY,
						VanillaHud.seconds(leftTicks) + "s", COLOR_EMERGENCY);
			}
			// limb silhouette + nape status, a hand-drawn row
			st.reserve(SIL_H + 2);
			int ry = st.top() + 1;
			silhouette(g, st.x0 + 1, ry, form);
			int textW = st.width - 20;
			Component nape = napeStatus(mc, form, now);
			if (nape != null) {
				g.drawString(mc.font, cut(mc, nape, textW), st.x0 + 20, ry + 2, VanillaHud.WHITE, true);
			}
			if (form.severedLimbs() != 0) {
				g.drawString(mc.font, cut(mc, Component.translatable("hud.projecthero.titan_shifter.limbs_regrowing").withStyle(ChatFormatting.GRAY), textW), st.x0 + 20,
						ry + 12, VanillaHud.GREY, true);
			}
			st.title(title, VanillaHud.GOLD);
			st.wrapped(Component.translatable("hud.projecthero.titan_shifter.revert", key), VanillaHud.YELLOW);
			st.moveList(moveRows(type));
			return;
		}

		// human form: the Partial Shifting keys (R G X)
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, 6).keys(HUMAN_KEYS.length);
		for (int i = 0; i < HUMAN_KEYS.length; i++) {
			AbilitySlot slot = HUMAN_KEYS[i];
			slot(st, mc, i, slot, TitanMove.of(null, slot.number(), false), TitanMove.of(null, slot.number(), true), shift,
					slot == AbilitySlot.SLOT_3 && TitanShifter.cocooned(mc.player));
		}
		// v0.16.7: the human form heals from its own pool, never from Steam
		double poolMax = TitanShifterConfig.energy().humanPoolMax;
		float pool = TitanShifter.humanPool(mc.player);
		float pFrac = (float) Math.max(0.0, Math.min(1.0, pool / Math.max(1.0, poolMax)));
		st.meter(Component.translatable("hud.projecthero.titan_shifter.pool_label"), pFrac, COLOR_POOL,
				(int) Math.floor(pFrac * 100f + 1.0e-3f) + "%", VanillaHud.WHITE);
		steam(st, s);
		st.title(title, VanillaHud.GOLD);

		boolean full = s.energy + 1.0e-3f >= TitanShifter.energyNeeded();
		Component status = switch (phase) {
			case HUMAN -> {
				int cd = TitanShifter.transformCooldownRemaining(mc.player);
				if (cd > 0) {
					yield Component.translatable("hud.projecthero.titan_shifter.cooldown", (cd + 19) / 20).withStyle(ChatFormatting.RED);
				}
				if (full) {
					yield Component.translatable("hud.projecthero.titan_shifter.ready", key).withStyle(ChatFormatting.YELLOW);
				}
				// v0.12.39: 30%+ (and no penalty from the last emergency form): hold H for an emergency shift
				if (TitanShifter.emergencyAllowed(s)) {
					yield Component.translatable("hud.projecthero.titan_shifter.emergency_ready", key).withStyle(ChatFormatting.AQUA);
				}
				yield s.emergencyPenalty ? Component.translatable("hud.projecthero.titan_shifter.drained").withStyle(ChatFormatting.GRAY) : null;
			}
			case TRANSFORMING -> Component.translatable("hud.projecthero.titan_shifter.transforming").withStyle(ChatFormatting.GOLD);
			case TITAN -> null;
			case REVERTING -> Component.translatable("hud.projecthero.titan_shifter.reverting").withStyle(ChatFormatting.GRAY);
			case DEFEATED -> Component.translatable("hud.projecthero.titan_shifter.defeated").withStyle(ChatFormatting.DARK_RED);
			case RECOVERING -> Component.translatable("hud.projecthero.titan_shifter.recovering",
					(int) Math.max(0, (s.phaseUntil - now + 19) / 20)).withStyle(ChatFormatting.GRAY);
		};
		if (status != null) {
			st.wrapped(status, VanillaHud.WHITE);
		}
		if (s.regenOff) {
			// v0.12.43: Shift+N switched the passive regeneration off
			st.wrapped(Component.translatable("hud.projecthero.titan_shifter.regen_off"), VanillaHud.GREY);
		}
		if (phase == TitanPhase.HUMAN) {
			st.moveList(moveRows(null));
		}
	}

	/**
	 * One key: the tap move (Shift held: the Shift move, yellow letter), its recharge as the white sweep + seconds, the other
	 * move's recharge as the durability bar; {@code active} lights it with the selection frame.
	 */
	private static void slot(VanillaHud.Stack st, Minecraft mc, int i, AbilitySlot slot, TitanMove tap, TitanMove alt, boolean shift,
			boolean active) {
		boolean showAlt = shift && alt != null;
		TitanMove shown = showAlt ? alt : tap;
		TitanMove other = showAlt ? tap : alt;
		int cd = shown == null ? 0 : TitanShifter.cooldownRemaining(mc.player, shown.id);
		int max = shown == null ? 0 : shown.cooldown();
		int ocd = other == null ? 0 : TitanShifter.cooldownRemaining(mc.player, other.id);
		int omax = other == null ? 0 : Math.max(1, other.cooldown());
		st.slot(i, VanillaHud.keyLabel(slot), shown != null, showAlt, cd > 0 && max > 0 ? Math.min(1f, cd / (float) max) : 0f,
				VanillaHud.seconds(cd), ocd > 0 ? Math.min(1f, ocd / (float) omax) : 0f, active);
	}

	/** The first line of {@code text} wrapped to {@code width} (keeps its colours; a long translation is cut, never sprawls). */
	private static net.minecraft.util.FormattedCharSequence cut(Minecraft mc, Component text, int width) {
		List<net.minecraft.util.FormattedCharSequence> lines = mc.font.split(text, width);
		return lines.isEmpty() ? net.minecraft.util.FormattedCharSequence.EMPTY : lines.get(0);
	}

	private static boolean signatureActive(TitanFormEntity form, TitanType type) {
		return switch (type) {
			case ARMORED_TITAN -> form.isCharging();
			case FEMALE_TITAN -> form.isCrystal();
			case BEAST_TITAN -> form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_BOULDER);
			default -> form.hasMoveFlag(TitanFormEntity.FLAG_RAGING);
		};
	}

	/** The Steam meter (yellow, the percentage). */
	private static void steam(VanillaHud.Stack st, TitanShifterState s) {
		double eMax = TitanShifterConfig.energy().max;
		float eFrac = (float) Math.max(0.0, Math.min(1.0, s.energy / eMax));
		int pct = (int) Math.floor(eFrac * 100.0f + 1.0e-3f);
		st.meter(Component.translatable("hud.projecthero.titan_shifter.energy_label"), eFrac, COLOR_ENERGY, pct + "%", VanillaHud.WHITE);
	}

	/** v0.16.7: a little Titan -- head, body, two arms, two legs; a severed limb is drawn as a dark red stump. */
	private static void silhouette(GuiGraphics g, int x, int y, TitanFormEntity form) {
		int body = 0xFFB8A890;
		boolean napeOpen = !form.isNapeGuarded() && !form.isCrystal() && !form.isHardened();
		g.fill(x + 6, y, x + 11, y + 5, body);                                     // head
		g.fill(x + 7, y + 4, x + 10, y + 6, napeOpen ? 0xFFE04040 : 0xFF6FB0FF);   // the nape
		g.fill(x + 5, y + 6, x + 12, y + 14, body);                                // body
		limb(g, x + 2, y + 6, 3, 8, form.hasLimb(TitanFormEntity.LIMB_L_ARM));     // left arm (viewer's left)
		limb(g, x + 12, y + 6, 3, 8, form.hasLimb(TitanFormEntity.LIMB_R_ARM));    // right arm
		limb(g, x + 5, y + 14, 3, 8, form.hasLimb(TitanFormEntity.LIMB_L_LEG));    // left leg
		limb(g, x + 9, y + 14, 3, 8, form.hasLimb(TitanFormEntity.LIMB_R_LEG));    // right leg
	}

	private static void limb(GuiGraphics g, int x, int y, int w, int h, boolean present) {
		if (present) {
			g.fill(x, y, x + w, y + h, COLOR_LIMB);
		} else {
			g.fill(x, y, x + w, y + 2, COLOR_LIMB_GONE);
			g.renderOutline(x, y, w, h, 0x807A1010);
		}
	}

	/** The nape line: EXPOSED (flashing) with a hostile behind, guarded / crystal, or nothing. */
	private static Component napeStatus(Minecraft mc, TitanFormEntity form, long now) {
		if (form.isNapeGuarded()) {
			return Component.translatable("hud.projecthero.titan_shifter.nape_guarded").withStyle(ChatFormatting.AQUA);
		}
		if (form.isCrystal() || form.isHardened()) {
			return Component.translatable("hud.projecthero.titan_shifter.nape_crystal").withStyle(ChatFormatting.AQUA);
		}
		if (hostileBehind(mc, form)) {
			boolean on = (now / 4) % 2 == 0;
			return Component.translatable("hud.projecthero.titan_shifter.nape_warning")
					.withStyle(on ? ChatFormatting.RED : ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		}
		return null;
	}

	/** A hostile (a monster, or a mob hunting the shifter / the Titan) behind the Titan, within the warning range. */
	public static boolean hostileBehind(Minecraft mc, TitanFormEntity form) {
		double range = TitanShifterConfig.nape().warningRange;
		Vec3 c = form.position();
		Vec3 fwd = Vec3.directionFromRotation(0, form.getYRot());
		for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, form.getBoundingBox().inflate(range, range * 0.5, range),
				e -> e != form && e != mc.player && e.isAlive() && !e.isPassengerOfSameVehicle(form))) {
			boolean hostile = e instanceof Enemy || (e instanceof Mob m && (m.getTarget() == mc.player || m.getTarget() == form));
			if (!hostile) {
				continue;
			}
			Vec3 to = e.position().subtract(c);
			double d = Math.sqrt(to.x * to.x + to.z * to.z);
			if (d > 0.5 && d <= range && (to.x * fwd.x + to.z * fwd.z) < -0.25 * d) {
				return true;
			}
		}
		return false;
	}

	/** v0.16.7: every key, tap and Shift, for this Titan (or the human kit when {@code type} is null), in R G Z X C V order. */
	private static List<MoveListOverlay.Row> moveRows(TitanType type) {
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : type == null ? HUMAN_KEYS : VanillaHud.ORDER) {
			TitanMove tap = TitanMove.of(type, slot.number(), false);
			TitanMove shift = TitanMove.of(type, slot.number(), true);
			if (tap == null) {
				continue;
			}
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(slot), Component.translatable(tap.nameKey(type)),
					shift == null ? null : Component.translatable(shift.nameKey(type))));
		}
		if (type != null) {
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(7), Component.translatable("hud.projecthero.titan_shifter.move.grab"),
					Component.translatable("hud.projecthero.titan_shifter.move.set_down")));
		} else {
			rows.add(MoveListOverlay.Row.of("Shift+" + MoveListOverlay.key(7), Component.translatable("hud.projecthero.titan_shifter.move.regen_toggle"), null));
		}
		rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(6), Component.translatable(type == null
				? "hud.projecthero.titan_shifter.move.shift" : "hud.projecthero.titan_shifter.move.revert"), null));
		return rows;
	}
}

package com.projecthero.mod.client.gui;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.TitanUi;
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
 * The Titan Shifter HUD (bottom-right). v0.16.7 (user, Titan revamp) layout, every line fitted to the 130-px block:
 *
 * <pre>
 *   Titan form:  [emergency bar] / the Titan's name / six key boxes (tap move; a strip under each = its Shift move's
 *                recharge) / limb silhouette + nape status (flashes NAPE EXPOSED when a hostile is behind) / Steam % + bar /
 *                Titan HP + bar / "Revert Form [H]"
 *   Human form:  Titan Shifter -- the Titan's name / three Partial Shifting boxes (R G X) / Steam % + bar / the healing
 *                pool + bar / status ("Titan Shift Ready [H]", the 60 s lock, ...)
 * </pre>
 * Hold Left-Alt for the full move list (every key, tap and Shift, with names).
 */
public final class TitanShifterHud {
	private static final int BOX = TitanUi.HUD_BOX;
	private static final int GAP = TitanUi.HUD_GAP;
	private static final int MARGIN = 4;
	private static final int BAR_H = 3;
	private static final int STRIP_H = 2;
	private static final int SIL_H = 22;
	private static final int COLOR_BOX_BG = 0xC0121212;
	private static final int COLOR_BORDER = 0xFF3A3A3A;
	private static final int COLOR_COOLDOWN = 0xB0000000;
	private static final int COLOR_KEY = 0xFFD8D8D8;
	private static final int COLOR_ENERGY = 0xFFFFD53A; // v0.12.35: yellow, and it stays yellow when full
	private static final int COLOR_EMERGENCY = 0xFFB8E4FF; // v0.12.39: pale electric blue
	private static final int COLOR_POOL = 0xFF5ED36A;
	private static final int COLOR_LIMB = 0xFFD8C8B0;
	private static final int COLOR_LIMB_GONE = 0xFF7A1010;

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
		int totalW = TitanUi.HUD_W;
		int x0 = g.guiWidth() - MARGIN - totalW;
		Component key = ModKeyBindings.POWER_SELECT.getTranslatedKeyMessage();
		boolean emergency = inside && s.inEmergency();

		int height = 11 + (emergency ? BAR_H + 3 : 0);
		if (inside) {
			height += BOX + STRIP_H + 4 + SIL_H + 3 + 11 + BAR_H + 3 + 11 + BAR_H + 3 + 11;
		} else {
			height += BOX + 4 + 11 + BAR_H + 3 + 11 + BAR_H + 3 + 11 + (s.regenOff ? 11 : 0);
		}
		// v0.16.7: on a narrow screen the block would run under the hotbar / food row -- lift it clear of them
		boolean overHotbar = x0 < g.guiWidth() / 2 + 96;
		int y = g.guiHeight() - (overHotbar ? 52 : 12) - height;
		if (MoveListOverlay.held() && (inside || phase == TitanPhase.HUMAN)) {
			MoveListOverlay.draw(g, x0 + totalW, y, moveRows(inside ? type : null));
		}

		if (emergency) {
			float left = Math.max(0f, Math.min(1f, (s.emergencyUntil - now) / (float) TitanShifterConfig.emergency().durationTicks));
			thinBar(g, x0, y, totalW, left, COLOR_EMERGENCY);
			y += BAR_H + 3;
		}
		// v0.16.7: the title is the shifter's Titan ("Attack Titan", "Armoured Titan" ...)
		Component title = Component.translatable(type.nameKey());
		g.drawString(mc.font, title.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x0, y, 0xFFFFAA00, true);
		y += 11;

		if (inside) {
			for (int i = 0; i < 6; i++) {
				int x = x0 + i * (BOX + GAP);
				TitanMove tap = TitanMove.of(type, i + 1, false);
				TitanMove shift = TitanMove.of(type, i + 1, true);
				boolean active = (tap == TitanMove.REGEN && s.regenUntil > now) || (shift == TitanMove.HARDEN && s.hardenUntil > now)
						|| (shift == TitanMove.signature(type) && signatureActive(form, type))
						|| (shift == TitanMove.NAPE_GUARD && form.isNapeGuarded());
				box(g, mc, x, y, i, tap, active);
				// the Shift move's recharge: a strip under the box (amber while recharging, dim when ready)
				int scd = shift == null ? 0 : TitanShifter.cooldownRemaining(mc.player, shift.id);
				int smax = shift == null ? 0 : Math.max(1, shift.cooldown());
				g.fill(x, y + BOX + 1, x + BOX, y + BOX + 1 + STRIP_H, 0xFF2A2A2A);
				if (scd > 0) {
					g.fill(x, y + BOX + 1, x + (int) (BOX * (1f - Math.min(1f, scd / (float) smax))), y + BOX + 1 + STRIP_H, 0xFFE0A020);
				} else if (shift != null) {
					g.fill(x, y + BOX + 1, x + BOX, y + BOX + 1 + STRIP_H, 0xFF6A8A6A);
				}
			}
			y += BOX + STRIP_H + 4;

			// limb silhouette + nape status
			silhouette(g, x0 + 1, y, form);
			Component nape = napeStatus(mc, form, now);
			if (nape != null) {
				g.drawString(mc.font, nape, x0 + 20, y + 2, 0xFFFFFFFF, true);
			}
			int bits = form.severedLimbs();
			if (bits != 0) {
				g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.limbs_regrowing").withStyle(ChatFormatting.GRAY),
						x0 + 20, y + 12, 0xFFAAAAAA, true);
			}
			y += SIL_H + 3;

			steam(g, mc, x0, y, totalW, s);
			y += 11 + BAR_H + 3;

			float hp = form.getHealth();
			float max = form.getMaxHealth();
			float frac = max > 0f ? Math.max(0f, Math.min(1f, hp / max)) : 0f;
			boolean hard = s.hardenUntil > now;
			boolean regen = s.regenUntil > now;
			int fill = hard ? 0xFF5AB0FF : regen ? 0xFF40E060 : frac > 0.5f ? 0xFFE0A020 : frac > 0.25f ? 0xFFE06020 : 0xFFD02020;
			Component hpLabel = Component.translatable("hud.projecthero.titan_shifter.hp");
			g.drawString(mc.font, hpLabel, x0, y, 0xFFE8E8E8, true);
			String hpText = (int) Math.ceil(hp) + " / " + (int) Math.ceil(max);
			g.drawString(mc.font, hpText, x0 + totalW - mc.font.width(hpText), y, 0xFFFFFFFF, true);
			y += 11;
			thinBar(g, x0, y, totalW, frac, fill);
			y += BAR_H + 3;
			g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.revert", key).withStyle(ChatFormatting.YELLOW),
					x0, y, 0xFFFFFFFF, true);
			return;
		}

		// human form: the Partial Shifting keys (R G X)
		for (int i = 0; i < 3; i++) {
			box(g, mc, x0 + i * (BOX + GAP), y, i, TitanMove.of(null, i + 1, false), i == 2 && TitanShifter.cocooned(mc.player));
		}
		y += BOX + 4;
		steam(g, mc, x0, y, totalW, s);
		y += 11 + BAR_H + 3;
		// v0.16.7: the human form heals from its own pool, never from Steam
		double poolMax = TitanShifterConfig.energy().humanPoolMax;
		float pool = TitanShifter.humanPool(mc.player);
		float pFrac = (float) Math.max(0.0, Math.min(1.0, pool / Math.max(1.0, poolMax)));
		g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.pool_label"), x0, y, 0xFFE8E8E8, true);
		String poolText = (int) Math.floor(pFrac * 100f + 1.0e-3f) + "%";
		g.drawString(mc.font, poolText, x0 + totalW - mc.font.width(poolText), y, 0xFFFFFFFF, true);
		y += 11;
		thinBar(g, x0, y, totalW, pFrac, COLOR_POOL);
		y += BAR_H + 3;

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
			g.drawString(mc.font, status, x0, y, 0xFFFFFFFF, true);
		}
		if (s.regenOff) {
			// v0.12.43: Shift+N switched the passive regeneration off
			g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.regen_off").withStyle(ChatFormatting.GRAY),
					x0, y + 11, 0xFFAAAAAA, true);
		}
	}

	private static boolean signatureActive(TitanFormEntity form, TitanType type) {
		return switch (type) {
			case ARMORED_TITAN -> form.isCharging();
			case FEMALE_TITAN -> form.isCrystal();
			case BEAST_TITAN -> form.hasMoveFlag(TitanFormEntity.FLAG_HOLDING_BOULDER);
			default -> form.hasMoveFlag(TitanFormEntity.FLAG_RAGING);
		};
	}

	/** One key box: the key letter, or the seconds left with a falling shade while the move recharges. */
	private static void box(GuiGraphics g, Minecraft mc, int x, int y, int index, TitanMove move, boolean active) {
		g.fill(x, y, x + BOX, y + BOX, COLOR_BOX_BG);
		g.renderOutline(x, y, BOX, BOX, COLOR_BORDER);
		if (active) {
			g.fill(x + 1, y + 1, x + BOX - 1, y + BOX - 1, 0x8040E060);
		}
		int cd = move == null ? 0 : TitanShifter.cooldownRemaining(mc.player, move.id);
		int max = move == null ? 0 : move.cooldown();
		if (cd > 0 && max > 0) {
			int h = (int) (BOX * Math.min(1f, cd / (float) max));
			g.fill(x, y + BOX - h, x + BOX, y + BOX, COLOR_COOLDOWN);
			String t = String.valueOf((cd + 19) / 20);
			g.drawString(mc.font, t, x + (BOX - mc.font.width(t)) / 2 + 1, y + 6, 0xFFFFFFFF, true);
		} else {
			String k = MoveListOverlay.key(index);
			if (mc.font.width(k) > BOX - 2) {
				k = k.substring(0, 1);
			}
			g.drawString(mc.font, k, x + (BOX - mc.font.width(k)) / 2 + 1, y + 6, COLOR_KEY, true);
		}
	}

	private static void steam(GuiGraphics g, Minecraft mc, int x0, int y, int w, TitanShifterState s) {
		double eMax = TitanShifterConfig.energy().max;
		float eFrac = (float) Math.max(0.0, Math.min(1.0, s.energy / eMax));
		int pct = (int) Math.floor(eFrac * 100.0f + 1.0e-3f);
		g.drawString(mc.font, Component.translatable("hud.projecthero.titan_shifter.energy_label"), x0, y, 0xFFE8E8E8, true);
		String pctText = pct + "%";
		g.drawString(mc.font, pctText, x0 + w - mc.font.width(pctText), y, 0xFFFFFFFF, true);
		thinBar(g, x0, y + 11, w, eFrac, COLOR_ENERGY);
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

	/** v0.16.7: every key, tap and Shift, for this Titan (or the human kit when {@code type} is null). */
	private static java.util.List<MoveListOverlay.Row> moveRows(TitanType type) {
		java.util.List<MoveListOverlay.Row> rows = new java.util.ArrayList<>();
		int keys = type == null ? 3 : 6;
		for (int i = 0; i < keys; i++) {
			TitanMove tap = TitanMove.of(type, i + 1, false);
			TitanMove shift = TitanMove.of(type, i + 1, true);
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(i), Component.translatable(tap.nameKey(type)),
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

	private static void thinBar(GuiGraphics g, int x, int y, int w, float frac, int color) {
		g.fill(x, y, x + w, y + BAR_H, COLOR_BOX_BG);
		g.fill(x, y, x + (int) (w * frac), y + BAR_H, color);
	}
}

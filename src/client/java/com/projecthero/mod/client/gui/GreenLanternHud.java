package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.visual.MutationMeters;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The Green Lantern HUD. v0.17.3 (user: "I really like the Minecraft-style HUD and ability keybinds ... apply this to all the
 * powers"): built from the game's own pieces on the shared {@link VanillaHud.Stack}, bottom-right, stacking upward:
 * <pre>
 *   (Alt held: every key's move names)
 *   Charging / Oath 18s / Reciting / Flying / Boost  Death Save  Buzzsaw 12s ...   status tags (constructs on cooldown)
 *   Buzzsaw  35 charge                         the selected construct and its cost (or cooldown; the Sniper Rifle's state)
 *   Green Lantern                              the title
 *   TAKING OFF THE RING [Meter ===--]  60%     while Sneak + N is held (gold)
 *   DOME [Meter ======--]  80%                 the Dome's uptime meter while it is up or refills
 *   BUBBLE [Hairline =====--]  40              the Z bubble's HP (v0.15.16 Hairline) while it is up or regenerates
 *   Ring [Meter ===============|---]  87%      Ring Charge, the emergency reserve marked, blinking when low
 *   +----+----+----+----+----+----+            R G Z X C V (no H / N): the bound construct's icon sits in the slot like an
 *   | R  | G  | Z  | X  | C  | V  |            item; the tap move's cooldown sweeps it, the Shift move's is the durability
 *   +----+----+----+----+----+----+            bar; a running move gets the selection frame; the Oath counts down on X
 * </pre>
 * Drawn whenever the player has the power at all -- the ring works unsuited, so the HUD is not suit-gated. Hidden while
 * the construct wheel is open. {@link #icon} and the atlas constants stay here because the wheel draws with them.
 */
public final class GreenLanternHud {
	public static final ResourceLocation ICONS = ProjectHeroMod.id("textures/gui/green_lantern/constructs.png");
	public static final int EMBLEM_ICON = 31;

	/** v0.15.15: six keys -- the H and N boxes are gone (user rule: no H / N boxes on the HUD). */
	private static final int KEYS = 6;

	private static final int GREEN = 0xFF35F075;
	/** The selected construct's name -- a mid green that still reads over grass (v0.14.4; was the darker GREEN_DIM). */
	private static final int SELECTED = 0xFF5FD08A;
	private static final int LOW = 0xFFFF5A5A;
	private static final int GOLD = 0xFFFFD23A;
	private static final int CYAN = 0xFF35C8F0;

	/** The key letters the Construct Menu binds by, indexed by ability-slot ordinal (R G X Z V C). */
	private static final String[] KEY_LABELS = {"R", "G", "X", "Z", "V", "C"};
	/** Alt names: {@code projecthero.guide.green_lantern.ability.<key>} per box. */
	private static final String[] KEY_NAMES = {
			"ring_bolt", "construct_fist", "oath", "shield", "giant_hand", "construct" // v0.15.15: V = Giant Hand
	};
	/** v0.15.15: the cooldown ids of each key's Shift move (the slot's durability bar). */
	private static final String[][] SHIFT_COOLDOWNS = {
			{"blast_wave"}, {"war_hammer_slam"}, {"emerald_gatling"}, {"protective_dome"}, {"ring_scan"}, {"missile_barrage"}
	};
	/** Longest cooldown seen per id since it last started (the bars' full length -- some moves set different lengths). */
	private static final java.util.Map<String, Integer> PEAK = new java.util.HashMap<>();

	private GreenLanternHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || mc.level == null
				|| mc.screen instanceof GreenLanternConstructWheelScreen || mc.screen instanceof GreenLanternConstructMenuScreen) {
			return;
		}
		GreenLanternState s = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		// v0.17.1: inside the Hard-Light Mech its own HUD (GreenLanternMechHud) takes over
		if (com.projecthero.mod.greenlantern.mech.HardLightMechEntity.isPilot(player)) {
			return;
		}
		// The keys belong to Green Lantern only while no mutation is selected (GreenLanternAbilityManager.hasContext) --
		// mirror that here, or a bonded Lantern with a mutation active gets these boxes drawn on top of AbilityHud's.
		com.projecthero.mod.hero.data.ExperimentalState experimental =
				player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (experimental != null && !experimental.activePower.isEmpty()) {
			return;
		}
		long now = mc.level.getGameTime();
		float pt = delta.getGameTimeDeltaPartialTick(false);
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);

		long oathUntil = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		boolean oathActive = oathUntil > now;
		boolean oathReciting = !oathActive && player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L) > 0L;
		boolean shift = player.isShiftKeyDown();

		// ---- the keys, R G Z X C V
		VanillaHud.Stack st = VanillaHud.Stack.bottomRight(g, KEYS).keys(KEYS);
		boolean barrierUp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		for (int i = 0; i < KEYS; i++) {
			AbilitySlot slot = VanillaHud.ORDER[i];
			int k = slot.ordinal(); // the GL key index (R G X Z V C)
			int ix = st.stripX() + 3 + i * VanillaHud.SLOT;
			int iy = st.stripY() + 3;
			// v0.17.1: G / X / Z / V / C show the icon of whatever the Construct Menu bound to them -- here like an item in the slot
			if (k > 0) {
				com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry bound =
						com.projecthero.mod.greenlantern.bind.GreenLanternBinding.resolve(s, KEY_LABELS[k]);
				if (bound != null) {
					GreenLanternConstructMenuScreen.icon(g, bound, ix, iy, 16);
				}
			}
			// lit while the key's move is running (beam, Oath / Gatling, shield / dome, Giant Hand) or as its animation goes off
			boolean active = switch (k) {
				case 0 -> fx.has(GreenLanternFx.CH_BEAM);
				case 2 -> oathActive || oathReciting || fx.has(GreenLanternFx.CH_GATLING);
				case 3 -> barrierUp;
				case 4 -> fx.has(GreenLanternFx.CH_HAND); // v0.15.15: Giant Hand moved to V, the suit to H
				default -> false;
			};
			boolean flash = fx.anim() != GreenLanternFx.ANIM_NONE && now - fx.animStart() < 6 && animKey(fx.anim()) == k;
			// the shown move (the tap move, the Shift move while Shift is held) sweeps the slot; the other one is the durability bar
			float shiftFrac = 1f - shiftReady(player, k);
			int shiftCd = shiftCooldown(player, k);
			int tapCd = cooldownFor(player, k, s);
			float tapFrac = peakFrac("key/" + k, tapCd);
			if (k == 2 && oathActive && !shift) {
				// empowered: a green wash (not the cooldown sweep) with the Oath's seconds left, so it reads as a buff
				g.fill(RenderType.guiOverlay(), ix, iy, ix + 16, iy + 16, 0x8010A040);
				st.slot(i, VanillaHud.keyLabel(slot), true, false, 0f, VanillaHud.seconds((int) Math.max(0L, oathUntil - now)), shiftFrac, true);
			} else if (shift) {
				st.slot(i, VanillaHud.keyLabel(slot), true, true, shiftFrac, VanillaHud.seconds(shiftCd), tapFrac, active || flash);
			} else {
				st.slot(i, VanillaHud.keyLabel(slot), true, false, tapFrac, VanillaHud.seconds(tapCd), shiftFrac, active || flash);
			}
		}

		// ---- Ring Charge: blinking faster the lower it gets, the emergency reserve marked
		float maxCharge = com.projecthero.mod.greenlantern.GreenLanternRanks.maxCharge(player); // v0.17.1: grows with rank
		float frac = Mth.clamp(s.ringCharge, 0f, maxCharge) / maxCharge;
		int severity = com.projecthero.mod.greenlantern.GreenLanternEnergy.severityTier(frac);
		boolean low = severity > 0;
		int pulsePeriod = Math.max(3, 16 - severity * 2);
		boolean pulseOff = low && (now % pulsePeriod) < Math.max(1, pulsePeriod / 3);
		String pct = Math.round(frac * 100.0f) + "%";
		Component ringLabel = Component.translatable("hud.projecthero.vanilla_hud.ring");
		st.meter(ringLabel, pulseOff ? 0f : frac, low ? LOW : GREEN, pct, low ? LOW : 0xFFA8E6B8);
		reserveMark(st, ringLabel, pct, GreenLanternConfig.EMERGENCY_RESERVE / maxCharge);

		// ---- the Z bubble's HP, the Dome's uptime, the ring coming off
		shieldHp(st, player, now);
		barrier(st, player);
		ringRemoval(st, fx, now, pt);

		// ---- the title, the selected construct, then what is running
		st.title(Component.translatable("projecthero.guide.green_lantern"), GREEN);
		// v0.16.1: while the Sniper Rifle is out this row is the rifle instead (READY / CHARGING n% / FULL CHARGE / seconds)
		List<VanillaHud.Tag> selected = new ArrayList<>();
		VanillaHud.Tag sniper = sniperStatus(player, pt);
		ConstructType sel = sniper != null ? ConstructType.SNIPER_RIFLE : ConstructType.byOrdinal(s.selectedConstruct);
		selected.add(new VanillaHud.Tag(Component.translatable(sel.translationKey()).getString(), SELECTED));
		if (sniper != null) {
			selected.add(sniper);
		} else {
			int selCd = GreenLanternConstructs.cooldownRemainingFor(player, sel);
			selected.add(selCd > 0
					? new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.cooldown_short", (selCd + 19) / 20).getString(), VanillaHud.RED)
					: new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.cost", Math.round(sel.initialCost())).getString(), VanillaHud.GREY));
		}
		st.tags(selected);

		List<VanillaHud.Tag> tags = new ArrayList<>();
		VanillaHud.Tag status = status(player, oathActive, oathReciting, oathUntil, now);
		if (status != null) {
			tags.add(status);
		}
		VanillaHud.Tag save = deathSave(player, s);
		if (save != null) {
			tags.add(save);
		}
		// one tag per construct on its post-use cooldown, with the seconds left
		for (ConstructType type : ConstructType.values()) {
			int cd = GreenLanternConstructs.cooldownRemainingFor(player, type);
			if (cd > 0) {
				tags.add(new VanillaHud.Tag(Component.translatable(type.translationKey()).getString() + " " + (cd + 19) / 20 + "s", VanillaHud.GREY));
			}
		}
		st.tags(tags);

		if (Screen.hasAltDown() || MoveListOverlay.held()) {
			renderAltNames(g, st.x0 + st.width, st.top() - 4, s);
		}
	}

	/** A 1-px gold mark across the last meter row's bar at {@code at} (0..1 of the bar), as {@link VanillaHud#meter} lays it out. */
	private static void reserveMark(VanillaHud.Stack st, Component label, String value, float at) {
		int by = st.top() + 2;
		int labelW = st.font.width(label);
		int valueW = Math.max(st.font.width("0:00"), st.font.width(value));
		int barX = st.x0 + labelW + 4;
		int barW = Math.max(20, st.width - labelW - 4 - valueW - 3);
		int x = barX + Math.round(barW * Mth.clamp(at, 0f, 1f));
		st.g.fill(x, by - 2, x + 1, by + 6, 0xFFFFDD33);
	}

	/**
	 * v0.17.1 Ring Auto-Protection: the Death Save's state as a status word -- green when it is ready, grey with the seconds left
	 * while it cools down. Hidden while the Death Save (or the whole Auto-Protection) is switched off in the suit screen.
	 */
	private static VanillaHud.Tag deathSave(Player player, GreenLanternState s) {
		int off = s.autoProtectOff;
		if ((off & com.projecthero.mod.greenlantern.GreenLanternAutoProtect.MASTER) != 0
				|| (off & com.projecthero.mod.greenlantern.GreenLanternAutoProtect.DEATH_SAVE) != 0) {
			return null;
		}
		int cd = com.projecthero.mod.greenlantern.GreenLantern.cooldownRemaining(player,
				com.projecthero.mod.greenlantern.GreenLanternAutoProtect.DEATH_SAVE_COOLDOWN);
		return cd > 0
				? new VanillaHud.Tag(Component.translatable("hud.projecthero.vanilla_hud.gl.death_save_cd", (cd + 19) / 20).getString(), VanillaHud.GREY)
				: new VanillaHud.Tag(Component.translatable("hud.projecthero.vanilla_hud.gl.death_save").getString(), GREEN);
	}

	/** What is running right now (most important first), or null. */
	private static VanillaHud.Tag status(Player player, boolean oathActive, boolean oathReciting, long oathUntil, long now) {
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY).has(GreenLanternFx.CH_CHARGE)) {
			return new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.tag.charging").getString(), VanillaHud.GREEN);
		}
		if (oathActive) {
			return new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.tag.oath", (int) Math.ceil((oathUntil - now) / 20.0)).getString(),
					VanillaHud.GOLD);
		}
		if (oathReciting) {
			return new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.tag.reciting").getString(), VanillaHud.YELLOW);
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)) {
			return new VanillaHud.Tag(Component.translatable(player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BOOSTING, false)
					? "hud.projecthero.green_lantern.tag.boost" : "hud.projecthero.green_lantern.tag.flying").getString(), 0xFF55FFFF);
		}
		return null;
	}

	/** Which key (GL index, R G X Z V C) a move animation belongs to (so the slot lights as the move goes off). */
	private static int animKey(int anim) {
		return switch (anim) {
			case GreenLanternFx.ANIM_BOLT, GreenLanternFx.ANIM_BLAST -> 0;
			case GreenLanternFx.ANIM_FIST, GreenLanternFx.ANIM_HAMMER -> 1;
			case GreenLanternFx.ANIM_OATH -> 2;
			case GreenLanternFx.ANIM_DOME -> 3;
			case GreenLanternFx.ANIM_SCAN -> 4;
			case GreenLanternFx.ANIM_MISSILES, GreenLanternFx.ANIM_CONSTRUCT -> 5;
			case GreenLanternFx.ANIM_GRAB, GreenLanternFx.ANIM_THROW -> 4;
			default -> -1;
		};
	}

	/** Draws cell {@code index} of the construct icon atlas at {@code size} px (used by the construct wheel). */
	public static void icon(GuiGraphics g, int index, int x, int y, int size) {
		RenderSystem.enableBlend();
		g.blit(ICONS, x, y, size, size, (index % 8) * 16f, (index / 8) * 16f, 16, 16, 128, 64);
		RenderSystem.disableBlend();
	}

	/** Sneak + hold N: a gold row filling toward the ring coming off. */
	private static void ringRemoval(VanillaHud.Stack st, GreenLanternFx fx, long now, float pt) {
		if (!fx.has(GreenLanternFx.CH_RING_REMOVE) || fx.ringRemoveStart() == 0L) {
			return;
		}
		float f = Mth.clamp((now - fx.ringRemoveStart() + pt) / GreenLanternConfig.RING_REMOVE_HOLD_TICKS, 0f, 1f);
		st.meter(Component.translatable("hud.projecthero.green_lantern.ring_remove"), f, GOLD, Math.round(f * 100) + "%", GOLD);
	}

	/**
	 * v0.16.1: while the Sniper Rifle construct is out, its state -- the seconds until the next shot, CHARGING n% / FULL CHARGE
	 * while a shot charges, else READY. Null when the rifle is not out.
	 */
	private static VanillaHud.Tag sniperStatus(Player player, float pt) {
		if (!com.projecthero.mod.client.greenlantern.GreenLanternSniperClient.isOut(player)) {
			return null;
		}
		int cd = GreenLantern.cooldownRemaining(player, com.projecthero.mod.greenlantern.GreenLanternSniper.COOLDOWN_ID);
		float charge = com.projecthero.mod.client.greenlantern.GreenLanternSniperClient.chargeProgress(pt);
		if (cd > 0) {
			return new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.sniper.cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20f)).getString(), VanillaHud.RED);
		}
		if (charge >= 0f) {
			return new VanillaHud.Tag(Component.translatable(charge >= 1f ? "hud.projecthero.green_lantern.sniper.charged"
					: "hud.projecthero.green_lantern.sniper.charging", Math.round(charge * 100f)).getString(), VanillaHud.WHITE);
		}
		return new VanillaHud.Tag(Component.translatable("hud.projecthero.green_lantern.sniper.ready").getString(), VanillaHud.GREEN);
	}

	private static float shieldShown = -1f;
	private static long shieldHitAt = -100L;

	/**
	 * v0.15.16: the Z bubble's HP as a Hairline row. Shown while the bubble is up and while it regenerates, hidden once it is full
	 * again; a hit flashes it white.
	 */
	private static void shieldHp(VanillaHud.Stack st, Player player, long now) {
		float max = GreenLanternConfig.SHIELD_HP;
		float hp = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_SHIELD_HP, max);
		boolean up = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f
				&& !player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		if (shieldShown < 0f) {
			shieldShown = hp;
		}
		if (hp < shieldShown - 0.01f) {
			shieldHitAt = now;
		}
		shieldShown = hp < shieldShown ? Math.max(hp, shieldShown - max * 0.01f) : hp;
		if (!up && hp >= max - 0.01f) {
			return;
		}
		boolean flash = now - shieldHitAt < 6;
		int fill = flash ? 0xFFFFFFFF : !up ? 0xFF1E7A94 : hp / max > 0.5f ? CYAN : hp / max > 0.25f ? 0xFFFFC040 : LOW;
		st.meter(Component.translatable("hud.projecthero.green_lantern.shield"), hp / max, fill, String.valueOf(Math.round(hp)),
				flash ? VanillaHud.WHITE : 0xFF8FE0F8, MutationMeters.Style.HAIRLINE);
	}

	/**
	 * The Dome's uptime meter (v0.11.8 semantics: time in use, not damage taken), shown while the Dome is up or the meter is
	 * refilling, hidden once neither is true.
	 */
	private static void barrier(VanillaHud.Stack st, Player player) {
		boolean dome = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		// v0.15.16: the Z bubble has its own HP row (shieldHp); this uptime meter belongs to the Dome alone
		boolean active = dome && player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
		float meter = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
		if (!active && meter >= 1f) {
			return;
		}
		Component label = Component.translatable(active ? "hud.projecthero.green_lantern.dome" : "hud.projecthero.green_lantern.barrier");
		st.meter(label, meter, active ? CYAN : 0xFF1E7A94, Math.round(meter * 100f) + "%", 0xFF8FE0F8);
	}

	/**
	 * Alt held (v0.16.6: the shared move list): every key's moves, R G Z X C V, the Shift move in its own column, stacked above
	 * the topmost HUD line.
	 */
	private static void renderAltNames(GuiGraphics g, int right, int top, GreenLanternState s) {
		List<MoveListOverlay.Row> rows = new ArrayList<>();
		for (AbilitySlot slot : VanillaHud.ORDER) {
			int i = slot.ordinal();
			if (i == 0) {
				// R is the ring itself (Ring Bolt / hold for the beam, Shift = Blast Wave) -- not bindable
				rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(i), entryName(com.projecthero.mod.greenlantern.bind.GreenLanternBinding.RING_BOLT,
						"projecthero.guide.green_lantern.ability." + KEY_NAMES[0]),
						entryName(com.projecthero.mod.greenlantern.bind.GreenLanternBinding.BLAST_WAVE, null)));
				continue;
			}
			// v0.17.1 (user: "make it change based on the constructs and whatever is binded"): what the Construct Menu put on
			// this key and its Shift version, not the old fixed defaults
			rows.add(MoveListOverlay.Row.of(MoveListOverlay.key(i), boundName(s, KEY_LABELS[i]), boundName(s, "S" + KEY_LABELS[i]),
					i == 5 ? Component.translatable("hud.projecthero.green_lantern.alt.hold_menu") : null));
		}
		MoveListOverlay.draw(g, right, top, rows);
	}

	private static Component boundName(GreenLanternState s, String key) {
		com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry e =
				s == null ? null : com.projecthero.mod.greenlantern.bind.GreenLanternBinding.resolve(s, key);
		return e == null ? Component.translatable("screen.projecthero.power_info.gl.empty") : Component.translatable(e.nameKey());
	}

	private static Component entryName(String id, String fallback) {
		com.projecthero.mod.greenlantern.bind.GreenLanternBinding.Entry e = com.projecthero.mod.greenlantern.bind.GreenLanternBinding.byId(id);
		return e != null ? Component.translatable(e.nameKey()) : fallback == null ? null : Component.translatable(fallback);
	}

	private static int cooldownFor(Player player, int slot, GreenLanternState s) {
		return switch (slot) {
			case 0 -> Math.max(GreenLantern.cooldownRemaining(player, "ring_bolt"),
					GreenLantern.cooldownRemaining(player, "continuous_beam"));
			// v0.15.15: the slot shows the tap move; each Shift move is its durability bar (shiftReady)
			case 1 -> GreenLantern.cooldownRemaining(player, "construct_fist");
			case 2 -> GreenLantern.cooldownRemaining(player, "oath_mode");
			case 3 -> GreenLantern.cooldownRemaining(player, "directional_shield");
			case 4 -> GreenLantern.cooldownRemaining(player, "giant_hand"); // v0.15.15: V = Giant Hand (its Shift move is the bar)
			case 5 -> GreenLanternConstructs.cooldownRemainingFor(player, ConstructType.byOrdinal(s.selectedConstruct));
			default -> 0;
		};
	}

	/** 0..1 of a cooldown left, against the longest seen since it started ({@link #PEAK}: the moves set different lengths). */
	private static float peakFrac(String id, int cd) {
		if (cd <= 0) {
			PEAK.remove(id);
			return 0f;
		}
		int peak = Math.max(cd, PEAK.getOrDefault(id, 0));
		PEAK.put(id, peak);
		return cd / (float) peak;
	}

	/** Ticks left on key {@code slot}'s Shift move. */
	private static int shiftCooldown(Player player, int slot) {
		int cd = 0;
		for (String c : SHIFT_COOLDOWNS[slot]) {
			cd = Math.max(cd, GreenLantern.cooldownRemaining(player, c));
		}
		return cd;
	}

	/** v0.15.15: how far key {@code slot}'s Shift move has cooled down, 0..1 (1 = ready). */
	private static float shiftReady(Player player, int slot) {
		int cd = 0;
		String id = null;
		for (String c : SHIFT_COOLDOWNS[slot]) {
			int r = GreenLantern.cooldownRemaining(player, c);
			if (r > cd) {
				cd = r;
				id = c;
			}
		}
		if (id == null) {
			for (String c : SHIFT_COOLDOWNS[slot]) {
				PEAK.remove(c);
			}
			return 1f;
		}
		int peak = Math.max(cd, PEAK.getOrDefault(id, 0));
		PEAK.put(id, peak);
		return 1f - cd / (float) Math.max(1, peak);
	}
}

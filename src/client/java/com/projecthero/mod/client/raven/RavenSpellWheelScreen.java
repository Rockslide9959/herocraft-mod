package com.projecthero.mod.client.raven;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenSpell;
import com.projecthero.mod.raven.RavenSpells;
import com.projecthero.mod.raven.network.RavenActionPayload;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Raven revamp: the <b>Spell Wheel</b>. Hold V (a tap casts the picked spell instead, {@code RavenClient}): the ten spells
 * round a black ring in Azarath violet (the power wheel's ring geometry, {@link PowerWheelScreen#band}), combat spells on the right
 * half, utility on the left. Each slice shows only the spell's icon and name (wrapped to the slice) with a cooldown sweep; the one
 * pointed at (or the current pick) gets its name, Dark Energy cost, cooldown and a few words in the centre circle, and its full
 * description in a wrapped panel beside or under the wheel (whichever leaves the bigger wheel), sized so nothing is ever clipped
 * and nothing overlaps the ring. The HUD is put away while it's open, like the I menus. Point and let go of V to pick (a click
 * picks too); let go over the centre, or Escape, to keep the old pick. The game keeps running.
 */
public final class RavenSpellWheelScreen extends Screen {
	public static final ResourceLocation ICONS = ProjectHeroMod.id("textures/gui/raven/spells.png");
	private static final int DIM_TOP = 0x80000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF00C0812;
	private static final int SEG_FILL_ALT = 0xF0120C1A;
	private static final int SEG_FILL_HOVER = 0xF83A1E5C;
	private static final int SEG_FILL_UTILITY = 0xF00A0C14;
	private static final int TRACK = 0xD0040306;
	private static final int VIOLET = 0xFFA66CF0;
	private static final int VIOLET_DIM = 0xFF4A2A72;
	private static final int CENTRE_FILL = 0xF0060409;
	private static final int PANEL = 0xC8080510;
	private static final int TEXT = 0xFFE4D8FF;
	private static final int TEXT_DIM = 0xFF9A8CB4;
	private static final int COST = 0xFFB98CF0;
	private static final int COOL = 0xFFFF8A8A;
	private static final int DEAD_ZONE = 22;
	private static final float OPEN_TICKS = 3f;
	/** Screen margin, and the gap between the wheel and the description panel. */
	private static final int MARGIN = 6;
	private static final int PAD = 6;
	private static final float HINT_SCALE = 0.75f;
	private static final float MAX_OUTER = 140f;

	private final RavenSpell[] spells = RavenSpell.values();
	private int hovered = -1;
	private float[] hoverAnim = new float[0];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	private boolean held = false;
	private boolean picked = false;
	/** The F1 state from before the wheel opened (null = not hidden by the wheel). */
	private Boolean hudWas;
	private Layout layout;
	/** Set only by the client screenshot harness: the wheel ignores the V key (stays open until closed). */
	public static volatile boolean stayOpen;
	/** Set only by the client screenshot harness: hover this slice (-1 = the mouse decides). */
	public static volatile int forceHover = -1;

	/**
	 * Where everything goes for this screen size: the wheel's centre and outer radius, and the description panel (its box, the
	 * wrap width and scale of the description, and the hint lines at its foot). Worked out once per size from the longest
	 * description, so the wheel never jumps while you point around.
	 */
	private record Layout(float cx, float cy, float outer, int px, int py, int pw, int ph, float descScale, int descWrap,
			List<FormattedCharSequence> hint) {
	}

	public RavenSpellWheelScreen() {
		super(Component.translatable("screen.projecthero.raven.wheel"));
	}

	private static InputConstants.Key key() {
		return KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()]);
	}

	@Override
	protected void init() {
		layout = null;
		// the HUD (hotbar, the Raven corner HUD) is put away like F1 does while the wheel is open, so nothing runs under it
		if (hudWas == null && this.minecraft != null) {
			hudWas = this.minecraft.options.hideGui;
			this.minecraft.options.hideGui = true;
		}
	}

	@Override
	public void removed() {
		if (hudWas != null) {
			Minecraft.getInstance().options.hideGui = hudWas;
			hudWas = null;
		}
		super.removed();
	}

	@Override
	public void tick() {
		ticksOpen++;
		Minecraft mc = Minecraft.getInstance();
		InputConstants.Key key = key();
		if (!stayOpen && key.getType() == InputConstants.Type.KEYSYM) {
			if (InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue())) {
				held = true;
			} else if (held || ticksOpen > 2) {
				pickAndClose();
			}
		}
		if (mc.player == null || !Raven.hasPower(mc.player) || !mc.player.isAlive()) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (!stayOpen && ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].matches(keyCode, scanCode)) {
			pickAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		pickAndClose();
		return true;
	}

	/** The pointed-at spell becomes the pick (nothing is sent over the centre or for the same spell). */
	private void pickAndClose() {
		if (picked) {
			return;
		}
		picked = true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && hovered >= 0 && hovered < spells.length && spells[hovered] != Raven.selectedSpell(mc.player)) {
			ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.PICK_SPELL, hovered));
			mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.7f);
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float open = openEase(partialTick);
		g.fillGradient(0, 0, this.width, this.height, fade(DIM_TOP, open), fade(DIM_BOTTOM, open));
	}

	// ------------------------------------------------------------------ layout

	private static float liftFor(float outer) {
		return Math.max(4f, outer * 0.05f);
	}

	/** How far the ring reaches past the centre, a hovered (lifted) slice and the rim included. */
	private static float reach(float outer) {
		return outer + liftFor(outer) * 1.5f + 3f;
	}

	/** The biggest outer radius whose ring reaches no further than {@code r}. */
	private static float outerFor(float r) {
		return Math.max(40f, Math.min(MAX_OUTER, Math.min((r - 3f) / 1.075f, r - 9f)));
	}

	/** The most lines any spell's description needs at this wrap width. */
	private int maxDescLines(int wrap) {
		int most = 1;
		for (RavenSpell sp : spells) {
			most = Math.max(most, this.font.split(Component.translatable(sp.langKey() + ".desc"), wrap).size());
		}
		return most;
	}

	private static int headH() {
		return 13;
	}

	private static int hintH(int lines) {
		return Math.round(lines * 10 * HINT_SCALE);
	}

	/** Panel height for a panel {@code innerW} wide whose description is drawn at {@code ds}. */
	private int panelHeight(int innerW, float ds, int hintLines) {
		int lines = maxDescLines(Math.max(20, (int) Math.floor(innerW / ds)));
		return PAD + headH() + (int) Math.ceil(lines * 10 * ds) + 8 + hintH(hintLines) + PAD - 2;
	}

	/** The biggest description scale (1 down to 0.4) whose panel fits in {@code maxH}; the smallest if none does. */
	private float fitScale(int innerW, int maxH, int hintLines) {
		for (float ds = 1f; ds >= 0.4f - 1e-4f; ds -= 0.05f) {
			if (panelHeight(innerW, ds, hintLines) <= maxH) {
				return ds;
			}
		}
		return 0.4f;
	}

	private List<FormattedCharSequence> hintLines(int innerW) {
		return this.font.split(Component.translatable("screen.projecthero.raven.wheel.hint"), Math.max(20, (int) Math.floor(innerW / HINT_SCALE)));
	}

	/**
	 * Two candidates: the panel beside the wheel (right of it), or under it. Each is sized so the whole description fits; the one
	 * that leaves the bigger wheel wins (under, on a tie).
	 */
	private Layout layout() {
		if (layout != null) {
			return layout;
		}
		int w = this.width, h = this.height;
		// --- beside
		float outerS = outerFor((h - 2f * MARGIN) / 2f);
		int pwS = Math.min(260, Math.round(w - 2 * reach(outerS) - 3 * MARGIN));
		if (pwS < 150) {
			pwS = Math.min(150, w - 3 * MARGIN - 80);
			outerS = outerFor((w - 3f * MARGIN - pwS) / 2f);
		}
		List<FormattedCharSequence> hintS = hintLines(pwS - 2 * PAD);
		float dsS = fitScale(pwS - 2 * PAD, h - 2 * MARGIN, hintS.size());
		int phS = panelHeight(pwS - 2 * PAD, dsS, hintS.size());
		// --- under: the panel may take up to half the height, the description shrinking a little before it gets taller
		int pwB = Math.min(340, w - 2 * MARGIN);
		List<FormattedCharSequence> hintB = hintLines(pwB - 2 * PAD);
		float dsB = fitScale(pwB - 2 * PAD, Math.round(h * 0.42f), hintB.size());
		int phB = panelHeight(pwB - 2 * PAD, dsB, hintB.size());
		float outerB = outerFor(Math.min(w / 2f - MARGIN, (h - 3f * MARGIN - phB) / 2f));

		if (outerS > outerB + 2f && pwS >= 120) {
			float span = 2 * reach(outerS);
			float groupW = span + MARGIN + pwS;
			float left = Math.max(MARGIN, (w - groupW) / 2f);
			float cx = left + span / 2f;
			float cy = h / 2f;
			int px = Math.round(left + span + MARGIN);
			int py = Mth.clamp(Math.round(cy - phS / 2f), MARGIN, Math.max(MARGIN, h - MARGIN - phS));
			layout = new Layout(cx, cy, outerS, px, py, pwS, phS, dsS, Math.max(20, (int) Math.floor((pwS - 2 * PAD) / dsS)), hintS);
		} else {
			float span = 2 * reach(outerB);
			float groupH = span + MARGIN + phB;
			float top = Math.max(MARGIN, (h - groupH) / 2f);
			float cx = w / 2f;
			float cy = top + span / 2f;
			int py = Math.round(top + span + MARGIN);
			int px = Math.round(cx - pwB / 2f);
			layout = new Layout(cx, cy, outerB, px, py, pwB, phB, dsB, Math.max(20, (int) Math.floor((pwB - 2 * PAD) / dsB)), hintB);
		}
		return layout;
	}

	// ------------------------------------------------------------------ text helpers

	/**
	 * Wraps {@code text} to {@code maxW} at the biggest scale from {@code maxScale} down to {@code minScale} that needs no more than
	 * {@code maxLines} lines with every line inside {@code maxW}. Writes the scale into {@code scaleOut[0]}.
	 */
	private List<FormattedCharSequence> fit(Component text, float maxW, float maxScale, float minScale, int maxLines, float[] scaleOut) {
		for (float sc = maxScale; sc >= minScale - 1e-4f; sc -= 0.05f) {
			List<FormattedCharSequence> ls = this.font.split(text, Math.max(8, (int) Math.floor(maxW / sc)));
			if (ls.size() <= maxLines && widest(ls) * sc <= maxW + 0.5f && wordsFit(text, maxW, sc)) {
				scaleOut[0] = sc;
				return ls;
			}
		}
		scaleOut[0] = minScale;
		return this.font.split(text, Math.max(8, (int) Math.floor(maxW / minScale)));
	}

	private int widest(List<FormattedCharSequence> ls) {
		int w = 0;
		for (FormattedCharSequence l : ls) {
			w = Math.max(w, this.font.width(l));
		}
		return w;
	}

	/** No single word wider than {@code maxW} at this scale (the font's wrapper would break it mid-word). */
	private boolean wordsFit(Component text, float maxW, float sc) {
		for (String word : text.getString().split("\\s+")) {
			if (this.font.width(word) * sc > maxW + 0.5f) {
				return false;
			}
		}
		return true;
	}

	private static float linesH(int n, float sc) {
		return n <= 0 ? 0f : (n * 10f - 1f) * sc;
	}

	/** Draws the lines centred on {@code x} from {@code y}; returns their height. */
	private float drawCentred(GuiGraphics g, List<FormattedCharSequence> ls, float sc, float x, float y, int color, boolean shadow) {
		for (int i = 0; i < ls.size(); i++) {
			FormattedCharSequence line = ls.get(i);
			g.pose().pushPose();
			g.pose().translate(x, y + i * 10f * sc, 0);
			g.pose().scale(sc, sc, 1f);
			g.drawString(this.font, line, Math.round(-this.font.width(line) / 2f), 0, color, shadow);
			g.pose().popPose();
		}
		return linesH(ls.size(), sc);
	}

	// ------------------------------------------------------------------ render

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Layout lay = layout();
		int n = spells.length;
		float open = openEase(partialTick);
		float outer = lay.outer();
		float inner = outer * 0.44f;
		float lift = liftFor(outer);
		float cx = lay.cx(), cy = lay.cy();
		RavenSpell cur = Raven.selectedSpell(mc.player);

		hovered = -1;
		if (Math.hypot(mouseX - cx, mouseY - cy) > DEAD_ZONE) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}
		if (forceHover >= 0 && forceHover < n) {
			hovered = forceHover;
		}
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = now;
		if (hoverAnim.length != n) {
			hoverAnim = new float[n];
		}
		float k = 1f - (float) Math.exp(-dt * 18f);
		for (int i = 0; i < n; i++) {
			hoverAnim[i] += ((i == hovered ? 1f : 0f) - hoverAnim[i]) * k;
		}

		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		float s = 0.82f + 0.18f * open;
		g.pose().scale(s, s, 1f);
		g.pose().translate(-cx, -cy, 0);
		double seg = Math.PI * 2 / n;
		float gap = 2.0f;
		float disc = inner - 5f;
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(VIOLET_DIM, open));
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			RavenSpell sp = spells[j];
			int cd = Raven.cooldownRemaining(mc.player, sp.id);
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int base = sp.combat ? (j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT) : (j % 2 == 0 ? SEG_FILL_UTILITY : SEG_FILL_ALT);
			int fill = lerp(base, cd > 0 ? 0xF8302040 : SEG_FILL_HOVER, he);
			int edge = lerp(sp == cur ? VIOLET : VIOLET_DIM, 0xFFFFFFFF, he);
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			if (cd > 0) {
				// cooldown shading: a dark sweep from the inner edge outwards, shrinking as the spell comes back
				float frac = Mth.clamp(cd / (float) Math.max(1, sp.cooldown), 0.08f, 1f);
				PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + (r1 - r0) * frac, e0, e1, gap, fade(0xA0000000, open));
			}
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (sp == cur) {
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(VIOLET, open));
			}
		}
		boolean centre = hovered < 0;
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(centre ? 0xF01A1028 : CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(VIOLET_DIM, open));
		g.flush();

		// each slice: just the icon and the name, wrapped to the slice (the details go in the middle)
		float[] sc = new float[1];
		float iconBase = outer >= 120f ? 1.25f : outer >= 85f ? 1.0f : 0.8f;
		float nameMax = outer >= 125f ? 1.0f : 0.85f;
		int alpha = Math.round(255 * open);
		// one name size for every slice (the size the longest name needs at rest), so the ring reads evenly
		// (the stack sits a little outward of the middle of the ring, where the slice is wider; widths are taken a little
		// further in, where a top slice's name sits)
		float restR = inner + (outer - inner) * 0.56f;
		float restW = Math.min((restR - 10f) * (float) seg - gap * 2f, outer - inner) - 6f;
		float nameS = nameMax;
		for (RavenSpell sp : spells) {
			fit(Component.translatable(sp.langKey()), restW, nameMax, 0.5f, 2, sc);
			nameS = Math.min(nameS, sc[0]);
		}
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			RavenSpell sp = spells[j];
			double mid = -Math.PI / 2 + j * seg;
			float r = restR + lift * he;
			float fx = cx + (float) Math.cos(mid) * r, fy = cy + (float) Math.sin(mid) * r;
			// the narrower of the slice's width at this radius and its depth, so the text stays inside at any angle
			float maxW = Math.min((r - 10f) * (float) seg - gap * 2f, outer - inner) - 6f;
			int cd = Raven.cooldownRemaining(mc.player, sp.id);
			float iconScale = iconBase * (1f + 0.08f * he);
			List<FormattedCharSequence> name = this.font.split(Component.translatable(sp.langKey()), Math.max(8, (int) Math.floor(maxW / nameS)));
			float iconH = 16f * iconScale;
			float total = iconH + 2f + linesH(name.size(), nameS);
			float top = fy - total / 2f;
			g.pose().pushPose();
			g.pose().translate(fx, top + iconH / 2f, 0);
			g.pose().scale(iconScale, iconScale, 1f);
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			g.setColor(cd > 0 ? 0.5f : 1f, cd > 0 ? 0.5f : 1f, cd > 0 ? 0.55f : 1f, alpha / 255f);
			g.blit(ICONS, -8, -8, sp.ordinal() * 16, 0, 16, 16, 16 * RavenSpell.count(), 16);
			g.setColor(1f, 1f, 1f, 1f);
			if (cd > 0) {
				// seconds left in the icon's corner, like an item count
				String secs = ((cd + 19) / 20) + "s";
				g.pose().pushPose();
				g.pose().translate(8f, 8f, 0);
				g.pose().scale(0.6f, 0.6f, 1f);
				g.drawString(this.font, secs, -this.font.width(secs), -8, fade(COOL, open), true);
				g.pose().popPose();
			}
			g.pose().popPose();
			int col = sp == cur ? 0xFFD8B8FF : cd > 0 ? TEXT_DIM : TEXT;
			drawCentred(g, name, nameS, fx, top + iconH + 2f, fade(lerp(col, 0xFFFFFFFF, he), open), true);
		}

		// the centre: the pointed-at spell (or the current pick): its name, cost, cooldown and a few words
		RavenSpell shown = hovered >= 0 ? spells[hovered] : cur;
		int cd = Raven.cooldownRemaining(mc.player, shown.id);
		float cw = disc * 1.5f;
		List<FormattedCharSequence> tag = centre ? fit(Component.translatable("screen.projecthero.raven.wheel.current"), cw, 0.65f, 0.5f, 1, sc) : List.of();
		float tagS = sc[0];
		List<FormattedCharSequence> nm = fit(Component.translatable(shown.langKey()), cw, 1f, 0.55f, 2, sc);
		float nmS = sc[0];
		List<FormattedCharSequence> cost = fit(Component.translatable("screen.projecthero.raven.wheel.cost", Math.round(RavenSpells.costNow(mc.player, shown))),
				cw, 0.7f, 0.45f, 1, sc);
		float costS = sc[0];
		Component cdText = cd > 0 ? Component.translatable("screen.projecthero.raven.wheel.cooling", (cd + 19) / 20)
				: Component.translatable("screen.projecthero.raven.wheel.cooldown", shown.cooldown / 20);
		List<FormattedCharSequence> cool = fit(cdText, cw, 0.7f, 0.45f, 1, sc);
		float coolS = sc[0];
		List<FormattedCharSequence> few = fit(Component.translatable(shown.langKey() + ".short"), cw, 0.7f, 0.45f, 2, sc);
		float fewS = sc[0];
		float stack = linesH(tag.size(), tagS) + (tag.isEmpty() ? 0f : 2f) + linesH(nm.size(), nmS) + 3f + linesH(1, costS) + 1f
				+ linesH(1, coolS) + 3f + linesH(few.size(), fewS);
		if (stack > disc * 1.45f) {
			// too tall for a small centre: the few words go (the full description is in the panel anyway)
			stack -= 3f + linesH(few.size(), fewS);
			few = List.of();
		}
		float y = cy - stack / 2f;
		if (!tag.isEmpty()) {
			y += drawCentred(g, tag, tagS, cx, y, fade(TEXT_DIM, open), false) + 2f;
		}
		y += drawCentred(g, nm, nmS, cx, y, fade(VIOLET, open), true) + 3f;
		y += drawCentred(g, cost, costS, cx, y, fade(COST, open), false) + 1f;
		y += drawCentred(g, cool, coolS, cx, y, fade(cd > 0 ? COOL : TEXT_DIM, open), false) + 3f;
		if (!few.isEmpty()) {
			drawCentred(g, few, fewS, cx, y, fade(TEXT, open), false);
		}
		g.pose().popPose();

		renderPanel(g, lay, shown, open);
	}

	/** The description panel: the spell's name and group, its full description (wrapped, never clipped) and the hint at the foot. */
	private void renderPanel(GuiGraphics g, Layout lay, RavenSpell shown, float open) {
		int px = lay.px(), py = lay.py(), pw = lay.pw();
		int innerW = pw - 2 * PAD;
		float ds = lay.descScale();
		List<FormattedCharSequence> desc = this.font.split(Component.translatable(shown.langKey() + ".desc"), lay.descWrap());
		List<FormattedCharSequence> hint = lay.hint();
		// the box hugs this spell's description (its top stays put; the layout reserved room for the longest one)
		int ph = Math.min(lay.ph(), PAD + headH() + (int) Math.ceil(desc.size() * 10 * ds) + 8 + hintH(hint.size()) + PAD - 2);
		g.fill(px, py, px + pw, py + ph, fade(PANEL, open));
		g.renderOutline(px, py, pw, ph, fade(VIOLET_DIM, open));
		int x0 = px + PAD;
		int y = py + PAD;
		// heading: the name, and the group on the right when there's room for both
		Component name = Component.translatable(shown.langKey());
		Component group = Component.translatable(shown.combat ? "screen.projecthero.raven.wheel.combat" : "screen.projecthero.raven.wheel.utility");
		int nameW = this.font.width(name);
		float groupW = this.font.width(group) * HINT_SCALE;
		boolean showGroup = nameW + groupW + 8 <= innerW;
		float ns = Math.min(1f, innerW / (float) Math.max(1, nameW));
		g.pose().pushPose();
		g.pose().translate(x0, y, 0);
		g.pose().scale(ns, ns, 1f);
		g.drawString(this.font, name, 0, 0, fade(VIOLET, open), true);
		g.pose().popPose();
		if (showGroup) {
			g.pose().pushPose();
			g.pose().translate(px + pw - PAD - groupW, y + 2f, 0);
			g.pose().scale(HINT_SCALE, HINT_SCALE, 1f);
			g.drawString(this.font, group, 0, 0, fade(TEXT_DIM, open), false);
			g.pose().popPose();
		}
		y += headH();
		// the full description
		for (int i = 0; i < desc.size(); i++) {
			g.pose().pushPose();
			g.pose().translate(x0, y + i * 10f * ds, 0);
			g.pose().scale(ds, ds, 1f);
			g.drawString(this.font, desc.get(i), 0, 0, fade(TEXT, open), false);
			g.pose().popPose();
		}
		// the hint, at the foot under a rule
		int hy =py + ph - PAD + 2 - hintH(hint.size());
		g.fill(x0, hy - 5, px + pw - PAD, hy - 4, fade(VIOLET_DIM, open));
		for (int i = 0; i < hint.size(); i++) {
			g.pose().pushPose();
			g.pose().translate(x0, hy + i * 10f * HINT_SCALE, 0);
			g.pose().scale(HINT_SCALE, HINT_SCALE, 1f);
			g.drawString(this.font, hint.get(i), 0, 0, fade(TEXT_DIM, open), false);
			g.pose().popPose();
		}
	}

	private float openEase(float partial) {
		float t = Mth.clamp((ticksOpen + partial) / OPEN_TICKS, 0f, 1f);
		return 1f - (1f - t) * (1f - t);
	}

	private static float smooth(float h) {
		h = Mth.clamp(h, 0f, 1f);
		return h * h * (3f - 2f * h);
	}

	private static int fade(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0f, 1f));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	private static int lerp(int c0, int c1, float t) {
		int a = Math.round(Mth.lerp(t, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF));
		int r = Math.round(Mth.lerp(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, c0 & 0xFF, c1 & 0xFF));
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}
}

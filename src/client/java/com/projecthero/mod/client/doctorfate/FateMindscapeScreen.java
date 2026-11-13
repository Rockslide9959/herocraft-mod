package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateMindscape;
import com.projecthero.mod.doctorfate.DoctorFateMindscape.Choice;
import com.projecthero.mod.doctorfate.DoctorFateMindscape.Reply;
import com.projecthero.mod.doctorfate.network.FateActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * v0.17.3 Doctor Fate: the <b>Tower of Fate</b> mindscape (hold Shift+V 1 s; {@code DoctorFateMindscape} on the server). A gold-on-black
 * vision: a starfield, the Tower of Fate in golden outline under a great faint ankh, columns of hieroglyphs drifting at the edges, and
 * Nabu's helmet above it all, its eyes burning while he speaks. His line types out in the dialogue panel; the answers are rows on the
 * right (under it on a narrow screen), each wrapped to its column with a grey line of what it costs. Number keys pick a row. Greyed
 * rows say why (cooldown, helmet off). Nothing pauses: the body stands where it was. Esc or "Leave the Tower" closes it.
 */
public class FateMindscapeScreen extends Screen {
	private static final int GOLD = 0xFFFFC93A;
	private static final int PALE = 0xFFFFE8B0;
	private static final int TEXT = 0xFFF2E6C8;
	private static final int GREY = 0xFF8A8070;
	private static final int PANEL = 0xD0080604;

	private Reply reply = Reply.GREETING;
	private int argA, argB, tier;
	private long replyAt = System.currentTimeMillis();
	private final long openedAt = System.currentTimeMillis();
	private boolean closed;

	private record Row(Choice choice, List<FormattedCharSequence> lines, boolean enabled, int y, int h) {
	}

	private final List<Row> rows = new ArrayList<>();
	private int rowsX, rowsW;

	public FateMindscapeScreen(int[] data) {
		super(Component.translatable("screen.projecthero.doctor_fate.mind.title"));
		apply(data);
	}

	/** A {@code MIND_REPLY} from the server: a greeting opens the Tower, anything else updates it. */
	static void receive(int[] data) {
		Minecraft mc = Minecraft.getInstance();
		if (data.length < 4) {
			return;
		}
		if (mc.screen instanceof FateMindscapeScreen s) {
			s.apply(data);
			if (s.reply == Reply.FAREWELL) {
				s.closed = true;
				mc.setScreen(null);
			}
			return;
		}
		if (data[0] == Reply.GREETING.ordinal() && (mc.screen == null || mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
			mc.setScreen(new FateMindscapeScreen(data));
		}
	}

	private void apply(int[] data) {
		Reply[] all = Reply.values();
		reply = data[0] >= 0 && data[0] < all.length ? all[data[0]] : Reply.GREETING;
		argA = data[1];
		argB = data[2];
		tier = Math.max(0, Math.min(3, data[3]));
		replyAt = System.currentTimeMillis();
		if (minecraft != null) {
			rebuildRows();
		}
	}

	// ---------------------------------------------------------------- the harness (TEMP-HARNESS calls these by reflection)

	public static void harnessOpen() {
		Minecraft mc = Minecraft.getInstance();
		var srv = mc.getSingleplayerServer();
		if (srv != null) {
			srv.execute(() -> srv.getPlayerList().getPlayers().forEach(DoctorFateMindscape::open));
		}
	}

	public static void harnessChoose(int choice) {
		if (Minecraft.getInstance().screen instanceof FateMindscapeScreen s) {
			s.choose(Choice.byId(choice));
		}
	}

	// ---------------------------------------------------------------- layout

	/** The vision on the left (helmet, tower, Dominion), the talk on the right. */
	private int leftW() {
		return Mth.clamp((int) (width * 0.36), 110, 240);
	}

	private int dialogueX() {
		return leftW() + 6;
	}

	private int dialogueW() {
		return width - dialogueX() - 8;
	}

	private int dialogueY() {
		return 8;
	}

	private int dialogueH;
	/** Each row's cost line fits under it; otherwise the hovered row's cost shows at the bottom. */
	private boolean inlineHints = true;
	private int detailY;

	@Override
	protected void init() {
		rebuildRows();
	}

	private void rebuildRows() {
		rows.clear();
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || font == null) {
			return;
		}
		dialogueH = 24 + font.split(replyText(), dialogueW() - 16).size() * 10 + 6;
		List<Choice> show = new ArrayList<>();
		show.add(Choice.STATUS);
		show.add(Choice.BLESSING);
		show.add(Choice.SILENCE);
		show.add(DoctorFateMindscape.debt(p) > 0f ? Choice.REPAY : Choice.DEBT);
		show.add(Choice.LORE_ORDER);
		show.add(Choice.LORE_HELMET);
		show.add(Choice.LORE_KENT);
		show.add(Choice.LEAVE);
		rowsX = dialogueX();
		rowsW = dialogueW();
		int top = dialogueY() + Math.max(dialogueH, 48) + 6;
		// do the cost lines fit under their rows?
		int need = 0;
		for (Choice c : show) {
			need += labelLines(c, show.indexOf(c) + 1).size() * 10 + 6;
			Component hint = hint(p, c);
			if (hint != null) {
				need += font.split(hint, rowsW - 16).size() * 10;
			}
		}
		inlineHints = top + need <= height - 6;
		int y = top;
		int n = 1;
		for (Choice c : show) {
			List<FormattedCharSequence> lines = new ArrayList<>(labelLines(c, n));
			Component hint = hint(p, c);
			if (hint != null && inlineHints) {
				lines.addAll(font.split(hint, rowsW - 16));
			}
			boolean enabled = enabled(p, c);
			int h = lines.size() * 10 + 4;
			rows.add(new Row(c, lines, enabled, y, h));
			y += h + 2;
			n++;
		}
		detailY = y + 2;
	}

	private List<FormattedCharSequence> labelLines(Choice c, int n) {
		String key = "screen.projecthero.doctor_fate.mind.choice." + c.name().toLowerCase(java.util.Locale.ROOT);
		return font.split(Component.literal(n + ". ").append(Component.translatable(key)), rowsW - 10);
	}

	private static int cooldownSec(LocalPlayer p, String id) {
		return (DoctorFate.cooldownRemaining(p, id) + 19) / 20;
	}

	private boolean enabled(LocalPlayer p, Choice c) {
		return switch (c) {
			case BLESSING -> DoctorFate.wearsHelmet(p) && cooldownSec(p, DoctorFateMindscape.BLESSING_CD) == 0;
			case SILENCE -> cooldownSec(p, DoctorFateMindscape.SILENCE_CD) == 0;
			case DEBT -> DoctorFate.dominion(p) >= DoctorFateConfig.MIND_DEBT_MIN;
			default -> true;
		};
	}

	private Component hint(LocalPlayer p, Choice c) {
		String k = "screen.projecthero.doctor_fate.mind.hint.";
		return switch (c) {
			case BLESSING -> {
				int cd = cooldownSec(p, DoctorFateMindscape.BLESSING_CD);
				if (cd > 0) {
					yield Component.translatable(k + "cooldown", cd).withStyle(s -> s.withColor(GREY));
				}
				if (!DoctorFate.wearsHelmet(p)) {
					yield Component.translatable(k + "needs_helmet").withStyle(s -> s.withColor(GREY));
				}
				yield Component.translatable(k + "blessing", Math.round(DoctorFateConfig.MIND_BLESSING_DOMINION),
						DoctorFateConfig.MIND_BLESSING_TICKS / 20).withStyle(s -> s.withColor(GREY));
			}
			case SILENCE -> {
				int cd = cooldownSec(p, DoctorFateMindscape.SILENCE_CD);
				yield cd > 0 ? Component.translatable(k + "cooldown", cd).withStyle(s -> s.withColor(GREY))
						: Component.translatable(k + "silence", DoctorFateConfig.MIND_SILENCE_TICKS / 20).withStyle(s -> s.withColor(GREY));
			}
			case DEBT -> Component.translatable(k + "debt", Math.round(DoctorFateConfig.MIND_DEBT_CUT), Math.round(DoctorFateConfig.MIND_DEBT_OWED),
					DoctorFateConfig.MIND_DEBT_TICKS / 1200).withStyle(s -> s.withColor(GREY));
			case REPAY -> Component.translatable(k + "repay", Math.round(Math.max(0f, DoctorFateMindscape.debt(p) - DoctorFateConfig.MIND_REPAY_DISCOUNT)),
					clock(DoctorFateMindscape.debtTicksLeft(p))).withStyle(s -> s.withColor(GREY));
			default -> null;
		};
	}

	private static String clock(int ticks) {
		int s = (ticks + 19) / 20;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	// ---------------------------------------------------------------- input

	private void choose(Choice c) {
		if (closed) {
			return;
		}
		ClientPlayNetworking.send(new FateActionPayload(FateActionPayload.Action.MIND_CHOICE, c.ordinal(), BlockPos.ZERO));
		if (c == Choice.LEAVE) {
			closed = true;
			super.onClose();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		for (Row r : rows) {
			if (mx >= rowsX && mx < rowsX + rowsW && my >= r.y && my < r.y + r.h) {
				if (r.enabled) {
					choose(r.choice);
					Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
							.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f));
				}
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
			int i = key - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
			if (i < rows.size() && rows.get(i).enabled) {
				choose(rows.get(i).choice);
				return true;
			}
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public void onClose() {
		if (!closed) {
			closed = true;
			ClientPlayNetworking.send(new FateActionPayload(FateActionPayload.Action.MIND_CLOSE, 0, BlockPos.ZERO));
		}
		super.onClose();
	}

	@Override
	public void tick() {
		super.tick();
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || !DoctorFate.hasPower(p) || DoctorFate.possessed(p)) {
			onClose(); // Nabu took the body: the vision breaks
			return;
		}
		if (p.tickCount % 10 == 0) {
			rebuildRows(); // cooldowns and the debt clock
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false; // the server answers; nothing in the world stops
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		// drawn in render (the backdrop) so it is never drawn twice
	}

	private void backdrop(GuiGraphics g) {
		float t = (System.currentTimeMillis() - openedAt) / 1000f;
		float in = Mth.clamp(t / 0.6f, 0f, 1f);
		g.fillGradient(0, 0, width, height, argb(0.94f * in, 0x06040C), argb(0.97f * in, 0x000000));
		// the starfield
		RandomSource r = RandomSource.create(4242);
		for (int i = 0; i < 140; i++) {
			int x = r.nextInt(Math.max(1, width)), y = r.nextInt(Math.max(1, height));
			float tw = 0.4f + 0.6f * (0.5f + 0.5f * Mth.sin(t * (1.0f + r.nextFloat() * 2f) + i));
			int c = r.nextInt(5) == 0 ? 0xFFD27A : 0xE8E0D0;
			g.fill(x, y, x + 1, y + 1, argb(tw * in * 0.9f, c));
		}
		int lw = leftW();
		int cx = lw / 2;
		int base = height - 44 - 10 * chipCount(); // above the Dominion gauge and its chips
		int top = helmetY() + helmetSize() + 18;
		// a great faint ankh behind the helmet and the tower
		int ah = base - helmetY() + 10;
		int aw = Math.min(lw - 20, ah / 2);
		ankh2d(g, cx, helmetY() - 4, aw, ah, argb(0.10f * in, 0xFFC93A), 5);
		// hieroglyph columns drifting at both edges of the vision
		for (int col = 0; col < 2; col++) {
			int x = col == 0 ? 4 : lw - 20;
			float drift = (t * 8f) % 26f;
			for (int k = -1; k < height / 26 + 1; k++) {
				int y = (int) (k * 26 + (col == 0 ? drift : -drift));
				glyph2d(g, 1 + Math.floorMod(k * 3 + col * 5, FateGlyphs.COUNT - 1), x + 8, y + 12, 14, argb(0.32f * in, 0xFFC93A));
			}
		}
		if (base - top > 40) {
			tower(g, cx, base, top, in, t);
		}
	}

	/** How many Blessing / Silence / Debt chips the status shows. */
	private static int chipCount() {
		LocalPlayer p = Minecraft.getInstance().player;
		var st = p == null ? null : DoctorFate.peek(p);
		if (st == null) {
			return 0;
		}
		long t0 = p.level().getGameTime();
		return (st.mind.blessingUntil() > t0 ? 1 : 0) + (st.mind.silenceUntil() > t0 ? 1 : 0) + (st.mind.debt() > 0f ? 1 : 0);
	}

	private int helmetSize() {
		return Math.max(32, Math.min((int) (leftW() * 0.42), (int) (height * 0.26)));
	}

	private int helmetY() {
		return 12;
	}

	/** The Tower of Fate in golden outline: a tall keep with lit windows, crenellations and a stepped spire. */
	private void tower(GuiGraphics g, int cx, int base, int top, float in, float t) {
		int gold = argb(0.85f * in, 0xFFC93A);
		int dim = argb(0.18f * in, 0xFFC93A);
		int w = Mth.clamp(leftW() / 6, 16, 40);
		int bodyTop = top + (base - top) / 5;
		// the plinth
		outline(g, cx - w - 14, base - 6, cx + w + 14, base, gold);
		g.fill(cx - w - 13, base - 5, cx + w + 13, base - 1, dim);
		// the keep
		outline(g, cx - w, bodyTop, cx + w, base - 6, gold);
		g.fill(cx - w + 1, bodyTop + 1, cx + w - 1, base - 7, argb(0.06f * in, 0xFFC93A));
		// crenellations
		for (int x = cx - w; x < cx + w; x += 8) {
			outline(g, x, bodyTop - 5, x + 4, bodyTop, gold);
		}
		// lit windows, flickering
		for (int row = 0; bodyTop + 12 + row * 18 < base - 18; row++) {
			for (int side = -1; side <= 1; side += 2) {
				int wx = cx + side * w / 2 - 2;
				int wy = bodyTop + 12 + row * 18;
				float f = 0.6f + 0.4f * Mth.sin(t * 2.3f + row * 1.7f + side);
				g.fill(wx, wy, wx + 4, wy + 8, argb(0.9f * f * in, 0xFFE27A));
			}
		}
		// the door
		outline(g, cx - 6, base - 22, cx + 6, base - 6, gold);
		// the stepped spire
		int y = bodyTop - 5;
		int sw = w - 4;
		while (sw > 2 && y > top) {
			outline(g, cx - sw, y - 8, cx + sw, y, gold);
			y -= 8;
			sw -= Math.max(3, w / 6);
		}
		g.fill(cx, y - 14, cx + 1, y, gold);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		backdrop(g);
		LocalPlayer p = Minecraft.getInstance().player;
		float t = (System.currentTimeMillis() - openedAt) / 1000f;
		long since = System.currentTimeMillis() - replyAt;
		Component line = replyText();
		String plain = line.getString();
		int shown = (int) Math.min(plain.length(), since / 22);
		boolean speaking = shown < plain.length();
		// Nabu's helmet
		int hs = helmetSize();
		int hx = leftW() / 2 - hs / 2;
		int hy = helmetY();
		for (int k = 4; k >= 1; k--) {
			int pad = k * hs / 9;
			g.fill(hx - pad, hy - pad, hx + hs + pad, hy + hs + pad, argb(0.05f + (speaking ? 0.03f : 0f), 0xFFC93A));
		}
		g.blit(FateSuitLayer.SUIT, hx, hy, hs, hs, 8f, 8f, 8, 8, 64, 64);
		g.blit(FateSuitLayer.SUIT, hx, hy, hs, hs, 40f, 8f, 8, 8, 64, 64);
		// the eyes burn while he speaks
		float burn = speaking ? 0.7f + 0.3f * Mth.sin(t * 18f) : 0.35f + 0.15f * Mth.sin(t * 2f);
		int px = hs / 8;
		for (int side = 0; side < 2; side++) {
			int ex = hx + (side == 0 ? 1 : 5) * px;
			int ey = hy + 4 * px;
			g.fill(ex - px / 2, ey - px / 2, ex + 2 * px + px / 2, ey + px + px / 2, argb(0.35f * burn, 0xFFE27A));
			g.fill(ex, ey, ex + 2 * px, ey + px, argb(burn, 0xFFFFF0));
		}
		// the title
		Component title = Component.translatable("screen.projecthero.doctor_fate.mind.title");
		List<FormattedCharSequence> titleLines = font.split(title, leftW() - 8);
		int ty = hy + hs + 5;
		for (FormattedCharSequence tl : titleLines) {
			g.drawString(font, tl, leftW() / 2 - font.width(tl) / 2, ty, GOLD, true);
			ty += 10;
		}
		// the dialogue panel (sized for the whole line, so the rows below never jump while it types)
		int dx = dialogueX(), dw = dialogueW(), dy = dialogueY();
		List<FormattedCharSequence> lines = font.split(Component.literal(plain.substring(0, shown)), dw - 16);
		int dh = Math.max(48, dialogueH);
		g.fill(dx, dy, dx + dw, dy + dh, PANEL);
		outline(g, dx, dy, dx + dw, dy + dh, 0xFFB08A30);
		g.drawString(font, Component.translatable("screen.projecthero.doctor_fate.mind.nabu"), dx + 8, dy + 6, GOLD, true);
		int y = dy + 20;
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, dx + 8, y, PALE, false);
			y += 10;
		}
		// the status strip: Dominion and what was agreed
		if (p != null) {
			status(g, p, 6, height - 34);
		}
		// the answers
		Row hovered = null;
		for (Row r : rows) {
			boolean hover = r.enabled && mouseX >= rowsX && mouseX < rowsX + rowsW && mouseY >= r.y && mouseY < r.y + r.h;
			if (mouseX >= rowsX && mouseX < rowsX + rowsW && mouseY >= r.y && mouseY < r.y + r.h) {
				hovered = r;
			}
			g.fill(rowsX, r.y, rowsX + rowsW, r.y + r.h, hover ? 0xC0402A08 : PANEL);
			g.fill(rowsX, r.y, rowsX + 2, r.y + r.h, r.enabled ? (hover ? 0xFFFFE27A : GOLD) : 0xFF4A4030);
			int ly = r.y + 4;
			boolean first = true;
			for (FormattedCharSequence l : r.lines) {
				g.drawString(font, l, rowsX + (first ? 6 : 12), ly, r.enabled ? (first ? TEXT : GREY) : GREY, false);
				ly += 10;
				first = false;
			}
		}
		// no room for the cost lines: the hovered row's shows under the list
		if (!inlineHints && hovered != null && p != null) {
			Component hint = hint(p, hovered.choice);
			if (hint != null) {
				int hy2 = Math.min(detailY, height - 22);
				for (FormattedCharSequence l : font.split(hint, rowsW - 8)) {
					g.drawString(font, l, rowsX + 4, hy2, GREY, false);
					hy2 += 10;
				}
			}
		}
		super.render(g, mouseX, mouseY, partial);
	}

	private void status(GuiGraphics g, LocalPlayer p, int x, int y) {
		float d = DoctorFate.dominion(p);
		int bw = Math.min(120, leftW() - 12);
		y -= chipCount() * 10; // the chips stack upward from the gauge
		Component label = Component.translatable("screen.projecthero.doctor_fate.mind.dominion", Math.round(d));
		g.drawString(font, label, x, y, GOLD, true);
		y += 11;
		g.fill(x, y, x + bw, y + 4, 0xFF201810);
		g.fill(x, y, x + Math.round(bw * d / DoctorFateConfig.DOMINION_MAX), y + 4, d >= DoctorFateConfig.TIER_HOLD ? 0xFFFF8A30 : GOLD);
		for (float mark : new float[] { DoctorFateConfig.TIER_OVERRIDE, DoctorFateConfig.TIER_PULL, DoctorFateConfig.TIER_HOLD }) {
			int mx = x + Math.round(bw * mark / DoctorFateConfig.DOMINION_MAX);
			g.fill(mx, y - 1, mx + 1, y + 5, 0xFF000000);
		}
		y += 8;
		long now = p.level().getGameTime();
		var mind = DoctorFate.peek(p) == null ? null : DoctorFate.peek(p).mind;
		if (mind == null) {
			return;
		}
		if (mind.blessingUntil() > now) {
			g.drawString(font, Component.translatable("screen.projecthero.doctor_fate.mind.chip.blessing", clock((int) (mind.blessingUntil() - now))), x,
					y, 0xFFFFE27A, true);
			y += 10;
		}
		if (mind.silenceUntil() > now) {
			g.drawString(font, Component.translatable("screen.projecthero.doctor_fate.mind.chip.silence", clock((int) (mind.silenceUntil() - now))), x,
					y, 0xFF9AB8FF, true);
			y += 10;
		}
		if (mind.debt() > 0f) {
			g.drawString(font, Component.translatable("screen.projecthero.doctor_fate.mind.chip.debt", Math.round(mind.debt()),
					clock((int) Math.max(0L, mind.debtDueAt() - now))), x, y, 0xFFFF9A60, true);
		}
	}

	private Component replyText() {
		String k = "screen.projecthero.doctor_fate.mind.reply.";
		return switch (reply) {
			case GREETING -> Component.translatable(k + "greeting." + tier);
			case STATUS -> Component.translatable(k + "status." + tier, argA);
			case BLESSED -> Component.translatable(k + "blessed", argA, DoctorFateConfig.MIND_BLESSING_TICKS / 20);
			case BLESS_NEEDS_HELMET -> Component.translatable(k + "bless_needs_helmet");
			case SILENCED -> Component.translatable(k + "silenced", DoctorFateConfig.MIND_SILENCE_TICKS / 20);
			case SILENCE_REFUSED -> Component.translatable(k + "silence_refused");
			case DEBT_MADE -> Component.translatable(k + "debt_made", argA, argB, DoctorFateConfig.MIND_DEBT_TICKS / 1200);
			case DEBT_OWED -> Component.translatable(k + "debt_owed", argA, argB);
			case DEBT_NOTHING -> Component.translatable(k + "debt_nothing");
			case REPAID -> Component.translatable(k + "repaid", argA);
			case NO_DEBT -> Component.translatable(k + "no_debt");
			case LORE_ORDER -> Component.translatable(k + "lore_order");
			case LORE_HELMET -> Component.translatable(k + "lore_helmet");
			case LORE_KENT -> Component.translatable(k + "lore_kent");
			case COOLING -> Component.translatable(k + "cooling", argA);
			case FAREWELL -> Component.translatable(k + "farewell");
			case DEBT_COLLECTED -> Component.translatable(k + "debt_collected", argA);
		};
	}

	// ---------------------------------------------------------------- 2D helpers

	private static int argb(float a, int rgb) {
		return (Math.round(Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int c) {
		g.fill(x0, y0, x1, y0 + 1, c);
		g.fill(x0, y1 - 1, x1, y1, c);
		g.fill(x0, y0, x0 + 1, y1, c);
		g.fill(x1 - 1, y0, x1, y1, c);
	}

	private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int c, int w) {
		float dx = x1 - x0, dy = y1 - y0;
		int n = (int) Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
		for (int i = 0; i <= n; i++) {
			int x = Math.round(x0 + dx * i / n), y = Math.round(y0 + dy * i / n);
			g.fill(x, y, x + w, y + w, c);
		}
	}

	private static void glyph2d(GuiGraphics g, int index, int cx, int cy, int size, int c) {
		for (float[] l : FateGlyphs.strokes(index)) {
			for (int i = 0; i + 3 < l.length; i += 2) {
				line(g, cx + l[i] * size, cy - l[i + 1] * size, cx + l[i + 2] * size, cy - l[i + 3] * size, c, 1);
			}
		}
	}

	/** A big outlined ankh: loop on top, bar, stem. */
	private static void ankh2d(GuiGraphics g, int cx, int top, int w, int h, int c, int thick) {
		float loopH = h * 0.36f, loopW = w * 0.55f;
		float lcY = top + loopH / 2;
		float px = cx + loopW / 2, py = lcY;
		for (int i = 1; i <= 40; i++) {
			double a = i * Math.PI * 2 / 40;
			float x = (float) (cx + Math.cos(a) * loopW / 2), y = (float) (lcY + Math.sin(a) * loopH / 2);
			line(g, px, py, x, y, c, thick);
			px = x;
			py = y;
		}
		float barY = top + loopH + thick;
		line(g, cx - w / 2f, barY, cx + w / 2f, barY, c, thick);
		line(g, cx - thick / 2f, barY, cx - thick / 2f, top + h, c, thick);
	}
}

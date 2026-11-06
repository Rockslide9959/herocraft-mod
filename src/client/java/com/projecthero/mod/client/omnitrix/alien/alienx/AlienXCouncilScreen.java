package com.projecthero.mod.client.omnitrix.alien.alienx;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXActions;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncil;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXCouncilPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.2 Celestial Council (user: "make a good debating system where the player can do many many many things, make it work well
 * and be intuitive please"): the council screen, in the game's own look (dark panels, white shadowed text, boss-bar meters,
 * vanilla buttons), drawn over the council's hall so the two heads stay visible behind it. Everything wraps.
 * <ol>
 * <li><b>Propose</b>: category (left), action (middle: greyed while it rests), target (right: what is round his body -- the area,
 * where the body looks, the body, creatures and players with health and distance -- only the kinds the action takes). Under them
 * the action's description and how each head leans (the meters preview where they start). "Propose the motion".</li>
 * <li><b>Debate</b>: both meters (each must reach AlienXCouncil.NEED, 85), the patience pips (turns left), the transcript, and the offered
 * arguments: each row shows its tone, Ben's line, and how each head feels about that tone (▲▲ / ▲ / ▼), marked ✖ if it
 * contradicts the motion and ★ once Listen has revealed a head's concern. Keys 1-5 argue, L listens, W withdraws.</li>
 * <li><b>Result</b>: carried or refused, what happened at his body; "New motion" or "Leave the Council".</li>
 * </ol>
 * The server validates every request; this only shows its state ({@link #update}).
 */
public class AlienXCouncilScreen extends Screen {
	private static final int SERENA = 0xFF9FE8FF, BELLICUS = 0xFFFF6A5A, BEN = 0xFF7CFF6A, COUNCIL = 0xFFFFD27A, PANEL = 0xF20A0A12, ROW = 0x50FFFFFF,
			ROW_SEL = 0x90FFFFFF;
	private static final String P = "projecthero.omnitrix.alien_x.";
	private CompoundTag state;
	private String category = AlienXActions.CATEGORIES.get(0);
	private String action;
	private int target = Integer.MIN_VALUE;
	private final List<Hit> hits = new ArrayList<>();
	private Button propose, leave, listen, withdraw, back;

	private record Hit(int x0, int y0, int x1, int y1, Runnable click) {
	}

	public AlienXCouncilScreen(CompoundTag state) {
		super(Component.translatable(P + "council.title"));
		this.state = state;
	}

	public void update(CompoundTag state) {
		this.state = state;
		layoutButtons();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int phase() {
		return state.getInt("phase");
	}

	@Override
	protected void init() {
		int w = 120;
		propose = addRenderableWidget(Button.builder(Component.translatable(P + "council.propose"), b -> send(AlienXCouncilPayload.OP_PROPOSE, action, target))
				.bounds(width / 2 - w - 4, height - 28, w, 20).build());
		leave = addRenderableWidget(Button.builder(Component.translatable(P + "council.leave"), b -> {
			send(AlienXCouncilPayload.OP_LEAVE, "", 0);
			onClose();
		}).bounds(width / 2 + 4, height - 28, w, 20).build());
		listen = addRenderableWidget(Button.builder(Component.translatable(P + "council.listen"), b -> send(AlienXCouncilPayload.OP_LISTEN, "", 0))
				.bounds(width / 2 - w - 4, height - 28, w, 20).build());
		withdraw = addRenderableWidget(Button.builder(Component.translatable(P + "council.withdraw"), b -> send(AlienXCouncilPayload.OP_WITHDRAW, "", 0))
				.bounds(width / 2 + 4, height - 28, w, 20).build());
		back = addRenderableWidget(Button.builder(Component.translatable(P + "council.new_motion"), b -> send(AlienXCouncilPayload.OP_BACK, "", 0))
				.bounds(width / 2 - w - 4, height - 28, w, 20).build());
		layoutButtons();
	}

	private void layoutButtons() {
		if (propose == null) {
			return;
		}
		int ph = phase();
		propose.visible = ph == AlienXCouncil.PHASE_PROPOSE;
		propose.active = action != null && target != Integer.MIN_VALUE && targetOk(action, target) && cooldown(action) == 0
				&& cooldown("cat:" + category(action)) == 0;
		leave.visible = ph != AlienXCouncil.PHASE_DEBATE;
		listen.visible = ph == AlienXCouncil.PHASE_DEBATE;
		withdraw.visible = ph == AlienXCouncil.PHASE_DEBATE;
		back.visible = ph == AlienXCouncil.PHASE_RESULT;
	}

	private static void send(int op, String action, int value) {
		if (ClientPlayNetworking.canSend(AlienXCouncilPayload.TYPE)) {
			ClientPlayNetworking.send(new AlienXCouncilPayload(op, action == null ? "" : action, value));
		}
	}

	@Override
	public void removed() {
		send(AlienXCouncilPayload.OP_CLOSE, "", 0);
		super.removed();
	}

	// ---------------------------------------------------------------- helpers

	private int cooldown(String key) {
		return state.getCompound("cooldowns").getInt(key);
	}

	private static String category(String actionId) {
		AlienXActions.Action a = AlienXActions.get(actionId);
		return a == null ? "" : a.category();
	}

	private boolean targetOk(String actionId, int id) {
		AlienXActions.Action a = AlienXActions.get(actionId);
		if (a == null) {
			return false;
		}
		for (Tag t : state.getList("targets", Tag.TAG_COMPOUND)) {
			CompoundTag c = (CompoundTag) t;
			if (c.getInt("id") == id) {
				return a.takes(c.getInt("kind"));
			}
		}
		return false;
	}

	/** A name or a "#lang.key". */
	private static MutableComponent text(String s) {
		return s.startsWith("#") ? Component.translatable(s.substring(1)) : Component.literal(s);
	}

	private static Component line(CompoundTag l) {
		int who = l.getInt("who");
		ListTag args = l.getList("args", Tag.TAG_STRING);
		Object[] a = new Object[args.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = text(args.getString(i));
		}
		// v0.17.2 (user: "make them stop saying bens name, make them say the players name"): {player} in the lines is whoever is here
		String me = net.minecraft.client.Minecraft.getInstance().getUser().getName();
		if (net.minecraft.client.Minecraft.getInstance().player != null) {
			me = net.minecraft.client.Minecraft.getInstance().player.getName().getString();
		}
		String you = me;
		MutableComponent body = Component.literal(Component.translatable(P + l.getString("key"), a).getString().replace("{player}", you));
		if (who == AlienXCouncil.WHO_COUNCIL) {
			return body.withStyle(s -> s.withColor(COUNCIL & 0xFFFFFF).withItalic(true));
		}
		int col = who == AlienXCouncil.WHO_BEN ? BEN : who == AlienXCouncil.WHO_SERENA ? SERENA : BELLICUS;
		String name = who == AlienXCouncil.WHO_BEN ? "ben" : who == AlienXCouncil.WHO_SERENA ? "serena" : "bellicus";
		return Component.empty().append((who == AlienXCouncil.WHO_BEN ? Component.literal(you) : Component.translatable(P + "voice." + name)).withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)))
				.append(Component.literal(": ").withStyle(s -> s.withColor(col & 0xFFFFFF))).append(body.withStyle(ChatFormatting.WHITE));
	}

	/** Wrapped text, top-left at (x, y); returns the y under it. */
	private int wrap(GuiGraphics g, Component c, int x, int y, int w, int colour) {
		for (FormattedCharSequence s : font.split(c, w)) {
			g.drawString(font, s, x, y, colour, true);
			y += 10;
		}
		return y;
	}

	private void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
		g.fill(x0, y0, x1, y1, PANEL);
		g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0x40FFFFFF);
	}

	private boolean row(GuiGraphics g, int x0, int y0, int x1, int y1, int mx, int my, boolean selected, Runnable click) {
		boolean hover = mx >= x0 && mx < x1 && my >= y0 && my < y1;
		g.fill(x0, y0, x1, y1, selected ? ROW_SEL : hover ? ROW : 0x20FFFFFF);
		if (selected) {
			g.renderOutline(x0, y0, x1 - x0, y1 - y0, 0xFFFFFFFF);
		}
		hits.add(new Hit(x0, y0, x1, y1, click));
		return hover;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
		// no blur: the heads stay in view behind the panels
		g.fillGradient(0, 0, width, height, 0x50000000, 0x90000000);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float partial) {
		hits.clear();
		layoutButtons();
		super.render(g, mx, my, partial);
		g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, 6, COUNCIL);
		CompoundTag d = state.getCompound("debate");
		boolean debating = phase() == AlienXCouncil.PHASE_DEBATE && d.contains("action");
		// the two meters (in a debate: where they stand; proposing: where the selected action starts them)
		float sv, bv;
		if (debating) {
			sv = d.getFloat("serena");
			bv = d.getFloat("bellicus");
		} else {
			AlienXActions.Action a = action == null ? null : AlienXActions.get(action);
			sv = a == null ? 0 : a.serena();
			bv = a == null ? 0 : a.bellicus();
		}
		int mw = Math.min(180, width / 2 - 20);
		if (debating || phase() == AlienXCouncil.PHASE_PROPOSE && action != null) {
			VanillaHud.meter(g, width / 2 - mw - 8, 22, mw, Component.translatable(P + "voice.serena"), Math.max(0f, sv) / AlienXCouncil.NEED, SERENA,
					(int) sv + "/" + AlienXCouncil.NEED, SERENA);
			VanillaHud.meter(g, width / 2 + 8, 22, mw, Component.translatable(P + "voice.bellicus"), Math.max(0f, bv) / AlienXCouncil.NEED, BELLICUS,
					(int) bv + "/" + AlienXCouncil.NEED, BELLICUS);
		}
		if (!debating && action != null) {
			g.drawCenteredString(font, Component.translatable(P + "council.leaning"), width / 2, 30, 0xFF9A9A9A);
		}
		int top = 40, bottom = height - 34;
		switch (phase()) {
			case AlienXCouncil.PHASE_DEBATE -> drawDebate(g, d, top, bottom, mx, my);
			case AlienXCouncil.PHASE_RESULT -> drawResult(g, top, bottom);
			default -> drawPropose(g, top, bottom, mx, my);
		}
	}

	private void drawPropose(GuiGraphics g, int top, int bottom, int mx, int my) {
		int pad = 8;
		int cw = 84, aw = 118;
		int x0 = pad, x1 = x0 + cw, x2 = x1 + 4, x3 = x2 + aw, x4 = x3 + 4, x5 = width - pad;
		int descTop = bottom - 46;
		// categories
		panel(g, x0, top, x1, descTop - 4);
		g.drawString(font, Component.translatable(P + "council.categories").withStyle(ChatFormatting.GOLD), x0 + 4, top + 3, 0xFFFFFFFF, true);
		int y = top + 14;
		for (String c : AlienXActions.CATEGORIES) {
			if (y + 11 > descTop - 4) {
				break;
			}
			final String cc = c;
			row(g, x0 + 2, y, x1 - 2, y + 11, mx, my, c.equals(category), () -> {
				category = cc;
				action = null;
			});
			int cd = cooldown("cat:" + c);
			g.drawString(font, Component.translatable(P + "council.category." + c), x0 + 5, y + 2, cd > 0 ? 0xFF777777 : 0xFFFFFFFF, true);
			y += 12;
		}
		// actions
		panel(g, x2, top, x3, descTop - 4);
		g.drawString(font, Component.translatable(P + "council.actions").withStyle(ChatFormatting.GOLD), x2 + 4, top + 3, 0xFFFFFFFF, true);
		y = top + 14;
		for (AlienXActions.Action a : AlienXActions.inCategory(category)) {
			final String id = a.id();
			row(g, x2 + 2, y, x3 - 2, y + 11, mx, my, id.equals(action), () -> {
				action = id;
				if (target != Integer.MIN_VALUE && !targetOk(id, target)) {
					target = Integer.MIN_VALUE;
				}
			});
			int cd = Math.max(cooldown(id), cooldown("cat:" + a.category()));
			Component name = Component.translatable(AlienXCouncil.actionKey(id));
			g.drawString(font, name, x2 + 5, y + 2, cd > 0 ? 0xFF777777 : 0xFFFFFFFF, true);
			if (cd > 0) {
				String s = (cd + 19) / 20 + "s";
				g.drawString(font, s, x3 - 5 - font.width(s), y + 2, 0xFFFF7070, true);
			}
			y += 12;
		}
		// targets
		panel(g, x4, top, x5, descTop - 4);
		g.drawString(font, Component.translatable(P + "council.targets").withStyle(ChatFormatting.GOLD), x4 + 4, top + 3, 0xFFFFFFFF, true);
		y = top + 14;
		AlienXActions.Action sel = action == null ? null : AlienXActions.get(action);
		if (sel == null) {
			wrap(g, Component.translatable(P + "council.pick_action"), x4 + 5, y + 2, x5 - x4 - 10, 0xFF9A9A9A);
		} else {
			for (Tag t : state.getList("targets", Tag.TAG_COMPOUND)) {
				CompoundTag c = (CompoundTag) t;
				if (!sel.takes(c.getInt("kind")) || y + 11 > descTop - 4) {
					continue;
				}
				final int id = c.getInt("id");
				row(g, x4 + 2, y, x5 - 2, y + 11, mx, my, id == target, () -> target = id);
				Component name = text(c.getString("name"));
				int kind = c.getInt("kind");
				int col = kind == AlienXActions.K_PLAYER ? 0xFF7CFF6A : kind == AlienXActions.K_LIVING ? 0xFFFFFFFF : 0xFF9FC4FF;
				List<FormattedCharSequence> cut = font.split(name, Math.max(20, x5 - x4 - 70));
				if (!cut.isEmpty()) {
					g.drawString(font, cut.get(0), x4 + 5, y + 2, col, true);
				}
				if (id >= 0) {
					String info = Mth.ceil(c.getFloat("hp")) + "❤ " + String.format(java.util.Locale.ROOT, "%.0fm", c.getFloat("dist"));
					g.drawString(font, info, x5 - 5 - font.width(info), y + 2, 0xFFBBBBBB, true);
				}
				y += 12;
			}
		}
		// the description
		panel(g, x0, descTop, x5, bottom);
		if (sel != null) {
			int dy = wrap(g, Component.translatable(AlienXCouncil.actionKey(sel.id())).withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(AlienXCouncil.actionKey(sel.id()) + ".desc").withStyle(ChatFormatting.WHITE)), x0 + 5, descTop + 4, x5 - x0 - 10,
					0xFFFFFFFF);
			if (sel.contra() != 0) {
				List<Component> bad = new ArrayList<>();
				for (int i = 0; i < AlienXCouncil.TONES.size(); i++) {
					if ((sel.contra() & (1 << i)) != 0) {
						bad.add(Component.translatable(AlienXCouncil.toneKey(i)));
					}
				}
				MutableComponent list = Component.empty();
				for (int i = 0; i < bad.size(); i++) {
					list.append(i > 0 ? Component.literal(", ") : Component.empty()).append(bad.get(i));
				}
				wrap(g, Component.translatable(P + "council.contra_hint", list), x0 + 5, dy, x5 - x0 - 10, 0xFFFF8070);
			}
		} else {
			wrap(g, Component.translatable(P + "council.how"), x0 + 5, descTop + 4, x5 - x0 - 10, 0xFFBBBBBB);
		}
	}

	private void drawDebate(GuiGraphics g, CompoundTag d, int top, int bottom, int mx, int my) {
		int pad = 8;
		int mid = width / 2;
		// patience pips and the motion
		int turn = d.getInt("turn"), turns = d.getInt("turns");
		Component motion = Component.translatable(P + "council.motion", Component.translatable(AlienXCouncil.actionKey(d.getString("action"))),
				text(d.getString("target")));
		g.drawCenteredString(font, motion, mid, top, 0xFFFFFFFF);
		StringBuilder pips = new StringBuilder();
		for (int i = 0; i < turns; i++) {
			pips.append(i < turns - turn ? '■' : '□');
		}
		Component patience = Component.translatable(P + "council.patience").append(" ").append(Component.literal(pips.toString()));
		g.drawCenteredString(font, patience, mid, top + 11, turns - turn <= 2 ? 0xFFFF7070 : 0xFFDDDDDD);
		int y0 = top + 24;
		// the transcript (left)
		panel(g, pad, y0, mid - 4, bottom);
		transcript(g, pad + 4, y0 + 4, mid - 4 - pad - 8, bottom - 4);
		// the arguments (right)
		panel(g, mid + 4, y0, width - pad, bottom);
		int x = mid + 8, w = width - pad - 4 - x;
		int y = y0 + 4;
		g.drawString(font, Component.translatable(P + "council.arguments").withStyle(ChatFormatting.GOLD), x, y, 0xFFFFFFFF, true);
		y += 12;
		int[] offered = d.getIntArray("offered");
		int[] used = d.getIntArray("used");
		int cb = d.getInt("concernB"), cs = d.getInt("concernS");
		AlienXActions.Action a = AlienXActions.get(d.getString("action"));
		Component preview = null;
		for (int i = 0; i < offered.length; i++) {
			int tone = offered[i];
			int times = 0;
			for (int u : used) {
				times += u == tone ? 1 : 0;
			}
			MutableComponent head = Component.literal("[" + (i + 1) + "] ").withStyle(ChatFormatting.YELLOW)
					.append(Component.translatable(AlienXCouncil.toneKey(tone)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
					.append(Component.literal("  "))
					.append(Component.literal("S" + arrows(AlienXCouncil.SERENA_PREF[tone]) + (tone == cs ? "★" : "")).withStyle(s -> s.withColor(SERENA & 0xFFFFFF)))
					.append(Component.literal(" "))
					.append(Component.literal("B" + arrows(AlienXCouncil.BELLICUS_PREF[tone]) + (tone == cb ? "★" : "")).withStyle(s -> s.withColor(BELLICUS & 0xFFFFFF)));
			if (a != null && (a.contra() & (1 << tone)) != 0) {
				head.append(Component.literal(" ✖").withStyle(ChatFormatting.RED)).append(Component.translatable(P + "council.contradicts").withStyle(ChatFormatting.RED));
			}
			if (times > 0) {
				head.append(Component.translatable(P + "council.used", times).withStyle(ChatFormatting.DARK_GRAY));
			}
			Component say = Component.translatable(P + "council.ben." + AlienXCouncil.TONES.get(tone) + "." + AlienXCouncil.benLine(turn),
					Component.translatable(AlienXCouncil.actionKey(d.getString("action"))), text(d.getString("target"))).withStyle(ChatFormatting.GRAY,
							ChatFormatting.ITALIC);
			// one line a row (the tone, how each head feels, contradictions, repeats); Ben's actual words for the hovered one below
			List<FormattedCharSequence> lines = font.split(head, w - 6);
			int h = 12;
			if (y + h > bottom - 40) {
				break;
			}
			final int t = tone;
			if (row(g, x - 2, y, x + w, y + h, mx, my, false, () -> send(AlienXCouncilPayload.OP_ARGUE, "", t)) || preview == null) {
				preview = say;
			}
			if (!lines.isEmpty()) {
				g.drawString(font, lines.get(0), x + 1, y + 2, 0xFFFFFFFF, true);
			}
			y += h + 2;
		}
		// the hovered argument's words, then the keys
		y += 2;
		if (preview != null) {
			y = wrap(g, preview, x, y, w - 4, 0xFFBBBBBB);
		}
		if (y + 10 <= bottom - 2) {
			List<FormattedCharSequence> keys = font.split(Component.translatable(P + "council.keys"), w - 4);
			int ky = Math.max(y + 2, bottom - 2 - keys.size() * 10);
			for (FormattedCharSequence s : keys) {
				if (ky + 10 > bottom - 1) {
					break;
				}
				g.drawString(font, s, x, ky, 0xFF7A7A7A, true);
				ky += 10;
			}
		}
	}

	private static String arrows(int pref) {
		return pref >= 25 ? "▲▲" : pref > 0 ? "▲" : pref == 0 ? "–" : pref <= -15 ? "▼▼" : "▼";
	}

	private void transcript(GuiGraphics g, int x, int y0, int w, int y1) {
		List<FormattedCharSequence> all = new ArrayList<>();
		for (Tag t : state.getList("lines", Tag.TAG_COMPOUND)) {
			all.addAll(font.split(line((CompoundTag) t), w));
			all.add(FormattedCharSequence.EMPTY);
		}
		int fit = Math.max(1, (y1 - y0) / 10);
		int from = Math.max(0, all.size() - fit);
		int y = y0;
		for (int i = from; i < all.size(); i++) {
			g.drawString(font, all.get(i), x, y, 0xFFFFFFFF, true);
			y += 10;
		}
	}

	private void drawResult(GuiGraphics g, int top, int bottom) {
		boolean carried = state.getBoolean("carried");
		g.drawCenteredString(font, Component.translatable(P + (carried ? "council.banner.carried" : "council.banner.refused")).withStyle(ChatFormatting.BOLD),
				width / 2, top, carried ? 0xFF7CFF6A : 0xFFFF6A5A);
		int x0 = Math.max(8, width / 2 - 170), x1 = Math.min(width - 8, width / 2 + 170);
		panel(g, x0, top + 14, x1, bottom);
		transcript(g, x0 + 4, top + 18, x1 - x0 - 8, bottom - 4);
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		for (Hit h : hits) {
			if (mx >= h.x0() && mx < h.x1() && my >= h.y0() && my < h.y1()) {
				h.click().run();
				layoutButtons();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (phase() == AlienXCouncil.PHASE_DEBATE) {
			int[] offered = state.getCompound("debate").getIntArray("offered");
			if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9 && key - GLFW.GLFW_KEY_1 < offered.length) {
				send(AlienXCouncilPayload.OP_ARGUE, "", offered[key - GLFW.GLFW_KEY_1]);
				return true;
			}
			if (key == GLFW.GLFW_KEY_L) {
				send(AlienXCouncilPayload.OP_LISTEN, "", 0);
				return true;
			}
			if (key == GLFW.GLFW_KEY_W) {
				send(AlienXCouncilPayload.OP_WITHDRAW, "", 0);
				return true;
			}
		} else if (phase() == AlienXCouncil.PHASE_RESULT && key == GLFW.GLFW_KEY_ENTER) {
			send(AlienXCouncilPayload.OP_BACK, "", 0);
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	/** Test / harness hooks: pick an action and a target as a click would. */
	public void select(String actionId, int targetId) {
		AlienXActions.Action a = AlienXActions.get(actionId);
		if (a != null) {
			category = a.category();
			action = actionId;
		}
		target = targetId;
		layoutButtons();
	}
}

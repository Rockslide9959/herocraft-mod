package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.mojang.math.Axis;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.squad.SquadClient;
import com.projecthero.mod.client.squad.SquadLocatorClient;
import com.projecthero.mod.network.SquadInfoPayload;
import com.projecthero.mod.squad.HeroIdentity;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The squad screen (default key P): who is on your team, how they are doing, and where they are.
 *
 * <p>A single framed panel. The header carries the squad's name, your role and a twelve-pip capacity
 * gauge; below it, one card per squadmate: their face, name, the hero identity currently holding their
 * ability slots as a coloured tag, a smoothly animated health bar (with a fading damage trail and a gold
 * absorption overlay), and where they are -- a compass arrow that turns with your own view, the distance
 * and bearing, or the dimension they are in. The leader wears a small crown, your own card is outlined,
 * and anyone offline is greyed out and sinks to the bottom. The list scrolls (wheel, arrow keys or the
 * slim scrollbar) when the squad outgrows the window.
 *
 * <p>Outside a squad -- or on the Commands tab inside one -- the body becomes a help panel listing every
 * {@code /squad} subcommand; clicking one opens chat with it typed in (nothing is ever sent for you).
 *
 * <p>Read-only, and driven entirely by {@link SquadClient}'s cached {@link SquadInfoPayload} -- the server
 * pushes a refresh a few times a second, so the screen stays live while it is open without polling
 * anything. Drawn with plain {@link GuiGraphics} fills and text only: no texture assets.
 */
public final class SquadScreen extends Screen {
	private static final int MAX_MEMBERS = 12; // mirrors SquadManager.MAX_MEMBERS (server-side class)

	// ---- layout (GUI pixels) ----
	private static final int MARGIN = 8;
	private static final int MAX_PANEL_W = 384;
	private static final int PAD = 8;
	private static final int HEADER_H = 34;
	private static final int FOOTER_H = 38;
	private static final int CARD_H = 32;
	private static final int CARD_GAP = 3;
	private static final int FACE = 20;
	private static final int BUTTON_H = 16;
	private static final int SCROLLBAR_W = 3;

	// ---- palette ----
	private static final int PANEL_TOP = 0xF2171B27;
	private static final int PANEL_BOTTOM = 0xF20D1017;
	private static final int PANEL_BORDER = 0xFF363D57;
	private static final int PANEL_INNER = 0x18FFFFFF;
	private static final int DIVIDER = 0xFF262C3F;
	private static final int CARD_BG = 0xB01B2030;
	private static final int CARD_BG_HOVER = 0xC0242B40;
	private static final int CARD_BG_SELF = 0xC0212B45;
	private static final int TEXT = 0xFFE8ECF8;
	private static final int TEXT_BRIGHT = 0xFFFFFFFF;
	private static final int TEXT_DIM = 0xFF8A91AB;
	private static final int TEXT_FAINT = 0xFF5E6480;
	private static final int OFFLINE = 0xFF5A5F72;
	private static final int GOLD = 0xFFFFD24A;
	private static final int ONLINE_GREEN = 0xFF4ADE80;
	private static final int OTHER_DIM = 0xFFB58CFF;
	private static final int HP_RED = 0xFFE0524F;
	private static final int HP_YELLOW = 0xFFF2C94C;
	private static final int HP_GREEN = 0xFF4ADE80;
	private static final int DEFAULT_ACCENT = 0xFF5AA9FF;

	private record HelpEntry(String command, String insert, String descKey) {
	}

	private static final HelpEntry[] HELP = {
			new HelpEntry("/squad create <name>", "/squad create ", "screen.projecthero.squad.help.create"),
			new HelpEntry("/squad invite <player>", "/squad invite ", "screen.projecthero.squad.help.invite"),
			new HelpEntry("/squad accept", "/squad accept", "screen.projecthero.squad.help.accept"),
			new HelpEntry("/squad decline", "/squad decline", "screen.projecthero.squad.help.decline"),
			new HelpEntry("/squad leave", "/squad leave", "screen.projecthero.squad.help.leave"),
			new HelpEntry("/squad list", "/squad list", "screen.projecthero.squad.help.list"),
			new HelpEntry("/squad kick <player>", "/squad kick ", "screen.projecthero.squad.help.kick"),
			new HelpEntry("/squad rename <name>", "/squad rename ", "screen.projecthero.squad.help.rename"),
			new HelpEntry("/squad disband", "/squad disband", "screen.projecthero.squad.help.disband"),
			new HelpEntry("/squad friendlyfire on|off", "/squad friendlyfire ", "screen.projecthero.squad.help.friendlyfire"),
	};

	private record HitBox(int x0, int y0, int x1, int y1, String insert) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	// ---- geometry, recomputed every frame (the roster can grow or shrink while the screen is open) ----
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int viewX;
	private int viewY;
	private int viewW;
	private int viewH;
	private int contentH;
	private boolean scrollable;

	// ---- animation state ----
	private final Map<UUID, float[]> healthAnim = new HashMap<>(); // {shown, trail}
	private long lastFrameMs = -1;
	private float scrollTarget;
	private float scrollShown;
	private boolean draggingThumb;
	private double dragGrabOffset;
	private boolean showHelp;
	private final List<HitBox> helpHitBoxes = new ArrayList<>();

	private FlatButton locatorButton;
	private FlatButton helpButton;
	private FlatButton closeButton;
	/** v0.14.16: the leader's friendly-fire switch (hidden for everyone else). */
	private FlatButton friendlyFireButton;

	public SquadScreen() {
		super(Component.translatable("screen.projecthero.squad"));
	}

	@Override
	protected void init() {
		locatorButton = addRenderableWidget(new FlatButton(locatorButtonLabel(), b -> {
			SquadLocatorClient.toggle();
			b.setMessage(locatorButtonLabel());
		}));
		helpButton = addRenderableWidget(new FlatButton(helpButtonLabel(), b -> {
			showHelp = !showHelp;
			scrollTarget = scrollShown = 0;
			b.setMessage(helpButtonLabel());
		}));
		closeButton = addRenderableWidget(new FlatButton(Component.translatable("gui.done"), b -> onClose()));
		// v0.14.16: only a request -- the server checks the sender leads the squad, then the next roster flips the label
		friendlyFireButton = addRenderableWidget(new FlatButton(friendlyFireButtonLabel(), b -> {
			if (net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
					com.projecthero.mod.network.SquadFriendlyFirePayload.TYPE)) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.projecthero.mod.network.SquadFriendlyFirePayload(!SquadClient.get().friendlyFire()));
			}
		}));
		layout();
	}

	private static Component friendlyFireButtonLabel() {
		boolean on = SquadClient.get().friendlyFire();
		return Component.translatable("screen.projecthero.squad.friendly_fire_button",
				Component.translatable(on ? "options.on" : "options.off")
						.withStyle(on ? ChatFormatting.RED : ChatFormatting.GRAY));
	}

	/** v0.14.16: whether the local player leads the squad on screen. */
	private boolean leading(SquadInfoPayload squad) {
		UUID self = selfId();
		for (SquadInfoPayload.Member m : squad.members()) {
			if (m.leader() && m.id().equals(self)) {
				return true;
			}
		}
		return false;
	}

	private static Component locatorButtonLabel() {
		boolean on = SquadLocatorClient.isEnabled();
		return Component.translatable("screen.projecthero.squad.locator",
				Component.translatable(on ? "options.on" : "options.off")
						.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}

	private Component helpButtonLabel() {
		return Component.translatable(showHelp ? "screen.projecthero.squad.button.roster"
				: "screen.projecthero.squad.button.commands");
	}

	@Override
	public boolean isPauseScreen() {
		return false; // it stays live, and pausing a singleplayer world would freeze the very data it shows
	}

	// =====================================================================================================
	// Layout
	// =====================================================================================================

	private static boolean inSquad(SquadInfoPayload squad) {
		return !squad.squadName().isEmpty() && !squad.members().isEmpty();
	}

	private boolean helpMode(SquadInfoPayload squad) {
		return !inSquad(squad) || showHelp;
	}

	private void layout() {
		SquadInfoPayload squad = SquadClient.get();
		panelW = Math.min(MAX_PANEL_W, this.width - 2 * MARGIN);
		int innerW = panelW - 2 * PAD;

		contentH = helpMode(squad) ? helpContentHeight(innerW - SCROLLBAR_W - 4)
				: squad.members().size() * (CARD_H + CARD_GAP) - CARD_GAP;
		int minBody = 2 * (CARD_H + CARD_GAP);
		int wantH = HEADER_H + PAD + Math.max(contentH, minBody) + PAD + FOOTER_H;
		panelH = Math.min(wantH, this.height - 2 * MARGIN);
		panelX = (this.width - panelW) / 2;
		panelY = (this.height - panelH) / 2;

		viewX = panelX + PAD;
		viewY = panelY + HEADER_H + PAD;
		viewH = Math.max(0, panelH - HEADER_H - FOOTER_H - 2 * PAD);
		scrollable = contentH > viewH;
		viewW = innerW - (scrollable ? SCROLLBAR_W + 4 : 0);
		if (helpMode(squad) && !scrollable) {
			// help text was measured assuming a scrollbar; re-measure at full width so it wraps the same
			contentH = helpContentHeight(viewW);
		}

		float maxScroll = Math.max(0, contentH - viewH);
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
		scrollShown = Mth.clamp(scrollShown, 0, maxScroll);

		layoutButtons(squad);
	}

	private void layoutButtons(SquadInfoPayload squad) {
		if (locatorButton == null) {
			return;
		}
		helpButton.visible = inSquad(squad);
		helpButton.setMessage(helpButtonLabel());
		// v0.14.16: the friendly-fire switch, for the leader only
		friendlyFireButton.visible = inSquad(squad) && leading(squad);
		friendlyFireButton.setMessage(friendlyFireButtonLabel());

		int y = panelY + panelH - FOOTER_H + 6;
		int avail = panelW - 2 * PAD;
		int gap = 4;
		int gaps = 1 + (helpButton.visible ? 1 : 0) + (friendlyFireButton.visible ? 1 : 0);
		int lw = this.font.width(locatorButton.getMessage()) + 16;
		int hw = helpButton.visible ? this.font.width(helpButton.getMessage()) + 16 : 0;
		int fw = friendlyFireButton.visible ? this.font.width(friendlyFireButton.getMessage()) + 16 : 0;
		int cw = Math.max(50, this.font.width(closeButton.getMessage()) + 16);
		int total = lw + hw + fw + cw + gap * gaps;
		if (total > avail) {
			float k = (avail - gap * gaps) / (float) (lw + hw + fw + cw);
			lw = (int) (lw * k);
			hw = (int) (hw * k);
			fw = (int) (fw * k);
			cw = (int) (cw * k);
		}
		int x = panelX + PAD;
		place(locatorButton, x, y, lw);
		x += lw + gap;
		if (helpButton.visible) {
			place(helpButton, x, y, hw);
			x += hw + gap;
		}
		if (friendlyFireButton.visible) {
			place(friendlyFireButton, x, y, fw);
		}
		place(closeButton, panelX + panelW - PAD - cw, y, cw);
	}

	private static void place(FlatButton b, int x, int y, int w) {
		b.setX(x);
		b.setY(y);
		b.setWidth(w);
		b.setHeight(BUTTON_H);
	}

	// =====================================================================================================
	// Rendering
	// =====================================================================================================

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		layout(); // before the buttons are drawn, so they sit in this frame's footer
		drawPanelFrame(g, accentFor(SquadClient.get()));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		SquadInfoPayload squad = SquadClient.get();
		animate(squad);
		super.render(g, mouseX, mouseY, partialTick); // background + frame + footer buttons

		int accent = accentFor(squad);
		if (inSquad(squad)) {
			renderSquadHeader(g, squad, accent);
		} else {
			renderEmptyHeader(g);
		}

		List<Component> tooltip = null;
		g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
		if (helpMode(squad)) {
			renderHelp(g, mouseX, mouseY, accent, squad);
		} else {
			tooltip = renderRoster(g, squad, mouseX, mouseY, partialTick, accent);
		}
		g.disableScissor();
		renderScrollFades(g);
		renderScrollbar(g, mouseX, mouseY);
		renderFooterHint(g);

		if (tooltip != null) {
			g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
		}
	}

	private void drawPanelFrame(GuiGraphics g, int accent) {
		int x0 = panelX;
		int y0 = panelY;
		int x1 = panelX + panelW;
		int y1 = panelY + panelH;
		// drop shadow
		g.fill(x0 + 3, y0 + 3, x1 + 3, y1 + 3, 0x50000000);
		// body
		g.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOTTOM);
		// header glow in the squad's colour
		g.fillGradient(x0 + 1, y0 + 2, x1 - 1, y0 + HEADER_H, withAlpha(accent, 0x2A), withAlpha(accent, 0x00));
		// borders
		g.renderOutline(x0, y0, panelW, panelH, PANEL_BORDER);
		g.renderOutline(x0 + 1, y0 + 1, panelW - 2, panelH - 2, PANEL_INNER);
		// accent rule along the top edge
		g.fill(x0, y0, x1, y0 + 2, accent);
		g.fill(x0, y0 + 2, x1, y0 + 3, withAlpha(accent, 0x55));
		// dividers under the header and above the footer
		g.fill(x0 + PAD, y0 + HEADER_H, x1 - PAD, y0 + HEADER_H + 1, DIVIDER);
		g.fill(x0 + PAD, y1 - FOOTER_H, x1 - PAD, y1 - FOOTER_H + 1, DIVIDER);
	}

	// ---- header ----

	private void renderSquadHeader(GuiGraphics g, SquadInfoPayload squad, int accent) {
		UUID self = selfId();
		boolean leading = false;
		int online = 0;
		for (SquadInfoPayload.Member m : squad.members()) {
			if (m.online()) {
				online++;
			}
			if (m.leader() && m.id().equals(self)) {
				leading = true;
			}
		}

		int right = panelX + panelW - PAD;
		// capacity: "5 / 12" over twelve pips
		int pipsW = MAX_MEMBERS * 5 - 1;
		Component count = Component.translatable("screen.projecthero.squad.count",
				squad.members().size(), MAX_MEMBERS);
		Component label = Component.translatable("screen.projecthero.squad.members");
		int countW = this.font.width(count);
		g.drawString(this.font, count, right - countW, panelY + 7, TEXT_BRIGHT, true);
		g.drawString(this.font, label, right - countW - 4 - this.font.width(label), panelY + 7, TEXT_FAINT, false);
		int pipX = right - pipsW;
		int pipY = panelY + 20;
		List<SquadInfoPayload.Member> sorted = sorted(squad);
		for (int i = 0; i < MAX_MEMBERS; i++) {
			int c;
			if (i < sorted.size()) {
				c = sorted.get(i).online() ? accent : OFFLINE;
			} else {
				c = 0xFF262B3B;
			}
			g.fill(pipX + i * 5, pipY, pipX + i * 5 + 4, pipY + 4, c);
		}

		int x = panelX + PAD;
		int nameMax = pipX - 10 - x;
		if (leading) {
			drawCrown(g, x, panelY + 8, GOLD);
			x += 10;
			nameMax -= 10;
		}
		Component name = Component.literal(squad.squadName()).withStyle(ChatFormatting.BOLD);
		g.drawString(this.font, ellipsize(name, nameMax), x, panelY + 7, TEXT_BRIGHT, true);

		int sx = panelX + PAD;
		int sy = panelY + 19;
		sx += drawChip(g, Component.translatable(leading ? "screen.projecthero.squad.role.leader"
				: "screen.projecthero.squad.role.member"), sx, sy, leading ? GOLD : 0xFF8FB4FF) + 5;
		g.fill(sx, sy + 4, sx + 3, sy + 7, online > 0 ? ONLINE_GREEN : OFFLINE);
		sx += 6;
		Component onlineText = Component.translatable("screen.projecthero.squad.online", online);
		g.drawString(this.font, onlineText, sx, sy + 2, TEXT_DIM, false);
		// v0.14.16: friendly fire, for everyone to see (red while it is on)
		sx += this.font.width(onlineText) + 6;
		boolean ff = squad.friendlyFire();
		Component ffChip = Component.translatable(ff ? "screen.projecthero.squad.friendly_fire.on"
				: "screen.projecthero.squad.friendly_fire.off");
		int room = pipX - 6 - sx;
		if (room > 24) {
			drawChip(g, ffChip, sx, sy, ff ? HP_RED : TEXT_FAINT, room);
		}
	}

	private void renderEmptyHeader(GuiGraphics g) {
		int x = panelX + PAD;
		g.drawString(this.font, Component.translatable("screen.projecthero.squad").withStyle(ChatFormatting.BOLD),
				x, panelY + 7, TEXT_BRIGHT, true);
		g.drawString(this.font, ellipsize(Component.translatable("screen.projecthero.squad.none"),
				panelW - 2 * PAD - 70), x, panelY + 21, TEXT_DIM, false);
		Component chip = Component.translatable("screen.projecthero.squad.role.none");
		int w = this.font.width(chip) + 6;
		drawChip(g, chip, panelX + panelW - PAD - w, panelY + 7, OFFLINE);
	}

	// ---- roster ----

	private List<Component> renderRoster(GuiGraphics g, SquadInfoPayload squad, int mouseX, int mouseY,
			float partialTick, int accent) {
		List<Component> tooltip = null;
		boolean mouseInView = mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH;
		int y = viewY - Math.round(scrollShown);
		UUID self = selfId();
		for (SquadInfoPayload.Member m : sorted(squad)) {
			if (y + CARD_H >= viewY && y <= viewY + viewH) {
				boolean hovered = mouseInView && mouseX >= viewX && mouseX < viewX + viewW
						&& mouseY >= y && mouseY < y + CARD_H;
				renderCard(g, m, viewX, y, viewW, hovered, m.id().equals(self), partialTick, accent);
				if (hovered) {
					tooltip = cardTooltip(m);
				}
			}
			y += CARD_H + CARD_GAP;
		}
		return tooltip;
	}

	private void renderCard(GuiGraphics g, SquadInfoPayload.Member m, int x, int y, int w, boolean hovered,
			boolean self, float partialTick, int accent) {
		boolean online = m.online();
		int identColor = online ? identityColor(m.identityKey()) : OFFLINE;
		float frac = healthFraction(m);

		// card body
		g.fill(x, y, x + w, y + CARD_H, self ? CARD_BG_SELF : (hovered ? CARD_BG_HOVER : CARD_BG));
		g.fill(x, y, x + w, y + 1, hovered ? 0x22FFFFFF : 0x12FFFFFF);
		if (self) {
			g.renderOutline(x, y, w, CARD_H, withAlpha(accent, 0x90));
		} else if (hovered) {
			g.renderOutline(x, y, w, CARD_H, 0x40FFFFFF);
		}
		// accent strip -- pulses red when the member is in trouble
		int strip = identColor;
		if (online && frac < 0.25f) {
			float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() % 100000L) / 140.0f);
			strip = lerpColor(identColor, HP_RED, pulse);
		}
		g.fill(x, y, x + 2, y + CARD_H, strip);
		g.fill(x + 2, y, x + 3, y + CARD_H, withAlpha(strip, 0x50));

		// face
		int fx = x + 9;
		int fy = y + 6;
		g.fill(fx - 1, fy - 1, fx + FACE + 1, fy + FACE + 1, m.leader() ? GOLD : 0xFF07080C);
		PlayerSkin skin = online ? resolveSkin(m.id()) : null;
		if (skin != null) {
			PlayerFaceRenderer.draw(g, skin, fx, fy, FACE);
		} else {
			int base = nameColor(m.name());
			g.fillGradient(fx, fy, fx + FACE, fy + FACE, base, lerpColor(base, 0xFF000000, 0.45f));
			String initial = m.name().isEmpty() ? "?" : m.name().substring(0, 1).toUpperCase(Locale.ROOT);
			g.drawCenteredString(this.font, initial, fx + FACE / 2, fy + (FACE - 8) / 2, TEXT_BRIGHT);
		}
		if (!online) {
			g.fill(fx, fy, fx + FACE, fy + FACE, 0xA0101018);
		}
		if (m.leader()) {
			drawCrown(g, fx + FACE / 2 - 3, y + 1, GOLD);
		}
		// presence dot
		g.fill(fx + FACE - 3, fy + FACE - 3, fx + FACE + 2, fy + FACE + 2, 0xFF07080C);
		g.fill(fx + FACE - 2, fy + FACE - 2, fx + FACE + 1, fy + FACE + 1, online ? ONLINE_GREEN : OFFLINE);

		// right column: where they are
		int rightW = w >= 300 ? 92 : 64;
		int rx = x + w - 7;
		renderLocation(g, m, self, rx, y, rightW, partialTick);

		// middle column: name, identity tag, health
		int tx = fx + FACE + 8;
		int colRight = x + w - 7 - rightW - 6;
		int nameColor = !online ? OFFLINE : (self ? TEXT_BRIGHT : TEXT);
		MutableComponent name = Component.literal(m.name());
		if (self) {
			name.withStyle(ChatFormatting.BOLD);
		}
		FormattedCharSequence nameSeq = ellipsize(name, colRight - tx);
		g.drawString(this.font, nameSeq, tx, y + 6, nameColor, online);
		int nx = tx + this.font.width(nameSeq) + 4;
		if (self) {
			Component you = Component.translatable("screen.projecthero.squad.you");
			if (nx + this.font.width(you) <= colRight) {
				g.drawString(this.font, you, nx, y + 6, TEXT_FAINT, false);
				nx += this.font.width(you) + 4;
			}
		}
		if (online && colRight - nx >= 24) {
			drawChip(g, HeroIdentity.render(m.identityKey()), nx, y + 5, identColor, colRight - nx);
		}

		int ly = y + 18;
		if (!online) {
			int cw = drawChip(g, Component.translatable("screen.projecthero.squad.offline"), tx, ly, OFFLINE);
			g.drawString(this.font, ellipsize(Component.translatable("screen.projecthero.squad.tooltip.offline"),
					colRight - tx - cw - 5), tx + cw + 5, ly + 2, TEXT_FAINT, false);
			return;
		}
		MutableComponent hp = Component.literal(fmt(m.health())).withStyle(s -> s.withColor(TEXT_BRIGHT & 0xFFFFFF))
				.append(Component.literal(" / " + fmt(m.maxHealth())).withStyle(s -> s.withColor(TEXT_DIM & 0xFFFFFF)));
		if (m.absorption() > 0.0f) {
			hp.append(Component.literal(" +" + fmt(m.absorption())).withStyle(s -> s.withColor(GOLD & 0xFFFFFF)));
		}
		int hpW = this.font.width(hp);
		int barW = colRight - tx - hpW - 6;
		if (barW < 24) {
			barW = colRight - tx;
			hpW = -1;
		}
		float[] anim = healthAnim.get(m.id());
		float shown = anim == null ? frac : anim[0];
		float trail = anim == null ? frac : anim[1];
		float absorbFrac = m.maxHealth() <= 0 ? 0 : m.absorption() / m.maxHealth();
		drawHealthBar(g, tx, ly + 2, barW, 5, shown, trail, absorbFrac);
		if (hpW >= 0) {
			g.drawString(this.font, hp, tx + barW + 6, ly + 1, TEXT, false);
		}
	}

	/** Right-aligned two-line block: bearing/distance (or dimension) over raw coordinates. */
	private void renderLocation(GuiGraphics g, SquadInfoPayload.Member m, boolean self, int rx, int y, int maxW,
			float partialTick) {
		if (!m.online()) {
			g.drawString(this.font, "—", rx - this.font.width("—"), y + 6, TEXT_FAINT, false);
			return;
		}
		Player me = this.minecraft == null ? null : this.minecraft.player;
		boolean sameDim = me != null && me.level().dimension().location().getPath().equals(m.dimension());

		FormattedCharSequence coords = ellipsize(Component.translatable("screen.projecthero.squad.coords",
				m.x(), m.y(), m.z()), maxW);
		g.drawString(this.font, coords, rx - this.font.width(coords), y + 19, TEXT_FAINT, false);

		if (self || me == null) {
			FormattedCharSequence dim = ellipsize(dimensionName(m.dimension()), maxW);
			g.drawString(this.font, dim, rx - this.font.width(dim), y + 6, TEXT_DIM, false);
			return;
		}
		if (!sameDim) {
			FormattedCharSequence line = ellipsize(Component.translatable("screen.projecthero.squad.dimension",
					dimensionName(m.dimension())), maxW - 10);
			int lw = this.font.width(line);
			g.drawString(this.font, line, rx - lw, y + 6, OTHER_DIM, false);
			drawDiamond(g, rx - lw - 7, y + 9, OTHER_DIM);
			return;
		}

		double dx = m.x() + 0.5 - me.getX();
		double dz = m.z() + 0.5 - me.getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		Component text = dist < 4.0
				? Component.translatable("screen.projecthero.squad.nearby")
				: Component.translatable("screen.projecthero.squad.distance", (int) Math.round(dist), compass(dx, dz));
		FormattedCharSequence line = ellipsize(text, maxW - 14);
		int lw = this.font.width(line);
		g.drawString(this.font, line, rx - lw, y + 6, TEXT, false);

		int cx = rx - lw - 8;
		int cy = y + 9;
		g.fill(cx - 5, cy - 4, cx + 5, cy + 5, 0xFF0B0D14);
		g.fill(cx - 4, cy - 5, cx + 4, cy + 6, 0xFF0B0D14);
		if (dist < 4.0) {
			g.fill(cx - 1, cy - 1, cx + 1, cy + 2, ONLINE_GREEN);
		} else {
			drawArrow(g, cx, cy + 0.5f, (float) relativeBearing(me, dx, dz, partialTick),
					dist < 32 ? TEXT_BRIGHT : (dist < 128 ? 0xFFC9D2EE : 0xFF8E97B5));
		}
	}

	private List<Component> cardTooltip(SquadInfoPayload.Member m) {
		List<Component> lines = new ArrayList<>();
		MutableComponent title = Component.literal(m.name()).withStyle(ChatFormatting.BOLD);
		if (m.leader()) {
			title.append(Component.literal("  ")).append(Component.translatable("screen.projecthero.squad.role.leader")
					.withStyle(s -> s.withColor(GOLD & 0xFFFFFF).withBold(false)));
		}
		lines.add(title);
		if (!m.online()) {
			lines.add(Component.translatable("screen.projecthero.squad.tooltip.offline").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		lines.add(HeroIdentity.render(m.identityKey()).copy()
				.withStyle(s -> s.withColor(identityColor(m.identityKey()) & 0xFFFFFF)));
		lines.add(Component.translatable("screen.projecthero.squad.tooltip.health", fmt(m.health()), fmt(m.maxHealth()))
				.withStyle(ChatFormatting.GRAY));
		if (m.absorption() > 0.0f) {
			lines.add(Component.translatable("screen.projecthero.squad.tooltip.absorption", fmt(m.absorption()))
					.withStyle(ChatFormatting.GOLD));
		}
		lines.add(Component.translatable("screen.projecthero.squad.tooltip.position", dimensionName(m.dimension()),
				m.x(), m.y(), m.z()).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	// ---- help / empty state ----

	private boolean helpStacked(int width) {
		return width < 300;
	}

	private int helpCommandColumn() {
		int w = 0;
		for (HelpEntry e : HELP) {
			w = Math.max(w, this.font.width(e.command()));
		}
		return w;
	}

	private Component helpIntro() {
		return Component.translatable("screen.projecthero.squad.help.intro", MAX_MEMBERS);
	}

	private int helpRowHeight(int width) {
		return helpStacked(width) ? 22 : 13;
	}

	private int helpContentHeight(int width) {
		int h = 0;
		if (!inSquad(SquadClient.get())) {
			h += this.font.split(Component.translatable("screen.projecthero.squad.none_hint"), width).size() * 10 + 2;
		}
		h += this.font.split(helpIntro(), width).size() * 10 + 6;
		h += 14; // section label
		h += HELP.length * helpRowHeight(width);
		h += 4 + this.font.split(Component.translatable("screen.projecthero.squad.help.click"), width).size() * 10;
		return h;
	}

	private void renderHelp(GuiGraphics g, int mouseX, int mouseY, int accent, SquadInfoPayload squad) {
		helpHitBoxes.clear();
		boolean mouseInView = mouseX >= viewX && mouseX < viewX + viewW && mouseY >= viewY && mouseY < viewY + viewH;
		int x = viewX;
		int y = viewY - Math.round(scrollShown);
		int w = viewW;

		if (!inSquad(squad)) {
			for (FormattedCharSequence line : this.font.split(Component.translatable("screen.projecthero.squad.none_hint"), w)) {
				g.drawString(this.font, line, x, y, TEXT, false);
				y += 10;
			}
			y += 2;
		}
		for (FormattedCharSequence line : this.font.split(helpIntro(), w)) {
			g.drawString(this.font, line, x, y, TEXT_DIM, false);
			y += 10;
		}
		y += 6;

		Component section = Component.translatable("screen.projecthero.squad.help.title");
		g.drawString(this.font, section, x, y, accent, false);
		int sw = this.font.width(section);
		g.fill(x + sw + 6, y + 4, x + w, y + 5, DIVIDER);
		y += 14;

		boolean stacked = helpStacked(w);
		int rowH = helpRowHeight(w);
		int cmdCol = helpCommandColumn() + 10;
		for (int i = 0; i < HELP.length; i++) {
			HelpEntry e = HELP[i];
			boolean hovered = mouseInView && mouseX >= x && mouseX < x + w && mouseY >= y - 2 && mouseY < y - 2 + rowH;
			if (hovered) {
				g.fill(x - 2, y - 2, x + w, y - 2 + rowH, 0x30FFFFFF);
				g.fill(x - 2, y - 2, x - 1, y - 2 + rowH, accent);
			} else if (i % 2 == 0) {
				g.fill(x - 2, y - 2, x + w, y - 2 + rowH, 0x0CFFFFFF);
			}
			boolean leaderOnly = e.command().contains("kick") || e.command().contains("rename")
					|| e.command().contains("disband");
			int cmdColor = hovered ? TEXT_BRIGHT : (leaderOnly ? GOLD : lerpColor(accent, TEXT_BRIGHT, 0.35f));
			g.drawString(this.font, e.command(), x + 2, y, cmdColor, false);
			Component desc = Component.translatable(e.descKey());
			if (stacked) {
				g.drawString(this.font, ellipsize(desc, w - 10), x + 10, y + 10, TEXT_DIM, false);
			} else {
				g.drawString(this.font, ellipsize(desc, w - cmdCol - 4), x + cmdCol, y, TEXT_DIM, false);
			}
			helpHitBoxes.add(new HitBox(x - 2, Math.max(viewY, y - 2), x + w, Math.min(viewY + viewH, y - 2 + rowH),
					e.insert()));
			y += rowH;
		}
		y += 4;
		for (FormattedCharSequence line : this.font.split(Component.translatable("screen.projecthero.squad.help.click")
				.withStyle(ChatFormatting.ITALIC), w)) {
			g.drawString(this.font, line, x, y, TEXT_FAINT, false);
			y += 10;
		}
	}

	// ---- scrolling chrome + footer ----

	private void renderScrollFades(GuiGraphics g) {
		if (!scrollable) {
			return;
		}
		int fade = 8;
		if (scrollShown > 0.5f) {
			g.fillGradient(viewX, viewY, viewX + viewW, viewY + fade, 0xE0121620, 0x00121620);
		}
		if (scrollShown < contentH - viewH - 0.5f) {
			g.fillGradient(viewX, viewY + viewH - fade, viewX + viewW, viewY + viewH, 0x000F121A, 0xE00F121A);
		}
	}

	private int thumbHeight() {
		return Math.max(14, Math.round(viewH * (viewH / (float) contentH)));
	}

	private int thumbY() {
		float max = Math.max(1, contentH - viewH);
		return viewY + Math.round((viewH - thumbHeight()) * (scrollShown / max));
	}

	private int trackX() {
		return panelX + panelW - PAD - SCROLLBAR_W;
	}

	private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY) {
		if (!scrollable) {
			return;
		}
		int tx = trackX();
		g.fill(tx, viewY, tx + SCROLLBAR_W, viewY + viewH, 0x40000000);
		int ty = thumbY();
		int th = thumbHeight();
		boolean hot = draggingThumb || (mouseX >= tx - 2 && mouseX < tx + SCROLLBAR_W + 2 && mouseY >= ty && mouseY < ty + th);
		g.fill(tx, ty, tx + SCROLLBAR_W, ty + th, hot ? 0xFFC9D2EE : 0xFF5B6380);
	}

	private void renderFooterHint(GuiGraphics g) {
		MutableComponent hint = Component.translatable("screen.projecthero.squad.footer",
				ModKeyBindings.SQUAD_MENU.getTranslatedKeyMessage());
		if (scrollable) {
			hint.append(Component.literal("  ·  ")).append(Component.translatable("screen.projecthero.squad.scroll"));
		} else if (inSquad(SquadClient.get())) {
			hint.append(Component.literal("  ·  ")).append(Component.translatable("screen.projecthero.squad.footer_invite"));
		}
		FormattedCharSequence seq = ellipsize(hint, panelW - 2 * PAD);
		int y = panelY + panelH - 13;
		if (y < panelY + panelH - FOOTER_H + 6 + BUTTON_H + 1) {
			return; // window too short for the hint; the buttons matter more
		}
		g.drawString(this.font, seq, panelX + (panelW - this.font.width(seq)) / 2, y, TEXT_FAINT, false);
	}

	// =====================================================================================================
	// Input
	// =====================================================================================================

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.SQUAD_MENU.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		switch (keyCode) {
			case 265 -> { // up
				scrollBy(-(CARD_H + CARD_GAP));
				return true;
			}
			case 264 -> { // down
				scrollBy(CARD_H + CARD_GAP);
				return true;
			}
			case 266 -> { // page up
				scrollBy(-viewH);
				return true;
			}
			case 267 -> { // page down
				scrollBy(viewH);
				return true;
			}
			default -> {
				return super.keyPressed(keyCode, scanCode, modifiers);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollBy((float) (-scrollY * 24));
		return true;
	}

	private void scrollBy(float amount) {
		scrollTarget = Mth.clamp(scrollTarget + amount, 0, Math.max(0, contentH - viewH));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0) {
			return false;
		}
		if (scrollable) {
			int tx = trackX();
			if (mouseX >= tx - 2 && mouseX < tx + SCROLLBAR_W + 2 && mouseY >= viewY && mouseY < viewY + viewH) {
				int ty = thumbY();
				int th = thumbHeight();
				if (mouseY < ty || mouseY >= ty + th) {
					// jump so the thumb centres on the click, then drag from there
					setScrollFromThumb(mouseY - th / 2.0);
					ty = thumbY();
				}
				draggingThumb = true;
				dragGrabOffset = mouseY - ty;
				return true;
			}
		}
		if (helpMode(SquadClient.get()) && this.minecraft != null) {
			for (HitBox box : helpHitBoxes) {
				if (box.contains(mouseX, mouseY)) {
					this.minecraft.setScreen(new ChatScreen(box.insert()));
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingThumb) {
			setScrollFromThumb(mouseY - dragGrabOffset);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingThumb = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void setScrollFromThumb(double thumbTop) {
		int travel = viewH - thumbHeight();
		float max = Math.max(0, contentH - viewH);
		float t = travel <= 0 ? 0 : (float) Mth.clamp((thumbTop - viewY) / travel, 0.0, 1.0);
		scrollTarget = scrollShown = t * max;
	}

	// =====================================================================================================
	// Animation
	// =====================================================================================================

	private void animate(SquadInfoPayload squad) {
		long now = Util.getMillis();
		float dt = lastFrameMs < 0 ? 0.0f : Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
		lastFrameMs = now;

		float fast = 1.0f - (float) Math.exp(-12.0 * dt);
		float slow = 1.0f - (float) Math.exp(-2.2 * dt);
		for (SquadInfoPayload.Member m : squad.members()) {
			float target = healthFraction(m);
			float[] s = healthAnim.get(m.id());
			if (s == null) {
				healthAnim.put(m.id(), new float[] {target, target});
				continue;
			}
			s[0] += (target - s[0]) * fast;
			if (s[1] < s[0]) {
				s[1] = s[0];
			} else {
				s[1] += (s[0] - s[1]) * slow;
			}
		}
		scrollShown += (scrollTarget - scrollShown) * (1.0f - (float) Math.exp(-18.0 * dt));
		if (Math.abs(scrollTarget - scrollShown) < 0.25f) {
			scrollShown = scrollTarget;
		}
	}

	// =====================================================================================================
	// Drawing helpers
	// =====================================================================================================

	private void drawHealthBar(GuiGraphics g, int x, int y, int w, int h, float shown, float trail, float absorb) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF07080C);
		g.fill(x, y, x + w, y + h, 0xFF1A1E2A);
		int shownW = Math.round(w * Mth.clamp(shown, 0, 1));
		int trailW = Math.round(w * Mth.clamp(trail, 0, 1));
		if (trailW > shownW) {
			g.fill(x + shownW, y, x + trailW, y + h, 0xD0FFE6D0);
		}
		int c = healthColor(shown);
		if (shownW > 0) {
			g.fillGradient(x, y, x + shownW, y + h, lerpColor(c, 0xFFFFFFFF, 0.25f), lerpColor(c, 0xFF000000, 0.2f));
			g.fill(x, y, x + shownW, y + 1, 0x50FFFFFF);
		}
		for (int i = 1; i < 5; i++) {
			int tx = x + w * i / 5;
			g.fill(tx, y, tx + 1, y + h, 0x50000000);
		}
		if (absorb > 0.0f) {
			int aw = Math.max(1, Math.round(w * Math.min(1.0f, absorb)));
			g.fill(x, y, x + aw, y + 2, GOLD);
		}
	}

	/** Returns the chip's width. */
	private int drawChip(GuiGraphics g, Component text, int x, int y, int color) {
		return drawChip(g, text, x, y, color, Integer.MAX_VALUE);
	}

	private int drawChip(GuiGraphics g, Component text, int x, int y, int color, int maxW) {
		FormattedCharSequence seq = ellipsize(text, maxW - 6);
		int w = this.font.width(seq) + 6;
		int h = 11;
		g.fill(x, y, x + w, y + h, withAlpha(color, 0x2E));
		g.renderOutline(x, y, w, h, withAlpha(color, 0x8C));
		g.drawString(this.font, seq, x + 3, y + 2, color, false);
		return w;
	}

	/** A 7x5 pixel crown. */
	private static void drawCrown(GuiGraphics g, int x, int y, int color) {
		int shade = lerpColor(color, 0xFF000000, 0.35f);
		g.fill(x, y, x + 1, y + 1, color);
		g.fill(x + 3, y, x + 4, y + 1, color);
		g.fill(x + 6, y, x + 7, y + 1, color);
		g.fill(x, y + 1, x + 2, y + 2, color);
		g.fill(x + 3, y + 1, x + 4, y + 2, color);
		g.fill(x + 5, y + 1, x + 7, y + 2, color);
		g.fill(x, y + 2, x + 7, y + 3, color);
		g.fill(x, y + 3, x + 7, y + 4, shade);
	}

	private static void drawDiamond(GuiGraphics g, int cx, int cy, int color) {
		g.fill(cx - 1, cy - 3, cx + 1, cy - 2, color);
		g.fill(cx - 2, cy - 2, cx + 2, cy - 1, color);
		g.fill(cx - 3, cy - 1, cx + 3, cy + 1, color);
		g.fill(cx - 2, cy + 1, cx + 2, cy + 2, color);
		g.fill(cx - 1, cy + 2, cx + 1, cy + 3, color);
	}

	/** A small arrow pointing "up" (= the way you are facing), rotated clockwise by {@code degrees}. */
	private static void drawArrow(GuiGraphics g, float cx, float cy, float degrees, int color) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0.0f);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		pose.translate(-0.5f, -0.5f, 0.0f);
		// head
		g.fill(0, -4, 1, -3, color);
		g.fill(-1, -3, 2, -2, color);
		g.fill(-2, -2, 3, -1, color);
		g.fill(-3, -1, 4, 0, color);
		// stem
		g.fill(-1, 0, 2, 4, color);
		pose.popPose();
	}

	private FormattedCharSequence ellipsize(Component text, int maxW) {
		if (maxW <= 0) {
			return FormattedCharSequence.EMPTY;
		}
		if (this.font.width(text) <= maxW) {
			return text.getVisualOrderText();
		}
		int ellipsisW = this.font.width("…");
		FormattedText cut = this.font.substrByWidth(text, Math.max(0, maxW - ellipsisW));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
	}

	// =====================================================================================================
	// Data helpers
	// =====================================================================================================

	/** Leader first, then everyone online, then the offline -- otherwise the server's own order. */
	private static List<SquadInfoPayload.Member> sorted(SquadInfoPayload squad) {
		List<SquadInfoPayload.Member> list = new ArrayList<>(squad.members());
		list.sort(Comparator.comparingInt((SquadInfoPayload.Member m) -> m.leader() ? 0 : (m.online() ? 1 : 2)));
		return list;
	}

	private UUID selfId() {
		return this.minecraft == null || this.minecraft.player == null ? null : this.minecraft.player.getUUID();
	}

	private static float healthFraction(SquadInfoPayload.Member m) {
		return m.maxHealth() <= 0 ? 0.0f : Mth.clamp(m.health() / m.maxHealth(), 0.0f, 1.0f);
	}

	private static String fmt(float v) {
		return String.format(Locale.ROOT, "%.0f", Math.ceil(v));
	}

	private static PlayerSkin resolveSkin(UUID id) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return null;
		}
		PlayerInfo info = client.getConnection().getPlayerInfo(id);
		return info == null ? null : info.getSkin();
	}

	/**
	 * Signed angle, in degrees, from where the local player is looking to the squadmate: 0 = dead ahead,
	 * positive = to the right. Same 2D cross/dot trick as {@link SquadLocatorBarHud}.
	 */
	private static double relativeBearing(Player me, double dx, double dz, float partialTick) {
		Vec3 look = me.getViewVector(partialTick);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-6) {
			float yaw = me.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
			flat = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		}
		flat = flat.normalize();
		Vec3 to = new Vec3(dx, 0.0, dz).normalize();
		double cross = flat.x * to.z - flat.z * to.x;
		double dot = flat.dot(to);
		return Math.toDegrees(Math.atan2(cross, dot));
	}

	private static Component dimensionName(String path) {
		return switch (path) {
			case "overworld", "the_nether", "the_end" -> Component.translatable("screen.projecthero.squad.dim." + path);
			default -> {
				String spaced = path.replace('_', ' ');
				yield Component.literal(spaced.isEmpty() ? "?" : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1));
			}
		};
	}

	/** Minecraft's +X is east and +Z is south, so a bearing reads straight off the two deltas. */
	private static String compass(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
		String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return points[index];
	}

	private static int accentFor(SquadInfoPayload squad) {
		if (!inSquad(squad)) {
			return DEFAULT_ACCENT;
		}
		float hue = (squad.squadName().hashCode() & 0xFFFF) / 65535.0f;
		return 0xFF000000 | Mth.hsvToRgb(hue, 0.55f, 0.98f);
	}

	private static int nameColor(String name) {
		float hue = (name.hashCode() & 0xFFFF) / 65535.0f;
		return 0xFF000000 | Mth.hsvToRgb(hue, 0.45f, 0.75f);
	}

	private static int identityColor(String key) {
		if (key == null || key.isEmpty()) {
			return 0xFF8A91AB;
		}
		String id = key.startsWith("projecthero.squad.identity.") ? key.substring("projecthero.squad.identity.".length()) : null;
		if (id != null) {
			switch (id) {
				case "thor": return 0xFF6FB7FF;
				case "iron_man": return 0xFFFF5A4E;
				case "spider_man": return 0xFFFF4D6A;
				case "max_steel": return 0xFF3FB6FF;
				case "punisher": return 0xFFDADDE6;
				case "agent_venom": return 0xFF9C8CFF;
				case "green_lantern": return 0xFF3EE06A;
				case "titan_shifter": return 0xFFE0A070;
				case "all_might": return 0xFFFFD24A;
				case "moon_knight": return 0xFFEDEAF5;
				case "hulk": return 0xFF7BE04A;
				case "kryptonian": return 0xFFFFC83C;
				case "banner": return 0xFFA6D08A;
				case "wolverine": return 0xFFF5C542;
				case "super_soldier": return 0xFF4A7BD8;
				case "symbiote": return 0xFFB4B4D8;
				case "none": return 0xFF8A91AB;
				default: break;
			}
		}
		// mutations and anything new: a stable colour from the key
		float hue = (key.hashCode() & 0xFFFF) / 65535.0f;
		return 0xFF000000 | Mth.hsvToRgb(hue, 0.5f, 1.0f);
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha << 24) | (color & 0xFFFFFF);
	}

	private static int lerpColor(int a, int b, float t) {
		t = Mth.clamp(t, 0.0f, 1.0f);
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	/** Red at empty, yellow at half, green at full. */
	private static int healthColor(float f) {
		f = Mth.clamp(f, 0.0f, 1.0f);
		return f < 0.5f ? lerpColor(HP_RED, HP_YELLOW, f / 0.5f) : lerpColor(HP_YELLOW, HP_GREEN, (f - 0.5f) / 0.5f);
	}

	// =====================================================================================================
	// Widgets
	// =====================================================================================================

	/** A flat, dark button that matches the panel instead of vanilla's stone-texture one. */
	private static final class FlatButton extends Button {
		FlatButton(Component message, OnPress onPress) {
			super(0, 0, 60, BUTTON_H, message, onPress, DEFAULT_NARRATION);
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
			// keyboard focus lights it up too, but a mouse click must not leave it stuck "hot"
			boolean hot = this.active && (this.isHovered()
					|| (this.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()));
			int x = getX();
			int y = getY();
			int w = getWidth();
			int h = getHeight();
			g.fillGradient(x, y, x + w, y + h, hot ? 0xFF2E3654 : 0xFF1F2436, hot ? 0xFF232A42 : 0xFF171B29);
			g.renderOutline(x, y, w, h, hot ? 0xFF8FA6E6 : 0xFF3A4160);
			g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
			Font font = Minecraft.getInstance().font;
			Component msg = getMessage();
			int maxW = w - 6;
			FormattedCharSequence seq;
			if (font.width(msg) <= maxW) {
				seq = msg.getVisualOrderText();
			} else {
				FormattedText cut = font.substrByWidth(msg, Math.max(0, maxW - font.width("…")));
				seq = Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
			}
			int color = !this.active ? 0xFF6A7088 : (hot ? 0xFFFFFFFF : 0xFFD0D6EA);
			g.drawString(font, seq, x + (w - font.width(seq)) / 2, y + (h - 8) / 2, color, false);
		}
	}
}

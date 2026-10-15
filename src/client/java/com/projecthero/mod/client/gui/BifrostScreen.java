package com.projecthero.mod.client.gui;

import com.projecthero.mod.network.BifrostActionPayload;
import com.projecthero.mod.network.BifrostScreenPayload;
import com.projecthero.mod.stormbreaker.BifrostWaypoints;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Stormbreaker's Bifrost screen (v0.14.20), opened by Sneak + right-click. Type X / Y / Z (prefilled with where you
 * stand) and open the Bifrost, or use one of three saved waypoints: each has a name, its coordinates and dimension, and
 * Save here / Go / clear buttons. The footer shows the Bifrost's 60 s cooldown.
 *
 * <p>Purely a request form -- the server re-checks every button (see {@code Bifrost.handleAction}) and answers with a
 * refreshed {@link BifrostScreenPayload}. Waypoints saved in another dimension are greyed out: the Bifrost only
 * travels within the world you are in. Drawn with plain fills and text, in Asgard's gold on night blue with the
 * rainbow bridge running under the title.
 *
 * <p>v0.16.5: the same screen is Cyborg's <b>Boom Tube menu</b> (Shift+V, user: "works the same as the bifrost") through a
 * {@link Theme}: its own words and colours (Mother Box teal on black), its own payloads, and waypoints in another
 * dimension stay usable -- a Boom Tube crosses worlds.
 */
public final class BifrostScreen extends Screen {
	/** Sends a button press to the server. */
	@FunctionalInterface
	public interface Sender {
		void send(int action, int slot, int x, int y, int z, String name);
	}

	/**
	 * Everything that differs between the Bifrost and the Boom Tube menu. {@code keys} is the lang prefix
	 * ({@code <keys>.title} etc.); the action numbers match on both payloads.
	 */
	public record Theme(String keys, int accent, int accentDim, int panelTop, int panelBottom, int[] stripe, int cooldownMax,
			boolean crossDimension, Sender sender) {
	}

	public static final Theme BIFROST = new Theme("screen.projecthero.bifrost", 0xFFE8C766, 0xFF8C7536, 0xF2141A2E, 0xF20A0D18,
			new int[] { 0xFFFF4040, 0xFFFF9020, 0xFFFFE840, 0xFF40E860, 0xFF3C8CFF, 0xFF5A40E6, 0xFFB050FF },
			com.projecthero.mod.stormbreaker.Bifrost.COOLDOWN_TICKS, false,
			(action, slot, x, y, z, name) -> ClientPlayNetworking.send(new BifrostActionPayload(action, slot, x, y, z, name)));

	public static final Theme BOOM_TUBE = new Theme("screen.projecthero.cyborg_boom", 0xFF6FE0FF, 0xFF2F7690, 0xF20E1A20, 0xF2060A0E,
			new int[] { 0xFFFFFFFF, 0xFFD8F6FF, 0xFF9AE8FF, 0xFF5FD6FF, 0xFF3AA8E0, 0xFF9AE8FF, 0xFFFFFFFF },
			com.projecthero.mod.cyborg.CyborgConfig.BOOM_COOLDOWN, true,
			(action, slot, x, y, z, name) -> ClientPlayNetworking.send(new com.projecthero.mod.cyborg.network.CyborgBoomActionPayload(action, slot,
					x, y, z, name)));

	private static final int PANEL_W = 300;
	private static final int PANEL_H = 196;
	private static final int ROW_H = 30;

	private static final int TEXT = 0xFFE8ECF8;
	private static final int MUTED = 0xFF8D94AD;
	private static final int ROW_BG = 0x60283050;
	private static final int ROW_BG_OFF = 0x40181C2C;
	private final Theme theme;
	private BifrostWaypoints waypoints;
	private String dimension;
	/** Client game time at which the Bifrost is ready (from the server's tick count when the payload arrived). */
	private long readyAt;

	private final String[] coordDraft = new String[3];
	private final String[] nameDraft = new String[BifrostWaypoints.SLOTS];
	private EditBox fx;
	private EditBox fy;
	private EditBox fz;
	private final EditBox[] names = new EditBox[BifrostWaypoints.SLOTS];
	private Button travel;
	private final Button[] useButtons = new Button[BifrostWaypoints.SLOTS];

	private BifrostScreen(Theme theme, BifrostWaypoints waypoints, int cooldownTicks, String dimension) {
		super(Component.translatable(theme.keys() + ".title"));
		this.theme = theme;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			coordDraft[0] = Integer.toString(mc.player.blockPosition().getX());
			coordDraft[1] = Integer.toString(mc.player.blockPosition().getY());
			coordDraft[2] = Integer.toString(mc.player.blockPosition().getZ());
		} else {
			coordDraft[0] = coordDraft[1] = coordDraft[2] = "0";
		}
		accept(waypoints, cooldownTicks, dimension, true);
	}

	/** Client receiver: open a fresh screen, or refresh one that is already open. */
	public static void receive(BifrostScreenPayload payload) {
		receive(BIFROST, payload.open(), payload.waypoints(), payload.cooldownTicks(), payload.dimension());
	}

	/** v0.16.5: Cyborg's Boom Tube menu. */
	public static void receiveBoomTube(com.projecthero.mod.cyborg.network.CyborgBoomScreenPayload payload) {
		receive(BOOM_TUBE, payload.open(), payload.waypoints(), payload.cooldownTicks(), payload.dimension());
	}

	private static void receive(Theme theme, boolean open, BifrostWaypoints waypoints, int cooldownTicks, String dimension) {
		Minecraft mc = Minecraft.getInstance();
		if (open) {
			mc.setScreen(new BifrostScreen(theme, waypoints, cooldownTicks, dimension));
		} else if (mc.screen instanceof BifrostScreen screen && screen.theme == theme) {
			screen.keepDrafts();
			screen.accept(waypoints, cooldownTicks, dimension, false);
			screen.rebuildWidgets();
		}
	}

	private Component tr(String key, Object... args) {
		return Component.translatable(theme.keys() + "." + key, args);
	}

	/** Whether waypoint {@code w} can be travelled to from here. */
	private boolean reachable(BifrostWaypoints.Waypoint w) {
		return !w.isEmpty() && (theme.crossDimension() || w.dimension().equals(dimension));
	}

	private void accept(BifrostWaypoints waypoints, int cooldownTicks, String dimension, boolean first) {
		BifrostWaypoints old = this.waypoints;
		this.waypoints = waypoints;
		this.dimension = dimension;
		this.readyAt = gameTime() + cooldownTicks;
		for (int i = 0; i < BifrostWaypoints.SLOTS; i++) {
			BifrostWaypoints.Waypoint w = waypoints.get(i);
			boolean changed = old == null || !old.get(i).equals(w);
			if (first || (changed && !w.isEmpty())) {
				nameDraft[i] = w.isEmpty() ? "" : w.name();
			}
		}
	}

	private void keepDrafts() {
		if (fx != null) {
			coordDraft[0] = fx.getValue();
			coordDraft[1] = fy.getValue();
			coordDraft[2] = fz.getValue();
		}
		for (int i = 0; i < names.length; i++) {
			if (names[i] != null) {
				nameDraft[i] = names[i].getValue();
			}
		}
	}

	private static long gameTime() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	private int cooldownTicks() {
		return (int) Math.max(0L, readyAt - gameTime());
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - PANEL_H) / 2);
	}

	@Override
	protected void init() {
		int l = left();
		int t = top();

		fx = coordBox(l + 22, t + 40, coordDraft[0]);
		fy = coordBox(l + 86, t + 40, coordDraft[1]);
		fz = coordBox(l + 150, t + 40, coordDraft[2]);
		addRenderableWidget(fx);
		addRenderableWidget(fy);
		addRenderableWidget(fz);
		travel = addRenderableWidget(Button.builder(tr("travel"), b -> travelCoords())
				.bounds(l + 210, t + 39, 80, 20).build());

		for (int i = 0; i < BifrostWaypoints.SLOTS; i++) {
			final int slot = i;
			int y = t + 80 + i * ROW_H;
			BifrostWaypoints.Waypoint w = waypoints.get(i);
			EditBox name = new EditBox(this.font, l + 12, y + 2, 92, 14, tr("name"));
			name.setMaxLength(BifrostWaypoints.MAX_NAME_LENGTH);
			name.setValue(nameDraft[i] == null ? "" : nameDraft[i]);
			name.setHint(Component.literal("Waypoint " + (i + 1)).withColor(MUTED));
			names[i] = addRenderableWidget(name);

			addRenderableWidget(Button.builder(tr("save"), b -> save(slot))
					.bounds(l + 168, y + 2, 56, 18)
					.tooltip(Tooltip.create(tr("save.tip"))).build());

			Button use = Button.builder(tr("use"), b -> travelWaypoint(slot))
					.bounds(l + 228, y + 2, 40, 18).build();
			if (w.isEmpty()) {
				use.active = false;
			} else if (!reachable(w)) {
				use.active = false;
				use.setTooltip(Tooltip.create(tr("other_dimension", dimensionName(w.dimension()))));
			}
			useButtons[i] = addRenderableWidget(use);

			Button clear = Button.builder(Component.literal("✕"), b -> send(BifrostActionPayload.CLEAR, slot, 0, 0, 0, ""))
					.bounds(l + 272, y + 2, 18, 18)
					.tooltip(Tooltip.create(tr("clear"))).build();
			clear.active = !w.isEmpty();
			addRenderableWidget(clear);
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(l + PANEL_W - 62, t + PANEL_H - 24, 52, 18).build());
		updateActive();
	}

	private EditBox coordBox(int x, int y, String value) {
		EditBox box = new EditBox(this.font, x, y, 56, 18, Component.literal("coord"));
		box.setMaxLength(9);
		box.setValue(value);
		box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,8}"));
		return box;
	}

	private void updateActive() {
		boolean ready = cooldownTicks() <= 0;
		if (travel != null) {
			travel.active = ready && parse(fx) != null && parse(fy) != null && parse(fz) != null;
		}
		for (int i = 0; i < useButtons.length; i++) {
			BifrostWaypoints.Waypoint w = waypoints.get(i);
			if (useButtons[i] != null) {
				useButtons[i].active = ready && reachable(w);
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		updateActive();
	}

	private void travelCoords() {
		Integer x = parse(fx);
		Integer y = parse(fy);
		Integer z = parse(fz);
		if (x == null || y == null || z == null) {
			return;
		}
		send(BifrostActionPayload.TRAVEL_COORDS, 0, x, y, z, "");
		onClose();
	}

	private void travelWaypoint(int slot) {
		send(BifrostActionPayload.TRAVEL_WAYPOINT, slot, 0, 0, 0, "");
		onClose();
	}

	private void save(int slot) {
		send(BifrostActionPayload.SAVE, slot, 0, 0, 0, names[slot].getValue());
	}

	private void send(int action, int slot, int x, int y, int z, String name) {
		theme.sender().send(action, slot, x, y, z, name);
	}

	private static Integer parse(EditBox box) {
		if (box == null) {
			return null;
		}
		try {
			return Integer.parseInt(box.getValue().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	static String dimensionName(String id) {
		return switch (id) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "The Nether";
			case "minecraft:the_end" -> "The End";
			default -> {
				String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
				yield path.isEmpty() ? "?" : Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
			}
		};
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		int r = l + PANEL_W;
		int b = t + PANEL_H;
		// frame: gold outline, night-blue body
		g.fill(l - 1, t - 1, r + 1, b + 1, theme.accentDim());
		g.fillGradient(l, t, r, b, theme.panelTop(), theme.panelBottom());
		g.fill(l + 2, t + 2, r - 2, t + 3, 0x40FFFFFF);
		// the rainbow bridge under the title
		int[] stripe = theme.stripe();
		int bandW = (PANEL_W - 24) / stripe.length;
		for (int i = 0; i < stripe.length; i++) {
			int x0 = l + 12 + i * bandW;
			g.fill(x0, t + 22, x0 + bandW, t + 24, stripe[i]);
		}
		// waypoint rows
		for (int i = 0; i < BifrostWaypoints.SLOTS; i++) {
			int y = t + 80 + i * ROW_H;
			BifrostWaypoints.Waypoint w = waypoints.get(i);
			boolean here = reachable(w);
			g.fill(l + 8, y - 2, r - 8, y + ROW_H - 4, here ? ROW_BG : ROW_BG_OFF);
			g.fill(l + 8, y - 2, l + 10, y + ROW_H - 4, w.isEmpty() ? theme.accentDim() : stripe[(i * 3) % stripe.length]);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		int accent = theme.accent();
		g.drawCenteredString(this.font, this.title, l + PANEL_W / 2, t + 9, accent);

		g.drawString(this.font, tr("coords"), l + 12, t + 29, MUTED, false);
		g.drawString(this.font, "X", l + 12, t + 45, accent, false);
		g.drawString(this.font, "Y", l + 77, t + 45, accent, false);
		g.drawString(this.font, "Z", l + 141, t + 45, accent, false);

		g.drawString(this.font, tr("waypoints"), l + 12, t + 68, MUTED, false);
		for (int i = 0; i < BifrostWaypoints.SLOTS; i++) {
			int y = t + 80 + i * ROW_H;
			BifrostWaypoints.Waypoint w = waypoints.get(i);
			if (w.isEmpty()) {
				g.drawString(this.font, tr("empty"), l + 110, y + 7, MUTED, false);
			} else {
				boolean here = reachable(w);
				g.enableScissor(l + 108, y - 2, l + 166, y + 24);
				g.drawString(this.font, w.x() + " " + w.y() + " " + w.z(), l + 110, y + 2, here ? TEXT : MUTED, false);
				g.drawString(this.font, dimensionName(w.dimension()), l + 110, y + 12, here ? theme.accentDim() : 0xFF6A6F80, false);
				g.disableScissor();
				if (!here) {
					g.drawString(this.font, tr("elsewhere"), l + 12, y + 18,
							0xFFB06060, false);
				}
			}
		}

		// cooldown footer
		int fy0 = t + PANEL_H - 20;
		int cd = cooldownTicks();
		int barX = l + 12;
		int barW = 150;
		float frac = 1.0f - Mth.clamp(cd / (float) theme.cooldownMax(), 0.0f, 1.0f);
		g.fill(barX - 1, fy0 + 9, barX + barW + 1, fy0 + 14, 0xFF000000);
		int filled = Math.round(barW * frac);
		for (int x = 0; x < filled; x++) {
			g.fill(barX + x, fy0 + 10, barX + x + 1, fy0 + 13, stripe(x, barW));
		}
		Component status = cd <= 0 ? tr("ready") : tr("recharging", (cd + 19) / 20);
		g.drawString(this.font, status, barX, fy0, cd <= 0 ? accent : MUTED, false);
	}

	private int stripe(int x, int width) {
		int[] stripe = theme.stripe();
		return stripe[Math.min(stripe.length - 1, x * stripe.length / width)];
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

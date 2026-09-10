package com.projecthero.mod.client.gui;

import java.util.Locale;

import com.projecthero.mod.client.squad.SquadClient;
import com.projecthero.mod.network.SquadInfoPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The squad screen (default key P): who is on your team, how they are doing, and where they are.
 *
 * <p>Every row is one squadmate — health bar, hero identity, dimension, and their position expressed
 * both as raw coordinates and as a distance-and-compass-bearing from you, since "Nether, 340 blocks
 * north-east" is what you actually need mid-fight and raw XYZ is what you need afterwards. Members in
 * another dimension are shown greyed, because a bearing across dimensions is meaningless.
 *
 * <p>Read-only, and driven entirely by {@link SquadClient}'s cached
 * {@link SquadInfoPayload} — the server pushes a refresh a few times a second, so the screen stays live
 * while it is open without polling anything.
 */
public final class SquadScreen extends Screen {
	private static final int ROW_H = 34;
	private static final int PANEL_W = 320;

	private static final int COLOR_PANEL = 0xC0101018;
	private static final int COLOR_BORDER = 0xFF2E2E44;
	private static final int COLOR_LEADER = 0xFFFFD24A;
	private static final int COLOR_NAME = 0xFFE8E8F6;
	private static final int COLOR_DIM = 0xFF8A90A8;
	private static final int COLOR_OFFLINE = 0xFF5A5F72;
	private static final int COLOR_HP = 0xFF4ADE80;
	private static final int COLOR_HP_LOW = 0xFFE05252;
	private static final int COLOR_ABSORB = 0xFFFFD24A;

	private int scroll;

	public SquadScreen() {
		super(Component.translatable("screen.projecthero.squad"));
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
	}

	@Override
	public boolean isPauseScreen() {
		return false; // it stays live, and pausing a singleplayer world would freeze the very data it shows
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
		return true;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		SquadInfoPayload squad = SquadClient.get();

		int left = this.width / 2 - PANEL_W / 2;
		int top = 30;
		int bottom = this.height - 40;

		if (squad.squadName().isEmpty() || squad.members().isEmpty()) {
			graphics.drawCenteredString(this.font, Component.translatable("screen.projecthero.squad.none"),
					this.width / 2, this.height / 2 - 18, COLOR_NAME);
			graphics.drawCenteredString(this.font, Component.translatable("screen.projecthero.squad.none_hint"),
					this.width / 2, this.height / 2 - 4, COLOR_DIM);
			return;
		}

		graphics.drawCenteredString(this.font, Component.translatable("screen.projecthero.squad.title",
				squad.squadName(), squad.members().size()), this.width / 2, 14, COLOR_NAME);

		int rows = Math.max(1, (bottom - top) / ROW_H);
		int maxScroll = Math.max(0, squad.members().size() - rows);
		scroll = Mth.clamp(scroll, 0, maxScroll);

		int y = top;
		for (int i = scroll; i < squad.members().size() && y + ROW_H <= bottom; i++) {
			renderMember(graphics, squad.members().get(i), left, y);
			y += ROW_H;
		}
		if (maxScroll > 0) {
			graphics.drawCenteredString(this.font, Component.translatable("screen.projecthero.squad.scroll"),
					this.width / 2, bottom + 2, COLOR_DIM);
		}
	}

	private void renderMember(GuiGraphics graphics, SquadInfoPayload.Member m, int x, int y) {
		graphics.fill(x, y, x + PANEL_W, y + ROW_H - 3, COLOR_PANEL);
		graphics.renderOutline(x, y, PANEL_W, ROW_H - 3, COLOR_BORDER);

		Component name = Component.literal(m.name());
		graphics.drawString(this.font, name, x + 6, y + 4,
				!m.online() ? COLOR_OFFLINE : (m.leader() ? COLOR_LEADER : COLOR_NAME), false);
		if (m.leader()) {
			graphics.drawString(this.font, Component.translatable("screen.projecthero.squad.leader"),
					x + 8 + this.font.width(name), y + 4, COLOR_LEADER, false);
		}

		if (!m.online()) {
			graphics.drawString(this.font, Component.translatable("screen.projecthero.squad.offline"),
					x + 6, y + 16, COLOR_OFFLINE, false);
			return;
		}

		// --- health ---
		int barX = x + 6;
		int barY = y + 16;
		int barW = 110;
		float frac = m.maxHealth() <= 0 ? 0.0f : Mth.clamp(m.health() / m.maxHealth(), 0.0f, 1.0f);
		graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 6, COLOR_BORDER);
		graphics.fill(barX, barY, barX + barW, barY + 5, 0xAA101018);
		graphics.fill(barX, barY, barX + Math.round(barW * frac), barY + 5, frac < 0.35f ? COLOR_HP_LOW : COLOR_HP);
		if (m.absorption() > 0.0f) {
			int aw = Math.min(barW, Math.round(barW * (m.absorption() / Math.max(1.0f, m.maxHealth()))));
			graphics.fill(barX, barY, barX + aw, barY + 2, COLOR_ABSORB);
		}
		graphics.drawString(this.font, Component.literal(String.format(Locale.ROOT, "%.0f / %.0f",
				m.health(), m.maxHealth())), barX + barW + 6, barY - 1, COLOR_DIM, false);

		// --- identity ---
		graphics.drawString(this.font, com.projecthero.mod.squad.HeroIdentity.render(m.identityKey())
				.copy().withStyle(ChatFormatting.ITALIC), x + 6, y + 25, COLOR_DIM, false);

		// --- where ---
		Component where = locationLine(m);
		graphics.drawString(this.font, where, x + PANEL_W - 6 - this.font.width(where), y + 25, COLOR_DIM, false);
		Component coords = Component.literal(m.x() + ", " + m.y() + ", " + m.z());
		graphics.drawString(this.font, coords, x + PANEL_W - 6 - this.font.width(coords), y + 4, COLOR_DIM, false);
	}

	/** "212 blocks NE" for a squadmate in your dimension, the dimension's own name otherwise. */
	private Component locationLine(SquadInfoPayload.Member m) {
		var self = this.minecraft == null ? null : this.minecraft.player;
		String dim = m.dimension();
		if (self == null || !self.level().dimension().location().getPath().equals(dim)) {
			return Component.translatable("screen.projecthero.squad.dimension", dimensionName(dim));
		}
		double dx = m.x() - self.getX();
		double dz = m.z() - self.getZ();
		int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
		return Component.translatable("screen.projecthero.squad.distance", distance, compass(dx, dz));
	}

	private static String dimensionName(String path) {
		return switch (path) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "The End";
			default -> path.replace('_', ' ');
		};
	}

	/** Minecraft's +X is east and +Z is south, so a bearing reads straight off the two deltas. */
	private static String compass(double dx, double dz) {
		if (Math.abs(dx) < 4 && Math.abs(dz) < 4) {
			return "here";
		}
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
		String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return points[index];
	}
}

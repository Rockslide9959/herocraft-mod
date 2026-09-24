package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * "Your Power" — opened with the dedicated info key (default I). Shows exactly the HeroPack Guide
 * entry for the player's currently active experimental power: what the power is, and a description of
 * each of its six abilities and passive traits. Read-only; a single scrollable column.
 *
 * <p>All content comes from {@link com.projecthero.mod.hero.guide.HeroPackGuide#powerChapter} so it is
 * identical to the book. If the player has no power (or holds Mjolnir, where Thor owns the slots) it
 * says so instead.
 */
public final class PowerInfoScreen extends Screen {
	private static final int LINE_H = 11;

	private final List<FormattedCharSequence> body = new ArrayList<>();
	private Component heading = Component.empty();
	private int scroll = 0;
	private int left;
	private int right;
	private int top;
	private int bottom;

	public PowerInfoScreen() {
		super(Component.translatable("screen.projecthero.power_info"));
	}

	@Override
	protected void init() {
		this.left = Math.max(20, this.width / 2 - 180);
		this.right = Math.min(this.width - 20, this.width / 2 + 180);
		this.top = 40;
		this.bottom = this.height - 40;

		if (trainingState() != null) {
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
					.bounds(this.width / 2 - 122, this.height - 28, 120, 20).build());
			addRenderableWidget(Button.builder(
					Component.translatable("screen.projecthero.power_info.abandon_training")
							.withStyle(ChatFormatting.RED), b -> {
						net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
								new com.projecthero.mod.network.PunisherActionPayload(
										com.projecthero.mod.network.PunisherActionPayload.Action.ABANDON_TRAINING));
						onClose();
					}).bounds(this.width / 2 + 2, this.height - 28, 120, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
					.bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());
		}

		buildBody();
	}

	private void buildBody() {
		body.clear();
		scroll = 0;

		int wrapW = this.right - this.left;

		// Hero-Tier powers (Thor, Iron Man, Spider-Man, Max Steel) come first: a player holding one of
		// these cannot also have an experimental power, and the info key should show their guide entry.
		// Mid Vigilante Training (not yet the Punisher): show live objective progress instead.
		com.projecthero.mod.punisher.data.PunisherState training = trainingState();
		if (training != null) {
			this.heading = Component.translatable("screen.projecthero.power_info.training")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
			wrap(Component.translatable("screen.projecthero.power_info.training.body").withStyle(ChatFormatting.GRAY), wrapW);
			body.add(FormattedCharSequence.EMPTY);
			objective(wrapW, "screen.projecthero.power_info.obj.kills",
					training.killCount, com.projecthero.mod.punisher.PunisherConfig.TRAIN_KILLS);
			objective(wrapW, "screen.projecthero.power_info.obj.ranged",
					training.rangedKillCount, com.projecthero.mod.punisher.PunisherConfig.TRAIN_RANGED_KILLS);
			objective(wrapW, "screen.projecthero.power_info.obj.headshots",
					training.headshotCount, com.projecthero.mod.punisher.PunisherConfig.TRAIN_HEADSHOTS);
			checkbox(wrapW, "screen.projecthero.power_info.obj.craft", training.craftedFirearm);
			checkbox(wrapW, "screen.projecthero.power_info.obj.captain", training.defeatedCaptain);
			body.add(FormattedCharSequence.EMPTY);
			wrap(Component.translatable("screen.projecthero.power_info.training.hint").withStyle(ChatFormatting.DARK_GRAY), wrapW);
			return;
		}

		com.projecthero.mod.hero.guide.HeroPackGuide.Chapter heroChapter = heroTierChapter();
		if (heroChapter != null) {
			this.heading = net.minecraft.network.chat.Component.empty().append(heroChapter.title())
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
			for (Component line : heroChapter.lines()) {
				wrap(line, wrapW);
			}
			return;
		}

		Power power = activePower();

		if (power == null) {
			this.heading = Component.translatable("screen.projecthero.power_info.none")
					.withStyle(ChatFormatting.GRAY);
			wrap(Component.translatable("screen.projecthero.power_info.none.body").withStyle(ChatFormatting.GRAY), wrapW);
			return;
		}

		this.heading = Component.translatable(power.nameKey()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

		// A quick "which key does what" line-up first, with the player's live (possibly rebound) keys.
		wrap(Component.translatable("screen.projecthero.power_info.controls").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), wrapW);
		for (AbilitySlot slot : AbilitySlot.values()) {
			Ability a = power.ability(slot);
			wrap(Component.empty()
					.append(Component.literal(" [").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.keybind(slot.keyBindingTranslationKey()).withStyle(ChatFormatting.GOLD))
					.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(a.nameKey()).withStyle(ChatFormatting.WHITE)), wrapW);
		}
		body.add(FormattedCharSequence.EMPTY);

		// Full description of the power, its abilities and passives — exactly the HeroPack Guide entry.
		for (Component line : com.projecthero.mod.hero.guide.HeroPackGuide.powerChapter(power, -1).lines()) {
			wrap(line, wrapW);
		}
	}

	/**
	 * The guide chapter for whichever Hero-Tier power the local player holds, or {@code null} if they
	 * hold none. All four attachments are synced target-only, so this is reliable client-side.
	 */
	private static com.projecthero.mod.hero.guide.HeroPackGuide.Chapter heroTierChapter() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		// v0.9.19: a player who is BOTH bonded with a Symbiote AND currently Spider-Man is specifically
		// Black Suit Spider-Man -- show that bonded variant (Spider-Man's real keybinds plus the three
		// Symbiote extras), not the generic Normal-host Symbiote menu, and not the plain Spider-Man menu
		// either (its keybind list would be right, but it would say nothing about the suit that's on).
		// This is checked BEFORE the plain single-power cases below, either of which would otherwise win.
		boolean symbiote = com.projecthero.mod.symbiote.Symbiote.hasSymbiote(mc.player);
		boolean spiderMan = com.projecthero.mod.spider.SpiderMan.hasPower(mc.player);
		if (symbiote && spiderMan) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.symbioteSpiderManChapter();
		}
		if (symbiote) {
			// A plain Normal Host: their own six-ability kit only -- no web abilities, no sneak "alt"
			// extras (that material belongs to Black Suit Spider-Man, handled just above).
			return com.projecthero.mod.hero.guide.HeroPackGuide.symbioteNormalHostChapter();
		}
		if (spiderMan) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.spiderManChapter();
		}
		if (com.projecthero.mod.ironman.TonyStark.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.ironManChapter();
		}
		if (com.projecthero.mod.maxsteel.MaxSteel.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.maxSteelChapter();
		}
		if (com.projecthero.mod.punisher.Punisher.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.punisherChapter();
		}
		if (com.projecthero.mod.wolverine.Wolverine.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.wolverineChapter();
		}
		if (com.projecthero.mod.greenlantern.GreenLantern.hasPower(mc.player)) {
			return com.projecthero.mod.hero.guide.HeroPackGuide.greenLanternChapter();
		}
		boolean thor = com.projecthero.mod.worthiness.Worthiness.isWorthy(mc.player)
				|| mc.player.getMainHandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR)
				|| mc.player.getOffhandItem().is(com.projecthero.mod.item.ModItems.MJOLNIR);
		return thor ? com.projecthero.mod.hero.guide.HeroPackGuide.thorChapter() : null;
	}

	private static Power activePower() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		ExperimentalState state = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (state == null || state.activePower.isEmpty()) {
			return null;
		}
		return Powers.byKey(state.activePower);
	}

	/** The local player's Punisher state if they are mid Vigilante Training and not yet the Punisher. */
	private static com.projecthero.mod.punisher.data.PunisherState trainingState() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return null;
		}
		com.projecthero.mod.punisher.data.PunisherState s =
				mc.player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		return s != null && s.trainingActive && !s.hasPower ? s : null;
	}

	private void objective(int wrapW, String key, int have, int need) {
		boolean done = have >= need;
		wrap(Component.literal(done ? " [x] " : " [ ] ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY)
				.append(Component.translatable(key, Math.min(have, need), need)
						.withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE)), wrapW);
	}

	private void checkbox(int wrapW, String key, boolean done) {
		wrap(Component.literal(done ? " [x] " : " [ ] ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY)
				.append(Component.translatable(key).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE)), wrapW);
	}

	private void wrap(Component line, int width) {
		var split = this.font.split(line, width);
		if (split.isEmpty()) {
			body.add(FormattedCharSequence.EMPTY);
		} else {
			body.addAll(split);
		}
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int max = Math.max(0, body.size() * LINE_H - (bottom - top));
		scroll = Math.max(0, Math.min(max, scroll - (int) (dy * 20)));
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
		g.fill(left - 8, top - 6, right + 8, bottom + 6, 0x50000000);

		g.drawString(this.font, this.heading, left, top - 2, 0xFFFFE08A);

		g.enableScissor(left - 6, top + 12, right + 6, bottom);
		int y = top + 12 - scroll;
		for (FormattedCharSequence line : body) {
			if (y > top && y < bottom) {
				g.drawString(this.font, line, left, y, 0xFFD8D8E4, false);
			}
			y += LINE_H;
		}
		g.disableScissor();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

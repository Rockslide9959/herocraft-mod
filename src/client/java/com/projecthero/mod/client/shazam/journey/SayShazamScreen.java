package com.projecthero.mod.client.shazam.journey;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.shazam.journey.JourneyPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.7 Shazam acquisition: "Say my name." -- the prompt before the first transformation. A big gold <b>SHAZAM!</b> button
 * (Enter or Space work too) sends the answer; the line under it (wrapped to the screen) says that typing "Shazam" in chat works
 * as well. Esc closes it without answering (the HUD keeps the hint up, and right-clicking the Wizard opens it again). Never
 * pauses the game.
 */
public class SayShazamScreen extends Screen {
	private Button button;
	private int age;

	public SayShazamScreen() {
		super(Component.translatable("screen.projecthero.shazam_journey.title"));
	}

	@Override
	protected void init() {
		int bw = Math.min(180, width - 40), bh = 28;
		button = Button.builder(Component.translatable("screen.projecthero.shazam_journey.button").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				b -> answer()).bounds((width - bw) / 2, height / 2 + 6, bw, bh).build();
		addRenderableWidget(button);
	}

	private void answer() {
		if (ClientPlayNetworking.canSend(JourneyPayloads.Answer.TYPE)) {
			ClientPlayNetworking.send(new JourneyPayloads.Answer());
		}
		JourneyClient.answered();
		onClose();
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
			answer();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public void tick() {
		age++;
		if (!JourneyClient.prompting()) {
			onClose();
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// no blur or dirt: the Wizard stays in view behind the prompt; just a soft dark band behind the words
		int top = height / 2 - 52, bottom = height / 2 + 62;
		g.fillGradient(0, top - 20, width, top, 0x00000000, 0x90000000);
		g.fill(0, top, width, bottom, 0x90000000);
		g.fillGradient(0, bottom, width, bottom + 20, 0x90000000, 0x00000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int maxW = Math.min(320, width - 40);
		// "Say my name."
		Component ask = Component.translatable("screen.projecthero.shazam_journey.ask").withStyle(ChatFormatting.ITALIC);
		List<FormattedCharSequence> lines = font.split(ask, maxW);
		int y = height / 2 - 40;
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, (width - font.width(l)) / 2, y, 0xFFE8DCC0, true);
			y += 11;
		}
		// the hint, wrapped
		Component hint = Component.translatable("screen.projecthero.shazam_journey.hint");
		y = height / 2 + 42;
		for (FormattedCharSequence l : font.split(hint, maxW)) {
			g.drawString(font, l, (width - font.width(l)) / 2, y, 0xFFAAAAAA, false);
			y += 10;
		}
		// a little lightning flicker round the button
		float f = 0.5f + 0.5f * Mth.sin((age + partialTick) * 0.6f);
		int a = (int) (40 + 60 * f);
		g.renderOutline(button.getX() - 2, button.getY() - 2, button.getWidth() + 4, button.getHeight() + 4, (a << 24) | 0xFFD24A);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

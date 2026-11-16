package com.projecthero.mod.client.powerarmour;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.powerarmour.BodyPart;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.menu.PowerArmourEditorMenu;
import com.projecthero.mod.powerarmour.network.PowerArmourEditPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;

/**
 * v0.17.5 Power Armour: the Armour Editor.
 * <pre>
 *  +-----------------------------------------------------------+
 *  | Armour Editor                                             |
 *  | +-----------+  Helmet [ ][ ]          DIAMOND             |
 *  | |  rotating |  Chest  [ ]             Armour 20           |
 *  | |   suit    |  Arms   [ ]             Integrity 100%      |
 *  | | (click to |  Back   [ ]             Core 812/1000       |
 *  | |  open it) |  Legs   [ ]             Output 7/10         |
 *  | +-----------+                         Drain 1.3 E/s       |
 *  | [core] Core  (P)(S)(G) paint          [ Pick Up ]          |
 *  |            player inventory                               |
 *  +-----------------------------------------------------------+
 * </pre>
 * Drag the preview to turn it, click it to open / close the plates. Paint swatches: left-click the next colour, right-click the
 * previous one (the first step past the last dye goes back to the tier's own colour). All text wraps to its column.
 */
public class PowerArmourEditorScreen extends AbstractContainerScreen<PowerArmourEditorMenu> {
	private static final int PREVIEW_X = 8, PREVIEW_Y = 16, PREVIEW_W = 92, PREVIEW_H = 104;
	private static final int STATS_X = 192, STATS_W = 58;
	private static final int PAINT_X = 62, PAINT_Y = 126;
	private static final int[] PALETTE = buildPalette();

	private PlayerModel<LivingEntity> model;
	private float spin = 200f;
	private boolean dragging;
	private double dragFrom;
	private boolean previewOpen;
	private float previewOpenness;

	public PowerArmourEditorScreen(PowerArmourEditorMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = PowerArmourEditorMenu.WIDTH;
		this.imageHeight = PowerArmourEditorMenu.HEIGHT;
		this.inventoryLabelX = PowerArmourEditorMenu.INV_X;
		this.inventoryLabelY = PowerArmourEditorMenu.INV_Y - 10;
	}

	private static int[] buildPalette() {
		DyeColor[] dyes = DyeColor.values();
		int[] out = new int[dyes.length + 1];
		out[0] = SuitData.DEFAULT_PAINT;
		for (int i = 0; i < dyes.length; i++) {
			out[i + 1] = dyes[i].getTextureDiffuseColor() & 0xFFFFFF;
		}
		return out;
	}

	@Override
	protected void init() {
		super.init();
		model = new PlayerModel<>(minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
		addRenderableWidget(Button.builder(Component.translatable("projecthero.power_armour.editor.pick_up"), b -> {
			if (menu.frame() != null) {
				ClientPlayNetworking.send(new PowerArmourEditPayload(menu.frame().getId(), PowerArmourEditPayload.PICK_UP, 0));
			}
		}).bounds(leftPos + STATS_X - 2, topPos + 122, STATS_W + 2, 18).build());
	}

	// ------------------------------------------------------------------ drawing

	@Override
	protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		GuiBits.panel(g, x, y, imageWidth, imageHeight);
		GuiBits.inset(g, x + PREVIEW_X, y + PREVIEW_Y, PREVIEW_W, PREVIEW_H, 0xFF20242A);
		// a faint grid on the preview floor
		for (int i = 1; i < 6; i++) {
			g.fill(x + PREVIEW_X + 1, y + PREVIEW_Y + PREVIEW_H - 4 - i * 3, x + PREVIEW_X + PREVIEW_W - 1, y + PREVIEW_Y + PREVIEW_H - 3 - i * 3,
					0x1840A0FF);
		}
		for (Slot s : menu.slots) {
			GuiBits.slot(g, x + s.x, y + s.y);
		}
		// locked part rows (this tier has no slot there yet)
		SuitData suit = menu.suit();
		for (BodyPart part : BodyPart.values()) {
			if (suit.tier.slotsFor(part) == 0) {
				int ry = y + PowerArmourEditorMenu.PART_Y0 + part.ordinal() * PowerArmourEditorMenu.PART_DY;
				g.fill(x + PowerArmourEditorMenu.PART_SLOT_X - 1, ry - 1, x + PowerArmourEditorMenu.PART_SLOT_X + 17, ry + 17, 0xFF9C9C9C);
				g.drawString(font, "x", x + PowerArmourEditorMenu.PART_SLOT_X + 6, ry + 4, 0xFF6A6A6A, false);
			}
		}
		// paint swatches
		int[] cols = { suit.primaryColour(), suit.secondaryColour(), suit.glowColour() };
		for (int i = 0; i < 3; i++) {
			int sx = x + PAINT_X + i * 16, sy = y + PAINT_Y;
			GuiBits.inset(g, sx, sy, 14, 14, 0xFF000000 | cols[i]);
		}
		renderPreview(g, partial, mouseX, mouseY);
	}

	private void renderPreview(GuiGraphics g, float partial, int mouseX, int mouseY) {
		SuitData suit = menu.suit();
		previewOpenness = Mth.approach(previewOpenness, previewOpen ? 1f : 0f, 0.04f);
		if (!dragging) {
			spin += partial * 0.8f;
		}
		float time = minecraft.level == null ? 0f : minecraft.level.getGameTime() + partial;
		SuitRenderer.Look look = new SuitRenderer.Look(suit);
		if (previewOpen) {
			look.sequence(previewOpenness, true);
		} else {
			look.sequence(1f - previewOpenness, false);
		}
		look.glow = suit.powered() ? 1f : 0.1f;
		look.time = time;
		int x0 = leftPos + PREVIEW_X, y0 = topPos + PREVIEW_Y;
		g.enableScissor(x0 + 1, y0 + 1, x0 + PREVIEW_W - 1, y0 + PREVIEW_H - 1);
		PoseStack pose = g.pose();
		pose.pushPose();
		float scale = 44f;
		pose.translate(x0 + PREVIEW_W / 2f, y0 + PREVIEW_H - 8f, 120f);
		pose.mulPose(new Matrix4f().scaling(scale, scale, -scale));
		pose.mulPose(Axis.ZP.rotationDegrees(180f));
		pose.mulPose(Axis.XP.rotationDegrees(-12f));
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		Lighting.setupForEntityInInventory();
		PowerArmourFrameRenderer.renderStanding(model, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, look);
		g.flush();
		Lighting.setupFor3DItems();
		pose.popPose();
		g.disableScissor();
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, GuiBits.TEXT, false);
		SuitData suit = menu.suit();
		for (BodyPart part : BodyPart.values()) {
			int ry = PowerArmourEditorMenu.PART_Y0 + part.ordinal() * PowerArmourEditorMenu.PART_DY;
			Component label = Component.translatable(part.translationKey());
			List<FormattedCharSequence> lines = font.split(label, PowerArmourEditorMenu.PART_SLOT_X - 106 - 2);
			g.drawString(font, lines.get(0), 106, ry + 5, suit.tier.slotsFor(part) == 0 ? 0xFF8A8A8A : GuiBits.TEXT, false);
		}
		g.drawString(font, Component.translatable("projecthero.power_armour.editor.core"), 31, 130, GuiBits.TEXT, false);
		// stats column
		int y = 18;
		y += GuiBits.wrapped(g, font, Component.translatable(suit.tier.translationKey()).withStyle(ChatFormatting.BOLD), STATS_X, y, STATS_W,
				0xFF7A5A00, false) + 2;
		for (Component line : stats(suit)) {
			y += GuiBits.wrapped(g, font, line, STATS_X, y, STATS_W, GuiBits.TEXT, false) + 1;
			if (y > 116) {
				break;
			}
		}
	}

	private List<Component> stats(SuitData suit) {
		List<Component> out = new ArrayList<>();
		out.add(Component.translatable("projecthero.power_armour.editor.armour", Math.round(suit.tier.armour)));
		out.add(Component.translatable("projecthero.power_armour.editor.integrity", Math.round(suit.integrityFraction() * 100))
				.withStyle(suit.wrecked() ? ChatFormatting.DARK_RED : ChatFormatting.RESET));
		if (suit.core == null) {
			out.add(Component.translatable("projecthero.power_armour.editor.no_core").withStyle(ChatFormatting.DARK_RED));
		} else {
			out.add(Component.translatable("projecthero.power_armour.editor.charge", Math.round(suit.coreCharge), Math.round(suit.capacity())));
		}
		boolean over = suit.core != null && suit.load() > suit.output();
		out.add(Component.translatable("projecthero.power_armour.editor.output", suit.load(), suit.output())
				.withStyle(over ? ChatFormatting.DARK_RED : ChatFormatting.RESET));
		float drain = 20f * PowerArmourConfig.IDLE_DRAIN * suit.tier.drain * (suit.has(ModuleType.OVERCLOCK) ? PowerArmourConfig.OVERCLOCK_DRAIN : 1f);
		out.add(Component.translatable("projecthero.power_armour.editor.drain", String.format(java.util.Locale.ROOT, "%.1f", drain)));
		int used = 0;
		for (ModuleType m : suit.modules) {
			used += m == null ? 0 : 1;
		}
		out.add(Component.translatable("projecthero.power_armour.editor.slots", used, suit.modules.length));
		return out;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		renderTooltip(g, mouseX, mouseY);
		// empty slot / swatch / preview hints
		Slot hovered = hoveredSlot;
		SuitData suit = menu.suit();
		if (hovered != null && !hovered.hasItem() && !(hovered.container instanceof Inventory) && menu.getCarried().isEmpty()) {
			List<Component> tip = new ArrayList<>();
			int idx = hovered.getContainerSlot();
			if (idx == 0) {
				tip.add(Component.translatable("projecthero.power_armour.editor.core_slot"));
			} else {
				BodyPart part = suit.tier.slotPart(Math.min(idx - 1, suit.modules.length - 1));
				tip.add(Component.translatable("projecthero.power_armour.editor.module_slot", Component.translatable(part.translationKey())));
				tip.add(Component.translatable("projecthero.power_armour.editor.budget_left", Math.max(0, suit.output() - suit.load()))
						.withStyle(ChatFormatting.GRAY));
			}
			g.renderComponentTooltip(font, wrap(tip), mouseX, mouseY);
		}
		int swatch = swatchAt(mouseX, mouseY);
		if (swatch >= 0) {
			g.renderComponentTooltip(font, wrap(List.of(Component.translatable("projecthero.power_armour.editor.paint." + swatch),
					Component.translatable("projecthero.power_armour.editor.paint_hint").withStyle(ChatFormatting.GRAY))), mouseX, mouseY);
		} else if (inPreview(mouseX, mouseY) && menu.getCarried().isEmpty()) {
			g.renderComponentTooltip(font, wrap(List.of(Component.translatable("projecthero.power_armour.editor.preview_hint")
					.withStyle(ChatFormatting.GRAY))), mouseX, mouseY);
		}
	}

	private List<Component> wrap(List<Component> lines) {
		List<Component> out = new ArrayList<>();
		for (Component c : lines) {
			out.addAll(com.projecthero.mod.client.gui.TooltipWrap.wrap(font, c));
		}
		return out;
	}

	// ------------------------------------------------------------------ input

	private int swatchAt(double mx, double my) {
		for (int i = 0; i < 3; i++) {
			int sx = leftPos + PAINT_X + i * 16, sy = topPos + PAINT_Y;
			if (mx >= sx && mx < sx + 14 && my >= sy && my < sy + 14) {
				return i;
			}
		}
		return -1;
	}

	private boolean inPreview(double mx, double my) {
		return mx >= leftPos + PREVIEW_X && mx < leftPos + PREVIEW_X + PREVIEW_W && my >= topPos + PREVIEW_Y && my < topPos + PREVIEW_Y + PREVIEW_H;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int swatch = swatchAt(mx, my);
		if (swatch >= 0 && menu.frame() != null) {
			SuitData suit = menu.suit();
			int cur = swatch == 0 ? suit.primary : swatch == 1 ? suit.secondary : suit.glow;
			int idx = 0;
			for (int i = 0; i < PALETTE.length; i++) {
				if (PALETTE[i] == cur) {
					idx = i;
				}
			}
			idx = Math.floorMod(idx + (button == 1 ? -1 : 1), PALETTE.length);
			ClientPlayNetworking.send(new PowerArmourEditPayload(menu.frame().getId(), swatch, PALETTE[idx]));
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.3f));
			return true;
		}
		if (inPreview(mx, my) && menu.getCarried().isEmpty()) {
			dragging = true;
			dragFrom = mx;
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging) {
			spin += (float) dx * 2.2f;
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging) {
			dragging = false;
			if (Math.abs(mx - dragFrom) < 2) {
				previewOpen = !previewOpen;
			}
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}
}

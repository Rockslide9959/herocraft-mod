package com.projecthero.mod.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.projecthero.mod.client.render.RingPlacement.Placement;
import com.projecthero.mod.client.render.RingPlacement.View;

import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.14.23: live editor for where the rings sit on the hand ({@link RingPlacement}). Open with {@code /ringeditor} (or the
 * unbound "Ring Editor" key).
 *
 * <ul>
 *   <li><b>Third person</b>: a big copy of your own character on the right -- left-drag turns it, right-drag pans,
 *   scroll zooms.</li>
 *   <li><b>First person</b>: the camera switches to first person and the panel leaves the world visible, so you see the
 *   ring on your real hand (keep your main hand empty so the arm is drawn).</li>
 * </ul>
 * The ring being edited is shown on your hand whether you own it or not ({@link HandRing#preview}). Done saves to
 * {@code config/projecthero_ring_placement.json}; Cancel / Esc puts every value back.
 */
public class RingEditorScreen extends Screen {
	private static final int PANEL_W = 196;
	private static final int ROW_H = 18;
	private static final int H = 16;

	private final List<RowSlider> sliders = new ArrayList<>();
	private final java.util.Map<String, Placement[]> undo = new java.util.HashMap<>();
	private int ringIndex;
	private View view = View.THIRD_PERSON;
	private CameraType cameraBefore;
	private boolean saved;

	// third-person preview camera
	private float dollYaw = 30f;
	private float dollPitch = -5f;
	private float dollZoom = 1f;
	private float panX, panY;
	private int dragButton = -1;

	public RingEditorScreen() {
		super(Component.translatable("screen.projecthero.ring_editor"));
	}

	private String ring() {
		return RingPlacement.RINGS[ringIndex];
	}

	private Placement current() {
		return RingPlacement.get(ring(), view);
	}

	@Override
	protected void init() {
		if (cameraBefore == null) {
			cameraBefore = minecraft.options.getCameraType();
			for (String r : RingPlacement.RINGS) {
				undo.put(r, new Placement[] { RingPlacement.get(r, View.THIRD_PERSON).copy(), RingPlacement.get(r, View.FIRST_PERSON).copy() });
			}
		}
		applyView();
		sliders.clear();
		int x = 8;
		int y = 6;
		int half = (PANEL_W - 20) / 2;
		addRenderableWidget(Button.builder(ringLabel(), b -> {
			ringIndex = (ringIndex + 1) % RingPlacement.RINGS.length;
			rebuild();
		}).bounds(x, y, half, H).build());
		addRenderableWidget(Button.builder(viewLabel(), b -> {
			view = view == View.THIRD_PERSON ? View.FIRST_PERSON : View.THIRD_PERSON;
			rebuild();
		}).bounds(x + half + 4, y, half, H).build());
		y += ROW_H + 4;

		y = row(x, y, "X", -8, 8, 0.05, () -> current().x, v -> current().x = (float) v);
		y = row(x, y, "Y", -8, 8, 0.05, () -> current().y, v -> current().y = (float) v);
		y = row(x, y, "Z", -8, 8, 0.05, () -> current().z, v -> current().z = (float) v);
		y = row(x, y, "Pitch", -180, 180, 1, () -> current().pitch, v -> current().pitch = (float) v);
		y = row(x, y, "Yaw", -180, 180, 1, () -> current().yaw, v -> current().yaw = (float) v);
		y = row(x, y, "Roll", -180, 180, 1, () -> current().roll, v -> current().roll = (float) v);
		y = row(x, y, "Scale", 0.25, 3, 0.01, () -> current().scale, v -> current().scale = (float) v);
		y += 4;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.ring_editor.reset"), b -> {
			current().set(RingPlacement.defaults(ring(), view));
			refresh();
		}).bounds(x, y, half, H).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.ring_editor.copy_view"), b -> {
			View other = view == View.THIRD_PERSON ? View.FIRST_PERSON : View.THIRD_PERSON;
			current().set(RingPlacement.get(ring(), other));
			refresh();
		}).bounds(x + half + 4, y, half, H).build());
		y += ROW_H;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.ring_editor.copy_ring"), b -> {
			for (String r : RingPlacement.RINGS) {
				if (!r.equals(ring())) {
					RingPlacement.get(r, view).set(current());
				}
			}
		}).bounds(x, y, half, H).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.ring_editor.clipboard"), b -> {
			minecraft.keyboardHandler.setClipboard(RingPlacement.toJson());
		}).bounds(x + half + 4, y, half, H).build());
		y += ROW_H + 4;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.ring_editor.done"), b -> {
			RingPlacement.save();
			saved = true;
			onClose();
		}).bounds(x, y, half, H).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x + half + 4, y, half, H).build());
	}

	private Component ringLabel() {
		return Component.translatable("screen.projecthero.ring_editor.ring",
				Component.translatable("screen.projecthero.ring_editor.ring." + ring()));
	}

	private Component viewLabel() {
		return Component.translatable("screen.projecthero.ring_editor.view",
				Component.translatable("screen.projecthero.ring_editor.view." + view.name().toLowerCase(java.util.Locale.ROOT)));
	}

	private void rebuild() {
		clearWidgets();
		init();
	}

	private void refresh() {
		for (RowSlider s : sliders) {
			s.refresh();
		}
	}

	/** One editable value: [-] [slider] [+]. */
	private int row(int x, int y, String name, double min, double max, double step, DoubleSupplier get, DoubleConsumer set) {
		RowSlider slider = new RowSlider(x + 22, y, PANEL_W - 16 - 44, H, name, min, max, step, get, set);
		addRenderableWidget(Button.builder(Component.literal("-"), b -> slider.nudge(-1)).bounds(x, y, 20, H).build());
		addRenderableWidget(slider);
		addRenderableWidget(Button.builder(Component.literal("+"), b -> slider.nudge(1)).bounds(x + PANEL_W - 16 - 20, y, 20, H).build());
		sliders.add(slider);
		return y + ROW_H;
	}

	private void applyView() {
		HandRing.preview = ring();
		minecraft.options.setCameraType(view == View.FIRST_PERSON ? CameraType.FIRST_PERSON : cameraBefore);
	}

	@Override
	public void removed() {
		HandRing.preview = null;
		if (cameraBefore != null) {
			minecraft.options.setCameraType(cameraBefore);
		}
		if (!saved) {
			undo.forEach((r, pair) -> {
				RingPlacement.get(r, View.THIRD_PERSON).set(pair[0]);
				RingPlacement.get(r, View.FIRST_PERSON).set(pair[1]);
			});
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** No blur: in first person the world (and your hand) is the preview. */
	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		g.fill(0, 0, PANEL_W, height, 0xB0101014);
		if (view == View.THIRD_PERSON) {
			g.fill(PANEL_W, 0, width, height, 0x90000000);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		if (view == View.THIRD_PERSON && minecraft.player != null) {
			renderDoll(g, minecraft.player);
			g.drawCenteredString(font, Component.translatable("screen.projecthero.ring_editor.hint_third"),
					PANEL_W + (width - PANEL_W) / 2, height - 14, 0xA0A0A0);
		} else {
			g.drawCenteredString(font, Component.translatable("screen.projecthero.ring_editor.hint_first"),
					PANEL_W + (width - PANEL_W) / 2, height - 14, 0xE0E0E0);
		}
	}

	/** Your own character, big, centred on the right hand. */
	private void renderDoll(GuiGraphics g, LocalPlayer player) {
		float bodyRot = player.yBodyRot, bodyRotO = player.yBodyRotO;
		float yRot = player.getYRot(), xRot = player.getXRot();
		float headRot = player.yHeadRot, headRotO = player.yHeadRotO;
		player.yBodyRot = player.yBodyRotO = 180f;
		player.setYRot(180f);
		player.setXRot(0f);
		player.yHeadRot = player.yHeadRotO = 180f;
		float scale = 140f * dollZoom;
		int cx = PANEL_W + (width - PANEL_W) / 2;
		int cy = height / 2;
		g.enableScissor(PANEL_W, 0, width, height);
		Quaternionf pose = new Quaternionf().rotateZ(Mth.PI)
				.rotateX(dollPitch * Mth.DEG_TO_RAD)
				.rotateY(dollYaw * Mth.DEG_TO_RAD);
		// focus the right hand: ~0.7 blocks up, a little to the player's right
		Vector3f focus = new Vector3f(panX / scale, 0.72f + panY / scale, 0f);
		InventoryScreen.renderEntityInInventory(g, cx, cy, scale, focus, pose, null, player);
		g.disableScissor();
		player.yBodyRot = bodyRot;
		player.yBodyRotO = bodyRotO;
		player.setYRot(yRot);
		player.setXRot(xRot);
		player.yHeadRot = headRot;
		player.yHeadRotO = headRotO;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (view == View.THIRD_PERSON && mouseX > PANEL_W) {
			dragButton = button;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragButton = -1;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (dragButton == 0) {
			dollYaw += (float) dx * 0.8f;
			dollPitch = Mth.clamp(dollPitch - (float) dy * 0.8f, -80f, 80f);
			return true;
		}
		if (dragButton == 1) {
			panX += (float) dx;
			panY += (float) dy;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (view == View.THIRD_PERSON && mouseX > PANEL_W) {
			dollZoom = Mth.clamp(dollZoom * (scrollY > 0 ? 1.15f : 1f / 1.15f), 0.3f, 6f);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** A slider over [min, max] that writes straight into the live placement, snapping to {@code step}. */
	private static final class RowSlider extends AbstractSliderButton {
		private final String name;
		private final double min, max, step;
		private final DoubleSupplier get;
		private final DoubleConsumer set;

		RowSlider(int x, int y, int w, int h, String name, double min, double max, double step, DoubleSupplier get, DoubleConsumer set) {
			super(x, y, w, h, Component.empty(), 0);
			this.name = name;
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			refresh();
		}

		void refresh() {
			value = Mth.clamp((get.getAsDouble() - min) / (max - min), 0, 1);
			updateMessage();
		}

		void nudge(int dir) {
			set.accept(snap(get.getAsDouble() + dir * step));
			refresh();
		}

		private double snap(double v) {
			return Mth.clamp(Math.round(v / step) * step, min, max);
		}

		@Override
		protected void updateMessage() {
			double v = get.getAsDouble();
			String text = step >= 1 ? String.format(java.util.Locale.ROOT, "%s: %.0f", name, v)
					: String.format(java.util.Locale.ROOT, "%s: %.2f", name, v);
			setMessage(Component.literal(text));
		}

		@Override
		protected void applyValue() {
			set.accept(snap(min + value * (max - min)));
		}
	}
}

package com.projecthero.mod.client.spider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

import org.joml.Quaternionf;

/**
 * v0.16.1: the in-game PERCH POSE EDITOR ({@code /spiderposeeditor}). Client only -- it changes how THIS client draws
 * the ledge perch, nobody else's.
 *
 * <ul>
 * <li>Left: a live 3D preview of his own model (suit and skin) in the perch -- drag to turn it (yaw / pitch), scroll
 * to zoom, a toggle to stand it on a block edge.</li>
 * <li>Right: pick a part (head, body, either arm, either leg, or the whole body) and edit its xRot / yRot / zRot
 * (degrees, 0.5 steps) and x / y / z (pixels, 0.1 steps) with -/+ (Shift = x10) or by typing. The whole-body page has
 * the drop, the back / forward shift, the extra lean (hip-anchored) and the first-person camera drop / tilt.</li>
 * <li>Mirror: editing one arm / leg mirrors it onto the other. Undo, Reset (to the built-in default), Save
 * ({@code config/projecthero_perch_pose.json}, read at runtime), Export (compact JSON to the clipboard and
 * {@code config/projecthero_perch_pose_export.txt}).</li>
 * </ul>
 * Every change applies to the real perch at once, so he can close the screen and look at himself perching (F5).
 */
public class PerchPoseEditorScreen extends Screen {
	private static boolean openNextTick;

	private static final String[] PART_KEYS = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "whole"};
	private static final String[] PARAM_KEYS = {"x_rot", "y_rot", "z_rot", "x", "y", "z"};
	private static final String[] WHOLE_KEYS = {"drop", "shift", "lean", "camera_drop", "camera_tilt"};
	private static final int WHOLE = 6;
	private static final int ROWS = 6;

	private static int partIdx = 2;
	private static boolean mirror = true;
	private static boolean showBlock = true;
	private static float yaw = 160f;
	private static float pitch = 8f;
	private static float zoom = 1.0f;

	private final Deque<PerchPose> undo = new ArrayDeque<>();
	private long lastTypedSnapshot;
	private final EditBox[] boxes = new EditBox[ROWS];
	private final Button[] minus = new Button[ROWS];
	private final Button[] plus = new Button[ROWS];
	private Button partButton;
	private Button mirrorButton;
	private Button blockButton;
	private boolean syncing;
	private boolean dragging;
	private int previewW;
	private Component status = Component.empty();
	private long statusUntil;

	public PerchPoseEditorScreen() {
		super(Component.translatable("screen.projecthero.perch_editor.title"));
	}

	/** Registers {@code /spiderposeeditor} and the deferred open (the chat screen closes after the command runs). */
	public static void initialize() {
		PerchPose.load();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				ClientCommandManager.literal("spiderposeeditor").executes(ctx -> {
					openNextTick = true;
					return 1;
				})));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (openNextTick && mc.player != null) {
				openNextTick = false;
				mc.setScreen(new PerchPoseEditorScreen());
			}
		});
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------- layout ----------------

	@Override
	protected void init() {
		previewW = Math.max(120, (int) (width * 0.42f));
		int cx = previewW + 6;
		int cw = width - cx - 6;
		int rowH = 20;
		int top = 18;
		// part picker: < Part >
		addRenderableWidget(Button.builder(Component.literal("<"), b -> pickPart(-1)).bounds(cx, top, 20, 18).build());
		partButton = addRenderableWidget(Button.builder(Component.empty(), b -> pickPart(1)).bounds(cx + 22, top, cw - 44, 18).build());
		addRenderableWidget(Button.builder(Component.literal(">"), b -> pickPart(1)).bounds(cx + cw - 20, top, 20, 18).build());
		int labelW = Math.min(76, cw / 3);
		int boxW = Math.max(36, cw - labelW - 2 * 18 - 6);
		for (int i = 0; i < ROWS; i++) {
			final int row = i;
			int y = top + 22 + i * rowH;
			int bx = cx + labelW;
			minus[i] = addRenderableWidget(Button.builder(Component.literal("-"), b -> nudge(row, -1)).bounds(bx, y, 18, 18).build());
			boxes[i] = new EditBox(font, bx + 20, y + 1, boxW, 16, Component.empty());
			boxes[i].setMaxLength(10);
			boxes[i].setResponder(s -> typed(row, s));
			addRenderableWidget(boxes[i]);
			plus[i] = addRenderableWidget(Button.builder(Component.literal("+"), b -> nudge(row, 1)).bounds(bx + 22 + boxW, y, 18, 18).build());
			minus[i].setTooltip(Tooltip.create(Component.translatable("screen.projecthero.perch_editor.step_hint")));
			plus[i].setTooltip(Tooltip.create(Component.translatable("screen.projecthero.perch_editor.step_hint")));
		}
		// toggles under the preview
		int ty = height - 46;
		int half = (previewW - 12) / 2;
		mirrorButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			mirror = !mirror;
			refresh();
		}).bounds(6, ty, half, 18).build());
		mirrorButton.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.perch_editor.mirror_hint")));
		blockButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			showBlock = !showBlock;
			refresh();
		}).bounds(8 + half, ty, half, 18).build());
		// actions
		int ay = height - 24;
		String[] actions = {"undo", "reset", "save", "export", "done"};
		int aw = (cw - 4 * 3) / 5;
		for (int i = 0; i < actions.length; i++) {
			final String a = actions[i];
			Button b = addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.perch_editor." + a), x -> action(a))
					.bounds(cx + i * (aw + 3), ay, aw, 18).build());
			b.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.perch_editor." + a + "_hint")));
		}
		refresh();
	}

	private void pickPart(int d) {
		partIdx = Math.floorMod(partIdx + d, PART_KEYS.length);
		refresh();
	}

	private int rowCount() {
		return partIdx == WHOLE ? WHOLE_KEYS.length : ROWS;
	}

	private void refresh() {
		syncing = true;
		partButton.setMessage(Component.translatable("screen.projecthero.perch_editor.part." + PART_KEYS[partIdx]).withStyle(ChatFormatting.YELLOW));
		mirrorButton.setMessage(Component.translatable(mirror ? "screen.projecthero.perch_editor.mirror_on" : "screen.projecthero.perch_editor.mirror_off"));
		blockButton.setMessage(Component.translatable(showBlock ? "screen.projecthero.perch_editor.block_on" : "screen.projecthero.perch_editor.block_off"));
		for (int i = 0; i < ROWS; i++) {
			boolean on = i < rowCount();
			boxes[i].visible = on;
			minus[i].visible = on;
			plus[i].visible = on;
			if (on) {
				boxes[i].setValue(fmt(get(i)));
			}
		}
		syncing = false;
	}

	private static String fmt(float v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		// trim trailing zeros for a tidy field
		if (s.contains(".")) {
			s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		}
		return s.equals("-0") ? "0" : s;
	}

	// ---------------- values ----------------

	private float get(int row) {
		PerchPose p = PerchPose.current();
		if (partIdx == WHOLE) {
			return switch (row) {
				case 0 -> p.drop;
				case 1 -> p.shift;
				case 2 -> p.lean;
				case 3 -> p.cameraDrop;
				default -> p.cameraTilt;
			};
		}
		return p.part(partIdx).get(row);
	}

	private void put(int row, float v) {
		PerchPose p = PerchPose.current();
		if (partIdx == WHOLE) {
			switch (row) {
				case 0 -> p.drop = v;
				case 1 -> p.shift = v;
				case 2 -> p.lean = v;
				case 3 -> p.cameraDrop = v;
				default -> p.cameraTilt = v;
			}
			return;
		}
		p.part(partIdx).set(row, v);
		int other = PerchPose.mirrorOf(partIdx);
		if (mirror && other >= 0) {
			p.setPart(other, p.part(partIdx).mirrored());
		}
	}

	/** Rotations step 0.5 degrees, positions 0.1 px (camera drop 0.01 blocks); Shift = x10. */
	private float step(int row) {
		float s;
		if (partIdx == WHOLE) {
			s = row == 2 || row == 4 ? 0.5f : row == 3 ? 0.01f : 0.1f;
		} else {
			s = row < 3 ? 0.5f : 0.1f;
		}
		return hasShiftDown() ? s * 10f : s;
	}

	private void snapshot() {
		undo.push(PerchPose.current().copy());
		while (undo.size() > 200) {
			undo.removeLast();
		}
	}

	private void nudge(int row, int dir) {
		snapshot();
		float v = Math.round((get(row) + dir * step(row)) * 1000f) / 1000f;
		put(row, v);
		refresh();
	}

	private void typed(int row, String s) {
		if (syncing) {
			return;
		}
		try {
			float v = Float.parseFloat(s.trim());
			if (!Float.isFinite(v)) {
				return;
			}
			long now = System.currentTimeMillis();
			if (now - lastTypedSnapshot > 800) {
				snapshot();
			}
			lastTypedSnapshot = now;
			put(row, v);
			if (mirror && PerchPose.mirrorOf(partIdx) >= 0) {
				return; // the other side follows; this field keeps what is being typed
			}
		} catch (NumberFormatException ignored) {
			// half-typed ("-", "1.") -- wait for more
		}
	}

	private void action(String a) {
		Minecraft mc = Minecraft.getInstance();
		switch (a) {
			case "undo" -> {
				if (!undo.isEmpty()) {
					PerchPose.set(undo.pop());
					refresh();
				}
			}
			case "reset" -> {
				snapshot();
				PerchPose.set(PerchPose.defaults());
				refresh();
				status(Component.translatable("screen.projecthero.perch_editor.reset_done"));
			}
			case "save" -> status(PerchPose.save()
					? Component.translatable("screen.projecthero.perch_editor.saved", PerchPose.file().getFileName().toString())
					: Component.translatable("screen.projecthero.perch_editor.save_failed"));
			case "export" -> {
				mc.keyboardHandler.setClipboard(PerchPose.export());
				status(Component.translatable("screen.projecthero.perch_editor.exported", PerchPose.exportFile().getFileName().toString()));
			}
			default -> onClose();
		}
	}

	private void status(Component c) {
		status = c;
		statusUntil = System.currentTimeMillis() + 4000;
	}

	// ---------------- input: drag to turn, scroll to zoom ----------------

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) {
			return true;
		}
		if (mx < previewW && my > 14 && my < height - 50) {
			dragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		dragging = false;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging) {
			yaw += (float) dx * 1.2f;
			pitch = Mth.clamp(pitch + (float) dy * 1.2f, -89f, 89f);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (mx < previewW) {
			zoom = Mth.clamp(zoom + (float) sy * 0.1f, 0.4f, 3.0f);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	// ---------------- drawing ----------------

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		g.drawString(font, title, 6, 5, 0xFFE070);
		g.fill(previewW, 14, previewW + 1, height - 4, 0x40FFFFFF);
		renderPreview(g);
		int cx = previewW + 6;
		int top = 18;
		for (int i = 0; i < rowCount(); i++) {
			String key = partIdx == WHOLE ? WHOLE_KEYS[i] : PARAM_KEYS[i];
			g.drawString(font, Component.translatable("screen.projecthero.perch_editor.param." + key), cx, top + 22 + i * 20 + 5, 0xE0E0E0);
		}
		// hints: wrapped to the preview's width
		var hint = font.split(Component.translatable("screen.projecthero.perch_editor.drag_hint"), previewW - 12);
		int hy = height - 50 - hint.size() * 9;
		for (var line : hint) {
			g.drawString(font, line, 6, hy, 0x9AA0B0);
			hy += 9;
		}
		if (System.currentTimeMillis() < statusUntil) {
			int sy = height - 36;
			int y0 = sy - 9 * Math.max(0, font.split(status, width - previewW - 12).size() - 1);
			for (var line : font.split(status, width - previewW - 12)) {
				g.drawString(font, line, cx, y0, 0x80FF80);
				y0 += 9;
			}
		}
	}

	private void renderPreview(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		int hintH = font.split(Component.translatable("screen.projecthero.perch_editor.drag_hint"), previewW - 12).size() * 9;
		int areaBottom = height - 52 - hintH;
		int cx = previewW / 2;
		int cy = 14 + (int) ((areaBottom - 14) * 0.62f);
		float scale = Math.min(previewW, areaBottom - 14) * 0.3f * zoom;
		PoseStack ps = g.pose();
		ps.pushPose();
		ps.translate(cx, cy, 200);
		ps.scale(scale, scale, -scale);
		ps.mulPose(new Quaternionf().rotateZ((float) Math.PI).rotateX(pitch * Mth.DEG_TO_RAD).rotateY(yaw * Mth.DEG_TO_RAD));
		Lighting.setupForEntityInInventory();
		EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
		d.setRenderShadow(false);
		float by = player.yBodyRot, byo = player.yBodyRotO, hy = player.yHeadRot, hyo = player.yHeadRotO;
		float xr = player.getXRot(), xro = player.xRotO, yr = player.getYRot(), yro = player.yRotO;
		player.yBodyRot = player.yBodyRotO = 0f;
		player.yHeadRot = player.yHeadRotO = 0f;
		player.setXRot(0f);
		player.xRotO = 0f;
		player.setYRot(0f);
		player.yRotO = 0f;
		SpiderPose.previewPerch = true;
		try {
			RenderSystem.runAsFancy(() -> d.render(player, 0.0, 0.0, 0.0, 0.0f, 1.0f, ps, g.bufferSource(), 0xF000F0));
			if (showBlock) {
				ps.pushPose();
				// the block under him, its edge just in front of his toes (he faces +z, out over the drop)
				ps.translate(-0.5, -1.0, -0.55);
				mc.getBlockRenderer().renderSingleBlock(Blocks.STONE_BRICKS.defaultBlockState(), ps, g.bufferSource(), 0xF000F0,
						OverlayTexture.NO_OVERLAY);
				ps.popPose();
			}
			g.flush();
		} finally {
			SpiderPose.previewPerch = false;
			player.yBodyRot = by;
			player.yBodyRotO = byo;
			player.yHeadRot = hy;
			player.yHeadRotO = hyo;
			player.setXRot(xr);
			player.xRotO = xro;
			player.setYRot(yr);
			player.yRotO = yro;
			d.setRenderShadow(true);
			Lighting.setupFor3DItems();
			ps.popPose();
		}
	}
}

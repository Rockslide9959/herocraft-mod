package com.projecthero.mod.client.gui;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManMark3;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.network.IronManWeaponWheelPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The Mark 7 weapon wheel ("changes 16" / "changes 17"; v0.14.21 redesign): a radial menu of six real pie wedges
 * (ring sectors) -- five that re-bind slot 3 (X) to an ability (micro missiles, flamethrower, wrist laser, rocket,
 * supersonic flight) and one that toggles the coloured entity-glow overlay. Each wedge carries the ability's
 * icon; the hovered wedge lifts and brightens, the bound one wears a gold rim. The centre disc names the hovered
 * (else the bound) option with a one-line description. v0.15.3: open only while V (the slot-5 key) is held -- letting
 * go of V equips the hovered wedge ({@link IronManUiLayout#wheelReleaseChoice}); a click still picks one too.
 *
 * <p>The hit-test is {@link IronManUiLayout#wheelSector}: sector 0 centred straight up, half-sector offset
 * ("changes 17" fix) so the wedge under the cursor is the wedge that lights up.
 */
public final class IronManWeaponWheelScreen extends Screen {
	/** v0.14.27: the Mark III wheel (Rockets / Miniguns / Micro-Missiles for G) reuses this screen with its own wedges. */
	private final boolean mark3;
	/** v0.14.29: which kit suit this Mark III-style wheel belongs to (Mark III or Mark 4); null for the Mark VII wheel. */
	private final String kitSuit;
	/** v0.15.4: the Mark 6 / Mark 7 arsenal wheel ({@link com.projecthero.mod.ironman.ability.IronManMark6#WEAPONS}, picks what G fires). */
	private final boolean modern;
	private final String[] SECTORS;
	private int hovered = -1;
	private final float[] lift;
	private long lastNanos;

	public IronManWeaponWheelScreen() {
		this(false);
	}

	public IronManWeaponWheelScreen(boolean mark3) {
		this(mark3 ? IronManMark3.SUIT_ID : null);
	}

	/** v0.14.29: {@code kitSuit} = the Mark III / Mark 4 whose arsenal wheel to show, null = the Mark VII wheel. */
	public IronManWeaponWheelScreen(String kitSuit) {
		this(kitSuit, false);
	}

	/** v0.15.4: the Mark 6 / Mark 7 arsenal wheel for {@code suitId}. */
	public static IronManWeaponWheelScreen modern(String suitId) {
		return new IronManWeaponWheelScreen(suitId, true);
	}

	private IronManWeaponWheelScreen(String kitSuit, boolean modern) {
		super(Component.translatable(modern
				? (com.projecthero.mod.ironman.ability.IronManMark7.SUIT_ID.equals(kitSuit)
						? "screen.projecthero.weapon_wheel.mk7_title"
						: com.projecthero.mod.ironman.ability.IronManMark7.MARK_8_ID.equals(kitSuit) // v0.15.9
						? "screen.projecthero.weapon_wheel.mk8_title" : "screen.projecthero.weapon_wheel.mk6_title")
				: kitSuit == null ? "screen.projecthero.weapon_wheel.title"
				: IronManMark3.MARK_4_ID.equals(kitSuit) ? "screen.projecthero.weapon_wheel.mk4_title"
				: "screen.projecthero.weapon_wheel.mk3_title"));
		this.mark3 = kitSuit != null;
		this.modern = modern;
		this.kitSuit = kitSuit;
		this.SECTORS = modern ? com.projecthero.mod.ironman.ability.IronManMark6.WEAPONS : mark3 ? IronManMark3.WEAPONS : IronManAbilities.WEAPON_WHEEL_SECTORS;
		this.lift = new float[SECTORS.length];
	}

	private TonyStarkState state() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? null : mc.player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
	}

	private String currentBinding() {
		TonyStarkState s = state();
		if (modern) {
			return com.projecthero.mod.ironman.ability.IronManMark6.selectedWeapon(s, kitSuit);
		}
		if (mark3) {
			return IronManMark3.selectedWeapon(s, kitSuit);
		}
		return s == null ? SECTORS[0] : s.weaponWheelChoice;
	}

	private boolean glowOn() {
		Minecraft mc = Minecraft.getInstance(); // v0.15.4: the highlight state is synced to the wearer only
		return mc.player != null && mc.player.getAttachedOrElse(ModAttachments.IRON_MAN_HIGHLIGHT_ON, false);
	}

	private boolean isActive(String sector) {
		return IronManAbilities.ENTITY_GLOW_TOGGLE.equals(sector) ? glowOn() : sector.equals(currentBinding());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		this.renderTransparentBackground(g);
		int cx = this.width / 2;
		int cy = this.height / 2;
		int[] radii = IronManUiLayout.wheelRadii(width, height);
		float inner = radii[0];
		float outer = radii[1];
		int n = SECTORS.length;

		hovered = IronManUiLayout.wheelSector(mouseX - cx, mouseY - cy, n, inner - 6);

		long now = System.nanoTime();
		float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		float k = 1f - (float) Math.exp(-dt * 18f);
		for (int i = 0; i < n; i++) {
			lift[i] += ((i == hovered ? 1f : 0f) - lift[i]) * k;
		}

		g.drawCenteredString(this.font, this.title.copy().withStyle(s -> s.withBold(true)), cx,
				Math.max(4, cy - (int) outer - 18), IronManGui.CYAN);

		double half = Math.PI / n;
		double gap = 0.035;
		VertexConsumer vc = IronManGui.guiBuffer(g);
		Matrix4f m = g.pose().last().pose();
		// track ring behind everything
		IronManGui.sector(vc, m, cx, cy, inner - 3, outer + 3, 0, Math.PI * 2, 0xC0050A10);
		for (int i = 0; i < n; i++) {
			double c = IronManUiLayout.sectorCentre(i, n);
			float l = lift[i] * 4f;
			float ox = (float) Math.cos(c) * l;
			float oy = (float) Math.sin(c) * l;
			boolean active = isActive(SECTORS[i]);
			int fill = IronManGui.lerp(i % 2 == 0 ? 0xE00E1824 : 0xE0122030, 0xF0234A66, lift[i]);
			IronManGui.sector(vc, m, cx + ox, cy + oy, inner, outer, c - half + gap, c + half - gap, fill);
			int rim = active ? IronManGui.GOLD : IronManGui.lerp(0xFF2A4458, IronManGui.CYAN, lift[i]);
			IronManGui.sector(vc, m, cx + ox, cy + oy, outer - 2, outer, c - half + gap, c + half - gap, rim);
			IronManGui.sector(vc, m, cx + ox, cy + oy, inner, inner + 1, c - half + gap, c + half - gap,
					IronManGui.alpha(rim, 0.6f));
		}
		// centre disc
		IronManGui.sector(vc, m, cx, cy, 0, inner - 6, 0, Math.PI * 2, 0xF0070C14);
		IronManGui.sector(vc, m, cx, cy, inner - 7, inner - 6, 0, Math.PI * 2, IronManGui.CYAN_DIM);
		g.flush();

		// icons + short labels
		float mid = (inner + outer) / 2f;
		for (int i = 0; i < n; i++) {
			double c = IronManUiLayout.sectorCentre(i, n);
			float l = lift[i] * 4f;
			int ix = Math.round(cx + (float) Math.cos(c) * (mid + l));
			int iy = Math.round(cy + (float) Math.sin(c) * (mid + l));
			String sector = SECTORS[i];
			ItemStack icon = IronManHud.icon(sector, mark3 ? IronManSuits.byId(kitSuit) : IronManSuits.MARK_VII);
			g.renderItem(icon, ix - 8, iy - 12);
			if (IronManAbilities.ENTITY_GLOW_TOGGLE.equals(sector)) {
				boolean on = glowOn();
				String t = Component.translatable(on ? "screen.projecthero.weapon_wheel.on" : "screen.projecthero.weapon_wheel.off").getString();
				g.drawCenteredString(font, t, ix, iy + 6, on ? IronManGui.CYAN : IronManGui.TEXT_MUTED);
			} else if (isActive(sector)) {
				String t = Component.translatable("screen.projecthero.weapon_wheel.bound").getString();
				g.drawCenteredString(font, t, ix, iy + 6, IronManGui.GOLD);
			}
		}

		// centre readout: the hovered option, else the bound one
		int show = hovered >= 0 ? hovered : indexOf(currentBinding());
		if (show >= 0) {
			String sector = SECTORS[show];
			int textW = IronManUiLayout.wheelTextWidth(radii[0]);
			String name = IronManGui.fit(font, Component.translatable("hud.projecthero.ironman.ability." + sector), textW);
			g.drawCenteredString(font, name, cx, cy - 14, hovered >= 0 ? 0xFFFFFFFF : IronManGui.GOLD);
			int y = cy - 2;
			int lines = 0;
			for (FormattedCharSequence line : font.split(description(sector), textW)) {
				if (lines++ >= 2) {
					break;
				}
				g.drawCenteredString(font, line, cx, y, IronManGui.TEXT_DIM);
				y += 10;
			}
		}

		String hint = IronManGui.fit(font, Component.translatable(mark3 ? "screen.projecthero.weapon_wheel.mk3_hint" : "screen.projecthero.weapon_wheel.hint"), width - 12);
		g.drawCenteredString(this.font, hint, cx, Math.min(height - 12, cy + (int) outer + 10), IronManGui.TEXT_DIM);
	}

	/** v0.14.29: the Mark 4's rocket line quotes its own (+2) damage; everything else uses the shared text. */
	private Component description(String sector) {
		if (modern) {
			return Component.translatable("screen.projecthero.weapon_wheel.desc.mk6." + sector);
		}
		if (IronManMark3.MARK_4_ID.equals(kitSuit) && IronManMark3.ROCKETS.equals(sector)) {
			return Component.translatable("screen.projecthero.weapon_wheel.desc.mk4_rockets",
					Math.round(IronManMark3.damageFor(kitSuit, IronManMark3.ROCKET_DAMAGE)));
		}
		return Component.translatable("screen.projecthero.weapon_wheel.desc." + sector);
	}

	private int indexOf(String id) {
		for (int i = 0; i < SECTORS.length; i++) {
			if (SECTORS[i].equals(id)) {
				return i;
			}
		}
		return -1;
	}

	// ---------------- v0.15.3: hold V to open, release V to equip ----------------

	/** Is the V (ability 5) key physically held right now? */
	private static boolean wheelKeyHeld() {
		Minecraft mc = Minecraft.getInstance();
		com.mojang.blaze3d.platform.InputConstants.Key key =
				net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(com.projecthero.mod.client.ModKeyBindings.ABILITY_5);
		if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
			return org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), key.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
		}
		return com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
	}

	/** V let go: equip the hovered wedge (if it is a change) and close. Nothing hovered = keep the current weapon. */
	private void releaseSelect() {
		String pick = IronManUiLayout.wheelReleaseChoice(SECTORS, hovered, currentBinding(), IronManAbilities.ENTITY_GLOW_TOGGLE);
		if (pick != null) {
			ClientPlayNetworking.send(new IronManWeaponWheelPayload(pick));
		}
		onClose();
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (com.projecthero.mod.client.ModKeyBindings.ABILITY_5.matches(keyCode, scanCode)) {
			releaseSelect();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public void tick() {
		super.tick();
		// the release can land before the wheel even opened (a quick tap) or be swallowed by a focus change -- if V is
		// no longer down, treat it as released
		if (!wheelKeyHeld()) {
			releaseSelect();
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < SECTORS.length) {
			ClientPlayNetworking.send(new IronManWeaponWheelPayload(SECTORS[hovered]));
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

}

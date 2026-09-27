package com.projecthero.mod.client.gui;

import com.projecthero.mod.client.squad.SquadLocatorClient;
import com.projecthero.mod.client.squad.SquadClient;
import com.projecthero.mod.network.SquadInfoPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Bedrock Edition-style locator bar (v0.13.4, reworked into a hairline bar in v0.13.5): a thin line
 * across the top of the screen with every online, same-dimension squadmate's face sitting on it, sliding
 * left/right as you turn to face them (clamped to the bar's edges once they are behind you) and growing
 * or shrinking with distance. Squad members only -- see {@link SquadLocatorClient} for the toggle (a
 * button on the P/roster screen).
 *
 * <p>Purely a client-side render over data the client already has ({@link SquadClient}'s cached
 * roster, refreshed a few times a second by the server): no new networking of its own.
 */
public final class SquadLocatorBarHud {
	/** Thickness of the hairline itself. Faces sit centred on it, well outside these thin bounds. */
	private static final int HAIRLINE_HEIGHT = 3;
	/** v0.13.5: the baseline face size is triple the hairline's thickness; distance then scales a face
	 * up or down around this baseline (see {@link #sizeForDistance}) rather than using it as a fixed
	 * size. */
	private static final int BASE_ICON_SIZE = HAIRLINE_HEIGHT * 3;
	private static final int MIN_ICON_SIZE = Math.round(BASE_ICON_SIZE * 0.7f);
	private static final int MAX_ICON_SIZE = BASE_ICON_SIZE * 2;
	/** Distance range the size scaling is stretched across: at or under this many blocks a face is at
	 * its largest, at or beyond this many blocks it's at its smallest. */
	private static final double NEAR_DISTANCE = 6.0;
	private static final double FAR_DISTANCE = 100.0;

	private static final int BAR_WIDTH = 220;
	private static final int BAR_TOP = 16;
	/** Degrees of turn mapped across the full width of the bar; beyond this the icon just pins to the
	 * nearest edge, exactly like Bedrock's own locator bar does for a target behind you. */
	private static final float HALF_FOV_DEGREES = 70.0f;

	private static final int COLOR_HAIRLINE_BG = 0x80000000;
	private static final int COLOR_BORDER_FRONT = 0xFFE8E8F6;
	private static final int COLOR_BORDER_BEHIND = 0x90888888;

	private SquadLocatorBarHud() {
	}

	public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (!SquadLocatorClient.isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Player player = client.player;
		if (player == null || client.options.hideGui || !SquadClient.inSquad()) {
			return;
		}

		SquadInfoPayload squad = SquadClient.get();
		String myDimension = player.level().dimension().location().getPath();

		boolean anyVisible = false;
		for (SquadInfoPayload.Member m : squad.members()) {
			if (isLocatable(m, player, myDimension)) {
				anyVisible = true;
				break;
			}
		}
		if (!anyVisible) {
			return;
		}

		int centerX = graphics.guiWidth() / 2;
		int halfBar = BAR_WIDTH / 2;
		int hairlineY = BAR_TOP + MAX_ICON_SIZE / 2 - HAIRLINE_HEIGHT / 2;
		graphics.fill(centerX - halfBar, hairlineY, centerX + halfBar, hairlineY + HAIRLINE_HEIGHT, COLOR_HAIRLINE_BG);

		Vec3 look = player.getViewVector(1.0f);
		Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
		if (flatLook.lengthSqr() < 1.0E-6) {
			flatLook = new Vec3(0.0, 0.0, 1.0);
		} else {
			flatLook = flatLook.normalize();
		}
		int hairlineCenterY = hairlineY + HAIRLINE_HEIGHT / 2;

		for (SquadInfoPayload.Member m : squad.members()) {
			if (!isLocatable(m, player, myDimension)) {
				continue;
			}
			double dx = m.x() - player.getX();
			double dz = m.z() - player.getZ();
			double distance = Math.sqrt(dx * dx + dz * dz);

			Vec3 toMember = distance < 1.0E-3 ? flatLook : new Vec3(dx, 0.0, dz).normalize();
			// Signed horizontal angle from the look direction to the member (2D cross/dot trick), so
			// this never has to reverse-engineer Minecraft's yaw-to-world convention by hand.
			double cross = flatLook.x * toMember.z - flatLook.z * toMember.x;
			double dot = flatLook.dot(toMember);
			double relativeDegrees = Math.toDegrees(Math.atan2(cross, dot));

			boolean behind = Math.abs(relativeDegrees) > HALF_FOV_DEGREES;
			double clamped = Math.max(-HALF_FOV_DEGREES, Math.min(HALF_FOV_DEGREES, relativeDegrees));
			float t = (float) (clamped / HALF_FOV_DEGREES);
			int iconSize = sizeForDistance(distance);
			int iconX = centerX + Math.round(t * halfBar) - iconSize / 2;
			int iconY = hairlineCenterY - iconSize / 2;

			PlayerSkin skin = resolveSkin(m.id());
			if (skin != null) {
				PlayerFaceRenderer.draw(graphics, skin, iconX, iconY, iconSize);
			} else {
				graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF888888);
			}
			int borderColor = behind ? COLOR_BORDER_BEHIND : COLOR_BORDER_FRONT;
			graphics.renderOutline(iconX - 1, iconY - 1, iconSize + 2, iconSize + 2, borderColor);
		}
	}

	/** Bigger up close, smaller far away, clamped to a sane size range around {@link #BASE_ICON_SIZE}. */
	private static int sizeForDistance(double distance) {
		double t = Mth.clamp((distance - NEAR_DISTANCE) / (FAR_DISTANCE - NEAR_DISTANCE), 0.0, 1.0);
		return Math.round((float) Mth.lerp(t, MAX_ICON_SIZE, MIN_ICON_SIZE));
	}

	private static boolean isLocatable(SquadInfoPayload.Member m, Player self, String myDimension) {
		return m.online() && !m.id().equals(self.getUUID()) && myDimension.equals(m.dimension());
	}

	private static PlayerSkin resolveSkin(java.util.UUID id) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return null;
		}
		PlayerInfo info = client.getConnection().getPlayerInfo(id);
		return info == null ? null : info.getSkin();
	}
}

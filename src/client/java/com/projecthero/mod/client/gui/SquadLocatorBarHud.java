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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Bedrock Edition-style locator bar (v0.13.4): a strip across the top of the screen showing every
 * online, same-dimension squadmate as a small face icon that slides left/right as you turn to face
 * them, clamped to the bar's edges once they are behind you, with their distance underneath. Squad
 * members only -- see {@link SquadLocatorClient} for the toggle (a button on the P/roster screen).
 *
 * <p>Purely a client-side render over data the client already has ({@link SquadClient}'s cached
 * roster, refreshed a few times a second by the server): no new networking of its own.
 */
public final class SquadLocatorBarHud {
	private static final int ICON_SIZE = 16;
	private static final int BAR_WIDTH = 220;
	private static final int BAR_TOP = 6;
	/** Degrees of turn mapped across the full width of the bar; beyond this the icon just pins to the
	 * nearest edge, exactly like Bedrock's own locator bar does for a target behind you. */
	private static final float HALF_FOV_DEGREES = 70.0f;

	private static final int COLOR_BACKDROP = 0x60000000;
	private static final int COLOR_BORDER_FRONT = 0xFFE8E8F6;
	private static final int COLOR_BORDER_BEHIND = 0x90888888;
	private static final int COLOR_DISTANCE = 0xFFE8E8F6;

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

		int visibleCount = 0;
		for (SquadInfoPayload.Member m : squad.members()) {
			if (isLocatable(m, player, myDimension)) {
				visibleCount++;
			}
		}
		if (visibleCount == 0) {
			return;
		}

		int centerX = graphics.guiWidth() / 2;
		int halfBar = BAR_WIDTH / 2;
		graphics.fill(centerX - halfBar - 4, BAR_TOP - 3, centerX + halfBar + 4, BAR_TOP + ICON_SIZE + 12, COLOR_BACKDROP);

		Vec3 look = player.getViewVector(1.0f);
		Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
		if (flatLook.lengthSqr() < 1.0E-6) {
			flatLook = new Vec3(0.0, 0.0, 1.0);
		} else {
			flatLook = flatLook.normalize();
		}

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
			int iconX = centerX + Math.round(t * halfBar) - ICON_SIZE / 2;
			int iconY = BAR_TOP;

			PlayerSkin skin = resolveSkin(m.id());
			if (skin != null) {
				PlayerFaceRenderer.draw(graphics, skin, iconX, iconY, ICON_SIZE);
			} else {
				graphics.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF888888);
			}
			int borderColor = behind ? COLOR_BORDER_BEHIND : COLOR_BORDER_FRONT;
			graphics.renderOutline(iconX - 1, iconY - 1, ICON_SIZE + 2, ICON_SIZE + 2, borderColor);

			String distanceText = Math.round(distance) + "m";
			graphics.drawCenteredString(client.font, distanceText, iconX + ICON_SIZE / 2, iconY + ICON_SIZE + 2, COLOR_DISTANCE);
		}
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

package com.projecthero.mod.client.quirk.zerogravity;

import java.util.List;

import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityConfig;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.7 revamp: Zero Gravity's client side -- the keyframed poses ({@link ZeroGravityPose}), Float Self's low-gravity glide
 * for the local player, the Zero Gravity Field's pink dome and the HUD's Nausea block ({@link #drawStatus}).
 *
 * <p>Float Self: the server turned her gravity off and keeps her from being kicked; at the start of every client tick the owning
 * client moves her. Jump rises, Sneak sinks, no input: a slow glide down ({@link ZeroGravityConfig#SELF_GLIDE_SINK}); the move
 * keys push her gently the way she faces, capped, and she drifts on when they are let go.
 */
public final class ZeroGravityClient implements QuirkClientKit {
	private static final DustParticleOptions FIELD_PINK = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.85f), 1.2f);
	private static final int BAR_BG = 0xC0141414;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int NAME = 0xFFE2E2E2;
	private static final int PINK = 0xFFFF8FC8;

	private ZeroGravityClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.ZERO_GRAVITY, new ZeroGravityClient());
		ClientTickEvents.START_CLIENT_TICK.register(ZeroGravityClient::glide);
		ClientTickEvents.END_CLIENT_TICK.register(ZeroGravityClient::fx);
	}

	private static void glide(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !ZeroGravity.selfFloating(p) || p.getAbilities().flying || p.isPassenger()) {
			return;
		}
		Vec3 v = p.getDeltaMovement();
		double vy;
		if (p.input.jumping) {
			vy = Mth.lerp(0.35, v.y, ZeroGravityConfig.SELF_RISE_SPEED);
		} else if (p.input.shiftKeyDown) {
			vy = Mth.lerp(0.35, v.y, -ZeroGravityConfig.SELF_SINK_SPEED);
		} else {
			// low gravity: she glides down slowly
			vy = Mth.lerp(0.12, v.y * ZeroGravityConfig.SELF_HOVER_DRAG, -ZeroGravityConfig.SELF_GLIDE_SINK);
		}
		float fwd = p.input.forwardImpulse;
		float side = p.input.leftImpulse;
		double hx = v.x * 0.985;
		double hz = v.z * 0.985;
		if (fwd != 0f || side != 0f) {
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			double sin = Mth.sin(yaw);
			double cos = Mth.cos(yaw);
			hx += (side * cos - fwd * sin) * 0.035;
			hz += (fwd * cos + side * sin) * 0.035;
			double h = Math.sqrt(hx * hx + hz * hz);
			if (h > ZeroGravityConfig.SELF_MAX_SPEED) {
				hx *= ZeroGravityConfig.SELF_MAX_SPEED / h;
				hz *= ZeroGravityConfig.SELF_MAX_SPEED / h;
			}
		}
		p.setDeltaMovement(hx, vy, hz);
		p.resetFallDistance();
	}

	/** The Field's dome (pink rings rising and sparkles inside) for every Zero Gravity player near the viewer. */
	private static void fx(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused() || mc.player == null) {
			return;
		}
		RandomSource rnd = level.getRandom();
		long now = level.getGameTime();
		for (Player p : level.players()) {
			if (!ZeroGravity.is(p) || p.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			if (ZeroGravity.running(p, ZeroGravity.V_FIELD)) {
				Vec3 c = ZeroGravity.vec(p, ZeroGravity.VEC_FIELD);
				if (c != null) {
					double r = ZeroGravityConfig.FIELD_RADIUS;
					// a ground ring, and a ring that rises through the dome over and over
					for (int i = 0; i < 6; i++) {
						double a = rnd.nextDouble() * Math.PI * 2.0;
						level.addParticle(FIELD_PINK, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.02, 0);
					}
					double h = ((now % 30) / 30.0) * r;
					double rr = Math.sqrt(Math.max(0.0, r * r - h * h));
					for (int i = 0; i < 5; i++) {
						double a = rnd.nextDouble() * Math.PI * 2.0;
						level.addParticle(FIELD_PINK, c.x + Math.cos(a) * rr, c.y + h, c.z + Math.sin(a) * rr, 0, 0, 0);
					}
					if (rnd.nextInt(2) == 0) {
						level.addParticle(FIELD_PINK, c.x + (rnd.nextDouble() - 0.5) * r * 1.4, c.y + rnd.nextDouble() * 2.5,
								c.z + (rnd.nextDouble() - 0.5) * r * 1.4, 0, 0.03, 0);
					}
				}
			}
			if (ZeroGravity.settling(p) && rnd.nextInt(4) == 0) {
				level.addParticle(FIELD_PINK, p.getX() + (rnd.nextDouble() - 0.5) * 0.8, p.getY() + 0.2, p.getZ() + (rnd.nextDouble() - 0.5) * 0.8, 0,
						0.03, 0);
			}
			if (Quirks.overloaded(p) && rnd.nextInt(5) == 0) {
				level.addParticle(ParticleTypes.SNEEZE, p.getX(), p.getEyeY() - 0.2, p.getZ(), 0, -0.02, 0);
			}
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ZeroGravityPose.apply(player, model);
	}

	/**
	 * The Nausea block: a status line (SICK / Settling / what she holds up, wrapped to the HUD's width) over the Nausea bar, pastel
	 * pink that turns a sickly green past {@link ZeroGravityConfig#NAUSEA_WARN}.
	 */
	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		long now = player.level().getGameTime();
		float nausea = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		boolean sick = Quirks.overloaded(player);
		int barH = 4;
		int by = bottom - 1 - barH;
		Component label = Component.translatable("hud.projecthero.zero_gravity.nausea");
		int labelW = mc.font.width(label);
		String value = String.valueOf(Math.round(nausea));
		int valueW = mc.font.width("100");
		int barX = x0 + labelW + 4;
		int barW = Math.max(20, width - labelW - 4 - valueW - 3);
		int colour;
		if (sick) {
			colour = (now / 5) % 2 == 0 ? 0xFF9BD45A : 0xFF6FA040;
		} else if (nausea >= ZeroGravityConfig.NAUSEA_WARN) {
			float t = (nausea - ZeroGravityConfig.NAUSEA_WARN) / (100f - ZeroGravityConfig.NAUSEA_WARN);
			colour = 0xFF000000 | ((int) Mth.lerp(t, 0xFF, 0xA8) << 16) | ((int) Mth.lerp(t, 0x8F, 0xD8) << 8) | (int) Mth.lerp(t, 0xC8, 0x60);
		} else {
			colour = PINK;
		}
		g.drawString(mc.font, label, x0, by - 2, NAME, false);
		g.fill(barX - 1, by - 1, barX + barW + 1, by + barH + 1, BORDER);
		g.fill(barX, by, barX + barW, by + barH, BAR_BG);
		com.projecthero.mod.client.quirk.QuirkHud.bandedFill(g, player, com.projecthero.mod.quirk.Quirk.ZERO_GRAVITY, barX, by, barW, barH, nausea, colour); // v0.17.1 bands
		g.drawString(mc.font, value, x0 + width - mc.font.width(value), by - 2, NAME, false);
		int top = by - 2;

		Component status = null;
		int sc = PINK;
		long things = ZeroGravity.val(player, ZeroGravity.V_FLOATING);
		long blocks = ZeroGravity.val(player, ZeroGravity.V_BLOCKS);
		if (sick) {
			status = Component.translatable("hud.projecthero.zero_gravity.sick");
			sc = 0xFF9BD45A;
		} else if (ZeroGravity.settling(player)) {
			status = Component.translatable("hud.projecthero.zero_gravity.settling");
		} else if (ZeroGravity.running(player, ZeroGravity.V_CLOUD)) {
			status = Component.translatable("hud.projecthero.zero_gravity.cloud", blocks);
		} else if (things > 0 || blocks > 0) {
			status = Component.translatable("hud.projecthero.zero_gravity.floating", things, blocks);
		} else if (ZeroGravity.selfFloating(player)) {
			status = Component.translatable("hud.projecthero.zero_gravity.self");
		}
		if (status != null) {
			List<FormattedCharSequence> lines = mc.font.split(status, width);
			int y = top - 2 - lines.size() * 9;
			top = y;
			for (FormattedCharSequence line : lines) {
				g.drawString(mc.font, line, x0, y, sc, false);
				y += 9;
			}
		}
		return top;
	}
}

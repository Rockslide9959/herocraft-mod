package com.projecthero.mod.client.quirk.explosion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.client.quirk.QuirkHud;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;
import com.projecthero.mod.quirk.explosion.ExplosionInputPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp), v0.17.1 (Quirks revamp): the Explosion Quirk's client side -- the poses ({@link ExplosionPose}, the
 * first-person clips in {@link ExplosionFirstPerson}; the Howitzer's / Spin Dodge's whole-body turns go through
 * {@code QuirkBodyTurnMixin}), Explosion Propulsion's flight ({@link ExplosionPropulsionClient}), the corkscrew vortex and
 * palm glows ({@link ExplosionRender}), the WASD hint for the Dash / Spin Dodge, the palm-crackle tell and the HUD block:
 * <pre>
 *   [drop] Sweat [=====|=========|====]  63        the sweat-droplet bar (Dry / Primed / Soaked bands, the C thresholds)
 *   [arm][arm]  Gauntlets ooooo                    Arm Strain as two forearm icons that fill and heat up; the suit's bank
 * </pre>
 */
public final class ExplosionClient implements QuirkClientKit {
	private static final int NAME = 0xFFE2E2E2;
	private static final int HINT = 0xFF9A9A9A;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BAR_BG = 0xC0141414;
	private static final int AMBER = 0xFFFFB020;
	private static final int AMBER_LIGHT = 0xFFFFD87A;
	private static final int ORANGE = 0xFFFF7A2A;
	private static final int RED = 0xFFFF4A3A;

	private static int sentForward;
	private static int sentStrafe;

	private ExplosionClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.EXPLOSION, new ExplosionClient());
		ClientTickEvents.START_CLIENT_TICK.register(ExplosionPropulsionClient::startTick);
		ClientTickEvents.END_CLIENT_TICK.register(ExplosionPropulsionClient::endTick);
		ClientTickEvents.END_CLIENT_TICK.register(ExplosionClient::sendInput);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ExplosionPropulsionClient.reset();
			sentForward = 0;
			sentStrafe = 0;
		});
		ExplosionRender.register();
		ExplosionFirstPerson.register();
	}

	/** The WASD hint for the server (the Dash's direction, the Spin Dodge's side), sent only when it changes. */
	private static void sendInput(Minecraft mc) {
		var p = mc.player;
		if (p == null || !Explosion.is(p) || !ClientPlayNetworking.canSend(ExplosionInputPayload.TYPE)) {
			return;
		}
		int f = (int) Math.signum(p.input.forwardImpulse);
		int s = (int) Math.signum(p.input.leftImpulse);
		if (f != sentForward || s != sentStrafe) {
			sentForward = f;
			sentStrafe = s;
			ClientPlayNetworking.send(new ExplosionInputPayload(f, s));
		}
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		ExplosionPose.apply(player, model);
	}

	@Override
	public float holdCharge(Player player, AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_1) {
			return Explosion.pierceCharge(player, 0f);
		}
		if (slot == AbilitySlot.SLOT_2 && Explosion.gripping(player)) {
			long at = Explosion.val(player, Explosion.V_GRIP);
			return Mth.clamp((player.level().getGameTime() - at) / (float) ExplosionConfig.GRIP_THROW_AT, 0f, 1f);
		}
		return -1f;
	}

	@Override
	public boolean fullBodyFirstPerson(Player player) {
		return Explosion.spinning(player) || Explosion.dodging(player);
	}

	/**
	 * The palm crackle tell: sparks snapping and popping off both palms, from Primed up and fiercely when Soaked (and while
	 * the Plus Ultra C is held); the arms smoke-dark glow red past 70 strain.
	 */
	@Override
	public boolean renderTell(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> model, float partial) {
		float hold = PlusUltra.clientHold(player);
		// v0.17.1 (user): no palm sparks from high sweat -- only while the Plus Ultra C is held
		float k = hold >= 0f ? 1f : 0f;
		float strain = Explosion.strain(player);
		if (k <= 0f && strain < ExplosionConfig.STRAIN_WARN) {
			return true;
		}
		float t = player.tickCount + partial;
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			pose.pushPose();
			arm.translateAndRotate(pose);
			pose.translate(right ? -0.06f : 0.06f, 0.64f, 0f);
			if (strain >= ExplosionConfig.STRAIN_WARN) {
				float s = Mth.clamp((strain - ExplosionConfig.STRAIN_WARN) / 30f, 0f, 1f);
				QuirkFxDraw.shell(vc, pose.last(), 0.16f, 0.2f, player.getId() * 7L, t * 0.3f, 0xFF3A10, 0.22f * (0.5f + 0.5f * s));
			}
			if (k > 0f) {
				// a few sparks, each living two ticks at a random spot round the palm
				int sparks = k > 0.5f ? 4 : 2;
				long tick = (long) t / 2;
				for (int i = 0; i < sparks; i++) {
					float h = QuirkFxDraw.hash(player.getId() * 977L + (right ? 1 : 2), (int) tick, i);
					if (h > 0.25f + 0.5f * (1f - k)) {
						continue;
					}
					float ox = (QuirkFxDraw.hash(tick, i, 1) - 0.5f) * 0.22f;
					float oy = (QuirkFxDraw.hash(tick, i, 2) - 0.5f) * 0.18f;
					float oz = (QuirkFxDraw.hash(tick, i, 3) - 0.5f) * 0.22f;
					pose.pushPose();
					pose.translate(ox, oy, oz);
					QuirkFxDraw.shell(vc, pose.last(), 0.035f + 0.03f * k, 0.4f, tick * 31L + i, t, 0xFFF4C0, 0.9f);
					QuirkFxDraw.shell(vc, pose.last(), 0.07f + 0.04f * k, 0.4f, tick * 17L + i, t, 0xFF8A20, 0.45f);
					pose.popPose();
				}
				QuirkFxDraw.shell(vc, pose.last(), 0.1f, 0.3f, player.getId() * 3L, t * 0.5f, 0xFFB040, 0.12f * k);
			}
			pose.popPose();
		}
		return true;
	}

	@Override
	public int drawStatus(GuiGraphics g, Player player, int x0, int bottom, int width) {
		Minecraft mc = Minecraft.getInstance();
		var font = mc.font;
		long now = player.level().getGameTime();
		boolean strained = Quirks.overloaded(player);

		// ---- the arms (bottom row): two forearm icons that fill with the strain and heat from amber to red
		float strain = strained ? 100f : Mth.clamp(Explosion.strain(player), 0f, 100f);
		int ay = bottom - 8;
		Component armsLabel = Component.translatable("hud.projecthero.quirk.explosion.arms");
		g.drawString(font, armsLabel, x0, ay - 1, strained ? RED : HINT, false);
		int ax = x0 + font.width(armsLabel) + 4;
		int armCol = strained ? ((now / 4) % 2 == 0 ? RED : 0xFFFFFFFF) : strain >= 90f ? ((now / 4) % 2 == 0 ? RED : ORANGE)
				: strain >= ExplosionConfig.STRAIN_WARN ? ORANGE : AMBER_LIGHT;
		drawArm(g, ax, ay - 1, false, strain, armCol);
		drawArm(g, ax + 19, ay - 1, true, strain, armCol);
		int right = ax + 35;
		// the Ground Zero gauntlets' bank (full suit only)
		float bank = Explosion.bank(player);
		if (bank > 0f) {
			Component bl = Component.translatable("hud.projecthero.quirk.explosion.bank");
			int bx = right + 6;
			if (bx + font.width(bl) + 2 + 5 * 5 <= x0 + width) {
				g.drawString(font, bl, bx, ay - 1, HINT, false);
				int px = bx + font.width(bl) + 3;
				for (int i = 0; i < 5; i++) {
					boolean full = bank >= (i + 1) * ExplosionConfig.BANK_MAX / 5f - 0.01f;
					g.fill(px + i * 5, ay + 1, px + i * 5 + 3, ay + 4, full ? AMBER_LIGHT : 0xFF505050);
				}
			}
		}

		// ---- the sweat-droplet bar
		float sweat = Mth.clamp(Quirks.clientMeter(player), 0f, 100f);
		Component sweatLabel = Component.translatable("projecthero.quirk.meter.sweat");
		int by = ay - 13;
		drawDrop(g, x0, by - 2, sweat >= ExplosionConfig.SOAKED_FROM ? AMBER_LIGHT : AMBER, sweat >= ExplosionConfig.SOAKED_FROM && (now / 3) % 2 == 0);
		int lx = x0 + 8;
		g.drawString(font, sweatLabel, lx, by - 2, NAME, false);
		int valueW = font.width("100");
		int barX = lx + font.width(sweatLabel) + 4;
		int barW = Math.max(20, x0 + width - barX - valueW - 3);
		// the droplet bar: rounded ends, a highlight along the top
		g.fill(barX, by - 1, barX + barW, by + 5, BORDER);
		g.fill(barX - 1, by, barX + barW + 1, by + 4, BORDER);
		g.fill(barX, by, barX + barW, by + 4, BAR_BG);
		QuirkHud.bandedFill(g, player, Quirk.EXPLOSION, barX, by, barW, 4, sweat, sweat >= ExplosionConfig.GAUNTLET_MIN_SWEAT ? AMBER_LIGHT : AMBER);
		for (float mark : new float[] { ExplosionConfig.HOWITZER_MIN_SWEAT, ExplosionConfig.GAUNTLET_MIN_SWEAT }) {
			int mx = barX + Math.round(barW * mark / 100f);
			g.fill(mx, by - 2, mx + 1, by + 6, sweat >= mark ? 0xFFFFFFFF : 0xFF707070);
		}
		String value = String.valueOf(Math.round(sweat));
		g.drawString(font, value, x0 + width - font.width(value), by - 2, NAME, false);
		return by - 3;
	}

	/** A 6x7 sweat drop. */
	private static void drawDrop(GuiGraphics g, int x, int y, int col, boolean glint) {
		int[][] rows = { { 2, 3 }, { 2, 3 }, { 1, 4 }, { 1, 4 }, { 0, 5 }, { 0, 5 }, { 1, 4 } };
		for (int r = 0; r < rows.length; r++) {
			g.fill(x + rows[r][0], y + r, x + rows[r][1] + 1, y + r + 1, col);
		}
		g.fill(x + 1, y + 4, x + 2, y + 5, glint ? 0xFFFFFFFF : 0xC0FFF0C0);
	}

	/**
	 * A 16x9 forearm-and-fist icon (mirrored for the left arm): the forearm fills from the elbow with the strain, the fist
	 * last; amber, orange past 70, blinking red near full (and white-red while the arms are shot).
	 */
	private static void drawArm(GuiGraphics g, int x, int y, boolean mirror, float strain, int col) {
		// columns 0..15 from the elbow (mirror: from the right)
		int[][] shape = { { 0, 2, 6 }, { 1, 2, 6 }, { 2, 2, 6 }, { 3, 2, 6 }, { 4, 2, 6 }, { 5, 1, 7 }, { 6, 1, 7 }, { 7, 1, 7 }, { 8, 1, 7 },
				{ 9, 1, 7 }, { 10, 0, 8 }, { 11, 0, 8 }, { 12, 0, 8 }, { 13, 0, 8 }, { 14, 1, 7 }, { 15, 2, 6 } };
		int fill = Math.round(16 * strain / 100f);
		for (int[] c : shape) {
			int px = mirror ? x + 15 - c[0] : x + c[0];
			g.fill(px, y + c[1], px + 1, y + c[2] + 1, BORDER);
			boolean filled = c[0] < fill;
			int inner = filled ? col : BAR_BG;
			g.fill(px, y + c[1] + 1, px + 1, y + c[2], inner);
		}
		// the knuckles: three dark creases across the fist
		for (int k = 0; k < 3; k++) {
			int px = mirror ? x + 15 - 12 : x + 12;
			g.fill(px, y + 2 + k * 2, px + 2 * (mirror ? -1 : 1) + (mirror ? 1 : 0), y + 3 + k * 2, 0x60000000);
		}
		// the palm's sweat glint
		int gx = mirror ? x + 15 - 11 : x + 11;
		g.fill(gx, y + 1, gx + 1, y + 2, 0x90FFFFFF);
	}
}

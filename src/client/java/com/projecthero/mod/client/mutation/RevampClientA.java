package com.projecthero.mod.client.mutation;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.network.AbilityInputPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22 mutation revamp, batch A -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>01 Super Strength</li>
 * <li>02 Laser Vision</li>
 * <li>03 Flight</li>
 * <li>04 Super Speed</li>
 * <li>12 Super Regeneration</li>
 * <li>13 Super Durability</li>
 * </ul>
 * Also owns Flight's double-tap-jump toggle and the Haymaker combo pips under the crosshair.
 */
public final class RevampClientA {
	private static final ResourceLocation P01_VEINS = tex("p01_veins");
	private static final ResourceLocation P03_STREAKS = tex("p03_streaks");
	private static final ResourceLocation P04_CRACKLE_A = tex("p04_crackle_a");
	private static final ResourceLocation P04_CRACKLE_B = tex("p04_crackle_b");
	private static final ResourceLocation P12_VEINS = tex("p12_veins");
	private static final ResourceLocation P13_PLATING = tex("p13_plating");
	private static final ResourceLocation P13_GLINT = tex("p13_glint");

	private static final String FLIGHT = "power_03_flight";
	private static final String STRENGTH = "power_01_super_strength";

	private RevampClientA() {
	}

	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/mutation/" + name + ".png");
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(com.projecthero.mod.hero.revamp.batcha.BatchAEntities.THROWN_CHUNK,
				com.projecthero.mod.client.revamp.batcha.ThrownChunkRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(RevampClientA::flightDoubleTap);
		HudRenderCallback.EVENT.register((graphics, delta) -> haymakerPips(graphics));
	}

	// =============================================================================================
	// poses: {tick, rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX}
	// =============================================================================================

	private static float[] f(int tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(int tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static void registerPoses() {
		// ---- 01 Super Strength ----
		float[] diveA = { 0, -3.0f, 0, -0.18f, -3.0f, 0, 0.18f, 0.1f, 0, 0.25f, -0.15f, 0.3f };
		float[] diveB = { 0, -2.9f, 0, -0.22f, -2.9f, 0, 0.22f, 0.12f, 0, 0.15f, -0.05f, 0.3f };
		MutationPose.registerLoop("p01.dive", 3, new float[][] { z(0), at(3, diveA), at(6, diveB), at(9, diveA) });
		float[] rushA = f(0, -0.9f, -0.5f, 0.1f, 0.6f, 0.2f, -0.1f, 0.45f, 0.35f, 0.9f, -0.9f, -0.25f);
		float[] rushB = f(0, -0.9f, -0.5f, 0.1f, 0.6f, 0.2f, -0.1f, 0.45f, 0.35f, -0.9f, 0.9f, -0.25f);
		MutationPose.registerLoop("p01.rush", 3, new float[][] { z(0), at(3, rushA), at(6, rushB), at(9, rushA) });

		// ---- 02 Laser Vision ----
		float[] temple = f(0, -2.35f, -0.65f, 0.25f, 0, 0, 0, 0.05f, 0, 0, 0, 0.08f);
		MutationPose.register("p02.glare", new float[][] { z(0), at(3, temple), at(9, temple), z(13) });
		float[] recoil = f(0, -0.9f, 0, 0.55f, -0.9f, 0, -0.55f, -0.35f, 0, -0.4f, 0.3f, 0);
		MutationPose.register("p02.recoil", new float[][] { z(0), at(2, recoil), at(8, recoil), z(14) });
		float[] sweepL = f(0, 0.2f, 0, 0.3f, 0.2f, 0, -0.3f, 0, 0.85f, 0, 0, 0);
		float[] sweepR = f(0, 0.2f, 0, 0.3f, 0.2f, 0, -0.3f, 0, -0.85f, 0, 0, 0);
		MutationPose.register("p02.sweep", new float[][] { z(0), at(2, sweepL), at(12, sweepR), z(16) });
		float[] cauter = f(0, -1.15f, -0.75f, 0, -1.15f, 0.75f, 0, 0.2f, 0, 0.1f, -0.1f, 0.35f);
		float[] cauter2 = f(0, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, -0.15f, 0, 0, 0, -0.25f);
		MutationPose.register("p02.cauterize", new float[][] { z(0), at(4, cauter), at(11, cauter), at(15, cauter2), z(20) });

		// ---- 03 Flight ----
		float[] superA = f(0, -3.0f, 0.1f, 0.05f, 0.25f, 0, -0.18f, 0.08f, 0, 0.3f, 0.45f, 0);
		float[] superB = f(0, -3.05f, 0.1f, 0.08f, 0.3f, 0, -0.22f, 0.1f, 0, 0.4f, 0.35f, 0);
		MutationPose.registerLoop("p03.superman", 4, new float[][] { z(0), at(4, superA), at(14, superB), at(24, superA) });
		float[] fdive = f(0, -3.05f, 0, -0.12f, -3.05f, 0, 0.12f, 0, 0, 0.1f, 0.1f, 0.4f);
		float[] fdive2 = f(0, -3.1f, 0, -0.08f, -3.1f, 0, 0.08f, 0, 0, 0.14f, 0.06f, 0.4f);
		MutationPose.registerLoop("p03.dive", 3, new float[][] { z(0), at(3, fdive), at(5, fdive2), at(7, fdive) });
		float[] rise = f(0, -3.1f, 0, -0.06f, -3.1f, 0, 0.06f, -0.05f, 0, 0.05f, -0.05f, -0.35f);
		float[] rise2 = f(0, -3.05f, 0, -0.1f, -3.05f, 0, 0.1f, -0.05f, 0, -0.05f, 0.05f, -0.35f);
		MutationPose.registerLoop("p03.rise", 2, new float[][] { z(0), at(2, rise), at(4, rise2), at(6, rise) });
		float[] rollR = f(0, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, 0, -0.7f, 0.2f, -0.2f, 0);
		float[] rollR2 = f(0, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0, 0.6f, -0.2f, 0.2f, 0);
		MutationPose.register("p03.roll_right", new float[][] { z(0), at(3, rollR), at(7, rollR2), z(12) });
		float[] rollL = f(0, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, 0, 0.7f, -0.2f, 0.2f, 0);
		float[] rollL2 = f(0, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0, -0.6f, 0.2f, -0.2f, 0);
		MutationPose.register("p03.roll_left", new float[][] { z(0), at(3, rollL), at(7, rollL2), z(12) });
		float[] beck1 = f(0, -1.7f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0);
		float[] beck2 = f(0, -2.8f, 0, 0.45f, 0, 0, 0, -0.05f, 0, 0, 0, -0.1f);
		MutationPose.register("p03.beckon", new float[][] { z(0), at(3, beck1), at(7, beck2), at(11, beck1), z(15) });
		float[] carryF = f(0, -0.7f, 0, 0.25f, -0.7f, 0, -0.25f, 0.1f, 0, 0.15f, 0.2f, 0.25f);
		MutationPose.registerLoop("p03.carry", 4, new float[][] { z(0), at(4, carryF), at(14, carryF) });

		// ---- 04 Super Speed ----
		float[] jR = f(0, -1.6f, -0.1f, 0, 0.4f, 0.2f, 0, 0.15f, -0.4f, -0.2f, 0.2f, 0);
		float[] jL = f(0, 0.4f, -0.2f, 0, -1.6f, 0.1f, 0, 0.15f, 0.4f, 0.2f, -0.2f, 0);
		MutationPose.register("p04.flurry", new float[][] { z(0), at(1, jR), at(3, jL), at(5, jR), at(7, jL), at(9, jR), z(13) });
		float[] hold = f(0, -1.35f, -0.28f, 0, -1.35f, 0.28f, 0, 0.12f, 0, 0, 0, 0);
		MutationPose.registerLoop("p04.carry", 4, new float[][] { z(0), at(4, hold), at(14, hold) });
		float[] vibA = f(0, -0.3f, 0, 0.35f, -0.3f, 0, -0.35f, 0.1f, 0.18f, 0.1f, -0.1f, 0);
		float[] vibB = f(0, -0.3f, 0, 0.4f, -0.3f, 0, -0.4f, 0.1f, -0.18f, -0.1f, 0.1f, 0);
		MutationPose.register("p04.vibrate", new float[][] { z(0), at(1, vibA), at(2, vibB), at(3, vibA), at(4, vibB),
				at(5, vibA), at(6, vibB), at(7, vibA), z(10) });

		// ---- 12 Super Regeneration ----
		float[] chest = f(0, -1.1f, -0.8f, 0, -1.1f, 0.8f, 0, 0.1f, 0, 0, 0, 0.3f);
		float[] open = f(0, -0.6f, 0, 1.1f, -0.6f, 0, -1.1f, -0.1f, 0, 0, 0, -0.25f);
		MutationPose.register("p12.heal", new float[][] { z(0), at(3, chest), at(8, chest), at(12, open), z(18) });
		float[] hunch = f(0, 0.3f, 0, 0.2f, 0.3f, 0, -0.2f, 0.5f, 0, 0.2f, -0.1f, 0.4f);
		float[] fling = f(0, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.2f, 0, 0, 0, -0.3f);
		MutationPose.register("p12.purge", new float[][] { z(0), at(4, hunch), at(8, fling), at(12, fling), z(17) });
		float[] roar = f(0, 0.4f, 0, 1.0f, 0.4f, 0, -1.0f, -0.25f, 0, 0.1f, -0.1f, -0.6f);
		float[] roar2 = f(0, 0.45f, 0, 1.1f, 0.45f, 0, -1.1f, -0.3f, 0, 0.1f, -0.1f, -0.65f);
		MutationPose.register("p12.roar", new float[][] { z(0), at(5, roar), at(10, roar2), at(16, roar), z(22) });

		// ---- 13 Super Durability ----
		float[] shoulder = f(0, -0.25f, 0.2f, 0.15f, -1.0f, 0.3f, 0, 0.35f, 0.75f, 0.6f, -0.6f, -0.1f);
		float[] shoulder2 = f(0, -0.25f, 0.2f, 0.15f, -1.0f, 0.3f, 0, 0.35f, 0.75f, -0.6f, 0.6f, -0.1f);
		MutationPose.register("p13.shoulder", new float[][] { z(0), at(3, shoulder), at(7, shoulder2), at(10, shoulder), z(14) });
		float[] spread = f(0, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, -0.15f, 0, 0, 0, -0.2f);
		float[] come = f(0, -1.5f, 0, 0.2f, -0.3f, 0, -1.2f, -0.15f, 0, 0, 0, -0.2f);
		MutationPose.register("p13.taunt", new float[][] { z(0), at(4, spread), at(9, spread), at(12, come), at(15, spread), z(20) });
	}

	private static float[] at(int tick, float[] pose) {
		float[] p = pose.clone();
		p[0] = tick;
		return p;
	}

	// =============================================================================================
	// overlays
	// =============================================================================================

	/** Emissive (additive) colour: intensity scales the rgb, since the eyes blend ignores alpha. */
	private static int glow(float intensity, int r, int g, int b) {
		float k = Math.max(0f, Math.min(1f, intensity));
		return MutationRender.argb(255, Math.round(r * k), Math.round(g * k), Math.round(b * k));
	}

	private static void registerOverlays() {
		// 01: red veins pulsing under the skin during Maximum Effort
		MutationOverlays.register("p01.effort", ctx -> MutationRender.shell(ctx, P01_VEINS, MutationRender.Shell.THIN,
				glow(0.55f + 0.45f * MutationRender.pulse(ctx, 14f), 255, 45, 25), true));

		// 02: glowing red eyes -- smouldering at rest, brighter with heat, blazing while a beam fires
		MutationOverlays.register("p02.eyes", ctx -> {
			float v = ctx.state().value("p02.eye_glow", 0.35f);
			float flicker = v >= 0.99f ? 0.9f + 0.1f * MutationRender.pulse(ctx, 3f) : 1f;
			MutationRender.eyes(ctx, glow(v * flicker, 255, 50, 30));
		});

		// 03: a sheath of wind streaks at the upper speed tiers / Sonic Flight
		MutationOverlays.register("p03.wind", ctx -> {
			float speed = ctx.state().value("p03.speed", 2f);
			float k = Math.min(1f, 0.25f + speed * 0.15f) * (0.8f + 0.2f * MutationRender.pulse(ctx, 4f));
			MutationRender.shell(ctx, P03_STREAKS, MutationRender.Shell.THICK, glow(k, 230, 240, 255), true);
		});

		// 04: yellow lightning crackling over the body (two frames swapped every other tick)
		MutationOverlays.register("p04.crackle", ctx -> {
			float intensity = ctx.state().value("p04.intensity", 1f);
			boolean frameA = ((int) ((ctx.ageInTicks() + ctx.partialTick()) / 2f)) % 2 == 0;
			float k = (intensity >= 2f ? 1.0f : 0.6f) * (0.75f + 0.25f * MutationRender.pulse(ctx, 5f));
			MutationRender.shell(ctx, frameA ? P04_CRACKLE_A : P04_CRACKLE_B, MutationRender.Shell.THIN, glow(k, 255, 225, 70), true);
		});

		// 12: green veins while regenerating hard (brighter in a Cellular Surge), red while Blood Rage runs
		MutationOverlays.register("p12.regen", ctx -> {
			float surge = ctx.state().value("p12.surge", 0f);
			float k = (surge > 0.5f ? 0.85f : 0.45f) * (0.6f + 0.4f * MutationRender.pulse(ctx, 20f));
			MutationRender.shell(ctx, P12_VEINS, MutationRender.Shell.THIN, glow(k, 80, 255, 120), true);
		});
		MutationOverlays.register("p12.rage", ctx -> MutationRender.shell(ctx, P12_VEINS, MutationRender.Shell.THIN,
				glow(0.6f + 0.4f * MutationRender.pulse(ctx, 10f), 255, 30, 30), true));

		// 13: thin metallic plating in Tank Mode; gilded, with a glinting sheen, while Unbreakable
		MutationOverlays.register("p13.steel", ctx -> {
			boolean gold = ctx.state().has("p13.gold");
			int tint = gold ? MutationRender.argb(255, 255, 205, 90) : MutationRender.argb(255, 215, 220, 230);
			MutationRender.shell(ctx, P13_PLATING, MutationRender.Shell.THIN, tint, false);
		});
		MutationOverlays.register("p13.gold", ctx -> MutationRender.shell(ctx, P13_GLINT, MutationRender.Shell.THIN,
				glow(0.35f + 0.65f * MutationRender.pulse(ctx, 9f), 255, 235, 150), true));
	}

	// =============================================================================================
	// Flight: double-tap jump toggles flight (sends the X slot's press / release edges)
	// =============================================================================================

	private static int clientTicks;
	private static int lastJumpTap = -100;
	private static boolean jumpWasDown;

	private static void flightDoubleTap(Minecraft client) {
		clientTicks++;
		LocalPlayer p = client.player;
		if (p == null || client.screen != null || client.getConnection() == null) {
			jumpWasDown = false;
			return;
		}
		boolean down = client.options.keyJump.isDown();
		if (down && !jumpWasDown) {
			if (clientTicks - lastJumpTap <= 7 && canToggleFlight(p)) {
				ClientPlayNetworking.send(new AbilityInputPayload(3, true));
				ClientPlayNetworking.send(new AbilityInputPayload(3, false));
				lastJumpTap = -100;
			} else {
				lastJumpTap = clientTicks;
			}
		}
		jumpWasDown = down;
	}

	/** Only when Flight is the selected mutation and nothing else would take the X slot. */
	private static boolean canToggleFlight(LocalPlayer p) {
		if (p.isCreative() || p.isSpectator() || p.isPassenger()) {
			return false;
		}
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !FLIGHT.equals(st.activePower) || !st.ownedPowers.contains(FLIGHT)) {
			return false;
		}
		return !com.projecthero.mod.power.ThorPowers.isHoldingMjolnir(p)
				&& !com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(p);
	}

	// =============================================================================================
	// Super Strength: Haymaker combo pips under the crosshair (the strength HUD rows are AbilityHud's own)
	// =============================================================================================

	private static void haymakerPips(GuiGraphics g) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer p = client.player;
		if (p == null || client.options.hideGui) {
			return;
		}
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !STRENGTH.equals(st.activePower)) {
			return;
		}
		float window = st.resources.getOrDefault(STRENGTH + "/combo_window", 0f);
		int combo = Math.round(st.resources.getOrDefault(STRENGTH + "/combo", 0f));
		if (window <= 0.5f || combo <= 0) {
			return;
		}
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2 + 10;
		for (int i = 0; i < 3; i++) {
			int x = cx - 10 + i * 8;
			int color = i < combo ? 0xFFFFB347 : (i == combo ? 0xFFFFE0A0 : 0x66FFFFFF);
			g.fill(x, cy, x + 5, cy + 3, color);
		}
	}
}

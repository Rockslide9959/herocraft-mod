package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixHud;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.omnitrix.alien.jetray.RecalFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.chromastone.ChromastoneKit;
import com.projecthero.mod.omnitrix.alien.jetray.RecalKitMath;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Chromastone's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i provided please
 * no extra things untill i say"): the crown / shoulder / back crystal spikes and the glowing eye / chest cubes are gone -- he is
 * ONLY the player model in the user's skin. Effects (world pass, {@link RecalFx}): the bolt, the prism / full-spectrum rainbow
 * beams, the refraction rays, the Prism Shield's hexagon of hard light, the Absorb Stance and Redirect shells, the UV flare's
 * glow; ribbons, rings and blasts come from the server as QuirkFx. The HUD shows the Charge meter (flashing when Overloading).
 *
 * <p>v0.17.2 round 4: his flight is {@link ChromastoneFlightPose}; the Prism Punch's fist, the Spectrum Sweep's beam from his hand and
 * the Charge Release (the colours drawn into his chest, then rings of the spectrum and rays out of him) are drawn here from the synced
 * clip, so every viewer sees them. Arm-pointing clips follow the aim up and down ({@link #play}).
 */
public final class ChromastoneClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/chromastone.png");

	public static final int VIOLET = 0xFFC860FF;
	public static final int WHITE = 0xFFFFF4FF;
	public static final int METER = 0xFFC060F0;

	private static boolean worldPass;

	// ---------------------------------------------------------------- third-person poses
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, QuirkKeyframes.AIM, -0.1f, 0, 0.35f, 0, -0.1f, 0.1f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(4, QuirkKeyframes.AIM, -0.1f, 0, 0.3f, 0, -0.1f, 0.08f, -0.26f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PRISM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, QuirkKeyframes.AIM, 0.25f, 0, QuirkKeyframes.AIM, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0),
			QuirkKeyframes.f(12, QuirkKeyframes.AIM, 0.25f, 0, QuirkKeyframes.AIM, -0.25f, 0, 0.1f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(16) };
	// v0.17.2 revamp: Redirect -- palms forward, braced; the return -- a palm thrust
	private static final float[][] REDIRECT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, 0.4f, 0, -1.4f, -0.4f, 0, 0.15f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0),
			QuirkKeyframes.f(28, -1.4f, 0.4f, 0, -1.4f, -0.4f, 0, 0.15f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0), QuirkKeyframes.rest(32) };
	private static final float[][] RETURN = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, QuirkKeyframes.AIM, 0, 0, -0.4f, 0, 0, 0.1f, -0.3f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] REFRACT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f),
			QuirkKeyframes.f(10, -0.3f, 0, 1.6f, -0.3f, 0, -1.6f, -0.25f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.2f), QuirkKeyframes.rest(14) };
	// v0.17.2 round 4: a dip into the knees, then up on the light, arms driven down; the landing reaches for the ground and settles
	private static final float[][] TAKEOFF = { QuirkKeyframes.restC(0), QuirkKeyframes.fc(3, 0.55f, 0, 0.3f, 0.55f, 0, -0.3f, 0.3f, 0, -0.1f, 0, 0, -0.1f, 0, 0, 0.15f, 0.7f),
			QuirkKeyframes.fc(7, 0.3f, 0, 0.55f, 0.3f, 0, -0.55f, -0.12f, 0, 0.1f, 0, 0.05f, 0.25f, 0, -0.05f, -0.2f, 0f), QuirkKeyframes.restC(13) };
	private static final float[][] LAND = { QuirkKeyframes.restC(0), QuirkKeyframes.fc(3, -0.1f, 0, 0.7f, -0.1f, 0, -0.7f, 0.05f, 0, -0.15f, 0, 0.08f, 0.1f, 0, -0.08f, 0.1f, 0f),
			QuirkKeyframes.fc(6, 0.35f, 0, 0.5f, 0.35f, 0, -0.5f, 0.3f, 0, 0, 0, 0.05f, 0, 0, -0.05f, 0.1f, 0.65f), QuirkKeyframes.restC(13) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f),
			QuirkKeyframes.f(8, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.7f, 0, 0.5f, 0, 0, 0.5f, 0, 0, -0.6f), QuirkKeyframes.rest(12) };
	// v0.17.2 revamp: Radiant Burst -- curled in, then thrown wide open as the light pours out of him
	private static final float[][] RADIANT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.4f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.2f, 0, 1.9f, -0.2f, 0, -1.9f, -0.35f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.35f),
			QuirkKeyframes.f(12, -0.2f, 0, 1.9f, -0.2f, 0, -1.9f, -0.35f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.35f), QuirkKeyframes.rest(18) };
	private static final float[][] STANCE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f),
			QuirkKeyframes.f(96, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, -0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.25f), QuirkKeyframes.rest(100) };
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(10, -1.5f, -0.2f, 0, -1.5f, 0.2f, 0, 0.05f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] FLARE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0.5f, 0, -0.6f, -0.5f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(5, -2.6f, -0.4f, 0.5f, -2.6f, 0.4f, -0.5f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	// v0.17.2 round 4 (user: "upgrade the vfx and animation on charge release"): he folds round his chest as the light comes in, knees giving
	// under it, then the whole body is thrown open as it bursts out of him: arms flung high, head back, up on his feet
	private static final float[][] RELEASE = { QuirkKeyframes.restC(0),
			QuirkKeyframes.fc(4, -1.3f, 0.75f, 0.1f, -1.3f, -0.75f, -0.1f, 0.42f, 0, -0.14f, 0, 0.08f, 0.1f, 0, -0.08f, 0.3f, 0.55f),
			QuirkKeyframes.fc(ChromastoneKit.RELEASE_WINDUP, -1.5f, 0.95f, 0.05f, -1.5f, -0.95f, -0.05f, 0.58f, 0, -0.16f, 0, 0.1f, 0.12f, 0, -0.1f, 0.42f, 1f),
			QuirkKeyframes.fc(ChromastoneKit.RELEASE_WINDUP + 2, -0.5f, 0, 2.35f, -0.5f, 0, -2.35f, -0.42f, 0, 0.05f, 0, 0.3f, 0.05f, 0, -0.3f, -0.4f, 0f),
			QuirkKeyframes.fc(ChromastoneKit.RELEASE_WINDUP + 11, -0.42f, 0, 2.1f, -0.42f, 0, -2.1f, -0.3f, 0, 0.05f, 0, 0.26f, 0.05f, 0, -0.26f, -0.3f, 0f),
			QuirkKeyframes.restC(ChromastoneKit.RELEASE_WINDUP + 19) };
	// v0.17.2 round 4 (user: "give him more attack moves on G and shift+G please"). The Prism Punch: the right fist drawn back past the hip with
	// the shoulders turned away, the left held out to measure, then the whole body behind one straight right down the aim
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(ChromastoneKit.PUNCH_WINDUP - 1, 0.75f, 0.1f, 0.3f, -1.05f, -0.1f, -0.1f, 0.18f, 0.55f, -0.3f, 0, 0, 0.28f, 0, 0, 0.05f),
			QuirkKeyframes.f(ChromastoneKit.PUNCH_WINDUP + 1, QuirkKeyframes.AIM, -0.1f, 0.05f, 0.5f, 0, -0.2f, 0.38f, -0.6f, 0.42f, 0, 0, -0.42f, 0, 0, -0.1f),
			QuirkKeyframes.f(ChromastoneKit.PUNCH_WINDUP + 6, QuirkKeyframes.AIM, -0.1f, 0.05f, 0.45f, 0, -0.2f, 0.32f, -0.52f, 0.36f, 0, 0, -0.36f, 0, 0, -0.08f),
			QuirkKeyframes.rest(ChromastoneKit.PUNCH_WINDUP + 12) };
	// The Spectrum Sweep: the arm reaches across his body, then swings the beam from left to right on the same curve the server sweeps it,
	// the shoulders carrying it round and the left arm thrown back to balance
	private static final float SWEEP_Y = ChromastoneKit.SWEEP_ARC * 0.5f * Mth.DEG_TO_RAD;
	private static final float[][] SWEEP = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(ChromastoneKit.SWEEP_WINDUP - 2, QuirkKeyframes.AIM, -SWEEP_Y, 0, 0.3f, 0, -0.6f, 0.14f, -0.5f, 0.12f, 0, 0.05f, -0.1f, 0, -0.1f, 0),
			QuirkKeyframes.f(ChromastoneKit.SWEEP_WINDUP, QuirkKeyframes.AIM, -SWEEP_Y, 0, 0.3f, 0, -0.6f, 0.14f, -0.5f, 0.12f, 0, 0.05f, -0.1f, 0, -0.1f, 0),
			QuirkKeyframes.f(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS, QuirkKeyframes.AIM, SWEEP_Y, 0, 0.3f, 0, -0.6f, 0.14f, 0.5f, -0.1f, 0, 0.1f, 0.12f, 0, -0.05f, 0),
			QuirkKeyframes.f(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS + 2, QuirkKeyframes.AIM, SWEEP_Y, 0, 0.3f, 0, -0.6f, 0.14f, 0.5f, -0.1f, 0, 0.1f, 0.12f, 0, -0.05f, 0),
			QuirkKeyframes.rest(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS + 8) };
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, -0.4f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.5f),
			QuirkKeyframes.f(8, 0.4f, 0, 0.3f, 0.4f, 0, -0.3f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.4f), QuirkKeyframes.rest(16) };
	private static final float[][] SPECTRUM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.4f, 0, 1.3f, -0.4f, 0, -1.3f, -0.3f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(14, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.35f, 0, 0.1f, 0, 0.2f, 0.1f, 0, -0.2f, -0.3f),
			QuirkKeyframes.f(17, QuirkKeyframes.AIM, 0.2f, 0, QuirkKeyframes.AIM, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f),
			QuirkKeyframes.f(28, QuirkKeyframes.AIM, 0.2f, 0, QuirkKeyframes.AIM, -0.2f, 0, 0.15f, 0, 0.2f, 0, 0.1f, -0.1f, 0, -0.1f, 0f), QuirkKeyframes.rest(32) };

	// ---------------------------------------------------------------- first-person clips
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -15, 10, 0, 0.05f, 0.15f, -0.3f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PALMS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(12, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GUARD = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(28, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_OUT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, 0, -0.2f, 0.1f),
			FirstPersonClip.f(6, -20, -45, 30, -0.35f, 0.1f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RUN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 40, 15, 0, 0.1f, -0.4f, 0.25f),
			FirstPersonClip.f(10, 40, 15, 0, 0.1f, -0.4f, 0.25f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STANCE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 25, -0.3f, 0.05f, 0.1f),
			FirstPersonClip.f(96, -20, -40, 25, -0.3f, 0.05f, 0.1f), FirstPersonClip.f(100, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, 35, 0, 0.25f, 0, -0.1f),
			FirstPersonClip.f(5, -70, -20, 20, -0.15f, 0.45f, 0.1f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPECTRUM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, -40, 25, -0.3f, 0.05f, 0.1f),
			FirstPersonClip.f(14, -22, -42, 26, -0.31f, 0.06f, 0.1f), FirstPersonClip.f(17, -15, 25, 0, 0.18f, 0.15f, -0.3f),
			FirstPersonClip.f(28, -15, 25, 0, 0.18f, 0.15f, -0.3f), FirstPersonClip.f(32, 0, 0, 0, 0, 0, 0)).both();

	// v0.17.2 round 4
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(ChromastoneKit.PUNCH_WINDUP - 1, 32, -12, 0, -0.08f, -0.22f, 0.22f),
			FirstPersonClip.f(ChromastoneKit.PUNCH_WINDUP + 1, -16, 20, 0, 0.2f, 0.12f, -0.5f), FirstPersonClip.f(ChromastoneKit.PUNCH_WINDUP + 5, -14, 18, 0, 0.18f, 0.1f, -0.42f),
			FirstPersonClip.f(ChromastoneKit.PUNCH_WINDUP + 12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SWEEP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(ChromastoneKit.SWEEP_WINDUP, -16, 34, 0, 0.26f, 0.14f, -0.22f),
			FirstPersonClip.f(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS, -16, 2, 0, 0.04f, 0.14f, -0.22f),
			FirstPersonClip.f(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS + 2, -16, 2, 0, 0.04f, 0.14f, -0.22f),
			FirstPersonClip.f(ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS + 8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RELEASE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -22, 40, 0, 0.28f, -0.02f, 0f),
			FirstPersonClip.f(ChromastoneKit.RELEASE_WINDUP, -26, 46, 0, 0.32f, -0.1f, 0.06f), FirstPersonClip.f(ChromastoneKit.RELEASE_WINDUP + 2, -32, -48, 32, -0.38f, 0.24f, 0.1f),
			FirstPersonClip.f(ChromastoneKit.RELEASE_WINDUP + 11, -28, -44, 30, -0.34f, 0.2f, 0.1f), FirstPersonClip.f(ChromastoneKit.RELEASE_WINDUP + 19, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LAND = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 18, 8, 0, 0.05f, -0.22f, 0.1f),
			FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0)).both();

	public ChromastoneClient() {
		if (!worldPass) {
			worldPass = true;
			WorldRenderEvents.AFTER_ENTITIES.register(ChromastoneClient::renderWorld);
			// v0.17.2 revamp: the Prism Bolt -- a short streak of violet light along its flight
			OmnitrixRenderers.projectileLook("chromastone_bolt", (e, partial, pose, buffers) -> {
				Vec3 v = e.getDeltaMovement();
				Vec3 dir = v.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : v.normalize();
				Vec3 c = new Vec3(0, e.getBbHeight() * 0.5, 0);
				RecalFx.beam(pose, buffers, c.subtract(dir.scale(0.9)), c, 0.18f, WHITE, VIOLET, 1f);
			});
		}
	}

	public static void register() {
		AlienClients.register(Aliens.CHROMASTONE.id(), new ChromastoneClient());
	}

	/** Three pinks off his own skin (the hands): base, mid, tip. */
	private static final float[] PINK_DARK = AlienHorns.pixel(20, 7, 64), PINK = AlienHorns.pixel(12, 7, 64), PINK_LIGHT = AlienHorns.pixel(12, 6, 64);

	/**
	 * v0.17.2 (user: "Give him a pink spike on his head, 2 on his chest where it shows in like the reference and also 2 coming out of
	 * his back, make it the same colour of the pink on his skin model"): crystal spikes in his skin's own pinks -- one up off the
	 * crown, two forward-and-out off the top of the chest by the shoulders, two back-and-out between the shoulder blades.
	 */
	@Override
	public void renderExtras(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			net.minecraft.client.player.AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> model, float partial,
			float age) {
		var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN));
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.head, () -> {
			pose.pushPose();
			pose.translate(0f, -8f / 16f, -1f / 16f);
			AlienHorns.spike(pose, vc, light, 4.2f, 1.8f, -12f, 0f, PINK_DARK, PINK, PINK_LIGHT);
			pose.popPose();
		});
		com.projecthero.mod.client.omnitrix.AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				// the chest crystals: short (2 px), straight out the front, a touch out to the side
				pose.translate(side * 2.4f / 16f, 1.6f / 16f, -2.2f / 16f);
				AlienHorns.spikeAlong(pose, vc, light, 2.2f, 1.3f, side * 0.25f, 0f, -1f, PINK_DARK, PINK, PINK_LIGHT);
				pose.popPose();
				pose.pushPose();
				// the back crystals: thin and long, straight out behind like Diamondhead's, angled a little up and out
				pose.translate(side * 2.0f / 16f, 2.4f / 16f, 1.9f / 16f);
				AlienHorns.spikeAlong(pose, vc, light, 5.5f, 1.1f, side * 0.35f, -0.3f, 1f, PINK_DARK, PINK, PINK_LIGHT);
				pose.popPose();
			}
		});
	}

	/** v0.17.2 (user: "raise our omnitrix badge slightly"): a pixel higher than the default, where the skin's own symbol was. */
	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.05f, 3.2f };
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int f = Omnitrix.state(p).kitFlags;
		// v0.17.2 round 4: the flight is the base every clip blends on and off, so nothing snaps at either end
		boolean air = (f & ChromastoneKit.F_FLYING) != 0 && !p.onGround() || ChromastoneFlightPose.bodyTilt(p, age - Mth.floor(age)) != null;
		if (air) {
			ChromastoneFlightPose.limbs(p, m, age);
		}
		float[][] clip = clip(animId, f, ticks);
		return clip != null && play(m, clip, ticks) || air;
	}

	/** Plays a keyframe table, its {@link QuirkKeyframes#AIM} arm columns pointing down the look. */
	private static boolean play(HumanoidModel<?> m, float[][] clip, float ticks) {
		// (the crouching tables -- 17 columns -- have no aimed arms: sampled whole so the crouch column is kept)
		float[] s = clip[0].length > QuirkKeyframes.COLS ? QuirkKeyframes.sample(clip, ticks) : QuirkKeyframes.sample(clip, ticks, -Mth.HALF_PI + m.head.xRot, m.head.yRot);
		if (s == null) {
			return false;
		}
		QuirkKeyframes.apply(m, s, QuirkKeyframes.weight(ticks, QuirkKeyframes.length(clip)));
		return true;
	}

	private static float[][] clip(int animId, int f, float ticks) {
		return switch (animId) {
			case ChromastoneKit.ANIM_BOLT -> BOLT;
			case ChromastoneKit.ANIM_PRISM -> PRISM;
			case ChromastoneKit.ANIM_REDIRECT -> (f & ChromastoneKit.F_REDIRECT) != 0 || ticks < 4 ? REDIRECT : null;
			case ChromastoneKit.ANIM_RETURN -> RETURN;
			case ChromastoneKit.ANIM_REFRACT -> REFRACT;
			case ChromastoneKit.ANIM_TAKEOFF -> TAKEOFF;
			case ChromastoneKit.ANIM_LAND -> LAND;
			case ChromastoneKit.ANIM_DASH -> DASH;
			case ChromastoneKit.ANIM_RADIANT -> RADIANT;
			case ChromastoneKit.ANIM_STANCE -> (f & ChromastoneKit.F_STANCE) != 0 || ticks < 6 ? STANCE : null;
			case ChromastoneKit.ANIM_SHIELD -> SHIELD;
			case ChromastoneKit.ANIM_FLARE -> FLARE;
			case ChromastoneKit.ANIM_RELEASE -> RELEASE;
			case ChromastoneKit.ANIM_OVERLOAD -> OVERLOAD;
			case ChromastoneKit.ANIM_SPECTRUM -> SPECTRUM;
			case ChromastoneKit.ANIM_PUNCH -> PUNCH;
			case ChromastoneKit.ANIM_SWEEP -> SWEEP;
			default -> null;
		};
	}

	/** v0.17.2 round 4: the flight's state, and a few motes shed off the light under him. */
	@Override
	public void tick(AbstractClientPlayer p) {
		OmnitrixState st = Omnitrix.state(p);
		boolean flying = (st.kitFlags & ChromastoneKit.F_FLYING) != 0 && !p.onGround();
		float ticks = p.level().getGameTime() - st.animStart;
		float[][] c = clip(st.animId, st.kitFlags, ticks);
		boolean attacking = c != null && c != TAKEOFF && c != LAND && c != DASH && ticks <= QuirkKeyframes.length(c);
		ChromastoneFlightPose.tick(p, flying, attacking);
		var r = p.getRandom();
		if (flying && r.nextInt(5) == 0) {
			p.level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5) * 0.7, p.getY() - 0.1, p.getZ() + (r.nextDouble() - 0.5) * 0.7, 0,
					-0.04, 0);
		}
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case ChromastoneKit.ANIM_BOLT, ChromastoneKit.ANIM_RETURN -> FP_POINT;
			case ChromastoneKit.ANIM_PRISM, ChromastoneKit.ANIM_SHIELD -> FP_PALMS;
			case ChromastoneKit.ANIM_REDIRECT -> FP_GUARD;
			case ChromastoneKit.ANIM_REFRACT, ChromastoneKit.ANIM_RADIANT, ChromastoneKit.ANIM_OVERLOAD -> FP_OUT;
			case ChromastoneKit.ANIM_DASH, ChromastoneKit.ANIM_TAKEOFF -> FP_RUN;
			case ChromastoneKit.ANIM_LAND -> FP_LAND;
			case ChromastoneKit.ANIM_PUNCH -> FP_PUNCH;
			case ChromastoneKit.ANIM_SWEEP -> FP_SWEEP;
			case ChromastoneKit.ANIM_RELEASE -> FP_RELEASE;
			case ChromastoneKit.ANIM_STANCE -> FP_STANCE;
			case ChromastoneKit.ANIM_FLARE -> FP_FLASH;
			case ChromastoneKit.ANIM_SPECTRUM -> FP_SPECTRUM;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: the Charge meter

	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = ChromastoneKit.charge(p);
		int f = Omnitrix.state(p).kitFlags;
		boolean stance = (f & ChromastoneKit.F_STANCE) != 0;
		boolean over = (f & ChromastoneKit.F_OVER) != 0;
		boolean full = c >= ChromastoneKit.SPECTRUM_MIN;
		int col = over ? ((p.tickCount / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFFFF60C0) : stance ? 0xFFF0C0FF : full ? 0xFFE080FF : METER;
		OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable(over ? "hud.projecthero.omnitrix.chromastone.overload" : "hud.projecthero.omnitrix.chromastone.charge"),
				c / (float) ChromastoneKit.MAX_CHARGE, col, String.valueOf(c), full ? 0xFFF4D0FF : 0xFFE2E2E2);
	}

	@Override
	public int hudHeight() {
		return 10;
	}

	// ---------------------------------------------------------------- the world pass

	/**
	 * v0.17.2 round 4, the Charge Release: seven motes of the spectrum wind into his chest and a ring closes on him, then the light bursts
	 * out: a white core, rays of every colour thrown up and out round him and seven rings of the spectrum racing over the ground. In his own
	 * view the core and the rays stay out of the lens: only the rings on the ground and thin motes low in front of him.
	 */
	private static void release(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 eye, Vec3 f, Vec3 right, Vec3 feet, float t, float now, float sc, boolean fp) {
		float wind = ChromastoneKit.RELEASE_WINDUP;
		Vec3 heart = fp ? eye.add(f.scale(1.5)).add(0, -0.55, 0) : feet.add(0, 1.0 * sc, 0);
		Vec3 up = new Vec3(0, 1, 0);
		if (t < wind) {
			float g = t / wind;
			g *= g;
			for (int i = 0; i < 7; i++) {
				double a = now * 0.45 + i * Math.PI * 2 / 7;
				double d = (fp ? 1.1 : 2.1) * (1 - g) + 0.12;
				Vec3 at = heart.add(right.scale(Math.cos(a) * d)).add(f.cross(up).lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat(f).scale(Math.sin(a) * d)).add(0, Math.sin(a * 2) * d * 0.3, 0);
				PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), at.subtract(cam), fp ? 0.09f : 0.2f, PyroDraw.dim(RecalFx.SPECTRUM[i], 0.55f + g * 0.45f));
			}
			if (!fp) {
				RecalFx.ring(pose, buffers, feet.add(0, 0.1, 0).subtract(cam), (float) ChromastoneKit.RELEASE_RADIUS * (1f - g) + 0.6f, 0.5f, 0xC860FF, 0.25f + g * 0.5f);
			}
			PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), heart.subtract(cam), (fp ? 0.1f : 0.25f) + g * (fp ? 0.14f : 0.6f), PyroDraw.dim(0xFFFFFFFF, 0.4f + g * 0.6f));
			return;
		}
		float u = t - wind;
		if (u > 20f) {
			return;
		}
		float ease = 1f - (1f - Math.min(1f, u / 11f)) * (1f - Math.min(1f, u / 11f)) * (1f - Math.min(1f, u / 11f));
		float fade = Math.min(1f, u / 1.5f) * Mth.clamp(1f - u / 17f, 0f, 1f);
		fade *= fade < 1f ? Math.sqrt(fade) : 1f;
		float reach = (float) ChromastoneKit.RELEASE_RADIUS * 1.05f;
		// the rings: red leads, violet follows, each a thin band with a soft inner edge
		pose.pushPose();
		Vec3 g = feet.add(0, 0.12, 0).subtract(cam);
		pose.translate(g.x, g.y, g.z);
		for (int i = 0; i < 7; i++) {
			float r = reach * ease * (1f - i * 0.075f);
			int col = RecalFx.SPECTRUM[i];
			PyroDraw.annulus(pose, PyroDraw.additive(buffers), Math.max(0f, r - 0.4f), r, PyroDraw.dim(col, 0f), PyroDraw.dim(col, fade * 0.85f), i * 0.012f);
		}
		pose.popPose();
		if (fp) {
			return;
		}
		// the core and the rays
		Vec3 core = feet.add(0, 1.15 * sc, 0);
		PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), core.subtract(cam), 1.8f * RecalFx.fade(u, 7f) + 0.3f, PyroDraw.dim(0xFFFFFFFF, RecalFx.fade(u, 9f)));
		pose.pushPose();
		Vec3 c = core.subtract(cam);
		pose.translate(c.x, c.y, c.z);
		PyroDraw.sphere(pose, PyroDraw.additive(buffers), reach * 0.75f * ease, PyroDraw.dim(0xFFFFF0FF, fade * 0.12f), PyroDraw.dim(0xFFC860FF, fade * 0.2f), 0.04f, u,
				LightTexture.FULL_BRIGHT);
		pose.popPose();
		for (int i = 0; i < 14; i++) {
			// two crowns of rays, one low and wide, one thrown high
			double a = i * Math.PI * 2 / 7 + (i >= 7 ? 0.45 : 0);
			double rise = i >= 7 ? 0.95 : 0.3;
			Vec3 dir = new Vec3(Math.cos(a), rise, Math.sin(a)).normalize();
			// each ray leaves him and flies off: its tail lets go of him, so the burst opens instead of standing there as spikes
			float head = (i >= 7 ? 5.2f : 6.4f) * ease;
			float tail = Math.max(0f, head - 2.6f) + Mth.clamp((u - 4f) / 9f, 0f, 1f) * 2.2f;
			float k = Mth.clamp(1f - u / 12f, 0f, 1f);
			int col = RecalFx.SPECTRUM[i % 7];
			Vec3 at = core.subtract(cam);
			RecalFx.beam(pose, buffers, at.add(dir.scale(0.3 + tail)), at.add(dir.scale(0.3 + Math.max(head, tail + 0.2f))), 0.17f, PyroDraw.lerpRgb(0xFFFFFFFF, col, 0.55f), col, k);
		}
	}

	private static Vec3 flat(Vec3 f) {
		Vec3 h = new Vec3(f.x, 0, f.z);
		return h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
	}

	/**
	 * v0.17.2 round 4, the Spectrum Sweep: the light gathers in his right hand, then the rainbow beam leaves it and swings across 120 degrees
	 * on the server's own curve ({@link ChromastoneKit#sweepYaw}), flaring where it strikes.
	 */
	private static void sweep(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player pl, Vec3 eye, Vec3 f, Vec3 right, Vec3 feet, float t, float now, float sc, boolean fp, float partial) {
		float end = ChromastoneKit.SWEEP_WINDUP + ChromastoneKit.SWEEP_TICKS;
		Vec3 dir = RecalKitMath.turned(f, ChromastoneKit.sweepYaw(t));
		// in his own view it leaves the fist low in front of him and the far end does the swinging, so the beam stays joined to his hand
		Vec3 hand = fp ? eye.add(f.scale(1.1)).add(right.scale(0.28)).add(0, -0.33, 0)
				: ChromastoneFlightPose.tilted(pl, partial, feet.add(0, 1.36 * sc, 0).add(right.scale(0.36 * sc)).add(dir.scale(0.78 * sc)));
		if (t < ChromastoneKit.SWEEP_WINDUP) {
			float g = t / ChromastoneKit.SWEEP_WINDUP;
			PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), hand.subtract(cam), (fp ? 0.08f : 0.15f) + g * (fp ? 0.14f : 0.3f), PyroDraw.dim(0xFFFFFFFF, g));
			return;
		}
		float k = Mth.clamp(end + 2f - t, 0f, 1f) * Mth.clamp((t - ChromastoneKit.SWEEP_WINDUP) * 2f + 0.3f, 0f, 1f);
		if (k <= 0f) {
			return;
		}
		Vec3 hit = RecalFx.traceEnd(pl, eye, dir, ChromastoneKit.SWEEP_RANGE);
		spectrumBand(pose, buffers, hand.subtract(cam), hit.subtract(cam), fp ? 0.035f : 0.075f, k);
		PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), hit.subtract(cam), 0.9f, PyroDraw.dim(0xFFF0D8FF, k * 0.8f));
		PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), hand.subtract(cam), fp ? 0.14f : 0.32f, PyroDraw.dim(0xFFFFFFFF, k));
	}

	/**
	 * Seven strips of colour side by side, always turned to the viewer, with a thread of white down the middle: the colours stay
	 * apart where {@link RecalFx#rainbow}'s fan melts to white when it is seen close to. {@code step} is one strip's width.
	 */
	private static void spectrumBand(PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, float step, float k) {
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(a.add(b).scale(0.5));
		side = side.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : side.normalize();
		var vc = PyroDraw.additive(buffers);
		for (int i = 0; i < 7; i++) {
			int col = RecalFx.SPECTRUM[i];
			Vec3 off = side.scale((i - 3) * step);
			// a point at the hand, opening to its full width a block or two out
			PyroDraw.ribbon(pose, vc, java.util.List.of(a, a.add(d.scale(0.1)).add(off.scale(0.85)), a.add(d.scale(0.25)).add(off), b.add(off)), s -> step * 1.15, s -> PyroDraw.dim(col, k * 0.9f));
		}
		PyroDraw.ribbon(pose, vc, java.util.List.of(a, b), s -> step * 0.7, s -> PyroDraw.dim(0xFFFFFFFF, k * 0.8f));
	}

	private static void renderWorld(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		for (Player pl : mc.level.players()) {
			if (!Omnitrix.hasPower(pl)) {
				continue;
			}
			OmnitrixState st = Omnitrix.state(pl);
			if (!ChromastoneKit.ID.equals(st.active) || !st.transformed()) {
				continue;
			}
			boolean fp = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			float now = pl.level().getGameTime() + partial;
			float t = now - st.animStart;
			float sc = pl.getScale();
			Vec3 f = pl.getViewVector(partial);
			Vec3 right = f.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 eye = pl.getEyePosition(partial);
			Vec3 feet = pl.getPosition(partial);
			Vec3 chest = fp ? eye.add(f.scale(0.7)).add(0, -0.35, 0) : feet.add(0, 1.15 * sc, 0).add(f.scale(0.3 * sc));
			if (pl instanceof AbstractClientPlayer acp) {
				ChromastoneFlightPose.render(pose, buffers, cam, acp, partial, now, fp);
			}
			switch (st.animId) {
				case ChromastoneKit.ANIM_RELEASE -> release(pose, buffers, cam, eye, f, right, feet, t, now, sc, fp);
				case ChromastoneKit.ANIM_SWEEP -> sweep(pose, buffers, cam, pl, eye, f, right, feet, t, now, sc, fp, partial);
				case ChromastoneKit.ANIM_PUNCH -> {
					// the light gathered round the fist on the way in, gone when it lands
					if (t < ChromastoneKit.PUNCH_WINDUP + 1 && !fp) {
						Vec3 fist = ChromastoneFlightPose.tilted(pl, partial, BrainstormFx.hand(pl, partial));
						float g = Mth.clamp(t / ChromastoneKit.PUNCH_WINDUP, 0f, 1f);
						PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), fist.subtract(cam), 0.2f + g * 0.3f, PyroDraw.dim(0xFFE8B8FF, g));
					}
				}
				case ChromastoneKit.ANIM_PRISM -> {
					Vec3 end = RecalFx.traceEnd(pl, eye, f, 28.0);
					RecalFx.rainbow(pose, buffers, chest.subtract(cam), end.subtract(cam), 0.35f, 0.18f, RecalFx.fade(t, 12f), now);
				}
				case ChromastoneKit.ANIM_REFRACT, ChromastoneKit.ANIM_RADIANT, ChromastoneKit.ANIM_RETURN -> {
					PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 0.7f, PyroDraw.dim(0xFFFFFFFF, RecalFx.fade(t, 10f)));
				}
				case ChromastoneKit.ANIM_FLARE -> {
					float k = RecalFx.fade(t - 4f, 8f);
					PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), eye.subtract(cam), 1f + Math.max(0f, t - 4f) * 1.2f, PyroDraw.dim(0xFFB890FF, k));
				}
				case ChromastoneKit.ANIM_SPECTRUM -> {
					if (t < ChromastoneKit.SPECTRUM_WINDUP) {
						// the colours gather into his chest
						float g = t / ChromastoneKit.SPECTRUM_WINDUP;
						for (int i = 0; i < 7; i++) {
							double a = now * 0.3 + i * Math.PI * 2 / 7;
							double d = 1.6 * (1 - g) + 0.2;
							Vec3 at = chest.add(right.scale(Math.cos(a) * d)).add(0, Math.sin(a) * d, 0);
							PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), at.subtract(cam), 0.25f, PyroDraw.dim(RecalFx.SPECTRUM[i], 0.6f + g * 0.4f));
						}
						PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), chest.subtract(cam), 0.3f + g * 0.6f, PyroDraw.dim(0xFFFFFFFF, g));
					} else {
						float k = RecalFx.fade(t - ChromastoneKit.SPECTRUM_WINDUP, 13f);
						Vec3 end = RecalFx.traceEnd(pl, eye, f, ChromastoneKit.SPECTRUM_RANGE);
						RecalFx.rainbow(pose, buffers, chest.subtract(cam), end.subtract(cam), 1.3f, 0.65f, k, now);
					}
				}
				default -> {
				}
			}
			int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(eye));
			if ((st.kitFlags & ChromastoneKit.F_STANCE) != 0 && !fp) {
				// the absorb stance: a faceted crystal shell, pulsing
				int a = 0x50 + (int) (0x20 * Mth.sin(now * 0.3f));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.18f, (a << 24) | 0xD890FF, light);
			}
			if ((st.kitFlags & ChromastoneKit.F_REDIRECT) != 0 && !fp) {
				int a = 0x40 + (int) (0x30 * Mth.sin(now * 0.8f));
				RecalFx.shell(pose, buffers, cam, pl.getBoundingBox().move(feet.subtract(pl.position())), 0.1f, (a << 24) | 0xFFFFFF, light);
			}
			if ((st.kitFlags & ChromastoneKit.F_SHIELD) != 0) {
				// the Prism Shield: a hexagon of hard light standing in front of him, edges cycling through the spectrum
				Vec3 flat = new Vec3(f.x, 0, f.z);
				flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
				Vec3 side = flat.cross(new Vec3(0, 1, 0)).normalize();
				Vec3 c = feet.add(0, 1.0 * sc, 0).add(flat.scale(fp ? 1.4 : 1.0 * sc));
				float r = 1.25f * sc;
				for (int i = 0; i < 6; i++) {
					double a0 = i * Math.PI / 3 + Math.PI / 6, a1 = (i + 1) * Math.PI / 3 + Math.PI / 6;
					Vec3 p0 = c.add(side.scale(Math.cos(a0) * r)).add(0, Math.sin(a0) * r, 0);
					Vec3 p1 = c.add(side.scale(Math.cos(a1) * r)).add(0, Math.sin(a1) * r, 0);
					int col = RecalFx.SPECTRUM[(i + (int) (now * 0.2f)) % RecalFx.SPECTRUM.length];
					RecalFx.beam(pose, buffers, p0.subtract(cam), p1.subtract(cam), 0.12f, WHITE, col, 0.85f);
				}
				PyroDraw.worldGlow(pose, PyroDraw.glowBuffer(buffers), c.subtract(cam), r * 1.1f, PyroDraw.dim(0xFFE0C8FF, 0.35f));
			}
		}
	}
}

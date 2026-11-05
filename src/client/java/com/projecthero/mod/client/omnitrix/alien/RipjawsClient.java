package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Ripjaws' client kit -- the grey-blue fish-man skin (pale belly, gills, dark trunks) (v0.17.1: the huge jaw is gone, user's skin face instead; it used to be an
 * upper snout and a hinged lower jaw, both lined with teeth, that gape and snap on every bite) (v0.17.2: no head or
 * forearm fins), and a FISH TAIL (v0.17.2: its own ripjaws_tail.png sheet, black with lime bands): a short finned tail behind him on land; in water the legs vanish into a long undulating
 * tail. The hydration meter over the keys turns orange, then red, as he dries out. A pose for every move.
 *
 * <p>v0.17.2 revamp (user: "Draw jaws as an effect"): every bite draws the JAWS in the world pass -- two toothed crescents (upper
 * and lower) that open in front of his face and snap shut; Megamouth's are the same jaws 4 blocks wide, gaping over the 0.75 s
 * wind-up. Drag Under keeps them clenched on the catch. Seen in first person too. Also: the Lure Light (a stalk with a glowing
 * bulb on his head), and the Blood Scent / Blood in the Water outlines (his screen only).
 */
public final class RipjawsClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] BUBBLE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.9f, -0.5f, 0f, -1.9f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f),
			QuirkKeyframes.f(7, -1.2f, 0f, 0.9f, -1.2f, 0f, -0.9f, -0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.2f), QuirkKeyframes.rest(14) };
	private static final FirstPersonClip FP_BUBBLE = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -65f, 30f, 0f, 0.1f, 0.35f, 0.1f),
			FirstPersonClip.f(7, -30f, -30f, 20f, -0.2f, 0.15f, 0f), FirstPersonClip.f(14, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ripjaws.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/ripjaws_extras.png");

	private static final float[] FIN = { 24, 0, 40, 16 };

	private static final float[][] BITE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.3f, -0.4f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.rest(10) };
	private static final float[][] LUNGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.55f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.5f),
			QuirkKeyframes.f(10, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.55f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.5f), QuirkKeyframes.rest(14) };
	// v0.17.2 revamp: Drag Under in water -- clamped on, arms gripping it, leaning into the swim
	private static final float[][] DRAG = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.3f, 0, -1.5f, 0.3f, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(60, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(64) };
	// v0.17.2 revamp: the land Drag Under's throw
	private static final float[][] FLING = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0, 0.3f, -1.6f, 0, -0.3f, 0.3f, 0.6f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -2.6f, 0, 0.6f, -2.6f, 0, -0.6f, -0.2f, -0.6f, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(12) };
	// v0.17.2 revamp: Tail Sweep -- the whole body spins round behind the tail
	private static final float[][] SWEEP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0, 2.0f, 0, 0, 0.3f, 0, 0, -0.3f, 0),
			QuirkKeyframes.f(8, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0, -2.4f, 0, 0, 0.3f, 0, 0, -0.3f, 0), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.4f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.3f),
			QuirkKeyframes.f(12, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.4f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] BREACH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, -0.1f, 0, 0.2f, 0, 0, 0.2f, 0, 0, -0.3f),
			QuirkKeyframes.f(16, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, -0.1f, 0, 0.2f, 0, 0, 0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(22) };
	private static final float[][] WHIRL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, 0.6f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(10, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, -0.6f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.f(17, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, 0.6f, 0, 0, 0, 0,
					0, 0, 0), QuirkKeyframes.rest(22) };
	// v0.17.2 revamp: Gnash -- a fast head-snap loop, five bites in a second
	private static final float[][] GNASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(4, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.35f, 0.15f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(8, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.35f, -0.15f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(10, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(12, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.35f, 0.15f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(14, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, -0.35f),
			QuirkKeyframes.f(16, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.35f, -0.15f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(18, -0.5f, 0, 0.3f, -0.5f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, -0.35f), QuirkKeyframes.rest(22) };
	// v0.17.2 revamp: Megamouth -- head thrown back and arms flung wide as the jaw unhinges, then the lunge into the chomp
	private static final float[][] MEGA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(6, -0.6f, 0, 1.5f, -0.6f, 0, -1.5f, -0.25f, 0, 0, 0, 0, 0, 0, 0, -0.8f),
			QuirkKeyframes.f(15, -0.7f, 0, 1.6f, -0.7f, 0, -1.6f, -0.3f, 0, 0, 0, 0, 0, 0, 0, -0.9f),
			QuirkKeyframes.f(17, -1.4f, 0, 0.4f, -1.4f, 0, -0.4f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.4f),
			QuirkKeyframes.f(22, -1.4f, 0, 0.4f, -1.4f, 0, -0.4f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.4f), QuirkKeyframes.rest(28) };
	private static final float[][] FRENZY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, -0.5f, 0, -1.3f, 0.5f, 0, 0.35f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.2f),
			QuirkKeyframes.f(12, -1.4f, -0.6f, 0, -1.4f, 0.6f, 0, 0.35f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.2f), QuirkKeyframes.rest(18) };
	private static final float[][] DRENCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.4f, -2.9f, 0, -0.4f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.rest(14) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] DIG = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.9f, 0, 0, -0.3f, 0, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.rest(6) };
	private static final float[][] GASP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.9f, -0.6f, 0, 0.1f, 0, -0.1f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.rest(16) };
	// v0.17.2 revamp: the Dive Strike -- straight down, arms forward over his head, the tail (legs) up behind
	private static final float[][] DIVE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.1f, -2.9f, 0, -0.1f, 0.45f, 0, 0.5f, 0, 0, 0.5f, 0, 0, 0.5f),
			QuirkKeyframes.f(60, -2.9f, 0, 0.1f, -2.9f, 0, -0.1f, 0.45f, 0, 0.5f, 0, 0, 0.5f, 0, 0, 0.5f), QuirkKeyframes.rest(64) };
	private static final float[][] DIVE_HIT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0, 0.4f, -1.6f, 0, -0.4f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.rest(10) };
	// v0.17.2 revamp: Shift+V -- a hand up to the lure as it sprouts
	private static final float[][] LURE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.7f, -0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.rest(12) };

	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 25, 0, 0.15f, 0.1f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BITE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, -0.05f, -0.2f, 0.05f),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BACK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 30, 10, 0, 0.05f, -0.35f, 0.2f),
			FirstPersonClip.f(12, 30, 10, 0, 0.05f, -0.35f, 0.2f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -35, -35, 25, -0.2f, 0.2f, 0),
			FirstPersonClip.f(8, -35, 40, 0, 0.3f, 0.2f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, -10, 15, -0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(14, -70, -10, 15, -0.05f, 0.5f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GRAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 30, 0, 0.2f, 0.15f, -0.1f),
			FirstPersonClip.f(12, -32, 32, 0, 0.2f, 0.15f, -0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 15, 0, 0.1f, -0.15f, -0.15f),
			FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));

	// v0.17.2 revamp: the rendered jaws' colours -- his grey-blue gums, bone-white teeth, the dark throat line
	private static final int GUM = 0xFF4E6470;
	private static final int GUM_EDGE = 0xFF7A2C30;
	private static final int TOOTH = 0xFFF2EEDC;
	/** The Blood Scent outline (Feeding Frenzy, under half health) and the fainter Blood in the Water one. */
	public static final int SCENT_RED = 0xFF2A2A;
	public static final int WATER_SCENT = 0xD08A8A;

	private static boolean worldPassRegistered;

	public RipjawsClient() {
		// v0.17.2 revamp: the jaws (and the lure's glow) draw in the world pass, so they show in first person too
		if (!worldPassRegistered) {
			worldPassRegistered = true;
			WorldRenderEvents.AFTER_ENTITIES.register(RipjawsClient::renderWorld);
		}
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/**
	 * v0.17.2 (user: "fix ripjaws tail please, the textures arent working properly"): the tail has its own 64x32 sheet
	 * (ripjaws_tail.png; v0.17.2 user: "look like the green and black tail on his skin model": black with lime bands, the leg tail's colours) instead of borrowing the skin's striped leg (whose yellow bands and white blocks
	 * never matched his body). Layout, u in px: 0-8 the dorsal face (banded,
	 * continuing the one down his back), 8-16 a flank (col 0 = the dorsal edge, col 7 = the belly edge), 16-24 the darker
	 * underside with its ventral plates, 24-32 x 0-8 an end cap (row 0 = dorsal); 32-64 x 0-16 the fluke's upper face, 32-64 x
	 * 16-32 its underside.
	 */
	private static final ResourceLocation TAIL = ProjectHeroMod.id("textures/entity/omnitrix/ripjaws_tail.png");

	/**
	 * A tail segment running {@code len} px down +y, the dorsal side on +z (the side of his back) and the belly on -z.
	 * {@code v0..v1} (0..1) is the stretch of the tail it shows, so the scale rows run on from segment to segment. Each flank is
	 * mapped dorsal-edge to belly-edge (the +x one mirrored), the end caps dorsal row to the +z edge. The buffer is fetched for
	 * every cube: asking a buffer source for another render type ends the previous one.
	 */
	private static void tailSeg(PoseStack pose, MultiBufferSource buffers, float hx, float hz, float len, float v0, float v1, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TAIL));
		PoseStack.Pose last = pose.last();
		float x0 = -hx / 16f, x1 = hx / 16f, z0 = -hz / 16f, z1 = hz / 16f, y0 = 0f, y1 = len / 16f;
		float t = v0, b = v1; // the face regions run the sheet's full 32 rows
		float px = 1f / 64f;
		int c = 0xFFFFFFFF;
		// belly (-z): u 16-24
		AlienParts.quad(vc, last, c, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, 16 * px, t, 24 * px, b);
		// dorsal (+z): u 0-8, the green stripe in the middle
		AlienParts.quad(vc, last, c, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, 0f, t, 8 * px, b);
		// -x flank: a runs at z1 (dorsal) -> b at z0 (belly), so u 8 -> 16
		AlienParts.quad(vc, last, c, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, 8 * px, t, 16 * px, b);
		// +x flank: a runs at z0 (belly) -> b at z1 (dorsal), so u 16 -> 8 (mirrored)
		AlienParts.quad(vc, last, c, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, 16 * px, t, 8 * px, b);
		// the top end: a, b at z1 (dorsal) -> c, d at z0, so the cap's row 0 (the stripe) sits on the dorsal edge
		AlienParts.quad(vc, last, c, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, 24 * px, 0f, 32 * px, 8f / 32f);
		// the bottom end: a, b at z0 (belly) -> c, d at z1, so the cap is flipped (row 0 to z1)
		AlienParts.quad(vc, last, c, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, 24 * px, 8f / 32f, 32 * px, 0f);
	}

	/**
	 * The tail fluke: a flat, swept crescent (a dolphin's, it beats up and down with the tail) {@code 2 x halfW} px wide and
	 * {@code len} px long down +y, in the x-y plane: the upper face (dorsal, +z, with the green stripe running on into it) and the
	 * underside (-z, darker) as two cut-out sheets half a pixel apart.
	 */
	private static void fluke(PoseStack pose, MultiBufferSource buffers, float halfW, float len, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TAIL));
		PoseStack.Pose last = pose.last();
		float x0 = -halfW / 16f, x1 = halfW / 16f, y1 = len / 16f, h = 0.25f / 16f;
		int c = 0xFFFFFFFF;
		AlienParts.quad(vc, last, c, light, x1, 0f, h, x0, 0f, h, x0, y1, h, x1, y1, h, 0, 0, 1, 0.5f, 0f, 1f, 0.5f);
		AlienParts.quad(vc, last, c, light, x0, 0f, -h, x1, 0f, -h, x1, y1, -h, x0, y1, -h, 0, 0, -1, 0.5f, 0.5f, 1f, 1f);
	}

	private static boolean wet(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & RipjawsKit.F_WET) != 0 || p.isInWater();
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		boolean wet = wet(p);
		if ((s.kitFlags & RipjawsKit.F_BUBBLE) != 0) {
			// v0.17.1: the Water Bubble -- a wobbling globe of water round his head
			float w = 1f + Mth.sin(age * 0.3f) * 0.03f;
			AlienParts.on(pose, model.head, () -> {
				pose.pushPose();
				pose.translate(0, -4.5f / 16f, -1.5f / 16f);
				pose.scale(w, 2f - w, w);
				AlienParts.box(pose, buffers.getBuffer(RenderType.entityTranslucent(EXTRAS)), -6.5f, -6.5f, -7.5f, 6.5f, 6.5f, 5.5f, FIN[0] / 64f, FIN[1] / 64f,
						FIN[2] / 64f, FIN[3] / 64f, 0x5570B8FF, light);
				pose.popPose();
			});
		}
		if ((s.kitFlags & RipjawsKit.F_LURE) != 0) {
			// v0.17.2 revamp (user: "Do lure light i suppose"): the lure -- a stalk from his brow arching forward, a glowing bulb
			// at the tip, gently bobbing
			float bob = Mth.sin(age * 0.15f) * 0.12f;
			AlienParts.on(pose, model.head, () -> {
				pose.pushPose();
				pose.translate(0, -8f / 16f, -2.5f / 16f);
				pose.mulPose(Axis.XP.rotation(0.25f + bob * 0.5f));
				AlienParts.box(pose, PyroDraw.solidBuffer(buffers), -0.4f, -4f, -0.4f, 0.4f, 0f, 0.4f, 0f, 0f, 1f, 1f, 0xFF5E7A86, light);
				pose.translate(0, -4f / 16f, 0);
				pose.mulPose(Axis.XP.rotation(1.0f + bob));
				AlienParts.box(pose, PyroDraw.solidBuffer(buffers), -0.35f, -3.5f, -0.35f, 0.35f, 0f, 0.35f, 0f, 0f, 1f, 1f, 0xFF5E7A86, light);
				pose.translate(0, -3.5f / 16f, 0);
				float pulse = 0.85f + 0.15f * Mth.sin(age * 0.25f);
				AlienParts.box(pose, PyroDraw.additive(buffers), -1.1f, -2.2f, -1.1f, 1.1f, 0f, 1.1f, 0f, 0f, 1f, 1f, PyroDraw.dim(0xFFFFF2A0, pulse),
						LightTexture.FULL_BRIGHT);
				pose.popPose();
			});
		}
		// v0.17.1 (user: "remove the huge jaws from ripjaws"): no snout, lower jaw, teeth or snout eyes any more -- the user's skin
		// has his face (v0.17.2: the jaws are a rendered effect now, renderWorld)
		// v0.17.2 (user: "remove the fins on ripjaws arms and his head"): no dorsal fin, no forearm fins
		// the tail: a short one on land; in water the legs become a long undulating one -- v0.17.2 (user: "fix ripjaws tail please,
		// the textures arent working properly"): every segment and the fluke wear the tail sheet (black with lime bands now, was the
		// green dorsal stripe running on down the tail, a darker underside), not the skin's striped leg
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			if (wet) {
				pose.translate(0, 11.5f / 16f, 0);
				float[][] seg = { { 3.6f, 2.4f, 5f }, { 3.0f, 2.0f, 5f }, { 2.4f, 1.6f, 4.5f }, { 1.6f, 1.2f, 4f } };
				float total = 18.5f, run = 0f;
				for (int i = 0; i < seg.length; i++) {
					float[] g = seg[i];
					pose.mulPose(Axis.XP.rotation(Mth.sin(age * 0.35f - i * 0.9f) * 0.22f));
					tailSeg(pose, buffers, g[0], g[1], g[2], run / total, (run + g[2]) / total, light);
					run += g[2];
					pose.translate(0, (g[2] - 0.4f) / 16f, 0);
				}
				pose.mulPose(Axis.XP.rotation(Mth.sin(age * 0.35f - 4 * 0.9f) * 0.3f));
				fluke(pose, buffers, 7f, 7f, light);
			} else {
				pose.translate(0, 10.5f / 16f, 1.8f / 16f);
				pose.mulPose(Axis.XP.rotation(-0.9f));
				pose.mulPose(Axis.YP.rotation(Mth.sin(age * 0.12f) * 0.15f));
				// the segments run down +y, so turn +y onto +z (out behind him); that turns the dorsal (+z) face up, so the green stripe
				// runs along the top -- v0.17.2 (user: "fix ripjaws tail please, the textures arent working properly"): the tail sheet
				pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
				tailSeg(pose, buffers, 1.8f, 1.6f, 4.5f, 0f, 0.56f, light);
				pose.translate(0, 4.2f / 16f, 0);
				pose.mulPose(Axis.XP.rotation(0.6f));
				tailSeg(pose, buffers, 1.2f, 1.1f, 3.5f, 0.56f, 1f, light);
				pose.translate(0, 3.2f / 16f, 0);
				fluke(pose, buffers, 4.5f, 4.5f, light);
			}
			pose.popPose();
		});
	}

	// ---------------------------------------------------------------- v0.17.2 (user: "Draw jaws as an effect"): the jaws

	/**
	 * The jaws for pose {@code animId}, {@code t} ticks in: {open 0..1, size 0..1, megamouth 0/1}, or null when none show. A bite
	 * opens over 3 ticks and snaps shut in one; Gnash does that five times in a second; Drag Under stays clenched on the catch;
	 * Megamouth gapes over the whole 0.75 s wind-up and slams shut.
	 */
	static float[] jawState(int animId, float t, int flags) {
		if (t < 0f) {
			return null;
		}
		switch (animId) {
			case RipjawsKit.ANIM_BITE -> {
				if (t > 8f) {
					return null;
				}
				float open = t < 3f ? t / 3f : t < 4f ? 1f - (t - 3f) : 0f;
				return new float[] { open, t < 6f ? 1f : 1f - (t - 6f) / 2f, 0f };
			}
			case RipjawsKit.ANIM_LUNGE -> {
				if (t > 12f) {
					return null;
				}
				float open = t < 8f ? Math.min(1f, t / 2f) : t < 9f ? 1f - (t - 8f) : 0f;
				return new float[] { open, t < 10f ? 1f : 1f - (t - 10f) / 2f, 0f };
			}
			case RipjawsKit.ANIM_GNASH -> {
				if (t > 22f) {
					return null;
				}
				float c = t % RipjawsKit.GNASH_EVERY;
				float open = c < 2.5f ? c / 2.5f : 1f - (c - 2.5f) / 1.5f;
				return new float[] { t < 20f ? open : 0f, t < 20f ? 1f : 1f - (t - 20f) / 2f, 0f };
			}
			case RipjawsKit.ANIM_DRAG, RipjawsKit.ANIM_THRASH -> {
				if ((flags & RipjawsKit.F_DRAG) == 0) {
					return null;
				}
				float open = t < 2f ? t / 2f : t < 3f ? 1f - (t - 2f) * 0.88f : 0.12f; // clenched on the catch
				return new float[] { open, 1f, 0f };
			}
			case RipjawsKit.ANIM_FLING -> {
				return t > 4f ? null : new float[] { 0.12f + t / 4f * 0.8f, 1f - t / 4f, 0f };
			}
			case RipjawsKit.ANIM_MEGA -> {
				float w = RipjawsKit.MEGA_WINDUP;
				if (t > w + 9f) {
					return null;
				}
				if (t < w) {
					float u = t / w;
					return new float[] { u * u * (3f - 2f * u), 0.55f + 0.45f * u, 1f };
				}
				float open = t < w + 2f ? 1f - (t - w) / 2f : 0f;
				return new float[] { open, t < w + 2f ? 1f : 1f - (t - w - 2f) / 7f, 1f };
			}
			case RipjawsKit.ANIM_DIVE -> {
				return (flags & RipjawsKit.F_DIVING) != 0 ? new float[] { Math.min(1f, t / 3f), 1f, 0f } : null;
			}
			case RipjawsKit.ANIM_DIVE_HIT -> {
				if (t > 7f) {
					return null;
				}
				return new float[] { t < 2f ? 1f - t / 2f : 0f, t < 2f ? 1f : 1f - (t - 2f) / 5f, 0f };
			}
			default -> {
				return null;
			}
		}
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
			if (!RipjawsKit.ID.equals(st.active) || !st.transformed() || pl.isInvisible()) {
				continue;
			}
			boolean firstPerson = pl == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 f = pl.getViewVector(partial);
			Vec3 r = f.cross(new Vec3(0, 1, 0));
			r = r.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : r.normalize();
			Vec3 u = r.cross(f).normalize();
			Vec3 eye = pl.getEyePosition(partial);
			float scale = pl.getScale();
			if ((st.kitFlags & RipjawsKit.F_LURE) != 0 && !firstPerson) {
				// the lure's glow, seen from round about
				Vec3 fh = new Vec3(f.x, 0, f.z);
				fh = fh.lengthSqr() < 1.0e-6 ? Vec3.ZERO : fh.normalize();
				Vec3 bulb = eye.add(0, 0.62 * scale, 0).add(fh.scale(0.38 * scale)).subtract(cam);
				PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), bulb, 0.32f, PyroDraw.dim(0xFFFFE890, 0.45f + 0.1f * Mth.sin((pl.tickCount + partial) * 0.25f)));
			}
			float t = pl.level().getGameTime() + partial - st.animStart;
			float[] js = jawState(st.animId, t, st.kitFlags);
			if (js == null || js[1] <= 0.01f) {
				continue;
			}
			boolean mega = js[2] > 0.5f;
			float radius;
			Vec3 anchor;
			if (mega) {
				// v0.17.2 (user: "Draw jaws as an effect"): Megamouth's jaws, 4 blocks across, out in front of him
				radius = 2.0f * js[1];
				anchor = eye.add(f.scale(firstPerson ? 2.3 : 1.9 * scale)).subtract(u.scale(firstPerson ? 0.35 : 0.15));
			} else if (firstPerson) {
				radius = 0.42f * js[1];
				anchor = eye.add(f.scale(0.8)).subtract(u.scale(0.24));
			} else {
				radius = 0.4f * scale * js[1];
				anchor = eye.add(f.scale(0.3 * scale)).subtract(u.scale(0.1 * scale));
			}
			int light = LevelRenderer.getLightColor(pl.level(), BlockPos.containing(anchor));
			jaws(pose, PyroDraw.solidBuffer(buffers), anchor.subtract(cam), f, r, u, radius, js[0], light);
		}
	}

	/**
	 * Two toothed crescents round {@code anchor} (camera-relative), the front of the curve at the anchor and the corners swept
	 * back {@code radius} either side; {@code open} 0..1 parts them (wider at the front than at the hinge).
	 */
	private static void jaws(PoseStack pose, VertexConsumer vc, Vec3 anchor, Vec3 f, Vec3 r, Vec3 u, float radius, float open, int light) {
		PoseStack.Pose last = pose.last();
		int n = 10;
		float a = 1.3f; // the arc: 75 degrees either side of straight ahead
		float gap = open * radius * 1.1f;
		for (int s = -1; s <= 1; s += 2) {
			Vec3[] edge = new Vec3[n + 1];
			Vec3[] outer = new Vec3[n + 1];
			float[] depth = new float[n + 1];
			for (int i = 0; i <= n; i++) {
				float th = -a + 2f * a * i / n;
				float c = Mth.cos(th);
				depth[i] = (c - Mth.cos(a)) / (1f - Mth.cos(a));
				Vec3 pt = anchor.add(f.scale(radius * (c - 1f) * 0.8f)).add(r.scale(radius * Mth.sin(th)));
				edge[i] = pt.add(u.scale(s * (radius * 0.05f + gap * (0.25f + 0.75f * depth[i]))));
				outer[i] = edge[i].add(u.scale(s * radius * 0.24f)).add(f.scale(radius * 0.04f));
			}
			for (int i = 0; i < n; i++) {
				// the gum band, its inner lip darker
				Vec3 m0 = edge[i].lerp(outer[i], 0.25), m1 = edge[i + 1].lerp(outer[i + 1], 0.25);
				quad(vc, last, GUM_EDGE, light, edge[i], edge[i + 1], m1, m0, u.scale(s));
				quad(vc, last, GUM, light, m0, m1, outer[i + 1], outer[i], u.scale(s));
				// a tooth from the lip toward the other jaw, longest at the front
				Vec3 b0 = edge[i].lerp(edge[i + 1], 0.12), b1 = edge[i].lerp(edge[i + 1], 0.88);
				float dm = (depth[i] + depth[i + 1]) * 0.5f;
				Vec3 tip = edge[i].lerp(edge[i + 1], 0.5).subtract(u.scale(s * radius * (0.12f + 0.16f * dm))).add(f.scale(radius * 0.02f));
				quad(vc, last, TOOTH, light, b0, b1, tip, tip, f);
			}
		}
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose last, int argb, int light, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 nrm) {
		AlienParts.quad(vc, last, argb, light, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, (float) c.x, (float) c.y,
				(float) c.z, (float) d.x, (float) d.y, (float) d.z, (float) nrm.x, (float) nrm.y, (float) nrm.z, 0f, 0f, 1f, 1f);
	}

	// ---------------------------------------------------------------- v0.17.2: Blood Scent / Blood in the Water

	/**
	 * The outline colour {@code viewer} (a transformed Ripjaws) sees on {@code e}, or -1 for none: in a Feeding Frenzy, anything
	 * under half health within 24 is red (Blood Scent); in water, anything in water within 24 that is hurt at all is a faint
	 * pink (Blood in the Water).
	 */
	public static int scentColour(LocalPlayer viewer, Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || e instanceof ArmorStand || e == viewer
				|| e.distanceToSqr(viewer) > RipjawsKit.SCENT_RANGE * RipjawsKit.SCENT_RANGE) {
			return -1;
		}
		OmnitrixState s = Omnitrix.state(viewer);
		if (!RipjawsKit.ID.equals(s.active) || !s.transformed()) {
			return -1;
		}
		if ((s.kitFlags & RipjawsKit.F_FRENZY) != 0 && le.getHealth() < le.getMaxHealth() * 0.5f) {
			return SCENT_RED;
		}
		if ((s.kitFlags & RipjawsKit.F_WET) != 0 && e.isInWater() && le.getHealth() < le.getMaxHealth()) {
			return WATER_SCENT;
		}
		return -1;
	}

	@Override
	public boolean glows(LocalPlayer viewer, Entity e) {
		return scentColour(viewer, e) >= 0;
	}

	/** For the outline colour mixin: whether {@code viewer} is shown as Ripjaws. */
	public static boolean shown(LocalPlayer viewer) {
		return AlienClients.shownKit(viewer) instanceof RipjawsClient;
	}

	// ---------------------------------------------------------------- poses

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		if (wet(p)) {
			// in water the legs are the tail
			m.rightLeg.visible = false;
			m.leftLeg.visible = false;
			if (m instanceof PlayerModel<?> pm) {
				pm.rightPants.visible = false;
				pm.leftPants.visible = false;
			}
		}
		if (animId == RipjawsKit.ANIM_THRASH && ticks < RipjawsKit.DRAG_TICKS + 2 && (Omnitrix.state(p).kitFlags & RipjawsKit.F_DRAG) != 0) {
			// v0.17.2 revamp: Drag Under on land -- he shakes the catch side to side, head and torso swinging with it
			float w = Mth.clamp(ticks / 3f, 0f, 1f);
			float sway = Mth.sin(ticks * 0.45f) * 0.5f * w;
			m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -1.4f);
			m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, -1.4f);
			m.rightArm.yRot = sway - 0.3f * w;
			m.leftArm.yRot = sway + 0.3f * w;
			m.body.yRot = sway;
			m.head.yRot += sway * 1.2f;
			return true;
		}
		float[][] clip = switch (animId) {
			case RipjawsKit.ANIM_BITE -> BITE;
			case RipjawsKit.ANIM_LUNGE -> LUNGE;
			case RipjawsKit.ANIM_DRAG -> DRAG;
			case RipjawsKit.ANIM_SWEEP -> SWEEP;
			case RipjawsKit.ANIM_DASH -> DASH;
			case RipjawsKit.ANIM_BREACH -> BREACH;
			case RipjawsKit.ANIM_WHIRL -> WHIRL;
			case RipjawsKit.ANIM_GNASH -> GNASH;
			case RipjawsKit.ANIM_MEGA -> MEGA;
			case RipjawsKit.ANIM_FRENZY -> FRENZY;
			case RipjawsKit.ANIM_DRENCH -> DRENCH;
			case RipjawsKit.ANIM_PUNCH -> PUNCH;
			case RipjawsKit.ANIM_DIG -> DIG;
			case RipjawsKit.ANIM_GASP -> GASP;
			case RipjawsKit.ANIM_BUBBLE -> BUBBLE;
			case RipjawsKit.ANIM_FLING -> FLING;
			case RipjawsKit.ANIM_DIVE -> DIVE;
			case RipjawsKit.ANIM_DIVE_HIT -> DIVE_HIT;
			case RipjawsKit.ANIM_LURE -> LURE;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (Omnitrix.state(p).kitFlags & RipjawsKit.F_CHOKING) != 0) {
			// choking on dry land: a hand at the gills
			m.rightArm.xRot = -1.8f;
			m.rightArm.yRot = -0.6f;
			m.head.xRot -= 0.25f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case RipjawsKit.ANIM_PUNCH -> FP_CLAW;
			case RipjawsKit.ANIM_BITE, RipjawsKit.ANIM_GNASH -> FP_BITE;
			case RipjawsKit.ANIM_LUNGE, RipjawsKit.ANIM_DASH -> FP_BACK;
			case RipjawsKit.ANIM_SWEEP, RipjawsKit.ANIM_MEGA, RipjawsKit.ANIM_WHIRL, RipjawsKit.ANIM_FLING -> FP_WIDE;
			case RipjawsKit.ANIM_BREACH, RipjawsKit.ANIM_DRENCH, RipjawsKit.ANIM_DIVE, RipjawsKit.ANIM_DIVE_HIT -> FP_UP;
			case RipjawsKit.ANIM_FRENZY, RipjawsKit.ANIM_GASP, RipjawsKit.ANIM_DRAG, RipjawsKit.ANIM_THRASH -> FP_GRAB;
			case RipjawsKit.ANIM_DIG -> FP_DIG;
			case RipjawsKit.ANIM_BUBBLE, RipjawsKit.ANIM_LURE -> FP_BUBBLE;
			default -> null;
		};
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		// v0.17.2: no forearm fins
	}

	/** v0.17.2 swim: the velocity handed to vanilla last tick and where he was then (null when not driving the swim). */
	private static Vec3 swimSet, swimFrom;
	private static int swimTick;

	/**
	 * v0.17.2 (user: "make ripjaws cruise swim at 10 blocks per second and his sprint swim be 30 blocks per second please"): his
	 * own client drives the swim (a player's movement is his client's), steered by his look -- forward held under water: 10
	 * blocks a second, sprint-swimming 30. Set at the end of each client tick, so the next tick's water travel moves him by it;
	 * vanilla's own additions that tick (the keys' swim push, the pitch nudge) are measured and taken back off
	 * ({@link RipjawsKit#swimPush}), so the speed is the number, not the number plus vanilla. Water Dash, Jaw Lunge, Breach and the
	 * Dive Strike keep their own launch.
	 */
	private static void swim(LocalPlayer me) {
		OmnitrixState s = Omnitrix.state(me);
		boolean drive = RipjawsKit.ID.equals(s.active) && s.transformed() && me.isInWater() && me.isAlive() && !me.isPassenger() && !me.isSpectator()
				&& !me.getAbilities().flying && me.input != null && me.input.forwardImpulse > 0.05f && !RipjawsKit.swimHeld(s, me.level().getGameTime());
		if (!drive) {
			swimSet = null;
			swimFrom = null;
			return;
		}
		Vec3 now = me.position();
		// only last tick's numbers count (the kit is not ticked while he is someone else)
		Vec3 moved = swimFrom == null || me.tickCount != swimTick + 1 ? null : now.subtract(swimFrom);
		swimTick = me.tickCount;
		Vec3 desired = RipjawsKit.swimVelocity(me.getLookAngle(), me.isSprinting(), me.isUnderWater());
		Vec3 set = RipjawsKit.swimPush(desired, swimSet, moved, me.horizontalCollision);
		me.setDeltaMovement(set);
		swimSet = set;
		swimFrom = now;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		if (p instanceof LocalPlayer me && p == Minecraft.getInstance().player) {
			swim(me);
		}
		var r = p.getRandom();
		var level = p.level();
		if (p.isInWater()) {
			if (r.nextInt(10) == 0) {
				level.addParticle(ParticleTypes.BUBBLE, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() * 0.85,
						p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
			}
			return;
		}
		float hyd = RipjawsKit.hydration(p, level.getGameTime());
		if (hyd > 0.6f && r.nextInt(16) == 0) {
			level.addParticle(ParticleTypes.DRIPPING_WATER, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + p.getBbHeight() * 0.6,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
		}
	}

	@Override
	public int hudHeight() {
		return 10; // one meter row over the name line
	}

	/**
	 * The hydration meter, in the Omnitrix HUD's meter style: a blue bar (orange under 25%, flashing red when he is choking),
	 * "+" while water or rain refills it (v0.17.2: no seconds-left timer any more).
	 */
	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		long now = p.level().getGameTime();
		int dry = RipjawsKit.dryness(p, now);
		float hyd = 1f - dry / (float) RipjawsKit.MAX_DRY;
		boolean choking = dry >= RipjawsKit.MAX_DRY;
		int flags = Omnitrix.state(p).kitFlags;
		boolean wet = (flags & (RipjawsKit.F_WET | RipjawsKit.F_RAIN)) != 0;
		boolean flash = (now / 5) % 2 == 0;
		int col = choking ? (flash ? 0xFFFF3A2E : 0xFF801810) : hyd < 0.25f ? 0xFFF0A020 : 0xFF3AA8F0;
		// v0.17.2 (user: "remove the timer from the hydration bar for ripjaws please"): the bar alone, no seconds left
		String value = choking ? "!" : wet ? "+" : "";
		int valueCol = choking ? (flash ? 0xFFFF6A5A : 0xFFE2E2E2) : wet ? 0xFF8AD0FF : hyd < 0.25f ? 0xFFF0C060 : 0xFFE2E2E2;
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.ripjaws.meter"), hyd, col,
				value, valueCol);
	}


	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), high on his left breast
		return new float[] { 2.5f, 2.0f, -2.05f, 3.0f };
	}
}

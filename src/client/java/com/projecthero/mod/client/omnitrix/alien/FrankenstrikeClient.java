package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.frankenstrike.FrankenstrikeFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.frankenstrike.FrankenstrikeKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Frankenstrike's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body (x1.2, a brute), the pixel Omnitrix on his
 * chest painted out and the mod's badge in its place; his lime eyes and glow spots full-bright ({@code frankenstrike_glow.png}). The two
 * electrode bolts stand up off the backs of his shoulders (geometry: steel posts, ringed caps, glowing tips) with electricity arcing
 * between them ({@link FrankenstrikeFx}) -- a lazy flicker when he is low, a constant crackle when he is full. The HUD shows his Charge.
 */
public final class FrankenstrikeClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/frankenstrike.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/frankenstrike_glow.png");
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	/** The chest badge (body-bone pixels): where the skin painted its symbol (jacket texels 23..24 x 38..39). */
	public static final float[] BADGE = { 0f, 3.0f, -2.32f, 2.8f };
	/**
	 * The bolts in body-bone pixels: each stands on the back of a shoulder (x either side of the head, z behind the body's middle) and
	 * leans back ({@link #BOLT_LEAN} radians), so the tips -- and the arc between them -- clear the back of his head.
	 */
	public static final float BOLT_X = 4.9f, BOLT_Z = 2.0f, BOLT_H = 4.4f, BOLT_LEAN = 0.55f;

	/** Into one bolt's space (from the body bone's): its base at the origin, +y down its post, the tip at y = -BOLT_H - 1. */
	private static void boltSpace(PoseStack pose, float side) {
		pose.translate(side * BOLT_X / 16f, 0f, BOLT_Z / 16f);
		pose.mulPose(com.mojang.math.Axis.XP.rotation(-BOLT_LEAN));
		pose.mulPose(com.mojang.math.Axis.ZP.rotation(side * 0.12f));
	}

	/** A bolt tip in body-bone pixels at rest (y down, z back), for the arc between them ({@link FrankenstrikeFx}). */
	public static org.joml.Vector3f tip(float side) {
		org.joml.Matrix4f m = new org.joml.Matrix4f().translate(side * BOLT_X / 16f, 0f, BOLT_Z / 16f).rotateX(-BOLT_LEAN).rotateZ(side * 0.12f);
		return m.transformPosition(new org.joml.Vector3f(0f, (-BOLT_H - 1.0f) / 16f, 0f));
	}

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.FRANKENSTRIKE.id(), new FrankenstrikeClient());
		if (!registered) {
			registered = true;
			FrankenstrikeFx.register();
		}
	}

	// ---------------------------------------------------------------- poses
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly, float rlz, float llx,
			float lly, float llz, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/** Shock Punch: a heavy wind-up, the whole body turned into a haymaker. */
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, 0.6f, 0.4f, 0.3f, -0.6f, 0, -0.2f, 0.1f, 0.5f, 0.15f, 0, 0, -0.2f, 0, 0, 0),
			f(4, -1.7f, -0.2f, 0, 0.5f, 0, -0.3f, 0.2f, -0.55f, -0.4f, 0, 0, 0.35f, 0, 0, 0.05f), f(8, -1.5f, -0.1f, 0, 0.4f, 0, -0.2f, 0.15f, -0.4f, -0.3f, 0, 0, 0.25f, 0, 0, 0),
			QuirkKeyframes.rest(14) };
	private static final float[][] JAB = { QuirkKeyframes.rest(0), f(2, -1.5f, 0.1f, 0, 0.3f, 0, -0.1f, 0.15f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(8) };
	/** Lightning Bolt: the arm thrown out, fingers spread, the other fist clenched back. */
	private static final float[][] BOLT = { QuirkKeyframes.rest(0), f(2, -1.75f, 0.15f, 0, 0.6f, 0, -0.35f, 0.1f, -0.4f, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			f(7, -1.6f, 0.1f, 0, 0.5f, 0, -0.3f, 0.06f, -0.3f, -0.2f, 0, 0, 0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	/** Conductor Slam: both fists up over his head, then hammered into the ground on a bent knee. */
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), f(3, -2.9f, 0.2f, 0, -2.9f, -0.2f, 0, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.3f),
			f(6, -0.9f, 0, 0, -0.9f, 0, 0, 0.75f, 0, -1.2f, 0, 0.1f, 0.3f, 0, -0.1f, 0.4f), f(13, -0.9f, 0, 0, -0.9f, 0, 0, 0.7f, 0, -1.1f, 0, 0.1f, 0.3f, 0, -0.1f, 0.35f),
			QuirkKeyframes.rest(20) };
	/** Electro Leap: a deep crouch and a big spring, fists back. */
	private static final float[][] LEAP = { QuirkKeyframes.rest(0), f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.5f, 0, 0.4f, 0, 0, -0.8f, 0, 0, -0.3f),
			f(8, -0.6f, 0, 0.6f, -0.6f, 0, -0.6f, 0.1f, 0, -0.9f, 0, 0, -0.4f, 0, 0, 0), QuirkKeyframes.rest(16) };
	/** The landing: a three-point drop, a fist into the ground. */
	private static final float[][] LAND = { QuirkKeyframes.rest(0), f(2, -0.8f, 0, 0.1f, 0.4f, 0, -0.5f, 0.6f, 0, -1.1f, 0, 0.1f, 0.4f, 0, -0.1f, 0.3f),
			f(8, -0.7f, 0, 0.1f, 0.3f, 0, -0.4f, 0.5f, 0, -1.0f, 0, 0.1f, 0.35f, 0, -0.1f, 0.25f), QuirkKeyframes.rest(14) };
	/** Overcharge Storm: both arms raised to the sky, head back, calling it down (held while the storm lasts). */
	private static final float[][] STORM = { QuirkKeyframes.rest(0), f(5, -3.0f, 0, 0.35f, -3.0f, 0, -0.35f, -0.15f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.6f),
			f(60, -3.0f, 0, 0.4f, -3.0f, 0, -0.4f, -0.18f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.65f), QuirkKeyframes.rest(66) };
	/** Grab: both hands out round the creature, then lifting it up. */
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), f(3, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.15f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0) };
	private static final float[] GRAB_HOLD = f(0, -2.0f, -0.3f, 0, -2.0f, 0.3f, 0, -0.08f, 0, -0.2f, 0, 0.08f, 0.25f, 0, -0.08f, -0.15f);
	/** The throw: hurled forward off both arms. */
	private static final float[][] THROW = { f(0, -2.0f, -0.3f, 0, -2.0f, 0.3f, 0, -0.08f, 0, -0.2f, 0, 0.08f, 0.25f, 0, -0.08f, -0.15f),
			f(3, -1.1f, -0.2f, 0, -1.1f, 0.2f, 0, 0.35f, 0, -0.5f, 0, 0, 0.4f, 0, 0, 0.1f), QuirkKeyframes.rest(10) };
	/** Recharge's held stance: arms out and up, palms open to the sky. */
	private static final float[] RECHARGE_HOLD = f(0, -2.4f, 0, 0.7f, -2.4f, 0, -0.7f, -0.1f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.4f);
	private static final float[][] RECHARGE = { QuirkKeyframes.rest(0), f(5, -2.4f, 0, 0.7f, -2.4f, 0, -0.7f, -0.1f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.4f) };
	private static final float[][] RECHARGE_END = { f(0, -2.4f, 0, 0.7f, -2.4f, 0, -0.7f, -0.1f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.4f), QuirkKeyframes.rest(8) };
	/** v0.17.4 Static Armour: a fist raised and shaken as the crackle spreads over him. */
	private static final float[][] QUICK = { QuirkKeyframes.rest(0), f(3, -2.8f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.3f),
			f(8, -2.8f, 0, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -0.3f), QuirkKeyframes.rest(13) };
	/** Lightning Rod: braced, chest out, arms down and back as the bolt hits his electrodes. */
	private static final float[][] ROD = { QuirkKeyframes.rest(0), f(2, 0.5f, 0, 0.6f, 0.5f, 0, -0.6f, -0.35f, 0, -0.2f, 0, 0.2f, 0.2f, 0, -0.2f, -0.6f),
			f(12, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, -0.3f, 0, -0.2f, 0, 0.2f, 0.2f, 0, -0.2f, -0.5f), QuirkKeyframes.rest(20) };

	// ---------------------------------------------------------------- v0.17.4 poses
	/** Arc Stream (held): the right arm thrust out, fingers spread, the left braced across; the arm shudders with the current. */
	private static final float[] ARC_HELD = f(0, -1.6f, 0.1f, 0, -0.5f, 0.5f, 0, 0.12f, -0.35f, -0.3f, 0, 0, 0.3f, 0, 0, 0);
	private static final float[][] ARC = { QuirkKeyframes.rest(0), at(3, ARC_HELD), at(4000, ARC_HELD) };
	/** Tesla Field: both fists driven down either side of him, planting the electrodes. */
	private static final float[][] TESLA = { QuirkKeyframes.rest(0), f(3, -2.6f, 0, 0.6f, -2.6f, 0, -0.6f, -0.15f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.2f),
			f(6, 0.2f, 0, 0.7f, 0.2f, 0, -0.7f, 0.35f, 0, -0.4f, 0, 0.25f, 0.4f, 0, -0.25f, 0.2f), f(12, 0.15f, 0, 0.6f, 0.15f, 0, -0.6f, 0.3f, 0, -0.35f, 0, 0.2f, 0.35f, 0, -0.2f, 0.15f),
			QuirkKeyframes.rest(18) };
	/** Charged Slam (held): both fists raised high, the body arched back, winding up. */
	private static final float[] CSLAM_HELD = f(0, -3.0f, 0.2f, 0, -3.0f, -0.2f, 0, -0.3f, 0, 0.15f, 0, 0.12f, -0.15f, 0, -0.12f, -0.35f);
	private static final float[][] CSLAM = { QuirkKeyframes.rest(0), at(6, CSLAM_HELD), at(4000, CSLAM_HELD) };
	/** Lightning Ride: arriving low out of the bolt, one hand down on the ground. */
	private static final float[][] RIDE = { f(0, -0.6f, 0, 0.4f, 0.6f, 0, -0.6f, 0.6f, 0, -1.2f, 0, 0.1f, 0.5f, 0, -0.1f, 0.3f),
			f(6, -0.5f, 0, 0.35f, 0.5f, 0, -0.5f, 0.5f, 0, -1.0f, 0, 0.1f, 0.4f, 0, -0.1f, 0.25f), QuirkKeyframes.rest(14) };
	/** Defibrillate: both palms slapped to his chest, then thrown back as the jolt goes through him. */
	private static final float[][] DEFIB = { QuirkKeyframes.rest(0), f(3, -1.4f, -0.8f, 0, -1.4f, 0.8f, 0, 0.2f, 0, -0.1f, 0, 0, 0.1f, 0, 0, 0.3f),
			f(6, 0.5f, 0, 0.7f, 0.5f, 0, -0.7f, -0.4f, 0, 0.1f, 0, 0.15f, -0.1f, 0, -0.15f, -0.6f),
			f(14, 0.4f, 0, 0.6f, 0.4f, 0, -0.6f, -0.3f, 0, 0.1f, 0, 0.15f, -0.1f, 0, -0.15f, -0.45f), QuirkKeyframes.rest(20) };
	/** Monster Roar: hunched forward, arms flung back, head thrust out. */
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), f(3, -0.6f, 0, 0.3f, -0.6f, 0, -0.3f, -0.2f, 0, 0.1f, 0, 0, -0.1f, 0, 0, -0.4f),
			f(6, 0.8f, 0, 0.6f, 0.8f, 0, -0.6f, 0.35f, 0, -0.4f, 0, 0.2f, 0.4f, 0, -0.2f, 0.25f),
			f(20, 0.7f, 0, 0.55f, 0.7f, 0, -0.55f, 0.3f, 0, -0.35f, 0, 0.2f, 0.35f, 0, -0.2f, 0.2f), QuirkKeyframes.rest(26) };

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	private static final FirstPersonClip FP_ARC = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -24, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(4000, -24, 8, 0, 0.06f, 0.14f, -0.42f));
	private static final FirstPersonClip FP_CSLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(6, -70, 10, 0, 0.05f, 0.55f, 0.0f),
			FirstPersonClip.f(4000, -70, 10, 0, 0.05f, 0.55f, 0.0f)).both();
	private static final FirstPersonClip FP_CHEST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -40, 0, -0.25f, -0.15f, 0.05f),
			FirstPersonClip.f(6, 40, 0, 0, 0, -0.5f, 0.15f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, 40, 0, 0, 0, -0.5f, 0.15f),
			FirstPersonClip.f(20, 40, 0, 0, 0, -0.5f, 0.15f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0)).both();

	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 20, -20, 0, -0.1f, -0.1f, 0.2f),
			FirstPersonClip.f(4, -18, 12, 0, 0.1f, 0.12f, -0.5f), FirstPersonClip.f(9, -10, 8, 0, 0.06f, 0.06f, -0.3f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -24, 8, 0, 0.06f, 0.14f, -0.42f),
			FirstPersonClip.f(7, -20, 6, 0, 0.05f, 0.12f, -0.32f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 0, 0, 0, 0.5f, 0.1f),
			FirstPersonClip.f(6, 40, 0, 0, 0, -0.4f, -0.2f), FirstPersonClip.f(13, 35, 0, 0, 0, -0.35f, -0.2f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GRAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 14, 0, 0.12f, 0.16f, -0.4f),
			FirstPersonClip.f(40, -28, 14, 0, 0.12f, 0.24f, -0.4f), FirstPersonClip.f(46, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, 10, 0, 0.05f, 0.55f, 0.0f),
			FirstPersonClip.f(12, -70, 10, 0, 0.05f, 0.55f, 0.0f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	/** How lit his bolt tips are, 0.35..1 by Charge (full flicker while he recharges or calls the storm). */
	public static float boltGlow(AbstractClientPlayer p, float age) {
		float c = FrankenstrikeKit.charge(p) / (float) FrankenstrikeKit.MAX_CHARGE;
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & (FrankenstrikeKit.F_RECHARGE | FrankenstrikeKit.F_STORM)) != 0) {
			return 0.8f + 0.2f * Mth.sin(age * 2.3f);
		}
		return 0.35f + 0.6f * c + 0.05f * Mth.sin(age * 0.7f);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float g = boltGlow(p, age);
		for (float side : new float[] { -1f, 1f }) {
			AlienParts.on(pose, model.body, () -> {
				boltSpace(pose, side);
				// the post, rising up and back off the top of the shoulder
				AlienParts.box(pose, steel, -0.8f, -BOLT_H, -0.8f, 0.8f, 0.6f, 0.8f, 0, 0, 1, 1, 0xFF6E747A, light);
				// two rings and a broad cap
				AlienParts.box(pose, steel, -1.15f, -1.2f, -1.15f, 1.15f, -0.5f, 1.15f, 0, 0, 1, 1, 0xFF4E5358, light);
				AlienParts.box(pose, steel, -1.15f, -BOLT_H + 1.3f, -1.15f, 1.15f, -BOLT_H + 2.0f, 1.15f, 0, 0, 1, 1, 0xFF4E5358, light);
				AlienParts.box(pose, steel, -1.35f, -BOLT_H - 0.6f, -1.35f, 1.35f, -BOLT_H, 1.35f, 0, 0, 1, 1, 0xFF8A9096, light);
			});
		}
		// the glowing tips, full-bright
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
		int tip = 0xFF000000 | (PyroDraw.dim(PyroDraw.lerpRgb(0x9ACB4A, FrankenstrikeKit.COLOUR, g), g) & 0xFFFFFF);
		for (float side : new float[] { -1f, 1f }) {
			AlienParts.on(pose, model.body, () -> {
				boltSpace(pose, side);
				AlienParts.box(pose, glow, -0.7f, -BOLT_H - 1.4f, -0.7f, 0.7f, -BOLT_H - 0.6f, 0.7f, 0, 0, 1, 1, tip, LightTexture.FULL_BRIGHT);
			});
		}
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFA0A0A0);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		boolean held = false;
		if ((flags & FrankenstrikeKit.F_RECHARGE) != 0) {
			QuirkKeyframes.apply(m, RECHARGE_HOLD, Mth.clamp(animId == FrankenstrikeKit.ANIM_RECHARGE ? ticks / 5f : 1f, 0f, 1f));
			m.rightArm.zRot += Mth.sin(age * 1.3f) * 0.04f; // the current shaking him
			m.leftArm.zRot -= Mth.sin(age * 1.3f) * 0.04f;
			held = true;
		}
		if ((flags & FrankenstrikeKit.F_ARC) != 0) {
			boolean on = QuirkKeyframes.play(m, ARC, Math.min(ticks, 3f), 1f);
			m.rightArm.zRot += Mth.sin(age * 2.3f) * 0.05f;
			m.rightArm.xRot += Mth.sin(age * 1.9f) * 0.04f;
			return on;
		}
		if ((flags & FrankenstrikeKit.F_CSLAM) != 0) {
			boolean on = QuirkKeyframes.play(m, CSLAM, Math.min(ticks, 6f), 1f);
			float k = FrankenstrikeKit.slamCharge(p, 0f);
			m.rightArm.zRot += Mth.sin(age * 2.1f) * 0.05f * k;
			m.leftArm.zRot -= Mth.sin(age * 2.1f) * 0.05f * k;
			return on;
		}
		if ((flags & FrankenstrikeKit.F_GRAB) != 0) {
			if (animId == FrankenstrikeKit.ANIM_GRAB && ticks < 3f) {
				return QuirkKeyframes.play(m, GRAB, ticks, 1f);
			}
			QuirkKeyframes.apply(m, GRAB_HOLD, 1f);
			m.rightArm.xRot += Mth.sin(age * 1.7f) * 0.05f;
			m.leftArm.xRot += Mth.sin(age * 1.7f + 1f) * 0.05f;
			return true;
		}
		float[][] clip = switch (animId) {
			case FrankenstrikeKit.ANIM_PUNCH -> PUNCH;
			case FrankenstrikeKit.ANIM_JAB -> JAB;
			case FrankenstrikeKit.ANIM_BOLT -> BOLT;
			case FrankenstrikeKit.ANIM_SLAM -> SLAM;
			case FrankenstrikeKit.ANIM_LEAP -> LEAP;
			case FrankenstrikeKit.ANIM_LAND -> LAND;
			case FrankenstrikeKit.ANIM_STORM -> STORM;
			case FrankenstrikeKit.ANIM_THROW -> THROW;
			case FrankenstrikeKit.ANIM_RECHARGE_END -> RECHARGE_END;
			case FrankenstrikeKit.ANIM_STATIC -> QUICK;
			case FrankenstrikeKit.ANIM_ROD -> ROD;
			case FrankenstrikeKit.ANIM_TESLA -> TESLA;
			case FrankenstrikeKit.ANIM_RIDE -> RIDE;
			case FrankenstrikeKit.ANIM_DEFIB -> DEFIB;
			case FrankenstrikeKit.ANIM_ROAR -> ROAR;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f) || held;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case FrankenstrikeKit.ANIM_PUNCH -> FP_PUNCH;
			case FrankenstrikeKit.ANIM_BOLT -> FP_BOLT;
			case FrankenstrikeKit.ANIM_SLAM, FrankenstrikeKit.ANIM_LAND -> FP_SLAM;
			case FrankenstrikeKit.ANIM_GRAB -> FP_GRAB;
			case FrankenstrikeKit.ANIM_STORM, FrankenstrikeKit.ANIM_STATIC, FrankenstrikeKit.ANIM_ROD -> FP_UP;
			case FrankenstrikeKit.ANIM_TESLA, FrankenstrikeKit.ANIM_RIDE -> FP_SLAM;
			case FrankenstrikeKit.ANIM_ARC -> FP_ARC;
			case FrankenstrikeKit.ANIM_CSLAM -> FP_CSLAM;
			case FrankenstrikeKit.ANIM_DEFIB -> FP_CHEST;
			case FrankenstrikeKit.ANIM_ROAR -> FP_ROAR;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the HUD: his Charge

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int c = FrankenstrikeKit.charge(p);
		boolean full = c >= FrankenstrikeKit.MAX_CHARGE;
		boolean flash = (p.level().getGameTime() / 6) % 2 == 0;
		String value = full && flash ? "FULL" : String.valueOf(c);
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.frankenstrike.meter"),
				c / (float) FrankenstrikeKit.MAX_CHARGE, 0xFF000000 | FrankenstrikeKit.COLOUR, value, full ? 0xFFE8FBFF : 0xFFE2E2E2);
	}
}

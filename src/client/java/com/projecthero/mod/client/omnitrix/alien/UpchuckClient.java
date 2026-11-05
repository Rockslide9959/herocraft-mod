package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.upchuck.UpchuckKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Upchuck's client kit (user: "heres upchucks skin please build him: https://www.planetminecraft.com/skin/upchuck/ ... remove
 * the pixel omnitrix from his skin model and replace it with our omnitrix badge please"). The user's skin on the player body at 0.6
 * -- a small green Gourmand in the original series' black-and-white suit -- with the skin's wrist Omnitrix painted out
 * ({@code scratchpad/ben10_v0171/gen_upchuck.js}) and the mod's badge on the outside of his left wrist in its place (the show: he
 * wears the symbol on his left hand). His three tongues are drawn from his mouth (sticky pink tubes that shoot out, curl and pull
 * taut), his belly swells as his Gut fills, and the HUD shows the Gut meter.
 *
 * <p>v0.17.2 (user: "edit and update animations on all moves please", "upgrade his abilities vfx and models please"): every move has its
 * own pose, third and first person; his mouth really opens on the face when he spits, eats or gobbles; the belly is a smooth swell rather
 * than a box; his spit, its explosions and splats are {@link com.projecthero.mod.client.omnitrix.alien.upchuck.UpchuckFx}.
 */
public final class UpchuckClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/upchuck.png");
	/** The wrist badge: the left arm's outer side (+x), at the skin's painted symbol (rows 60..61 = arm y 6..8). */
	public static final float[] BADGE = { 3.3f, 7.0f, 0f, 3.0f, 1f };
	/** Tongue colours: the top, the darker underside, the tip. */
	private static final int TONGUE = 0xFFD8607A, TONGUE_DARK = 0xFFA83E58, TIP = 0xFFF08AA0;

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.UPCHUCK.id(), new UpchuckClient());
		if (!registered) {
			registered = true;
			OmnitrixRenderers.projectileLook("upchuck_tongue", UpchuckClient::tongue);
			com.projecthero.mod.client.omnitrix.alien.upchuck.UpchuckFx.register();
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg x, lLeg x, head x, crouch)
	private static float[] g(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx, float hx,
			float crouch) {
		return QuirkKeyframes.fc(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, hx, crouch);
	}

	private static float[] rest(float t) {
		return QuirkKeyframes.restC(t);
	}

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	/** Hands on his belly (the gulp, the Digest): a frame to copy. */
	private static final float[] BELLY = g(0, -0.8f, 0.6f, 0, -0.8f, -0.6f, 0, 0.05f, 0, 0, 0, 0, 0.1f);

	/** A loogie: the shot leaves on the press, so the whole body is already heaving after it -- head and shoulders thrown forward, arms back. */
	private static final float[][] SPIT = { rest(0), g(2, 0.55f, 0, 0.35f, 0.55f, 0, -0.35f, 0.5f, 0, 0.15f, -0.15f, 0.25f, 0.15f),
			g(5, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.4f, 0, 0.15f, -0.15f, 0.2f, 0.1f), rest(12) };
	/** Curveball: slung sidearm like a frisbee, the body whipping round after it. */
	private static final float[][] CURVE = { rest(0), g(2, -0.9f, -0.7f, 0.5f, 0.3f, 0, -0.4f, 0.1f, 0.5f, 0.1f, -0.1f, 0.05f, 0.1f),
			g(5, -0.9f, 0.9f, 0.3f, 0.5f, 0, -0.4f, 0.3f, -0.5f, 0.1f, -0.1f, 0.1f, 0.15f), rest(13) };
	/** Slime Slick: head back, then a gob lobbed up and over. */
	private static final float[][] SLICK = { rest(0), g(2, 0.3f, 0, 0.4f, 0.3f, 0, -0.4f, -0.4f, 0, 0.1f, -0.1f, -0.4f, 0),
			g(5, 0.6f, 0, 0.3f, 0.6f, 0, -0.3f, 0.35f, 0, 0.1f, -0.1f, 0.1f, 0.15f), rest(13) };
	/** Tongues out: thrown forward as they lash out, then leaning back on his heels, hauling. */
	private static final float[][] TONGUE_OUT = { rest(0), g(2, 0.7f, 0, 0.45f, 0.7f, 0, -0.45f, 0.4f, 0, -0.35f, 0.35f, 0.2f, 0.3f),
			g(7, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, -0.2f, 0, -0.25f, 0.3f, -0.1f, 0.35f), g(11, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, -0.22f, 0, -0.25f, 0.3f, -0.1f, 0.35f), rest(15) };
	/** A gulp: head thrown back as it goes down, then hands on his belly. */
	private static final float[][] GULP = { rest(0), g(3, -0.3f, 0.2f, 0.3f, -0.3f, -0.2f, -0.3f, -0.4f, 0, 0, 0, -0.45f, 0), g(5, -0.3f, 0.2f, 0.3f, -0.3f, -0.2f, -0.3f, -0.4f, 0, 0, 0, -0.45f, 0),
			at(9, BELLY), at(13, BELLY), rest(17) };
	/** The Vomit Rocket: arms swept back, legs trailing, nose first -- held until he comes down. */
	private static final float[] ROCKET_AIR = g(0, 1.2f, 0, 0.55f, 1.2f, 0, -0.55f, 0.6f, 0, 0.75f, 0.85f, -0.45f, 0);
	private static final float[][] ROCKET = { rest(0), at(2, ROCKET_AIR), at(400, ROCKET_AIR) };
	/** Coming down: he lands in a squat and bobs up. */
	private static final float[][] LAND = { at(0, ROCKET_AIR), g(3, 0.4f, 0, 0.6f, 0.4f, 0, -0.6f, 0.4f, 0, -0.2f, 0.2f, 0.1f, 0.8f), rest(11) };
	/** Devour: a lunge at the block, a snap and a chew. */
	private static final float[][] EAT = { rest(0), g(2, -1.0f, 0, 0.25f, -1.0f, 0, -0.25f, 0.55f, 0, 0.1f, -0.1f, 0.3f, 0.2f),
			g(4, -0.6f, 0.2f, 0.2f, -0.6f, -0.2f, -0.2f, 0.2f, 0, 0, 0, -0.1f, 0.1f), g(6, -0.55f, 0.2f, 0.2f, -0.55f, -0.2f, -0.2f, 0.3f, 0, 0, 0, 0.1f, 0.1f), rest(10) };
	/** Gobble: braced, arms flung wide, head back and mouth open to the world, held for as long as C is. */
	private static final float[] GOBBLE_HELD = g(0, -0.35f, 0, 1.3f, -0.35f, 0, -1.3f, -0.2f, 0, 0.14f, -0.14f, -0.15f, 0.2f);
	private static final float[][] GOBBLE = { rest(0), at(4, GOBBLE_HELD), at(40000, GOBBLE_HELD) };
	/** C comes up: the mouth snaps shut, the gulp, hands to his belly. */
	private static final float[][] GOBBLE_END = { at(0, GOBBLE_HELD), g(2, -0.35f, 0, 1.0f, -0.35f, 0, -1.0f, 0.15f, 0, 0, 0, 0.2f, 0.1f), at(6, BELLY), rest(12) };
	/** Digest: a slow belly rub, side to side. */
	private static final float[][] DIGEST = { rest(0), at(3, BELLY), g(7, -0.65f, 0.75f, 0, -0.95f, -0.4f, 0, 0.05f, 0.12f, 0, 0, -0.1f, 0.1f),
			g(11, -0.95f, 0.4f, 0, -0.65f, -0.75f, 0, 0.05f, -0.12f, 0, 0, -0.1f, 0.1f), at(15, BELLY), rest(19) };
	/** The Mega Loogie: he rears back, swelling, arms spreading; on tick 14 the heave; then reeling from the recoil. */
	private static final float[][] MEGA = { rest(0), g(6, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.35f, 0, 0.15f, -0.15f, -0.3f, 0.1f),
			g(13, -0.6f, 0, 1.15f, -0.6f, 0, -1.15f, -0.55f, 0, 0.3f, -0.3f, -0.4f, 0.2f), g(15, 1.1f, 0, 0.4f, 1.1f, 0, -0.4f, 0.75f, 0, 0.35f, -0.35f, 0.35f, 0.45f),
			g(22, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, 0.25f, 0, 0, 0, 0.1f, 0.2f), rest(30) };
	/** Feeding Frenzy: fists up, bouncing, ravenous. */
	private static final float[][] FRENZY = { rest(0), g(3, -2.8f, 0, 0.35f, -2.8f, 0, -0.35f, -0.2f, 0, 0, 0, -0.3f, 0.3f), g(7, -2.5f, 0, 0.5f, -2.5f, 0, -0.5f, -0.1f, 0, 0, 0, -0.2f, 0),
			g(11, -2.8f, 0, 0.35f, -2.8f, 0, -0.35f, -0.2f, 0, 0, 0, -0.3f, 0.3f), rest(18) };
	/** His bite: a quick lunge, teeth first. */
	private static final float[][] CHOMP = { rest(0), g(2, 0.3f, 0, 0.35f, 0.3f, 0, -0.35f, 0.45f, 0, 0.1f, -0.1f, 0.3f, 0.15f), rest(8) };

	private static boolean follow(HumanoidModel<?> m, float[][] clip, float ticks) {
		float[] p = QuirkKeyframes.sample(clip, ticks);
		if (p == null) {
			return false;
		}
		QuirkKeyframes.apply(m, p, Math.min(1f, (QuirkKeyframes.length(clip) - ticks) / QuirkKeyframes.FADE_OUT));
		return true;
	}

	private static float[] fp(float t, float pitch, float yaw, float roll, float x, float y, float z) {
		return FirstPersonClip.f(t, pitch, yaw, roll, x, y, z);
	}

	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 34, 0, 0, 0f, -0.3f, 0.12f), fp(6, 28, 0, 0, 0f, -0.25f, 0.1f), fp(12, 0, 0, 0, 0, 0, 0)).both();
	/** The right hand slung across the view. */
	private static final FirstPersonClip FP_CURVE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -18, -40, 0, -0.3f, 0.08f, 0f), fp(5, -14, 46, 0, 0.42f, 0.04f, -0.1f), fp(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLICK = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -20, 0, 0, 0f, 0.12f, 0.06f), fp(5, 36, 0, 0, 0f, -0.32f, 0.1f), fp(13, 0, 0, 0, 0, 0, 0)).both();
	/** Tongues out: the hands drop away and brace as he hauls. */
	private static final FirstPersonClip FP_TONGUE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 20, -14, 0, -0.1f, -0.2f, 0.08f), fp(7, 30, -18, 0, -0.14f, -0.3f, 0.16f),
			fp(11, 30, -18, 0, -0.14f, -0.3f, 0.16f), fp(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROCKET = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 40, 0, 0, 0f, -0.42f, 0.25f), fp(400, 40, 0, 0, 0f, -0.42f, 0.25f)).both();
	private static final FirstPersonClip FP_LAND = FirstPersonClip.of(fp(0, 40, 0, 0, 0f, -0.42f, 0.25f), fp(3, -10, -20, 0, -0.14f, -0.08f, 0f), fp(11, 0, 0, 0, 0, 0, 0)).both();
	/** Devour: the hands come up and shovel it in. */
	private static final FirstPersonClip FP_EAT = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, -32, 24, 0, 0.18f, 0.18f, -0.1f), fp(5, -10, 34, 0, 0.26f, 0.04f, 0.05f), fp(10, 0, 0, 0, 0, 0, 0)).both();
	/** Gobble: arms out to the edges of the view, held; they come back in as he closes. */
	private static final FirstPersonClip FP_GOBBLE = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, -16, -44, 0, -0.38f, 0.12f, 0.05f), fp(40000, -16, -44, 0, -0.38f, 0.12f, 0.05f)).both();
	private static final FirstPersonClip FP_GOBBLE_END = FirstPersonClip.of(fp(0, -16, -44, 0, -0.38f, 0.12f, 0.05f), fp(6, 22, -30, 0, -0.1f, -0.3f, 0.1f), fp(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BELLY = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(4, 22, -30, 0, -0.1f, -0.32f, 0.1f), fp(8, 26, -24, 0, -0.06f, -0.34f, 0.1f),
			fp(12, 22, -34, 0, -0.14f, -0.32f, 0.1f), fp(17, 0, 0, 0, 0, 0, 0)).both();
	/** The Mega Loogie: the hands spread as he swells, then thrown down with the heave. */
	private static final FirstPersonClip FP_MEGA = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(13, -22, -36, 0, -0.32f, 0.16f, 0.08f), fp(15, 40, -8, 0, -0.04f, -0.42f, 0.2f),
			fp(22, 30, -10, 0, -0.06f, -0.32f, 0.14f), fp(30, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FRENZY = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(3, -55, 10, 0, 0.06f, 0.34f, 0f), fp(7, -45, 10, 0, 0.06f, 0.22f, 0f), fp(11, -55, 10, 0, 0.06f, 0.34f, 0f),
			fp(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHOMP = FirstPersonClip.of(fp(0, 0, 0, 0, 0, 0, 0), fp(2, 18, -8, 0, -0.04f, -0.2f, 0.08f), fp(8, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		// v0.17.2 (user: "remove the pixel omnitrix from his skin model and replace it with our omnitrix badge please"): where the skin
		// painted it, the outside of his left wrist
		return BADGE;
	}

	@Override
	public int badgeBone() {
		return BADGE_LEFT_ARM;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & UpchuckKit.F_ZIP) != 0) {
			// hauled along by his tongues: stretched out flat, arms back, legs trailing
			m.rightArm.xRot = 0.9f;
			m.leftArm.xRot = 0.9f;
			m.rightArm.zRot = 0.4f;
			m.leftArm.zRot = -0.4f;
			m.rightLeg.xRot = 0.55f + Mth.sin(age * 0.8f) * 0.12f;
			m.leftLeg.xRot = 0.3f - Mth.sin(age * 0.8f) * 0.12f;
			return true;
		}
		boolean played = switch (animId) {
			case UpchuckKit.ANIM_GOBBLE_END -> follow(m, GOBBLE_END, ticks);
			case UpchuckKit.ANIM_LAND -> follow(m, LAND, ticks);
			default -> {
				float[][] clip = switch (animId) {
					case UpchuckKit.ANIM_SPIT -> SPIT;
					case UpchuckKit.ANIM_CURVE -> CURVE;
					case UpchuckKit.ANIM_SLICK -> SLICK;
					case UpchuckKit.ANIM_TONGUE -> TONGUE_OUT;
					case UpchuckKit.ANIM_GULP -> GULP;
					case UpchuckKit.ANIM_ROCKET -> (flags & UpchuckKit.F_ROCKET) != 0 ? ROCKET : null;
					case UpchuckKit.ANIM_EAT -> EAT;
					case UpchuckKit.ANIM_GOBBLE -> GOBBLE;
					case UpchuckKit.ANIM_DIGEST -> DIGEST;
					case UpchuckKit.ANIM_MEGA -> MEGA;
					case UpchuckKit.ANIM_FRENZY -> FRENZY;
					case UpchuckKit.ANIM_CHOMP -> CHOMP;
					default -> null;
				};
				yield clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
			}
		};
		if ((flags & UpchuckKit.F_GOBBLE) != 0) {
			if (!played) {
				// another move played over it and finished: back to the open stance while C is still down
				QuirkKeyframes.apply(m, QuirkKeyframes.sample(GOBBLE, 10f), 1f);
				played = true;
			}
			// the big breath in: a shudder through the arms
			m.rightArm.zRot += Mth.sin(age * 1.7f) * 0.03f;
			m.leftArm.zRot -= Mth.sin(age * 1.7f) * 0.03f;
			return true;
		}
		if (!played && (flags & UpchuckKit.F_FRENZY) != 0) {
			// a Feeding Frenzy: hunched forward, arms out, itching to eat
			m.rightArm.zRot += 0.3f;
			m.leftArm.zRot -= 0.3f;
			m.rightArm.xRot -= 0.5f + Mth.sin(age * 0.9f) * 0.2f;
			m.leftArm.xRot -= 0.5f - Mth.sin(age * 0.9f) * 0.2f;
		}
		if (!played && (flags & UpchuckKit.F_FULL) != 0) {
			// stuffed: arms out round the belly, a waddle
			m.rightArm.zRot += 0.35f;
			m.leftArm.zRot -= 0.35f;
			m.body.zRot = Mth.sin(age * 0.25f) * 0.06f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case UpchuckKit.ANIM_SPIT -> FP_SPIT;
			case UpchuckKit.ANIM_CURVE -> FP_CURVE;
			case UpchuckKit.ANIM_SLICK -> FP_SLICK;
			case UpchuckKit.ANIM_TONGUE -> FP_TONGUE;
			case UpchuckKit.ANIM_ROCKET -> FP_ROCKET;
			case UpchuckKit.ANIM_LAND -> FP_LAND;
			case UpchuckKit.ANIM_EAT -> FP_EAT;
			case UpchuckKit.ANIM_GOBBLE -> FP_GOBBLE;
			case UpchuckKit.ANIM_GOBBLE_END -> FP_GOBBLE_END;
			case UpchuckKit.ANIM_GULP, UpchuckKit.ANIM_DIGEST -> FP_BELLY;
			case UpchuckKit.ANIM_MEGA -> FP_MEGA;
			case UpchuckKit.ANIM_FRENZY -> FP_FRENZY;
			case UpchuckKit.ANIM_CHOMP -> FP_CHOMP;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the face and the belly

	/** How far his mouth is open right now, 0..1: wide for Gobble, a beat for a spit, a chomp, a lick of the tongues. */
	private static float mouthOpen(int flags, int animId, float t, float age) {
		if ((flags & UpchuckKit.F_GOBBLE) != 0) {
			return 0.92f + 0.08f * Mth.sin(age * 0.6f);
		}
		if ((flags & UpchuckKit.F_ZIP) != 0) {
			return 0.7f;
		}
		float o = switch (animId) {
			case UpchuckKit.ANIM_SPIT, UpchuckKit.ANIM_CURVE, UpchuckKit.ANIM_SLICK -> bump(t, 1f, 8f) * 0.85f;
			case UpchuckKit.ANIM_TONGUE -> bump(t, 1f, 13f) * 0.7f;
			case UpchuckKit.ANIM_EAT -> bump(t, 1.5f, 5f);
			case UpchuckKit.ANIM_GULP -> bump(t, 2f, 7f);
			case UpchuckKit.ANIM_ROCKET -> bump(t, 1f, 14f) * 0.8f;
			case UpchuckKit.ANIM_CHOMP -> bump(t, 1f, 4f);
			case UpchuckKit.ANIM_GOBBLE_END -> Mth.clamp(1f - t / 2f, 0f, 1f);
			case UpchuckKit.ANIM_FRENZY -> bump(t, 2f, 16f) * 0.7f;
			case UpchuckKit.ANIM_MEGA -> t < UpchuckKit.MEGA_WINDUP ? Mth.clamp(t / 14f, 0f, 1f) * 0.55f : bump(t - UpchuckKit.MEGA_WINDUP, 1f, 12f);
			default -> 0f;
		};
		if ((flags & UpchuckKit.F_FRENZY) != 0) {
			o = Math.max(o, 0.3f + 0.3f * Mth.sin(age * 0.8f)); // chewing at nothing
		}
		return o;
	}

	/** Up over {@code in} ticks, back down by {@code end}. */
	private static float bump(float t, float in, float end) {
		return Mth.clamp(Math.min(t / in, (end - t) / 2f), 0f, 1f);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		var s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float open = mouthOpen(s.kitFlags, s.animId, t, age);
		if (open > 0.02f) {
			AlienParts.on(pose, model.head, () -> mouth(pose, buffers, light, open));
		}
		int gut = UpchuckKit.gut(p);
		boolean gulped = (s.kitFlags & UpchuckKit.F_GULPED) != 0;
		float swell = Math.max(gulped ? 1f : 0f, (gut - 30) / 70f);
		if (s.animId == UpchuckKit.ANIM_MEGA) {
			// the Mega Loogie: he swells as it comes up, and empties at once
			float k = t < UpchuckKit.MEGA_WINDUP ? t / UpchuckKit.MEGA_WINDUP : 1f - (t - UpchuckKit.MEGA_WINDUP) / 3f;
			swell = Math.max(k <= 1f ? k * 1.25f : 0f, t < UpchuckKit.MEGA_WINDUP ? swell : 0f);
		}
		if (swell <= 0f) {
			return;
		}
		// a creature inside squirms
		float squirm = gulped ? Mth.sin(age * 0.9f) * 0.35f : 0f;
		float swellBy = swell;
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
			// the skin's own belly green (the body's front, about texel 24, 27)
			belly(pose, vc, light, squirm, 7.6f, -1.6f, 3.5f + 1.0f * swellBy, 3.6f + 1.4f * swellBy, 1.0f + 2.8f * swellBy, 24.5f / 64f, 27.5f / 64f);
		});
	}

	/** A smooth bulge on the front of his body: a half ellipsoid in pixels, lit by its own normals. */
	private static void belly(PoseStack pose, VertexConsumer vc, int light, float cx, float cy, float cz, float rx, float ry, float rz, float u, float v) {
		PoseStack.Pose last = pose.last();
		int rings = 10, segs = 18;
		float s = 1f / 16f;
		for (int i = 0; i < rings; i++) {
			float a0 = Mth.HALF_PI * i / rings, a1 = Mth.HALF_PI * (i + 1) / rings; // from the tip of the bulge out to its rim
			for (int j = 0; j < segs; j++) {
				float b0 = Mth.TWO_PI * j / segs, b1 = Mth.TWO_PI * (j + 1) / segs;
				float[][] q = { { a0, b0 }, { a1, b0 }, { a1, b1 }, { a0, b1 } };
				for (float[] c : q) {
					float nx = Mth.sin(c[0]) * Mth.cos(c[1]), ny = Mth.sin(c[0]) * Mth.sin(c[1]), nz = -Mth.cos(c[0]);
					vc.addVertex(last, (cx + nx * rx) * s, (cy + ny * ry) * s, (cz + nz * rz) * s).setColor(255, 255, 255, 255).setUv(u, v)
							.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
				}
			}
		}
	}

	/** Colours of his open mouth: the dark throat, the pink tongue, the teeth, the green lip. */
	private static final int THROAT = 0x2A0A12, MOUTH_PINK = 0xD8607A, TEETH = 0xF0F0E0, LIP = 0x3E8A20;

	/** His mouth, open by {@code open}: low on the face, drawn just proud of the head: lip, throat, top teeth, the tongue on the bottom. */
	private static void mouth(PoseStack pose, MultiBufferSource buffers, int light, float open) {
		VertexConsumer vc = PyroDraw.solidBuffer(buffers);
		float cy = -2.1f + 0.2f * open, rx = 1.6f + 2.1f * open, ry = 0.3f + 1.7f * open;
		float z = -4.72f;
		ellipse(pose, vc, light, 0f, cy, z, rx + 0.35f, ry + 0.32f, LIP);
		ellipse(pose, vc, light, 0f, cy, z - 0.03f, rx, ry, THROAT);
		if (open > 0.3f) {
			// the top row: a pale band following the top of the opening
			clippedBand(pose, vc, light, cy, rx, ry, z - 0.06f, TEETH);
			ellipse(pose, vc, light, 0f, cy + ry * 0.55f, z - 0.06f, rx * 0.6f, ry * 0.4f, MOUTH_PINK);
		}
	}

	private static void ellipse(PoseStack pose, VertexConsumer vc, int light, float cx, float cy, float z, float rx, float ry, int rgb) {
		PoseStack.Pose last = pose.last();
		int segs = 22;
		float s = 1f / 16f;
		for (int j = 0; j < segs; j++) {
			float a = Mth.TWO_PI * j / segs, b = Mth.TWO_PI * (j + 1) / segs;
			face(vc, last, cx * s, cy * s, z * s, rgb, light);
			face(vc, last, (cx + Mth.cos(a) * rx) * s, (cy + Mth.sin(a) * ry) * s, z * s, rgb, light);
			face(vc, last, (cx + Mth.cos(b) * rx) * s, (cy + Mth.sin(b) * ry) * s, z * s, rgb, light);
			face(vc, last, cx * s, cy * s, z * s, rgb, light);
		}
	}

	/** The arc of the top of the opening, a little over a pixel deep. */
	private static void clippedBand(PoseStack pose, VertexConsumer vc, int light, float cy, float rx, float ry, float z, int rgb) {
		PoseStack.Pose last = pose.last();
		int segs = 14;
		float s = 1f / 16f, depth = Math.min(ry * 0.55f, 1.1f);
		for (int j = 0; j < segs; j++) {
			float a = Mth.PI + Mth.PI * j / segs, b = Mth.PI + Mth.PI * (j + 1) / segs;
			float ax = Mth.cos(a) * rx, ay = cy + Mth.sin(a) * ry, bx = Mth.cos(b) * rx, by = cy + Mth.sin(b) * ry;
			float ax2 = Mth.cos(a) * rx * 0.97f, bx2 = Mth.cos(b) * rx * 0.97f;
			face(vc, last, ax * s, ay * s, z * s, rgb, light);
			face(vc, last, bx * s, by * s, z * s, rgb, light);
			face(vc, last, bx2 * s, (by + depth) * s, z * s, rgb, light);
			face(vc, last, ax2 * s, (ay + depth) * s, z * s, rgb, light);
		}
	}

	private static void face(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int rgb, int light) {
		vc.addVertex(last, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light).setNormal(last, 0f, 0f, -1f);
	}

	// ---------------------------------------------------------------- the HUD: his Gut

	@Override
	public int hudHeight() {
		return 10;
	}

	@Override
	public void hud(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.player.LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		int gut = UpchuckKit.gut(p);
		float rich = (UpchuckKit.richness(p) - 1f) / UpchuckKit.RICH_BONUS;
		boolean full = gut > UpchuckKit.FULL_CHEEKS;
		boolean flash = (p.level().getGameTime() / 6) % 2 == 0;
		int col = 0xFF000000 | PyroDraw.lerpRgb(0x7CD83A, 0xF0D040, rich);
		String value = full ? (flash ? "FULL" : String.valueOf(gut)) : String.valueOf(gut);
		int valueCol = full ? 0xFFF0C060 : 0xFFE2E2E2;
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.upchuck.meter"),
				gut / (float) UpchuckKit.MAX_GUT, col, value, valueCol);
	}

	// ---------------------------------------------------------------- the tongues

	/** His mouth: in first person just under the view, otherwise low on his big face, by his body's facing and size. */
	public static Vec3 mouth(Player p, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			Vec3 eye = p.getEyePosition(partial);
			return eye.add(p.getViewVector(partial).scale(0.35)).add(0, -0.22, 0);
		}
		float yaw = Mth.lerp(partial, p.yHeadRotO, p.yHeadRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		float s = p.getScale();
		return p.getPosition(partial).add(0, 1.42 * s, 0).add(fwd.scale(0.26 * s));
	}

	/** One of his three tongues ({@code size()} = which), from his mouth to the creature colour - 1 or the point here. */
	private static void tongue(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		Minecraft mc = Minecraft.getInstance();
		if (!(e.getOwner() instanceof Player owner)) {
			return;
		}
		int which = Math.round(e.size());
		Vec3 start = mouth(owner, partial);
		Vec3 end = e.getPosition(partial);
		if (e.colour() > 0) {
			Entity t = mc.level.getEntity(e.colour() - 1);
			if (t == null) {
				return;
			}
			end = t.getPosition(partial).add(0, t.getBbHeight() * (0.35 + 0.2 * which), 0);
		}
		float age = e.tickCount + partial;
		float reach = Mth.clamp(age / 3f, 0.08f, 1f);
		// the three spread a little where they stick
		Vec3 d = end.subtract(start);
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		end = end.add(side.scale((which - 1) * 0.18));
		List<Vec3> pts = path(start, end, reach, age, which);
		Vec3 at = e.getPosition(partial);
		Vec3 cam = BrainstormFx.cam();
		pose.pushPose();
		pose.translate(cam.x - at.x, cam.y - at.y, cam.z - at.z);
		List<Vec3> rel = new ArrayList<>(pts.size());
		for (Vec3 v : pts) {
			rel.add(v.subtract(cam));
		}
		int light = LevelRenderer.getLightColor(owner.level(), BlockPos.containing(owner.position().add(0, 0.5, 0)));
		tube(pose, PyroDraw.solidBuffer(buffers), rel, s -> 0.05 * (1.0 - 0.3 * s) + (s > 0.92 ? 0.03 : 0.0), light);
		pose.popPose();
	}

	/**
	 * A Feeding Frenzy: his three tongues out of his mouth, lashing about for something to eat. World space: {@code pose} is the world
	 * stack, {@code cam} the camera.
	 */
	public static void frenzyTongues(PoseStack pose, MultiBufferSource buffers, Player p, float partial, float time, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
			return; // they would only fill his own view
		}
		Vec3 m = mouth(p, partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 side = look.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(look).normalize();
		int light = LevelRenderer.getLightColor(p.level(), BlockPos.containing(p.position().add(0, 0.5, 0)));
		for (int i = 0; i < 3; i++) {
			float ph = time * 0.21f + i * 2.1f;
			Vec3 end = m.add(look.scale(0.6 + 0.2 * Mth.sin(ph * 1.3f))).add(side.scale((i - 1) * 0.3 + 0.2 * Mth.sin(ph))).add(up.scale(0.25 * Mth.cos(ph * 0.8f) - 0.12));
			List<Vec3> rel = new ArrayList<>();
			for (Vec3 v : path(m, end, 1f, time, i)) {
				rel.add(v.subtract(cam));
			}
			tube(pose, PyroDraw.solidBuffer(buffers), rel, s -> 0.03 * (1.0 - 0.3 * s), light);
		}
	}

	/** A tongue's points: lashing out over its first ticks, a wavy curl that pulls taut as it reaches, a slight sag. */
	private static List<Vec3> path(Vec3 a, Vec3 b, float reach, float age, int seed) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		Vec3 dir = len < 1.0e-6 ? new Vec3(0, 0, 1) : d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		int n = Math.max(6, Math.min(36, (int) (len * 3)));
		double wig = Math.min(0.3, len * 0.03) * (1.4 - reach * 0.8);
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n * reach;
			double env = Math.sin(Math.PI * t);
			double w = Math.sin(t * Math.PI * 2 + age * 0.4 + seed * 2.1) * wig * env;
			double sag = Math.min(0.3, len * 0.02) * 4 * t * (1 - t);
			pts.add(a.add(d.scale(t)).add(side.scale(w)).add(up.scale(w * 0.5)).add(0, -sag, 0));
		}
		return pts;
	}

	/** A lit, flattened tube along {@code pts}: pink on top, darker underneath, a paler sticky tip. */
	private static void tube(PoseStack pose, VertexConsumer vc, List<Vec3> pts, java.util.function.DoubleUnaryOperator radius, int light) {
		int sides = 6;
		PoseStack.Pose last = pose.last();
		Vec3[] prev = null, prevN = null;
		for (int i = 0; i < pts.size(); i++) {
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			tan = tan.lengthSqr() < 1.0e-10 ? new Vec3(0, 1, 0) : tan.normalize();
			Vec3 n0 = Math.abs(tan.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
			Vec3 nx = tan.cross(n0).normalize();
			Vec3 ny = nx.cross(tan).normalize();
			double s = i / (double) Math.max(1, pts.size() - 1);
			double r = radius.applyAsDouble(s);
			Vec3[] ring = new Vec3[sides], norm = new Vec3[sides];
			for (int k = 0; k < sides; k++) {
				double a = k * Math.PI * 2 / sides;
				norm[k] = nx.scale(Math.cos(a)).add(ny.scale(Math.sin(a)));
				ring[k] = p.add(nx.scale(Math.cos(a) * r * 1.5)).add(ny.scale(Math.sin(a) * r * 0.7)); // flat, like a tongue
			}
			if (prev != null) {
				for (int k = 0; k < sides; k++) {
					int k1 = (k + 1) % sides;
					int c = s > 0.9 ? TIP : norm[k].y >= 0 ? TONGUE : TONGUE_DARK;
					v(vc, last, prev[k], prevN[k], c, light);
					v(vc, last, ring[k], norm[k], c, light);
					v(vc, last, ring[k1], norm[k1], c, light);
					v(vc, last, prev[k1], prevN[k1], c, light);
				}
			}
			prev = ring;
			prevN = norm;
		}
	}

	private static void v(VertexConsumer vc, PoseStack.Pose last, Vec3 p, Vec3 n, int colour, int light) {
		vc.addVertex(last, (float) p.x, (float) p.y, (float) p.z).setColor((colour >> 16) & 255, (colour >> 8) & 255, colour & 255, 255).setUv(0.5f, 0.5f)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, (float) n.x, (float) n.y, (float) n.z);
	}
}

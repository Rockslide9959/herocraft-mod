package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.eyeguy.EyeGuyFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.eyeguy.EyeGuyKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * v0.17.3 Eye Guy's client kit (user: "make aliens based on their abilities and stuff they do in the show please, replace the pixel
 * omnitrix on their skin with our omnitrix badge please"). The user's skin on the player body, the pixel Omnitrix on his belt buckle
 * painted out and the mod's badge on the buckle in its place. Every iris on him is in {@code eye_guy_glow.png} (white, gen_textures.js),
 * drawn full-bright and TINTED: a soft green at rest, and the colour of whatever beam he just fired -- green lasers, ice blue, orange
 * fire, the chest eye's blazing yellow-white -- flaring up as it leaves. His beams are {@link EyeGuyFx}; All-Seeing outlines every
 * creature near him through walls ({@link #glows}).
 */
public final class EyeGuyClient implements AlienClientKit {
	public static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/eye_guy.png");
	public static final ResourceLocation GLOW = ProjectHeroMod.id("textures/entity/omnitrix/eye_guy_glow.png");
	/** The belt-buckle badge (body-bone pixels): the buckle's middle (texels 23..24 x 29..30), out past the belt's jacket layer. */
	public static final float[] BADGE = { 0f, 10.0f, -2.32f, 2.6f };
	/** The irises at rest. */
	public static final int REST = 0x5FCF3A;

	private static boolean registered;

	public static void register() {
		AlienClients.register(Aliens.EYE_GUY.id(), new EyeGuyClient());
		if (!registered) {
			registered = true;
			EyeGuyFx.register();
		}
	}

	// ---------------------------------------------------------------- poses
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float rly, float rlz, float llx,
			float lly, float llz, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, rly, rlz, llx, lly, llz, hx);
	}

	/** Eye Laser: a sharp jut of the head into the shot, the shoulders squared. */
	private static final float[][] LASER = { QuirkKeyframes.rest(0), f(2, 0.2f, 0, 0.15f, 0.2f, 0, -0.15f, 0.1f, 0, -0.1f, 0, 0, 0.1f, 0, 0, 0.12f),
			QuirkKeyframes.rest(8) };
	/** Laser Volley: arms flung back and chest out, every eye open at once. */
	private static final float[][] VOLLEY = { QuirkKeyframes.rest(0), f(3, 0.6f, 0, 0.7f, 0.6f, 0, -0.7f, -0.25f, 0, -0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.2f),
			f(9, 0.5f, 0, 0.6f, 0.5f, 0, -0.6f, -0.2f, 0, -0.15f, 0, 0.1f, 0.15f, 0, -0.1f, -0.15f), QuirkKeyframes.rest(15) };
	/** Freeze / Fire Beam: braced, a hand steadying his brow as the beam pours out of his eyes. */
	private static final float[][] BEAM = { QuirkKeyframes.rest(0), f(3, -2.1f, -0.5f, 0, 0.3f, 0, -0.25f, 0.12f, 0, -0.35f, 0, 0, 0.3f, 0, 0, 0.1f),
			f(10, -2.0f, -0.5f, 0, 0.3f, 0, -0.25f, 0.1f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0.1f), QuirkKeyframes.rest(15) };
	/** Eye Dodge: tucked for the hop. */
	private static final float[][] DODGE = { QuirkKeyframes.rest(0), f(2, 0.5f, 0, 0.4f, 0.5f, 0, -0.4f, 0.3f, 0, -0.9f, 0, 0, -0.6f, 0, 0, 0),
			f(6, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.15f, 0, -0.4f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	/** The chest eye charging: arms thrown wide to bare it, leaning back into a planted stance. */
	private static final float[][] MEGA_CHARGE = { QuirkKeyframes.rest(0), f(6, -0.3f, 0, 1.3f, -0.3f, 0, -1.3f, -0.18f, 0, -0.3f, 0, 0.15f, 0.35f, 0, -0.15f, -0.2f),
			f(30, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.26f, 0, -0.4f, 0, 0.2f, 0.45f, 0, -0.2f, -0.28f) };
	/** The chest eye fires: the recoil shoves him back on his heels. */
	private static final float[][] MEGA = { f(0, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.26f, 0, -0.4f, 0, 0.2f, 0.45f, 0, -0.2f, -0.28f),
			f(3, -0.6f, 0, 1.7f, -0.6f, 0, -1.7f, -0.4f, 0, -0.5f, 0, 0.2f, 0.55f, 0, -0.2f, -0.4f),
			f(13, -0.5f, 0, 1.6f, -0.5f, 0, -1.6f, -0.34f, 0, -0.45f, 0, 0.2f, 0.5f, 0, -0.2f, -0.3f), QuirkKeyframes.rest(20) };
	/** Eye Flash: hunched, then everything thrown open. */
	private static final float[][] FLASH = { QuirkKeyframes.rest(0), f(3, -0.4f, 0.5f, -0.4f, -0.4f, -0.5f, 0.4f, 0.4f, 0, -0.2f, 0, 0, -0.2f, 0, 0, 0.3f),
			f(5, -0.5f, 0, 1.9f, -0.5f, 0, -1.9f, -0.3f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.5f), f(10, -0.4f, 0, 1.7f, -0.4f, 0, -1.7f, -0.2f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.3f),
			QuirkKeyframes.rest(15) };
	/** All-Seeing: a slow look round, a hand at his brow. */
	private static final float[][] SIGHT = { QuirkKeyframes.rest(0), f(4, -2.3f, -0.6f, 0, 0, 0, 0, 0, 0.35f, 0, 0, 0, 0, 0, 0, -0.1f),
			f(10, -2.3f, -0.6f, 0, 0, 0, 0, 0, -0.35f, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(15) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.5f, 0.15f, 0, 0.3f, 0, -0.1f, 0.12f, -0.45f, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_BEAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -40, 30, 10, 0.02f, 0.35f, -0.2f),
			FirstPersonClip.f(10, -40, 30, 10, 0.02f, 0.35f, -0.2f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -25, -20, 15, -0.1f, 0.2f, -0.1f),
			FirstPersonClip.f(30, -25, -20, 15, -0.1f, 0.2f, -0.1f), FirstPersonClip.f(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -14, 10, 0, 0.08f, 0.1f, -0.44f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public float[] badge() {
		return BADGE;
	}

	@Override
	public boolean glows(LocalPlayer viewer, Entity e) {
		return EyeGuyKit.seeing(viewer) && e instanceof LivingEntity le && le.isAlive() && !(e instanceof ArmorStand) && e != viewer
				&& e.distanceToSqr(viewer) <= EyeGuyKit.SIGHT_RANGE * EyeGuyKit.SIGHT_RANGE;
	}

	/**
	 * The irises' colour and brightness right now: the last beam's colour flaring as it fires and fading back to the resting green over
	 * half a second; the chest eye's charge building white-hot.
	 */
	public static int irisColour(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float charge = EyeGuyKit.megaCharge(p, partial);
		if (charge > 0f) {
			return PyroDraw.lerpRgb(REST, EyeGuyKit.MEGA, 0.4f + 0.6f * charge) | 0xFF000000;
		}
		int beam = switch (s.animId) {
			case EyeGuyKit.ANIM_LASER, EyeGuyKit.ANIM_VOLLEY -> EyeGuyKit.LASER;
			case EyeGuyKit.ANIM_FREEZE -> EyeGuyKit.ICE;
			case EyeGuyKit.ANIM_FIRE -> EyeGuyKit.FIRE;
			case EyeGuyKit.ANIM_MEGA -> EyeGuyKit.MEGA;
			case EyeGuyKit.ANIM_FLASH -> EyeGuyKit.FLASH;
			case EyeGuyKit.ANIM_SIGHT -> 0xC8FFB0;
			default -> REST;
		};
		float k = t < 0f ? 0f : Mth.clamp(1f - (t - 4f) / 10f, 0f, 1f);
		int c = PyroDraw.lerpRgb(REST, beam, k);
		if (EyeGuyKit.seeing(p)) {
			c = PyroDraw.lerpRgb(c, 0xD8FFC0, 0.35f + 0.1f * Mth.sin((p.tickCount + partial) * 0.3f));
		}
		return 0xFF000000 | c;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, irisColour(p, partial));
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case EyeGuyKit.ANIM_LASER -> LASER;
			case EyeGuyKit.ANIM_VOLLEY -> VOLLEY;
			case EyeGuyKit.ANIM_FREEZE, EyeGuyKit.ANIM_FIRE -> BEAM;
			case EyeGuyKit.ANIM_DODGE -> DODGE;
			case EyeGuyKit.ANIM_MEGA_CHARGE -> (Omnitrix.state(p).kitFlags & EyeGuyKit.F_MEGA) != 0 ? MEGA_CHARGE : null;
			case EyeGuyKit.ANIM_MEGA -> MEGA;
			case EyeGuyKit.ANIM_FLASH -> FLASH;
			case EyeGuyKit.ANIM_SIGHT -> SIGHT;
			case EyeGuyKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		return clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case EyeGuyKit.ANIM_FREEZE, EyeGuyKit.ANIM_FIRE, EyeGuyKit.ANIM_SIGHT -> FP_BEAM;
			case EyeGuyKit.ANIM_MEGA_CHARGE, EyeGuyKit.ANIM_FLASH, EyeGuyKit.ANIM_VOLLEY -> FP_WIDE;
			case EyeGuyKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}
}

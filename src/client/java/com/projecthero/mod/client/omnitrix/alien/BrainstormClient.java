package com.projecthero.mod.client.omnitrix.alien;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.brainstorm.BrainstormKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.2: Brainstorm's client kit. v0.17.2 revamp (user: "delete all the alien models and only use the skins i provided please no
 * extra things untill i say"): ONLY the player body at 0.9 in the user's 128x128 HD skin (the renderer's UVs are normalised, so the
 * HD sheet maps straight onto the wide-arm model) -- no head shell, brain dome, back shell, pincers or crab legs any more. A pose and
 * a first-person clip for every move; his lightning (from the top of his head, where the show's skull opens) and the lift disc /
 * force field are {@link BrainstormFx}.
 */
public final class BrainstormClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/brainstorm.png");

	public static void register() {
		AlienClients.register(Aliens.BRAINSTORM.id(), new BrainstormClient());
		BrainstormFx.register();
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, hx);
	}

	private static final float[][] BOLT = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.1f, 0, 0.2f, 0, 0, 0.05f, -0.15f), f(6, -1.55f, 0.1f, 0, 0.2f, 0, 0, 0.05f, -0.15f),
			QuirkKeyframes.rest(10) };
	private static final float[][] CHAIN = { QuirkKeyframes.rest(0), f(3, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.1f, -0.2f),
			f(10, -1.45f, -0.25f, 0, -1.45f, 0.25f, 0, 0.1f, -0.2f), QuirkKeyframes.rest(15) };
	private static final float[][] PINCER = { QuirkKeyframes.rest(0), f(2, -2.3f, 0, 0.3f, 0, 0, 0, -0.1f, 0), f(5, -1.1f, 0, 0, 0, 0, 0, 0.25f, 0.2f),
			QuirkKeyframes.rest(11) };
	private static final float[][] ORB = { QuirkKeyframes.rest(0), f(3, -1.3f, -0.45f, 0, -1.3f, 0.45f, 0, 0, 0), f(7, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.1f, 0),
			QuirkKeyframes.rest(13) };
	private static final float[][] HOP = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, 0.6f, 0, 0.4f, 0.6f, 0, -0.4f, 0.2f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0),
			QuirkKeyframes.f(12, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.1f, 0, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0), QuirkKeyframes.rest(16) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), f(1, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.55f, -0.3f), f(6, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.5f, -0.3f),
			QuirkKeyframes.rest(10) };
	private static final float[][] DISCHARGE = { QuirkKeyframes.rest(0), f(3, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.15f, -0.4f),
			f(9, -0.5f, 0, 1.4f, -0.5f, 0, -1.4f, -0.15f, -0.4f), QuirkKeyframes.rest(14) };
	private static final float[][] MEGA = { QuirkKeyframes.rest(0), f(4, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, -0.1f, 0), f(20, -1.55f, -0.15f, 0, -1.55f, 0.15f, 0, -0.1f, 0),
			f(23, -1.7f, -0.1f, 0, -1.7f, 0.1f, 0, -0.3f, -0.1f), f(30, -1.6f, -0.1f, 0, -1.6f, 0.1f, 0, -0.1f, 0), QuirkKeyframes.rest(35) };
	private static final float[][] ANALYZE = { QuirkKeyframes.rest(0), f(3, -2.4f, -0.6f, 0, 0.1f, 0, 0, 0, -0.1f), f(20, -2.4f, -0.6f, 0, 0.1f, 0, 0, 0, -0.1f),
			QuirkKeyframes.rest(26) };
	private static final float[][] FIELD = { QuirkKeyframes.rest(0), f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0, -0.2f), f(10, -0.25f, 0, 1.25f, -0.25f, 0, -1.25f, 0, -0.2f),
			QuirkKeyframes.rest(16) };
	private static final float[][] STORM = { QuirkKeyframes.rest(0), f(4, -2.9f, 0, 0.35f, -2.9f, 0, -0.35f, -0.2f, -0.6f),
			f(30, -2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.2f, -0.6f), QuirkKeyframes.rest(36) };
	private static final float[][] CAGE = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.35f, 0, -0.3f, 0, 0, 0.1f, 0), f(6, -2.3f, -0.2f, 0, -0.3f, 0, 0, 0.05f, -0.1f),
			QuirkKeyframes.rest(13) };
	/** v0.17.2 revamp: riding the lift disc -- arms out for balance, knees a little bent. */
	private static final float[][] LIFT = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(4, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0, -0.25f, 0, 0.1f, -0.25f, 0, -0.1f, 0.15f),
			QuirkKeyframes.f(100, -0.3f, 0, 0.9f, -0.3f, 0, -0.9f, 0.1f, 0, -0.25f, 0, 0.1f, -0.25f, 0, -0.1f, 0.15f) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.2f, 0, 0, 0, 0, 0.1f, 0), QuirkKeyframes.rest(7) };

	// ---------------------------------------------------------------- first person (tick, pitch, yaw, roll, x, y, z)
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 12, 0, 0.08f, 0.15f, -0.25f),
			FirstPersonClip.f(7, -16, 10, 0, 0.08f, 0.14f, -0.24f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 18, 0, 0.12f, 0.12f, -0.25f),
			FirstPersonClip.f(10, -14, 16, 0, 0.12f, 0.12f, -0.24f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SNAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -60, 10, 10, 0.05f, 0.45f, 0.1f),
			FirstPersonClip.f(5, 15, 15, 0, 0.1f, -0.15f, -0.35f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -10, 22, 0, 0.16f, 0.1f, -0.15f),
			FirstPersonClip.f(20, -12, 22, 0, 0.17f, 0.12f, -0.16f), FirstPersonClip.f(23, -20, 18, 0, 0.12f, 0.15f, -0.35f), FirstPersonClip.f(35, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, -25, 20, -0.15f, 0.25f, 0.05f),
			FirstPersonClip.f(9, -25, -25, 20, -0.15f, 0.25f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -75, 10, 0, 0.05f, 0.6f, 0.1f),
			FirstPersonClip.f(30, -78, 10, 0, 0.05f, 0.62f, 0.1f), FirstPersonClip.f(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TEMPLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -55, 40, 15, 0.25f, 0.45f, 0.1f),
			FirstPersonClip.f(20, -55, 40, 15, 0.25f, 0.45f, 0.1f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LOW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(10, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.25f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & BrainstormKit.F_LIFT) != 0) {
			return QuirkKeyframes.play(m, LIFT, Math.min(ticks, 4f), 1f);
		}
		float[][] clip = switch (animId) {
			case BrainstormKit.ANIM_BOLT -> BOLT;
			case BrainstormKit.ANIM_CHAIN -> CHAIN;
			case BrainstormKit.ANIM_PINCER -> PINCER;
			case BrainstormKit.ANIM_ORB -> ORB;
			case BrainstormKit.ANIM_HOP -> HOP;
			case BrainstormKit.ANIM_DASH -> DASH;
			case BrainstormKit.ANIM_DISCHARGE -> DISCHARGE;
			case BrainstormKit.ANIM_MEGA -> MEGA;
			case BrainstormKit.ANIM_ANALYZE -> ANALYZE;
			case BrainstormKit.ANIM_FIELD -> FIELD;
			case BrainstormKit.ANIM_STORM -> STORM;
			case BrainstormKit.ANIM_CAGE -> CAGE;
			case BrainstormKit.ANIM_PUNCH -> PUNCH;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// a crab's stance: claws held a little out and forward
			m.rightArm.xRot -= 0.25f;
			m.leftArm.xRot -= 0.25f;
			m.rightArm.zRot += 0.15f;
			m.leftArm.zRot -= 0.15f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BrainstormKit.ANIM_BOLT, BrainstormKit.ANIM_CAGE -> FP_POINT;
			case BrainstormKit.ANIM_CHAIN, BrainstormKit.ANIM_ORB -> FP_BOTH;
			case BrainstormKit.ANIM_PINCER -> FP_SNAP;
			case BrainstormKit.ANIM_MEGA -> FP_CHARGE;
			case BrainstormKit.ANIM_DISCHARGE, BrainstormKit.ANIM_FIELD, BrainstormKit.ANIM_LIFT -> FP_WIDE;
			case BrainstormKit.ANIM_STORM -> FP_UP;
			case BrainstormKit.ANIM_ANALYZE -> FP_TEMPLE;
			case BrainstormKit.ANIM_HOP, BrainstormKit.ANIM_DASH -> FP_LOW;
			case BrainstormKit.ANIM_PUNCH -> FP_PUNCH;
			default -> null;
		};
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.1f, 3.0f };
	}
}

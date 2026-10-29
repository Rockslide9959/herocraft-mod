package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.stinkfly.StinkflyAnimatable;
import com.projecthero.mod.client.omnitrix.alien.stinkfly.StinkflyFirstPerson;
import com.projecthero.mod.client.omnitrix.alien.stinkfly.StinkflyModel;
import com.projecthero.mod.client.omnitrix.alien.stinkfly.StinkflyRenderer;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.1: Stinkfly's client side -- a fully custom body ({@link #customBody}, Wildmutt's framework; user: a proper,
 * good-looking body from the reference images): the GeckoLib insect ({@link StinkflyRenderer}: black head with four eye-stalks,
 * white-and-black suit, four thin green limbs, the white tail and stinger fin, two pairs of see-through wings; clips picked by
 * {@link StinkflyAnimatable}) in place of the player model, his own clawed mantis hands in first person
 * ({@link StinkflyFirstPerson}, both always in view), a first-person clip for every move, the goo drips and the wing shimmer.
 * (Before v0.17.1's body: the player model with a skin, wing quads and limb cubes.)
 */
public final class StinkflyClient implements AlienClientKit {
	// first-person clips for the moves: {tick, pitch, yaw, roll, x, y, z}
	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 10, -8, 0, 0.04f, -0.08f, 0.08f),
			FirstPersonClip.f(4, -6, 4, 0, 0.02f, 0.03f, -0.04f), FirstPersonClip.f(9, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BARRAGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 8, -12, 0, 0.06f, -0.1f, 0.06f),
			FirstPersonClip.f(5, 4, 10, 0, -0.02f, -0.08f, 0.06f), FirstPersonClip.f(8, 8, -6, 0, 0.04f, -0.1f, 0.06f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_NET = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -30, -30, 20, 0.18f, 0.22f, 0.12f),
			FirstPersonClip.f(6, 14, 28, -8, 0.22f, -0.12f, -0.12f), FirstPersonClip.f(9, 10, 22, -6, 0.2f, -0.1f, -0.08f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GAS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 18, -24, 10, 0.2f, -0.18f, 0.08f),
			FirstPersonClip.f(20, 18, -26, 12, 0.22f, -0.2f, 0.08f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, -18, 0, 0.12f, 0.12f, 0.1f),
			FirstPersonClip.f(5, 20, 24, 0, 0.16f, -0.24f, 0.02f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -8, 10, 0, 0.04f, 0.04f, -0.1f),
			FirstPersonClip.f(7, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GUST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, -40, 28, -0.22f, 0.2f, 0.06f),
			FirstPersonClip.f(6, -24, 30, 0, 0.14f, 0.16f, -0.16f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_STORM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -40, -12, 16, -0.06f, 0.3f, 0.1f),
			FirstPersonClip.f(20, -44, -12, 16, -0.06f, 0.32f, 0.1f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 16, 18, 0, -0.1f, -0.14f, 0.18f),
			FirstPersonClip.f(10, 16, 18, 0, -0.1f, -0.14f, 0.18f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -10, 14, 0, 0.1f, 0.1f, -0.14f),
			FirstPersonClip.f(56, -10, 14, 0, 0.1f, 0.1f, -0.14f), FirstPersonClip.f(60, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TAKEOFF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, 0, 0, 0, -0.2f, 0.06f),
			FirstPersonClip.f(6, -40, -10, 14, -0.04f, 0.3f, 0.06f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(1, 30, 22, -10, 0.18f, -0.3f, -0.04f),
			FirstPersonClip.f(7, 26, 20, -8, 0.16f, -0.26f, -0.02f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LIFT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, 34, -16, 0, -0.06f, -0.3f, -0.08f),
			FirstPersonClip.f(8, 26, -16, 0, -0.06f, -0.24f, -0.04f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return StinkflyModel.TEXTURE;
	}

	// ---------------------------------------------------------------- the custom body

	@Override
	public boolean customBody() {
		return true;
	}

	@Override
	public void buildBody(EntityRendererProvider.Context context) {
		StinkflyRenderer.rebuild(context);
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		StinkflyRenderer r = StinkflyRenderer.get();
		if (r != null) {
			r.render(p, yaw, partial, pose, buffers, light);
		}
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		StinkflyFirstPerson.render(pose, buffers, light, p, right);
		return true;
	}

	@Override
	public boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case StinkflyKit.ANIM_SHOT -> FP_SPIT;
			case StinkflyKit.ANIM_BARRAGE -> FP_BARRAGE;
			case StinkflyKit.ANIM_NET -> FP_NET;
			case StinkflyKit.ANIM_GAS -> FP_GAS;
			case StinkflyKit.ANIM_STAB -> FP_STAB;
			case StinkflyKit.ANIM_JAB -> FP_JAB;
			case StinkflyKit.ANIM_GUST -> FP_GUST;
			case StinkflyKit.ANIM_STORM -> FP_STORM;
			case StinkflyKit.ANIM_DASH -> FP_DASH;
			case StinkflyKit.ANIM_DIVE -> FP_DIVE;
			case StinkflyKit.ANIM_TAKEOFF -> FP_TAKEOFF;
			case StinkflyKit.ANIM_SPLASH -> FP_SPLASH;
			case StinkflyKit.ANIM_LIFT -> FP_LIFT;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		if (r.nextInt(25) == 0) {
			level.addParticle(ParticleTypes.ITEM_SLIME, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() * 0.4,
					p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, -0.05, 0);
		}
		if (StinkflyAnimatable.flying(p) && r.nextInt(3) == 0) {
			// the wing shimmer
			level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 1.4, p.getY() + p.getBbHeight() * 0.7,
					p.getZ() + (r.nextDouble() - 0.5) * 1.4, 0, 0, 0);
		}
	}
}

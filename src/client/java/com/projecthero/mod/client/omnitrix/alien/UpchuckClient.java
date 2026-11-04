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
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg x, lLeg x)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, 0);
	}

	/** A loogie: reared back, then the whole body heaves forward. */
	private static final float[][] SPIT = { QuirkKeyframes.rest(0), f(3, 0.6f, 0, 0.5f, 0.6f, 0, -0.5f, -0.3f, 0, 0, 0),
			f(6, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.45f, 0, 0.2f, -0.2f), QuirkKeyframes.rest(12) };
	/** Tongues out: leaning in, arms braced back. */
	private static final float[][] TONGUE_OUT = { QuirkKeyframes.rest(0), f(2, 0.7f, 0, 0.4f, 0.7f, 0, -0.4f, 0.35f, 0, -0.3f, 0.3f),
			f(10, 0.7f, 0, 0.4f, 0.7f, 0, -0.4f, 0.35f, 0, -0.3f, 0.3f), QuirkKeyframes.rest(14) };
	/** A gulp: thrown back, hands on his belly. */
	private static final float[][] GULP = { QuirkKeyframes.rest(0), f(3, -0.6f, 0.3f, 0, -0.6f, -0.3f, 0, -0.35f, 0, 0, 0),
			f(8, -0.7f, 0.5f, 0, -0.7f, -0.5f, 0, 0.1f, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] ROCKET = { QuirkKeyframes.rest(0), f(2, 1.2f, 0, 0.5f, 1.2f, 0, -0.5f, 0.6f, 0, 0.8f, 0.8f),
			f(14, 1.0f, 0, 0.6f, 1.0f, 0, -0.6f, 0.5f, 0, 0.7f, 0.7f), QuirkKeyframes.rest(22) };
	/** Devour: lunging at the block, chewing. */
	private static final float[][] EAT = { QuirkKeyframes.rest(0), f(2, -1.0f, 0, 0.2f, -1.0f, 0, -0.2f, 0.5f, 0, 0, 0),
			f(5, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.25f, 0, 0, 0), QuirkKeyframes.rest(9) };
	/** Gobble: arms flung wide, mouth open to the world. */
	private static final float[][] GOBBLE = { QuirkKeyframes.rest(0), f(3, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.25f, 0, 0, 0),
			f(56, -0.4f, 0, 1.4f, -0.4f, 0, -1.4f, -0.25f, 0, 0, 0), QuirkKeyframes.rest(62) };
	/** Digest: a belly rub. */
	private static final float[][] DIGEST = { QuirkKeyframes.rest(0), f(3, -0.7f, 0.5f, 0, -0.7f, -0.5f, 0, 0, 0, 0, 0),
			f(7, -0.5f, 0.6f, 0, -0.5f, -0.6f, 0, 0, 0, 0, 0), f(11, -0.7f, 0.5f, 0, -0.7f, -0.5f, 0, 0, 0, 0, 0), QuirkKeyframes.rest(16) };
	/** The Mega Loogie: a long rear back, the heave, then reeling from the recoil. */
	private static final float[][] MEGA = { QuirkKeyframes.rest(0), f(6, -0.5f, 0, 0.9f, -0.5f, 0, -0.9f, -0.5f, 0, 0.3f, -0.3f),
			f(9, 1.1f, 0, 0.4f, 1.1f, 0, -0.4f, 0.7f, 0, 0.3f, -0.3f), f(16, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, 0.2f, 0, 0, 0), QuirkKeyframes.rest(24) };
	private static final float[][] FRENZY = { QuirkKeyframes.rest(0), f(3, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.2f, 0, 0, 0),
			f(14, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.2f, 0, 0, 0), QuirkKeyframes.rest(20) };
	private static final float[][] CHOMP = { QuirkKeyframes.rest(0), f(2, -0.3f, 0, 0.3f, -0.3f, 0, -0.3f, 0.4f, 0, 0, 0), QuirkKeyframes.rest(7) };

	private static final FirstPersonClip FP_SPIT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 30, 0, 0, 0f, -0.25f, 0.1f),
			FirstPersonClip.f(10, 30, 0, 0, 0f, -0.25f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 40, 0, 0.2f, 0.1f, 0f),
			FirstPersonClip.f(14, -20, 40, 0, 0.2f, 0.1f, 0f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BELLY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -30, 0, -0.1f, -0.3f, 0.1f),
			FirstPersonClip.f(10, 20, -30, 0, -0.1f, -0.3f, 0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();

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
			// hauled along by his tongues: arms back, legs trailing
			m.rightArm.xRot = 0.9f;
			m.leftArm.xRot = 0.9f;
			m.rightArm.zRot = 0.4f;
			m.leftArm.zRot = -0.4f;
			m.rightLeg.xRot = 0.5f;
			m.leftLeg.xRot = 0.3f;
			return true;
		}
		float[][] clip = switch (animId) {
			case UpchuckKit.ANIM_SPIT -> SPIT;
			case UpchuckKit.ANIM_TONGUE -> TONGUE_OUT;
			case UpchuckKit.ANIM_GULP -> GULP;
			case UpchuckKit.ANIM_ROCKET -> ROCKET;
			case UpchuckKit.ANIM_EAT -> EAT;
			case UpchuckKit.ANIM_GOBBLE -> GOBBLE;
			case UpchuckKit.ANIM_DIGEST -> DIGEST;
			case UpchuckKit.ANIM_MEGA -> MEGA;
			case UpchuckKit.ANIM_FRENZY -> FRENZY;
			case UpchuckKit.ANIM_CHOMP -> CHOMP;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
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
			case UpchuckKit.ANIM_SPIT, UpchuckKit.ANIM_MEGA -> FP_SPIT;
			case UpchuckKit.ANIM_GOBBLE, UpchuckKit.ANIM_FRENZY -> FP_WIDE;
			case UpchuckKit.ANIM_GULP, UpchuckKit.ANIM_DIGEST -> FP_BELLY;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the belly: the fuller his Gut, the bigger it swells

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model,
			float partial, float age) {
		int gut = UpchuckKit.gut(p);
		boolean gulped = (Omnitrix.state(p).kitFlags & UpchuckKit.F_GULPED) != 0;
		float swell = Math.max(gulped ? 1f : 0f, (gut - 30) / 70f);
		if (swell <= 0f) {
			return;
		}
		// a creature inside squirms
		float squirm = gulped ? Mth.sin(age * 0.9f) * 0.35f : 0f;
		float d = 0.6f + 2.6f * swell, w = 3.0f + 1.2f * swell, h = 3.2f + 1.2f * swell;
		AlienParts.on(pose, model.body, () -> {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
			// the skin's own belly green (the body's front, texels 21..27 x 25..30)
			float cy = 8.0f;
			AlienParts.box(pose, vc, -w + squirm, cy - h, -2.0f - d, w + squirm, cy + h, -1.5f, 21 / 64f, 25 / 64f, 27 / 64f, 30 / 64f, 0xFFFFFFFF, light);
		});
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

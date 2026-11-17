package com.projecthero.mod.client.symbiote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteParticles;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteSuitStyle;
import com.projecthero.mod.symbiote.SymbioteTransform;
import com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.17.5 round 2 (user: "right now as it is the player grows as the suit covers them, make it so that the suit comes out of the player
 * and grows pixel by pixel ... based on the comics and movies and shows ... i want a really cool looking suitup animation"): the Normal
 * host's suit-up and take-off, drawn over the host's own, normal-sized body (the size surge now waits until the suit is on:
 * {@code Symbiote.surge}).
 *
 * <h2>What it looks like (suit-up, progress 0 .. 1 of the 3 s clock)</h2>
 * <ol>
 *   <li><b>Burst</b> (0 .. 0.17): black goo erupts from the middle of the chest -- seven wet tendrils whip out of the sternum, writhe,
 *   then curl back down onto the body as the goo starts to spread; droplets fly ({@link SymbioteParticles#GOO}).</li>
 *   <li><b>Spread</b> (0.035 .. 0.70): the suit crawls over the skin texel by texel along a per-texel ARRIVAL TIME field -- the
 *   distance from the chest measured <em>along the body</em> (across the torso, over the shoulders and down the arms, over the hips and
 *   down the legs) through a vein-network cost field, so the goo races ahead along branching vein channels and fills in between them:
 *   organic, finger-like fronts. The leading edge is wet: the newest texels flash a glossy highlight and a raised, inflated rim rides
 *   just ahead (a second pass of the model, {@link #rimTexture}); dark droplet speckles spatter the skin just ahead of the front; thin
 *   3D tendrils reach down each limb ahead of the front, pulling it along.</li>
 *   <li><b>Head</b> (0.50 .. 0.83): goo wraps up the neck and over the head -- over the back and top first (the face is slower), so it
 *   closes over the face last; tendrils crawl up over the jaw.</li>
 *   <li><b>Eyes</b> (0.855 .. 0.90): the lenses are dark goo until they snap open from the middle outward with a white flash.</li>
 *   <li><b>Lethal Protector</b>: the mouth is shut goo until 0.88; then the jaw splits open into the grin and the tongue lolls out
 *   ({@code SymbioteMouth}'s debut); then the host bulks up ({@code Symbiote.surge}).</li>
 * </ol>
 * Take-off runs the same clock backwards (1.8 s): deflate, the eyes go dark, the head peels back down the neck, the goo recedes over
 * the body back into the chest with the skin reappearing texel by texel behind a wet edge, droplets left behind, and the tendrils
 * reach out of the sternum and are sucked back in.
 *
 * <h2>How</h2>
 * One field per suit style is precomputed once ({@link Field}: arrival per texel, layer, eyes, mouth, dither). Each wearer being
 * revealed gets a {@link Canvas}: two small {@link DynamicTexture}s (128 px suit + rim) recomposed when their progress changes (once a
 * frame at most, ~16k texels, no allocation) from the wearer's baked suit colours. {@code SuperheroArmorRenderer#getRenderType} draws
 * the suit with {@link #texture}; {@code SymbioteSuitFx.Layer} adds the rim pass; {@code SuperheroArmorRenderer#renderCubesOfBone}
 * adds the tendrils ({@link #renderStrands}); the first-person sleeve does the same ({@code SymbioteSuitSleeve}). Everything is a pure
 * function of the synced {@link SymbioteState} clock, so every viewer sees the same thing. Canvases are keyed by entity id (no entity
 * references), released after 3 s unused, on a resource reload and on leaving a world.
 */
public final class SymbioteEmerge {
	// ------------------------------------------------------------------ the timeline (transform progress, 0 bare .. 1 on)
	static final float BURST_HOLD = 0.035f;
	static final float BURST_END = 0.13f;
	static final float BURST_GONE = 0.21f;
	static final float BODY_START = 0.035f;
	static final float BODY_END = 0.70f;
	static final float HEAD_START = 0.50f;
	static final float HEAD_END = 0.83f;
	static final float EYES_OPEN = 0.855f;
	static final float EYES_SPAN = 0.04f;
	static final float FLASH = 0.075f;
	/** The Lethal Protector's mouth: shut goo until here, then the bones show and the jaw splits open. */
	public static final float MOUTH_SHOW = 0.88f;
	/** From here the wearer's own hat layer is hidden (the head is fully covered), so the head shell need not be filled any more. */
	static final float HAT_SWAP = 0.90f;
	static final float SHELL_LAG = 0.012f;
	/** Widths of the wet edge, the raised rim and the speckles ahead of the front, in ticks of the clock. */
	static final float EDGE_TICKS = 3.2f;
	static final float RIM_TICKS = 2.0f;
	static final float SPECK_TICKS = 2.8f;
	/** How long after the goo the emblem takes to bloom out of the black. */
	static final float EMBLEM_TICKS = 9f;

	/** Middle of the chest (model px, feet at y = 0, front = -z): where the goo comes out. */
	static final float[] ORIGIN = { 0f, 19.2f, -2f };
	static final int STEPS = 64;

	static final int HEAD = 0;
	static final int BODY = 1;
	static final int R_ARM = 2;
	static final int L_ARM = 3;
	static final int R_LEG = 4;
	static final int L_LEG = 5;
	/** {part, u, v, layer (0 base, 1 shell)} for every box of the 64 x 64 player-skin layout. */
	static final int[][] BOXES = {
			{ HEAD, 0, 0, 0 }, { HEAD, 32, 0, 1 },
			{ BODY, 16, 16, 0 }, { BODY, 16, 32, 1 },
			{ R_ARM, 40, 16, 0 }, { R_ARM, 40, 32, 1 },
			{ L_ARM, 32, 48, 0 }, { L_ARM, 48, 48, 1 },
			{ R_LEG, 0, 16, 0 }, { R_LEG, 0, 32, 1 },
			{ L_LEG, 16, 48, 0 }, { L_LEG, 0, 48, 1 },
	};
	/** Per part: box origin and size (model px). */
	static final float[][] GEOMETRY = {
			{ -4, 24, -4, 8, 8, 8 },
			{ -4, 12, -2, 8, 12, 4 },
			{ -8, 12, -2, 4, 12, 4 },
			{ 4, 12, -2, 4, 12, 4 },
			{ -4, 0, -2, 4, 12, 4 },
			{ 0, 0, -2, 4, 12, 4 },
	};
	/** Region A of the goo patch (skin (24,0)-(40,8), never on the body): what the tendrils are wrapped in. */
	static final float STRAND_U0 = 25.5f / 64f, STRAND_U1 = 38.5f / 64f, STRAND_V0 = 0.5f / 64f, STRAND_V1 = 7.5f / 64f;

	private static final Map<SymbioteSuitStyle, Field> FIELDS = new HashMap<>();
	private static final LinkedHashMap<ResourceLocation, Source> SOURCES = new LinkedHashMap<>(8, 0.75f, true);
	private static final Int2ObjectMap<Canvas> CANVASES = new Int2ObjectOpenHashMap<>();
	private static final Int2ObjectMap<Beats> BEATS = new Int2ObjectOpenHashMap<>();
	private static final int MAX_SOURCES = 6;
	private static final int MAX_CANVASES = 10;
	private static int canvasSerial;

	/** Set while {@code SymbioteSuitFx} draws the rim pass: how far (blocks) to push every face out along its normal. */
	public static float inflate;

	private SymbioteEmerge() {
	}

	// ------------------------------------------------------------------ who

	/** Is {@code p} a Normal host whose suit ({@code symbiote_host}) is coming on or going off right now? */
	public static boolean applies(Player p) {
		return p != null && p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SymbioteHostArmorItem && SymbioteReveal.isRevealing(p);
	}

	private static SymbioteState state(Player p) {
		return p.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
	}

	/** Seconds-free clock facts for {@code p}: transform duration (ticks) and whether it is coming on. */
	private static int duration(Player p) {
		SymbioteState s = state(p);
		return s == null ? 60 : Math.max(1, s.transformDurationTicks);
	}

	private static boolean up(Player p) {
		SymbioteState s = state(p);
		return s == null || s.transformDir != SymbioteState.DIR_DOWN;
	}

	/**
	 * The suit has just been taken off server side (state settled: not active, not animating) but the stripped pieces have not reached
	 * this client yet -- draw nothing rather than flash the whole suit for a frame at the end of the take-off.
	 */
	public static boolean strippedButWorn(Player p) {
		SymbioteState s = state(p);
		return s != null && !s.active && s.transformDir == SymbioteState.DIR_IDLE
				&& p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SymbioteHostArmorItem;
	}

	/** Hide the wearer's hat layer? Only once the head is covered (before that the head shell is filled where the goo has reached). */
	public static boolean hidesHat(Player p) {
		return applies(p) && SymbioteReveal.progress(p, 0f) >= HAT_SWAP;
	}

	// ------------------------------------------------------------------ the textures

	/**
	 * The suit texture to draw on {@code p} this frame: {@code base} (the wearer's baked colours) composed with the arrival field at the
	 * current progress. Falls back to the plain spread frames when the texture is not the 128 px player-skin layout.
	 */
	public static ResourceLocation texture(Player p, ResourceLocation base, float partialTick) {
		Canvas c = canvas(p, base, partialTick);
		return c == null ? SymbioteSpread.texture(base, SymbioteReveal.progress(p, partialTick)) : c.mainId;
	}

	/** The rim pass texture (only the wet leading edge, opaque), or null when there is nothing to draw. */
	public static ResourceLocation rimTexture(Player p, ResourceLocation base, float partialTick) {
		Canvas c = canvas(p, base, partialTick);
		return c == null || !c.rimAny ? null : c.rimId;
	}

	private static Canvas canvas(Player p, ResourceLocation base, float partialTick) {
		SymbioteSuitStyle style = SymbioteSuitTint.styleOf(p);
		Field f = field(style);
		Source src = source(base);
		if (f == null || src == null || src.w != f.w || src.h != f.h) {
			return null;
		}
		Canvas c = CANVASES.get(p.getId());
		if (c == null) {
			if (CANVASES.size() >= MAX_CANVASES) {
				evictOldest();
			}
			c = new Canvas(f.w, f.h);
			CANVASES.put(p.getId(), c);
		}
		float pr = SymbioteReveal.progress(p, partialTick);
		double time = p.level().getGameTime() + partialTick;
		c.lastUsed = p.level().getGameTime();
		if (c.lastBase == base && c.lastStyle == style && c.lastP == pr && c.lastTime == time) {
			return c;
		}
		c.lastBase = base;
		c.lastStyle = style;
		c.lastP = pr;
		c.lastTime = time;
		compose(f, src, c, pr, duration(p), up(p), time, SymbioteSuitTint.colours(p));
		return c;
	}

	/** Recomposes the two canvases. Pure per-texel maths over precomputed arrays; no allocation. */
	static void compose(Field f, Source src, Canvas c, float p, int dur, boolean up, double time, SymbioteColours colours) {
		NativeImage main = c.main.getPixels();
		NativeImage rim = c.rim.getPixels();
		if (main == null || rim == null) {
			return;
		}
		float edgeW = EDGE_TICKS / dur, rimW = RIM_TICKS / dur, speckW = SPECK_TICKS / dur, emblemW = EMBLEM_TICKS / dur;
		int gloss = glossAbgr(colours.primary());
		boolean rimAny = false;
		int w = f.w;
		int n = f.w * f.h;
		float shimmerT = (float) (time * 0.9);
		for (int i = 0; i < n; i++) {
			int s = src.px[i];
			float a = f.arrive[i];
			int outMain = 0;
			int outRim = 0;
			if (Float.isNaN(a)) {
				outMain = s;
			} else {
				float age = p - a;
				if (age >= 0f) {
					int col = s;
					if (f.eye[i] != 0) {
						col = eye(f, src, i, s, p, up);
					} else if (f.emblem[i] != 0 && age < emblemW) {
						// the emblem develops out of the black a moment after the goo has covered it
						float k = smooth((age - emblemW * 0.3f) / (emblemW * 0.7f));
						col = mix(src.lid, col, k) | (col & 0xFF000000);
					} else if (f.mouth[i] != 0 && p < MOUTH_SHOW) {
						col = src.lid; // the Lethal Protector's mouth is shut goo until the jaw splits open
					} else if (f.fillFrom[i] >= 0 && alpha(col) == 0 && p < HAT_SWAP) {
						col = src.px[f.fillFrom[i]]; // the head shell, solid over the wearer's hat layer until it is hidden
					}
					if (alpha(col) != 0) {
						if (age < edgeW) {
							float k = 1f - age / edgeW;
							float shimmer = 0.72f + 0.28f * (float) Math.sin(shimmerT + f.dither[i] * 6.2832f);
							col = mix(col, gloss, 0.4f * k * k * shimmer);
						}
						if (age < rimW && f.layer[i] == 1 && alpha(s) != 0) {
							float k = 1f - age / rimW;
							outRim = mix(s, gloss, 0.18f + 0.37f * k) | 0xFF000000;
							rimAny = true;
						}
					}
					outMain = col;
				} else if (f.layer[i] == 1 && age > -speckW) {
					// droplets spattered on the skin just ahead of the front (on the way off: left behind as it pulls away)
					float q = 1f + age / speckW;
					if (f.dither[i] < 0.5f * q * q) {
						int base = alpha(s) != 0 ? s : src.lid;
						outMain = scale(base, 0.55f) | 0xFF000000;
					}
				}
			}
			int x = i % w, y = i / w;
			main.setPixelRGBA(x, y, outMain);
			rim.setPixelRGBA(x, y, outRim);
		}
		c.rimAny = rimAny;
		c.main.upload();
		if (rimAny) {
			c.rim.upload();
		}
	}

	/** An eye texel: dark goo until its lid splits open, a white flash as it does (on the way on), then the lens colour. */
	private static int eye(Field f, Source src, int i, int s, float p, boolean up) {
		float open = p - (EYES_OPEN + EYES_SPAN * f.eyeOrder[i]);
		if (open < 0f) {
			return src.lid;
		}
		if (!up) {
			return s;
		}
		if (open < 0.008f) {
			return 0xFFFFFFFF;
		}
		float k = Math.min(1f, (open - 0.008f) / FLASH);
		return mix(0xFFFFFFFF, s, k * k * (3f - 2f * k)) | (s & 0xFF000000);
	}

	// ------------------------------------------------------------------ the tendrils

	/**
	 * Called from {@code SuperheroArmorRenderer#renderCubesOfBone} after a Symbiote suit bone's own cubes, with the bone's pose on
	 * {@code pose}: draws this bone's tendrils (the chest burst on the body, reaching tendrils ahead of the front on each limb and up
	 * over the head), wrapped in goo texels of the same suit texture.
	 */
	public static void renderStrands(PoseStack pose, GeoBone bone, VertexConsumer buffer, int light, int overlay, Player p, float partialTick) {
		if (inflate != 0f || bone.getCubes().isEmpty()) {
			return;
		}
		int part = partOf(bone.getName());
		if (part < 0) {
			return;
		}
		Field f = field(SymbioteSuitTint.styleOf(p));
		if (f == null) {
			return;
		}
		BoneMap m = BoneMap.of(bone.getCubes().get(0), part);
		if (m == null) {
			return;
		}
		float pr = SymbioteReveal.progress(p, partialTick);
		double t = p.level().getGameTime() + partialTick;
		float ph = (float) ((p.getId() * 0.6180339887) % 1.0) * 6.2832f;
		Strands out = new Strands(pose, buffer, light, overlay, m);
		if (part == BODY) {
			float env = smooth(pr / BURST_HOLD) * (1f - smooth((pr - BURST_END) / (BURST_GONE - BURST_END)));
			if (env > 0.01f) {
				float curl = 0.22f * smooth((pr - (BURST_END - 0.01f)) / 0.06f);
				for (int k = 0; k < 7; k++) {
					float th = k * 6.2832f / 7f + 0.4f * (float) Math.sin(k * 2.3f + ph);
					float hk = hash01(k * 31 + 7);
					float[] radial = { (float) Math.cos(th), (float) Math.sin(th) * 0.9f, 0f };
					float[] root = { ORIGIN[0] + radial[0] * 0.55f, ORIGIN[1] + radial[1] * 0.55f, -2.1f };
					float[] dir = norm(new float[] { radial[0] * 0.7f, radial[1] * 0.7f, -0.75f });
					out.strand(root, dir, new float[] { 0f, 0f, -1f }, (3.4f + 2.6f * hk) * env, 0.42f, 0.09f,
							0.38f + 0.2f * (1f - env), t * (1.0 + 0.4 * hk), ph + k * 1.7f, curl);
				}
			}
			return;
		}
		float cov = f.coverage(part, pr);
		float act = smooth(cov / 0.06f) * (1f - smooth((cov - 0.88f) / 0.1f));
		if (act <= 0.01f) {
			return;
		}
		float fy = f.front(part, pr);
		float[] g = GEOMETRY[part];
		float cx = g[0] + g[3] / 2f, cz = g[2] + g[5] / 2f;
		float hx = g[3] / 2f + 0.3f, hz = g[5] / 2f + 0.3f;
		if (part == HEAD) {
			float[][] faces = { { 0, 0, -1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 } };
			for (int k = 0; k < faces.length; k++) {
				float[] nrm = faces[k];
				float off = (hash01(k * 13 + 5) - 0.5f) * 3f;
				float[] root = { cx + nrm[0] * hx + (nrm[0] == 0 ? off : 0), Math.min(31.5f, fy), cz + nrm[2] * hz + (nrm[2] == 0 ? off : 0) };
				float[] dir = norm(new float[] { nrm[0] * 0.45f, 0.85f, nrm[2] * 0.45f });
				out.strand(root, dir, nrm, 2.3f * act * (0.8f + 0.4f * hash01(k + 40)), 0.3f, 0.07f, 0.55f, t * 1.2, ph + k * 2.1f, 0.12f);
			}
			return;
		}
		float outer = part == R_ARM || part == R_LEG ? -1f : 1f;
		float[][] faces = { { 0, 0, -1 }, { outer, 0, 0 }, { 0, 0, 1 } };
		for (int k = 0; k < faces.length; k++) {
			float[] nrm = faces[k];
			float off = (hash01(part * 17 + k * 5) - 0.5f) * 1.6f;
			float[] root = { cx + nrm[0] * hx + (nrm[0] == 0 ? off : 0), fy, cz + nrm[2] * hz + (nrm[2] == 0 ? off * 0.6f : 0) };
			float[] dir = norm(new float[] { nrm[0] * 0.42f, -0.88f, nrm[2] * 0.42f });
			out.strand(root, dir, nrm, 2.5f * act * (0.8f + 0.4f * hash01(part * 7 + k)), 0.3f, 0.07f, 0.55f, t * 1.3, ph + part + k * 1.9f, 0.1f);
		}
	}

	private static int partOf(String bone) {
		return switch (bone) {
			case "armorHead" -> HEAD;
			case "armorBody" -> BODY;
			case "armorRightArm" -> R_ARM;
			case "armorLeftArm" -> L_ARM;
			case "armorRightLeg" -> R_LEG;
			case "armorLeftLeg" -> L_LEG;
			default -> -1;
		};
	}

	/**
	 * Maps this mod's model px (right = -x, front = -z, up = +y, feet at 0) onto a GeckoLib bone's baked space, from where the base
	 * cube's six faces actually are (found by their UVs, so any axis flip GeckoLib applies is followed).
	 */
	static final class BoneMap {
		final float ax, bx, ay, by, az, bz; // baked = a + b * px, per axis

		private BoneMap(float ax, float bx, float ay, float by, float az, float bz) {
			this.ax = ax;
			this.bx = bx;
			this.ay = ay;
			this.by = by;
			this.az = az;
			this.bz = bz;
		}

		static BoneMap of(GeoCube cube, int part) {
			float[] g = GEOMETRY[part];
			int[] box = BOXES[part * 2];
			float u = box[1], v = box[2], sx = g[3], sy = g[4], sz = g[5];
			// face regions (64 px space): right side, front, left side, back, top, bottom
			float[][] regions = {
					{ u, v + sz, sz, sy }, { u + sz, v + sz, sx, sy }, { u + sz + sx, v + sz, sz, sy }, { u + 2 * sz + sx, v + sz, sx, sy },
					{ u + sz, v, sx, sz }, { u + sz + sx, v, sx, sz } };
			float[] plane = new float[6];
			boolean[] found = new boolean[6];
			for (GeoQuad q : cube.quads()) {
				if (q == null) {
					continue;
				}
				float tu = 0, tv = 0, px = 0, py = 0, pz = 0;
				for (GeoVertex vx : q.vertices()) {
					tu += vx.texU();
					tv += vx.texV();
					px += vx.position().x();
					py += vx.position().y();
					pz += vx.position().z();
				}
				int nv = q.vertices().length;
				tu = tu / nv * 64f;
				tv = tv / nv * 64f;
				for (int r = 0; r < 6; r++) {
					float[] rg = regions[r];
					if (tu > rg[0] && tu < rg[0] + rg[2] && tv > rg[1] && tv < rg[1] + rg[3]) {
						plane[r] = r == 0 || r == 2 ? px / nv : r == 1 || r == 3 ? pz / nv : py / nv;
						found[r] = true;
					}
				}
			}
			for (boolean b : found) {
				if (!b) {
					return null;
				}
			}
			float infl = 0.3f;
			float x0 = g[0] - infl, x1 = g[0] + sx + infl; // px: right side .. left side
			float y0 = g[1] - infl, y1 = g[1] + sy + infl; // bottom .. top
			float z0 = g[2] - infl, z1 = g[2] + sz + infl; // front .. back
			float bx = (plane[2] - plane[0]) / (x1 - x0), by = (plane[4] - plane[5]) / (y1 - y0), bz = (plane[3] - plane[1]) / (z1 - z0);
			return new BoneMap(plane[0] - bx * x0, bx, plane[5] - by * y0, by, plane[1] - bz * z0, bz);
		}
	}

	/** Draws tapered, wriggling tendrils as chains of four-sided segments. */
	static final class Strands {
		static final int SEGMENTS = 7;
		final Matrix4f mat;
		final Matrix3f nmat;
		final VertexConsumer buf;
		final int light, overlay;
		final BoneMap m;
		final Vector4f v4 = new Vector4f();
		final Vector3f n3 = new Vector3f();

		Strands(PoseStack pose, VertexConsumer buf, int light, int overlay, BoneMap m) {
			this.mat = pose.last().pose();
			this.nmat = pose.last().normal();
			this.buf = buf;
			this.light = light;
			this.overlay = overlay;
			this.m = m;
		}

		void strand(float[] root, float[] dir, float[] nrm, float len, float r0, float r1, float wig, double t, float ph, float curl) {
			if (len < 0.15f) {
				return;
			}
			float seg = len / SEGMENTS;
			float[] cur = { root[0] - nrm[0] * 0.15f, root[1] - nrm[1] * 0.15f, root[2] - nrm[2] * 0.15f };
			float[] d = dir.clone();
			for (int k = 0; k < SEGMENTS; k++) {
				float[] lat = norm(cross(nrm, d));
				float a1 = wig * (float) Math.sin(t * 0.55 + k * 0.95 + ph);
				float a2 = wig * 0.6f * (float) Math.sin(t * 0.43 + k * 1.3 + ph * 1.7);
				d = norm(new float[] { d[0] + lat[0] * a1 * 0.5f + nrm[0] * (a2 * 0.3f - curl), d[1] + lat[1] * a1 * 0.5f + nrm[1] * (a2 * 0.3f - curl),
						d[2] + lat[2] * a1 * 0.5f + nrm[2] * (a2 * 0.3f - curl) });
				float[] nxt = { cur[0] + d[0] * seg, cur[1] + d[1] * seg, cur[2] + d[2] * seg };
				float ra = r0 + (r1 - r0) * k / SEGMENTS, rb = r0 + (r1 - r0) * (k + 1) / SEGMENTS;
				segment(cur, nxt, ra, rb, k, k == SEGMENTS - 1);
				cur = nxt;
			}
		}

		private void segment(float[] a, float[] b, float ra, float rb, int k, boolean tip) {
			float[] f = norm(new float[] { b[0] - a[0], b[1] - a[1], b[2] - a[2] });
			float[] any = Math.abs(f[1]) < 0.9f ? new float[] { 0, 1, 0 } : new float[] { 1, 0, 0 };
			float[] u = norm(cross(f, any));
			float[] v = cross(f, u);
			float va = STRAND_V0 + (STRAND_V1 - STRAND_V0) * k / SEGMENTS, vb = STRAND_V0 + (STRAND_V1 - STRAND_V0) * (k + 1) / SEGMENTS;
			float[][] side = { u, v, { -u[0], -u[1], -u[2] }, { -v[0], -v[1], -v[2] } };
			for (int s = 0; s < 4; s++) {
				float[] e0 = side[s], e1 = side[(s + 1) % 4];
				float[] nn = norm(new float[] { e0[0] + e1[0], e0[1] + e1[1], e0[2] + e1[2] });
				float u0 = STRAND_U0 + (STRAND_U1 - STRAND_U0) * s / 4f, u1 = STRAND_U0 + (STRAND_U1 - STRAND_U0) * (s + 1) / 4f;
				vert(a, e0, ra, u0, va, nn);
				vert(b, e0, rb, u0, vb, nn);
				vert(b, e1, rb, u1, vb, nn);
				vert(a, e1, ra, u1, va, nn);
			}
			if (tip) {
				float um = (STRAND_U0 + STRAND_U1) / 2f;
				vert(b, u, rb, um, STRAND_V1, f);
				vert(b, v, rb, um, STRAND_V1, f);
				vert(b, new float[] { -u[0], -u[1], -u[2] }, rb, um, STRAND_V1, f);
				vert(b, new float[] { -v[0], -v[1], -v[2] }, rb, um, STRAND_V1, f);
			}
		}

		private void vert(float[] c, float[] e, float r, float u, float v, float[] nrm) {
			float x = m.ax + m.bx * (c[0] + e[0] * r), y = m.ay + m.by * (c[1] + e[1] * r), z = m.az + m.bz * (c[2] + e[2] * r);
			mat.transform(v4.set(x, y, z, 1f));
			nmat.transform(n3.set(nrm[0] * Math.signum(m.bx), nrm[1] * Math.signum(m.by), nrm[2] * Math.signum(m.bz)));
			n3.normalize();
			buf.addVertex(v4.x(), v4.y(), v4.z(), 0xFFFFFFFF, u, v, overlay, light, n3.x(), n3.y(), n3.z());
		}
	}

	// ------------------------------------------------------------------ the client tick: droplets, sounds, housekeeping

	/** Per entity id: the last progress the beat tracker saw, so each sound plays once as the clock crosses it. */
	static final class Beats {
		long start = Long.MIN_VALUE;
		int dir;
		float last = -1f;
		long seen;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			if (!CANVASES.isEmpty() || !BEATS.isEmpty()) {
				clear();
			}
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		long now = mc.level.getGameTime();
		Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
		for (Player p : mc.level.players()) {
			if (!applies(p) || p.distanceToSqr(eye) > 40 * 40) {
				continue;
			}
			SymbioteState s = state(p);
			Field f = field(SymbioteSuitTint.styleOf(p));
			if (s == null || f == null) {
				continue;
			}
			float pr = SymbioteTransform.effectiveProgress(s, now);
			boolean up = s.transformDir == SymbioteState.DIR_UP;
			Beats b = BEATS.computeIfAbsent(p.getId(), k -> new Beats());
			if (b.start != s.transformStartTick || b.dir != s.transformDir) {
				b.start = s.transformStartTick;
				b.dir = s.transformDir;
				b.last = up ? -1f : 2f;
			}
			b.seen = now;
			if (!p.isInvisible()) {
				droplets(mc, p, f, s, pr, up);
			}
			sounds(mc, p, b.last, pr, up);
			b.last = pr;
		}
		if (now % 20 == 0) {
			Iterator<Int2ObjectMap.Entry<Canvas>> it = CANVASES.int2ObjectEntrySet().iterator();
			while (it.hasNext()) {
				Canvas c = it.next().getValue();
				if (now - c.lastUsed > 60 || now < c.lastUsed) {
					c.release();
					it.remove();
				}
			}
			BEATS.int2ObjectEntrySet().removeIf(en -> now - en.getValue().seen > 200 || now < en.getValue().seen);
		}
	}

	private static void droplets(Minecraft mc, Player p, Field f, SymbioteState s, float pr, boolean up) {
		var rng = p.getRandom();
		int rgb = SymbioteSuitTint.colours(p).primary();
		float scale = p.getScale();
		int dur = Math.max(1, s.transformDurationTicks);
		// the burst: goo flung out of the chest while the tendrils are out
		float env = smooth(pr / BURST_HOLD) * (1f - smooth((pr - BURST_END) / (BURST_GONE - BURST_END)));
		if (env > 0.05f) {
			int count = up ? (rng.nextInt(3) == 0 ? 2 : 1) : 1;
			for (int k = 0; k < count; k++) {
				Vec3 at = world(p, ORIGIN[0] + (rng.nextFloat() - 0.5f) * 1.5f, ORIGIN[1] + (rng.nextFloat() - 0.5f) * 1.5f, -2.6f, scale);
				Vec3 vel;
				if (up) {
					vel = dirWorld(p, (rng.nextFloat() - 0.5f) * 1.6f, 0.4f + rng.nextFloat() * 0.8f, -1.2f).scale(0.09 + rng.nextFloat() * 0.06);
				} else {
					// on the way off the droplets are sucked back toward the chest
					at = world(p, (rng.nextFloat() - 0.5f) * 8f, ORIGIN[1] + (rng.nextFloat() - 0.5f) * 6f, -5f - rng.nextFloat() * 3f, scale);
					vel = world(p, ORIGIN[0], ORIGIN[1], -2.2f, scale).subtract(at).scale(0.22);
				}
				mc.level.addParticle(new SymbioteParticles.Goo(rgb, 0.04f + rng.nextFloat() * 0.03f), at.x, at.y, at.z, vel.x, vel.y, vel.z);
			}
		}
		// along the front: a droplet or two dripping off where the goo is moving
		float near = 1.6f / dur;
		int tries = 0;
		int emitted = 0;
		int want = pr > 0.04f && pr < 0.9f && rng.nextBoolean() ? 1 : 0;
		while (emitted < want && tries++ < 14) {
			int k = rng.nextInt(f.nodeCount);
			if (Math.abs(f.nodeA[k] - pr) > near) {
				continue;
			}
			float x = f.nodeP[k * 3], y = f.nodeP[k * 3 + 1], z = f.nodeP[k * 3 + 2];
			float nx = f.nodeN[k * 3], ny = f.nodeN[k * 3 + 1], nz = f.nodeN[k * 3 + 2];
			Vec3 at = world(p, x + nx * 0.5f, y + ny * 0.5f, z + nz * 0.5f, scale);
			Vec3 vel = dirWorld(p, nx, ny + 0.3f, nz).scale(0.03 + rng.nextFloat() * 0.03);
			mc.level.addParticle(new SymbioteParticles.Goo(rgb, 0.025f + rng.nextFloat() * 0.02f), at.x, at.y, at.z, vel.x, vel.y, vel.z);
			emitted++;
		}
		// the eyes snapping open: a few strands of goo snapping off the face
		if (up && pr > EYES_OPEN && pr < EYES_OPEN + 1.5f / dur) {
			for (int k = 0; k < 4; k++) {
				Vec3 at = world(p, (rng.nextFloat() - 0.5f) * 6f, 28f + rng.nextFloat() * 2f, -4.6f, scale);
				Vec3 vel = dirWorld(p, (rng.nextFloat() - 0.5f) * 2f, 0.6f, -1f).scale(0.07);
				mc.level.addParticle(new SymbioteParticles.Goo(rgb, 0.04f), at.x, at.y, at.z, vel.x, vel.y, vel.z);
			}
		}
	}

	private static void sounds(Minecraft mc, Player p, float last, float pr, boolean up) {
		if (up) {
			beat(mc, p, last, pr, 0.01f, true, SoundEvents.SCULK_BLOCK_CHARGE, 0.7f, 0.7f);
			beat(mc, p, last, pr, 0.30f, true, SoundEvents.SCULK_VEIN_PLACE, 0.6f, 0.6f);
			beat(mc, p, last, pr, HEAD_START + 0.04f, true, SoundEvents.HONEY_BLOCK_SLIDE, 0.6f, 0.55f);
			beat(mc, p, last, pr, EYES_OPEN, true, SoundEvents.WARDEN_TENDRIL_CLICKS, 0.8f, 1.35f);
			beat(mc, p, last, pr, 0.9f, true, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 0.75f);
			if (SymbioteSuitTint.styleOf(p) == SymbioteSuitStyle.LETHAL_PROTECTOR) {
				beat(mc, p, last, pr, MOUTH_SHOW + 0.03f, true, SoundEvents.WARDEN_AGITATED, 0.45f, 1.25f);
			}
		} else {
			beat(mc, p, last, pr, 0.97f, false, SoundEvents.SCULK_BLOCK_CHARGE, 0.6f, 1.25f);
			beat(mc, p, last, pr, 0.62f, false, SoundEvents.HONEY_BLOCK_SLIDE, 0.6f, 0.75f);
			beat(mc, p, last, pr, 0.12f, false, SoundEvents.SCULK_VEIN_PLACE, 0.7f, 0.8f);
		}
	}

	private static void beat(Minecraft mc, Player p, float last, float pr, float at, boolean rising, SoundEvent sound, float vol, float pitch) {
		boolean crossed = rising ? last < at && pr >= at : last > at && pr <= at;
		if (crossed) {
			mc.level.playLocalSound(p.getX(), p.getY() + 1.0, p.getZ(), sound, SoundSource.PLAYERS, vol, pitch, false);
		}
	}

	/** Model px on {@code p}'s body (right = -x, front = -z, feet at y 0) to a world position (ignores limb swing). */
	static Vec3 world(Player p, float x, float y, float z, float scale) {
		double yaw = Math.toRadians(p.yBodyRot);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
		double k = scale / 16.0;
		return new Vec3(p.getX() + (lx * x - fx * z) * k, p.getY() + y * k, p.getZ() + (lz * x - fz * z) * k);
	}

	static Vec3 dirWorld(Player p, float x, float y, float z) {
		double yaw = Math.toRadians(p.yBodyRot);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
		return new Vec3(lx * x - fx * z, y, lz * x - fz * z).normalize();
	}

	// ------------------------------------------------------------------ caches

	/** Drops every canvas, source and field (resource reload / leaving a world). Render thread. */
	public static void clear() {
		for (Canvas c : CANVASES.values()) {
			c.release();
		}
		CANVASES.clear();
		BEATS.clear();
		SOURCES.clear();
		FIELDS.clear();
	}

	/** A baked suit texture was released ({@link SymbioteSuitTint}): forget its pixels. */
	public static void forget(ResourceLocation base) {
		SOURCES.remove(base);
	}

	private static void evictOldest() {
		int oldest = 0;
		long when = Long.MAX_VALUE;
		for (Int2ObjectMap.Entry<Canvas> e : CANVASES.int2ObjectEntrySet()) {
			if (e.getValue().lastUsed < when) {
				when = e.getValue().lastUsed;
				oldest = e.getIntKey();
			}
		}
		Canvas c = CANVASES.remove(oldest);
		if (c != null) {
			c.release();
		}
	}

	/** Two dynamic textures for one wearer. */
	static final class Canvas {
		final DynamicTexture main, rim;
		final ResourceLocation mainId, rimId;
		ResourceLocation lastBase;
		SymbioteSuitStyle lastStyle;
		float lastP = -1f;
		double lastTime = Double.NaN;
		long lastUsed;
		boolean rimAny;

		Canvas(int w, int h) {
			int n = canvasSerial++;
			main = new DynamicTexture(w, h, true);
			rim = new DynamicTexture(w, h, true);
			mainId = ProjectHeroMod.id("dynamic/symbiote_emerge/main_" + n);
			rimId = ProjectHeroMod.id("dynamic/symbiote_emerge/rim_" + n);
			Minecraft.getInstance().getTextureManager().register(mainId, main);
			Minecraft.getInstance().getTextureManager().register(rimId, rim);
		}

		void release() {
			Minecraft.getInstance().getTextureManager().release(mainId);
			Minecraft.getInstance().getTextureManager().release(rimId);
		}
	}

	/** A suit texture's pixels (ABGR) and its dark "lid" colour (the body's average, darkened). */
	static final class Source {
		final int w, h;
		final int[] px;
		final int lid;

		Source(int w, int h, int[] px, int lid) {
			this.w = w;
			this.h = h;
			this.px = px;
			this.lid = lid;
		}
	}

	private static Source source(ResourceLocation base) {
		Source s = SOURCES.get(base);
		if (s != null) {
			return s;
		}
		Optional<NativeImage> baked = SymbioteSuitTint.image(base);
		NativeImage img = baked.orElseGet(() -> SymbioteSuitTint.read(base));
		if (img == null) {
			return null;
		}
		int w = img.getWidth(), h = img.getHeight();
		int[] px = new int[w * h];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				px[y * w + x] = img.getPixelRGBA(x, y);
			}
		}
		img.close();
		// the lid: the average of the darker half of the body front's texels (skin (20,20)-(28,32)) -- the body colour, not the
		// emblem -- darkened
		long r = 0, g = 0, b = 0, n = 0;
		int su = w / 64;
		int[] lums = new int[256];
		int count = 0;
		for (int y = 20 * su; y < 32 * su && y < h; y++) {
			for (int x = 20 * su; x < 28 * su && x < w; x++) {
				int c = px[y * w + x];
				if (alpha(c) != 0) {
					lums[lum(c)]++;
					count++;
				}
			}
		}
		int median = 0;
		for (int acc = 0; median < 255 && acc + lums[median] < count / 2; median++) {
			acc += lums[median];
		}
		for (int y = 20 * su; y < 32 * su && y < h; y++) {
			for (int x = 20 * su; x < 28 * su && x < w; x++) {
				int c = px[y * w + x];
				if (alpha(c) == 0 || lum(c) > median) {
					continue;
				}
				r += c & 0xFF;
				g += (c >> 8) & 0xFF;
				b += (c >> 16) & 0xFF;
				n++;
			}
		}
		int lid = n == 0 ? 0xFF1A1614 : abgr(255, (int) (b / n * 7 / 10), (int) (g / n * 7 / 10), (int) (r / n * 7 / 10));
		s = new Source(w, h, px, lid);
		SOURCES.put(base, s);
		Iterator<Map.Entry<ResourceLocation, Source>> it = SOURCES.entrySet().iterator();
		while (SOURCES.size() > MAX_SOURCES && it.hasNext()) {
			it.next();
			it.remove();
		}
		return s;
	}

	private static Field field(SymbioteSuitStyle style) {
		Field f = FIELDS.get(style);
		if (f != null) {
			return f.failed ? null : f;
		}
		boolean lethal = style == SymbioteSuitStyle.LETHAL_PROTECTOR;
		NativeImage mask = SymbioteSuitTint.read(lethal ? SymbioteSuitTint.LETHAL_MASK : SymbioteSuitTint.MASK);
		try {
			int w = mask == null ? 128 : mask.getWidth(), h = mask == null ? 128 : mask.getHeight();
			int[] mk = null;
			if (mask != null) {
				mk = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						mk[y * w + x] = mask.getPixelRGBA(x, y);
					}
				}
			}
			f = Field.build(w, h, mk, lethal);
		} catch (RuntimeException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not build the Symbiote suit-up field: {}", e.toString());
			f = Field.failed();
		} finally {
			if (mask != null) {
				mask.close();
			}
		}
		FIELDS.put(style, f);
		return f.failed ? null : f;
	}

	// ------------------------------------------------------------------ the field (built once per style)

	/** Per texel: arrival time (NaN = always drawn), layer, eye / mouth flags, the head-shell fill source, a dither value. */
	public static final class Field {
		final int w, h;
		final float[] arrive;
		final byte[] layer; // 0 none, 1 base, 2 shell
		final byte[] eye;
		final float[] eyeOrder;
		final byte[] mouth;
		final byte[] emblem;
		final float[] dither;
		final int[] fillFrom;
		int nodeCount;
		float[] nodeP, nodeN, nodeA;
		final float[][] frontY = new float[6][STEPS + 1];
		final float[][] cov = new float[6][STEPS + 1];
		boolean failed;

		Field(int w, int h) {
			this.w = w;
			this.h = h;
			int n = w * h;
			arrive = new float[n];
			layer = new byte[n];
			eye = new byte[n];
			eyeOrder = new float[n];
			mouth = new byte[n];
			emblem = new byte[n];
			dither = new float[n];
			fillFrom = new int[n];
			Arrays.fill(arrive, Float.NaN);
			Arrays.fill(fillFrom, -1);
		}

		static Field failed() {
			Field f = new Field(1, 1);
			f.failed = true;
			return f;
		}

		float front(int part, float p) {
			return lerpTable(frontY[part], p);
		}

		float coverage(int part, float p) {
			return lerpTable(cov[part], p);
		}

		private static float lerpTable(float[] t, float p) {
			float x = Math.max(0f, Math.min(1f, p)) * STEPS;
			int i = Math.min(STEPS - 1, (int) x);
			float k = x - i;
			return t[i] + (t[i + 1] - t[i]) * k;
		}

		/** Test / harness hook: the arrival time at texel (x, y) (NaN when it is not on the body). */
		public float arrivalAt(int x, int y) {
			return arrive[y * w + x];
		}

		/**
		 * Builds the field. Pure maths -- {@code mask} is the style's tint mask (ABGR, may be null: no eyes / mouth), used for the
		 * lenses (B channel = eyes) and, for the Lethal Protector, the mouth (A = 0 on the lower face).
		 */
		public static Field build(int w, int h, int[] mask, boolean lethal) {
			Field f = new Field(w, h);
			int su = w / 64;
			List<float[]> nodes = new ArrayList<>(); // {texel, part, x, y, z, nx, ny, nz}
			int[] nodeOfTexel = new int[w * h];
			Arrays.fill(nodeOfTexel, -1);
			for (int[] box : BOXES) {
				int part = box[0];
				float[] g = GEOMETRY[part];
				float sx = g[3], sy = g[4], sz = g[5];
				float u = box[1], v = box[2];
				int[] baseBox = BOXES[part * 2];
				float[][] faces = { { u + sz, v, sx, sz, 0 }, { u + sz + sx, v, sx, sz, 1 }, { u, v + sz, sz, sy, 2 }, { u + sz, v + sz, sx, sy, 3 },
						{ u + sz + sx, v + sz, sz, sy, 4 }, { u + 2 * sz + sx, v + sz, sx, sy, 5 } };
				for (float[] fc : faces) {
					int x0 = Math.round(fc[0] * su), x1 = Math.round((fc[0] + fc[2]) * su);
					int y0 = Math.round(fc[1] * su), y1 = Math.round((fc[1] + fc[3]) * su);
					for (int ty = y0; ty < y1; ty++) {
						for (int tx = x0; tx < x1; tx++) {
							if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
								continue;
							}
							int i = ty * w + tx;
							float fu = ((tx + 0.5f) / su - fc[0]) / fc[2];
							float fv = ((ty + 0.5f) / su - fc[1]) / fc[3];
							float[] pt = point((int) fc[4], fu, fv, g);
							f.layer[i] = (byte) (box[3] + 1);
							if (box[3] == 0) {
								float[] nn = normal((int) fc[4]);
								nodeOfTexel[i] = nodes.size();
								nodes.add(new float[] { i, part, pt[0], pt[1], pt[2], nn[0], nn[1], nn[2] });
							} else {
								int bx = tx - (box[1] - baseBox[1]) * su, by = ty - (box[2] - baseBox[2]) * su;
								f.fillFrom[i] = part == HEAD ? by * w + bx : -1;
								f.arrive[i] = -1f; // set from the base texel below
								f.eyeOrder[i] = by * w + bx; // temporarily: the base texel index
							}
							f.dither[i] = hash01(tx * 7919 + ty * 104729);
						}
					}
				}
			}
			int n = nodes.size();
			f.nodeCount = n;
			f.nodeP = new float[n * 3];
			f.nodeN = new float[n * 3];
			f.nodeA = new float[n];
			int[] part = new int[n];
			float[] cost = new float[n];
			for (int k = 0; k < n; k++) {
				float[] nd = nodes.get(k);
				part[k] = (int) nd[1];
				f.nodeP[k * 3] = nd[2];
				f.nodeP[k * 3 + 1] = nd[3];
				f.nodeP[k * 3 + 2] = nd[4];
				f.nodeN[k * 3] = nd[5];
				f.nodeN[k * 3 + 1] = nd[6];
				f.nodeN[k * 3 + 2] = nd[7];
				cost[k] = cost(nd[2], nd[3], nd[4]);
				if (part[k] == HEAD && nd[7] < -0.5f) {
					cost[k] *= 1.65f; // the face is slow: the goo closes over it last
				}
			}
			int[][] adj = adjacency(f.nodeP, part, n);
			float[] dist = new float[n];
			Arrays.fill(dist, Float.POSITIVE_INFINITY);
			// the body, from the chest
			for (int k = 0; k < n; k++) {
				if (part[k] == BODY) {
					float d = d3(f.nodeP, k, ORIGIN);
					if (d < 1.0f) {
						dist[k] = d * cost[k];
					}
				}
			}
			dijkstra(dist, adj, f.nodeP, cost, part, BODY);
			// the limbs, from the shoulders and hips; the head from the neck
			for (int limb = R_ARM; limb <= L_LEG; limb++) {
				float sign = limb == R_ARM || limb == R_LEG ? -1f : 1f;
				boolean arm = limb <= L_ARM;
				for (int k = 0; k < n; k++) {
					if (part[k] != limb || (arm ? f.nodeP[k * 3 + 1] < 22f : f.nodeP[k * 3 + 1] < 10.5f)) {
						continue;
					}
					float best = Float.POSITIVE_INFINITY;
					for (int j = 0; j < n; j++) {
						if (part[j] != BODY || Float.isInfinite(dist[j])) {
							continue;
						}
						float jx = f.nodeP[j * 3], jy = f.nodeP[j * 3 + 1];
						boolean ok = arm ? jy >= 21.5f && jx * sign >= 2.5f : jy <= 13f && jx * sign >= -0.5f;
						if (ok) {
							best = Math.min(best, dist[j] + 1.1f * dd(f.nodeP, k, j) * cost[k]);
						}
					}
					dist[k] = best;
				}
				dijkstra(dist, adj, f.nodeP, cost, part, limb);
			}
			for (int k = 0; k < n; k++) {
				if (part[k] != HEAD || f.nodeP[k * 3 + 1] > 25f) {
					continue;
				}
				float best = Float.POSITIVE_INFINITY;
				for (int j = 0; j < n; j++) {
					if (part[j] == BODY && f.nodeP[j * 3 + 1] >= 23f && Math.abs(f.nodeP[j * 3]) <= 3.5f && !Float.isInfinite(dist[j])) {
						best = Math.min(best, dist[j] + dd(f.nodeP, k, j) * cost[k]);
					}
				}
				dist[k] = best;
			}
			dijkstra(dist, adj, f.nodeP, cost, part, HEAD);
			// normalise: body + limbs into BODY_START .. BODY_END, the head into HEAD_START .. HEAD_END
			float dMax = 1e-3f, hMin = Float.POSITIVE_INFINITY, hMax = 0f;
			for (int k = 0; k < n; k++) {
				if (Float.isInfinite(dist[k])) {
					continue;
				}
				if (part[k] == HEAD) {
					hMin = Math.min(hMin, dist[k]);
					hMax = Math.max(hMax, dist[k]);
				} else {
					dMax = Math.max(dMax, dist[k]);
				}
			}
			for (int k = 0; k < n; k++) {
				int i = (int) nodes.get(k)[0];
				float jit = (f.dither[i] - 0.5f) * 0.012f;
				float a;
				if (Float.isInfinite(dist[k])) {
					a = part[k] == HEAD ? HEAD_END : BODY_END;
				} else if (part[k] == HEAD) {
					a = HEAD_START + (HEAD_END - HEAD_START) * ((dist[k] - hMin) / Math.max(1e-3f, hMax - hMin));
				} else {
					a = BODY_START + (BODY_END - BODY_START) * (float) Math.pow(dist[k] / dMax, 0.92);
				}
				a = Math.max(0.005f, Math.min(HEAD_END, a + jit));
				f.nodeA[k] = a;
				f.arrive[i] = a;
			}
			// the shell follows its base texel a moment later (the raised layer thickens in behind the front)
			for (int i = 0; i < w * h; i++) {
				if (f.layer[i] == 2) {
					int b = (int) f.eyeOrder[i];
					f.eyeOrder[i] = 0f;
					float ba = b >= 0 && b < w * h ? f.arrive[b] : Float.NaN;
					f.arrive[i] = Float.isNaN(ba) ? HEAD_END : Math.min(HEAD_END + SHELL_LAG, ba + SHELL_LAG);
				}
			}
			// the spine nodes' goo patch (skin (56,16)-(64,48)) arrives with the middle of the back
			int spine = nearest(f.nodeP, part, BODY, 0f, 14f, 2f);
			float spineA = spine >= 0 ? f.nodeA[spine] : 0.3f;
			for (int y = 16 * su; y < 48 * su && y < h; y++) {
				for (int x = 56 * su; x < 64 * su && x < w; x++) {
					f.arrive[y * w + x] = spineA;
				}
			}
			// eyes (mask channel = eyes) and the Lethal Protector's mouth (mask A = 0 on the lower face)
			if (mask != null) {
				eyesAndMouth(f, mask, nodeOfTexel, lethal);
			}
			tables(f, part);
			return f;
		}

		private static void eyesAndMouth(Field f, int[] mask, int[] nodeOfTexel, boolean lethal) {
			int w = f.w, h = f.h;
			// pass 1: centroid and extent of each eye (left / right of the face)
			float[] sumX = new float[2], sumY = new float[2], cnt = new float[2], ex = new float[2], ey = new float[2];
			float[][] pts = new float[w * h][];
			for (int i = 0; i < w * h; i++) {
				int ch = (mask[i] >> 16) & 0xFF;
				if (f.layer[i] != 0 && alpha(mask[i]) != 0 && ch >= 64 && ch < 192) {
					f.emblem[i] = 1; // the emblem blooms in a moment after the goo arrives
				}
				if (f.layer[i] == 0 || !inHead(f, i)) {
					continue;
				}
				int m = mask[i];
				if (alpha(m) == 0) {
					if (lethal) {
						float[] pt = headPoint(f, i, nodeOfTexel);
						if (pt != null && pt[2] < -3.9f && pt[1] < 28.6f) {
							f.mouth[i] = 1;
						}
					}
					continue;
				}
				if (((m >> 16) & 0xFF) < 192) {
					continue;
				}
				float[] pt = headPoint(f, i, nodeOfTexel);
				if (pt == null) {
					continue;
				}
				f.eye[i] = 1;
				pts[i] = pt;
				int e = pt[0] < 0 ? 0 : 1;
				sumX[e] += pt[0];
				sumY[e] += pt[1];
				cnt[e]++;
			}
			float[] cx = new float[2], cy = new float[2];
			for (int e = 0; e < 2; e++) {
				cx[e] = cnt[e] > 0 ? sumX[e] / cnt[e] : 0f;
				cy[e] = cnt[e] > 0 ? sumY[e] / cnt[e] : 0f;
			}
			for (int i = 0; i < w * h; i++) {
				if (pts[i] != null) {
					int e = pts[i][0] < 0 ? 0 : 1;
					ex[e] = Math.max(ex[e], Math.abs(pts[i][0] - cx[e]));
					ey[e] = Math.max(ey[e], Math.abs(pts[i][1] - cy[e]));
				}
			}
			for (int i = 0; i < w * h; i++) {
				if (pts[i] != null) {
					int e = pts[i][0] < 0 ? 0 : 1;
					float o = 0.65f * Math.abs(pts[i][1] - cy[e]) / Math.max(0.3f, ey[e]) + 0.35f * Math.abs(pts[i][0] - cx[e]) / Math.max(0.3f, ex[e]);
					f.eyeOrder[i] = Math.min(1f, o);
				}
			}
		}

		private static boolean inHead(Field f, int i) {
			int su = f.w / 64;
			int x = i % f.w, y = i / f.w;
			return y < 16 * su && x < 64 * su;
		}

		/** The body point of a head texel (a shell texel uses its base texel's point). */
		private static float[] headPoint(Field f, int i, int[] nodeOfTexel) {
			int b = f.layer[i] == 2 ? f.fillFrom[i] : i;
			if (b < 0) {
				return null;
			}
			int k = nodeOfTexel[b];
			return k < 0 ? null : new float[] { f.nodeP[k * 3], f.nodeP[k * 3 + 1], f.nodeP[k * 3 + 2] };
		}

		/** Coverage and front height per limb / head per progress step, for the reaching tendrils. */
		private static void tables(Field f, int[] part) {
			for (int pt = 0; pt < 6; pt++) {
				if (pt == BODY) {
					continue;
				}
				float[] g = GEOMETRY[pt];
				int bins = (int) g[4];
				int[] total = new int[bins];
				for (int k = 0; k < f.nodeCount; k++) {
					if (part[k] == pt) {
						total[Math.max(0, Math.min(bins - 1, (int) (f.nodeP[k * 3 + 1] - g[1])))]++;
					}
				}
				int all = 0;
				for (int t : total) {
					all += t;
				}
				for (int s = 0; s <= STEPS; s++) {
					float p = s / (float) STEPS;
					int[] cov = new int[bins];
					int covered = 0;
					for (int k = 0; k < f.nodeCount; k++) {
						if (part[k] == pt && f.nodeA[k] <= p) {
							cov[Math.max(0, Math.min(bins - 1, (int) (f.nodeP[k * 3 + 1] - g[1])))]++;
							covered++;
						}
					}
					f.cov[pt][s] = all == 0 ? 0f : covered / (float) all;
					float front;
					if (pt == HEAD) {
						front = g[1];
						for (int b = 0; b < bins && cov[b] * 2 >= total[b] && total[b] > 0; b++) {
							front = g[1] + b + 1;
						}
					} else {
						front = g[1] + g[4];
						for (int b = bins - 1; b >= 0 && cov[b] * 2 >= total[b] && total[b] > 0; b--) {
							front = g[1] + b;
						}
					}
					f.frontY[pt][s] = front;
				}
			}
		}

		private static int nearest(float[] p, int[] part, int want, float x, float y, float z) {
			int best = -1;
			float bd = Float.POSITIVE_INFINITY;
			for (int k = 0; k < part.length; k++) {
				if (part[k] != want) {
					continue;
				}
				float d = d3(p, k, new float[] { x, y, z });
				if (d < bd) {
					bd = d;
					best = k;
				}
			}
			return best;
		}

		private static int[][] adjacency(float[] p, int[] part, int n) {
			float cell = 0.75f;
			Map<Long, List<Integer>> grid = new HashMap<>();
			for (int k = 0; k < n; k++) {
				grid.computeIfAbsent(key(part[k], p[k * 3], p[k * 3 + 1], p[k * 3 + 2], cell, 0, 0, 0), q -> new ArrayList<>()).add(k);
			}
			int[][] adj = new int[n][];
			int[] tmp = new int[64];
			for (int k = 0; k < n; k++) {
				int c = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						for (int dz = -1; dz <= 1; dz++) {
							List<Integer> l = grid.get(key(part[k], p[k * 3], p[k * 3 + 1], p[k * 3 + 2], cell, dx, dy, dz));
							if (l == null) {
								continue;
							}
							for (int j : l) {
								if (j != k && dd(p, k, j) <= 0.75f && c < tmp.length) {
									tmp[c++] = j;
								}
							}
						}
					}
				}
				adj[k] = Arrays.copyOf(tmp, c);
			}
			return adj;
		}

		private static long key(int part, float x, float y, float z, float cell, int dx, int dy, int dz) {
			long ix = (long) Math.floor(x / cell) + dx + 512, iy = (long) Math.floor(y / cell) + dy + 512, iz = (long) Math.floor(z / cell) + dz + 512;
			return ((long) part << 40) | (ix << 26) | (iy << 13) | iz;
		}

		private static void dijkstra(float[] dist, int[][] adj, float[] p, float[] cost, int[] part, int only) {
			PriorityQueue<long[]> q = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[0]), Float.intBitsToFloat((int) b[0])));
			for (int k = 0; k < dist.length; k++) {
				if (part[k] == only && !Float.isInfinite(dist[k])) {
					q.add(new long[] { Float.floatToIntBits(dist[k]), k });
				}
			}
			while (!q.isEmpty()) {
				long[] top = q.poll();
				int k = (int) top[1];
				float dk = Float.intBitsToFloat((int) top[0]);
				if (dk > dist[k]) {
					continue;
				}
				for (int j : adj[k]) {
					if (part[j] != only) {
						continue;
					}
					float nd = dk + dd(p, k, j) * 0.5f * (cost[k] + cost[j]);
					if (nd < dist[j]) {
						dist[j] = nd;
						q.add(new long[] { Float.floatToIntBits(nd), j });
					}
				}
			}
		}
	}

	/**
	 * The travel cost at a body point: low along a branching vein network (two octaves of ridged value noise, in body space so it is
	 * seamless across faces), high between -- the goo races along the veins and fills in after.
	 */
	static float cost(float x, float y, float z) {
		float r1 = 1f - Math.abs(vnoise(x * 0.42f + 3.1f, y * 0.42f, z * 0.42f - 1.7f));
		float r2 = 1f - Math.abs(vnoise(x * 0.95f - 8.3f, y * 0.95f + 2.2f, z * 0.95f + 5.1f));
		float v = Math.max((float) Math.pow(r1, 9), 0.75f * (float) Math.pow(r2, 7));
		return 1.3f - 0.95f * v;
	}

	private static float hash3(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 2147483647;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
	}

	/** Smooth value noise, -1 .. 1. */
	static float vnoise(float x, float y, float z) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
		float xf = x - xi, yf = y - yi, zf = z - zi;
		float u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf), w = zf * zf * (3 - 2 * zf);
		float r = 0f;
		for (int dz = 0; dz < 2; dz++) {
			for (int dy = 0; dy < 2; dy++) {
				for (int dx = 0; dx < 2; dx++) {
					r += hash3(xi + dx, yi + dy, zi + dz) * (dx == 1 ? u : 1 - u) * (dy == 1 ? v : 1 - v) * (dz == 1 ? w : 1 - w);
				}
			}
		}
		return r * 2f - 1f;
	}

	/** Where face texel (fu, fv) sits on a box (player-skin box-UV unwrap: right side | front | left side | back). */
	static float[] point(int face, float fu, float fv, float[] g) {
		float ox = g[0], oy = g[1], oz = g[2], sx = g[3], sy = g[4], sz = g[5];
		fu = Math.max(0f, Math.min(1f, fu));
		fv = Math.max(0f, Math.min(1f, fv));
		return switch (face) {
			case 0 -> new float[] { ox + sx * fu, oy + sy, oz + sz * (1 - fv) };       // top (bottom row meets the front)
			case 1 -> new float[] { ox + sx * fu, oy, oz + sz * (1 - fv) };            // bottom
			case 2 -> new float[] { ox, oy + sy * (1 - fv), oz + sz * (1 - fu) };      // right side (-x), its right edge meets the front
			case 3 -> new float[] { ox + sx * fu, oy + sy * (1 - fv), oz };            // front (-z)
			case 4 -> new float[] { ox + sx, oy + sy * (1 - fv), oz + sz * fu };       // left side (+x), its left edge meets the front
			default -> new float[] { ox + sx * (1 - fu), oy + sy * (1 - fv), oz + sz }; // back (+z)
		};
	}

	static float[] normal(int face) {
		return switch (face) {
			case 0 -> new float[] { 0, 1, 0 };
			case 1 -> new float[] { 0, -1, 0 };
			case 2 -> new float[] { -1, 0, 0 };
			case 3 -> new float[] { 0, 0, -1 };
			case 4 -> new float[] { 1, 0, 0 };
			default -> new float[] { 0, 0, 1 };
		};
	}

	// ------------------------------------------------------------------ small maths

	private static float d3(float[] p, int k, float[] q) {
		float x = p[k * 3] - q[0], y = p[k * 3 + 1] - q[1], z = p[k * 3 + 2] - q[2];
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	private static float dd(float[] p, int a, int b) {
		float x = p[a * 3] - p[b * 3], y = p[a * 3 + 1] - p[b * 3 + 1], z = p[a * 3 + 2] - p[b * 3 + 2];
		return (float) Math.sqrt(x * x + y * y + z * z);
	}

	static float[] cross(float[] a, float[] b) {
		return new float[] { a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0] };
	}

	static float[] norm(float[] a) {
		float l = (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
		return l < 1e-6f ? new float[] { 0, 1, 0 } : new float[] { a[0] / l, a[1] / l, a[2] / l };
	}

	static float smooth(float x) {
		float u = Math.max(0f, Math.min(1f, x));
		return u * u * (3f - 2f * u);
	}

	static float hash01(int n) {
		return hash3(n, n * 31 + 17, n * 7 + 3);
	}

	private static int lum(int abgr) {
		return Math.min(255, ((abgr & 0xFF) * 299 + ((abgr >> 8) & 0xFF) * 587 + ((abgr >> 16) & 0xFF) * 114) / 1000);
	}

	private static int alpha(int abgr) {
		return abgr >>> 24;
	}

	private static int abgr(int a, int b, int g, int r) {
		return (a & 0xFF) << 24 | (b & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	/** ABGR lerp (alpha from {@code a}). */
	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		return (a & 0xFF000000) | (bl & 0xFF) << 16 | (g & 0xFF) << 8 | (r & 0xFF);
	}

	private static int scale(int c, float k) {
		int r = Math.round((c & 0xFF) * k), g = Math.round(((c >> 8) & 0xFF) * k), b = Math.round(((c >> 16) & 0xFF) * k);
		return (c & 0xFF000000) | b << 16 | g << 8 | r;
	}

	/** The wet highlight for a body colour (the suit bake's gloss: a near-white of the hue), opaque ABGR. */
	static int glossAbgr(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float m = Math.max(r, Math.max(g, b));
		float hr = m < 0.03f ? 0.82f : r / m, hg = m < 0.03f ? 0.86f : g / m, hb = m < 0.03f ? 1f : b / m;
		int R = Math.round(Math.min(1f, 0.72f + 0.28f * hr) * 255), G = Math.round(Math.min(1f, 0.72f + 0.28f * hg) * 255),
				B = Math.round(Math.min(1f, 0.72f + 0.28f * hb) * 255);
		return abgr(255, B, G, R);
	}
}

package com.projecthero.mod.client.omnitrix.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttAnimatable;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttFirstPerson;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttModel;
import com.projecthero.mod.client.omnitrix.alien.wildmutt.WildmuttRenderer;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Wildmutt's client side -- the first alien with a fully custom body ({@link #customBody}): a GeckoLib quadruped
 * ({@link WildmuttRenderer}, clips picked by {@link WildmuttAnimatable}) in place of the player model, his own clawed arms in
 * first person ({@link WildmuttFirstPerson}, both always in view), the scent trail to a tracked creature, the Sniff ripple and
 * the scent of buried ores / chests (v0.17.1: no HUD row -- user: "remove the hud thing that shows it looks weird"). The outlines of what he senses are {@code WildmuttSense} (two glow mixins).
 */
public final class WildmuttClient implements AlienClientKit {
	private static final DustParticleOptions SCENT = new DustParticleOptions(new Vector3f(1.0f, 0.42f, 0.25f), 0.7f);
	private static final DustParticleOptions RIPPLE = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.5f), 1.1f);

	// first-person clips for the moves: {tick, pitch, yaw, roll, x, y, z}; two-handed (both paws)
	private static final FirstPersonClip FP_POUNCE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, 18, 0, 0, 0, -0.15f, 0.15f), FirstPersonClip.f(6, -70, 8, 0, 0.06f, 0.42f, -0.2f),
			FirstPersonClip.f(9, 25, 14, 0, 0.1f, -0.1f, -0.3f), FirstPersonClip.f(17, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_MAUL = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(2, -80, -10, 20, 0, 0.5f, 0.1f), FirstPersonClip.f(5, 20, 25, -10, 0.2f, -0.1f, -0.25f),
			FirstPersonClip.f(8, -80, -10, 20, 0, 0.5f, 0.1f), FirstPersonClip.f(11, 20, 25, -10, 0.2f, -0.1f, -0.25f),
			FirstPersonClip.f(14, -95, 0, 0, 0.05f, 0.6f, 0.1f), FirstPersonClip.f(17, 30, 10, 0, 0.1f, -0.25f, -0.2f),
			FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(7, -100, 5, 0, 0.05f, 0.65f, 0.2f), FirstPersonClip.f(9, 35, 10, 0, 0.05f, -0.35f, -0.15f),
			FirstPersonClip.f(14, 30, 10, 0, 0.05f, -0.3f, -0.12f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(5, 10, -25, 0, -0.15f, -0.12f, 0.1f), FirstPersonClip.f(24, 12, -28, 0, -0.18f, -0.14f, 0.1f),
			FirstPersonClip.f(29, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FRENZY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(4, -60, 0, 0, 0, 0.35f, 0), FirstPersonClip.f(7, 25, 10, 0, 0.05f, -0.2f, -0.1f),
			FirstPersonClip.f(11, -60, 0, 0, 0, 0.35f, 0), FirstPersonClip.f(14, 25, 10, 0, 0.05f, -0.2f, -0.1f),
			FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_QUILLS = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, 14, -10, 6, -0.05f, -0.18f, 0.1f), FirstPersonClip.f(8, 14, -10, -6, -0.05f, -0.18f, 0.1f),
			FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LEAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0),
			FirstPersonClip.f(3, -40, 0, 0, 0, 0.25f, -0.1f), FirstPersonClip.f(14, -30, 4, 0, 0, 0.2f, -0.1f),
			FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return WildmuttModel.TEXTURE;
	}

	// ---------------------------------------------------------------- the custom body

	@Override
	public boolean customBody() {
		return true;
	}

	@Override
	public void buildBody(EntityRendererProvider.Context context) {
		WildmuttRenderer.rebuild(context);
	}

	@Override
	public void renderBody(AbstractClientPlayer p, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		WildmuttRenderer r = WildmuttRenderer.get();
		if (r != null) {
			r.render(p, yaw, partial, pose, buffers, light);
		}
	}

	@Override
	public boolean renderFirstPersonHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, boolean right) {
		WildmuttFirstPerson.render(pose, buffers, light, p, right);
		return true;
	}

	@Override
	public boolean bothHandsInFirstPerson(AbstractClientPlayer p) {
		return true;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WildmuttKit.ANIM_POUNCE -> FP_POUNCE;
			case WildmuttKit.ANIM_MAUL -> FP_MAUL;
			case WildmuttKit.ANIM_SLAM -> FP_SLAM;
			case WildmuttKit.ANIM_ROAR -> FP_ROAR;
			case WildmuttKit.ANIM_FRENZY -> FP_FRENZY;
			case WildmuttKit.ANIM_QUILLS -> FP_QUILLS;
			case WildmuttKit.ANIM_LEAP -> FP_LEAP;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- ambience, the scent trail

	private static boolean wasSniffing;
	private static int sniffStart;
	private static final int ORE_RADIUS = 12;

	/**
	 * v0.17.1 (user: "make his smelling power better"): while he sniffs -- a scent ripple rolling out along the ground for the
	 * first second, then the scent of what is buried nearby (ores, chests, spawners) leaking up through the ground: a wisp in its
	 * colour rises from the first open block between it and him, so he can dig straight to it. His eyes only.
	 */
	private static void sniffFx(AbstractClientPlayer p, int t) {
		var level = p.level();
		if (t < 22) {
			double r = 1.0 + t * 1.1;
			int n = 10 + (int) (r * 3);
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + t * 0.05;
				double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
				if (Math.abs(y - p.getY()) < 4) {
					level.addParticle(RIPPLE, x, y + 0.15, z, 0, 0.01, 0);
				}
			}
		}
		if (t % 8 != 4) {
			return;
		}
		net.minecraft.core.BlockPos c = p.blockPosition();
		var cur = new net.minecraft.core.BlockPos.MutableBlockPos();
		Vec3 eye = p.getEyePosition();
		int shown = 0;
		for (int dx = -ORE_RADIUS; dx <= ORE_RADIUS && shown < 40; dx++) {
			for (int dy = -ORE_RADIUS; dy <= 4 && shown < 40; dy++) {
				for (int dz = -ORE_RADIUS; dz <= ORE_RADIUS && shown < 40; dz++) {
					cur.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					int col = buriedScent(level.getBlockState(cur));
					if (col == 0) {
						continue;
					}
					// walk from the block toward his eyes to the first open block: the scent seeps out there
					Vec3 from = Vec3.atCenterOf(cur), d = eye.subtract(from);
					double len = d.length();
					Vec3 step = d.scale(1.0 / Math.max(1e-3, len));
					Vec3 out = null;
					for (double s = 0.8; s < len; s += 0.5) {
						Vec3 q = from.add(step.scale(s));
						net.minecraft.core.BlockPos qb = net.minecraft.core.BlockPos.containing(q);
						if (!qb.equals(cur) && level.getBlockState(qb).getCollisionShape(level, qb).isEmpty()) {
							out = q;
							break;
						}
					}
					if (out == null) {
						continue;
					}
					shown++;
					DustParticleOptions dust = new DustParticleOptions(new Vector3f(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f), 0.9f);
					for (int i = 0; i < 3; i++) {
						level.addParticle(dust, out.x + (p.getRandom().nextDouble() - 0.5) * 0.3, out.y + i * 0.18, out.z + (p.getRandom().nextDouble() - 0.5) * 0.3, 0,
								0.03, 0);
					}
				}
			}
		}
	}

	/** The colour a buried block smells of, or 0: ores by what they give, chests and barrels, spawners. */
	private static int buriedScent(net.minecraft.world.level.block.state.BlockState st) {
		var b = st.getBlock();
		if (b == net.minecraft.world.level.block.Blocks.DIAMOND_ORE || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_DIAMOND_ORE) {
			return 0x5FF0E8;
		}
		if (b == net.minecraft.world.level.block.Blocks.EMERALD_ORE || b == net.minecraft.world.level.block.Blocks.DEEPSLATE_EMERALD_ORE) {
			return 0x40E060;
		}
		if (b == net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS) {
			return 0x8A5A48;
		}
		if (st.is(net.minecraft.tags.BlockTags.GOLD_ORES)) {
			return 0xFFD040;
		}
		if (st.is(net.minecraft.tags.BlockTags.IRON_ORES)) {
			return 0xE0B090;
		}
		if (st.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)) {
			return 0xFF2020;
		}
		if (st.is(net.minecraft.tags.BlockTags.LAPIS_ORES)) {
			return 0x3050FF;
		}
		if (st.is(net.minecraft.tags.BlockTags.COPPER_ORES)) {
			return 0xE07040;
		}
		if (st.is(net.minecraft.tags.BlockTags.COAL_ORES)) {
			return 0x505050;
		}
		if (b == net.minecraft.world.level.block.Blocks.CHEST || b == net.minecraft.world.level.block.Blocks.TRAPPED_CHEST || b == net.minecraft.world.level.block.Blocks.BARREL) {
			return 0xC08A40;
		}
		if (b == net.minecraft.world.level.block.Blocks.SPAWNER || b == net.minecraft.world.level.block.Blocks.TRIAL_SPAWNER) {
			return 0xB040FF;
		}
		return 0;
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		Minecraft mc = Minecraft.getInstance();
		if (p != mc.player || !(Aliens.kit(WildmuttKit.ID) instanceof WildmuttKit k)) {
			return;
		}
		boolean sniffing = k.flag(p, WildmuttKit.F_SNIFF);
		if (sniffing && !wasSniffing) {
			sniffStart = p.tickCount;
		}
		wasSniffing = sniffing;
		if (sniffing) {
			sniffFx(p, p.tickCount - sniffStart);
		}
		// the trail to the tracked creature: a drift of scent along the ground toward it (his eyes only)
		Entity t = k.tracked(p);
		if (t != null && p.tickCount % 3 == 0) {
			Vec3 from = p.position().add(0, 0.4, 0);
			Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
			Vec3 d = to.subtract(from);
			double len = d.length();
			int n = (int) Math.min(24, len);
			double phase = (p.tickCount % 12) / 12.0;
			for (int i = 1; i <= n; i++) {
				double f = (i - 1 + phase) / Math.max(1, len);
				Vec3 q = from.add(d.scale(f));
				double wob = Mth.sin((float) (i * 1.7 + p.tickCount * 0.2)) * 0.12;
				p.level().addParticle(SCENT, q.x + wob, q.y, q.z - wob, 0, 0.01, 0);
			}
		}
	}
}

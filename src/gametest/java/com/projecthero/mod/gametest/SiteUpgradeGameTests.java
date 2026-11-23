package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.LootItems;
import com.projecthero.mod.loot.sites.SiteAnnexPiece;
import com.projecthero.mod.loot.sites.SiteGuards;
import com.projecthero.mod.loot.sites.SiteTheme;
import com.projecthero.mod.loot.sites.SiteUpgrade;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5 Tier 2 Sites: every one of the 19 old sites (the {@code locator/tier2} tag) has an upgrade theme; each annex
 * builds whole on a plinth with a Tier 2 Hero Vault where it says, its secured entrance (iron door + button, or a
 * carpet-covered trapdoor over a ladder), and its guard squad (3-6, persistent, tagged, never a daylight-burning
 * vanilla zombie / skeleton); the planner puts the annex just outside the site's box, never on top of it.
 */
public class SiteUpgradeGameTests implements FabricGameTest {
	private static final int LIFT = 40;

	private static SiteAnnexPiece buildOnPlinth(GameTestHelper helper, SiteTheme theme, int rotation) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, LIFT, 0);
		// v0.17.7: the back wing reaches 13 blocks behind the centre
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -10, -16), at.offset(16, 10, 16))) {
			level.setBlock(q, (q.getY() > at.getY() ? Blocks.AIR : q.getY() == at.getY() ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
		}
		SiteAnnexPiece piece = new SiteAnnexPiece(theme, at.getX(), at.getY() + 1, at.getZ() - 1, rotation, 0x5175L * (theme.ordinal() + 3));
		SiteUpgrade.buildAll(level, piece);
		return piece;
	}

	private static void wipe(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, LIFT, 0);
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at).inflate(20))) {
			m.discard();
		}
		for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(at).inflate(20))) {
			e.discard();
		}
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -10, -16), at.offset(16, 10, 16))) {
			level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
		}
		com.projecthero.mod.alarm.AlarmZones.get(level).removeWithin(new BoundingBox(at.getX() - 16, at.getY() - 10, at.getZ() - 16,
				at.getX() + 16, at.getY() + 10, at.getZ() + 16));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyTierTwoSiteHasAnUpgrade(GameTestHelper helper) {
		TagKey<Structure> tier2 = TagKey.create(Registries.STRUCTURE, ResourceLocation.fromNamespaceAndPath("projecthero", "locator/tier2"));
		var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
		int n = 0;
		for (var holder : registry.getTagOrEmpty(tier2)) {
			String id = holder.unwrapKey().orElseThrow().location().getPath();
			helper.assertTrue(SiteTheme.byId(id) != null, "a site upgrade theme for " + id);
			n++;
		}
		helper.assertTrue(n == SiteTheme.values().length, "19 tier 2 sites, one theme each (tag " + n + ", themes " + SiteTheme.values().length + ")");
		for (SiteTheme t : SiteTheme.values()) {
			helper.assertTrue(t.minGuards >= 3 && t.maxGuards <= 6 && t.minGuards <= t.maxGuards, t + ": a squad of 3-6");
			helper.assertTrue(t.roster.size() >= t.maxGuards, t + ": enough roster for the biggest squad");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "site_annex_build", timeoutTicks = 200)
	public void everyAnnexHoldsATierTwoVaultAndItsGuards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (SiteTheme theme : SiteTheme.values()) {
			SiteAnnexPiece piece = buildOnPlinth(helper, theme, theme.ordinal() % 4);
			BlockPos v = piece.vaultPos();
			helper.assertTrue(level.getBlockState(v).is(LootItems.HERO_VAULT), theme + ": a hero_vault at " + v + " (found " + level.getBlockState(v) + ")");
			helper.assertTrue(HeroVault.tierAt(level, v) == 2, theme + ": the vault is tier 2");
			// v0.17.7 layout 2: the vault room is behind a locked Blast Door (the iron door / hatch is layout 1, see VaultSitesGameTests)
			helper.assertTrue(piece.layout() == SiteAnnexPiece.LAYOUT, theme + ": a new annex is layout " + SiteAnnexPiece.LAYOUT);
			var door = level.getBlockState(piece.blastDoorPos());
			helper.assertTrue(door.is(com.projecthero.mod.alarm.block.AlarmBlocks.BLAST_DOOR) && door.getValue(com.projecthero.mod.alarm.block.BlastDoorBlock.LOCKED),
					theme + ": a locked Blast Door into the vault room (found " + door + ")");
			List<Mob> guards = level.getEntitiesOfClass(Mob.class, AABB.of(piece.getBoundingBox()).inflate(2), SiteGuards::isGuard);
			helper.assertTrue(guards.size() == piece.totalGuards(), theme + ": " + piece.totalGuards() + " guards (got " + guards.size() + ")");
			helper.assertTrue(guards.size() >= 5 && guards.size() <= 8, theme + ": a squad of 3-6 plus the Captain and the back-room guard");
			helper.assertTrue(guards.stream().allMatch(Mob::isPersistenceRequired), theme + ": the guards don't despawn");
			helper.assertTrue(guards.stream().noneMatch(m -> m.getType() == EntityType.ZOMBIE || m.getType() == EntityType.SKELETON
					|| m.getType() == EntityType.STRAY || m.getType() == EntityType.DROWNED), theme + ": no daylight-burning vanilla undead");
			wipe(helper);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plannerPutsTheAnnexBesideTheSite(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos c = helper.absolutePos(new BlockPos(3, 1, 3));
		for (SiteTheme theme : new SiteTheme[] { SiteTheme.GAMMA_LAB, SiteTheme.TOMB_OF_NABU, SiteTheme.GRAVEYARD, SiteTheme.HYDROSTATIC_FACILITY }) {
			BoundingBox site = new BoundingBox(c.getX() - 8, c.getY() - 6, c.getZ() - 8, c.getX() + 8, c.getY() + 10, c.getZ() + 8);
			SiteAnnexPiece piece = SiteUpgrade.plan(theme, site, level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
			BoundingBox a = piece.getBoundingBox();
			boolean apart = a.maxX() < site.minX() || a.minX() > site.maxX() || a.maxZ() < site.minZ() || a.minZ() > site.maxZ();
			helper.assertTrue(apart, theme + ": the annex " + a + " stays clear of the site " + site);
			int gap = Math.max(Math.max(site.minX() - a.maxX(), a.minX() - site.maxX()), Math.max(site.minZ() - a.maxZ(), a.minZ() - site.maxZ()));
			helper.assertTrue(gap >= 3 && gap <= 4, theme + ": 3 blocks of ground between them (got " + gap + ")");
			BlockPos mid = site.getCenter();
			helper.assertTrue(piece.doorPos().distManhattan(new BlockPos(mid.getX(), piece.doorPos().getY(), mid.getZ()))
					< a.getCenter().atY(piece.doorPos().getY()).distManhattan(new BlockPos(mid.getX(), piece.doorPos().getY(), mid.getZ())),
					theme + ": the door faces the site");
		}
		helper.succeed();
	}
}

package com.projecthero.mod.gametest.mixin;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev/CI-speed selection, driven by system properties set from Gradle ({@code build.gradle}, "runGameTest"):
 * <ul>
 * <li>{@code -Dprojecthero.gametest.filter=<regex>} ({@code -PtestFilter}): only tests whose "classname.methodname" or
 * batch name matches (case-insensitive) are registered, so one class / one test runs in seconds.</li>
 * <li>{@code -Dprojecthero.gametest.shard=i/n} ({@code -PtestShard}): only the i-th of n disjoint slices of the suite,
 * so CI can run the slices on parallel runners. Tests of a named batch always land in the same slice (they share state
 * on purpose); the big default batch is split test by test.</li>
 * </ul>
 * Neither set = every test, exactly as before.
 */
@Mixin(GameTestRegistry.class)
public abstract class GameTestFilterMixin {
	@Inject(method = "getAllTestFunctions", at = @At("RETURN"), cancellable = true)
	private static void projecthero$filter(CallbackInfoReturnable<Collection<TestFunction>> cir) {
		String filter = System.getProperty("projecthero.gametest.filter");
		String shard = System.getProperty("projecthero.gametest.shard");
		boolean filtered = filter != null && !filter.isBlank();
		boolean sharded = shard != null && !shard.isBlank();
		if (!filtered && !sharded) {
			return;
		}
		Pattern pattern = filtered ? Pattern.compile(filter.trim(), Pattern.CASE_INSENSITIVE) : null;
		int index = 0;
		int count = 1;
		if (sharded) {
			String[] parts = shard.trim().split("/");
			index = Integer.parseInt(parts[0].trim()) - 1;
			count = Integer.parseInt(parts[1].trim());
			if (count < 1 || index < 0 || index >= count) {
				throw new IllegalArgumentException("projecthero.gametest.shard must be i/n with 1 <= i <= n, got " + shard);
			}
		}
		final int wantedShard = index;
		final int shards = count;
		List<TestFunction> kept = cir.getReturnValue().stream()
				.filter(t -> pattern == null || pattern.matcher(t.testName()).find() || pattern.matcher(t.batchName()).find())
				.filter(t -> shards == 1
						|| Math.floorMod(("defaultBatch".equals(t.batchName()) ? t.testName() : t.batchName()).hashCode(), shards) == wantedShard)
				.toList();
		System.out.println("[projecthero-gametest] filter '" + filter + "' shard '" + shard + "' kept " + kept.size() + " of "
				+ cir.getReturnValue().size() + " tests");
		if (kept.isEmpty()) {
			throw new IllegalStateException("projecthero.gametest filter/shard matched no tests");
		}
		cir.setReturnValue(kept);
	}
}

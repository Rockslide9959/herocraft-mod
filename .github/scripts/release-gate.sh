#!/usr/bin/env bash
# v0.16.1: the release gate (release.yml "tested" job). Decides whether the tagged commit still needs the gametest
# suite and whether master CI already built its jar.
#
#   GH_TOKEN=... REPO=owner/name SHA=<tagged commit> bash .github/scripts/release-gate.sh
#
# Needs a full-history checkout (git merge-base, select-gametests.js). Writes to $GITHUB_OUTPUT (and prints):
#   green=true    the FULL suite already passed on master for this commit, or for an earlier master commit with
#                 nothing since that the suite can see (select-gametests.js says "none": docs, client code, textures,
#                 models, sounds, the version bump...) -- the release does not run the suite again
#   jar_run=<id>  a build.yml run on this exact commit whose "build" job passed and whose "Artifacts" jar is still
#                 downloadable -- the release publishes that jar instead of building it again (empty = build it)
# A build.yml run of this commit that is still going is waited for (up to 25 min), so pushing master and the tag
# together still skips the second suite run.
set -uo pipefail

: "${REPO:?}" "${SHA:?}"
green=false
jar_run=""
reason="no green full-suite master run covers this commit"

# a finished run that passed and ran the FULL suite (its gametest jobs are named "gametest full (slice i/n)")
full_green() {
	local n
	n="$(gh run view "$1" -R "$REPO" --json conclusion,jobs \
		--jq 'if .conclusion == "success" then [.jobs[] | select(.name | startswith("gametest full"))] | length else 0 end' \
		2>/dev/null)" || n=0
	[ "${n:-0}" -ge 1 ]
}

# a run whose "build" job passed and whose jar artifact has not expired
jar_ok() {
	local b a
	b="$(gh run view "$1" -R "$REPO" --json jobs \
		--jq '[.jobs[] | select(.name == "build" and .conclusion == "success")] | length' 2>/dev/null)" || b=0
	a="$(gh api "repos/$REPO/actions/runs/$1/artifacts" \
		--jq '[.artifacts[] | select(.name == "Artifacts" and (.expired | not))] | length' 2>/dev/null)" || a=0
	[ "${b:-0}" -ge 1 ] && [ "${a:-0}" -ge 1 ]
}

runs_of_commit() {
	gh run list -R "$REPO" --workflow build.yml --commit "$SHA" --limit 20 --json databaseId,status "$@"
}

# 1. wait for any build.yml run of this commit that has not finished yet
for id in $(runs_of_commit --jq '.[] | select(.status != "completed") | .databaseId'); do
	echo "waiting for build.yml run $id on $SHA"
	timeout 1500 gh run watch "$id" -R "$REPO" --interval 20 > /dev/null || echo "run $id did not finish cleanly"
done

# 2. this exact commit: a green full run, and a reusable jar
for id in $(runs_of_commit --jq '.[].databaseId'); do
	if [ "$green" != true ] && full_green "$id"; then
		green=true
		reason="build.yml run $id passed the full suite on this commit"
	fi
	if [ -z "$jar_run" ] && jar_ok "$id"; then
		jar_run="$id"
	fi
done

# 3. otherwise the newest earlier master commit that passed the full suite, if nothing the suite sees changed since
#    (only the 10 newest green ancestors are looked at -- each look is an API call on the release's critical path)
if [ "$green" != true ]; then
	looked=0
	for row in $(gh run list -R "$REPO" --workflow build.yml --branch master --status success --limit 40 \
			--json databaseId,headSha --jq '.[] | "\(.databaseId):\(.headSha)"'); do
		id="${row%%:*}"
		base="${row#*:}"
		if [ "$base" = "$SHA" ] || ! git merge-base --is-ancestor "$base" "$SHA" 2>/dev/null; then
			continue
		fi
		looked=$((looked + 1))
		if [ "$looked" -gt 10 ]; then
			break
		fi
		if ! full_green "$id"; then
			continue
		fi
		mode="$(GITHUB_OUTPUT='' FORCE_FULL='' node .github/scripts/select-gametests.js "$base" "$SHA" | sed -n 's/^mode=//p')"
		if [ "$mode" = none ]; then
			green=true
			reason="build.yml run $id passed the full suite on $base, and nothing since then reaches the gametests"
		else
			reason="newest fully tested master commit is $base, but the changes since then need tests ($mode)"
		fi
		break
	done
fi

echo "$reason"
{
	echo "green=$green"
	echo "jar_run=$jar_run"
} | tee -a "${GITHUB_OUTPUT:-/dev/null}"

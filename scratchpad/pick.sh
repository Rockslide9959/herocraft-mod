#!/usr/bin/env bash
# usage: pick.sh <commit>...   (run inside the target worktree)
# cherry-picks each commit; JSON / Markdown conflicts are union-merged (+ comma fix for JSON);
# stops on the first Java (or other) conflict so it can be resolved by hand.
S="C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad"
for c in "$@"; do
	echo "== $c"
	if git cherry-pick "$c" >/dev/null 2>&1; then
		continue
	fi
	manual=""
	for f in $(git status --short | grep -E "^(UU|AA)" | cut -c4-); do
		case "$f" in
			*.json) node "$S/resolve.js" "$f" both && node "$S/fixjson.js" "$f" && git add "$f" ;;
			*.md) node "$S/resolve.js" "$f" both && git add "$f" ;;
			*) manual="$manual $f" ;;
		esac
	done
	if [ -n "$manual" ]; then
		echo "MANUAL:$manual"
		exit 1
	fi
	git -c core.editor=true cherry-pick --continue >/dev/null || { echo "continue failed"; exit 1; }
done
echo "all picked"

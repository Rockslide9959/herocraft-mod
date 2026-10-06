#!/usr/bin/env bash
# Continue a rebase, union-merging JSON / Markdown conflicts (+ comma fix); stops on anything else.
S="C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad"
for i in $(seq 1 30); do
	U=$(git status --short | grep -E "^(UU|AA)" | cut -c4-)
	if [ -z "$U" ]; then
		if [ -d "$(git rev-parse --git-path rebase-merge)" ] || [ -d "$(git rev-parse --git-path rebase-apply)" ]; then
			GIT_EDITOR=true git rebase --continue >/dev/null 2>&1 || true
			continue
		fi
		echo "rebase done"
		exit 0
	fi
	for f in $U; do
		case "$f" in
			*.json) node "$S/resolve.js" "$f" both && node "$S/fixjson.js" "$f" && git add "$f" ;;
			*.md) node "$S/resolve.js" "$f" both && git add "$f" ;;
			*) echo "MANUAL: $f"; exit 1 ;;
		esac
	done
	GIT_EDITOR=true git rebase --continue >/dev/null 2>&1 || true
done
echo "gave up"
exit 1

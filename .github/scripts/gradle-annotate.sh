#!/usr/bin/env bash
# Runs gradle with the given arguments, and when it fails, repeats the first errors as GitHub
# annotations (which show on the commit's checks without opening the job log).
set +e
gradle "$@" --console=plain 2>&1 | tee gradle-build.log
code=${PIPESTATUS[0]}
if [ "$code" -ne 0 ]; then
  prefix="file://$GITHUB_WORKSPACE/"
  grep -E '^e: ' gradle-build.log | sed "s#$prefix##" | head -20 | while IFS= read -r line; do
    echo "::error::${line}"
  done
  # Dependency or configuration problems end up in the "What went wrong" block.
  block=$(grep -A8 'What went wrong' gradle-build.log | head -12 | tr '\n' '|' | sed 's/|/ | /g')
  [ -n "$block" ] && echo "::error::${block}"
fi
exit "$code"

#!/usr/bin/env bash
set -euo pipefail

PATCH="patches/android9/0001-EthernetNetworkFactory-fix-releaseNetworkFor-refcount.patch"

test -s "$PATCH"
grep -Fq 'if (--network.refCount == 1)' "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$PATCH"
grep -Fq 'network.stop();' "$PATCH"

# Validate unified-diff syntax without applying it to an unknown OEM tree.
git apply --numstat "$PATCH" >/dev/null

echo "PASS: upstream Android 9 Ethernet refCount patch is present and structurally valid."
echo "NOTE: OEM source applicability and framework build require the matching Toshiba source tree."

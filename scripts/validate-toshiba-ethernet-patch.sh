#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/patches/android9/0001-EthernetNetworkFactory-fix-releaseNetworkFor-refcount.patch"
test -s "$PATCH"
grep -Fq 'if (--network.refCount == 1)' "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$PATCH"
grep -Fq 'network.stop();' "$PATCH"
git apply --numstat "$PATCH" >/dev/null

# Apply against a minimal Android 9 source fixture to catch malformed hunks
# and verify the intended source transformation without touching an OEM tree.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
TARGET="$TMP/java/com/android/server/ethernet/EthernetNetworkFactory.java"
mkdir -p "$(dirname "$TARGET")"
cat > "$TARGET" <<'EOF'
class EthernetNetworkFactory {
    protected void releaseNetworkFor(NetworkRequest networkRequest) {
        NetworkInterfaceState network = networkForRequest(networkRequest);
        if (network == null) {
            Log.e(TAG, "needNetworkFor, failed to get a network for " + networkRequest);
            return;
        }

        if (--network.refCount == 1) {
            network.stop();
        }
    }
}
EOF
git -C "$TMP" init -q
git -C "$TMP" apply --check "$PATCH"
git -C "$TMP" apply "$OLDPWD/$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$TARGET"
! grep -Fq 'if (--network.refCount == 1)' "$TARGET"

echo "PASS: patch syntax, applicability fixture, and refCount correction verified."
echo "NOTE: Toshiba OEM source applicability and system-image build remain separate checks."

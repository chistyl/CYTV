#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/patches/android9/0001-EthernetNetworkFactory-fix-releaseNetworkFor-refcount.patch"
test -s "$PATCH"
grep -Fq 'if (--network.refCount == 1)' "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$PATCH"
grep -Fq 'network.stop();' "$PATCH"
git apply --numstat "$PATCH" >/dev/null

# Also verify the patch against a real AOSP framework source revision known
# to contain the pre-fix logic (Android Q preview 1). This is a source-compatibility
# check only; it does not identify the Toshiba OEM revision.
AOSP_URL="https://android.googlesource.com/platform/frameworks/opt/net/ethernet/+/refs/tags/android-q-preview-1/java/com/android/server/ethernet/EthernetNetworkFactory.java?format=TEXT"
AOSP_TMP="$(mktemp -d)"
trap 'rm -rf "$TMP" "$AOSP_TMP"' EXIT
AOSP_TARGET="$AOSP_TMP/java/com/android/server/ethernet/EthernetNetworkFactory.java"
mkdir -p "$(dirname "$AOSP_TARGET")"
curl -fsSL "$AOSP_URL" | base64 -d > "$AOSP_TARGET"
grep -Fq 'if (--network.refCount == 1)' "$AOSP_TARGET"
git -C "$AOSP_TMP" init -q
git -C "$AOSP_TMP" add .
git -C "$AOSP_TMP" -c user.name=validator -c user.email=validator@example.invalid commit -qm 'AOSP pre-fix fixture'
git -C "$AOSP_TMP" apply --check "$PATCH"
git -C "$AOSP_TMP" apply "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$AOSP_TARGET"
! grep -Fq 'if (--network.refCount == 1)' "$AOSP_TARGET"
echo "PASS: patch applies to real AOSP android-q-preview-1 source."

# Apply against a minimal Android 9 source fixture to catch malformed hunks
# and verify the intended source transformation without touching an OEM tree.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
TARGET="$TMP/java/com/android/server/ethernet/EthernetNetworkFactory.java"
mkdir -p "$(dirname "$TARGET")"
cat > "$TARGET" <<'JAVA'
class EthernetNetworkFactory {
    static final String TAG = "EthernetNetworkFactory";
    static class NetworkRequest {}
    static class NetworkInterfaceState {
        int refCount = 2;
        boolean stopped = false;
        void stop() { stopped = true; }
    }
    static class Log {
        static void e(String tag, String message) {}
    }
    final NetworkInterfaceState network = new NetworkInterfaceState();
    NetworkInterfaceState networkForRequest(NetworkRequest request) { return network; }

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

    public static void main(String[] args) {
        EthernetNetworkFactory factory = new EthernetNetworkFactory();
        NetworkRequest request = new NetworkRequest();
        factory.releaseNetworkFor(request);
        if (factory.network.refCount != 1 || factory.network.stopped) {
            throw new AssertionError("Ethernet stopped while one request remained");
        }
        factory.releaseNetworkFor(request);
        if (factory.network.refCount != 0 || !factory.network.stopped) {
            throw new AssertionError("Ethernet did not stop after final request release");
        }
        System.out.println("PASS: refCount 2->1 keeps Ethernet; 1->0 stops Ethernet");
    }
}
JAVA
git -C "$TMP" init -q
git -C "$TMP" apply --check "$PATCH"
git -C "$TMP" apply "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$TARGET"
! grep -Fq 'if (--network.refCount == 1)' "$TARGET"
javac "$TARGET"
java -cp "$(dirname "$TARGET")" EthernetNetworkFactory
echo "PASS: patch syntax, applicability fixture, and refCount correction verified."
echo "NOTE: Toshiba OEM source applicability and system-image build remain separate checks."

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATCH="$ROOT/patches/android9/0001-EthernetNetworkFactory-fix-releaseNetworkFor-refcount.patch"
AOSP_TMP="$(mktemp -d)"
TMP="$(mktemp -d)"
cleanup() { rm -rf "$TMP" "$AOSP_TMP"; }
trap cleanup EXIT

test -s "$PATCH"
grep -Fq 'if (--network.refCount == 1)' "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$PATCH"
grep -Fq 'network.stop();' "$PATCH"
grep -Fq 'releaseNetworkFor, failed to get a network for' "$PATCH"
git apply --numstat "$PATCH" >/dev/null

# Verify against a real Android Pie maintenance source tree that contains the
# pre-fix logic. This mutable public branch is a compatibility fixture only;
# it is not Toshiba OEM source proof or an immutable framework revision.
AOSP_URL="https://raw.githubusercontent.com/digi-embedded/android_platform_frameworks_opt_net_ethernet/pie/maint/java/com/android/server/ethernet/EthernetNetworkFactory.java"
AOSP_TARGET="$AOSP_TMP/java/com/android/server/ethernet/EthernetNetworkFactory.java"
mkdir -p "$(dirname "$AOSP_TARGET")"
curl -fsSL "$AOSP_URL" > "$AOSP_TARGET"
grep -Fq 'if (--network.refCount == 1)' "$AOSP_TARGET"
git -C "$AOSP_TMP" init -q
git -C "$AOSP_TMP" add .
git -C "$AOSP_TMP" -c user.name=validator -c user.email=validator@example.invalid commit -qm 'Android Pie pre-fix compatibility fixture'
git -C "$AOSP_TMP" apply --check "$PATCH"
git -C "$AOSP_TMP" apply "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$AOSP_TARGET"
! grep -Fq 'if (--network.refCount == 1)' "$AOSP_TARGET"
grep -Fq 'releaseNetworkFor, failed to get a network for' "$AOSP_TARGET"
echo "PASS: patch applies to public Android Pie maintenance source."

# Compile and exercise the patched method with overlapping requests. The first
# release (3->2) and second release (2->1) must keep Ethernet alive; only 1->0
# may stop it. This is a focused behavior fixture, not a full framework build.
TARGET="$TMP/java/com/android/server/ethernet/EthernetNetworkFactory.java"
mkdir -p "$(dirname "$TARGET")"
cat > "$TARGET" <<'JAVA'
class EthernetNetworkFactory {
    static final String TAG = "EthernetNetworkFactory";
    static class NetworkRequest {}
    static class NetworkInterfaceState {
        int refCount = 3;
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
        if (factory.network.refCount != 2 || factory.network.stopped) {
            throw new AssertionError("Ethernet stopped at refCount 3->2");
        }
        factory.releaseNetworkFor(request);
        if (factory.network.refCount != 1 || factory.network.stopped) {
            throw new AssertionError("Ethernet stopped while one request remained");
        }
        factory.releaseNetworkFor(request);
        if (factory.network.refCount != 0 || !factory.network.stopped) {
            throw new AssertionError("Ethernet did not stop after final request release");
        }
        System.out.println("PASS: 3->2 and 2->1 keep Ethernet; 1->0 stops Ethernet");
    }
}
JAVA
git -C "$TMP" init -q
git -C "$TMP" apply --check "$PATCH"
git -C "$TMP" apply "$PATCH"
grep -Fq 'if (--network.refCount == 0)' "$TARGET"
! grep -Fq 'if (--network.refCount == 1)' "$TARGET"
grep -Fq 'releaseNetworkFor, failed to get a network for' "$TARGET"
javac "$TARGET"
java -cp "$(dirname "$TARGET")" EthernetNetworkFactory
echo "PASS: patch syntax, application, logging tag, compilation, and refCount transitions."
echo "LIMIT: Toshiba OEM source applicability, framework/system-image build, and device regression remain unverified."

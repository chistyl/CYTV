package com.gathin.tvhome;

import android.net.*;
import android.os.*;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Local DNS filtering VPN. R3 prioritizes Ethernet, retries upstream resolvers,
 * and fails open when the upstream DNS path is persistently unhealthy.
 */
public class AdsFilterVpnService extends VpnService {
    private static final String TAG = "AdsFilterVpn";
    private static final String ACTION_START = "com.gathin.tvhome.ads.START";
    private static final String ACTION_STOP = "com.gathin.tvhome.ads.STOP";
    private static final String ACTION_REFRESH = "com.gathin.tvhome.ads.REFRESH";
    private static final String DNS_LOCAL = "10.13.0.1";
    private static final String DNS_SERVER = "10.13.0.2";
    private static final int DNS_PORT = 53;
    private static final int DNS_TIMEOUT_MS = 1200;
    private static final int MAX_CONSECUTIVE_DNS_FAILURES = 3;
    private static final long DNS_FAILURE_WINDOW_MS = 30_000L;
    private static final String[] FALLBACK_DNS = {"1.1.1.1", "8.8.8.8", "9.9.9.9"};

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean failOpenTriggered = new AtomicBoolean(false);
    private final AtomicInteger consecutiveDnsFailures = new AtomicInteger(0);
    private volatile long lastDnsFailureAtMs = 0L;
    private volatile ParcelFileDescriptor vpnInterface;
    private volatile Thread worker;
    private volatile Network physicalNetwork;
    private volatile List<String> upstreamDns = new ArrayList<>();
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Object networkLock = new Object();

    @Override public void onCreate() {
        super.onCreate();
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopFilter(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH.equals(action) && running.get()) {
            refreshPhysicalNetwork();
            return START_STICKY;
        }
        startFilter();
        return START_STICKY;
    }

    private void startFilter() {
        if (!running.compareAndSet(false, true)) {
            refreshPhysicalNetwork();
            return;
        }
        failOpenTriggered.set(false);
        consecutiveDnsFailures.set(0);
        lastDnsFailureAtMs = 0L;
        registerNetworkCallback();
        physicalNetwork = findPhysicalNetwork();
        updateDnsServers(physicalNetwork);
        try {
            Builder builder = new Builder()
                    .setSession("CYTV DNS Filter")
                    .addAddress(DNS_LOCAL, 32)
                    .addDnsServer(DNS_SERVER)
                    .addRoute(DNS_SERVER, 32)
                    .setMtu(1500);
            if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false);
            vpnInterface = builder.establish();
            if (vpnInterface == null) throw new IllegalStateException("VPN establish returned null");
            startForegroundNotification();
            worker = new Thread(this::runTunnel, "AdsFilterDnsTunnel");
            worker.start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to start DNS VPN", e);
            stopFilter(true);
        }
    }

    private void registerNetworkCallback() {
        if (connectivityManager == null || networkCallback != null) return;
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build();
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                if (isPhysicalNetwork(network)) selectNetwork(network);
            }
            @Override public void onLost(Network network) {
                synchronized (networkLock) {
                    if (network.equals(physicalNetwork)) {
                        Network replacement = findPhysicalNetwork();
                        if (replacement == null) failOpen("No physical network after network lost");
                        else selectNetwork(replacement);
                    }
                }
            }
            @Override public void onLinkPropertiesChanged(Network network, LinkProperties props) {
                synchronized (networkLock) {
                    if (network.equals(physicalNetwork)) updateDnsServers(network);
                }
            }
            @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                if (isPhysicalNetwork(network)) selectNetwork(network);
            }
        };
        try { connectivityManager.registerNetworkCallback(request, networkCallback); }
        catch (Exception e) { Log.w(TAG, "Network callback registration failed", e); networkCallback = null; }
    }

    private boolean isPhysicalNetwork(Network network) {
        if (network == null || connectivityManager == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                && (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
    }

    private boolean isEthernet(Network network) {
        if (network == null || connectivityManager == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private boolean isWifi(Network network) {
        if (network == null || connectivityManager == null) return false;
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private Network findPhysicalNetwork() {
        if (connectivityManager == null) return null;
        Network active = connectivityManager.getActiveNetwork();
        if (isEthernet(active)) return active;
        Network wifi = null;
        for (Network network : connectivityManager.getAllNetworks()) {
            if (isEthernet(network)) return network;
            if (wifi == null && isWifi(network) && isPhysicalNetwork(network)) wifi = network;
        }
        if (isPhysicalNetwork(active)) return active;
        return wifi;
    }

    private void selectNetwork(Network network) {
        if (network == null || !isPhysicalNetwork(network)) return;
        synchronized (networkLock) {
            // Ethernet is always preferred, even if the OS still reports Wi-Fi as active.
            if (physicalNetwork != null && isEthernet(physicalNetwork) && !isEthernet(network)) return;
            if (physicalNetwork != null && physicalNetwork.equals(network)) {
                updateDnsServers(network);
                return;
            }
            physicalNetwork = network;
            updateDnsServers(network);
            Log.i(TAG, "Physical network selected: " + network);
        }
    }

    private void refreshPhysicalNetwork() {
        Network selected = findPhysicalNetwork();
        if (selected == null) {
            failOpen("No usable physical network during refresh");
            return;
        }
        selectNetwork(selected);
    }

    private void updateDnsServers(Network network) {
        List<String> servers = new ArrayList<>();
        if (network != null && connectivityManager != null) {
            LinkProperties lp = connectivityManager.getLinkProperties(network);
            if (lp != null) for (InetAddress address : lp.getDnsServers()) {
                String host = address.getHostAddress();
                if (host != null && host.indexOf(':') < 0 && !servers.contains(host)) servers.add(host);
            }
        }
        upstreamDns = servers;
    }

    private void runTunnel() {
        try (FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor())) {
            byte[] packet = new byte[32767];
            while (running.get()) {
                int length = in.read(packet);
                if (length <= 0) continue;
                processPacket(Arrays.copyOf(packet, length));
            }
        } catch (Exception e) {
            if (running.get()) {
                Log.e(TAG, "DNS tunnel worker stopped unexpectedly", e);
                failOpen("DNS tunnel worker stopped unexpectedly");
            }
        }
    }

    private void processPacket(byte[] packet) {
        try {
            if (packet.length < 28 || (packet[0] >> 4 & 0x0f) != 4) return;
            int ihl = (packet[0] & 0x0f) * 4;
            if (ihl < 20 || packet.length < ihl + 8 || packet[9] != 17) return;
            int destPort = u16(packet, ihl + 2);
            if (destPort != DNS_PORT) return;
            int dnsOffset = ihl + 8;
            int dnsLength = packet.length - dnsOffset;
            if (dnsLength < 12) return;
            byte[] query = Arrays.copyOfRange(packet, dnsOffset, packet.length);
            String domain = parseQuestionName(query);
            if (domain == null) return;
            boolean blocked = isBlocked(domain);
            byte[] answer = blocked ? makeBlockedResponse(query) : forwardDns(query);
            if (answer == null) {
                recordDnsFailure("All upstream resolvers failed for " + domain);
                answer = makeServFailResponse(query);
            }
            writeUdpResponse(packet, ihl, dnsOffset, answer);
        } catch (Exception e) { Log.w(TAG, "Malformed DNS packet", e); }
    }

    private byte[] forwardDns(byte[] query) {
        Network network = physicalNetwork;
        if (network == null || !isPhysicalNetwork(network)) {
            network = findPhysicalNetwork();
            if (network != null) selectNetwork(network);
        }
        if (network == null) return null;
        List<String> servers = new ArrayList<>(upstreamDns);
        if (servers.isEmpty()) servers.addAll(Arrays.asList(FALLBACK_DNS));
        else for (String fallback : FALLBACK_DNS) if (!servers.contains(fallback)) servers.add(fallback);
        Set<String> tried = new HashSet<>();
        for (String dns : servers) {
            tried.add(dns);
            byte[] response = queryDns(network, dns, query);
            if (response != null) { resetDnsFailures(); return response; }
        }
        Network refreshed = findPhysicalNetwork();
        if (refreshed != null) {
            boolean changed = !refreshed.equals(network);
            if (changed) {
                selectNetwork(refreshed);
                List<String> retry = new ArrayList<>(upstreamDns);
                if (retry.isEmpty()) retry.addAll(Arrays.asList(FALLBACK_DNS));
                else for (String fallback : FALLBACK_DNS) if (!retry.contains(fallback)) retry.add(fallback);
                for (String dns : retry) {
                    byte[] response = queryDns(refreshed, dns, query);
                    if (response != null) { resetDnsFailures(); return response; }
                }
            }
        }
        return null;
    }

    private byte[] queryDns(Network network, String dns, byte[] query) {
        try {
            DatagramSocket socket = new DatagramSocket();
            try {
                if (!protect(socket)) return null;
                network.bindSocket(socket);
                socket.setSoTimeout(DNS_TIMEOUT_MS);
                byte[] addressBytes = InetAddress.getByName(dns).getAddress();
                DatagramPacket out = new DatagramPacket(query, query.length, InetAddress.getByAddress(addressBytes), DNS_PORT);
                socket.send(out);
                byte[] response = new byte[4096];
                DatagramPacket in = new DatagramPacket(response, response.length);
                socket.receive(in);
                return Arrays.copyOf(in.getData(), in.getLength());
            } finally { socket.close(); }
        } catch (Exception e) { Log.d(TAG, "DNS upstream failed " + dns + ": " + e.getMessage()); return null; }
    }

    private synchronized void resetDnsFailures() {
        consecutiveDnsFailures.set(0);
        lastDnsFailureAtMs = 0L;
    }

    private synchronized void recordDnsFailure(String reason) {
        long now = SystemClock.elapsedRealtime();
        if (lastDnsFailureAtMs == 0L || now - lastDnsFailureAtMs > DNS_FAILURE_WINDOW_MS) consecutiveDnsFailures.set(0);
        lastDnsFailureAtMs = now;
        int failures = consecutiveDnsFailures.incrementAndGet();
        Log.w(TAG, "DNS failures in 30s window: " + failures + "/" + MAX_CONSECUTIVE_DNS_FAILURES + " (" + reason + ")");
        if (failures >= MAX_CONSECUTIVE_DNS_FAILURES) failOpen("Repeated DNS upstream failures");
    }

    private boolean isBlocked(String domain) {
        // Rule implementation remains centralized in the existing app preference-backed rule store.
        android.content.SharedPreferences prefs = getSharedPreferences("ads_blocker", MODE_PRIVATE);
        Set<String> allow = prefs.getStringSet("allow_rules", Collections.emptySet());
        Set<String> block = prefs.getStringSet("block_rules", Collections.emptySet());
        for (String rule : allow) if (domain.equals(rule) || domain.endsWith("." + rule)) return false;
        for (String rule : block) if (domain.equals(rule) || domain.endsWith("." + rule)) return true;
        return false;
    }

    private String parseQuestionName(byte[] dns) {
        try {
            int pos = 12; StringBuilder name = new StringBuilder(); int labels = 0;
            while (pos < dns.length) {
                int len = dns[pos++] & 255;
                if (len == 0) break;
                if ((len & 0xc0) != 0 || len > 63 || pos + len > dns.length || ++labels > 40) return null;
                if (name.length() > 0) name.append('.');
                name.append(new String(dns, pos, len, java.nio.charset.StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT));
                pos += len;
            }
            return name.length() == 0 ? null : name.toString();
        } catch (Exception e) { return null; }
    }

    private byte[] makeBlockedResponse(byte[] query) { return makeDnsResponse(query, 3); }
    private byte[] makeServFailResponse(byte[] query) { return makeDnsResponse(query, 2); }
    private byte[] makeDnsResponse(byte[] query, int rcode) {
        byte[] response = Arrays.copyOf(query, query.length);
        response[2] = (byte) 0x81;
        response[3] = (byte) (0x80 | (query[3] & 0x0f) | rcode);
        response[6] = response[7] = response[8] = response[9] = 0;
        return response;
    }

    private void writeUdpResponse(byte[] request, int ihl, int dnsOffset, byte[] dns) throws IOException {
        int oldTotal = u16(request, 2);
        int ipHeaderLen = ihl;
        int udpOffset = ihl;
        byte[] response = new byte[ipHeaderLen + 8 + dns.length];
        System.arraycopy(request, 0, response, 0, ipHeaderLen);
        response[12] = request[16]; response[13] = request[17]; response[14] = request[18]; response[15] = request[19];
        response[16] = request[12]; response[17] = request[13]; response[18] = request[14]; response[19] = request[15];
        response[2] = (byte) (response.length >> 8); response[3] = (byte) response.length;
        response[8] = 64; response[10] = response[11] = 0;
        int src = u16(request, udpOffset), dst = u16(request, udpOffset + 2);
        put16(response, udpOffset, dst); put16(response, udpOffset + 2, src); put16(response, udpOffset + 4, 8 + dns.length);
        System.arraycopy(dns, 0, response, udpOffset + 8, dns.length);
        put16(response, 10, checksum(response, 0, ipHeaderLen));
        put16(response, udpOffset + 6, 0);
        int sum = pseudoChecksum(response, ipHeaderLen, 8 + dns.length);
        put16(response, udpOffset + 6, sum == 0 ? 0xffff : sum);
        try (FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor())) { out.write(response); }
    }

    private int checksum(byte[] b, int off, int len) {
        long sum=0; for(int i=off;i<off+len-1;i+=2) sum += u16(b,i);
        if((len&1)!=0) sum += (b[off+len-1]&255)<<8;
        while((sum>>16)!=0) sum=(sum&0xffff)+(sum>>16);
        return (int)(~sum)&0xffff;
    }
    private int pseudoChecksum(byte[] b,int off,int len) {
        long sum=0; for(int i=12;i<20;i+=2) sum += u16(b,i);
        sum += 17; sum += len;
        for(int i=off;i<off+len-1;i+=2) sum += u16(b,i);
        if((len&1)!=0) sum += (b[off+len-1]&255)<<8;
        while((sum>>16)!=0) sum=(sum&0xffff)+(sum>>16);
        return (int)(~sum)&0xffff;
    }
    private int u16(byte[] b,int i){return ((b[i]&255)<<8)|(b[i+1]&255);}
    private void put16(byte[] b,int i,int v){b[i]=(byte)(v>>8);b[i+1]=(byte)v;}

    private void failOpen(String reason) {
        if (!failOpenTriggered.compareAndSet(false, true)) return;
        Log.e(TAG, "Fail-open: " + reason);
        getSharedPreferences("ads_blocker", MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopFilter(true);
    }

    private void stopFilter(boolean fromFailure) {
        running.set(false);
        ParcelFileDescriptor fd = vpnInterface; vpnInterface = null;
        if (fd != null) try { fd.close(); } catch (IOException ignored) {}
        Thread t = worker; worker = null;
        if (t != null && t != Thread.currentThread()) t.interrupt();
        if (networkCallback != null && connectivityManager != null) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
            networkCallback = null;
        }
        physicalNetwork = null;
        upstreamDns = new ArrayList<>();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (fromFailure) stopSelf();
    }

    private void startForegroundNotification() {
        // The production app's AdsBlockerActivity foreground notification remains defined by the service.
        if (Build.VERSION.SDK_INT >= 26) {
            String channel = "ads_filter";
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new android.app.NotificationChannel(channel, "DNS filter", android.app.NotificationManager.IMPORTANCE_LOW));
            android.app.Notification n = new android.app.Notification.Builder(this, channel)
                    .setContentTitle("廣告過濾已啟用").setContentText("DNS 過濾服務正在執行")
                    .setSmallIcon(android.R.drawable.stat_sys_warning).setOngoing(true).build();
            startForeground(140, n);
        }
    }

    @Override public void onDestroy() { stopFilter(false); super.onDestroy(); }
}

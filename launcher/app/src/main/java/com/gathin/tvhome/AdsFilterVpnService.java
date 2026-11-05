package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdsFilterVpnService extends VpnService {
    private static final String TAG = "AdsFilterVpn";
    private static final String CHANNEL = "ad_filter";
    private static final String PREFS = "ad_filter";
    // Use the RFC 2544 benchmarking range for the private TUN DNS hop.
    // The previous 10.13.0.0/24 choice could overlap a real Ethernet LAN/VLAN,
    // causing Android TV routing to treat the VPN DNS endpoint as a LAN host.
    private static final String DNS_CLIENT = "198.18.0.1";
    private static final String DNS_LOCAL = "198.18.0.2";
    private static final String UPSTREAM = "1.1.1.1";
    private static final String SECONDARY_UPSTREAM = "8.8.8.8";
    private static final String DNS_MODE_AUTO = "auto";
    static final String ACTION_REFRESH_NETWORK = "com.gathin.tvhome.action.REFRESH_NETWORK";
    static final String ACTION_STOP = "com.gathin.tvhome.action.STOP_AD_FILTER";
    private final AtomicBoolean running = new AtomicBoolean(false);
    // Prevent duplicate teardown from the UI action, worker thread and Service lifecycle callbacks.
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final AtomicInteger consecutiveDnsFailures = new AtomicInteger(0);
    private static final int DNS_FAILURES_BEFORE_FAIL_OPEN = 3;
    private ParcelFileDescriptor tunnel;
    private Thread worker;
    private volatile Network underlyingNetwork;
    private volatile List<InetAddress> upstreamServers = fallbackDnsServers();
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler startupHandler = new Handler(Looper.getMainLooper());
    private final Handler networkHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingNetworkLossCheck;
    private int startupAttempts = 0;
    // Android 9 TV firmware can report a short-lived onLost/ActiveNetwork=null
    // while Ethernet is still recovering. Allow a longer non-blocking recovery window.
    private static final int STARTUP_MAX_ATTEMPTS = 30;
    private static final long STARTUP_RETRY_MS = 500L;
    private static final long NETWORK_LOSS_GRACE_MS = 3000L;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_NETWORK.equals(intent.getAction())) {
            if (running.get()) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (cm != null) {
                    Network current = findPhysicalNetwork(cm);
                    if (current != null) updateUnderlyingNetwork(current);
                }
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopFilter();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (running.get()) return START_STICKY;
        stopping.set(false);
        appendDnsLog("-", "診斷", "收到啟動請求；Android API " + Build.VERSION.SDK_INT);
        try {
            startNotice();
            // Track real Wi-Fi/Ethernet networks, excluding the VPN itself. The default
            // network can change while the filter is running, so do not capture it only once.
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) throw new IllegalStateException("Connectivity service unavailable");
            // Do not require NET_CAPABILITY_NOT_VPN in the request. Some Android TV
            // firmwares transiently drop that capability from physical Ethernet when a
            // VPN becomes active. Transport checks below exclude the VPN itself.
            NetworkRequest request = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    // Keep the physical default selected before the VPN was established.
                    // A second available transport (for example Wi-Fi while Ethernet is active)
                    // must not silently steal DNS forwarding from the current default.
                    NetworkCapabilities currentCaps = underlyingNetwork == null
                            ? null : cm.getNetworkCapabilities(underlyingNetwork);
                    if ((underlyingNetwork == null || !isPhysicalTransport(currentCaps))
                            && isPhysicalTransport(caps)) {
                        cancelPendingNetworkLossCheck();
                        updateUnderlyingNetwork(network);
                        appendDnsLog("-", "診斷", "實體網路重新可用：" + network);
                        if (!running.get()) startTunnelWhenNetworkReady();
                    }
                }
                @Override public void onLost(Network network) {
                    if (network.equals(underlyingNetwork)) {
                        if (running.get()) {
                            // Android 9 TV firmware may hide the physical Network after the
                            // VPN becomes the active Network. A callback alone is not proof
                            // that Ethernet is physically disconnected. Protected DNS sockets
                            // follow Android's current default route; let real DNS failures,
                            // not onLost(), decide whether fail-open is necessary.
                            underlyingNetwork = null;
                            appendDnsLog("-", "診斷", "VPN 執行期間收到實體 Network onLost；等待實體網路恢復並重新選路");
                            schedulePhysicalNetworkRecovery(cm, network,
                                    "VPN 執行期間實體網路消失");
                        } else {
                            schedulePhysicalNetworkRecovery(cm, network,
                                    "啟動期間實體網路中斷，且沒有可用的替代網路");
                        }
                    }
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    if (network.equals(underlyingNetwork) && !isPhysicalTransport(caps)) {
                        if (running.get()) {
                            underlyingNetwork = null;
                            appendDnsLog("-", "診斷", "VPN 執行期間實體 Network 能力暫時消失；等待重新取得實體網路");
                            schedulePhysicalNetworkRecovery(cm, network,
                                    "VPN 執行期間實體網路能力消失");
                        } else {
                            schedulePhysicalNetworkRecovery(cm, network,
                                    "啟動期間網路已不再提供一般網際網路，且沒有可用的替代網路");
                        }
                    } else if (isPhysicalTransport(caps)) {
                        NetworkCapabilities currentCaps = underlyingNetwork == null
                                ? null : cm.getNetworkCapabilities(underlyingNetwork);
                        if (underlyingNetwork == null || !isPhysicalTransport(currentCaps)) {
                            cancelPendingNetworkLossCheck();
                            updateUnderlyingNetwork(network);
                            appendDnsLog("-", "診斷", "網路能力恢復，重新指定實體網路：" + network);
                            if (!running.get()) startTunnelWhenNetworkReady();
                        } else if (network.equals(underlyingNetwork)) {
                            cancelPendingNetworkLossCheck();
                        }
                    }
                }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    if (network.equals(underlyingNetwork)) updateUpstreamServers(network, cm);
                }
            };
            cm.registerNetworkCallback(request, networkCallback);
            Network initial = findPhysicalNetwork(cm);
            if (initial != null) {
                updateUnderlyingNetwork(initial);
                startTunnelWhenNetworkReady();
            } else {
                appendDnsLog("-", "診斷", "啟動時尚未取得 Wi-Fi／Ethernet；改用非阻塞網路等待");
                appendNetworkSnapshot(cm);
                scheduleNetworkRetry(cm);
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to start DNS filter", e);
            appendDnsLog("-", "啟動失敗", e.getClass().getSimpleName() + "：" + String.valueOf(e.getMessage()));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopFilter();
        }
        return START_STICKY;
    }

    private boolean isPhysicalTransport(NetworkCapabilities caps) {
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private void cancelPendingNetworkLossCheck() {
        if (pendingNetworkLossCheck != null) {
            networkHandler.removeCallbacks(pendingNetworkLossCheck);
            pendingNetworkLossCheck = null;
        }
    }

    // Do not tear down the VPN on the first transient onLost/capability callback.
    // Android TV may briefly publish no default/physical Network during Ethernet recovery.
    private void schedulePhysicalNetworkRecovery(ConnectivityManager cm, Network lostNetwork, String reason) {
        cancelPendingNetworkLossCheck();
        appendDnsLog("-", "診斷", "偵測到實體網路中斷，等待 "
                + (NETWORK_LOSS_GRACE_MS / 1000) + " 秒確認並重新搜尋：" + lostNetwork);
        pendingNetworkLossCheck = () -> {
            pendingNetworkLossCheck = null;
            if (stopping.get()) return;
            Network replacement = findPhysicalNetwork(cm);
            if (replacement != null) {
                updateUnderlyingNetwork(replacement);
                appendDnsLog("-", "診斷", "網路中斷後重新取得實體網路：" + replacement);
                Log.i(TAG, "Physical network recovery completed: " + replacement);
            } else {
                appendNetworkSnapshot(cm);
                failOpen(reason + "；等待恢復後仍找不到可用的 Wi-Fi／Ethernet");
            }
        };
        networkHandler.postDelayed(pendingNetworkLossCheck, NETWORK_LOSS_GRACE_MS);
    }

    private Network findPhysicalNetwork(ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (active != null && isPhysicalTransport(cm.getNetworkCapabilities(active))) return active;
        for (Network candidate : cm.getAllNetworks()) {
            if (isPhysicalTransport(cm.getNetworkCapabilities(candidate))) return candidate;
        }
        return null;
    }

    private void scheduleNetworkRetry(ConnectivityManager cm) {
        startupHandler.removeCallbacksAndMessages(null);
        startupAttempts = 0;
        startupHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (stopping.get() || running.get()) return;
                Network candidate = findPhysicalNetwork(cm);
                if (candidate != null) {
                    updateUnderlyingNetwork(candidate);
                    appendDnsLog("-", "診斷", "非阻塞等待取得實體網路成功：" + candidate);
                    startTunnelWhenNetworkReady();
                    return;
                }
                startupAttempts++;
                appendDnsLog("-", "診斷", "非阻塞等待實體網路 " + startupAttempts + "/" + STARTUP_MAX_ATTEMPTS);
                appendNetworkSnapshot(cm);
                if (startupAttempts < STARTUP_MAX_ATTEMPTS) {
                    startupHandler.postDelayed(this, STARTUP_RETRY_MS);
                } else {
                    appendDnsLog("-", "啟動失敗", "等待 15 秒仍找不到 Wi-Fi／Ethernet；服務已安全停止");
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
                    stopFilter();
                }
            }
        }, STARTUP_RETRY_MS);
    }

    private void appendNetworkSnapshot(ConnectivityManager cm) {
        try {
            Network active = cm.getActiveNetwork();
            appendDnsLog("-", "診斷", "目前 ActiveNetwork=" + active);
            for (Network candidate : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(candidate);
                LinkProperties links = cm.getLinkProperties(candidate);
                appendDnsLog("-", "診斷", "Network=" + candidate
                        + "；Ethernet=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                        + "；Wi-Fi=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                        + "；VPN=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                        + "；Internet=" + (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        + "；介面=" + (links == null ? "未知" : links.getInterfaceName())
                        + "；DNS=" + (links == null ? "未知" : describeDnsServersFromLinks(links)));
            }
        } catch (Exception e) {
            appendDnsLog("-", "診斷", "讀取網路清單失敗：" + e.getClass().getSimpleName() + "：" + e.getMessage());
        }
    }

    private String describeDnsServersFromLinks(LinkProperties links) {
        StringBuilder out = new StringBuilder();
        for (InetAddress server : links.getDnsServers()) {
            if (out.length() > 0) out.append("、");
            out.append(server.getHostAddress());
        }
        return out.length() == 0 ? "無" : out.toString();
    }

    private void startTunnelWhenNetworkReady() {
        if (stopping.get() || running.get() || tunnel != null) return;
        startupHandler.removeCallbacksAndMessages(null);
        try {
            Network selected = underlyingNetwork;
            if (selected == null) throw new IllegalStateException("Physical network is null");
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(selected);
            appendDnsLog("-", "診斷", "選取實體網路 " + selected
                    + "；Ethernet=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    + "；Wi-Fi=" + (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                    + "；Internet=" + (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)));
            Builder builder = new Builder().setSession("CleanYourTV 廣告過濾").setMtu(1500)
                    .addAddress(DNS_CLIENT, 32).addDnsServer(DNS_LOCAL).addRoute(DNS_LOCAL, 32);
            tunnel = builder.establish();
            if (tunnel == null) throw new IllegalStateException("VPN establish returned null");
            appendDnsLog("-", "診斷", "VPN TUN 建立成功");
            running.set(true);
            worker = new Thread(this::runDnsLoop, "CleanYourTV-DNS");
            worker.start();
            appendDnsLog("-", "診斷", "DNS 工作執行緒已啟動");
        } catch (Exception e) {
            Log.e(TAG, "Unable to establish DNS tunnel", e);
            appendDnsLog("-", "啟動失敗", e.getClass().getSimpleName() + "：" + String.valueOf(e.getMessage()));
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopFilter();
        }
    }

    private void updateUnderlyingNetwork(Network network) {
        underlyingNetwork = network;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) updateUpstreamServers(network, cm);
        // Do not call VpnService.setUnderlyingNetworks() on Android 9 TV firmware.
        // DNS sockets are independently protected from the VPN and bound to this Network.
    }

    private void updateUpstreamServers(Network network, ConnectivityManager cm) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!DNS_MODE_AUTO.equals(prefs.getString("dns_mode", DNS_MODE_AUTO))) {
            upstreamServers = fallbackDnsServers();
            return;
        }
        try {
            LinkProperties properties = cm.getLinkProperties(network);
            List<InetAddress> discovered = new ArrayList<>();
            if (properties != null) {
                for (InetAddress address : properties.getDnsServers()) {
                    if (address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress() && !address.isMulticastAddress()) {
                        discovered.add(address);
                    }
                }
            }
            // Keep router DNS first for LAN/split-DNS names, then public resolvers
            // as timeout fallbacks. An empty DHCP DNS list must not black-hole DNS.
            LinkedHashSet<InetAddress> candidates = new LinkedHashSet<>(discovered);
            candidates.add(parseAddress(UPSTREAM));
            candidates.add(parseAddress(SECONDARY_UPSTREAM));
            upstreamServers = Collections.unmodifiableList(new ArrayList<>(candidates));
            Log.i(TAG, "Auto DNS negotiated: " + discovered.size()
                    + " network DNS server(s); " + upstreamServers.size() + " total candidates");
            appendDnsLog("-", "診斷", "自動 DNS 候選：" + describeDnsServers(upstreamServers));
        } catch (Exception e) {
            // If DHCP DNS discovery fails, retain public resolver fallbacks instead
            // of leaving the DNS worker with an empty upstream list.
            upstreamServers = fallbackDnsServers();
            Log.w(TAG, "Unable to read network DNS; using public resolver fallbacks", e);
            appendDnsLog("-", "診斷", "讀取實體網路 DNS 失敗，改用備援：" + describeDnsServers(upstreamServers));
        }
    }

    private String describeDnsServers(List<InetAddress> servers) {
        StringBuilder out = new StringBuilder();
        for (InetAddress server : servers) {
            if (out.length() > 0) out.append("、");
            out.append(server.getHostAddress());
        }
        return out.length() == 0 ? "無可用 DNS" : out.toString();
    }

    private static List<InetAddress> fallbackDnsServers() {
        List<InetAddress> servers = new ArrayList<>();
        servers.add(parseAddress(UPSTREAM));
        servers.add(parseAddress(SECONDARY_UPSTREAM));
        return Collections.unmodifiableList(servers);
    }

    private static InetAddress parseAddress(String address) {
        try { return InetAddress.getByName(address); }
        catch (Exception e) { throw new IllegalStateException("Invalid DNS fallback", e); }
    }

    private void startNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "廣告過濾", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("CleanYourTV 廣告過濾")
                .setContentText("本機 DNS 網域過濾正在執行")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7413, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(7413, b.build());
    }

    private void runDnsLoop() {
        byte[] packet = new byte[32767];
        try (FileInputStream in = new FileInputStream(tunnel.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tunnel.getFileDescriptor())) {
            while (running.get()) {
                int n = in.read(packet);
                if (n <= 0) continue;
                try {
                    byte[] response = processPacket(packet, n);
                    if (response != null) out.write(response);
                } catch (Exception e) {
                    Log.w(TAG, "DNS packet skipped", e);
                }
            }
        } catch (Exception e) {
            if (running.get()) Log.e(TAG, "DNS tunnel stopped", e);
        } finally {
            // If the TUN reader exits unexpectedly, perform the same complete teardown
            // as an explicit user stop. A normal user stop has already cleared running.
            if (running.get()) stopFilter();
        }
    }

    private byte[] processPacket(byte[] p, int length) throws Exception {
        if (length < 28 || (p[0] >> 4 & 15) != 4 || (p[9] & 255) != 17) return null;
        int ihl = (p[0] & 15) * 4;
        if (length < ihl + 8) return null;
        int srcPort = u16(p, ihl), dstPort = u16(p, ihl + 2);
        if (dstPort != 53) return null;
        int udpLen = u16(p, ihl + 4);
        int dnsOff = ihl + 8;
        int dnsLen = Math.min(length - dnsOff, udpLen - 8);
        if (dnsLen < 17) return null;
        byte[] query = new byte[dnsLen];
        System.arraycopy(p, dnsOff, query, 0, dnsLen);
        String domain = questionName(query);
        if (domain == null) return null;

        if (isAllowed(domain)) {
            byte[] answer = forward(query);
            if (answer == null) {
                appendDnsLog(domain, "查詢失敗", "上游 DNS 無回應");
                return null;
            }
            appendDnsLog(domain, "允許", dnsResponseSummary(answer));
            return makeReply(p, ihl, srcPort, answer);
        }
        if (isBlocked(domain)) {
            byte[] blocked = query.clone();
            int flags = u16(blocked, 2);
            put16(blocked, 2, (flags & 0x0100) | 0x8083); // NXDOMAIN; retain RD
            put16(blocked, 6, 0); put16(blocked, 8, 0); put16(blocked, 10, 0);
            appendDnsLog(domain, "已阻擋", "NXDOMAIN（網域不存在回應）");
            return makeReply(p, ihl, srcPort, blocked);
        }
        byte[] answer = forward(query);
        if (answer == null) {
            appendDnsLog(domain, "查詢失敗", "上游 DNS 無回應");
            return null;
        }
        appendDnsLog(domain, "一般通過", dnsResponseSummary(answer));
        return makeReply(p, ihl, srcPort, answer);
    }

    private byte[] forward(byte[] query) {
        Network physical = resolvePhysicalNetwork();
        List<InetAddress> candidates = upstreamServers;
        for (InetAddress dns : candidates) {
            // Ethernet routers often expose a LAN-local DNS resolver. Try a socket
            // explicitly bound to the still-valid Ethernet Network first, but never
            // let a stale Android 9 Network handle block the ordinary protected-socket
            // fallback. A failed bind/query affects only this DNS attempt, not the VPN.
            if (!candidates.isEmpty() && dns.equals(candidates.get(0))
                    && physical != null && isEthernetNetwork(physical)) {
                try {
                    byte[] answer = queryUpstream(query, dns, physical, true, 750);
                    consecutiveDnsFailures.set(0);
                    return answer;
                } catch (Exception boundError) {
                    String failure = boundError.getClass().getSimpleName() + "："
                            + String.valueOf(boundError.getMessage());
                    Log.w(TAG, "Ethernet-bound DNS attempt failed; retrying protected default route: "
                            + dns.getHostAddress() + " " + failure);
                    appendDnsLog("-", "診斷", "有線網路 DNS 綁定嘗試失敗，改用受保護預設路由："
                            + dns.getHostAddress() + "；" + failure);
                }
            }

            try {
                byte[] answer = queryUpstream(query, dns, null, false, 1200);
                consecutiveDnsFailures.set(0);
                return answer;
            } catch (Exception e) {
                String failure = e.getClass().getSimpleName() + "：" + String.valueOf(e.getMessage());
                Log.w(TAG, "Protected DNS upstream failed: " + dns.getHostAddress() + " " + failure, e);
                appendDnsLog("-", "診斷", "受保護 DNS 上游 " + dns.getHostAddress()
                        + " 失敗：" + failure);
            }
        }
        noteDnsFailure("所有設定的上游 DNS 均無回應");
        return null;
    }

    private Network resolvePhysicalNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        Network current = underlyingNetwork;
        if (current != null && isPhysicalTransport(cm.getNetworkCapabilities(current))) {
            return current;
        }
        Network refreshed = findPhysicalNetwork(cm);
        if (refreshed != null) {
            updateUnderlyingNetwork(refreshed);
            appendDnsLog("-", "診斷", "DNS 轉送前重新取得實體網路：" + refreshed);
        }
        return refreshed;
    }

    private boolean isEthernetNetwork(Network network) {
        if (network == null) return false;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
    }

    private byte[] queryUpstream(byte[] query, InetAddress dns, Network network,
                                 boolean bindToNetwork, int timeoutMs) throws Exception {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            if (!protect(socket)) {
                throw new IOException("Could not protect upstream DNS socket from VPN loop");
            }
            if (bindToNetwork) {
                if (network == null) throw new IOException("Ethernet Network is unavailable");
                // Bind only an individual protected UDP socket; do not change the
                // process-wide route or VpnService underlying-network configuration.
                network.bindSocket(socket);
            }
            socket.setSoTimeout(timeoutMs);
            socket.connect(dns, 53);
            socket.send(new DatagramPacket(query, query.length));
            byte[] buffer = new byte[4096];
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            socket.receive(reply);
            if (reply.getLength() < 12 || query.length < 2
                    || reply.getData()[reply.getOffset()] != query[0]
                    || reply.getData()[reply.getOffset() + 1] != query[1]) {
                throw new IOException("DNS response ID/length validation failed");
            }
            byte[] answer = new byte[reply.getLength()];
            System.arraycopy(reply.getData(), reply.getOffset(), answer, 0, answer.length);
            return answer;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private void failOpen(String reason) {
        Log.e(TAG, "Fail-open: " + reason);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        appendDnsLog("-", "查詢失敗", reason + "；已自動關閉過濾並交還 Android 網路管理");
        stopFilter();
    }

    private void noteDnsFailure(String reason) {
        int failures = consecutiveDnsFailures.incrementAndGet();
        Log.w(TAG, "DNS forwarding failure " + failures + "/" + DNS_FAILURES_BEFORE_FAIL_OPEN + ": " + reason);
        appendDnsLog("-", "診斷", "DNS 轉送失敗 " + failures + "/" + DNS_FAILURES_BEFORE_FAIL_OPEN + "：" + reason);
        if (failures >= DNS_FAILURES_BEFORE_FAIL_OPEN && running.compareAndSet(true, false)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            appendDnsLog("-", "查詢失敗", "DNS 連續異常，已自動關閉過濾並交還 Android 原始網路");
            Log.e(TAG, "Fail-open: stopping local DNS VPN after repeated upstream failures");
            stopFilter();
        }
    }
    private byte[] makeReply(byte[] request, int ihl, int clientPort, byte[] dns) {
        int total = 20 + 8 + dns.length;
        byte[] out = new byte[total];
        out[0] = 0x45; put16(out, 2, total); put16(out, 4, 0); put16(out, 6, 0x4000);
        out[8] = 64; out[9] = 17;
        System.arraycopy(request, 16, out, 12, 4); // original destination becomes response source
        System.arraycopy(request, 12, out, 16, 4); // original source becomes response destination
        put16(out, 10, checksum(out, 0, 20));
        put16(out, 20, 53); put16(out, 22, clientPort); put16(out, 24, 8 + dns.length);
        System.arraycopy(dns, 0, out, 28, dns.length);
        return out;
    }

    // Keep a bounded local DNS history so the TV UI can explain each filtering decision.
    private void appendDnsLog(String domain, String result, String response) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            synchronized (AdsFilterVpnService.class) {
                JSONArray old = new JSONArray(prefs.getString("dns_logs", "[]"));
                JSONArray next = new JSONArray();
                int start = Math.max(0, old.length() - 299);
                for (int i = start; i < old.length(); i++) next.put(old.getJSONObject(i));
                JSONObject item = new JSONObject();
                item.put("time", System.currentTimeMillis());
                item.put("domain", domain);
                item.put("result", result);
                item.put("response", response);
                next.put(item);
                prefs.edit().putString("dns_logs", next.toString()).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to save DNS history", e);
        }
    }

    private String dnsResponseSummary(byte[] dns) {
        if (dns == null || dns.length < 4) return "回應資料不完整";
        int rcode = dns[3] & 15;
        if (rcode == 3) return "NXDOMAIN（網域不存在）";
        if (rcode == 0) return "NOERROR（DNS 回應正常）";
        return "DNS 錯誤碼：" + rcode;
    }

    private boolean isAllowed(String domain) {
        // User-controlled temporary exceptions take precedence over both bundled and custom block rules.
        return matches(domain, "temporary_allow_domains") || matches(domain, "allow_domains");
    }
    private boolean isBlocked(String domain) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = p.getString("block_domains", "");
        if (raw == null) return false;
        for (String item : raw.split("\\s+")) {
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (!rule.isEmpty() && (domain.equals(rule) || domain.endsWith("." + rule))) return true;
        }
        return false;
    }
    private boolean matches(String domain, String key) {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "");
        if (raw == null) return false;
        for (String item : raw.split("\\s+")) {
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (!rule.isEmpty() && (domain.equals(rule) || domain.endsWith("." + rule))) return true;
        }
        return false;
    }

    private String questionName(byte[] dns) {
        int p = 12; StringBuilder name = new StringBuilder();
        while (p < dns.length) {
            int n = dns[p++] & 255;
            if (n == 0) break;
            if ((n & 0xc0) != 0 || n > 63 || p + n > dns.length) return null;
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < n; i++) {
                int ch = dns[p++] & 255;
                if (ch < 33 || ch > 126) return null;
                name.append((char) ch);
            }
        }
        return name.length() == 0 ? null : name.toString().toLowerCase(Locale.ROOT);
    }
    private int u16(byte[] b, int p) { return ((b[p] & 255) << 8) | (b[p + 1] & 255); }
    private void put16(byte[] b, int p, int v) { b[p] = (byte)(v >> 8); b[p + 1] = (byte)v; }
    private int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = off; i < off + len; i += 2) {
            int word = (b[i] & 255) << 8;
            if (i + 1 < off + len) word |= b[i + 1] & 255;
            sum += word;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return (int)(~sum) & 0xffff;
    }

    @Override public void onRevoke() {
        appendDnsLog("-", "診斷", "Android 已撤銷 VPN 授權（onRevoke）");
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopFilter(); super.onRevoke();
    }
    @Override public void onDestroy() {
        appendDnsLog("-", "診斷", "VPN 服務進入 onDestroy");
        stopFilter(); super.onDestroy();
    }
    private void stopFilter() {
        if (!stopping.compareAndSet(false, true)) return;
        startupHandler.removeCallbacksAndMessages(null);
        cancelPendingNetworkLossCheck();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        appendDnsLog("-", "診斷", "開始停止 VPN／DNS 過濾服務");
        running.set(false);

        // Do not mutate VPN underlay state here; Android 9 TV firmware has shown
        // that setUnderlyingNetworks() can disrupt Ethernet restoration.

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null && networkCallback != null) {
            try { cm.unregisterNetworkCallback(networkCallback); }
            catch (Exception e) { Log.w(TAG, "Unable to unregister network callback", e); }
            networkCallback = null;
        }
        underlyingNetwork = null;

        ParcelFileDescriptor activeTunnel = tunnel;
        tunnel = null;
        if (activeTunnel != null) {
            try { activeTunnel.close(); }
            catch (Exception e) { Log.w(TAG, "Unable to close VPN tunnel", e); }
        }

        Thread activeWorker = worker;
        worker = null;
        if (activeWorker != null && activeWorker != Thread.currentThread()) activeWorker.interrupt();
        stopForeground(true);
        stopSelf();
    }
}

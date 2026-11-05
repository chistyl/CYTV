#!/usr/bin/env python3
"""Static regression check for the Toshiba Android 9 Ethernet VPN safety guard."""
from pathlib import Path
import re
import sys

SOURCE = Path("launcher/app/src/main/java/com/gathin/tvhome/AdsFilterVpnService.java")
if not SOURCE.is_file():
    sys.exit(f"FAIL: source file not found: {SOURCE}")

text = SOURCE.read_text(encoding="utf-8")
method_match = re.search(
    r"\bprivate\s+void\s+startTunnelWhenNetworkReady\s*\(\s*\)\s*\{",
    text,
)
if not method_match:
    sys.exit("FAIL: startTunnelWhenNetworkReady() not found")

next_method = re.search(r"\n\s{4}(?:private|protected|public)\s+", text[method_match.end():])
method_end = method_match.end() + next_method.start() if next_method else len(text)
method = text[method_match.start():method_end]

required_checks = {
    "Toshiba manufacturer detection": r'Build\.MANUFACTURER\)',
    "Toshiba brand detection": r'Build\.BRAND\)',
    "65U7000VS model detection": r'65u7000vs',
    "Android 9 API-level check": r'Build\.VERSION\.SDK_INT\s*==\s*Build\.VERSION_CODES\.P',
    "Ethernet transport check": r'hasTransport\(NetworkCapabilities\.TRANSPORT_ETHERNET\)',
    "all-connected-network Ethernet scan": r'cm\.getAllNetworks\(\)',
}
for label, pattern in required_checks.items():
    if not re.search(pattern, method, re.IGNORECASE):
        sys.exit(f"FAIL: missing {label}")

ethernet_scan = re.search(
    r"if\s*\(\s*!ethernet\s*&&\s*cm\s*!=\s*null\s*\)\s*\{(?P<body>.*?)\n\s*\}",
    method,
    re.DOTALL,
)
if not ethernet_scan:
    sys.exit("FAIL: all-network Ethernet fallback scan not found")
scan_body = ethernet_scan.group("body")
for label, pattern in {
    "enumerate all Android networks": r"cm\.getAllNetworks\(\)",
    "inspect each network capabilities": r"cm\.getNetworkCapabilities\(candidate\)",
    "promote Ethernet detection to true": r"ethernet\s*=\s*true",
}.items():
    if not re.search(pattern, scan_body):
        sys.exit(f"FAIL: Ethernet fallback scan missing {label}")

guard = re.search(
    r"if\s*\(\s*toshiba\s*&&\s*Build\.VERSION\.SDK_INT\s*==\s*Build\.VERSION_CODES\.P\s*&&\s*ethernet\s*\)\s*\{(?P<body>.*?)\n\s*\}",
    method,
    re.DOTALL,
)
if not guard:
    sys.exit("FAIL: Toshiba + Android 9 + Ethernet guard block not found")

body = guard.group("body")
for label, pattern in {
    "network snapshot": r"appendNetworkSnapshot\(cm\)",
    "automatic ad-filter disable": r'putBoolean\(\s*"enabled"\s*,\s*false\s*\)',
    "safe service stop": r"stopFilter\(\)",
    "early return": r"\breturn\s*;",
}.items():
    if not re.search(pattern, body):
        sys.exit(f"FAIL: guard block missing {label}")

builder = re.search(r"\bBuilder\s+builder\s*=", method)
establish = re.search(r"\bbuilder\.establish\s*\(", method)
if not builder or not establish:
    sys.exit("FAIL: VPN Builder creation/establish call not found")
if not (guard.start() < builder.start() < establish.start()):
    sys.exit("FAIL: VPN Builder/establish is not ordered after the safety guard")

print("PASS: Toshiba/API 28/Ethernet guard exists, disables filtering, stops service,")
print("      returns before VPN Builder creation and establish().")
print("NOTE: static regression check only; it does not prove OEM framework root cause.")

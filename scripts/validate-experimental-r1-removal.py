#!/usr/bin/env python3
"""Static checks for the explicitly requested experimental R1-removal branch."""
from pathlib import Path
import re
import sys

source = Path("launcher/app/src/main/java/com/gathin/tvhome/AdsFilterVpnService.java")
text = source.read_text(encoding="utf-8")
match = re.search(r"\bprivate\s+void\s+startTunnelWhenNetworkReady\s*\(\s*\)\s*\{", text)
if not match:
    sys.exit("FAIL: startTunnelWhenNetworkReady() not found")
next_method = re.search(r"\n\s{4}(?:private|protected|public)\s+", text[match.end():])
end = match.end() + next_method.start() if next_method else len(text)
method = text[match.start():end]
if re.search(r"toshiba\s*&&\s*Build\.VERSION\.SDK_INT\s*==\s*Build\.VERSION_CODES\.P\s*&&\s*ethernet", method):
    sys.exit("FAIL: Toshiba/API 28/Ethernet R1 guard is still present")
if "Builder builder =" not in method or "builder.establish()" not in method:
    sys.exit("FAIL: VPN Builder/establish path missing")
print("PASS: R1 guard removed; VPN establish path remains.")
print("WARNING: static check only; does not establish Ethernet safety on Toshiba firmware.")

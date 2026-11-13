#!/usr/bin/env python3
"""Generate a CYTV offline activation token using an externally stored RSA private key.

Keep the private key offline and never commit it to this repository or include it in an APK.
Requires OpenSSL on PATH.
"""
import argparse
import base64
import subprocess
import tempfile
from pathlib import Path

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")

parser = argparse.ArgumentParser()
parser.add_argument("--key", required=True, help="Path to the offline RSA private PEM")
parser.add_argument("--license-id", default="STANDARD", help="Non-secret license label")
args = parser.parse_args()

payload = f"CYTV-ACTIVATION-V1|{args.license_id}".encode("utf-8")
with tempfile.NamedTemporaryFile() as source:
    source.write(payload)
    source.flush()
    signature = subprocess.check_output(
        ["openssl", "dgst", "-sha256", "-sign", args.key, source.name]
    )
print(b64url(payload) + "." + b64url(signature))

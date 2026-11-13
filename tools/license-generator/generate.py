#!/usr/bin/env python3
"""Create a reusable, offline CYTV activation code. Requires OpenSSL on PATH.

Keep the RSA private key offline and out of the repository. One code is intentionally
reusable across devices; offline software cannot enforce a device-count limit.
"""
import argparse
import base64
import json
import subprocess
import tempfile
import time
from datetime import date, datetime, time as day_time, timezone
from pathlib import Path


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-key", required=True, type=Path, help="Path to the offline RSA private PEM")
    parser.add_argument("--license-id", required=True, help="Internal license reference")
    parser.add_argument("--expires", help="Optional expiry date YYYY-MM-DD (UTC, valid through that day); omit for perpetual")
    args = parser.parse_args()

    if not args.private_key.is_file():
        parser.error("private key file does not exist")
    if not args.license_id.strip():
        parser.error("license ID must not be empty")

    expires_at = 0
    if args.expires:
        try:
            expiry_date = date.fromisoformat(args.expires)
        except ValueError:
            parser.error("--expires must use YYYY-MM-DD")
        expires_at = int(datetime.combine(expiry_date, day_time.max, tzinfo=timezone.utc).timestamp() * 1000)

    payload = {
        "v": 1,
        "product": "CYTV",
        "license_id": args.license_id.strip(),
        "issued_at": int(time.time() * 1000),
        "expires_at": expires_at,
    }
    payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode("utf-8")

    with tempfile.TemporaryDirectory(prefix="cytv-license-") as temp_dir:
        payload_file = Path(temp_dir) / "payload.json"
        signature_file = Path(temp_dir) / "signature.bin"
        payload_file.write_bytes(payload_bytes)
        subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", str(args.private_key),
             "-out", str(signature_file), str(payload_file)],
            check=True,
        )
        print(b64url(payload_bytes) + "." + b64url(signature_file.read_bytes()))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Minimal authenticated ADB-over-TCP command runner for the Sony TV."""
import argparse
import base64
import hashlib
from pathlib import Path

from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("host")
    parser.add_argument("--out", type=Path, help="write command output locally")
    parser.add_argument("--base64-out", type=Path, help="decode base64 command output locally")
    parser.add_argument("--push", nargs=2, metavar=("LOCAL", "REMOTE"),
                        help="transfer a file as base64 chunks and verify its MD5")
    parser.add_argument("command", nargs="*", help="one shell command")
    args = parser.parse_args()

    key = Path(__file__).with_name("tv_adbkey")
    if not key.exists():
        keygen.keygen(str(key))
    signer = PythonRSASigner(key.with_suffix(".pub").read_bytes(), key.read_bytes())
    device = AdbDeviceTcp(args.host, 5555, default_transport_timeout_s=60)
    device.connect(rsa_keys=[signer], auth_timeout_s=30)
    try:
        if args.push:
            local, remote = Path(args.push[0]), args.push[1]
            data = local.read_bytes()
            expected = hashlib.md5(data).hexdigest()
            temporary = remote + ".b64"
            device.shell(f"rm -f {remote} {temporary}", timeout_s=30)
            chunk_size = 24 * 1024
            for index in range(0, len(data), chunk_size):
                chunk = base64.b64encode(data[index:index + chunk_size]).decode("ascii")
                result = device.shell(
                    f"echo '{chunk}' >> {temporary} && echo chunk-ok",
                    transport_timeout_s=60, read_timeout_s=60, timeout_s=120)
                if "chunk-ok" not in result:
                    raise RuntimeError(f"transfer failed at byte {index}: {result!r}")
            result = device.shell(
                f"base64 -d {temporary} > {remote} && rm {temporary} && md5sum {remote}",
                transport_timeout_s=120, read_timeout_s=180, timeout_s=300)
            actual = result.split()[0] if result.strip() else ""
            if actual != expected:
                raise RuntimeError(f"MD5 mismatch: local {expected}, TV {actual or 'missing'}")
            print(f"Transferred {local.name}; MD5 verified: {actual}")
            return
        output = device.shell(" ".join(args.command), transport_timeout_s=45,
                              read_timeout_s=60, timeout_s=90)
        if args.base64_out:
            args.base64_out.parent.mkdir(parents=True, exist_ok=True)
            args.base64_out.write_bytes(base64.b64decode(output))
        elif args.out:
            args.out.parent.mkdir(parents=True, exist_ok=True)
            args.out.write_text(output)
        else:
            print(output, end="")
    finally:
        device.close()


if __name__ == "__main__":
    main()

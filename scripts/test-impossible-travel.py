"""Submit two valid JSON scans from impossible locations for development testing."""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from pathlib import Path
from cryptography.hazmat.primitives.cmac import CMAC
from cryptography.hazmat.primitives.ciphers import algorithms


DEFAULT_VAULT = Path(__file__).resolve().parents[1] / "tools" / "simulator" / "simulator_vault.json"
DEFAULT_BACKEND_URL = "https://authentichain.website"


def load_aes_key(vault_path: Path, uid: str) -> str:
    try:
        vault = json.loads(vault_path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"Simulator vault not found: {vault_path}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"Simulator vault is not valid JSON: {vault_path}") from exc

    if not isinstance(vault, dict):
        raise ValueError(f"Simulator vault has an invalid structure: {vault_path}")
    tags = vault.get("tags")
    if not isinstance(tags, dict):
        raise ValueError(f"No tag inventory found in simulator vault: {vault_path}")

    tag = next(
        (item for vault_uid, item in tags.items()
         if str(vault_uid).upper() == uid or (
             isinstance(item, dict) and str(item.get("uid", "")).upper() == uid
         )),
        None,
    )
    if tag is None:
        raise ValueError(f"UID {uid} was not found in the local simulator vault.")
    key = tag.get("aesKey") if isinstance(tag, dict) else None
    if not isinstance(key, str) or not re.fullmatch(r"(?i)[0-9a-f]{32}", key):
        raise ValueError(f"UID {uid} does not have a valid 128-bit AES key in the local vault.")
    return key.upper()


def cmac_for(key_hex: str, uid_hex: str, counter: int) -> str:
    signer = CMAC(algorithms.AES(bytes.fromhex(key_hex)))
    payload = bytes.fromhex(uid_hex) + counter.to_bytes(3, "big")
    signer.update(payload)
    return signer.finalize()[:8].hex().upper()


def submit(url: str, payload: dict[str, object]) -> dict[str, object]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--uid", required=True, help="14-character UID from the local simulator vault")
    parser.add_argument("--vault", type=Path, default=DEFAULT_VAULT, help="Local simulator vault JSON")
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL, help="Backend base URL")
    parser.add_argument("--first-counter", type=int, default=25)
    args = parser.parse_args()

    uid = args.uid.upper()
    if not re.fullmatch(r"[0-9A-F]{14}", uid):
        parser.error("--uid must contain exactly 14 hexadecimal characters")
    if not 0 <= args.first_counter < 0xFFFFFF:
        parser.error("--first-counter must be between 0 and 0xFFFFFE")
    try:
        aes_key = load_aes_key(args.vault, uid)
    except ValueError as exc:
        parser.error(str(exc))

    backend = args.backend_url.rstrip("/") + "/api/v1/verify"
    print(
        "Travel checks compare submitted latitude/longitude; source IP and GeoIP "
        "do not drive this decision. Coordinates are unverified and advisory."
    )
    first = {
        "uid": uid,
        "ctr": f"{args.first_counter:06X}",
        "cmac": cmac_for(aes_key, uid, args.first_counter),
        "latitude": 40.4093,
        "longitude": 49.8671,
    }
    second_counter = args.first_counter + 1
    second = {
        "uid": uid,
        "ctr": f"{second_counter:06X}",
        "cmac": cmac_for(aes_key, uid, second_counter),
        "latitude": 51.5074,
        "longitude": -0.1278,
    }

    print("First scan:", json.dumps(submit(backend, first)))
    print("Second scan:", json.dumps(submit(backend, second)))


if __name__ == "__main__":
    main()

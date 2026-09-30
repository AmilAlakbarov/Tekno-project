"""Submit two valid JSON scans from impossible locations for development testing."""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from pathlib import Path
from cryptography.hazmat.primitives.cmac import CMAC
from cryptography.hazmat.primitives.ciphers import algorithms


DEFAULT_VAULT = Path(__file__).resolve().with_name("simulator_vault.json")
DEFAULT_BACKEND_URL = "https://authentichain.website"
BAKU = (40.4093, 49.8671)
SANLIURFA = (37.44317, 38.90030)


def load_tag(vault_path: Path, uid: str) -> tuple[str, int, dict[str, object], dict[str, object]]:
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
    if not isinstance(tag, dict):
        raise ValueError(f"UID {uid} was not found in the local simulator vault.")
    key = tag.get("aesKey")
    if not isinstance(key, str) or not re.fullmatch(r"(?i)[0-9a-f]{32}", key):
        raise ValueError(f"UID {uid} does not have a valid 128-bit AES key in the local vault.")
    counter = tag.get("counter", 0)
    if not isinstance(counter, int) or isinstance(counter, bool) or not 0 <= counter <= 0xFFFFFF:
        raise ValueError(f"UID {uid} does not have a valid counter in the local simulator vault.")
    return key.upper(), counter, vault, tag


def save_counter(vault_path: Path, vault: dict[str, object],
                 tag: dict[str, object], counter: int) -> None:
    tag["counter"] = counter
    temporary_path = vault_path.with_suffix(vault_path.suffix + ".tmp")
    temporary_path.write_text(json.dumps(vault, indent=2) + "\n", encoding="utf-8")
    temporary_path.replace(vault_path)


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
    parser.add_argument(
        "--first-counter",
        type=int,
        help="Override the next counter (must be greater than the vault counter)",
    )
    parser.add_argument("--first-latitude", type=float, default=BAKU[0], help="First scan latitude (default: Baku)")
    parser.add_argument("--first-longitude", type=float, default=BAKU[1], help="First scan longitude (default: Baku)")
    parser.add_argument("--second-latitude", type=float, default=SANLIURFA[0],
                        help="Second scan latitude (default: Sanliurfa)")
    parser.add_argument("--second-longitude", type=float, default=SANLIURFA[1],
                        help="Second scan longitude (default: Sanliurfa)")
    args = parser.parse_args()

    uid = args.uid.upper()
    if not re.fullmatch(r"[0-9A-F]{14}", uid):
        parser.error("--uid must contain exactly 14 hexadecimal characters")
    try:
        aes_key, vault_counter, vault, tag = load_tag(args.vault, uid)
    except ValueError as exc:
        parser.error(str(exc))
    first_counter = args.first_counter if args.first_counter is not None else vault_counter + 1
    if first_counter <= vault_counter:
        parser.error("--first-counter must be greater than the counter stored in the vault")
    if not 0 <= first_counter < 0xFFFFFF:
        parser.error("The next two counters must fit in the NTAG 424 three-byte counter")
    for name in ("first", "second"):
        latitude = getattr(args, f"{name}_latitude")
        longitude = getattr(args, f"{name}_longitude")
        if not -90 <= latitude <= 90:
            parser.error(f"--{name}-latitude must be between -90 and 90")
        if not -180 <= longitude <= 180:
            parser.error(f"--{name}-longitude must be between -180 and 180")

    backend = args.backend_url.rstrip("/") + "/api/v1/verify"
    print(
        "Travel checks compare submitted latitude/longitude and elapsed time; "
        "source IP and GeoIP do not drive this decision. Coordinates are unverified and advisory."
    )
    first = {
        "uid": uid,
        "ctr": f"{first_counter:06X}",
        "cmac": cmac_for(aes_key, uid, first_counter),
        "latitude": args.first_latitude,
        "longitude": args.first_longitude,
    }
    second_counter = first_counter + 1
    second = {
        "uid": uid,
        "ctr": f"{second_counter:06X}",
        "cmac": cmac_for(aes_key, uid, second_counter),
        "latitude": args.second_latitude,
        "longitude": args.second_longitude,
    }
    save_counter(args.vault, vault, tag, second_counter)
    print(first, second)
    print("First scan:", json.dumps(submit(backend, first)))
    print("Second scan:", json.dumps(submit(backend, second)))


if __name__ == "__main__":
    main()

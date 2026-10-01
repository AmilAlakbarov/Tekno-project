"""Development-only NTAG 424 DNA/HSM simulator.

This is not a production HSM and must not be used as one. It creates
software-only tags, keeps their test keys in a local vault, and generates
SDM-style URLs for dashboard and backend demonstrations.
"""

from __future__ import annotations

import argparse
import csv
import json
import secrets
import sys
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urlparse

from cryptography.hazmat.primitives.ciphers import algorithms
from cryptography.hazmat.primitives.cmac import CMAC


DEFAULT_VAULT = Path(__file__).with_name("simulator_vault.json")
DEFAULT_MANIFEST = Path(__file__).with_name("provisioning_manifest.json")


def aes_cmac(key: bytes, message: bytes) -> bytes:
    mac = CMAC(algorithms.AES(key))
    mac.update(message)
    return mac.finalize()


def derive_sdm_cmac_key(base_key: bytes, uid: bytes, counter: int) -> bytes:
    counter_bytes = counter.to_bytes(3, "big")
    vector = bytes.fromhex("3CC300010080") + uid + counter_bytes[::-1]
    return aes_cmac(base_key, vector)


def sdm_cmac(base_key: bytes, uid_hex: str, counter: int, mac_input: str) -> str:
    uid = bytes.fromhex(uid_hex)
    session_key = derive_sdm_cmac_key(base_key, uid, counter)
    full_cmac = aes_cmac(session_key, mac_input.encode("utf-8"))
    return bytes(full_cmac[index] for index in range(1, 16, 2)).hex().upper()


def read_json(path: Path) -> dict:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def validate_uid(uid: str) -> str:
    uid = uid.upper()
    if len(uid) != 14:
        raise ValueError("UID must contain exactly 14 hexadecimal characters")
    try:
        bytes.fromhex(uid)
    except ValueError as exc:
        raise ValueError("UID must contain only hexadecimal characters") from exc
    return uid


def generate(args: argparse.Namespace) -> None:
    if not 1 <= args.count <= 1000:
        raise ValueError("count must be between 1 and 1000")
    if args.uid and args.count != 1:
        raise ValueError("provide --uid for a single tag, or use --count to generate multiple tags")
    vault = read_json(args.vault)
    tags = vault.setdefault("tags", {})
    manifest = []
    for _ in range(args.count):
        uid = validate_uid(args.uid) if args.uid else "04" + secrets.token_hex(6).upper()
        if uid in tags:
            raise ValueError(f"UID already exists in vault: {uid}")
        key = secrets.token_hex(16).upper()
        tags[uid] = {
            "uid": uid,
            "aesKey": key,
            "counter": 0,
            "productId": args.product_id,
            "displayName": args.name or f"Simulated tag {uid}",
            "description": args.description or "Software-only provisioning demonstration tag",
        }
        manifest.append({
            "uid": uid,
            "productId": args.product_id,
            "displayName": tags[uid]["displayName"],
            "description": tags[uid]["description"],
            "status": "ACTIVE",
        })
        if args.uid:
            break
    write_json(args.vault, vault)
    write_json(args.manifest, {"source": "development-hsm-simulator", "tags": manifest})
    csv_output = args.csv_output or args.vault.with_name(
        f"provisioning_batch_{uuid.uuid4().hex[:8]}.csv"
    )
    write_tags_csv(csv_output, [tags[item["uid"]] for item in manifest])
    print(f"Created {len(manifest)} simulated tag(s).")
    print(f"Vault: {args.vault}")
    print(f"Safe manifest: {args.manifest}")
    print(f"Batch provisioning CSV: {csv_output}")
    for tag in manifest:
        print(f"  {tag['uid']}  {tag['displayName']}")


def sign(args: argparse.Namespace) -> None:
    vault = read_json(args.vault)
    uid = validate_uid(args.uid)
    tag = vault.get("tags", {}).get(uid)
    if tag is None:
        raise ValueError(f"UID is not in the simulator vault: {uid}")
    counter = args.counter if args.counter is not None else int(tag["counter"]) + 1
    if not 0 <= counter <= 0xFFFFFF:
        raise ValueError("counter must fit in the NTAG 424 three-byte counter")
    endpoint = args.endpoint.rstrip("?&")
    query_without_cmac = urlencode({"uid": uid, "ctr": f"{counter:06X}", "cmac": ""})
    mac_input = endpoint + "?" + query_without_cmac
    cmac = sdm_cmac(bytes.fromhex(tag["aesKey"]), uid, counter, mac_input)
    tag["counter"] = counter
    write_json(args.vault, vault)
    print(f"{endpoint}?uid={uid}&ctr={counter:06X}&cmac={cmac}")


def export_manifest(args: argparse.Namespace) -> None:
    vault = read_json(args.vault)
    result = []
    for tag in vault.get("tags", {}).values():
        item = {key: value for key, value in tag.items() if key not in {"counter", "aesKey"}}
        if args.include_keys:
            item["aesKey"] = tag["aesKey"]
        result.append(item)
    write_json(args.output, {"source": "development-hsm-simulator", "tags": result})
    print(f"Wrote {len(result)} tag(s) to {args.output}.")


def export_csv(args: argparse.Namespace) -> None:
    vault = read_json(args.vault)
    tags = vault.get("tags", {})
    write_tags_csv(args.output, list(tags.values()))
    print(f"Wrote {len(tags)} provisioning row(s) to {args.output}.")


def write_tags_csv(path: Path, tags: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=[
            "uid", "aesKey", "productId", "displayName", "description", "imageUrl"
        ])
        writer.writeheader()
        for tag in tags:
            writer.writerow({
                "uid": tag["uid"],
                "aesKey": tag["aesKey"],
                "productId": tag.get("productId", ""),
                "displayName": tag.get("displayName", ""),
                "description": tag.get("description", ""),
                "imageUrl": tag.get("imageUrl", ""),
            })


def import_csv(args: argparse.Namespace) -> None:
    vault = read_json(args.vault)
    tags = vault.setdefault("tags", {})
    pending = {}
    existing = 0
    with args.input.open("r", newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        columns = {name.strip().lower(): name for name in (reader.fieldnames or []) if name}
        required = {"uid", "aeskey", "productid"}
        if not required.issubset(columns):
            raise ValueError("CSV must contain uid,aesKey,productId columns")
        seen = set()
        for row_number, row in enumerate(reader, start=2):
            uid = validate_uid((row.get(columns["uid"]) or "").strip())
            aes_key = (row.get(columns["aeskey"]) or "").strip().upper()
            product_id = (row.get(columns["productid"]) or "").strip()
            if len(aes_key) != 32:
                raise ValueError(f"row {row_number}: aesKey must contain 32 hexadecimal characters")
            try:
                bytes.fromhex(aes_key)
            except ValueError as exc:
                raise ValueError(f"row {row_number}: aesKey must contain hexadecimal characters") from exc
            if not product_id:
                raise ValueError(f"row {row_number}: productId is required")
            if uid in seen:
                raise ValueError(f"row {row_number}: duplicate UID in CSV: {uid}")
            seen.add(uid)
            tag = {
                "uid": uid,
                "aesKey": aes_key,
                "counter": 0,
                "productId": product_id,
                "displayName": (row.get(columns.get("displayname", "")) or "").strip() or f"Imported tag {uid}",
                "description": (row.get(columns.get("description", "")) or "").strip(),
                "imageUrl": (row.get(columns.get("imageurl", "")) or "").strip(),
            }
            if uid in tags:
                if tags[uid].get("aesKey", "").upper() != aes_key:
                    raise ValueError(f"CSV key conflicts with the simulator vault UID: {uid}")
                existing += 1
            else:
                pending[uid] = tag
    tags.update(pending)
    write_json(args.vault, vault)
    print(f"Imported {len(pending)} tag(s); {existing} matching tag(s) already existed.")


class HsmHandler(BaseHTTPRequestHandler):
    server_version = "AuthentiChain-Development-HSM/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json({"status": "ok", "mode": "development-only"})
            return
        if parsed.path == "/v1/hsm/key":
            uid = parse_qs(parsed.query).get("uid", [None])[0]
            if not uid:
                self.send_json({"error": "uid is required"}, 400)
                return
            try:
                uid = validate_uid(uid)
            except ValueError as exc:
                self.send_json({"error": str(exc)}, 400)
                return
            tag = self.tags().get(uid)
            if tag is None:
                self.send_json({"error": "UID is not in the simulator vault"}, 404)
                return
            self.send_json({
                "uid": uid,
                "aesKey": tag["aesKey"],
                "warning": "Development simulator only; never expose this endpoint publicly",
            })
            return
        if parsed.path == "/v1/tags":
            self.send_json({"tags": list(self.tags().values())})
            return
        self.send_json({"error": "not found"}, 404)

    def do_POST(self) -> None:
        if self.path != "/v1/hsm/keys":
            self.send_json({"error": "not found"}, 404)
            return
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
            count = int(body.get("count", 1))
            if not 1 <= count <= 1000:
                raise ValueError("count must be between 1 and 1000")
            tags = self.tags()
            created = []
            for _ in range(count):
                uid = validate_uid(body.get("uid")) if body.get("uid") else "04" + secrets.token_hex(6).upper()
                if uid in tags:
                    raise ValueError(f"UID already exists in vault: {uid}")
                tags[uid] = {
                    "uid": uid,
                    "aesKey": secrets.token_hex(16).upper(),
                    "counter": 0,
                    "productId": body.get("productId", ""),
                    "displayName": body.get("displayName") or f"Simulated tag {uid}",
                    "description": body.get("description", ""),
                    "imageUrl": body.get("imageUrl", ""),
                }
                created.append({"uid": uid, "productId": tags[uid]["productId"]})
                if body.get("uid"):
                    break
            self.write_vault(tags)
            self.send_json({"created": created}, 201)
        except (ValueError, json.JSONDecodeError) as exc:
            self.send_json({"error": str(exc)}, 400)

    def tags(self) -> dict:
        return read_json(self.server.vault).setdefault("tags", {})

    def write_vault(self, tags: dict) -> None:
        write_json(self.server.vault, {"tags": tags})

    def send_json(self, value: dict, status: int = 200) -> None:
        payload = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: object) -> None:
        print(f"[hsm] {format % args}")


def serve(args: argparse.Namespace) -> None:
    server = ThreadingHTTPServer((args.bind, args.port), HsmHandler)
    server.vault = args.vault
    print(f"Development HSM listening on http://{args.bind}:{args.port}")
    print("This service must remain local and must never be deployed publicly.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
    finally:
        server.server_close()


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description="Development-only AuthentiChain tag/HSM simulator")
    root.add_argument("--vault", type=Path, default=DEFAULT_VAULT)
    sub = root.add_subparsers(dest="command", required=True)

    create = sub.add_parser("generate", help="create software-only tags")
    create.add_argument("--count", type=int, default=1)
    create.add_argument("--uid", help="custom 14-character hexadecimal UID")
    create.add_argument("--product-id", required=True)
    create.add_argument("--name")
    create.add_argument("--description")
    create.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    create.add_argument("--csv-output", type=Path,
                        help="write a key-bearing CSV for only this generated batch")
    create.set_defaults(function=generate)

    make_url = sub.add_parser("sign", help="generate one SDM-style verification URL")
    make_url.add_argument("--uid", required=True)
    make_url.add_argument("--endpoint", default="https://authentichain.website/nfc/v1/verify")
    make_url.add_argument("--counter", type=int)
    make_url.set_defaults(function=sign)

    export = sub.add_parser("export", help="export a provisioning manifest")
    export.add_argument("--output", type=Path, default=DEFAULT_MANIFEST)
    export.add_argument("--include-keys", action="store_true",
                        help="include AES keys; development use only")
    export.set_defaults(function=export_manifest)

    csv_export = sub.add_parser("export-csv", help="export key-bearing CSV for local provisioning only")
    csv_export.add_argument("--output", type=Path,
                            default=Path(__file__).with_name("provisioning_keys.csv"))
    csv_export.set_defaults(function=export_csv)

    csv_import = sub.add_parser("import-csv", help="import a provisioning CSV into the local simulator vault")
    csv_import.add_argument("--input", type=Path, required=True)
    csv_import.set_defaults(function=import_csv)

    server = sub.add_parser("serve", help="run the local development HSM HTTP service")
    server.add_argument("--bind", default="127.0.0.1")
    server.add_argument("--port", type=int, default=8787)
    server.set_defaults(function=serve)
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        args.function(args)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

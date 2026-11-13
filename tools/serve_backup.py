"""Serve one backup file so a phone can download it into a place MIUI will open.

Why this exists: `adb push` into `/sdcard/Download` produces a file the MIUI document picker
lists as "0 Б" and refuses to open, so the app's SAF restore dialog cannot see a backup that
arrived that way (P-47). A file an app *downloaded* is a first-class document. This script
turns "copy the backup to the phone" into a browser download:

    python tools/serve_backup.py tmp/balancecalories-backup.json

then on the phone open the printed URL, confirm the download, and pick the file in the app
(Settings -> Управление данными -> Восстановить из копии). The picker's tiles may still
ignore taps; `adb shell input keyevent KEYCODE_TAB`, `KEYCODE_DPAD_DOWN`, `KEYCODE_ENTER`
selects the focused tile.

The file is the user's health data, so the server serves exactly that one name, refuses
everything else, and is meant to run for minutes on the home LAN - not to be left running.
`Content-Disposition: attachment` is what stops a browser from merely rendering the JSON.
"""

from __future__ import annotations

import argparse
import http.server
import os
import socket
import socketserver
import sys


def lan_addresses() -> list[str]:
    """Every non-loopback IPv4 address, so the operator can pick the one the phone can reach."""
    addresses: list[str] = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            address = info[4][0]
            if not address.startswith("127.") and address not in addresses:
                addresses.append(address)
    except OSError:
        pass
    return addresses


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("backup", help="path to the balancecalories JSON backup to serve")
    parser.add_argument("--port", type=int, default=8081, help="port to listen on (default 8081)")
    parser.add_argument(
        "--name",
        default=None,
        help="file name the browser saves as (default: the backup's own name)",
    )
    args = parser.parse_args()

    path = os.path.abspath(args.backup)
    if not os.path.isfile(path):
        print(f"not a file: {path}", file=sys.stderr)
        return 2
    name = args.name or os.path.basename(path)
    with open(path, "rb") as handle:
        body = handle.read()

    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler's spelling
            requested = self.path.split("?", 1)[0].strip("/")
            if requested not in ("", name):
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Disposition", f'attachment; filename="{name}"')
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, fmt: str, *extra: object) -> None:
            print("served: " + (fmt % extra), flush=True)

    with socketserver.TCPServer(("0.0.0.0", args.port), Handler) as httpd:
        print(f"serving {name}, {len(body)} bytes, on port {args.port}:", flush=True)
        for address in lan_addresses() or ["<this host>"]:
            print(f"  http://{address}:{args.port}/{name}", flush=True)
        print("stop with Ctrl+C once the phone has the file", flush=True)
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("stopped", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Christoph Langner
# SPDX-License-Identifier: GPL-3.0-or-later
"""
QA only: an HTTP proxy that logs which hosts a device talks to – nothing else.

    tools/host-log-proxy.py --port 8888 --log hosts.log
    emulator -avd <avd> -http-proxy http://127.0.0.1:8888

HTTPS is tunnelled untouched (CONNECT): the proxy sees the host name and port, never the
content, and needs no certificate on the device. Plain HTTP requests are forwarded and
logged with host and path. Each line: ISO time, method, host:port[, path].
"""
import argparse
import datetime
import select
import socket
import threading


def log(f, lock, line):
    stamp = datetime.datetime.now().isoformat(timespec="seconds")
    with lock:
        f.write(f"{stamp} {line}\n")
        f.flush()


def pipe(a, b):
    try:
        while True:
            r, _, _ = select.select([a, b], [], [], 60)
            if not r:
                return
            for s in r:
                data = s.recv(65536)
                if not data:
                    return
                (b if s is a else a).sendall(data)
    except OSError:
        return
    finally:
        a.close()
        b.close()


def handle(client, f, lock):
    try:
        head = b""
        while b"\r\n\r\n" not in head and len(head) < 65536:
            chunk = client.recv(4096)
            if not chunk:
                return
            head += chunk
        first = head.split(b"\r\n", 1)[0].decode("latin-1")
        method, target, _ = first.split(" ", 2)
        if method == "CONNECT":
            host, port = target.rsplit(":", 1)
            log(f, lock, f"CONNECT {host}:{port}")
            upstream = socket.create_connection((host, int(port)), timeout=20)
            client.sendall(b"HTTP/1.1 200 Connection established\r\n\r\n")
            pipe(client, upstream)
            return
        # plain http: absolute URL in the request line
        rest = target.split("://", 1)[1]
        hostport, _, path = rest.partition("/")
        host, _, port = hostport.partition(":")
        log(f, lock, f"{method} {host}:{port or 80} /{path.split('?')[0]}")
        # the emulator's name for the host machine
        upstream = socket.create_connection(("127.0.0.1" if host == "10.0.2.2" else host, int(port or 80)), timeout=20)
        line = f"{method} /{path} HTTP/1.1".encode()
        upstream.sendall(line + head[len(first):])
        pipe(client, upstream)
    except Exception as e:  # noqa: BLE001 – a QA tool: log and go on
        log(f, lock, f"ERROR {e!r}")
        client.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8888)
    ap.add_argument("--log", default="hosts.log")
    a = ap.parse_args()
    f = open(a.log, "a")
    lock = threading.Lock()
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", a.port))
    srv.listen(64)
    print(f"logging proxy on 127.0.0.1:{a.port} → {a.log}", flush=True)
    while True:
        c, _ = srv.accept()
        threading.Thread(target=handle, args=(c, f, lock), daemon=True).start()


if __name__ == "__main__":
    main()

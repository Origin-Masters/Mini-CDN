#!/usr/bin/env python3
"""Real acceptance check for router load balancing.

This script creates an isolated region with 10 managed edge instances, sends
1000 real routing requests, and verifies that the requests were distributed
within a 10 percent tolerance inside a 60-second window.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Mapping

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))


class ScriptError(RuntimeError):
    """Raised for predictable validation and setup failures."""


class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Keep 307 responses visible for routing checks."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


@dataclass(frozen=True)
class Config:
    """Runtime configuration for the acceptance script."""

    token: str
    router_base: str
    origin_base: str
    request_count: int
    edge_count: int
    tolerance_percent: int
    window_sec: int
    concurrency: int
    region: str
    file_path: str
    auto_start_services: bool
    cleanup: bool


def env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def request(
        method: str,
        url: str,
        *,
        token: str | None = None,
        data: bytes | None = None,
        content_type: str | None = None,
        timeout: float = 10.0,
) -> tuple[int, bytes, Mapping[str, str]]:
    req = urllib.request.Request(url=url, method=method, data=data)
    if token:
        req.add_header("X-Admin-Token", token)
    if content_type:
        req.add_header("Content-Type", content_type)

    opener = urllib.request.build_opener(_NoRedirectHandler)
    try:
        with opener.open(req, timeout=timeout) as response:
            return int(response.status), response.read(), dict(response.headers.items())
    except urllib.error.HTTPError as ex:
        return int(ex.code), ex.read(), dict(ex.headers.items()) if ex.headers else {}
    except urllib.error.URLError as ex:
        reason = ex.reason if ex.reason else ex
        raise ScriptError(f"Request failed for {method} {url}: {reason}") from ex


def require_status(method: str, url: str, expected: int, **kwargs: object) -> tuple[bytes, Mapping[str, str]]:
    status, body, headers = request(method, url, **kwargs)
    if status != expected:
        raise ScriptError(f"Unexpected HTTP status {status} for {method} {url}, expected {expected}")
    return body, headers


def ensure_services(config: Config) -> None:
    health_url = f"{config.router_base}/api/cdn/health"
    try:
        status, _, _ = request("GET", health_url, token=config.token, timeout=3.0)
    except ScriptError:
        status = 0

    if status == 200:
        return

    if not config.auto_start_services:
        raise ScriptError(
            "Router is not reachable. Start the services manually or use --auto-start-services."
        )

    startup_script = os.path.join(ROOT_DIR, "scripts", "runtime", "startup-service.sh")
    bash = shutil.which("bash")
    if not bash or not os.path.exists(startup_script):
        raise ScriptError(
            "Auto startup requested, but scripts/runtime/startup-service.sh is unavailable."
        )

    subprocess.run([bash, startup_script], cwd=ROOT_DIR, check=True)

    status_after, _, _ = request("GET", health_url, token=config.token, timeout=5.0)
    if status_after != 200:
        raise ScriptError("Router is still unreachable after startup attempt.")


def ensure_edge_jar() -> None:
    edge_jar = os.path.join(ROOT_DIR, "edge", "target", "edge-1.0-SNAPSHOT-exec.jar")
    if os.path.exists(edge_jar):
        return

    mvn = shutil.which("mvn")
    if not mvn:
        raise ScriptError("Edge executable JAR is missing and Maven is not available.")

    print("[setup] building edge executable jar ...")
    subprocess.run([mvn, "-pl", "edge", "-am", "-DskipTests", "package"], cwd=ROOT_DIR, check=True)

    if not os.path.exists(edge_jar):
        raise ScriptError(f"Edge executable JAR not found after build: {edge_jar}")


def upload_test_file(config: Config) -> None:
    clean_path = urllib.parse.quote(config.file_path, safe="/")
    url = f"{config.origin_base}/api/origin/admin/files/{clean_path}"
    payload = f"load-balancing acceptance payload for {config.region}\n".encode("utf-8")
    status, _, _ = request(
        "PUT",
        url,
        token=config.token,
        data=payload,
        content_type="application/octet-stream",
        timeout=10.0,
    )
    if status not in (201, 204):
        raise ScriptError(f"Upload failed with HTTP {status}")


def delete_test_file(config: Config) -> None:
    clean_path = urllib.parse.quote(config.file_path, safe="/")
    url = f"{config.origin_base}/api/origin/admin/files/{clean_path}"
    status, body, _ = request("DELETE", url, token=config.token, timeout=10.0)
    if status in (200, 204, 404):
        return
    raise ScriptError(f"File cleanup failed with HTTP {status}: {body.decode('utf-8', errors='replace')}")


def start_managed_edges(config: Config) -> list[str]:
    payload = {
        "region": config.region,
        "count": config.edge_count,
        "originBaseUrl": config.origin_base,
        "autoRegister": True,
        "waitUntilReady": True,
    }
    url = f"{config.router_base}/api/cdn/admin/edges/activations/automations"
    status, body, _ = request(
        "POST",
        url,
        token=config.token,
        data=json.dumps(payload).encode("utf-8"),
        content_type="application/json",
        timeout=max(30.0, float(config.edge_count) * 10.0),
    )
    if status != 201:
        raise ScriptError(f"Auto-start failed with HTTP {status}: {body.decode('utf-8', errors='replace')}")

    response = json.loads(body.decode("utf-8"))
    edges = response.get("edges", [])
    urls = [edge["url"].rstrip("/") for edge in edges if "url" in edge]
    if len(urls) != config.edge_count:
        raise ScriptError(f"Expected {config.edge_count} started edges, got {len(urls)}")
    return urls


def stop_managed_region(config: Config) -> None:
    encoded_region = urllib.parse.quote(config.region, safe="")
    url = f"{config.router_base}/api/cdn/admin/edges/regions/{encoded_region}?deregister=true"
    status, body, _ = request("DELETE", url, token=config.token, timeout=20.0)
    if status in (200, 404):
        return
    raise ScriptError(f"Cleanup failed with HTTP {status}: {body.decode('utf-8', errors='replace')}")


def send_requests(config: Config) -> tuple[int, float]:
    path = urllib.parse.quote(config.file_path, safe="/")
    base_url = f"{config.router_base}/api/cdn/files/{path}"
    started = time.monotonic()

    def worker(request_indices: list[int]) -> int:
        ok = 0
        for index in request_indices:
            query = urllib.parse.urlencode(
                {"region": config.region, "clientId": f"lb-{index}"}
            )
            status, _, headers = request("GET", f"{base_url}?{query}", timeout=5.0)
            if status != 307:
                raise ScriptError(f"Routing request {index} failed with HTTP {status}")
            if "Location" not in headers:
                raise ScriptError(f"Routing request {index} returned 307 without Location header")
            ok += 1
        return ok

    chunks: list[list[int]] = [[] for _ in range(config.concurrency)]
    for index in range(config.request_count):
        chunks[index % config.concurrency].append(index)

    with concurrent.futures.ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        results = list(executor.map(worker, chunks))

    elapsed = time.monotonic() - started
    return sum(results), elapsed


def fetch_stats(config: Config) -> dict[str, object]:
    url = (
        f"{config.router_base}/api/cdn/admin/stats?"
        f"windowSec={config.window_sec}&aggregateEdge=false"
    )
    body, _ = require_status("GET", url, 200, token=config.token, timeout=10.0)
    return json.loads(body.decode("utf-8"))


def normalize_edge_url(value: str) -> str:
    clean = value.strip()
    return clean if clean.endswith("/") else clean + "/"


def extract_distribution(stats: dict[str, object], config: Config, edge_urls: list[str]) -> dict[str, int]:
    downloads = stats.get("downloads")
    if not isinstance(downloads, dict):
        raise ScriptError("Stats response does not contain a downloads section.")

    by_file_by_edge_window = downloads.get("byFileByEdgeWindow")
    if not isinstance(by_file_by_edge_window, dict):
        raise ScriptError("Stats response does not contain downloads.byFileByEdgeWindow.")

    relevant = by_file_by_edge_window.get(config.file_path)
    if not isinstance(relevant, dict):
        raise ScriptError(f"No download stats found for test file {config.file_path}.")

    normalized_relevant: dict[str, int] = {}
    for key, value in relevant.items():
        if isinstance(key, str):
            normalized_relevant[normalize_edge_url(key)] = int(value)

    distribution: dict[str, int] = {}
    for url in edge_urls:
        distribution[url] = normalized_relevant.get(normalize_edge_url(url), 0)
    return distribution


def verify_acceptance(config: Config, distribution: dict[str, int], elapsed_sec: float) -> None:
    total = sum(distribution.values())
    if total != config.request_count:
        raise ScriptError(
            f"Expected {config.request_count} routed requests in stats, got {total}."
        )
    if elapsed_sec > config.window_sec:
        raise ScriptError(
            f"Requests took {elapsed_sec:.2f}s and exceeded the {config.window_sec}s window."
        )

    ideal = config.request_count / config.edge_count
    tolerance_abs = ideal * (config.tolerance_percent / 100.0)

    failures = []
    for url, count in sorted(distribution.items()):
        deviation = abs(count - ideal)
        if deviation > tolerance_abs:
            failures.append(f"{url} -> {count} requests")

    print("\n[result] load-balancing distribution")
    print(f"  region               : {config.region}")
    print(f"  file                 : {config.file_path}")
    print(f"  requests             : {config.request_count}")
    print(f"  edges                : {config.edge_count}")
    print(f"  elapsedSec           : {elapsed_sec:.2f}")
    print(f"  windowSec            : {config.window_sec}")
    print(f"  idealPerEdge         : {ideal:.2f}")
    print(f"  tolerancePercent     : {config.tolerance_percent}")
    print(f"  toleranceAbsolute    : {tolerance_abs:.2f}")
    for url, count in sorted(distribution.items()):
        print(f"  {url:<28} {count}")

    if failures:
        raise ScriptError(
            "Acceptance criteria failed. Distribution outside tolerance:\n  "
            + "\n  ".join(failures)
        )

    print("\n[ok] acceptance criteria met")
    print("[ok] all 10 managed edges were used and remained within 10 percent tolerance")


def build_config(args: argparse.Namespace) -> Config:
    timestamp = int(time.time())
    region = args.region or f"LB-DEMO-{timestamp}"
    file_path = args.file_path or f"acceptance/lb-demo-{timestamp}.txt"
    return Config(
        token=args.token,
        router_base=args.router_base.rstrip("/"),
        origin_base=args.origin_base.rstrip("/"),
        request_count=args.request_count,
        edge_count=args.edge_count,
        tolerance_percent=args.tolerance_percent,
        window_sec=args.window_sec,
        concurrency=args.concurrency,
        region=region,
        file_path=file_path,
        auto_start_services=args.auto_start_services,
        cleanup=not args.keep_running,
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a real load-balancing acceptance check against the local Mini-CDN."
    )
    parser.add_argument(
        "--token",
        default=os.getenv("MINICDN_ADMIN_TOKEN", "secret-token"),
        help="admin token for router/origin requests",
    )
    parser.add_argument(
        "--router-base",
        default=os.getenv("MINICDN_ROUTER_BASE", "http://localhost:8082"),
        help="router base URL",
    )
    parser.add_argument(
        "--origin-base",
        default=os.getenv("MINICDN_ORIGIN_BASE", "http://localhost:8080"),
        help="origin base URL",
    )
    parser.add_argument(
        "--request-count",
        type=int,
        default=1000,
        help="number of routing requests to execute",
    )
    parser.add_argument(
        "--edge-count",
        type=int,
        default=10,
        help="number of managed edges to start",
    )
    parser.add_argument(
        "--tolerance-percent",
        type=int,
        default=10,
        help="allowed deviation per edge in percent",
    )
    parser.add_argument(
        "--window-sec",
        type=int,
        default=60,
        help="time window used for the stats check",
    )
    parser.add_argument(
        "--concurrency",
        type=int,
        default=20,
        help="number of worker threads for the request burst",
    )
    parser.add_argument(
        "--region",
        default="",
        help="optional dedicated region name for the script run",
    )
    parser.add_argument(
        "--file-path",
        default="",
        help="optional unique origin file path for the script run",
    )
    parser.add_argument(
        "--auto-start-services",
        action="store_true",
        default=env_bool("AUTO_START_SERVICES", False),
        help="start local services with scripts/runtime/startup-service.sh if router is unreachable",
    )
    parser.add_argument(
        "--keep-running",
        action="store_true",
        help="do not stop the managed demo edges after the check",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    config = build_config(args)

    if config.request_count <= 0:
        raise ScriptError("--request-count must be > 0")
    if config.edge_count <= 0:
        raise ScriptError("--edge-count must be > 0")
    if config.window_sec <= 0:
        raise ScriptError("--window-sec must be > 0")
    if config.concurrency <= 0:
        raise ScriptError("--concurrency must be > 0")

    edge_urls: list[str] = []
    try:
        print("[setup] checking local services ...")
        ensure_services(config)
        ensure_edge_jar()

        print(f"[setup] uploading test file to origin: {config.file_path}")
        upload_test_file(config)

        print(f"[setup] starting {config.edge_count} managed edge instances in region {config.region} ...")
        edge_urls = start_managed_edges(config)

        print(f"[run] sending {config.request_count} routing requests ...")
        successful_requests, elapsed_sec = send_requests(config)
        print(f"[run] completed {successful_requests} requests in {elapsed_sec:.2f}s")

        print("[check] fetching router stats ...")
        stats = fetch_stats(config)
        distribution = extract_distribution(stats, config, edge_urls)
        verify_acceptance(config, distribution, elapsed_sec)
        return 0
    finally:
        if config.cleanup and edge_urls:
            print("\n[cleanup] stopping managed demo edges ...")
            try:
                stop_managed_region(config)
            except ScriptError as ex:
                print(f"[cleanup] warning: {ex}", file=sys.stderr)
        if config.cleanup:
            print("[cleanup] deleting uploaded origin test file ...")
            try:
                delete_test_file(config)
            except ScriptError as ex:
                print(f"[cleanup] warning: {ex}", file=sys.stderr)


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv[1:]))
    except ScriptError as ex:
        print(f"[error] {ex}", file=sys.stderr)
        raise SystemExit(1)

#!/usr/bin/env python3
"""NFA-C1 throughput benchmark (1 Edge vs. 2 Edges).

This script is platform-independent and intentionally relies only on the Python
standard library.
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
from typing import Mapping, Optional


@dataclass(frozen=True)
class BenchmarkConfig:
    """Configuration values for the throughput benchmark."""

    token: str
    router_base: str
    origin_base: str
    test_region: str
    test_file: str
    duration_sec: int
    concurrency: int
    warmup_requests: int
    edge_jar: str
    auto_start_services: bool


@dataclass(frozen=True)
class RunResult:
    """Single benchmark run metrics."""

    label: str
    success_count: int
    elapsed_sec: float
    routed_to_edge_count: int
    edge_hits: dict[str, int]

    @property
    def rps(self) -> float:
        """Computed requests-per-second for this run."""
        if self.elapsed_sec <= 0:
            return 0.0
        return self.success_count / self.elapsed_sec


class BenchmarkError(RuntimeError):
    """Raised for benchmark setup and execution errors."""


def env_bool(name: str, default: bool) -> bool:
    """Parse boolean environment variables in a predictable way."""
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def build_config(args: argparse.Namespace) -> BenchmarkConfig:
    """Build benchmark configuration from CLI args and environment values."""
    return BenchmarkConfig(
        token=args.token,
        router_base=args.router_base.rstrip("/"),
        origin_base=args.origin_base.rstrip("/"),
        test_region=args.region,
        test_file=args.test_file,
        duration_sec=args.duration_sec,
        concurrency=args.concurrency,
        warmup_requests=args.warmup_requests,
        edge_jar=args.edge_jar,
        auto_start_services=args.auto_start_services,
    )

class _NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    """Prevent urllib from automatically following redirects."""
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def request(
        method: str,
        url: str,
        token: Optional[str] = None,
        data: Optional[bytes] = None,
        content_type: Optional[str] = None,
        timeout: float = 5.0,
) -> tuple[int, bytes, Mapping[str, str]]:
    """Execute an HTTP request and return status code with body.

    Redirects are intentionally not followed to keep router 307 responses
    observable in throughput measurements.
    """
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


def require_status(method: str, url: str, expected: int, **kwargs: object) -> bytes:
    """Execute request and ensure expected status code."""
    status, body, _ = request(method, url, **kwargs)
    if status != expected:
        raise BenchmarkError(f"Unexpected HTTP status {status} for {method} {url}, expected {expected}")
    return body


def ensure_services(config: BenchmarkConfig) -> None:
    """Validate router availability and optionally start local services."""
    health_url = f"{config.router_base}/api/cdn/health"
    status, _, _ = request("GET", health_url, token=config.token)
    if status == 200:
        return

    if not config.auto_start_services:
        raise BenchmarkError(
            "Router is not reachable. Start services manually or set AUTO_START_SERVICES=true."
        )

    startup_script = "startup-service.sh"
    bash = shutil.which("bash")
    if not bash or not os.path.exists(startup_script):
        raise BenchmarkError(
            "AUTO_START_SERVICES=true but bash/startup-service.sh is unavailable. Start services manually."
        )

    subprocess.run([bash, startup_script], check=True)

    status_after, _, _ = request("GET", health_url, token=config.token)
    if status_after != 200:
        raise BenchmarkError("Router is still unreachable after startup attempt.")


def ensure_edge_jar(config: BenchmarkConfig) -> None:
    """Ensure the Edge executable JAR exists."""
    if os.path.exists(config.edge_jar):
        return

    mvn = shutil.which("mvn")
    if not mvn:
        raise BenchmarkError(f"Missing {config.edge_jar} and Maven is not available to build it.")

    subprocess.run([mvn, "-q", "-DskipTests", "package"], cwd="edge", check=True)

    if not os.path.exists(config.edge_jar):
        raise BenchmarkError(f"Edge JAR not found after build: {config.edge_jar}")


def ensure_single_edge_setup(config: BenchmarkConfig) -> None:
    """Register baseline edge route for deterministic 1-edge run."""
    query = urllib.parse.urlencode({"region": config.test_region, "url": "http://localhost:8081"})
    url = f"{config.router_base}/api/cdn/routing?{query}"
    status, _, _ = request("POST", url, token=config.token)
    if status not in (200, 201, 204, 409):
        raise BenchmarkError(f"Failed baseline edge setup: status={status}")


def upload_test_file(config: BenchmarkConfig) -> None:
    """Upload fixed benchmark payload to origin."""
    url = f"{config.origin_base}/api/origin/admin/files/{config.test_file}"
    payload = b"nfa-c1 benchmark payload"
    status, _, _ = request("PUT", url, token=config.token, data=payload, content_type="application/octet-stream")
    if status not in (200, 201, 204):
        raise BenchmarkError(f"Upload failed with HTTP {status}")


def warmup_router(config: BenchmarkConfig, client_prefix: str) -> None:
    """Run warmup requests to reduce cold-start distortion."""
    for idx in range(1, config.warmup_requests + 1):
        query = urllib.parse.urlencode(
            {
                "region": config.test_region,
                "clientId": f"{client_prefix}-warmup-{idx}",
            }
        )
        url = f"{config.router_base}/api/cdn/files/{config.test_file}?{query}"
        status, _, _ = request("GET", url)
        if status != 307:
            raise BenchmarkError(f"Warmup failed with HTTP {status} at request {idx}")


def run_load_test(config: BenchmarkConfig, label: str, client_prefix: str) -> RunResult:
    """Execute concurrent load for fixed duration and return throughput metrics."""
    end_time = time.monotonic() + config.duration_sec
    start = time.monotonic()

    def worker(worker_id: int) -> tuple[int, int, dict[str, int]]:
        ok = 0
        routed = 0
        local_hits: dict[str, int] = {}
        attempt = 0
        while time.monotonic() < end_time:
            attempt += 1
            query = urllib.parse.urlencode(
                {
                    "region": config.test_region,
                    "clientId": f"{client_prefix}-w{worker_id}-r{attempt}",
                }
            )
            url = f"{config.router_base}/api/cdn/files/{config.test_file}?{query}"
            status, _, headers = request("GET", url)
            if status == 307:
                ok += 1
                location = headers.get("Location", "")
                if location:
                    routed += 1
                    host = urllib.parse.urlparse(location).netloc
                    local_hits[host] = local_hits.get(host, 0) + 1
        return ok, routed, local_hits

    with concurrent.futures.ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        worker_results = list(executor.map(worker, range(1, config.concurrency + 1)))

    elapsed = time.monotonic() - start
    successes = sum(result[0] for result in worker_results)
    routed = sum(result[1] for result in worker_results)
    edge_hits: dict[str, int] = {}
    for _, _, local_hits in worker_results:
        for host, count in local_hits.items():
            edge_hits[host] = edge_hits.get(host, 0) + count

    return RunResult(
        label=label,
        success_count=successes,
        elapsed_sec=elapsed,
        routed_to_edge_count=routed,
        edge_hits=edge_hits,
    )


def start_second_edge(config: BenchmarkConfig) -> str:
    """Start one additional edge using router lifecycle adapter endpoint."""
    url = f"{config.router_base}/api/cdn/admin/edges/start/auto"
    payload = {
        "region": config.test_region,
        "count": 1,
        "originBaseUrl": config.origin_base,
        "autoRegister": True,
        "waitUntilReady": True,
    }
    body = require_status(
        "POST",
        url,
        expected=201,
        token=config.token,
        data=json.dumps(payload).encode("utf-8"),
        content_type="application/json",
        timeout=30.0,
    )

    obj = json.loads(body.decode("utf-8"))
    edges = obj.get("edges", [])
    if not edges:
        raise BenchmarkError("No edge returned from lifecycle auto-start response.")
    instance_id = edges[0].get("instanceId")
    if not instance_id:
        raise BenchmarkError("Missing instanceId in lifecycle auto-start response.")
    return str(instance_id)


def stop_edge(config: BenchmarkConfig, instance_id: str) -> None:
    """Stop and deregister additional edge instance."""
    query = urllib.parse.urlencode({"deregister": "true"})
    url = f"{config.router_base}/api/cdn/admin/edges/{instance_id}?{query}"
    request("DELETE", url, token=config.token, timeout=15.0)


def print_report(result_one: RunResult, result_two: RunResult) -> float:
    """Print benchmark result table and return ratio two/one."""
    ratio = (result_two.rps / result_one.rps) if result_one.rps > 0 else 0.0
    print("\n=== NFA-C1 Throughput Report ===")
    print(f"{'Scenario':<14} | {'Success':<10} | {'Elapsed(s)':<12} | {'RPS':<12}")
    print(f"{'-'*14}-+-{'-'*10}-+-{'-'*12}-+-{'-'*12}")
    print(f"{result_one.label:<14} | {result_one.success_count:<10} | {result_one.elapsed_sec:<12.3f} | {result_one.rps:<12.3f}")
    print(f"{result_two.label:<14} | {result_two.success_count:<10} | {result_two.elapsed_sec:<12.3f} | {result_two.rps:<12.3f}")
    print(f"edge-hits one-edge={result_one.edge_hits}")
    print(f"edge-hits two-edges={result_two.edge_hits}")
    print(f"ratio(two/one)={ratio:.3f}")
    print("required>=1.5")
    return ratio


def parse_args() -> argparse.Namespace:
    """Parse command line arguments with environment-aware defaults."""
    parser = argparse.ArgumentParser(description="NFA-C1 throughput benchmark (1 vs 2 edges)")
    parser.add_argument("--token", default=os.getenv("MINICDN_ADMIN_TOKEN", "secret-token"))
    parser.add_argument("--router-base", default=os.getenv("ROUTER_BASE", "http://localhost:8082"))
    parser.add_argument("--origin-base", default=os.getenv("ORIGIN_BASE", "http://localhost:8080"))
    parser.add_argument("--region", default=os.getenv("TEST_REGION", "EU"))
    parser.add_argument("--test-file", default=os.getenv("TEST_FILE", "nfa-c1-throughput.txt"))
    parser.add_argument("--duration-sec", type=int, default=int(os.getenv("DURATION_SEC", "20")))
    parser.add_argument("--concurrency", type=int, default=int(os.getenv("CONCURRENCY", "40")))
    parser.add_argument("--warmup-requests", type=int, default=int(os.getenv("WARMUP_REQUESTS", "200")))
    parser.add_argument("--edge-jar", default=os.getenv("EDGE_JAR", "edge/target/edge-1.0-SNAPSHOT-exec.jar"))
    parser.add_argument(
        "--auto-start-services",
        action="store_true",
        default=env_bool("AUTO_START_SERVICES", False),
        help="Try starting services via startup-service.sh when router is not reachable",
    )
    return parser.parse_args()


def main() -> int:
    """Entry point executing setup, measurements, reporting, and pass/fail exit code."""
    args = parse_args()
    config = build_config(args)

    print(
        "NFA-C1 benchmark configuration: "
        f"duration={config.duration_sec}s concurrency={config.concurrency} warmup={config.warmup_requests}"
    )

    extra_edge_instance_id: Optional[str] = None
    try:
        print("[1/8] Ensure services are running...")
        ensure_services(config)

        print("[2/8] Ensure edge executable JAR exists...")
        ensure_edge_jar(config)

        print("[3/8] Ensure baseline routing with one managed edge...")
        ensure_single_edge_setup(config)

        print("[4/8] Upload benchmark file to origin...")
        upload_test_file(config)

        print("[5/8] Measure throughput with 1 edge instance...")
        warmup_router(config, "run1")
        one_result = run_load_test(config, "one-edge", "run1")

        print("[6/8] Start additional edge via lifecycle adapter...")
        extra_edge_instance_id = start_second_edge(config)
        print(f"  started extra edge: {extra_edge_instance_id}")

        print("[7/8] Measure throughput with 2 edge instances...")
        warmup_router(config, "run2")
        two_result = run_load_test(config, "two-edges", "run2")

        print("[8/8] Evaluate acceptance criterion...")
        ratio = print_report(one_result, two_result)

        if ratio >= 1.5:
            print("PASS: NFA-C1 fulfilled (throughput factor >= 1.5).")
            return 0

        print("FAIL: NFA-C1 not fulfilled (throughput factor < 1.5).")
        return 1
    except BenchmarkError as exc:
        print(f"ERROR: {exc}")
        return 2
    finally:
        if extra_edge_instance_id:
            stop_edge(config, extra_edge_instance_id)


if __name__ == "__main__":
    sys.exit(main())
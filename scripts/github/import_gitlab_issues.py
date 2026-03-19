#!/usr/bin/env python3
"""Import GitLab issues exported as CSV into a GitHub repository."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


ISSUE_ID_MARKER = "GitLab issue ID:"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Import GitLab issue CSV exports into GitHub issues."
    )
    parser.add_argument("csv_path", type=Path, help="Path to the GitLab CSV export.")
    parser.add_argument(
        "--repo",
        required=True,
        help="GitHub repository in OWNER/REPO form, for example Origin-Masters/Mini-CDN.",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("GITHUB_TOKEN", ""),
        help="GitHub token. Defaults to $GITHUB_TOKEN.",
    )
    parser.add_argument(
        "--github-api",
        default="https://api.github.com",
        help="GitHub API base URL.",
    )
    parser.add_argument(
        "--assignee-map",
        type=Path,
        help="Optional JSON file mapping GitLab usernames to GitHub usernames.",
    )
    parser.add_argument(
        "--skip-authors",
        nargs="*",
        default=[],
        help="Optional GitLab author usernames to skip entirely.",
    )
    parser.add_argument(
        "--max-issues",
        type=int,
        default=0,
        help="Optional cap on imported issues, useful for testing.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the planned actions without calling the GitHub API.",
    )
    parser.add_argument(
        "--no-create-labels",
        action="store_true",
        help="Do not create missing labels automatically.",
    )
    return parser.parse_args()


class GitHubClient:
    def __init__(self, api_base: str, token: str) -> None:
        self.api_base = api_base.rstrip("/")
        self.token = token

    def _request(
        self,
        method: str,
        path: str,
        data: dict[str, Any] | None = None,
        query: dict[str, Any] | None = None,
    ) -> Any:
        url = f"{self.api_base}{path}"
        if query:
            url = f"{url}?{urllib.parse.urlencode(query)}"

        payload = None
        headers = {
            "Accept": "application/vnd.github+json",
            "User-Agent": "gitlab-issues-importer",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        if data is not None:
            payload = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"

        request = urllib.request.Request(
            url=url,
            method=method,
            headers=headers,
            data=payload,
        )
        try:
            with urllib.request.urlopen(request) as response:
                body = response.read()
                if not body:
                    return None
                return json.loads(body.decode("utf-8"))
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"{method} {url} failed: {exc.code} {body}") from exc

    def list_repo_labels(self, repo: str) -> dict[str, dict[str, Any]]:
        labels: dict[str, dict[str, Any]] = {}
        page = 1
        while True:
            batch = self._request(
                "GET",
                f"/repos/{repo}/labels",
                query={"per_page": 100, "page": page},
            )
            if not batch:
                break
            for label in batch:
                labels[label["name"]] = label
            if len(batch) < 100:
                break
            page += 1
        return labels

    def create_label(self, repo: str, name: str, color: str) -> None:
        self._request(
            "POST",
            f"/repos/{repo}/labels",
            data={"name": name, "color": color},
        )

    def list_existing_imports(self, repo: str) -> dict[str, int]:
        imports: dict[str, int] = {}
        page = 1
        pattern = re.compile(rf"{re.escape(ISSUE_ID_MARKER)}\s*(\d+)")
        while True:
            batch = self._request(
                "GET",
                f"/repos/{repo}/issues",
                query={"state": "all", "per_page": 100, "page": page},
            )
            if not batch:
                break
            for issue in batch:
                if "pull_request" in issue:
                    continue
                body = issue.get("body") or ""
                match = pattern.search(body)
                if match:
                    imports[match.group(1)] = issue["number"]
            if len(batch) < 100:
                break
            page += 1
        return imports

    def create_issue(self, repo: str, payload: dict[str, Any]) -> dict[str, Any]:
        return self._request("POST", f"/repos/{repo}/issues", data=payload)

    def update_issue(self, repo: str, number: int, payload: dict[str, Any]) -> dict[str, Any]:
        return self._request("PATCH", f"/repos/{repo}/issues/{number}", data=payload)


def label_color(name: str) -> str:
    digest = hashlib.md5(name.encode("utf-8")).hexdigest()
    return digest[:6]


def split_labels(raw: str) -> list[str]:
    if not raw.strip():
        return []
    return [part.strip() for part in raw.split(",") if part.strip()]


def load_assignee_map(path: Path | None) -> dict[str, str]:
    if path is None:
        return {}
    with path.open(encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError("Assignee mapping must be a JSON object.")
    return {str(key): str(value) for key, value in data.items()}


def normalize_multiline(value: str) -> str:
    return value.replace("\r\n", "\n").strip()


def build_body(row: dict[str, str], labels: list[str]) -> str:
    description = normalize_multiline(row.get("Description", ""))
    metadata = [
        "Imported from GitLab.",
        "",
        f"- Original issue: {row.get('URL', '').strip()}",
        f"- {ISSUE_ID_MARKER} {row.get('Issue ID', '').strip()}",
        f"- Original author: {row.get('Author', '').strip()} ({row.get('Author Username', '').strip()})",
        f"- Original assignee: {row.get('Assignee', '').strip() or '-'} ({row.get('Assignee Username', '').strip() or '-'})",
        f"- Original state: {row.get('State', '').strip()}",
        f"- Original milestone: {row.get('Milestone', '').strip() or '-'}",
        f"- Original created at (UTC): {row.get('Created At (UTC)', '').strip() or '-'}",
        f"- Original updated at (UTC): {row.get('Updated At (UTC)', '').strip() or '-'}",
        f"- Original closed at (UTC): {row.get('Closed At (UTC)', '').strip() or '-'}",
        f"- Original labels: {', '.join(labels) if labels else '-'}",
        f"- Original time estimate: {row.get('Time Estimate', '').strip() or '-'}",
        f"- Original time spent: {row.get('Time Spent', '').strip() or '-'}",
    ]
    if description:
        return f"{description}\n\n---\n\n" + "\n".join(metadata)
    return "\n".join(metadata)


def read_rows(csv_path: Path) -> list[dict[str, str]]:
    with csv_path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        return [dict(row) for row in reader]


def main() -> int:
    args = parse_args()
    if not args.csv_path.exists():
        print(f"CSV file not found: {args.csv_path}", file=sys.stderr)
        return 1

    if not args.dry_run and not args.token:
        print("Missing GitHub token. Set GITHUB_TOKEN or pass --token.", file=sys.stderr)
        return 1

    assignee_map = load_assignee_map(args.assignee_map)
    rows = read_rows(args.csv_path)
    skipped_authors = set(args.skip_authors)
    client = GitHubClient(args.github_api, args.token)

    existing_imports: dict[str, int] = {}
    existing_labels: dict[str, dict[str, Any]] = {}

    if not args.dry_run:
        existing_imports = client.list_existing_imports(args.repo)
        existing_labels = client.list_repo_labels(args.repo)

    all_labels = sorted(
        {
            label
            for row in rows
            if row.get("Author Username", "").strip() not in skipped_authors
            for label in split_labels(row.get("Labels", ""))
        }
    )

    if args.dry_run:
        print(f"Dry run: found {len(rows)} CSV rows and {len(all_labels)} unique labels.")
    elif not args.no_create_labels:
        for label in all_labels:
            if label in existing_labels:
                continue
            print(f"Creating missing label: {label}")
            try:
                client.create_label(args.repo, label, label_color(label))
            except RuntimeError as exc:
                if "already_exists" not in str(exc):
                    raise
            time.sleep(0.1)

    created_count = 0
    skipped_count = 0

    for row in rows:
        issue_id = row.get("Issue ID", "").strip()
        title = row.get("Title", "").strip()
        author_username = row.get("Author Username", "").strip()

        if args.max_issues and created_count >= args.max_issues:
            break

        if author_username in skipped_authors:
            print(f"Skipping issue #{issue_id} ({title}): author {author_username} skipped.")
            skipped_count += 1
            continue

        if issue_id in existing_imports:
            print(
                f"Skipping issue #{issue_id} ({title}): already imported as "
                f"GitHub issue #{existing_imports[issue_id]}."
            )
            skipped_count += 1
            continue

        labels = split_labels(row.get("Labels", ""))
        assignees: list[str] = []
        assignee_username = row.get("Assignee Username", "").strip()
        if assignee_username and assignee_username in assignee_map:
            assignees = [assignee_map[assignee_username]]

        payload: dict[str, Any] = {
            "title": title,
            "body": build_body(row, labels),
        }
        if labels:
            payload["labels"] = labels
        if assignees:
            payload["assignees"] = assignees

        if args.dry_run:
            print(f"Would create issue #{issue_id}: {title}")
            if row.get("State", "").strip().lower() == "closed":
                print("  Would close it after creation.")
            created_count += 1
            continue

        print(f"Creating issue #{issue_id}: {title}")
        issue = client.create_issue(args.repo, payload)
        created_count += 1

        if row.get("State", "").strip().lower() == "closed":
            client.update_issue(args.repo, issue["number"], {"state": "closed"})
            print(f"Closed GitHub issue #{issue['number']} to match GitLab state.")

        time.sleep(0.2)

    print(
        f"Finished. Created {created_count} issue(s); skipped {skipped_count} issue(s)."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())

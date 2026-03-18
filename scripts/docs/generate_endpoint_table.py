#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTROLLER_ROOTS = {
    "Router": ROOT / "router" / "src" / "main" / "java",
    "Edge": ROOT / "edge" / "src" / "main" / "java",
    "Origin": ROOT / "origin" / "src" / "main" / "java",
}

HTTP_STATUS_CODES = {
    "OK": 200,
    "CREATED": 201,
    "ACCEPTED": 202,
    "NO_CONTENT": 204,
    "PARTIAL_CONTENT": 206,
    "BAD_REQUEST": 400,
    "UNAUTHORIZED": 401,
    "FORBIDDEN": 403,
    "NOT_FOUND": 404,
    "CONFLICT": 409,
    "UNPROCESSABLE_ENTITY": 422,
    "TOO_MANY_REQUESTS": 429,
    "BAD_GATEWAY": 502,
    "SERVICE_UNAVAILABLE": 503,
    "INTERNAL_SERVER_ERROR": 500,
}

MAPPING_ANNOTATIONS = {
    "@GetMapping": "GET",
    "@PostMapping": "POST",
    "@PutMapping": "PUT",
    "@DeleteMapping": "DELETE",
    "@PatchMapping": "PATCH",
}


@dataclass
class Endpoint:
    service: str
    method: str
    path: str
    path_params: list[str]
    query_params: list[str]
    headers: list[str]
    request_body: str
    response_codes: str
    response_format: str
    description: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate a Markdown endpoint inventory from Spring controller annotations."
    )
    parser.add_argument(
        "--source-root",
        type=Path,
        default=ROOT,
        help="Repository root containing router/edge/origin modules.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    endpoints = collect_endpoints(args.source_root.resolve())
    print(render_markdown(endpoints))


def collect_endpoints(source_root: Path) -> list[Endpoint]:
    results: list[Endpoint] = []
    for service_name, root in {
        key: source_root / value.relative_to(ROOT) for key, value in CONTROLLER_ROOTS.items()
    }.items():
        for file_path in sorted(root.rglob("*Controller.java")):
            results.extend(parse_controller(service_name, file_path))
    return sorted(results, key=lambda item: (item.service, item.path, item.method))


def parse_controller(service: str, file_path: Path) -> list[Endpoint]:
    text = file_path.read_text(encoding="utf-8")
    if "@RestController" not in text:
        return []

    lines = text.splitlines()
    class_base_path = extract_class_base_path(lines)
    endpoints: list[Endpoint] = []
    index = 0
    while index < len(lines):
        line = lines[index].strip()
        if not is_mapping_annotation(line):
            index += 1
            continue

        annotation_lines = []
        start_index = index
        while index < len(lines) and lines[index].strip().startswith("@"):
            annotation_lines.append(lines[index].strip())
            index += 1

        signature_lines = []
        while index < len(lines):
            signature_lines.append(lines[index])
            if "{" in lines[index]:
                break
            index += 1

        signature = " ".join(part.strip() for part in signature_lines).strip()
        if " class " in f" {signature} " or "(" not in signature:
            index += 1
            continue

        method_body, end_index = extract_method_body(lines, index)
        index = end_index + 1

        mapping = parse_mapping(" ".join(annotation_lines))
        if mapping is None:
            continue

        method, method_path, produces = mapping
        javadoc = extract_javadoc(lines, start_index)
        path_params, query_params, headers, request_body = parse_parameters(signature, class_base_path + method_path)
        response_codes = detect_response_codes(method_body)
        response_format = infer_response_format(signature, produces, method_body)
        description = summarize_javadoc(javadoc) or extract_method_name(signature)

        endpoints.append(
            Endpoint(
                service=service,
                method=method,
                path=normalize_path(class_base_path, method_path),
                path_params=path_params,
                query_params=query_params,
                headers=headers,
                request_body=request_body,
                response_codes=response_codes,
                response_format=response_format,
                description=description,
            )
        )

    return endpoints


def extract_class_base_path(lines: list[str]) -> str:
    class_line = next((idx for idx, line in enumerate(lines) if re.search(r"\bclass\b", line)), len(lines))
    for idx in range(class_line):
        stripped = lines[idx].strip()
        if stripped.startswith("@RequestMapping"):
            path = extract_path_from_annotation(stripped)
            if path:
                return path
    return ""


def is_mapping_annotation(line: str) -> bool:
    return any(line.startswith(name) for name in MAPPING_ANNOTATIONS) or (
        line.startswith("@RequestMapping") and "RequestMethod." in line
    )


def extract_method_body(lines: list[str], start_index: int) -> tuple[str, int]:
    body_lines: list[str] = []
    brace_balance = 0
    seen_opening_brace = False
    index = start_index

    while index < len(lines):
        line = lines[index]
        body_lines.append(line)
        for char in line:
            if char == "{":
                brace_balance += 1
                seen_opening_brace = True
            elif char == "}":
                brace_balance -= 1
        if seen_opening_brace and brace_balance == 0:
            break
        index += 1

    return "\n".join(body_lines), index


def parse_mapping(annotation_blob: str) -> tuple[str, str, str | None] | None:
    for annotation_name, http_method in MAPPING_ANNOTATIONS.items():
        if annotation_name in annotation_blob:
            return http_method, extract_path_from_annotation(annotation_blob), extract_attr(annotation_blob, "produces")

    if "@RequestMapping" not in annotation_blob:
        return None

    method_match = re.search(r"RequestMethod\.([A-Z]+)", annotation_blob)
    if not method_match:
        return None

    http_method = method_match.group(1)
    return http_method, extract_path_from_annotation(annotation_blob), extract_attr(annotation_blob, "produces")


def extract_path_from_annotation(annotation_blob: str) -> str:
    for attribute in ("value", "path"):
        value = extract_attr(annotation_blob, attribute)
        if value is not None:
            return value
    direct_match = re.search(r"\(\s*\"([^\"]+)\"\s*\)", annotation_blob)
    if direct_match:
        return direct_match.group(1)
    return ""


def extract_attr(annotation_blob: str, attribute: str) -> str | None:
    match = re.search(rf"{attribute}\s*=\s*\"([^\"]+)\"", annotation_blob)
    return match.group(1) if match else None


def parse_parameters(signature: str, full_path: str) -> tuple[list[str], list[str], list[str], str]:
    compact = " ".join(signature.split())
    annotated_params = list(
        re.finditer(
            r"@(PathVariable|RequestParam|RequestHeader|RequestBody)(\([^)]*\))?\s+(.+?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?=,|\))",
            compact,
        )
    )

    path_var_types: dict[str, str] = {}
    query_params: list[str] = []
    headers: list[str] = []
    request_body = "–"

    for match in annotated_params:
        annotation_type, annotation_args, type_name, variable_name = match.groups()
        args = annotation_args or ""
        name = extract_explicit_name(args) or variable_name
        required = "required = false" not in args
        default_value = extract_attr(args, "defaultValue")
        if default_value is not None:
            required = False
        type_name = normalize_type_name(type_name)

        if annotation_type == "PathVariable":
            path_var_types[name] = type_name
        elif annotation_type == "RequestParam":
            query_params.append(format_param(name, type_name, required, default_value))
        elif annotation_type == "RequestHeader":
            headers.append(format_param(name, type_name, required, default_value))
        elif annotation_type == "RequestBody":
            request_body = type_name

    path_params = []
    for raw_name in re.findall(r"\{([^}]+)\}", full_path):
        clean_name = raw_name.removeprefix("*").split(":", 1)[0]
        type_name = path_var_types.get(clean_name, "String")
        path_params.append(f"{clean_name}:{type_name} (pflicht)")

    return path_params, query_params, headers, request_body


def extract_explicit_name(args: str) -> str | None:
    match = re.search(r'(?:value|name)\s*=\s*"([^"]+)"', args)
    if match:
        return match.group(1)
    constant_match = re.search(r"(?:value|name)\s*=\s*([A-Za-z0-9_$.]+)", args)
    if constant_match:
        return normalize_constant_name(constant_match.group(1))
    direct_match = re.search(r'\(\s*"([^"]+)"', args)
    return direct_match.group(1) if direct_match else None


def format_param(name: str, type_name: str, required: bool, default_value: str | None) -> str:
    parts = [f"{name}:{type_name}"]
    parts.append("pflicht" if required else "optional")
    if default_value is not None:
        parts.append(f"default={default_value}")
    return f"{parts[0]} ({', '.join(parts[1:])})"


def normalize_type_name(type_name: str) -> str:
    cleaned = " ".join(type_name.replace("final ", "").split())
    cleaned = re.sub(r"\s+", " ", cleaned)
    cleaned = cleaned.replace(" ?", "?")
    return cleaned


def detect_response_codes(method_body: str) -> str:
    codes: set[int] = set()
    dynamic_status = False

    for matcher, code in (
        (r"ResponseEntity\.ok\b", 200),
        (r"ResponseEntity\.badRequest\b", 400),
        (r"ResponseEntity\.notFound\b", 404),
        (r"ResponseEntity\.noContent\b", 204),
    ):
        if re.search(matcher, method_body):
            codes.add(code)

    for status_name in re.findall(r"ResponseEntity\.status\(\s*HttpStatus\.([A-Z_]+)", method_body):
        mapped = HTTP_STATUS_CODES.get(status_name)
        if mapped is not None:
            codes.add(mapped)
        else:
            dynamic_status = True

    for numeric_code in re.findall(r"ResponseEntity\.status\(\s*(\d{3})", method_body):
        codes.add(int(numeric_code))

    if re.search(r"ResponseEntity\.status\(\s*(?!HttpStatus\.|\d{3})", method_body):
        dynamic_status = True

    ordered_codes = [str(code) for code in sorted(codes)]
    if dynamic_status:
        ordered_codes.append("variabel")
    return ", ".join(ordered_codes) if ordered_codes else "–"


def infer_response_format(signature: str, produces: str | None, method_body: str) -> str:
    if produces:
        return produces

    match = re.search(r"ResponseEntity<(.+?)>", signature)
    response_type = normalize_type_name(match.group(1)) if match else ""
    if response_type == "Void":
        return "Kein Body"
    if response_type in {"byte[]", "ByteArrayResource"}:
        return "Binary"
    if response_type:
        if response_type == "?" and ".body(" not in method_body:
            return "Kein Body"
        return "JSON"
    return "–"


def extract_javadoc(lines: list[str], start_index: int) -> str:
    index = start_index - 1
    while index >= 0 and not lines[index].strip():
        index -= 1
    if index < 0 or "*/" not in lines[index]:
        return ""

    block: list[str] = []
    while index >= 0:
        block.append(lines[index])
        if "/**" in lines[index]:
            break
        index -= 1
    block.reverse()
    return "\n".join(block)


def summarize_javadoc(javadoc: str) -> str:
    if not javadoc:
        return ""
    lines = []
    for raw_line in javadoc.splitlines():
        line = raw_line.strip()
        line = re.sub(r"^/\*\* ?", "", line)
        line = re.sub(r"^\* ?", "", line)
        line = line.replace("*/", "").strip()
        if not line or line.startswith("@") or line.startswith("<p>") or line == "</p>":
            continue
        lines.append(line)

    return lines[0].strip() if lines else ""


def extract_method_name(signature: str) -> str:
    match = re.search(r"\b([A-Za-z_][A-Za-z0-9_]*)\s*\(", signature)
    return match.group(1) if match else "ohne Beschreibung"


def normalize_path(base_path: str, method_path: str) -> str:
    combined = "/".join(part.strip("/") for part in (base_path, method_path) if part)
    return "/" + combined if combined else "/"


def normalize_constant_name(raw_name: str) -> str:
    if raw_name == "HttpHeaders.RANGE":
        return "Range"
    tail = raw_name.split(".")[-1]
    return "-".join(part.capitalize() for part in tail.split("_"))


def render_markdown(endpoints: list[Endpoint]) -> str:
    if not endpoints:
        return "# API Endpoint Inventar\n\nKeine Endpunkte gefunden."

    lines = [
        "# API Endpoint Inventar",
        "",
        "_Automatisch aus Spring-Controller-Annotationen generiert._",
        "",
    ]

    current_service = None
    for endpoint in endpoints:
        if endpoint.service != current_service:
            if current_service is not None:
                lines.append("")
            current_service = endpoint.service
            lines.extend(
                [
                    f"## {endpoint.service}",
                    "",
                    "| Methode | Pfad | Path-Parameter | Query-Parameter | Header | Request-Body | Response-Codes | Response-Format | Beschreibung |",
                    "|---------|------|----------------|-----------------|--------|--------------|----------------|-----------------|--------------|",
                ]
            )

        lines.append(
            "| "
            + " | ".join(
                escape_cell(value)
                for value in (
                    endpoint.method,
                    endpoint.path,
                    join_or_dash(endpoint.path_params),
                    join_or_dash(endpoint.query_params),
                    join_or_dash(endpoint.headers),
                    endpoint.request_body,
                    endpoint.response_codes,
                    endpoint.response_format,
                    endpoint.description,
                )
            )
            + " |"
        )

    lines.extend(
        [
            "",
            "Hinweise:",
            "- `Response-Codes` werden heuristisch aus `ResponseEntity`-Aufrufen im Code erkannt.",
            "- `variabel` bedeutet: der endgültige Statuscode wird indirekt zur Laufzeit bestimmt.",
        ]
    )
    return "\n".join(lines)


def join_or_dash(values: list[str]) -> str:
    return "<br>".join(values) if values else "–"


def escape_cell(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


if __name__ == "__main__":
    main()

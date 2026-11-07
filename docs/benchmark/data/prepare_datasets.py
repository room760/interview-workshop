"""Freeze the existing evaluation file without changing any relevance labels."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
source = root / "AI-Meeting-main/admin/src/main/resources/rag/evaluation.json"
target = Path(__file__).resolve().parent
cases = json.loads(source.read_text(encoding="utf-8"))
if len(cases) != 60:
    raise SystemExit(f"Expected the inspected 60 cases, found {len(cases)}; review before updating snapshots")
if len({item["query"] for item in cases}) != len(cases):
    raise SystemExit("Duplicate query in source")
for name, subset in (("historical24.json", cases[:24]), ("existing_additions36.json", cases[24:])):
    (target / name).write_text(json.dumps(subset, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Frozen historical24=24 and existing_additions36=36; labels copied unchanged")

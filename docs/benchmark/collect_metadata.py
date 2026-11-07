"""Collect reproducibility metadata without reading or printing local credentials."""
import hashlib
import json
import os
import platform
import subprocess
import shutil
from datetime import datetime
from pathlib import Path

root = Path(__file__).resolve().parents[2]
benchmark = Path(__file__).resolve().parent

def command(*args):
    return subprocess.check_output(args, cwd=root, stderr=subprocess.STDOUT)

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

datasets = {
    name: sha256(benchmark / "data" / name)
    for name in ("historical24.json", "existing_additions36.json", "proposed_queries.json")
}
source_paths = [
    "AI-Meeting-main/admin/src/main/java/com/cqr/interviewworkshop/interview/application/jobmatch/rag/eval/RagEvaluationRunner.java",
    "AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/application/jobmatch/application/RagBm25BenchmarkTest.java",
    "AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/application/guard/singleflight/service/LocalSingleFlightBenchmarkTest.java",
    "AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/application/guard/singleflight/service/RedisFlightCoordinatorBenchmarkTest.java",
    "AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/flow/answer/InterviewAnswerParallelBenchmarkTest.java",
    "AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/application/runtime/InterviewSessionRuntimeRehydrateServiceTest.java",
    "eval-rag.ps1",
]
status = command("git", "status", "--porcelain").decode("utf-8", "replace").splitlines()
metadata = {
    "collectedAtLocal": datetime.now().astimezone().isoformat(),
    "gitHead": command("git", "rev-parse", "HEAD").decode().strip(),
    "workingTreeDirty": bool(status),
    "changedOrUntrackedPaths": len(status),
    "trackedDiffSha256": hashlib.sha256(command("git", "diff", "--binary")).hexdigest(),
    "operatingSystem": platform.platform(),
    "javaExecutable": str(Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe") if os.environ.get("JAVA_HOME") else shutil.which("java"),
    "javaVersion": subprocess.check_output(
        [str(Path(os.environ["JAVA_HOME"]) / "bin" / "java.exe") if os.environ.get("JAVA_HOME") else "java", "-version"],
        stderr=subprocess.STDOUT,
    ).decode("utf-8", "replace").splitlines()[0],
    "redisVersion": os.environ.get("BENCHMARK_REDIS_VERSION", "not_collected"),
    "redisEndpoint": "127.0.0.1:16379 (isolated local test process)",
    "dockerAvailable": False,
    "realModelCalls": 0,
    "mongoUsed": False,
    "pgvectorUsed": False,
    "embeddingUsed": False,
    "mockAgentSleepMs": 120,
    "knowledgeCorpusSha256": sha256(root / "AI-Meeting-main/admin/src/main/resources/rag/job-knowledge.json"),
    "datasetSha256": datasets,
    "benchmarkSourceSha256": {path: sha256(root / path) for path in source_paths},
}
output = benchmark / "raw" / "run-metadata.json"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(output)

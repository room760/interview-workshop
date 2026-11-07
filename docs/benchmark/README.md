# AI-Meeting 评测复现

**只想看四项结果：先看 [`latest_summary.md`](latest_summary.md)。**其余文件用于复核和排错。

**长会话恢复新增真实集成测试：**详见 [`recovery-integration-report.md`](recovery-integration-report.md)。它使用真实 Redis 7.2.16 与 MongoDB 7.0.45，包含 100 次固定数据重复采样；终态二次访问断言发现真实缺陷，因此当前集成套件有 1 项失败。现有 `benchmark-4-recovery.cmd` 已升级为该集成测试的一键入口。

## 双击运行（2026-11-07）

在仓库最外层双击下列四个文件；每次运行的日志与原始数据分别保存在 `tmp/benchmark-runs/` 下的独立时间戳目录，结束后窗口会停留以便查看结果。

| 入口 | 实际执行 | 边界 |
|---|---|---|
| `benchmark-1-rag.cmd` | 120 条 BM25 离线测试，再以同一候选集运行真实 BM25、向量、混合、规则重排 | 会调用配置中的讯飞 Embedding，并重启后端；120 条标签为 AI 候选，不能称人工标注 |
| `benchmark-2-singleflight.cmd` | 本地与真实 Redis Lua 并发测试，自动启停临时 Redis 7.2 容器 | 模型任务是固定延迟 Mock，未测真实账单和多 JVM |
| `benchmark-3-multiagent.cmd` | 100 轮生产并行分支 Mock 对照、异常与 30 秒超时测试 | 重复同一输入，不是 100 个不同真实 Agent 任务 |
| `benchmark-4-recovery.cmd` | 启动真实 Redis/Mongo，运行 10 项集成测试和原有 5 项 Mockito 测试 | 终态二次缓存访问有已知断言失败；脚本保留日志与原始结果，并以失败状态结束 |

四个入口依赖 Java 17 和 Maven；RAG、Single-flight、长会话恢复还需 Docker Desktop。RAG 使用 `candidate120.json` 的只读容器挂载，不改写原 `evaluation.json`；原来的 `eval-rag.cmd` 仍用 60 条旧查询。可以只检查环境而不运行测试：`powershell.exe -NoProfile -File .\run-benchmark.ps1 -Suite rag -CheckOnly`。RAG 的离线验证方式：将 `-CheckOnly` 换成 `-OfflineOnly`，它只运行 BM25，不调用讯飞。

以下旧实验说明保存 2026-11-06 的阶段性证据；2026-11-07 已完成真实四方案 RAG 和真实 Redis + MongoDB 恢复集成测试，最新数字以[一页汇总](latest_summary.md)和[恢复报告](recovery-integration-report.md)为准。旧实验使用 Java 17、Maven 3.8.9、JUnit 5；其中 Redis 实测使用独立的本机 Redis 6.2.6，地址 `127.0.0.1:16379`，当时未使用 Docker。未新增 Maven 依赖。

## 数据与边界

- `data/historical24.json`：从当前 `evaluation.json` 的前 24 条原样冻结，仅用于检查历史简历所用查询；标签来源未留人工标注记录。
- `data/existing_additions36.json`：同一文件后 36 条原样冻结，可能已参与开发，不作为严格独立测试集。
- `data/proposed_queries.json`：本次新增的 8 条查询，包含改写、缩写、多条件、长尾和 2 条无相关文档问题。由 AI 起草，所有标签标为“待人工确认”；结果仅作探索，不可称人工标注。
- `data/candidate120.json`：2026-11-07 增补的 120 条不同候选查询，96 条正例、24 条无相关文档，和原 60 条无重复。由 `data/create_candidate120.py` 固定生成；所有正例标签和场景分类待人工确认，不属于独立盲测集。
- 语料为 `AI-Meeting-main/admin/src/main/resources/rag/job-knowledge.json` 的 8 份文档，测试调用生产 `JobKnowledgeChunker` 和 `LuceneKeywordIndex`。索引建在仓库忽略的 `tmp/benchmark/`。

## 运行

从仓库最外层进入 `AI-Meeting-main`，确保 `JAVA_HOME` 指向 Java 17。PowerShell 示例：

```powershell
cd AI-Meeting-main
mvn -o -pl admin -am '-Dtest=RagBm25BenchmarkTest,LocalSingleFlightBenchmarkTest,InterviewSessionRuntimeRehydrateServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

生产并行分支的 Mock 对照与超时测试单独运行如下；100 轮耗时对照约需 1–2 分钟，真正的非关键 Agent 超时测试另需等待 30 秒：

```powershell
mvn -o -pl admin -am '-Dtest=InterviewAnswerParallelBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

运行 Redis 测试前，在本机另起 Redis 6.2 服务，绑定 `127.0.0.1:16379`，避免复用日常数据库。随后运行：

```powershell
mvn -o -pl admin -am '-Dtest=RedisFlightCoordinatorBenchmarkTest' '-Dbenchmark.redis=true' '-Dbenchmark.redis.port=16379' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

本次 37 个 JUnit 的聚焦测试命令见 `raw/commands.txt`，完整输出见 `raw/maven-focused.log`。`-Dbenchmark.redis=true` 未设置时，Redis 测试明确跳过。生成结果位于 `raw/`，分别为逐查询 JSONL、摘要 JSON、并发逐轮 JSONL、接管与超时结果 JSON、运行元数据和 Maven 日志。从最外层仓库目录、设置与测试相同的 `JAVA_HOME` 后运行 `python docs/benchmark/collect_metadata.py`，可更新环境与语料哈希。数据拆分脚本为 `data/prepare_datasets.py`，它要求当前源集恰为 60 条，防止源数据悄悄变化。

2026-11-07 的增补运行分别见 `raw/maven-bm25-120.log` 与 `raw/maven-multi-agent-100.log`。原始数据为 `raw/bm25-queries.jsonl`、`raw/bm25-summary.json` 和 `raw/multi-agent.jsonl`；前两者包括 120 条候选查询，后一文件为 100 轮固定 Mock 输入的重复观测。恢复的后续真实集成结果与复现命令见 [集成报告](recovery-integration-report.md)。

## 真实向量评测

`RagEvaluationRunner` 现支持 BM25、VECTOR、HYBRID、HYBRID_RERANK 四组，逐查询输出 `REAL RAG observation`，`eval-rag.ps1` 根据实际查询数校验，不再固定为 24。该脚本会调用真实 Embedding 接口并重启服务；只有确认免费额度或取得明确授权后才能运行。2026-11-06 的基准测试**没有运行**真实 Embedding，相关模式保持“未测”，历史数字不能视为本次复现结果。

2026-11-07 修复了 `eval-rag.cmd` 使用 Windows PowerShell 5.1 时把 60 条查询计成 1 条的问题。修复后的脚本会先在 `tmp/rag-eval-attempt-*.log` 保存本次后端日志，即使校验失败也可定位原因。随后通过 `benchmark-1-rag.cmd` 实际完成 120 条查询的四方案评测；[逐查询记录](raw/rag-observations-20261107-102531.jsonl)和[最新汇总](latest_summary.md)已保存。

## 指标

检索以文档 ID 的二元相关性计算：Recall@5 为前五名命中不同相关文档数除以相关文档总数；MRR@5 为首个相关文档排名的倒数；NDCG@5 用 `1/log2(rank+1)` 折扣并除以理想 DCG。无相关文档查询不进入这些三个均值，另计返回非空的比例。平均耗时和 P95 按逐次请求纳秒统计，P95 用最近秩。BM25 耗时只覆盖检索，不含索引构建、Embedding、网络或回答生成；没有显式预热。Single-flight 延迟覆盖测试请求到结果返回，Mock 模型固定休眠 120ms；不代表真实模型耗时或 Token 成本。

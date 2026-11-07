# 真实结果与局限

**2026-11-07 四个双击脚本的汇总见 [`latest_summary.md`](latest_summary.md)。**长会话恢复的真实集成结果与失败见 [`recovery-integration-report.md`](recovery-integration-report.md)。下文保留此前的分阶段评测记录；旧段落中的“未测”只表示当时尚未运行。

原始运行日期：2026-11-06；2026-11-07 增补 120 条 RAG 候选查询与 100 轮 Multi-Agent Mock 对照。原始运行元数据、Git HEAD、工作区脏状态和原三组数据 SHA-256 见 [`raw/run-metadata.json`](raw/run-metadata.json)。本次 Git HEAD 为 `6c666b88c0c7aa31e742a89452af838d235db411`，但运行时已有用户未提交源码改动，另有本次测试改动；**只检出该提交不能单独复现以下结果**。语料 8 文档。以下本地测试没有调用真实 AI 模型。

| 方向 | 三位数样本现状 | 可报告的范围 | 尚缺 |
|---|---|---|---|
| RAG | 新增 120 条不同候选查询，BM25 各跑 3 轮 | 探索性 BM25 与无答案误报 | 人工核标签；同一数据集上的向量/混合/重排实测 |
| Single-flight | 50 并发 × 3 轮 = 150 个同键请求 | Mock 执行次数、真实 Redis Lua 协调、P95 | 真正多 JVM 与真实模型成本 |
| Multi-Agent | 同一 Mock 输入重复 100 轮 | 生产并行分支调度耗时 | 100 个不同任务的关键 Agent 成功率、真实模型耗时 |
| 长会话恢复 | 5 个 Mockito 分支测试 | 恢复分支与置信度断言 | 三位数真实 Redis/Mongo 会话的成功率和耗时 |

## RAG

实际执行的是生产 Lucene BM25 与生产 Chunker，Top-5，每组 3 轮。下表延迟为全部逐查询观测的平均和 P95，单位 ms。逐查询原始数据：[`raw/bm25-queries.jsonl`](raw/bm25-queries.jsonl)。

| 数据组 | 查询数 | Recall@5 | MRR@5 | NDCG@5 | 平均 | P95 | 标签状态 |
|---|---:|---:|---:|---:|---:|---:|---|
| 历史 24 条 | 24 | 1.000 | 1.000 | 1.000 | 3.541 | 5.178 | 原标签，人工来源未证实 |
| 现有追加 36 条 | 36 | 1.000 | 0.947 | 0.949 | 2.517 | 3.303 | 原标签，开发污染可能存在 |
| 本次提议 8 条 | 8（6 条正例） | 1.000 | 1.000 | 0.991 | 2.287 | 4.620 | AI 起草，待人工审核 |
| 本次候选 120 条 | 120（96 条正例） | 0.990 | 0.916 | 0.934 | 1.926 | 2.863 | AI 起草，待人工审核 |

本次 2 条无相关文档查询中，BM25 有 1 条返回了非空列表，误报率 50%（3 轮共 3/6）；样本极少，不可外推。原 24 条 BM25 已达到满分，因此该组数据不能证明混合检索比 BM25 更好。旧简历的向量 MRR 0.972/NDCG 0.979、混合 MRR/NDCG 1.000 来源于 2026-11-03 保存的 3 份历史日志（[`204233`](raw/rag-eval-20261103-204233.txt)、[`204525`](raw/rag-eval-20261103-204525.txt)、[`204927`](raw/rag-eval-20261103-204927.txt)）；它们均记录 24 条查询，但当时的源码、数据与运行环境未冻结，**本次未复现，也不能与本次 BM25 构成受控对照**。VECTOR、HYBRID、规则 Rerank 的本次 Recall、MRR、NDCG、延迟均为**未测**。规则 Rerank 是词项规则，不是模型重排。

新增 120 条候选查询与原 60 条无重复；其中 24 条无相关文档题里，BM25 返回非空结果 15 条，误报率 **62.5%**。96 条正例有 1 条 Top-5 未命中。数据由 AI 根据已知语料起草、标签未人工确认，**不是独立盲测集，不能因为分数较低就直接写入简历**。增补测试日志见 [`raw/maven-bm25-120.log`](raw/maven-bm25-120.log)，原始 2026-11-06 的 BM25 结果另存于 `raw/bm25-20261106-*.json*`。

增补数据集 `candidate120.json` 的 SHA-256 为 `027c08e82189e0f02a9af129a9453e55da47ce2cf0ad0960ab4ed78472a47bd8`；BM25 与 Multi-Agent 增补测试源码 SHA-256 分别为 `cb21452534f5d9a3f475477db8c19051d0c734cc2a05a737e02827a580ea4908`、`50a202e362130814cc32a324ba4050a19f6dbdfe9fcd06bfad4345d2e890f5d4`。工作区仍未提交，因此这些哈希比 Git HEAD 更能定位本次执行代码。

## Single-flight

本地服务和真实 Redis 6.2 Lua 协调各做并发 1、5、10、20、50，每档 3 轮。Mock 模型任务固定休眠 120ms，不调用真实服务。下表的 P95 是三轮各自 P95 的中位数；原始逐轮结果见 [`raw/local-singleflight.jsonl`](raw/local-singleflight.jsonl) 和 [`raw/redis-coordinator.jsonl`](raw/redis-coordinator.jsonl)。

| 并发 | 本地直接调用模型次数/3轮 | 本地 Single-flight 模型次数/3轮 | Redis Lua Mock 模型次数/3轮 | Redis 成功请求/3轮 | Redis P95 中位数 ms |
|---:|---:|---:|---:|---:|---:|
| 1 | 3 | 3 | 3 | 3/3 | 134.71 |
| 5 | 15 | 3 | 3 | 15/15 | 151.36 |
| 10 | 30 | 3 | 3 | 30/30 | 140.42 |
| 20 | 60 | 3 | 3 | 60/60 | 136.38 |
| 50 | 150 | 3 | 3 | 150/150 | 140.22 |

50 并发时，相同键的模型任务次数从直接调用的每轮 50 次降为每轮 1 次，重复调用减少 `1 - 1/50 = 98%`；这是**计数型 Mock**的调用次数节省，不是实际账单节省。本地服务通过 Micrometer miss/hit 计数；Redis 测试调用真实 `FlightCoordinatorRepository` Lua、状态与结果读写。Redis 测试使用单进程里的不同逻辑 node ID 和测试栅栏，不是两个真正的 JVM，也没有覆盖完整 `DistributedInterviewAiSingleFlightService` 的 Stream 等待与心跳调度。50 并发三轮共 3 Owner、147 Follower，成功复用 147 次，失败 0 次。

另用真实 Redis 模拟 Owner 停止心跳 100ms 后接管：新 Token 大于旧 Token；旧 Owner 的心跳、结果写入和完成操作被拒绝，新 Owner 可写入 `fresh`，见 [`raw/redis-takeover.json`](raw/redis-takeover.json)。这是租约超时模拟，未测真实模型超时、长任务心跳续约和真正多实例故障。

## Multi-Agent 与恢复

实际调用生产 `InterviewAnswerPipeline.stepEvaluateAndScore` 的并行分支，以两个固定休眠 120ms 的 Mock Agent 隔离模型。另以相同两个休眠任务顺序执行作**合成串行基线**，增补为 100 轮重复执行；原始数据见 [`raw/multi-agent.jsonl`](raw/multi-agent.jsonl)，一键运行日志见 `tmp/benchmark-runs/multiagent-20261107-101758/maven-multiagent.log`。串行 P50 **248.38ms**，生产并行分支 P50 **125.05ms**、P95 **126.80ms**，正常 Mock 路径 100/100 成功，并发执行计数峰值为 2。100 轮复用同一固定输入，因此是性能重复观测，**不是 100 个不同 Agent 场景，也不是生产关键 Agent 可靠性成功率**。岗位匹配异常触发降级、评分异常传播的断言通过。真实 `orTimeout(30s)` 测试中，Mock 岗位匹配不返回，主评分成功，但请求等待 **30014.19ms** 后才降级，见 [`raw/multi-agent-timeout.json`](raw/multi-agent-timeout.json)。**LiteFlow Fail-Open、真实模型端到端耗时未测**。

恢复链路的 Mockito 测试覆盖无持久来源、完整 Flow 快照、Flow 缺失、恢复锁争抢失败，以及题目 Key 已回填后 Flow 写入抛错；5 个断言测试通过。它们验证分支与 `READ_ONLY` / `EXACT` 分类，以及部分回填失败不能报成成功，**没有**通过真实 MongoDB + Redis 验证完整 Key 回填、多实例同时恢复、部分 Key 残留后的重试或终态恢复。

## 运行统计与失败记录

最终合并运行的聚焦 JUnit：**37 通过、0 断言失败、0 错误、0 跳过**，命令和完整输出见 [`raw/maven-focused.log`](raw/maven-focused.log)。**未执行 4 组实验**：真实 Embedding/PgVector 四模式对比、完整分布式服务多实例、真实模型 Multi-Agent、MongoDB/Redis 联合恢复。其中真实 RAG 的 VECTOR、HYBRID、HYBRID_RERANK 三组指标均为未测。首次 BM25 尝试因系统临时目录不允许 Lucene 重命名而报错；改为仓库 `tmp/benchmark` 后成功。首次 Redis JUnit 在受限环境无法创建 loopback 连接；使用隔离的本机 Redis 并在允许本地连接的环境重跑后成功。失败尝试未计入通过数，原因保留在此。

2026-11-07 增补的 100 轮 Multi-Agent 与 120 条 BM25 分别单独运行，**各 1 个 JUnit 通过，0 失败、0 跳过**；未再运行全部 37 项合并套件。长会话恢复仍只有 5 个 Mock 分支测试，没有三位数独立会话的真实 Redis/Mongo 恢复成功率和耗时，相关值保持**未测**。

2026-11-07 用户执行真实评测时，旧版 `eval-rag.ps1` 在 Windows PowerShell 5.1 下将 60 条查询误计为 1 条，因而校验失败且未保存本次指标。已修复计数与失败日志留存，并用合成日志验证脚本；**尚未重跑真实评测，不能将此次执行视为四模式通过**。

四个双击入口新增后，实际验证了 RAG **离线模式 2/2**、Single-flight **3/3**、Multi-Agent **3/3**、恢复分支 **5/5** JUnit，均 0 失败、0 跳过；各次日志和原始文件保存在 `tmp/benchmark-runs/` 的独立目录。RAG 的正式入口会使用 120 条候选查询并调用讯飞 Embedding，**本次只运行了 `-OfflineOnly`，真实四模式结果仍未测**。恢复入口仍只返回 Mock 分支状态，不生成虚假的真实恢复率。

## 后续补测：真实 Redis + MongoDB 恢复（2026-11-07）

以上段落保留历史运行口径。随后新增了真实 Redis 7.2.16 与 MongoDB 7.0.45 集成测试。最新完整运行于 2026-11-07 11:35 开始：100 次固定数据重复采样的恢复调用 P50/P95 为 **23.72/39.43ms**，预期可恢复观察 **106/106** 回填完整；10 线程同 session 重复初始化 0 次。集成测试 **9 通过、1 失败**，失败揭示终态会话在缓存命中后错误变成可写。原有 Mockito 单元测试仍 **5/5 通过**。初始状态、命令、局限与原始记录统一见 [`recovery-integration-report.md`](recovery-integration-report.md)。

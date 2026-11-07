# 长会话恢复：真实 Redis + MongoDB 集成测试

运行日期：2026-11-07 11:35–11:36（Asia/Shanghai）。Git HEAD：`6c666b88c0c7aa31e742a89452af838d235db411`；运行时工作区有未提交改动，本次上传将测试代码、报告和原始日志一起保存。该 HEAD 只记录运行当时的基线提交，不能单独还原评测源码。

## 环境、执行方法与数据

- Windows 11；Java 17.0.16；Maven 3.8.9；Spring Boot 3.4.4；Mongo Java Driver 5.2.1；Redisson 运行日志显示 3.21.0。
- Docker Compose 中的真实 Redis **7.2.16**（`127.0.0.1:6379`）与 MongoDB **7.0.45**（`127.0.0.1:27017`）；运行时均为 healthy。版本由容器内 `redis-server --version`、`mongod --version` 实测。
- Mongo 使用独立数据库 `interview_workshop_recovery_benchmark`；Redis 与日常服务共用实例，但只读写 `benchmark-recovery-*` 会话对应的 Key。每次测试前后删除自己的 Mongo 文档与 Redis Key，不执行 `FLUSHDB`。
- 生产 `InterviewSessionRuntimeRehydrateService`、`InterviewSessionRuntimeSnapshotService`、`InterviewQuestionCacheServiceImpl`、Redisson 锁服务和三个真实 Mongo Repository 被直接调用。MySQL 回退依赖使用空返回 Mockito 替身；**MySQL 回退链路没有在此集成测试中验证**。未调用付费模型，未新增依赖，未修改生产恢复逻辑。
- 固定题目为 Q1、Q2；当前题号 2；已答轮次得分 80；快照置信度通常为 EXACT。100 次重复样本采用确定性 session ID，四种可恢复状态各 25 次。数据由测试夹具写入真实 Mongo/Redis，不是用户线上会话。

从仓库根目录执行（先确保 Docker Compose 的 mongo、redis 已启动）：

```powershell
cd D:\AI-Meeting-main\AI-Meeting-main
$env:JAVA_HOME='E:\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& 'E:\Java\maven\apache-maven-3.8.9\bin\mvn.cmd' -o -pl admin -am '-Dtest=InterviewSessionRuntimeIntegrationTest,InterviewSessionRuntimeRehydrateServiceTest' '-Dbenchmark.recovery.integration=true' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

测试入口为 `AI-Meeting-main/admin/src/test/java/com/cqr/interviewworkshop/interview/application/runtime/InterviewSessionRuntimeIntegrationTest.java`。命令目前**预期以退出码 1 结束**，因为终态缓存二次访问的安全断言真实失败。去掉 `-Dbenchmark.recovery.integration=true` 会跳过真实集成测试，不能把那种运行计入实测。

也可从仓库最外层双击现有 `benchmark-4-recovery.cmd`。它会启动/检查 Docker 中的 Redis 与 MongoDB，运行上述两类测试，并将本次 JSONL、Maven 日志与摘要保存到 `tmp/benchmark-runs/recovery-时间戳/`；已知断言失败会显示为失败状态。每次一键运行写入独立目录，不覆盖本报告链接的历史原始记录。

本报告采用 `benchmark-4-recovery.cmd` 于 11:35 开始的完整运行：真实集成 10 项中 1 项失败、Mockito 5 项全通过。不同运行的耗时不同，不能混写成同一次实验。本报告的量化数字与下方公开的逐次记录、日志对应同一轮运行。

## 场景与实际结果

| 场景 | 测前状态、执行与检查 | 结果 |
| --- | --- | --- |
| 1. Redis 运行态全丢 | Redis 会话 Key 全无；Mongo 冷快照含题目，热快照含 Flow、分数、最近 Turns。调用 `ensureRuntime(HOT_RUNTIME)`，核对题目/Flow/Turns/分数 Key 和当前题号。 | EXACT，可写，回填完整。 |
| 2. Snapshot 部分缺失 | 热快照 `recentTurns` 为空、归档水位为 1；真实 Mongo TurnArchive 有 seq=1。调用恢复，核对 Turns 列表。 | EXACT，从 TurnArchive 回填 1 轮。仅证明最近轮次缺失的补偿。 |
| 3. Redis Flow 丢失 | Redis 题目与 Turns 留存，Flow 缺失；Mongo 热快照完整。调用恢复并读取 Redis Flow。 | EXACT，当前题号仍为 2。 |
| 4. Redis Turns 丢失 | Redis 题目与 Flow 留存，Turns 缺失；Mongo Archive 有 1 轮，热快照归档水位为 1。 | EXACT，Turns 回填 1 轮。 |
| 5. 同 session 并发 | 10 个线程由闸门同时调用恢复；真实 Redisson 锁，计数 `cacheRebuilt`，检查回填。 | 10 次请求中 1 次初始化、9 次复用；重复初始化 0 次，Key 完整。 |
| 6. 锁争抢失败 | 另一线程持有真实 Redis 分布式锁；调用恢复，检查置信度和 Key。 | READ_ONLY、不可写、未回填；**耗时 80,400.98 ms**。锁等待耗时异常偏高，原因未定位。 |
| 7. 部分回填后异常 | 用只在测试中生效的 Redis 客户端故障点，在题目 Key 写入后、Flow 写入前抛异常；Redis 服务器和前序写入均为真实。随后用正常客户端重试。 | 首次 READ_ONLY、题目 Key 残留；重试 EXACT、完整回填。此故障是客户端注入，不是 Redis 宕机。 |
| 8. 无持久来源 | Redis、Mongo 均无此 session；隔离测试中 MySQL 回退为空。 | READ_ONLY、不可写、无回填。不能外推到 MySQL 有数据时。 |
| 9. 终态快照 | Mongo 完整热快照的置信度为 TERMINAL。先恢复，再以同 session 第二次调用。 | 首次 TERMINAL、不可写；**第二次从 Redis 命中时变为 EXACT、可写，测试失败**。 |

## 指标与口径

- **场景数**：9 类测试场景，另 1 个重复采样测试；真实集成 JUnit 共 10 项。
- **恢复成功**：6 类预期可恢复情形（场景 1–5 与场景 7 的重试）均完成。将 100 次重复采样计入后，预期可恢复观察 **106 次，成功 106 次，实际恢复失败 0 次**。成功要求返回可写，并核实 Redis 题目、Flow、Turns、分数及当前题号；成功率分母仅为预期可恢复观察，故为 **106/106**。此数字只代表固定夹具，不是生产故障成功率。
- **预期不可恢复或暂不可写**：4 类（锁被占、异常首次调用、无数据、终态）。终态二次访问是意外放行，单列为 1 项断言失败，不混入恢复成功率分母。
- **Redis 缓存回填成功率**：预期可恢复观察中 **106/106**。终态首次也回填了 Key，但不计入可恢复分母；仅检查 Key 存在还不足以判定状态可写。
- **恢复调用延迟**：只计 `ensureRuntime` 方法耗时，排除 Mongo/Redis 夹具准备、断言和清理。100 个固定单线程重复样本（四类各 25，nearest-rank 分位数）**P50 = 23.72 ms，P95 = 39.43 ms**；最小 17.79 ms、最大 135.57 ms。若连同 6 个首次场景观察统计，106 次的 P50 = 23.75 ms、P95 = 52.67 ms。并发场景记录的是 10 个请求全部返回的墙钟耗时，不能当作单请求延迟；锁失败的 80.40 秒不计入成功恢复延迟。
- **并发重复初始化**：10 线程、同一 JVM、真实 Redis 锁下，`cacheRebuilt=true` 1 次，额外初始化 0 次。没有验证两个独立应用进程。
- **异常结果**：客户端故障首次 READ_ONLY、后续重试 EXACT；锁争抢 READ_ONLY；无数据 READ_ONLY；终态第二次访问错误地 EXACT/可写。
- **JUnit 结果**：真实 Redis+Mongo 集成测试 **10 项，9 通过、1 失败、0 跳过**；原有 Mockito 单元测试 **5 项全通过**；合计 **15 项，14 通过、1 失败、0 错误、0 跳过**。失败不是基础设施缺失，而是终态二次访问断言。第一次配置烟测与首次通过运行的日志也保留，最终指标只取最后一次完整运行。

原始逐次记录：[recovery-integration.jsonl](raw/recovery-integration.jsonl)（111 行，包括预期不可恢复及终态二次访问）；同轮 [Maven 原始日志](raw/recovery-integration-113500-log.txt)、[机器摘要](raw/recovery-summary-113500.json)和[运行信息](raw/recovery-run-info-113500.json)。此前的烟测和验证日志保留在本机，不计入本轮指标。

## 发现的问题与边界

1. **终态写保护在缓存命中后失效。** `ensureRuntime` 的“Redis 已就绪”分支直接返回 EXACT，没有沿用 Mongo 快照的 TERMINAL 置信度；第一次恢复回填缓存后，第二次调用 `canWrite()` 为 true。保留失败断言，未为让测试通过而调整业务代码。上线写路径是否还受其他状态检查保护，需单独审计。
2. **锁争抢耗时异常。** 多次运行约 80.4 秒才返回 READ_ONLY，本轮为 80.40 秒，显著长于源码表面上的 4×80 ms 轮询与 80 ms 再尝试。该场景仅有少量观察，不能归纳 P95；需进一步检查 Redisson `tryLock` 与租约配置。
3. 这些是固定合成数据、同机 Docker 及 Mongo 快照路径；未测 MySQL 数据回退、真实 Redis 宕机/网络分区、跨进程并发、快照与归档相互矛盾、长期压力与生产级恢复率。部分回填异常来自测试客户端故障点，不能称作 Redis 服务故障。
4. 对于热快照缺最近 Turns 但 Archive 存在，当前链路可恢复；本次没有证明“冷快照题目缺失但 MySQL 可补齐”。不能把场景 2 扩大表述为所有缺失字段均可恢复。

## 简历可用表述

“基于真实 Redis 7.2 与 MongoDB 7.0 为长会话运行态设计 9 类集成场景，并对 4 类恢复路径各重复 25 次；固定夹具下 106 次预期可恢复观察均完成 Redis 回填，单次恢复调用 P50/P95 为 23.72/39.43 ms。10 线程同会话并发仅 1 次缓存初始化；测试发现终态会话在缓存命中后错误恢复可写，以及一次锁争抢约 80 秒的延迟异常。”

面试时必须同时说明：这是本机固定夹具的集成结果、MySQL 回退未测、无跨进程验证，且终态写保护测试当前失败。不能简化为“线上恢复成功率 100%”或“全部测试通过”。

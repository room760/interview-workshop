# 面试工坊（Interview Workshop）

面试工坊是一个 Java 17、Spring Boot 3 后端项目，提供 AI 对话、智能体会话、模拟面试、答题评估和报告。语音相关服务端接口仍处于实验阶段，演示页面没有语音入口。

## 项目标识

- Maven：`com.cqr:interview-workshop`，后端模块为 `com.cqr:interview-workshop-admin`
- Java 根包：`com.cqr.interviewworkshop`
- HTTP 与 WebSocket API 前缀：`/api/interview-workshop/v1`
- 应用配置前缀：`interview-workshop-agent`

## 本地构建

使用 JDK 17，在项目根目录运行：

```powershell
.\mvnw.cmd -B -ntp clean verify
```

从完整项目启动时，使用上级目录的 `start.cmd` 或 `start.ps1`。本目录的 `docker-compose.yml` 定义后端和依赖服务；本机凭据集中在上级目录的 [`config/` 配置指南](../config/README.md)所述文件中，公开初始化脚本只保留非私密数据。

## 岗位知识检索评测

`admin/src/main/resources/rag/evaluation.json` 包含 60 条按知识文档内容标注的查询，覆盖 8 个知识文档，包括故障描述、口语化问法和跨主题问题。每条查询标注相关文档 ID，评测时按文档去重，计算 Top-5 Recall、MRR 和 NDCG。数据集也有单元测试检查重复查询、无效文档 ID 和主题覆盖。

真实检索评测需要先配置 Embedding 模型和 PgVector，再用 `rag-bootstrap,rag-eval` Profile 启动后端。评测会分别运行 BM25、纯向量、混合检索、混合检索加规则重排四种模式。历史 24 条查询得到的分数不能当作扩充后 60 条查询的结果；最新 120 条候选查询的实测结果和标签边界见[评测汇总](../docs/benchmark/latest_summary.md)。

## 代码位置

- `admin/src/main/java/com/cqr/interviewworkshop/`：应用源码
- `admin/src/main/resources/`：配置、SQL、Mapper 和工作流资源
- `admin/src/test/java/com/cqr/interviewworkshop/`：测试

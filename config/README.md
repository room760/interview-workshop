# 配置填写指南

完整项目从最外层的 `start.cmd` 启动。需要填写的私有值集中在本目录；改完后重新运行 `start.cmd`。

| 文件 | 用途 | 是否上传 GitHub |
| --- | --- | --- |
| `.env` | Docker、数据库、Spring AI、搜索工具的环境变量 | 否 |
| `ai-credentials.sql` | MySQL 中模型和智能体的密钥、工作流 ID；每次启动自动导入 | 否 |
| `.env.example` | 新电脑自动生成 `.env` 的无密钥模板 | 是 |
| `ai-credentials.example.sql` | 新电脑手动复制并填写的无密钥 SQL 模板 | 是 |
| `private-archive/` | 本机迁移前的旧副本，仅供备份，不会自动导入 | 否 |

本机已有的 `ai-credentials.sql` 请保留，不要用模板覆盖。真实 RAG 调用需要单独配置讯飞 Embedding 服务。不要把真实密钥粘贴到 `.env.example`、`ai-credentials.example.sql` 或 README。

## 第一次在新电脑配置

1. 运行 `start.cmd`。脚本会从 `.env.example` 创建 `.env`，并启动网页及后端。
2. 需要真实 AI 调用时，先填写 `.env` 中用到的服务商凭据。将 `ai-credentials.example.sql` **复制**为 `ai-credentials.sql`，填写 SQL 中的 `REPLACE_ME`。已有私有 SQL 的电脑不要用模板覆盖它。
3. 保存后再次运行 `start.cmd`。数据库凭据会被导入；网页地址为 `http://localhost:8080/`。

只有展示网页和非 AI 接口时，不需要购买模型服务或填写 API Key。Single-flight、会话快照、分布式锁和规则链使用项目自带的 Redis、MongoDB、MySQL，不需要额外密钥。

## 每组值从哪里来

| 要演示的能力 | 填写位置 | 获取方式 |
| --- | --- | --- |
| AI 对话中的星火模型 | `ai-credentials.sql` 的 `@spark_key`、`@spark_secret` | 在[讯飞开放平台](https://www.xfyun.cn/doc/spark/Web.html)创建应用并取得该模型接口的 APIKey、APISecret，确认应用有对应模型授权。 |
| DeepSeek 模型 | `ai-credentials.sql` 的 `@deepseek_key` | 在 [DeepSeek 开放平台](https://api-docs.deepseek.com/)申请 API Key；现有模型名称还要按控制台当前支持的名称核对。 |
| 面试题、答案评分、动态追问等工作流 | `ai-credentials.sql` 的 `@agent_key`、`@agent_secret`、各 `@agent_flow_*` | 在[讯飞星辰智能体平台](https://www.xfyun.cn/doc/spark/Agent04-API%E6%8E%A5%E5%85%A5.html)创建、调试并发布对应工作流，绑定应用后获取 APIKey、API Secret 和各自的 API Flowid。Flowid 必须对应实际发布的工作流，不能随意编一个。 |
| 岗位知识向量检索、RAG 实测 | `.env` 的 `XUNFEI_EMBEDDING_API_KEY`、`XUNFEI_EMBEDDING_MODEL_ID`、`PGVECTOR_DIMENSIONS` | 在[讯飞星辰 MaaS Embedding 服务](https://www.xfyun.cn/doc/spark/Embedding%26Rerank%E6%9C%8D%E5%8A%A1_HTTP%E5%8D%8F%E8%AE%AE.html)的服务管控页获取此服务的 API Key、模型 ID 和实际向量维度。星辰工作流的 Flowid 不能替代向量模型 ID，工作流 Key 也不保证拥有 Embedding 服务权限。 |
| 岗位网页搜索工具 | `.env` 的 `XUNFEI_WANSOU_API_PASSWORD` | 使用 `job_posting_search` 时，先在[讯飞万搜](https://www.xfyun.cn/doc/spark/Search_API/search_API.html)开通权限，再从控制台获取该服务的 APIPassword。星辰 MaaS 聊天模型 API Key 不能直接视为万搜凭据；岗位知识库 `job_knowledge_search` 不依赖万搜。 |

`XUNFEI_EMBEDDING_BASE_URL` 填不带版本号的主机地址，默认 `https://maas-api.cn-huabei-1.xf-yun.com`；`XUNFEI_EMBEDDING_PATH` 默认 `/v2/embeddings`。如果服务管控页显示的是旧版 `/v1`，只把 PATH 改为 `/v1/embeddings`。项目使用 Spring AI 的兼容协议适配器访问讯飞服务，不会向 OpenAI 发送请求。

填写讯飞向量服务时，在讯飞星辰 MaaS 的服务管控或在线推理页面找到 **Embedding 模型** 的调用信息，记录 API Key、模型 ID、API Base 和输出维度。将 Key 和模型 ID 填入 `.env` 的 `XUNFEI_EMBEDDING_API_KEY`、`XUNFEI_EMBEDDING_MODEL_ID`；API Base 中的主机填入 `XUNFEI_EMBEDDING_BASE_URL`，版本号填入 `XUNFEI_EMBEDDING_PATH`；把实际输出维度填入 `PGVECTOR_DIMENSIONS`。这四项属于向量服务，不是已有星辰工作流的 `api_flow_id`。

`XUNFEI_APP_ID`、`XUNFEI_API_KEY`、`XUNFEI_API_SECRET` 是 `.env` 里的其他讯飞应用配置，按实际调用的接口在[讯飞开放平台控制台](https://www.xfyun.cn/doc/spark/Web.html)获取。语音入口尚未开放，`XUNFEI_RTA_API_KEY` 可以留空。`REQUIRE_XUNFEI_SECRETS=false` 允许不配置讯飞时仍启动。

`MYSQL_PASSWORD` 等数据库值是本地 Docker 容器的连接配置，不是云服务密钥。已有数据卷时不要只修改 `.env` 中的数据库密码：容器内原有密码不会随之改变。镜像地址 `*_IMAGE` 是下载源，下载正常就不用改。

前端目录自带的 `.env.development`、`.env.production` 仅指定开发代理和 `/api` 路径，没有需要申请的密钥；使用最外层 `start.cmd` 时无需修改它们。

## 做一次真实 RAG 评测

项目自带 8 份知识文档和 24 条标注查询。先在 `.env` 填写有效的讯飞 Embedding API Key、模型 ID、接口版本，并确认 `PGVECTOR_DIMENSIONS` 等于模型实际输出维度。模板中的 `1536` 只是默认值。

启动 Docker Desktop 后，在项目最外层**双击 `eval-rag.cmd`**；也可以在 PowerShell 运行 `./eval-rag.ps1`。脚本临时启用 `prod,rag-bootstrap,rag-eval`，导入知识，分别评测 VECTOR、HYBRID、HYBRID_RERANK，并将真实指标保存到 `tmp/rag-eval-时间戳.txt`。脚本结束后自动恢复日常 profile，不修改 `.env`。首次运行需要构建镜像，真实 Embedding 调用可能产生费用。

若评测日志包含向量检索、BM25 或重排降级，脚本会将本次评测判为失败，不保存指标。失败时可查看后端日志排查。拿到结果后，记录 Recall@K、MRR@K、NDCG@K、P50/P95；不要把未跑出的指标写成实测结果。此评测只覆盖小规模岗位知识检索，不代表完整 Agent 的回答质量。

`RAG_LUCENE_PATH` 将关键词索引保存在后端 Docker 数据卷。切换 Embedding 模型或维度时，把 `PGVECTOR_DIMENSIONS` 设为模型实际输出维度，并同时为 `PGVECTOR_TABLE_NAME` 和 `RAG_LUCENE_PATH` 选用新的名称与路径；这样新旧索引隔离，旧数据卷仍保留。不要只改维度而继续复用旧向量表和关键词索引。

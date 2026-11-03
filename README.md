# 面试工坊

面试工坊是一个以 Java 17、Spring Boot 3 为核心的模拟面试项目。后端实现用户与会话管理、AI 对话、简历分析、面试追问、评分和报告；React 页面主要用于验证和演示后端接口。网页目前以文字交互为主。

## 一键启动

准备好 Docker Desktop 并确保它处于运行状态。从 GitHub 克隆或下载项目后，在项目最外层**双击 `start.cmd`**；也可以在 PowerShell 运行：

```powershell
.\start.ps1
```

脚本会自动创建缺失的 `config/.env`、启动 MySQL、MongoDB、Redis 和 pgvector、载入 `config/ai-credentials.sql`（如果存在），然后构建并启动后端与前端。首次运行需要下载镜像和依赖，时间会较长。启动完成后浏览器打开 `http://localhost:8080/`，后端健康检查为 `http://localhost:8002/actuator/health`。

关闭服务时，在项目最外层运行：

```powershell
docker compose -f compose.yaml down
```

`down` 不删除数据库数据卷。再次启动仍使用 `start.cmd`。

## RAG 一键评测

填写好讯飞 Embedding 配置后，启动 Docker Desktop 并双击 `eval-rag.cmd`。脚本会运行真实的岗位知识检索评测、保存指标，并恢复日常服务配置。操作和指标说明见 [配置指南](config/README.md)。

## AI 密钥与演示

公开仓库不包含真实密钥。**没有私有配置也能一键启动网页和后端**，但真实 AI 对话、评分等调用需要对应服务商的凭据和工作流 ID。所有待填值都在最外层的 [`config/` 配置指南](config/README.md)。换电脑完整演示时，可安全地复制自己的 `config/.env` 与 `config/ai-credentials.sql`，再运行 `start.cmd`。不要把私有文件加入仓库或发送给面试官。

## 项目结构

| 路径 | 用途 |
| --- | --- |
| `AI-Meeting-main/admin/src/main/java/` | 后端业务实现 |
| `AI-Meeting-main/admin/src/main/resources/` | 配置、Mapper、建表与非私密初始化数据 |
| `AI-Meeting-main/admin/src/test/` | 后端测试 |
| `AI-Meeting-main/docker-compose.yml` | 后端与依赖服务 |
| `AI-Meeting-Frontend-main/src/` | 演示页面和接口调用 |
| `config/` | 环境变量、AI 凭据模板与填写指南 |
| `compose.yaml`、`start.cmd`、`start.ps1` | 整体启动入口 |

本机产生的 `tmp/`、`.idea/`、`node_modules/`、`dist/`、`target/` 等目录已由 [.gitignore](.gitignore) 排除。后端开发细节见 [后端 README](AI-Meeting-main/README.md)。


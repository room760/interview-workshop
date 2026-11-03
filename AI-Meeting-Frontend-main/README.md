# 面试工坊演示页面

这是为验证后端接口而制作的 React / TypeScript 页面，使用 AI 辅助完成。项目介绍、Docker 一键启动与密钥配置请看 [总 README](../README.md)；页面统一从 `http://localhost:8080/` 访问，Nginx 将 `/api` 转发到后端。

需要单独开发页面时，在本目录运行：

```powershell
npm ci
npm run dev
```

开发服务器默认打开 `http://localhost:5173/`，并将 `/api` 代理到 `http://localhost:8002`。代码检查和构建命令为 `npm run check`、`npm run build`。前端密钥不要放入 `VITE_` 环境变量。

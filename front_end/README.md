# Anchor 前端

React 19 + Vite + TypeScript + wagmi。详细说明见仓库根目录 [`README.md`](../README.md)。

## 本地运行

```bash
npm install
npm run dev   # http://localhost:3000，开发代理 /api、/ws → 后端
```

- **Live**：连本机 API（默认 `8080`）与 WebSocket，展示真实扫块与指标。
- **Mock**：本地占位数据；底栏不展示 Kafka 状态。

常用脚本：`npm run build` / `npm run preview` / `npm run lint`。

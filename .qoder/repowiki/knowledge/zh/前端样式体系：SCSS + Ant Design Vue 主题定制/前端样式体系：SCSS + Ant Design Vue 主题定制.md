---
kind: frontend_style
name: 前端样式体系：SCSS + Ant Design Vue 主题定制
category: frontend_style
scope:
    - '**'
source_files:
    - admin-web/src/styles/variables.scss
    - admin-web/src/styles/global.scss
    - admin-web/vite.config.ts
    - booth-web/src/styles/variables.scss
    - booth-web/src/styles/global.scss
    - miniapp/app.wxss
    - admin-web/package.json
    - booth-web/package.json
---

## 样式系统概述

智慧停车 SaaS 平台采用 SCSS + Ant Design Vue 的前端样式方案，通过设计令牌（Design Tokens）统一管理视觉规范，在 admin-web、booth-web 两个 Web 应用和 miniapp 微信小程序中保持一致的视觉风格。

## 核心架构

### 设计令牌体系
每个前端工程都维护独立的 variables.scss 文件，定义统一的设计令牌：
- 色彩系统：主色 #165dff、成功 #00b578、警告 #f59e0b、错误 #dc2626
- 文本层级：主文本 #111827、次要文本 #64748b、禁用文本 #94a3b8
- 背景系统：页面背景 #f3f6fa、卡片背景 #ffffff
- 间距系统：四级间距 4px/8px/12px/16px/20px
- 圆角系统：基础 8px、小 6px、大 10px、全圆角 999px
- 阴影系统：卡片阴影、按钮阴影

### 全局样式组织
采用分层结构：
- variables.scss：设计令牌定义
- global.scss：全局重置、CSS 变量映射、通用组件样式覆盖
- index.scss：入口文件，导入全局样式

### CSS 变量桥接
通过 SCSS 编译时将设计令牌注入 CSS 变量，实现运行时主题切换能力。
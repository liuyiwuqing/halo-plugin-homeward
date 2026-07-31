# 归航寻亲

`homeward` 是一款 Halo 公益寻亲系统插件，首版聚焦“后台发布案例、前台公益展示、404 页面公益增强、访客提交线索、管理员处理线索”这一条闭环。

## 功能

- Console 管理页：`/console/homeward`
- 案例导入：支持从公网 URL 抓取，或粘贴网页正文/HTML；解析结果先回填为草稿，人工核对后再保存
- AI 案例导入：通过 `plugin-ai-foundation` 的结构化输出解析自然语言、网页、PDF、Word、TXT、Markdown 和 HTML 文档
- 批量 AI 导入：支持最多 20 条网页/文本资料或文档并发解析，逐条进入案例编辑器核对
- 前台公益页：`/homeward`
- 404 增强：在主题 404 页识别到 `error.status == 404` 时注入寻亲模块
- Public API：`/apis/public.homeward.muyin.site/v1alpha1/widget`
- Console API：`/apis/console.homeward.muyin.site/v1alpha1/**`
- 数据模型：`MissingPersonCase`、`ClueReport`
- 防刷：IP 哈希限流、honeypot 字段
- 通知：`homeward-clue-submitted-notification` 站内通知，可搜索并多选 Halo 用户，默认接收人 `admin`

## AI 解析

插件依赖 Halo 官方 [`plugin-ai-foundation`](https://github.com/halo-dev/plugin-ai-foundation)。安装并启用 AI Foundation 后，在归航寻亲设置的“AI
解析”分组中选择文本/网页模型；解析文档时优先选择声明支持 `file input` 的语言模型。模型设置保存的是
`AiModel.metadata.name`，留空时使用 AI Foundation 默认语言模型。

控制台入口：

- 单条导入：开启“使用 AI 智能识别”后解析网页或粘贴文本；也可以上传单个文档。
- 网页规则解析字段不足且 AI Foundation 已配置可用模型时，会在当前弹窗中提示一键使用 AI 重试，不会直接把空草稿送进编辑器。
- 批量导入：按 `---` 分隔多段文本，或一次选择多个文档；结果会逐条显示并进入编辑器核对。

相关 Console API：

- `POST /apis/console.homeward.muyin.site/v1alpha1/cases/import/preview`
- `POST /apis/console.homeward.muyin.site/v1alpha1/cases/import/preview/document`
- `POST /apis/console.homeward.muyin.site/v1alpha1/cases/import/batch/preview`
- `POST /apis/console.homeward.muyin.site/v1alpha1/cases/import/batch/preview/documents`

AI 结果不会自动保存或发布；网页抓取继续执行公网地址、响应大小和超时校验，文档上传限制为 10 MB。

## 后续 AI 方向

结合当前案例、访客线索和公益展示闭环，下一步适合按风险从低到高推进：

1. 线索摘要与标签：为管理员生成“地点、时间、关系、可信度待核验项”的摘要，但保留原文。
2. 相似案例/线索召回：使用 AI Foundation Embedding 建立候选召回，再由 Rerank 模型排序，最终仍由人工确认。
3. 重复案例检测：保存前提示疑似重复姓名、地点、日期和来源，不直接合并数据。
4. 隐私与发布检查：识别身份证号、精确住址、未授权联系方式等敏感信息，发布前提示脱敏。
5. 资料质量检查：检查日期矛盾、年龄不一致、来源缺失和联系方式格式，给出可解释的修正建议。
6. 图片辅助核验：在明确授权和模型支持的前提下提取照片元信息或生成无敏感细节的描述，禁止做人脸身份判断。

## 品牌

- 插件名：归航寻亲
- `metadata.name`：`homeward`
- 包名：`site.muyin.homeward`
- Logo：灯塔、回家路径、定位点组合，文件位于 `src/main/resources/logo.svg`

## 开发

```bash
./gradlew test
./gradlew build
```

本地启动 Halo DevTools：

```bash
./gradlew haloServer
```

## 许可证

[GPL-3.0](./LICENSE) © lywq

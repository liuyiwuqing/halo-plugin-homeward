# 归航寻亲

`homeward` 是一款 Halo 公益寻亲系统插件，支持公众查找案例、分享传播、提交寻亲申请与线索，以及管理员核实资料、发布案例、处理线索和结案。

## 功能

- Console 管理页：`/console/homeward`
- 案例导入：支持从公网 URL 抓取，或粘贴网页正文/HTML；解析结果先回填为草稿，人工核对后再保存
- AI 案例导入：通过 `plugin-ai-foundation` 的结构化输出解析自然语言、网页、PDF、Word、TXT、Markdown 和 HTML 文档
- 批量 AI 导入：支持最多 20 条网页/文本资料或文档并发解析，逐条进入案例编辑器核对
- 前台公益页：`/homeward`，支持姓名、标题、地点关键词搜索和分页，查询条件保留在地址中
- 案例详情页：`/homeward/cases/{slug}`，展示完整公开资料、来源、原图相册、更新时间，并支持复制链接、手机系统分享、本地二维码和 PNG 海报
- 寻亲申请：`/homeward/apply`，申请默认关闭；开启后收集资料与授权，由管理员核实并转为草稿
- 线索回执：`/homeward/receipts`，凭提交成功时获得的查询凭证查看处理进度
- 404 增强：页面 GET 请求抛出 404 异常时显示独立公益 404 页面，保留 HTTP 404 状态和返回首页入口；使用单独的 404 展示数量
- Public API：`/apis/public.homeward.muyin.site/v1alpha1/widget`
- Console API：`/apis/console.homeward.muyin.site/v1alpha1/**`
- 数据模型：`MissingPersonCase`、`ClueReport`、独立保存私密申请资料的 `CaseApplication`
- 防刷：IP 哈希限流、honeypot 字段
- 通知：`homeward-clue-submitted-notification` 站内通知，可搜索并多选 Halo 用户，默认接收人 `admin`
- 后台案例与线索支持服务端分页、关键词和状态筛选，概览指标可直达对应状态
- 线索处理记录真实登录账号并追加处理历史；已传播案例保持固定链接，结案后停止展示原资料

## 公众浏览与提交

- 公开案例列表：`GET /apis/public.homeward.muyin.site/v1alpha1/cases?keyword=&page=1&size=20`，返回 `page`、`size`、`total` 和 `items`；每页最多 50 条，只返回已发布案例。设置中的“公益页每页数量”控制默认分页大小。
- 案例详情：`GET /apis/public.homeward.muyin.site/v1alpha1/cases/{slug}`。草稿、归档和已撤下案例返回 404；曾公开且已找到的案例返回 410，旧详情链接展示结案提示，不披露姓名、照片或原始资料。未曾公开的已找到案例仍返回 404。
- 公益展示组件：`GET /apis/public.homeward.muyin.site/v1alpha1/widget`。保留原有展示契约，新增 `clueSubmissionEnabled`、`minContentLength`、`pageSize`；`surface=page` 使用公益页设置，不传则使用 404 展示设置。
- 线索内容按案例隔离，切换案例不会携带另一个案例的未提交资料。提交失败时保留本案例输入，并区分资料校验失败、暂未开放、提交过频和网络故障。
- 关闭“启用归航寻亲”会同时关闭公开列表、详情与线索提交；单独关闭线索提交仍可浏览已发布案例。
- 公开页面和分享只使用公开资料，不展示后台联系电话或线索联系方式。资料的“最近更新”表示编辑时间，不代表重新核验。

案例详情由服务端渲染，模板为 `homeward-case`，主题可通过 Halo 模板机制覆盖。覆盖模板时应保留公开字段和提交限制。当前 404 模式是独立页面，不是在主题原有 404 模板内插入模块。

分享链接使用案例 `slug`。案例公开或标记找到后，后台禁止修改该标识，接口返回 409，以保持已传播链接可用；草稿可以修改。二维码在浏览器本地生成，不调用外部二维码服务。

详情页可生成 1080 × 1600 PNG 海报，并预览、下载。生成前重新检查案例是否仍公开；海报包含公开资料、更新时间和详情页二维码，不包含后台联系电话。远程照片不支持跨域读取或加载超时时，改为无照片海报并说明原因。海报是生成时的资料快照，转发前应扫码核对最新状态；结案后请停止传播旧海报。

## 寻亲申请与审核

管理员在插件设置的“基础设置”中开启“允许公众提交寻亲申请”后，访客可以填写人员姓名、失踪时间和地点、详细情况、资料来源及私密联系信息，并确认资料授权。默认关闭；插件总开关关闭或设置读取失败时不接受申请。

- 开放状态：`GET /apis/public.homeward.muyin.site/v1alpha1/applications/config`。
- 提交申请：`POST /apis/public.homeward.muyin.site/v1alpha1/applications`。详细情况需 20～4000 字，必须填写联系方式并确认授权；同一远端 IP 每自然小时最多 3 次通过基本校验的提交尝试。
- 后台申请列表：`GET /apis/console.homeward.muyin.site/v1alpha1/applications/list`，支持分页和审核状态筛选。
- 后台审核：`POST /apis/console.homeward.muyin.site/v1alpha1/applications/{name}/review`，操作为 `accept` 或 `reject`，备注最多 2000 字。

审核通过只创建 `DRAFT` 案例，仍需管理员核对公开内容、隐私和授权后发布。申请人姓名、关系和联系方式留在私密申请中，不复制到案例字段。审核记录使用当前登录账号；拒绝后的申请不再转为草稿。草稿创建或审核状态保存中途失败时，后台显示“待恢复草稿创建”，可重试并复用关联草稿；重复通过已完成的申请不会重复建案例。

## 线索回执与处理历史

新线索提交成功时会显示一次 43 位查询凭证，可复制或下载 TXT 保存。查询凭证不会放进 URL 或浏览器本地存储，离开页面后清除；服务端仅保存凭证哈希，遗失后无法恢复。旧版本提交的线索没有回执凭证。

回执接口为 `POST /apis/public.homeward.muyin.site/v1alpha1/clue-receipts/lookup`，请求体为 `{"receiptToken":"查询凭证"}`。响应仅包含状态、提交时间、更新时间和固定进度说明，不返回线索内容、联系人、处理人或后台备注。无效凭证返回 404，查询过频返回 429；每 IP 每分钟最多 30 次、全实例每分钟最多 300 次，响应禁止缓存。

后台每次处理线索都会追加状态变化、登录账号、处理时间和备注，不覆盖已有历史。升级前仅保存的最后一条处理信息，会在首次追加时保留为“旧版记录”；更早的历史无法补造。普通管理账号通过业务接口处理线索，不能直接修改自动模型接口绕过历史记录；超级管理员仍保有 Halo 的全局权限。

申请和回执限流是当前插件进程内的保护，插件重启后计数重置，多实例不共享限流计数。反向代理部署时，需由 Halo 和可信代理正确解析远端地址；否则代理后的访客会共享配额。插件不会直接信任访客自行传入的转发头。

## AI 解析

插件依赖 Halo 官方 [`plugin-ai-foundation`](https://github.com/halo-dev/plugin-ai-foundation)。安装并启用 AI Foundation 后，在归航寻亲设置的“AI
解析”分组中选择文本/网页模型；解析文档时优先选择声明支持 `file input` 的语言模型。模型设置保存的是
`AiModel.metadata.name`，留空时使用 AI Foundation 默认语言模型。

控制台入口：

- 单条导入：开启“使用 AI 智能识别”后解析网页或粘贴文本；也可以上传单个文档。
- 网页规则解析字段不足且 AI Foundation 已配置可用模型时，会在当前弹窗中提示一键使用 AI 重试，不会直接把空草稿送进编辑器。
- 批量导入：按 `---` 分隔多段文本，或一次选择多个文档；结果会逐条显示并进入编辑器核对。关闭弹窗后，本次页面会话保留解析结果和已保存标记，再次打开可继续；开始新批次需确认。混合文本和文件解析时保留已成功的一侧，失败文件可重新选择。刷新或离开管理页后不保留批次资料。

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

覆盖安装相同版本号的本地构建时，Halo 2.25.4 的 Console 聚合脚本可能仍保留旧内容；应重启测试 Halo 并刷新浏览器后再验收。正式分发新版本时应同步更新 `gradle.properties` 和 `plugin.yaml` 中的版本号。

本地启动 Halo DevTools：

```bash
./gradlew haloServer
```

## 许可证

[GPL-3.0](./LICENSE) © lywq

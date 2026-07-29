# 归航寻亲

`homeward` 是一款 Halo 公益寻亲系统插件，首版聚焦“后台发布案例、前台公益展示、404 页面公益增强、访客提交线索、管理员处理线索”这一条闭环。

## 功能

- Console 管理页：`/console/homeward`
- 案例导入：支持从公网 URL 抓取，或粘贴网页正文/HTML；解析结果先回填为草稿，人工核对后再保存
- 前台公益页：`/homeward`
- 404 增强：在主题 404 页识别到 `error.status == 404` 时注入寻亲模块
- Public API：`/apis/public.homeward.muyin.site/v1alpha1/widget`
- Console API：`/apis/console.homeward.muyin.site/v1alpha1/**`
- 数据模型：`MissingPersonCase`、`ClueReport`
- 防刷：IP 哈希限流、honeypot 字段
- 通知：`homeward-clue-submitted-notification` 站内通知，可搜索并多选 Halo 用户，默认接收人 `admin`

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

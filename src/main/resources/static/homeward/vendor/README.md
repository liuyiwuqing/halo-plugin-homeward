# 本地二维码编码器

- 项目：[`qrcode-generator`](https://github.com/kazuhikoarase/qrcode-generator)
- 固定版本：`2.0.4`
- 作者：Kazuhiko Arase
- 许可证：MIT，完整许可见同目录 `qrcode.LICENSE`；原始文件的版权头未改动。
- npm 包：<https://registry.npmjs.org/qrcode-generator/-/qrcode-generator-2.0.4.tgz>
- `qrcode.js` 直接复制自该包的 `package/dist/qrcode.js`，未修改、未压缩，不依赖 CDN 或第三方二维码服务。
- npm 包未附独立 LICENSE 文件；本地许可全文取自上游固定提交 [`83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/LICENSE`](https://github.com/kazuhikoarase/qrcode-generator/blob/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/LICENSE)，与包声明的 MIT 许可和源文件版权一致。

SHA-256：

```text
02e2e18a99a90b02dad940851f59b7c3c5fd1ab79cbdece8595cb06328878159  qrcode-generator-2.0.4.tgz
79ec86f82856005b1c887905cfccfcfbec3821ca61c7fd5a952faa5f778f791c  qrcode.js
3a850fa5f08101db6f40676c2786e10bd2cd5fff7b12ffdf1e0c434d4e49d90c  qrcode.LICENSE
```

取包时已核对 `package.json` 的名称、版本、许可，并验证 npm registry 提供的完整性值：

```text
sha512-mZSiP6RnbHl4xL2Ap5HfkjLnmxfKcPWpWe/c+5XxCuetEenqmNFf1FH/ftXPCtFG5/TDobjsjz6sSNL0Sr8Z9g==
```

浏览器使用 `window.qrcode(0, 'M')` 自动选择 QR 版本，调用 `addData(url, 'Byte')`、`make()` 后以 `createSvgTag({cellSize: 4, margin: 16, scalable: true})` 生成 SVG。`margin` 的单位是像素，16 px 保留 4 个模块的静区。分享 URL 应先通过 `new URL(url).href` 规范化；编码任意非 ASCII 文本时可使用包内 `qrcode.stringToBytesFuncs['UTF-8']`。

// md-editor-v3 默认从 unpkg 等 CDN 动态加载 highlight.js、screenfull、cropper 等依赖，
// 国内网络或内网环境下经常加载失败（代码块没有高亮、全屏按钮无效）。
// 这里改成使用 npm 安装的本地实例，样式也走 Vite 打包后的本地地址（见 docs/pitfalls.md）。
import Cropper from 'cropperjs'
import cropperCss from 'cropperjs/dist/cropper.min.css?url'
import hljs from 'highlight.js'
import hlDark from 'highlight.js/styles/github-dark.min.css?url'
import hlLight from 'highlight.js/styles/github.min.css?url'
import { config } from 'md-editor-v3'
import screenfull from 'screenfull'

config({
  editorExtensions: {
    highlight: {
      instance: hljs,
      css: { github: { light: hlLight, dark: hlDark } },
    },
    screenfull: { instance: screenfull },
    cropper: { instance: Cropper, css: cropperCss },
  },
})

/** 关掉需要额外 CDN 依赖、本项目又用不到的扩展：mermaid、katex、prettier、echarts 代码块。 */
export const mdCommonProps = {
  codeTheme: 'github',
  noMermaid: true,
  noKatex: true,
  noEcharts: true,
} as const

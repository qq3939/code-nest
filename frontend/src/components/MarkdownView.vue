<script setup lang="ts">
import DOMPurify from 'dompurify'
import { MdCatalog, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { mdCommonProps } from '@/utils/mdEditor'

defineProps<{ content: string; id: string; showCatalog?: boolean; scrollElement?: string | HTMLElement }>()

// md-editor 把 Markdown 转成 HTML 后，经 sanitize 钩子再交给 v-html；
// 在这里用 DOMPurify 清洗，正文里手写的 <script>、onerror= 等都会被去掉（SPEC 9.15）
const sanitize = (html: string) => DOMPurify.sanitize(html)
</script>

<template>
  <MdPreview v-bind="mdCommonProps" :id="id" :model-value="content" :sanitize="sanitize" preview-theme="github" language="zh-CN" />
  <Teleport v-if="showCatalog" to="#pa-catalog" defer>
    <MdCatalog :editor-id="id" :scroll-element="scrollElement ?? 'html'" :offset-top="80" />
  </Teleport>
</template>

<template>
  <el-result icon="info" title="404" sub-title="页面不存在">
    <template #extra>
      <RouterLink to="/"><el-button type="primary">回首页</el-button></RouterLink>
    </template>
  </el-result>
</template>

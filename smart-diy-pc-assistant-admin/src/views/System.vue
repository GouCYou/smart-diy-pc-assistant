<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../lib/api";
const services = ref<any[]>([]);
onMounted(async () => {
  services.value = await Promise.all(
    [
      ["业务 API", "/api/health"],
      ["Agent API", "/agent/health"],
    ].map(async ([name, url]) => {
      try {
        return { name, url, status: "连接正常", data: await api.request(url!) };
      } catch (e) {
        return { name, url, status: (e as Error).message };
      }
    }),
  );
});
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>系统信息</h1>
      <p>检查服务连接与部署配置。</p>
    </div>
  </div>
  <section class="panel">
    <h2>服务连接</h2>
    <el-descriptions :column="1" border
      ><el-descriptions-item v-for="s in services" :key="s.name" :label="s.name"
        >{{ s.url }} · {{ s.status }}</el-descriptions-item
      ></el-descriptions
    >
  </section>
  <section class="panel">
    <h2>部署配置</h2>
    <p class="content-text">
      数据库、Redis、邮件及对象存储由 Java 服务的环境变量配置。模型和执行额度在
      Agent 配置中管理。前端使用同域代理连接 /api、/media 和 /agent。
    </p>
    <p class="muted">这些信息由服务器管理，不在浏览器保存数据库或存储密钥。</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
const { busy, error, perform } = useTask(),
  stats = ref<Record<string, number | null>>({
    用户: null,
    硬件型号: null,
    资讯: null,
    待处理反馈: null,
  }),
  services = ref<{ name: string; ok: boolean }[]>([]);
async function load() {
  await perform(async () => {
    const tasks = [
      ["用户", "/api/admin/users"],
      ["硬件型号", "/api/admin/hardware-parts"],
      ["资讯", "/api/admin/news"],
      ["待处理反馈", "/api/admin/feedback"],
    ];
    const results = await Promise.allSettled(
      tasks.map(async ([name, path]) => {
        const rows = await api.request(path!);
        stats.value[name!] =
          name === "待处理反馈"
            ? rows.filter((r: any) => r.status !== "RESOLVED").length
            : rows.length;
      }),
    );
    services.value = await Promise.all(
      [
        ["Java 业务服务", "/api/health"],
        ["Python Agent", "/agent/health"],
      ].map(async ([name, path]) => {
        try {
          await api.request(path!);
          return { name: name!, ok: true };
        } catch {
          return { name: name!, ok: false };
        }
      }),
    );
    if (results.some((r) => r.status === "rejected"))
      throw Error("部分统计加载失败，请重试");
  });
}
onMounted(load);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>工作概览</h1>
      <p>掌握内容与服务状态，开始今天的管理工作。</p>
    </div>
    <el-button :loading="busy" @click="load">刷新数据</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" show-icon />
  <div class="metrics">
    <div v-for="(value, key) in stats" :key="key" class="metric">
      <span>{{ key }}</span
      ><strong>{{ value ?? "—" }}</strong>
    </div>
  </div>
  <div class="dashboard-grid">
    <section class="panel">
      <h2>常用工作</h2>
      <div class="quick-links">
        <router-link to="/hardware" class="quick-link"
          >维护硬件目录<small>型号、规格与兼容性参数</small></router-link
        ><router-link to="/news" class="quick-link"
          >编辑与发布资讯<small>管理草稿、封面和正文</small></router-link
        ><router-link to="/knowledge" class="quick-link"
          >更新 Agent 知识<small>资料导入与检索验证</small></router-link
        ><router-link to="/feedback" class="quick-link"
          >处理用户反馈<small>受理问题并跟进解决</small></router-link
        >
      </div>
    </section>
    <section class="panel">
      <h2>服务状态</h2>
      <div
        v-for="s in services"
        :key="s.name"
        class="row"
        style="
          justify-content: space-between;
          padding: 17px 0;
          border-bottom: 1px solid #eef0f5;
        "
      >
        <span>{{ s.name }}</span
        ><el-tag :type="s.ok ? 'success' : 'danger'">{{
          s.ok ? "连接正常" : "连接失败"
        }}</el-tag>
      </div>
      <p class="muted">模型配置及任务情况请在 Agent 配置和运行记录中检查。</p>
    </section>
  </div>
</template>

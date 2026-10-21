<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
import {
  computed,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  type Component,
} from "vue";
import {
  Monitor,
  User,
  Cpu,
  Document,
  ChatDotRound,
  Tickets,
  Bell,
  Operation,
  Collection,
  Setting,
  Fold,
} from "@element-plus/icons-vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api } from "./lib/api";
const route = useRoute(),
  router = useRouter(),
  ready = ref(false),
  authenticated = ref(false),
  busy = ref(false),
  collapsed = ref(false),
  username = ref("");
const form = reactive({ username: "", password: "" });
const menus: Array<[string, string, Component]> = [
  ["/", "工作概览", Monitor],
  ["/users", "用户管理", User],
  ["/hardware", "硬件目录", Cpu],
  ["/news", "资讯管理", Document],
  ["/community", "社区内容", ChatDotRound],
  ["/feedback", "用户反馈", Tickets],
  ["/notifications", "系统通知", Bell],
  ["/assistant", "Agent 配置", Operation],
  ["/knowledge", "知识库", Collection],
  ["/runs", "运行记录", Tickets],
  ["/settings", "系统信息", Setting],
];
const title = computed(
  () => menus.find((m) => m[0] === route.path)?.[1] || "管理控制台",
);
function logout() {
  localStorage.removeItem("smartdiy_admin_token");
  authenticated.value = false;
  form.password = "";
}
function expired(e: Event) {
  if ((e as CustomEvent).detail === "smartdiy_admin_token") logout();
}
async function login() {
  if (!form.username.trim() || !form.password)
    return ElMessage.warning("请输入管理员账号和密码");
  busy.value = true;
  try {
    const d = await api.request("/api/auth/admin-login", "POST", form);
    localStorage.setItem("smartdiy_admin_token", d.token);
    username.value = d.username;
    authenticated.value = true;
    form.password = "";
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
onMounted(async () => {
  window.addEventListener("session-expired", expired);
  if (localStorage.getItem("smartdiy_admin_token")) {
    try {
      const u = await api.request("/api/auth/me");
      if (u.role !== "ADMIN") throw Error("需要管理员权限");
      username.value = u.username;
      authenticated.value = true;
    } catch {
      logout();
    }
  }
  ready.value = true;
});
onUnmounted(() => window.removeEventListener("session-expired", expired));
</script>
<template>
  <el-config-provider size="large" :locale="zhCn"
    ><div v-if="!ready" class="startup">正在验证管理会话…</div>
    <main v-else-if="!authenticated" class="login-page">
      <section class="login-intro">
        <div class="brand">
          <img src="/logo.png" alt="" />SMART DIY <span>管理控制台</span>
        </div>
        <div>
          <span class="eyebrow">为每一次装机，提供可靠支持</span>
          <h1>有序管理。<br />专注体验。</h1>
          <p>从硬件目录到社区内容，<br />让知识、服务与用户连接起来。</p>
        </div>
        <span>Smart DIY · Administration</span>
      </section>
      <section class="login-form">
        <div class="login-card">
          <span class="eyebrow">欢迎回来</span>
          <h2>登录管理控制台</h2>
          <p>使用具有管理权限的账号继续。</p>
          <el-form label-position="top" @submit.prevent="login"
            ><el-form-item label="管理员账号"
              ><el-input
                v-model="form.username"
                autocomplete="username"
                placeholder="请输入账号" /></el-form-item
            ><el-form-item label="密码"
              ><el-input
                v-model="form.password"
                type="password"
                autocomplete="current-password"
                show-password
                placeholder="请输入密码" /></el-form-item
            ><el-button
              type="primary"
              native-type="submit"
              :loading="busy"
              class="full"
              >登录控制台</el-button
            ></el-form
          >
        </div>
      </section>
    </main>
    <div v-else class="admin-shell" :class="{ collapsed }">
      <aside class="admin-sidebar">
        <div class="brand">
          <img src="/logo.png" alt="" /><strong v-if="!collapsed"
            >Smart DIY<small>管理控制台</small></strong
          >
        </div>
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          @select="router.push"
          ><el-menu-item v-for="m in menus" :key="m[0]" :index="m[0]"
            ><el-icon class="menu-icon"><component :is="m[2]" /></el-icon
            ><template #title>{{ m[1] }}</template></el-menu-item
          ></el-menu
        >
        <div class="sidebar-foot" v-if="!collapsed">内容与服务 · 集中管理</div>
      </aside>
      <section class="admin-main">
        <header class="admin-header">
          <div class="row">
            <el-button
              text
              aria-label="折叠菜单"
              @click="collapsed = !collapsed"
              ><el-icon><Fold /></el-icon></el-button
            ><span
              >控制台 / <strong>{{ title }}</strong></span
            >
          </div>
          <div class="row">
            <el-tag effect="plain">管理员</el-tag><span>{{ username }}</span
            ><el-button text @click="logout">退出登录</el-button>
          </div>
        </header>
        <main class="admin-content"><router-view /></main>
      </section></div
  ></el-config-provider>
</template>

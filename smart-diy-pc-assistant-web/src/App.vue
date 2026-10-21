<script setup lang="ts">
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ChatDotRound,
  Menu as Grid,
  Compass,
  Document,
  UserFilled as User,
  Bell,
  ArrowRight,
  ArrowLeft,
  DArrowLeft,
  DArrowRight,
  QuestionFilled,
} from "@element-plus/icons-vue";
import { user, restore, logout } from "./lib/session";
const router = useRouter();
const route = useRoute(),
  ready = ref(false),
  sidebarCollapsed = ref(true);
const nav = [
  { path: "/", label: "对话", icon: ChatDotRound },
  { path: "/builds", label: "配置", icon: Grid },
  { path: "/community", label: "发现", icon: Compass },
  { path: "/news", label: "热点", icon: Document },
  { path: "/account", label: "我的", icon: User },
];
const rootPaths = ["/", "/builds", "/community", "/news", "/account"];
const rootPage = computed(() => rootPaths.includes(route.path));
const pageTitle = computed(() => ({"/auth":"登录 / 注册", "/profile":"个人资料", "/settings":"设置", "/preferences":"装机偏好", "/notifications":"消息通知", "/feedback":"反馈", "/feedback/mine":"我的反馈"}[route.path] || (route.path.startsWith("/users/") ? "个人空间" : route.path.startsWith("/news/") ? "新闻详情" : "动态详情")));
function back() { if (window.history.state.back) router.back(); else router.push('/account'); }
function expired(e: Event) {
  if ((e as CustomEvent).detail === "smartdiy_token") logout();
}
onMounted(async () => {
  window.addEventListener("session-expired", expired);
  await restore();
  ready.value = true;
});
onUnmounted(() => window.removeEventListener("session-expired", expired));
</script>
<template>
  <el-config-provider size="large" :locale="zhCn"
    ><div class="web-shell" :class="{ 'is-chat': route.path === '/', 'is-auth': route.path === '/auth', 'is-subpage': !rootPage, 'sidebar-collapsed': sidebarCollapsed }">
      <aside class="web-sidebar">
        <router-link to="/" class="brand"
          ><img src="/logo.png" alt="" /><span
            >Smart DIY</span
          ></router-link
        >

        <nav v-slider class="primary-nav">
          <router-link
            v-for="n in nav"
            :key="n.path"
            :to="n.path"
            :title="n.path === '/' ? '装机对话' : n.label"
            :class="{
              active:
                route.path === n.path ||
                (n.path !== '/' && route.path.startsWith(n.path)),
            }"
            ><el-icon><component :is="n.icon" /></el-icon
            ><span class="nav-label">{{
              n.path === "/"
                ? "装机对话"
                : n.path === "/builds"
                  ? "我的配置"
                  : n.path === "/community"
                    ? "装机社区"
                    : n.path === "/news"
                      ? "硬件资讯"
                      : "个人中心"
            }}</span></router-link>
        </nav>
        <button class="sidebar-toggle" :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'" :title="sidebarCollapsed ? '展开导航' : '收起导航'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed"><el-icon><DArrowRight v-if="sidebarCollapsed" /><DArrowLeft v-else /></el-icon></button>
        <div class="sidebar-bottom">
          <router-link to="/feedback" title="帮助与反馈" aria-label="帮助与反馈" class="sidebar-help"><el-icon><QuestionFilled /></el-icon><span>帮助与反馈</span></router-link
          ><router-link :to="user ? '/account' : '/auth'" class="account-link" :title="user?.nickname || user?.username || '登录你的账户'"
            ><el-avatar :size="36" :src="user?.avatarUrl"><span v-if="user">{{(user.nickname || user.username).slice(0,1)}}</span><el-icon v-else><User /></el-icon></el-avatar
            ><span
              >{{ user?.nickname || user?.username || "登录你的账户"
              }}</span
            ><el-icon><ArrowRight /></el-icon
          ></router-link>
        </div>
      </aside>
      <div class="web-main">
        <header class="web-topbar">
          <router-link to="/" class="mobile-brand"
            ><img src="/logo.png" alt="" />Smart DIY</router-link
          >
          <div class="desktop-only topbar-crumb">
            Smart DIY <span>/</span>
            {{
              route.path === "/"
                ? "装机工作区"
                : nav.find((n) => n.path === route.path)?.label || pageTitle
            }}
          </div>
          <div class="row">
            <router-link
              to="/notifications"
              aria-label="消息通知"
              class="notice-link"
              ><el-icon><Bell /></el-icon></router-link
            ><span class="topbar-divider" /><router-link
              :to="user ? '/account' : '/auth'"
              >{{
                user ? user.nickname || user.username : "登录 / 注册"
              }}</router-link
            >
          </div>
        </header>
        <header v-if="!rootPage && route.path !== '/auth'" class="mobile-page-bar">
          <button aria-label="返回" @click="back"><el-icon><ArrowLeft /></el-icon></button><strong>{{ pageTitle }}</strong><span id="mobile-page-action" />
        </header>
        <main
          v-if="ready"
          class="web-content"
          :class="{ 'chat-content': route.path === '/' }"
        >
          <router-view v-slot="{ Component }"><Transition name="page-fade" mode="out-in"><div :key="route.path" class="route-page"><component :is="Component" /></div></Transition></router-view>
        </main>
        <div v-else class="startup">正在恢复会话…</div>
      </div>
      <nav v-if="rootPage" v-slider class="mobile-nav">
        <router-link
          v-for="n in nav"
          :key="n.path"
          :to="n.path"
          :class="{
            active:
              route.path === n.path ||
              (n.path !== '/' && route.path.startsWith(n.path)),
          }"
          ><span class="mobile-nav-icon"
            ><span v-if="n.path === '/'" class="ai-tab">AI</span
            ><el-icon v-else><component :is="n.icon" /></el-icon></span
          ><span>{{ n.label }}</span></router-link
        >
      </nav>
    </div></el-config-provider
  >
</template>

<script setup lang="ts">
import NavigationToggle from "../components/NavigationToggle.vue";
import { computed, onMounted, ref } from 'vue';
import { UserFilled, ArrowRight } from '@element-plus/icons-vue';
import { user } from '../lib/session';
import { api } from '../lib/api';
import { useTask } from '../lib/task';
const {error, perform} = useTask(), stats = ref({builds:0,favorites:0,posts:0});
const spacePath = computed(() => user.value ? `/users/${user.value.userId}` : '/auth');
onMounted(() => perform(async () => {
  if (!user.value) return;
  const [builds, space] = await Promise.all([api.request('/api/build-configs?segment=all'),api.request(spacePath.value.replace('/users/', '/api/users/')+'/space')]);
  stats.value = {builds:builds.length, favorites:space.favoriteBuilds.length+space.favoritePosts.length+space.favoriteNews.length,posts:space.posts.length};
}));
</script>
<template>
  <div class="account-home">
    <h1 class="desktop-only account-heading page-title-controls"><NavigationToggle />我的</h1>
    <el-alert v-if="error" :title="error" type="error" />
    <router-link :to="user ? '/profile' : '/auth'" class="profile-card">
      <el-avatar :size="64" :src="user?.avatarUrl"><el-icon><UserFilled /></el-icon></el-avatar>
      <div><strong>{{user?.nickname || user?.username || '登录 / 注册'}}</strong><p v-if="user">@{{user.username}}</p><p v-else>登录后同步配置、收藏和社区内容。</p></div><el-icon class="profile-chevron"><ArrowRight /></el-icon>
    </router-link>
    <div class="account-stats">
      <router-link to="/builds"><strong>{{stats.builds}}</strong><span>配置</span></router-link>
      <router-link :to="user ? spacePath+'?tab=builds' : '/auth'"><strong>{{stats.favorites}}</strong><span>收藏</span></router-link>
      <router-link :to="spacePath"><strong>{{stats.posts}}</strong><span>动态</span></router-link>
    </div>
    <div class="profile-tools">
      <router-link :to="spacePath"><strong>个人空间</strong><span>主页与动态</span></router-link>
      <router-link :to="user ? spacePath+'?tab=builds' : '/auth'"><strong>我的收藏</strong><span>配置、动态、热点</span></router-link>
      <router-link to="/builds?create=1"><strong>新建配置</strong><span>记录装机方案</span></router-link>
      <router-link to="/community?compose=1"><strong>发布动态</strong><span>分享装机想法</span></router-link>
    </div>
    <nav class="account-menu">
      <router-link to="/notifications"><span>消息通知</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/feedback"><span>反馈</span><el-icon><ArrowRight /></el-icon></router-link>
      <router-link to="/settings"><span>设置</span><el-icon><ArrowRight /></el-icon></router-link>
    </nav>
  </div>
</template>

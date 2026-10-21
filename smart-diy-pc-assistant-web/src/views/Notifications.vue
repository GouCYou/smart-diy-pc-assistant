<script setup lang="ts">
import DialogHeading from "../components/DialogHeading.vue";
import EmptyState from "../components/EmptyState.vue";
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { InfoFilled, ArrowRight } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { user } from "../lib/session";
import { useTask } from "../lib/task";
const { busy, error, perform } = useTask(),
  router = useRouter(),
  rows = ref<any[]>([]),
  selected = ref<any>(null);
async function markAll() {
  await api.request("/api/notifications/read-all", "POST");
  await load();
}
async function load() {
  if (user.value) rows.value = await api.request("/api/notifications");
}
async function view(row: any) {
  selected.value = await api.request(`/api/notifications/${row.id}`);
  await load();
}
function go() {
  if (selected.value.relatedPostId)
    router.push("/community/" + selected.value.relatedPostId);
  else if (selected.value.relatedNewsId)
    router.push("/news/" + selected.value.relatedNewsId);
  selected.value = null;
}
onMounted(() => perform(load));
</script>
<template>
  <Teleport to="#mobile-page-action"><button v-if="user" class="profile-save" :disabled="busy" @click="perform(markAll, '已全部标为已读')">全部已读</button></Teleport>
  <div class="page-heading desktop-only">
    <div class="desktop-only">
      <h1>消息通知</h1>

    </div>
    <el-button
      v-if="user"
      :loading="busy"
      @click="perform(markAll, '已全部标为已读')"
      >全部已读</el-button
    >
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section v-if="!user" class="panel empty-auth">
    <p>登录后查看消息通知。</p>
    <router-link to="/auth"
      ><el-button type="primary">登录</el-button></router-link
    >
  </section>
  <template v-else><div class="notification-group">
    <button v-for="n in rows" :key="n.id" class="notification-row" @click="perform(() => view(n))">
      <el-avatar :size="44" :src="n.actor?.avatarUrl"><el-icon><InfoFilled /></el-icon></el-avatar>
      <div><strong>{{n.title}}<i v-if="n.unread" class="unread-dot" /></strong><p>{{n.content.slice(0,120)}}</p><time>{{n.createdAt?.replace('T',' ').slice(0,16)}}</time></div><el-icon><ArrowRight /></el-icon>
    </button></div>
    <EmptyState v-if="!rows.length" description="暂时没有新消息" kind="bell" /></template
  ><el-dialog
    :model-value="!!selected"
    :title="selected?.title"
    width="620px"
    class="mobile-full-dialog" :show-close="false"
    @close="selected = null"
    ><template #header><DialogHeading title="通知详情" @close="selected = null" /></template><h2>{{selected?.title}}</h2><p class="content-text">{{ selected?.content }}</p>
    <el-button
      v-if="selected?.relatedPostId || selected?.relatedNewsId"
      type="primary"
      @click="go"
      >查看相关内容</el-button
    ></el-dialog
  >
</template>

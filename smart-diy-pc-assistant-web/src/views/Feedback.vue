<script setup lang="ts">
import EmptyState from "../components/EmptyState.vue";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { Plus, ArrowRight } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { user, authenticated } from "../lib/session";
import { useTask } from "../lib/task";
const props = defineProps<{ view?: string;embedded?:boolean }>();
const emit=defineEmits<{changeView:[view:string]}>();
const router = useRouter();
function mine(){if(props.embedded)emit("changeView","mine");else router.push("/feedback/mine");}
const { busy, error, perform } = useTask(),
  rows = ref<any[]>([]),
  form = reactive({ content: "", imageKeys: [] as string[] }),
  images = ref<string[]>([]);
async function load() {
  if (user.value) rows.value = await api.request("/api/feedback/mine");
}
async function upload(event: Event) {
  authenticated();
  for (const file of Array.from(
    (event.target as HTMLInputElement).files || [],
  )) {
    if (images.value.length >= 9) break;
    const d = await api.upload(file, "feedback");
    form.imageKeys.push(d.key);
    images.value.push(d.url);
  }
}
async function send() {
  authenticated();
  if (!form.content.trim()) throw Error("请填写反馈内容");
  await api.request("/api/feedback", "POST", form);
  form.content = "";
  form.imageKeys = [];
  images.value = [];
  await load();
}
onMounted(() => perform(load));
</script>
<template>
  <div class="feedback-page"><Teleport v-if="!embedded" to="#mobile-page-action"><router-link v-if="props.view !== 'mine'" to="/feedback/mine" class="feedback-nav-action">我的反馈</router-link></Teleport>
    <div class="page-heading feedback-heading"><h1 class="desktop-only">{{props.view === 'mine' ? '我的反馈' : '反馈'}}</h1><button v-if="props.view !== 'mine'" class="feedback-mine desktop-only" @click="mine">我的反馈 <el-icon><ArrowRight /></el-icon></button><button v-else-if="embedded" class="feedback-mine" @click="emit('changeView','new')">提交反馈</button></div>
    <el-alert v-if="error" :title="error" type="error" />
    <template v-if="props.view !== 'mine'">
      <section class="panel"><h2>反馈内容</h2><el-input v-model="form.content" type="textarea" :rows="embedded ? 5 : 7" aria-label="反馈内容" maxlength="1200" show-word-limit placeholder="写下遇到的问题、建议或想补充的功能" /></section>
      <section class="panel"><h2>截图</h2><div class="image-grid">
        <div v-for="(img,i) in images" :key="img"><el-image :src="img" :preview-src-list="images" fit="cover" /><el-button text @click="images.splice(i,1);form.imageKeys.splice(i,1)">移除</el-button></div>
        <label v-if="images.length < 9" class="image-add"><input hidden type="file" multiple accept="image/*" @change="perform(() => upload($event))" /><el-icon><Plus /></el-icon><span>添加图片</span></label>
      </div></section>
      <Teleport v-if="embedded" defer to="#feedback-dialog-actions"><el-button type="primary" :loading="busy" :disabled="!form.content.trim()" @click="perform(async () => { await send(); mine(); },'反馈已提交')">提交反馈</el-button></Teleport><div v-else class="feedback-submit"><el-button type="primary" :loading="busy" :disabled="!form.content.trim()" @click="perform(async () => { await send(); mine(); },'反馈已提交')">提交反馈</el-button></div>
    </template>
    <template v-else>
      <article v-for="r in rows" :key="r.id" class="panel feedback-record"><div class="row" style="justify-content:space-between"><el-tag :type="r.status === 'RESOLVED' ? 'success' : 'info'">{{r.statusLabel}}</el-tag><time>{{r.createdAt?.replace('T',' ').slice(0,16)}}</time></div><p class="content-text">{{r.content}}</p><div v-if="r.imageUrls?.length" class="image-grid"><el-image v-for="img in r.imageUrls" :key="img" :src="img" :preview-src-list="r.imageUrls" fit="cover" /></div><el-steps :active="r.status === 'RESOLVED' ? 3 : r.status === 'RECEIVED' ? 2 : 1" finish-status="success" align-center><el-step title="已提交" /><el-step title="已收到" /><el-step title="已解决" /></el-steps></article>
      <EmptyState v-if="!rows.length" :description="user ? '暂无反馈' : '登录后查看反馈记录'" />
    </template>
  </div>
</template>

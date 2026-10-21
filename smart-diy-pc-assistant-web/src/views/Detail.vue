<script setup lang="ts">
import EmptyState from "../components/EmptyState.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowRight, Plus, Star, StarFilled, Picture, Grid } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { user, authenticated } from "../lib/session";
import { markdown } from "../lib/markdown";
import { useTask } from "../lib/task";
const props = defineProps<{ kind: string; embedded?:boolean }>(),
  route = useRoute(),
  router = useRouter(),
  { busy, error, perform, confirm } = useTask(),
  item = ref<any>(null),
  comments = ref<any[]>([]),
  sort = ref("latest"),
  form = reactive({
    content: "",
    imageKeys: [] as string[],
    parentCommentId: null as number | null,
    buildConfigId: null as number | null,
  }),
  images = ref<string[]>([]),
  reply = ref(""),
  builds = ref<any[]>([]),
  showCommentTools = ref(false);
const emit=defineEmits<{close:[]}>();
function close(){if(props.embedded)emit("close");else void router.push("/"+props.kind);}
const base = computed(() =>
  props.kind === "news"
    ? `/api/news/${route.params.id}`
    : `/api/community/posts/${route.params.id}`,
);
async function load() {
  item.value = await api.request(base.value);
  await loadComments();
  if (user.value)
    builds.value = await api.request("/api/build-configs?segment=all");
}
async function loadComments() {
  comments.value = await api.request(
    `${base.value}/comments?sort=${sort.value}`,
  );
}
async function toggle(action: string) {
  authenticated();
  item.value = await api.request(`${base.value}/${action}`, "POST");
}
async function upload(event: Event) {
  for (const file of Array.from(
    (event.target as HTMLInputElement).files || [],
  )) {
    if (images.value.length >= 9) break;
    const d = await api.upload(file);
    form.imageKeys.push(d.key);
    images.value.push(d.url);
  }
}
async function deleteComment(id: number) {
  await api.request(`/api/community/comments/${id}`, "DELETE");
  await load();
}
async function deletePost() {
  await api.request(base.value, "DELETE");
  close();
}
async function likeComment(id: number) {
  authenticated();
  await api.request(
    props.kind === "news"
      ? `/api/news/comments/${id}/like`
      : `/api/community/comments/${id}/like`,
    "POST",
  );
  await loadComments();
}
async function send() {
  authenticated();
  if (!form.content.trim()) throw Error("请填写评论");
  await api.request(`${base.value}/comments`, "POST", form);
  form.content = "";
  form.imageKeys = [];
  images.value = [];
  form.parentCommentId = null;
  reply.value = "";
  await load();
}
onMounted(() => perform(load));
</script>
<template>
  <div class="article social-detail" :class="{'is-embedded':embedded}">
    <el-button v-if="!embedded" text @click="close"
      >← 返回{{ kind === "news" ? "资讯" : "社区" }}</el-button
    ><el-alert v-if="error" :title="error" type="error" />
    <article v-if="item" class="panel">
      <template v-if="kind === 'news'"
        ><h1>{{ item.title }}</h1>
        <span class="tag">{{ item.tag }}</span>
        <p class="muted">
          {{ item.createdAt?.replace("T", " ").slice(0, 16) }} ·
          {{ item.viewCount }} 次阅读
        </p>
        <img
          v-if="item.coverUrl"
          :src="item.coverUrl"
          alt="资讯封面"
          class="cover" />
        <div class="markdown" v-html="markdown(item.content)" /></template
      ><template v-else
        ><router-link :to="`/users/${item.author.id}`" class="post-author"
          ><el-avatar :src="item.author.avatarUrl" /><strong>{{
            item.author.nickname || item.author.username
          }}</strong></router-link
        >
        <p class="muted post-detail-date">{{item.createdAt?.replace("T"," ").slice(0,16)}}</p><p class="content-text">{{ item.content }}</p>
        <div class="image-grid">
          <el-image
            v-for="img in item.imageUrls"
            :key="img"
            :src="img"
            :preview-src-list="item.imageUrls"
            fit="cover"
          />
        </div>
        <router-link
          v-if="item.buildConfig"
          :to="`/builds?public=${item.buildConfig.id}`"
          class="reference"
          >▦ {{ item.buildConfig.title }} · ¥{{
            item.buildConfig.totalPrice
          }}</router-link
        ></template
      >
      <div class="action-row">
        <el-button
          :type="item.liked ? 'primary' : 'default'"
          @click="perform(() => toggle('like'))"
          >♡ {{ item.likeCount }} 赞</el-button
        ><el-button :class="{'is-favorited':item.favorite}" @click="perform(() => toggle('favorite'))"
          ><el-icon><StarFilled v-if="item.favorite" /><Star v-else /></el-icon>{{ item.favorite ? "已收藏" : "收藏" }} ·
          {{ item.favoriteCount }}</el-button
        ><el-button
          v-if="kind === 'community' && user?.userId === item.author.id"
          type="danger"
          text
          @click="confirm('删除这条动态？', deletePost)"
          >删除动态</el-button
        >
      </div>
    </article>
    <section v-if="item" class="panel">
      <div class="page-heading">
        <h2 style="margin: 0">评论 {{ comments.length }}</h2>
        <el-select
          v-model="sort"
          style="width: 130px"
          @change="perform(loadComments)"
          ><el-option label="最新" value="latest" /><el-option
            label="热门"
            value="hot"
        /></el-select>
      </div>
      <article v-for="c in comments" :key="c.id" class="comment">
        <router-link :to="`/users/${c.author.id}`" class="post-author"
          ><el-avatar :size="32" :src="c.author.avatarUrl" /><strong>{{
            c.author.nickname || c.author.username
          }}</strong></router-link
        >
        <p v-if="c.parentCommentId" class="muted">
          回复 {{ c.replyToNickname || "一条评论" }}
        </p>
        <p>{{ c.content }}</p><router-link v-if="c.buildConfig" :to="`/builds?public=${c.buildConfig.id}`" class="reference">▦ {{c.buildConfig.title}}</router-link>
        <div class="image-grid">
          <el-image
            v-for="img in c.imageUrls"
            :key="img"
            :src="img"
            :preview-src-list="c.imageUrls"
            fit="cover"
          />
        </div>
        <div class="action-row">
          <small class="muted">{{
            c.createdAt?.replace("T", " ").slice(0, 16)
          }}</small
          ><el-button
            v-if="kind === 'community' && c.author.id === user?.userId"
            text
            type="danger"
            @click="confirm('删除这条评论？', () => deleteComment(c.id))"
            >删除</el-button
          ><el-button
            text
            @click="
              form.parentCommentId = c.id;
              reply = c.author.nickname || c.author.username;
            "
            >回复</el-button
          ><el-button text @click="perform(() => likeComment(c.id))"
            >♡ {{ c.likeCount }}</el-button
          >
        </div>
      </article>
      <EmptyState
        v-if="!comments.length"
        description="暂无评论" kind="chat"
      />
      <div class="comment-composer">
        <el-alert v-if="reply" :title="`回复 ${reply}`" @close="reply = ''; form.parentCommentId = null" />
        <div class="comment-input-row"><el-popover v-model:visible="showCommentTools" trigger="click" placement="top-start" :width="260" popper-class="comment-attachment-popover"><template #reference><button type="button" class="comment-tool" aria-label="评论附件" :aria-expanded="showCommentTools"><el-icon><Plus /></el-icon></button></template><div class="comment-attachment-menu"><label class="attachment-choice"><el-icon><Picture /></el-icon>添加图片<input hidden type="file" multiple accept="image/*" @change="showCommentTools=false;perform(() => upload($event))" /></label><template v-if="kind==='community'"><label class="attachment-choice"><el-icon><Grid /></el-icon>关联配置</label><el-select v-model="form.buildConfigId" clearable placeholder="选择一套配置" @change="showCommentTools=false"><el-option v-for="b in builds" :key="b.id" :label="b.title" :value="b.id" /></el-select></template></div></el-popover><el-input v-model="form.content" type="textarea" :autosize="{minRows:1,maxRows:3}" maxlength="1000" :placeholder="reply ? '回复 '+reply : '说点什么…'" aria-label="评论内容" /><button class="comment-send" aria-label="发送评论" :disabled="busy || !form.content.trim()" @click="perform(send,'评论已发布')"><el-icon><ArrowRight /></el-icon></button></div>

        <div v-if="images.length" class="image-grid"><div v-for="(img,i) in images" :key="img"><el-image :src="img" fit="cover" /><el-button text @click="images.splice(i,1); form.imageKeys.splice(i,1)">移除</el-button></div></div>
      </div>
    </section>
  </div>
</template>

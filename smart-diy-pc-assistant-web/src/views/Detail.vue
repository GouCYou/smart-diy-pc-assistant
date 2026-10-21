<script setup lang="ts">
import { previewBuildId,openImages } from "../lib/previews";
import EmptyState from "../components/EmptyState.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowRight, Plus, Star, StarFilled, Picture, Grid, ChatLineRound } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { user, authenticated } from "../lib/session";
import { markdown } from "../lib/markdown";
import { useTask } from "../lib/task";
const props = defineProps<{ kind: string; embedded?:boolean; id?:number }>(),
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
const emit=defineEmits<{close:[];updated:[item:any];removed:[id:number]}>();
function close(){if(props.embedded)emit("close");else void router.push("/"+props.kind);}
const base = computed(() =>
  props.kind === "news"
    ? `/api/news/${props.id || route.params.id}`
    : `/api/community/posts/${props.id || route.params.id}`,
);
async function load() {
  item.value = await api.request(base.value);
  emit("updated",item.value);
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
  emit("updated",item.value);
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
  emit("removed",item.value.id);
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
          alt="资讯封面" @click="openImages([item.coverUrl])"
          class="cover" />
        <div class="markdown" @click="event=>{const el=event.target as HTMLElement;if(el.tagName==='IMG')openImages([(el as HTMLImageElement).src]);}" v-html="markdown(item.content)" /></template
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
            :preview-src-list="item.imageUrls" preview-teleported
            fit="cover"
          />
        </div>
        <button
          v-if="item.buildConfig"
          @click="previewBuildId=item.buildConfig.id"
          class="reference"
          >▦ {{ item.buildConfig.title }} · ¥{{
            item.buildConfig.totalPrice
          }}</button
        ></template
      >
      <div class="post-reactions"><button class="comment-reaction" :class="{liked:item.liked}" :aria-label="item.liked ? '取消点赞' : '点赞动态'" :aria-pressed="item.liked" @click="perform(()=>toggle('like'))"><span aria-hidden="true">♥</span>{{item.likeCount}}</button><button class="comment-reaction favorite-reaction" :class="{favorited:item.favorite}" :aria-label="item.favorite ? '取消收藏' : '收藏动态'" :aria-pressed="item.favorite" @click="perform(()=>toggle('favorite'))"><el-icon><StarFilled v-if="item.favorite"/><Star v-else/></el-icon>{{item.favoriteCount}}</button><button v-if="kind==='community' && user?.userId===item.author.id" class="comment-delete-button" @click="confirm('删除这条动态？',deletePost)">删除</button></div>
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
        <div class="comment-top"><router-link :to="`/users/${c.author.id}`" class="comment-person"><el-avatar :size="36" :src="c.author.avatarUrl" /><div><strong>{{ c.author.nickname || c.author.username }}</strong><time>{{c.createdAt?.replace('T',' ').slice(0,16)}}</time></div></router-link><div class="comment-reactions"><button class="comment-reply-button" @click="form.parentCommentId=c.id;reply=c.author.nickname || c.author.username"><el-icon><ChatLineRound /></el-icon>回复</button><button class="comment-reaction" :class="{liked:c.liked}" :aria-label="c.liked ? '取消评论点赞' : '点赞评论'" :aria-pressed="c.liked" @click="perform(()=>likeComment(c.id))"><span aria-hidden="true">♥</span>{{c.likeCount}}</button><button v-if="kind==='community' && c.author.id===user?.userId" class="comment-delete-button" @click="confirm('删除这条评论？',()=>deleteComment(c.id))">删除</button></div></div>
        <p v-if="c.parentCommentId" class="comment-reply-context">回复 {{c.replyTo?.nickname || c.replyTo?.username || '一条评论'}}</p>
        <p>{{ c.content }}</p><button v-if="c.buildConfig" @click="previewBuildId=c.buildConfig.id" class="reference">▦ {{c.buildConfig.title}}</button>
        <div class="image-grid">
          <el-image
            v-for="img in c.imageUrls"
            :key="img"
            :src="img"
            :preview-src-list="c.imageUrls" preview-teleported
            fit="cover"
          />
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

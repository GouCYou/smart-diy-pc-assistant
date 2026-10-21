<script setup lang="ts">
import NavigationToggle from "../components/NavigationToggle.vue";
import DialogHeading from "../components/DialogHeading.vue";
import MasonryFeed from "../components/MasonryFeed.vue";
import Detail from "./Detail.vue";
import EmptyState from "../components/EmptyState.vue";
import { computed, reactive, ref, watch, onMounted, onUnmounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Search, ChatDotRound, Star, Pointer, Close } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { authenticated } from "../lib/session";
import { useTask } from "../lib/task";
import type { Build } from "../../../shared/types";
const route = useRoute(), router=useRouter();
const detailOpen=computed(()=>!!route.params.id);
const desktop=ref(window.matchMedia("(min-width:761px)").matches);
const desktopMedia=window.matchMedia("(min-width:761px)");
function updateDesktop(){desktop.value=desktopMedia.matches;}
onMounted(()=>desktopMedia.addEventListener("change",updateDesktop));
onUnmounted(()=>desktopMedia.removeEventListener("change",updateDesktop));
function closeDetail(){void router.replace("/"+props.kind);}
function openPost(event:MouseEvent,id:number){if(event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;event.preventDefault();if(String(route.params.id)===String(id))closeDetail();else void router.push(`/${props.kind}/${id}`);}
function updatePost(item:any){rows.value=rows.value.map(r=>r.id===item.id ? {...r,...item} : r);}
function removePost(id:number){rows.value=rows.value.filter(r=>r.id!==id);}
const props = defineProps<{ kind: string }>(),
  { busy, error, perform } = useTask(),
  rows = ref<any[]>([]),
  sort = ref("latest"),
  query = ref(""),
  page = ref(1),
  open = ref(false),
  builds = ref<Build[]>([]),
  form = reactive({
    content: "",
    imageKeys: [] as string[],
    buildConfigId: null as number | null,
  }),
  images = ref<string[]>([]);
const workspaceElement=ref<HTMLElement>(),workspaceWidth=ref(1140);
let resizeObserver:ResizeObserver;
onMounted(()=>{resizeObserver=new ResizeObserver(entries=>{workspaceWidth.value=entries[0]!.contentRect.width;});if(workspaceElement.value)resizeObserver.observe(workspaceElement.value);});
onUnmounted(()=>resizeObserver?.disconnect());
const cardWidth=computed(()=>desktop.value ? Math.max(0,(workspaceWidth.value-72)/4) : workspaceWidth.value);
const columnCount=computed(()=>desktop.value ? detailOpen.value ? 2 : 4 : 1);
const filtered = computed(() =>
    rows.value.filter((r) =>
      `${r.title || ""} ${r.content || ""} ${r.summary || ""} ${r.tag || ""} ${r.author?.nickname || ""}`.includes(query.value),
    ),
  ),
  size = computed(() => props.kind === "news" ? 12 : 10),
  paginated = computed(() => props.kind === "news" || !desktop.value),
  visibleRows = computed(() => paginated.value ? filtered.value.slice((page.value-1)*size.value,page.value*size.value) : filtered.value);
watch([desktop,sort],()=>{page.value=1;});
watch(()=>filtered.value.length, count=>{page.value=Math.min(page.value,Math.max(1,Math.ceil(count/size.value)));});
async function load() {
  rows.value = await api.request(
    props.kind === "news"
      ? `/api/news?sort=${sort.value === "hot" ? "likes" : sort.value}`
      : `/api/community/posts?segment=${sort.value === "following" ? "following" : "recommended"}`,
  );
  if (props.kind === "community" && sort.value === "hot")
    rows.value.sort(
      (a, b) =>
        b.likeCount + b.commentCount * 2 - (a.likeCount + a.commentCount * 2),
    );
}
async function compose() {
  authenticated();
  builds.value = await api.request("/api/build-configs?segment=all");
  open.value = true;
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement;
  for (const file of Array.from(input.files || [])) {
    if (images.value.length >= 9) break;
    const d = await api.upload(file);
    form.imageKeys.push(d.key);
    images.value.push(d.url);
  }
  input.value = "";
}
async function publish() {
  if (!form.content.trim()) throw Error("请填写动态内容");
  await api.request("/api/community/posts", "POST", form);
  form.content = "";
  form.imageKeys = [];
  form.buildConfigId = null;
  images.value = [];
  open.value = false;
  await load();
}
watch(
  () => props.kind,
  () => {
    page.value = 1;
    perform(async () => { await load(); if (route.query.compose && props.kind === "community") await compose(); });
  },
  { immediate: true },
);
</script>
<template>
  <div ref="workspaceElement" class="social-workspace" :class="{'detail-open':desktop && detailOpen}"><section class="social-list-pane">
  <div class="page-heading feed-heading">
    <div class="page-title-controls"><NavigationToggle />
      <h1><span class="desktop-only">{{ kind === "news" ? "硬件资讯" : "装机社区" }}</span><span class="mobile-only">{{kind === 'news' ? '热点' : '发现'}}</span></h1>
    </div>
    <div class="feed-heading-actions"><el-input v-model="query" aria-label="搜索内容" :prefix-icon="Search" clearable :placeholder="kind === 'news' ? '搜索资讯' : '搜索动态、作者或配置'" @input="page = 1" /><el-button v-if="kind === 'community'" type="primary" @click="perform(compose)">发动态</el-button></div>
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <div class="feed-toolbar">
    <div v-if="kind === 'community'" v-slider class="segmented feed-segments">
      <button :class="{selected:sort === 'latest'}" @click="sort = 'latest'; perform(load)">推荐</button>
      <button :class="{selected:sort === 'following'}" @click="sort = 'following'; perform(load)">我的关注</button>
      <button :class="{selected:sort === 'hot'}" @click="sort = 'hot'; perform(load)">热门</button>
    </div>
    <div v-if="kind === 'news'" v-slider class="segmented feed-segments"><button :class="{selected:sort==='latest'}" @click="sort='latest';perform(load)">最新</button><button :class="{selected:sort==='hot'}" @click="sort='hot';perform(load)">热度</button></div>
  </div>
  <MasonryFeed :class="[kind === 'news' ? 'news-feed' : 'community-feed']" :items="visibleRows" :columns="columnCount" :card-width="cardWidth" :gap="24" v-loading="busy"><template #default="{item:r}">
      <a :href="`/${kind}/${r.id}`" @click="event=>openPost(event,r.id)"
        ><img
          v-if="r.coverUrl"
          class="cover"
          :src="r.coverUrl"
          :alt="r.title"
        />
        <div class="body">
          <div v-if="kind==='community' && r.imageUrls?.length" class="post-cover"><img :src="r.imageUrls[0]" alt="动态配图" loading="lazy" /><span v-if="r.imageUrls.length>1">{{r.imageUrls.length}} 张</span></div>
          <template v-if="kind === 'news'"
            ><span class="tag">{{ r.tag || "硬件资讯" }}</span>
            <h2>{{ r.title }}</h2>
            <p>{{ r.summary }}</p></template
          ><template v-else
            ><div class="post-author">
              <el-avatar :src="r.author.avatarUrl">{{
                (r.author.nickname || r.author.username).slice(0, 1)
              }}</el-avatar
              ><div><strong>{{ r.author.nickname || r.author.username }}</strong><small class="author-time">{{r.createdAt?.slice(0,10)}}</small></div>
            </div>
            <p class="content-text" style="color: #45516a">
              {{ r.content }}
            </p>
            <span v-if="r.buildConfig" class="tag"
              >▦ {{ r.buildConfig.title }}</span
            ></template
          >
          <div class="card-meta">
            <span>{{ r.createdAt?.replace("T", " ").slice(0, 10) }}</span
            ><span class="feed-counts"><span><el-icon><Pointer /></el-icon>{{r.likeCount}}</span><span :class="{'is-favorited':r.favorite}"><el-icon><Star /></el-icon>{{r.favoriteCount}}</span><span><el-icon><ChatDotRound /></el-icon>{{r.commentCount}}</span></span>
          </div>
        </div></a
      >
    </template></MasonryFeed>
  <EmptyState
    v-if="!filtered.length && !busy"
    description="还没有相关内容"
  /><el-pagination
    v-if="paginated && filtered.length > size"
    v-model:current-page="page"
    :total="filtered.length"
    :page-size="size"
    layout="prev,pager,next"
  /></section><aside class="social-detail-pane" :aria-hidden="!detailOpen || !desktop" :inert="!detailOpen || !desktop"><div class="social-detail-panel"><header class="social-detail-header"><strong>{{kind==='news' ? '硬件资讯' : '动态详情'}}</strong><button class="chat-icon-button" aria-label="关闭详情" @click="closeDetail"><el-icon><Close /></el-icon></button></header><div class="social-detail-body"><Transition name="detail-content" mode="out-in"><Detail v-if="desktop && detailOpen" :key="String(route.params.id)" :kind="kind" :embedded="true" @updated="updatePost" @removed="removePost" @close="closeDetail" /></Transition></div></div></aside></div>
  <el-drawer v-if="!desktop" :model-value="detailOpen" direction="rtl" size="100%" class="social-detail-drawer" :title="kind==='news' ? '硬件资讯' : '动态详情'" destroy-on-close @close="closeDetail"><Detail v-if="detailOpen" :key="String(route.params.id)" :kind="kind" :embedded="true" @updated="updatePost" @removed="removePost" @close="closeDetail" /></el-drawer><el-dialog v-model="open" title="发布动态" width="720px" class="mobile-full-dialog compose-dialog" :show-close="false"
    ><template #header><DialogHeading title="发布动态" @close="open = false" /></template><el-input
      v-model="form.content"
      type="textarea"
      :rows="7"
      maxlength="2000"
      show-word-limit
      placeholder="聊聊你的配置、体验或装机心得…"
    />
    <div class="image-grid">
      <div v-for="(img, i) in images" :key="img">
        <el-image :src="img" fit="cover" /><el-button
          text
          type="danger"
          @click="
            images.splice(i, 1);
            form.imageKeys.splice(i, 1);
          "
          >移除</el-button
        >
      </div>
    </div>
    <div class="toolbar">
      <label class="file-button"
        ><input
          hidden
          type="file"
          multiple
          accept="image/*"
          @change="perform(() => upload($event))"
        />添加图片（最多 9 张）</label
      >
    </div>
    <el-select
      v-model="form.buildConfigId"
      clearable
      placeholder="关联一套配置（可选）"
      ><el-option
        v-for="b in builds"
        :key="b.id"
        :label="b.title"
        :value="b.id" /></el-select
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        @click="perform(publish, '动态已发布')"
        >发动态</el-button
      ></template
    ></el-dialog
  >
</template>

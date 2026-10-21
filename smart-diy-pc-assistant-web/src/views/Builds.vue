<script setup lang="ts">
import EmptyState from "../components/EmptyState.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { api } from "../lib/api";
import { user, authenticated } from "../lib/session";
import { useTask } from "../lib/task";
import {
  hardwareTypes,
  type Build,
  type Hardware,
} from "../../../shared/types";
const route = useRoute(),
  router = useRouter(),
  { busy, error, perform, confirm } = useTask(),
  rows = ref<Build[]>([]),
  hardware = ref<Hardware[]>([]),
  segment = ref("mine"),
  detail = ref<Build | null>(null),
  open = ref(false),
  validation = ref<any>(null),
  form = reactive<any>({}),
  chosen = reactive<Record<string, number | null>>({}),
  prices = reactive<Record<string, number>>({});
const title = computed(() =>
    segment.value === "favorite" ? "收藏配置" : "我的配置",
  ),
  total = computed(() =>
    Object.values(prices).reduce((a, b) => a + (Number(b) || 0), 0),
  );
async function load() {
  if (user.value)
    rows.value = await api.request(
      `/api/build-configs?segment=${segment.value}`,
    );
}
async function edit(build?: Build) {
  authenticated();
  hardware.value = await api.request("/api/hardware-parts");
  Object.keys(chosen).forEach((k) => delete chosen[k]);
  Object.keys(prices).forEach((k) => delete prices[k]);
  Object.assign(form, {
    id: build?.id,
    title: build?.title || "",
    note: build?.note || "",
    source: build?.source || "MANUAL",
  });
  for (const p of build?.parts || []) {
    chosen[p.type] = p.id;
    prices[p.type] = p.productPrice || 0;
  }
  validation.value = null;
  open.value = true;
}
function payload() {
  const ids = Object.values(chosen).filter((id): id is number => !!id);
  return {
    ...form,
    partIds: ids,
    partSelections: ids.map((id) => {
      const p = hardware.value.find((p) => p.id === id)!;
      const old = detail.value?.parts.find((x) => x.id === id);
      return {
        hardwarePartId: id,
        title: old?.productTitle || p.model,
        price: prices[p.type] || 0,
        image: old?.productImage || "",
        link: old?.productLink || "",
        skuId: old?.skuId || "",
        matchedModel: p.model,
      };
    }),
  };
}
async function validate() {
  validation.value = await api.request("/api/build-configs/validate", "POST", {
    partIds: payload().partIds,
  });
}
async function save() {
  if (!form.title.trim()) throw Error("请填写配置标题");
  if (!payload().partIds.length) throw Error("请至少选择一个硬件");
  await validate();
  if (validation.value.status === "BLOCK") throw Error("请先解决兼容性问题");
  detail.value = await api.request(
    `/api/build-configs${form.id ? "/" + form.id : ""}`,
    form.id ? "PUT" : "POST",
    payload(),
  );
  open.value = false;
  await load();
}
async function toggleFavorite() {
  authenticated();
  detail.value = await api.request(
    `/api/build-configs/${detail.value!.id}/favorite`,
    "PATCH",
    { favorite: !detail.value!.favorite },
  );
  await load();
}
async function deleteBuild() {
  await api.request(`/api/build-configs/${detail.value!.id}`, "DELETE");
  detail.value = null;
  await load();
}
async function share(build: Build) {
  const link = `${location.origin}/builds?public=${build.id}`;
  try {
    await navigator.clipboard.writeText(link);
    ElMessage.success("配置链接已复制");
  } catch {
    ElMessage.info(link);
  }
}
onMounted(() =>
  perform(async () => {
    await load();
    if (route.query.create) await edit();
    if (route.query.public || route.query.id)
      detail.value = await api.request(
        `/api/build-configs/public/${route.query.public || route.query.id}`,
      );
  }),
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1><span class="desktop-only">{{title}}</span><span class="mobile-only">配置</span></h1>
    </div>
    <el-button type="primary" @click="perform(() => edit())"
      >新建</el-button
    >
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section v-if="!user && !detail" class="panel empty-auth">
    <h2>把你的方案保存下来</h2>
    <p>登录后创建配置，或收藏值得参考的搭配。</p>
    <router-link to="/auth"
      ><el-button type="primary">登录 / 注册</el-button></router-link
    >
  </section>
  <template v-else
    ><div v-slider class="segmented build-segments">
      <button :class="{selected:segment === 'mine'}" @click="segment = 'mine'; perform(load)">自定义</button>
      <button :class="{selected:segment === 'ai'}" @click="segment = 'ai'; perform(load)">AI 生成</button>
      <button :class="{selected:segment === 'favorite'}" @click="segment = 'favorite'; perform(load)">收藏</button>
      <button class="desktop-only" :class="{selected:segment === 'all'}" @click="segment = 'all'; perform(load)">全部</button>
    </div>
    <div class="feed-grid">
      <article v-for="b in rows" :key="b.id" class="card">
        <div class="row" style="justify-content: space-between">
          <span class="tag">{{
            b.source === "AI" ? "AI 方案" : "手动搭配"
          }}</span
          ><span class="muted">{{ b.serialNumber }}</span>
        </div>
        <h2>{{ b.title }}</h2>
        <p class="muted">
          {{
            b.parts
              .map((p) => p.model)
              .slice(0, 3)
              .join(" / ")
          }}
        </p>
        <div class="card-meta">
          <strong class="price"
            >已报价 ¥{{ b.totalPrice.toLocaleString() }}</strong
          ><el-button @click="detail = b">查看配置 →</el-button>
        </div>
      </article>
    </div>
    <EmptyState
      v-if="!rows.length"
      :description="segment === 'ai' ? '还没有 AI 生成记录' : segment === 'favorite' ? '还没有收藏配置' : '还没有配置'" /></template
  ><el-dialog
    :model-value="!!detail"
    :title="detail?.title"
    width="820px"
    class="mobile-full-dialog"
    @close="detail = null"
    ><template v-if="detail"
      ><p class="muted">{{ detail.note }}</p>
      <div v-for="p in detail.parts" :key="p.id" class="draft-part">
        <div>
          <small>{{ hardwareTypes[p.type] }}</small
          ><strong>{{ p.model }}</strong
          ><a
            v-if="p.productLink && /^https:\/\//.test(p.productLink)"
            :href="p.productLink"
            target="_blank"
            rel="noopener noreferrer"
            style="display: block; color: #4966e8; margin-top: 6px"
            >查看商品 ↗</a
          >
        </div>
        <span>{{ p.productPrice ? "¥" + p.productPrice : "待询价" }}</span>
      </div>
      <p class="price">
        已报价合计 ¥{{ detail.totalPrice }}（未报价部件需另行核实）
      </p>
      <el-alert
        v-for="m in detail.compatibilityMessages"
        :key="m"
        :title="m"
        :type="detail.compatibilityStatus === 'BLOCK' ? 'error' : 'info'"
        :closable="false"
      />
      <div class="action-row">
        <el-button
          type="primary"
          @click="router.push({ path: '/', query: { build: detail.id } })"
          >让 AI 评审</el-button
        ><el-button
          v-if="detail.ownerUserId === user?.userId"
          @click="perform(() => edit(detail!))"
          >编辑配置</el-button
        ><el-button @click="perform(toggleFavorite)">{{
          detail.favorite ? "取消收藏" : "收藏"
        }}</el-button
        ><el-button @click="share(detail)">分享</el-button
        ><el-button
          v-if="detail.ownerUserId === user?.userId"
          type="danger"
          plain
          @click="confirm('删除这套配置？', deleteBuild)"
          >删除</el-button
        >
      </div></template
    ></el-dialog
  ><el-dialog
    v-model="open"
    :title="form.id ? '编辑配置' : '创建配置'"
    width="880px"
    class="build-editor-dialog"
    ><div class="build-live-total"><span>已录入报价</span><strong>¥{{total.toLocaleString()}}</strong></div>
    <el-input v-model="form.title" maxlength="80" placeholder="配置名称" aria-label="配置名称" class="build-title-input" />
    <div class="hardware-edit-list">
      <div v-for="(label,kind) in hardwareTypes" :key="kind" class="hardware-edit-row">
        <strong>{{label}}</strong>
        <div><el-select v-model="chosen[kind]" :aria-label="label" clearable filterable placeholder="选择型号" @change="validation = null; prices[kind] = 0"><el-option v-for="h in hardware.filter(h => h.type === kind)" :key="h.id" :label="h.model" :value="h.id" /></el-select><el-input-number v-model="prices[kind]" :aria-label="label+'报价（元）'" :min="0" :precision="2" :controls="false" :disabled="!chosen[kind]" placeholder="待询价" /></div>
      </div>
    </div>
    <el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" placeholder="需求与备注（可选）" aria-label="需求与备注" />
    <el-alert
      v-for="m in validation?.messages || []"
      :key="m"
      :title="m"
      :type="validation.status === 'BLOCK' ? 'error' : 'info'"
    /><template #footer
      ><el-button @click="perform(validate)">检查兼容性</el-button
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        @click="perform(save, '配置已保存')"
        >保存配置</el-button
      ></template
    ></el-dialog
  >
</template>

<script setup lang="ts">
import ProductParts from "../components/ProductParts.vue";
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
  prices = reactive<Record<string, number>>({}),
  selections = reactive<Record<string,any>>({}),
  productPicker = ref(false), productLoading=ref(false), productOptions=ref<any[]>([]), productWarning=ref(''), productKind=ref('');
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
  Object.keys(selections).forEach(k=>delete selections[k]);
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
    selections[p.type]={title:p.productTitle || p.model,image:p.productImage,link:p.productLink,skuId:p.skuId};
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
      const old = selections[p.type];
      return {
        hardwarePartId: id,
        title: old?.title || p.model,
        price: prices[p.type] || 0,
        image: old?.image || "",
        link: old?.link || "",
        skuId: old?.skuId || "",
        matchedModel: p.model,
      };
    }),
  };
}
async function pickProducts(kind:string){
  if(!chosen[kind])return;
  productKind.value=kind;productPicker.value=true;productLoading.value=true;productOptions.value=[];productWarning.value='';
  try{const result=await api.request(`/agent/products/search?hardware_id=${chosen[kind]}`);productOptions.value=result.products;productWarning.value=result.warning;}catch(e){productWarning.value=(e as Error).message;}finally{productLoading.value=false;}
}
function selectProduct(p:any){selections[productKind.value]=p;prices[productKind.value]=p.price || 0;productPicker.value=false;validation.value=null;}
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
      <article v-for="b in rows" :key="b.id" class="card build-preview-card">
        <div class="row" style="justify-content: space-between">
          <span class="tag">{{
            b.source === "AI" ? "AI 方案" : "手动搭配"
          }}</span
          ><span class="muted">{{ b.serialNumber }}</span>
        </div>
        <h2>{{ b.title }}</h2>
        <div class="build-score-chip" v-if="b.performanceScore">综合性能 <b>{{b.performanceScore}} 分</b></div>
        <ProductParts :parts="b.parts.slice(0,3)" compact />
        <button class="build-more-parts" @click="detail=b">查看全部 {{b.parts.length}} 件配件 →</button>
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
    class="build-detail-dialog mobile-full-dialog"
    @close="detail = null"
    ><template v-if="detail"
      ><p class="muted">{{ detail.note }}</p>
      <div class="build-detail-overview"><span>{{detail.serialNumber}}</span><strong>¥{{detail.totalPrice.toLocaleString()}}</strong><span>已报价合计</span><span v-if="detail.performanceScore" class="build-score-chip">综合性能 <b>{{detail.performanceScore}} 分</b></span></div>
      <ProductParts :parts="detail.parts" />

      <details class="compatibility-details"><summary>兼容性与待核实事项<span class="compatibility-badge">{{detail.compatibilityStatus==='BLOCK' ? '需要调整' : '查看检查结果'}}</span></summary><ul><li v-for="m in detail.compatibilityMessages" :key="m">{{m}}</li></ul></details>
      </template><template #footer><div v-if="detail" class="action-row build-detail-actions">

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
        <div><el-select v-model="chosen[kind]" :aria-label="label" clearable filterable placeholder="选择型号" @change="validation = null; prices[kind] = 0; delete selections[kind]"><el-option v-for="h in hardware.filter(h => h.type === kind)" :key="h.id" :label="h.model" :value="h.id" /></el-select><el-input-number v-model="prices[kind]" :aria-label="label+'报价（元）'" :min="0" :precision="2" :controls="false" :disabled="!chosen[kind]" placeholder="待询价" /><el-button :disabled="!chosen[kind]" @click="pickProducts(kind)">选择京东商品</el-button><span v-if="selections[kind]?.title" class="editor-selected-product">{{selections[kind].title}}</span></div>
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
<el-dialog v-model="productPicker" title="选择京东商品" width="760px" class="product-picker-dialog"><div v-if="productLoading" class="product-loading"><span class="execution-spinner" />正在查询商品…</div><el-alert v-if="productWarning" :title="productWarning" type="info" :closable="false" /><div class="product-picker-list"><button v-for="p in productOptions" :key="p.skuId" class="product-picker-option" @click="selectProduct(p)"><el-image v-if="p.image" :src="p.image" fit="contain" /><div><strong>{{p.title}}</strong><p>{{p.shopName}}</p><b>{{p.price ? '¥'+p.price : '待询价'}}</b></div><span>选择</span></button></div><p class="muted">报价以商品页面实际结算为准。</p></el-dialog>
</template>

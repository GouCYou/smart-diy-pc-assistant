<script setup lang="ts">
import AnimatedDisclosure from "./AnimatedDisclosure.vue";
import { computed, ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../lib/api";
import {draftSaveIds} from "../lib/previews";
import ProductParts from "./ProductParts.vue";
import { type Draft } from "../../../shared/types";
const props = defineProps<{ draft: Draft; readOnly?:boolean }>(),
  busy = ref(false),
  saved = ref(draftSaveIds.get(props.draft) || 0);
const parts = computed(()=>props.draft.parts.map(p=>{const q=props.draft.partSelections?.find(q=>q.hardwarePartId===p.id);return {...p,selectedPrice:q?.price ?? p.selectedPrice,priceType:q?.priceType ?? p.priceType,priceLabel:q?.priceLabel ?? p.priceLabel,priceConditions:q?.priceConditions ?? p.priceConditions,productTitle:q?.title || p.productTitle,productImage:q?.image || p.productImage,productLink:q?.link || p.productLink};}));
async function save() {
  if (busy.value || saved.value) return;
  busy.value = true;
  try {
    const d = await api.request("/api/build-configs", "POST", props.draft);
    saved.value = d.id;
    draftSaveIds.set(props.draft,d.id);
    ElMessage.success("配置已保存到我的配置");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="configuration-card">
    <header class="configuration-heading"><div><span class="configuration-eyebrow">装机方案</span><h2>{{draft.title}}</h2></div><div class="configuration-total"><span>已报价合计</span><strong>¥{{draft.validation.quotedTotal.toLocaleString()}}</strong></div></header>
    <ProductParts :parts="parts" />
    <AnimatedDisclosure class="compatibility-details"><template #summary><span>兼容性与待核实事项</span><span class="compatibility-badge">{{draft.validation.warnings.length ? draft.validation.warnings.length+' 项待核实' : '检查完成'}}</span></template><ul><li v-for="w in draft.validation.warnings" :key="w">{{w}}</li><li v-if="!draft.validation.warnings.length">当前已检查的参数未发现冲突。</li></ul></AnimatedDisclosure>
    <footer class="configuration-footer"><p v-if="draft.validation.unpricedParts.length">{{draft.validation.unpricedParts.length}} 项部件未取得有效报价，合计暂不完整。</p><p v-else>报价以商品页面实际结算为准。</p><el-button v-if="!readOnly" type="primary" round :loading="busy" :disabled="!!saved" @click="save">{{saved ? '已保存到我的配置' : '保存这套配置'}}</el-button><router-link v-if="saved" :to="`/builds?id=${saved}`">查看配置 →</router-link></footer>
  </section>
</template>

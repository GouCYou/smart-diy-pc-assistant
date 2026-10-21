<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { api } from "../lib/api";
import { hardwareTypes, type Draft } from "../../../shared/types";
const props = defineProps<{ draft: Draft }>(),
  busy = ref(false),
  saved = ref(0);
async function save() {
  if (busy.value || saved.value) return;
  busy.value = true;
  try {
    const d = await api.request("/api/build-configs", "POST", props.draft);
    saved.value = d.id;
    ElMessage.success("配置已保存到我的配置");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="run-panel">
    <div class="row" style="justify-content: space-between">
      <strong>{{ draft.title }}</strong
      ><span class="price"
        >已报价 ¥{{ draft.validation.quotedTotal.toLocaleString() }}</span
      >
    </div>
    <div class="draft-list">
      <div v-for="p in draft.parts" :key="p.id" class="draft-part">
        <div>
          <small>{{ hardwareTypes[p.type] }}</small
          ><strong>{{ p.model }}</strong>
        </div>
        <span class="price">{{
          p.selectedPrice ? "¥" + p.selectedPrice : "待询价"
        }}</span>
      </div>
    </div>
    <el-collapse style="margin-top: 15px"
      ><el-collapse-item title="查看兼容性与待核实事项"
        ><p
          v-for="w in draft.validation.warnings"
          :key="w"
          class="muted"
          style="font-size: 14px"
        >
          {{ w }}
        </p></el-collapse-item
      ></el-collapse
    >
    <div class="action-row">
      <el-button
        type="primary"
        :loading="busy"
        :disabled="!!saved"
        @click="save"
        >{{ saved ? "已保存" : "保存这套配置" }}</el-button
      ><router-link v-if="saved" :to="`/builds?id=${saved}`"
        >查看配置 →</router-link
      >
    </div>
  </section>
</template>

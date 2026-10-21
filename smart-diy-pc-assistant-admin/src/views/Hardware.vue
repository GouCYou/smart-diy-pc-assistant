<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
import { hardwareTypes, type Hardware } from "../../../shared/types";
const { busy, error, perform, confirm } = useTask(),
  rows = ref<Hardware[]>([]),
  query = ref(""),
  kind = ref(""),
  page = ref(1),
  open = ref(false),
  form = reactive<any>({});
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (!kind.value || r.type === kind.value) &&
      r.model.toLowerCase().includes(query.value.toLowerCase()),
  ),
);
async function load() {
  rows.value = await api.request("/api/admin/hardware-parts");
}
function edit(row?: Hardware) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    row || {
      type: "CPU",
      model: "",
      brand: "",
      price: 0,
      performanceScore: 0,
      socketType: "",
      memoryType: "",
      formFactor: "",
      wattage: 0,
      gpuLengthMm: 0,
      coolerHeightMm: 0,
      supportedFormFactors: "",
      specs: "",
      enabled: true,
    },
  );
  open.value = true;
}
async function save() {
  if (!form.model.trim()) throw Error("请输入标准型号");
  await api.request(
    `/api/admin/hardware-parts${form.id ? "/" + form.id : ""}`,
    form.id ? "PUT" : "POST",
    form,
  );
  open.value = false;
  await load();
}
onMounted(() => perform(load));
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>硬件目录</h1>
      <p>维护标准型号与完整规格，为 Agent 选型提供可靠数据。</p>
    </div>
    <el-button type="primary" @click="edit()">新增硬件</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="query"
        clearable
        placeholder="搜索标准型号"
        @input="page = 1"
      /><el-select
        v-model="kind"
        clearable
        placeholder="全部分类"
        @change="page = 1"
        ><el-option
          v-for="(label, key) in hardwareTypes"
          :key="key"
          :label="label"
          :value="key" /></el-select
      ><el-button @click="perform(load)">刷新</el-button>
    </div>
    <el-table
      :data="filtered.slice((page - 1) * 12, page * 12)"
      v-loading="busy"
      ><el-table-column label="分类" width="100"
        ><template #default="{ row }">{{
          hardwareTypes[row.type]
        }}</template></el-table-column
      ><el-table-column
        prop="model"
        label="标准型号"
        min-width="240"
      /><el-table-column label="兼容参数" min-width="220"
        ><template #default="{ row }">{{
          [row.socketType, row.memoryType, row.formFactor]
            .filter(Boolean)
            .join(" · ") || "待补充"
        }}</template></el-table-column
      ><el-table-column label="功率" width="100"
        ><template #default="{ row }"
          >{{ row.wattage || "—" }} W</template
        ></el-table-column
      ><el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="row.enabled ? 'success' : 'info'">{{
            row.enabled ? "启用" : "停用"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作" width="145" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="edit(row as Hardware)">编辑</el-button
          ><el-button
            link
            type="danger"
            @click="
              confirm(
                '删除该型号？已保存配置可能受影响，建议优先停用。',
                async () => {
                  await api.request(
                    `/api/admin/hardware-parts/${row.id}`,
                    'DELETE',
                  );
                  await load();
                },
              )
            "
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :total="filtered.length"
      :page-size="12"
      layout="total, prev, pager, next"
    />
  </section>
  <el-dialog
    v-model="open"
    :title="form.id ? '编辑硬件' : '新增硬件'"
    width="760px"
    ><el-form label-position="top" class="form-grid"
      ><el-form-item label="分类"
        ><el-select v-model="form.type"
          ><el-option
            v-for="(label, key) in hardwareTypes"
            :key="key"
            :value="key"
            :label="label" /></el-select></el-form-item
      ><el-form-item label="标准型号" required
        ><el-input v-model="form.model" maxlength="120" /></el-form-item
      ><el-form-item label="CPU 插槽"
        ><el-input
          v-model="form.socketType"
          placeholder="如 AM5 / LGA1700" /></el-form-item
      ><el-form-item label="内存类型"
        ><el-input
          v-model="form.memoryType"
          placeholder="如 DDR5" /></el-form-item
      ><el-form-item label="版型"
        ><el-input
          v-model="form.formFactor"
          placeholder="如 ATX / MATX" /></el-form-item
      ><el-form-item label="支持主板版型（机箱）"
        ><el-input
          v-model="form.supportedFormFactors"
          placeholder="ATX,MATX,ITX" /></el-form-item
      ><el-form-item label="功耗 / 电源额定功率（W）"
        ><el-input-number
          v-model="form.wattage"
          :min="0"
          :max="3000" /></el-form-item
      ><el-form-item label="性能参考分"
        ><el-input-number
          v-model="form.performanceScore"
          :min="0" /></el-form-item
      ><el-form-item label="显卡长度 / 机箱显卡限长（mm）"
        ><el-input-number v-model="form.gpuLengthMm" :min="0" /></el-form-item
      ><el-form-item label="散热器高度 / 机箱散热限高（mm）"
        ><el-input-number
          v-model="form.coolerHeightMm"
          :min="0" /></el-form-item
      ><el-form-item label="规格与备注" class="wide"
        ><el-input
          v-model="form.specs"
          type="textarea"
          :rows="4" /></el-form-item
      ><el-form-item label="可供 Agent 和用户选择"
        ><el-switch v-model="form.enabled" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        @click="perform(save, '硬件已保存')"
        >保存硬件</el-button
      ></template
    ></el-dialog
  >
</template>

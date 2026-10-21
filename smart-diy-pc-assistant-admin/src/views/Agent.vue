<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
import { hardwareTypes, runStatuses } from "../../../shared/types";
const props = defineProps<{ kind: string }>(),
  { busy, error, perform, confirm } = useTask(),
  form = reactive<any>({}),
  docs = ref<any[]>([]),
  runs = ref<any[]>([]),
  events = ref<any[]>([]),
  showEvents = ref(false),
  open = ref(false),
  doc = reactive({ title: "", content: "", source: "" }),
  url = ref(""),
  query = ref(""),
  results = ref<any[]>([]),
  status = ref(""),
  productKeyword = ref("Ryzen 5 9600X"),
  productType = ref("CPU"),
  productResult = ref<any>(null);
async function searchProducts() {
  productResult.value = null;
  productResult.value = await api.request(`/agent/admin/products/search?keyword=${encodeURIComponent(productKeyword.value)}&part_type=${productType.value}`);
}
const filtered = computed(() =>
  runs.value.filter((r) => !status.value || r.status === status.value),
);
async function load() {
  if (props.kind === "agent") {
    Object.assign(form, await api.request("/agent/admin/settings"));
    form.api_key = "";
    form.clear_api_key = false;
  } else if (props.kind === "knowledge")
    docs.value = await api.request("/agent/admin/documents");
  else runs.value = await api.request("/agent/admin/runs");
}
async function save() {
  await api.request("/agent/admin/settings", "PUT", form);
  await load();
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  if (!file) return;
  await perform(async () => {
    const f = new FormData();
    f.append("file", file);
    await api.request("/agent/admin/documents/upload", "POST", f);
    await load();
  }, "文档导入成功");
  input.value = "";
}
watch(
  () => props.kind,
  () => {
    perform(load);
  },
  { immediate: true },
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>
        {{
          kind === "agent"
            ? "Agent 配置"
            : kind === "knowledge"
              ? "知识库"
              : "运行记录"
        }}
      </h1>
      <p>
        {{
          kind === "agent"
            ? "配置模型、执行额度与回答要求。"
            : kind === "knowledge"
              ? "导入可信资料，验证检索结果和知识来源。"
              : "查看任务状态与工具执行过程，定位失败原因。"
        }}
      </p>
    </div>
    <div class="row"><router-link v-if="kind === 'knowledge'" to="/assistant"><el-button>模型与检索配置</el-button></router-link><el-button :loading="busy" @click="perform(load)">刷新</el-button></div>
  </div>
  <el-alert v-if="error" :title="error" type="error" show-icon />
  <template v-if="kind === 'agent'">
  <section class="panel" style="max-width: 1000px">
    <el-form label-position="top" class="form-grid"
      ><el-form-item label="模型接口地址" required class="wide"
        ><el-input
          v-model="form.base_url"
          placeholder="https://模型接口/v1" /></el-form-item
      ><el-form-item label="模型名称" required
        ><el-input
          v-model="form.model"
          placeholder="填写支持工具调用的模型 ID" /></el-form-item
      ><el-form-item
        :label="
          form.api_key_configured ? 'API Key（已配置，留空保持）' : 'API Key'
        "
        ><el-input
          v-model="form.api_key"
          type="password"
          show-password
          autocomplete="new-password" /></el-form-item
      ><el-form-item label="Embedding 模型（可选）"
        ><el-input
          v-model="form.embedding_model"
          placeholder="留空使用关键词检索" /></el-form-item
      ><el-form-item label="清除已保存的 API Key"
        ><el-switch v-model="form.clear_api_key" /></el-form-item
      ><el-form-item
        v-for="[key, label, min, max] in [
          ['timeout_seconds', '模型请求超时（秒）', 1800, 7200],
          ['max_steps', '执行步骤上限', 4, 100],
          ['max_tool_calls', '工具调用上限', 2, 60],
          ['max_output_tokens', '回答 Token 上限', 256, 16384],
          ['max_run_seconds', '单次任务时限（秒）', 1800, 14400],
          ['rag_chunk_size', 'RAG 分块长度（字符）', 200, 4000],
          ['rag_chunk_overlap', 'RAG 分块重叠（字符）', 0, 1000],
          ['rag_top_k', 'RAG 返回片段数', 1, 20],
        ]"
        :key="String(key)"
        :label="String(label)"
        ><el-input-number
          v-model="form[String(key)]"
          :min="Number(min)"
          :max="Number(max)" /></el-form-item
      ><el-form-item label="向量相似度阈值"><el-input-number v-model="form.rag_similarity_threshold" :min="0" :max="1" :step="0.05" :precision="2" /></el-form-item><el-form-item label="补充回答要求" class="wide"
        ><el-input
          v-model="form.extra_prompt"
          type="textarea"
          :rows="5"
          placeholder="例如：优先解释取舍，并注明资料日期" /></el-form-item></el-form
    ><el-alert
      title="更改分块参数或 Embedding 模型后，请重新索引知识库文档。模型需支持工具调用和流式回答。"
      type="info"
      :closable="false"
    /><el-button
      type="primary"
      :loading="busy"
      @click="perform(save, 'Agent 配置已保存')"
      >保存配置</el-button
    >
  </section>
  <section class="panel">
    <h2>京东商品抓取</h2>
    <div class="toolbar"><el-select v-model="productType" style="max-width:160px"><el-option v-for="(label,value) in hardwareTypes" :key="value" :label="label" :value="value" /></el-select><el-input v-model="productKeyword" placeholder="输入硬件型号" maxlength="120" @keyup.enter="perform(searchProducts)" /><el-button type="primary" :disabled="productKeyword.trim().length < 2" :loading="busy" @click="perform(searchProducts)">查询京东</el-button></div>
    <template v-if="productResult">
      <el-alert v-if="productResult.warning" :title="productResult.warning" type="warning" :closable="false" />
      <el-table :data="productResult.products"><el-table-column label="商品" min-width="320"><template #default="{row}"><a :href="row.link" target="_blank" rel="noopener noreferrer">{{row.title}} ↗</a></template></el-table-column><el-table-column label="页面价格" width="130"><template #default="{row}">{{row.price === null ? '待核实' : '¥'+row.price}}</template></el-table-column><el-table-column prop="shopName" label="店铺" min-width="180" /><el-table-column label="抓取时间" width="180"><template #default="{row}">{{new Date(row.capturedAt).toLocaleString()}}</template></el-table-column></el-table>
      <el-empty v-if="!productResult.products.length" description="未返回有效商品" />
    </template>
  </section>
  </template>
  <template v-else-if="kind === 'knowledge'"
    ><section class="panel">
      <div class="toolbar">
        <el-button
          type="primary"
          @click="
            Object.assign(doc, { title: '', content: '', source: '' });
            open = true;
          "
          >新增文档</el-button
        ><label class="el-button"
          ><input
            hidden
            type="file"
            accept=".txt,.md,.pdf"
            @change="upload"
          />上传文件</label
        ><span class="muted">PDF / Markdown / TXT，最大 5MB</span>
      </div>
      <div class="toolbar">
        <el-input v-model="url" placeholder="公开 HTTPS 资料网址" /><el-button
          :disabled="!url.trim()"
          :loading="busy"
          @click="
            perform(async () => {
              await api.request('/agent/admin/documents/url', 'POST', { url });
              url = '';
              await load();
            }, '网址导入成功')
          "
          >导入网址</el-button
        >
      </div>
      <el-table :data="docs" v-loading="busy"
        ><el-table-column
          prop="title"
          label="文档"
          min-width="220"
        /><el-table-column
          prop="source"
          label="来源"
          min-width="220"
          show-overflow-tooltip
        /><el-table-column
          prop="chunk_count"
          label="分块"
          width="90"
        /><el-table-column label="操作" width="160"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              @click="
                perform(async () => {
                  await api.request(
                    `/agent/admin/documents/${row.id}/reindex`,
                    'POST',
                  );
                  await load();
                }, '索引已更新')
              "
              >重建索引</el-button
            ><el-button
              link
              type="danger"
              @click="
                confirm('删除文档及所有检索片段？', async () => {
                  await api.request(
                    `/agent/admin/documents/${row.id}`,
                    'DELETE',
                  );
                  await load();
                })
              "
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
    <section class="panel">
      <h2>检索预览</h2>
      <div class="toolbar">
        <el-input
          v-model="query"
          placeholder="输入一个用户可能提出的问题"
          @keyup.enter="
            perform(async () => {
              results = await api.request(
                '/agent/admin/search?q=' + encodeURIComponent(query),
              );
            })
          "
        /><el-button
          :disabled="!query.trim()"
          @click="
            perform(async () => {
              results = await api.request(
                '/agent/admin/search?q=' + encodeURIComponent(query),
              );
            })
          "
          >测试检索</el-button
        >
      </div>
      <article v-for="r in results" :key="r.id" class="panel">
        <strong>{{ r.title }}</strong>
        <p class="content-text">{{ r.excerpt }}</p>
        <span class="muted">{{ r.source }}</span>
      </article>
      <el-empty v-if="!results.length" description="暂无检索结果" /></section
  ></template>
  <section v-else class="panel">
    <div class="toolbar">
      <el-select v-model="status" clearable placeholder="全部运行状态"
        ><el-option
          v-for="(label, key) in runStatuses"
          :key="key"
          :value="key"
          :label="label" /></el-select
      ><span class="muted">最近 200 次运行</span>
    </div>
    <el-table :data="filtered" v-loading="busy"
      ><el-table-column
        prop="id"
        label="任务 ID"
        min-width="200"
        show-overflow-tooltip
      /><el-table-column
        prop="user_id"
        label="用户 ID"
        width="110"
      /><el-table-column label="状态" width="120"
        ><template #default="{ row }"
          ><el-tag
            :type="
              row.status === 'completed'
                ? 'success'
                : row.status === 'failed'
                  ? 'danger'
                  : 'info'
            "
            >{{ runStatuses[row.status] }}</el-tag
          ></template
        ></el-table-column
      ><el-table-column
        prop="error"
        label="错误说明"
        min-width="220"
      /><el-table-column label="创建时间" min-width="180"
        ><template #default="{ row }">{{
          new Date(row.created_at).toLocaleString()
        }}</template></el-table-column
      ><el-table-column label="操作" width="110"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            @click="
              perform(async () => {
                events = await api.request(
                  `/agent/admin/runs/${row.id}/events`,
                );
                showEvents = true;
              })
            "
            >执行详情</el-button
          ></template
        ></el-table-column
      ></el-table
    >
  </section>
  <el-dialog v-model="open" title="新增知识文档" width="780px"
    ><el-form label-position="top"
      ><el-form-item label="标题" required
        ><el-input v-model="doc.title" maxlength="200" /></el-form-item
      ><el-form-item label="来源网址或资料说明"
        ><el-input v-model="doc.source" /></el-form-item
      ><el-form-item label="正文" required
        ><el-input
          v-model="doc.content"
          type="textarea"
          :rows="14" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        @click="
          perform(async () => {
            await api.request('/agent/admin/documents', 'POST', doc);
            open = false;
            await load();
          }, '文档已建立索引')
        "
        >保存并建立索引</el-button
      ></template
    ></el-dialog
  ><el-dialog v-model="showEvents" title="任务执行详情" width="850px"
    ><el-timeline
      ><el-timeline-item
        v-for="e in events"
        :key="e.id"
        :timestamp="new Date(e.created_at).toLocaleString()"
        ><strong>{{ e.type }}</strong>
        <pre class="event-json">{{ JSON.stringify(e.data, null, 2) }}</pre>
      </el-timeline-item></el-timeline
    ></el-dialog
  >
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
import type { News } from "../../../shared/types";
const { busy, error, perform, confirm } = useTask(),
  rows = ref<News[]>([]),
  query = ref(""),
  status = ref(""),
  page = ref(1),
  open = ref(false),
  preview = ref(false),
  form = reactive<any>({});
const filtered = computed(() =>
    rows.value.filter(
      (r) =>
        (!status.value || r.status === status.value) &&
        r.title.includes(query.value),
    ),
  ),
  html = computed(() =>
    DOMPurify.sanitize(
      marked.parse(form.content || "", { async: false }) as string,
    ),
  );
async function load() {
  rows.value = await api.request("/api/admin/news");
}
function edit(row?: News) {
  Object.keys(form).forEach((k) => delete form[k]);
  Object.assign(
    form,
    row || {
      title: "",
      summary: "",
      content: "",
      tag: "硬件资讯",
      status: "DRAFT",
      coverKey: "",
      coverUrl: "",
    },
  );
  open.value = true;
}
async function save() {
  if (!form.title.trim() || !form.content.trim())
    throw Error("请填写标题和正文");
  await api.request(
    `/api/admin/news${form.id ? "/" + form.id : ""}`,
    form.id ? "PUT" : "POST",
    form,
  );
  open.value = false;
  await load();
}
async function upload(event: Event, inline = false) {
  const input = event.target as HTMLInputElement,
    file = input.files?.[0];
  if (!file) return;
  await perform(async () => {
    const d = await api.upload(file, "news");
    if (inline) form.content += `\n\n![图片](${d.url})\n`;
    else {
      form.coverKey = d.key;
      form.coverUrl = d.url;
    }
  }, "图片上传成功");
  input.value = "";
}
onMounted(() => perform(load));
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>资讯管理</h1>
      <p>清晰编辑、预览并发布内容。</p>
    </div>
    <el-button type="primary" @click="edit()">新建资讯</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="query"
        placeholder="搜索标题"
        clearable
        @input="page = 1"
      /><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        @change="page = 1"
        ><el-option label="已发布" value="PUBLISHED" /><el-option
          label="草稿"
          value="DRAFT"
      /></el-select>
    </div>
    <el-table
      :data="filtered.slice((page - 1) * 10, page * 10)"
      v-loading="busy"
      ><el-table-column
        prop="title"
        label="标题"
        min-width="280"
      /><el-table-column prop="tag" label="分类" width="130" /><el-table-column
        label="状态"
        width="110"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'">{{
            row.status === "PUBLISHED" ? "已发布" : "草稿"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column
        prop="viewCount"
        label="阅读"
        width="90"
      /><el-table-column label="更新日期" min-width="160"
        ><template #default="{ row }">{{
          (row.updatedAt || row.createdAt)?.replace("T", " ").slice(0, 16)
        }}</template></el-table-column
      ><el-table-column label="操作" width="140" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="edit(row as News)">编辑</el-button
          ><el-button
            link
            type="danger"
            @click="
              confirm('确认删除资讯及相关互动？', async () => {
                await api.request(`/api/admin/news/${row.id}`, 'DELETE');
                await load();
              })
            "
            >删除</el-button
          ></template
        ></el-table-column
      ></el-table
    ><el-pagination
      v-model:current-page="page"
      :total="filtered.length"
      :page-size="10"
      layout="total,prev,pager,next"
    />
  </section>
  <el-dialog
    v-model="open"
    :title="form.id ? '编辑资讯' : '新建资讯'"
    width="980px"
    ><el-form label-position="top"
      ><div class="form-grid">
        <el-form-item label="标题" required class="wide"
          ><el-input
            v-model="form.title"
            maxlength="120"
            show-word-limit /></el-form-item
        ><el-form-item label="分类"
          ><el-select v-model="form.tag" allow-create filterable
            ><el-option
              v-for="t in [
                '科技新闻',
                '硬件资讯',
                'AI 前沿',
                '行业观察',
                '新品发布',
                '装机指南',
              ]"
              :key="t"
              :label="t"
              :value="t" /></el-select></el-form-item
        ><el-form-item label="发布状态"
          ><el-radio-group v-model="form.status"
            ><el-radio-button value="DRAFT">草稿</el-radio-button
            ><el-radio-button value="PUBLISHED"
              >发布</el-radio-button
            ></el-radio-group
          ></el-form-item
        >
      </div>
      <el-form-item label="摘要"
        ><el-input
          v-model="form.summary"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit /></el-form-item
      ><el-form-item label="封面"
        ><div class="upload-row">
          <img
            v-if="form.coverUrl"
            :src="form.coverUrl"
            class="preview-image"
            alt="封面预览"
          /><label class="el-button"
            ><input
              hidden
              type="file"
              accept="image/*"
              @change="upload($event)"
            />上传封面</label
          ><el-button
            v-if="form.coverUrl"
            @click="
              form.coverUrl = '';
              form.coverKey = '';
            "
            >移除封面</el-button
          >
        </div></el-form-item
      >
      <div class="toolbar">
        <strong>正文 · Markdown</strong
        ><label class="el-button"
          ><input
            hidden
            type="file"
            accept="image/*"
            @change="upload($event, true)"
          />插入图片</label
        ><el-button @click="preview = !preview">{{
          preview ? "继续编辑" : "预览正文"
        }}</el-button>
      </div>
      <div v-if="preview" class="panel content-text" v-html="html" />
      <el-input
        v-else
        v-model="form.content"
        type="textarea"
        :rows="14"
        placeholder="在这里开始撰写…" /></el-form
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button
        type="primary"
        :loading="busy"
        @click="perform(save, '资讯已保存')"
        >{{
          form.status === "PUBLISHED" ? "保存并发布" : "保存草稿"
        }}</el-button
      ></template
    ></el-dialog
  >
</template>

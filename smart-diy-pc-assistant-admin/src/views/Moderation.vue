<script setup lang="ts">
import { computed, ref, watch, reactive } from "vue";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
const props = defineProps<{ kind: string }>(),
  { busy, error, perform, confirm } = useTask(),
  rows = ref<any[]>([]),
  query = ref(""),
  page = ref(1),
  comments = ref<any[]>([]),
  selected = ref<any>(null),
  notice = reactive({ title: "", content: "" }),
  sent = ref<any>(null);
const labels: Record<string, string> = {
    community: "社区内容",
    feedback: "用户反馈",
    notifications: "系统通知",
  },
  filtered = computed(() =>
    rows.value.filter((r) =>
      `${r.content} ${r.author?.nickname || ""}`.includes(query.value),
    ),
  );
async function load() {
  rows.value =
    props.kind === "notifications"
      ? []
      : await api.request(
          props.kind === "community"
            ? "/api/admin/community/posts"
            : "/api/admin/feedback",
        );
}
async function view(row: any) {
  selected.value = row;
  if (props.kind === "community")
    comments.value = await api.request(
      `/api/admin/community/posts/${row.id}/comments`,
    );
}
async function send() {
  if (!notice.title.trim() || !notice.content.trim())
    throw Error("请填写标题和内容");
  sent.value = await api.request(
    "/api/admin/notifications/system",
    "POST",
    notice,
  );
  notice.title = "";
  notice.content = "";
}
watch(
  () => props.kind,
  () => {
    selected.value = null;
    page.value = 1;
    query.value = "";
    perform(load);
  },
  { immediate: true },
);
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>{{ labels[kind] }}</h1>
      <p>
        {{
          kind === "notifications"
            ? "向所有用户发送清晰、有用的服务消息。"
            : "查看内容、处理问题，让社区保持良好体验。"
        }}
      </p>
    </div>
    <el-button v-if="kind !== 'notifications'" @click="perform(load)"
      >刷新</el-button
    >
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section
    v-if="kind === 'notifications'"
    class="panel"
    style="max-width: 850px"
  >
    <h2>发布系统通知</h2>
    <el-form label-position="top"
      ><el-form-item label="通知标题" required
        ><el-input v-model="notice.title" maxlength="120" /></el-form-item
      ><el-form-item label="通知内容" required
        ><el-input
          v-model="notice.content"
          type="textarea"
          :rows="8"
          maxlength="2000"
          show-word-limit /></el-form-item
      ><el-button
        type="primary"
        :loading="busy"
        @click="confirm('通知将发送给所有用户，确认发送？', send)"
        >发送系统通知</el-button
      ></el-form
    ><el-alert
      v-if="sent"
      style="margin-top: 24px"
      type="success"
      :title="`发送成功，已通知 ${sent.recipientCount} 位用户`"
    />
  </section>
  <section v-else class="panel">
    <div class="toolbar">
      <el-input
        v-model="query"
        placeholder="搜索内容或用户"
        clearable
        @input="page = 1"
      />
    </div>
    <el-table
      :data="filtered.slice((page - 1) * 12, page * 12)"
      v-loading="busy"
      ><el-table-column label="用户" min-width="150"
        ><template #default="{ row }">{{
          row.author?.nickname || row.author?.username || "用户"
        }}</template></el-table-column
      ><el-table-column
        prop="content"
        label="内容"
        min-width="330"
        show-overflow-tooltip
      /><el-table-column v-if="kind === 'feedback'" label="状态" width="120"
        ><template #default="{ row }"
          ><el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'">{{
            row.statusLabel
          }}</el-tag></template
        ></el-table-column
      ><el-table-column
        v-else
        prop="commentCount"
        label="评论"
        width="90"
      /><el-table-column label="操作" min-width="210" fixed="right"
        ><template #default="{ row }"
          ><el-button link type="primary" @click="perform(() => view(row))"
            >查看</el-button
          ><template v-if="kind === 'feedback'"
            ><el-button
              link
              :disabled="row.status !== 'SUBMITTED'"
              @click="
                perform(async () => {
                  await api.request(
                    `/api/admin/feedback/${row.id}/received`,
                    'PATCH',
                  );
                  await load();
                }, '已受理')
              "
              >受理</el-button
            ><el-button
              link
              type="success"
              :disabled="row.status === 'RESOLVED'"
              @click="
                perform(async () => {
                  await api.request(
                    `/api/admin/feedback/${row.id}/resolved`,
                    'PATCH',
                  );
                  await load();
                }, '已解决')
              "
              >解决</el-button
            ></template
          ><el-button
            v-else
            link
            type="danger"
            @click="
              confirm('删除该帖子及其评论？', async () => {
                await api.request(
                  `/api/admin/community/posts/${row.id}`,
                  'DELETE',
                );
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
      :page-size="12"
      layout="total,prev,pager,next"
    />
  </section>
  <el-dialog
    :model-value="!!selected"
    title="内容详情"
    width="780px"
    @close="selected = null"
    ><template v-if="selected"
      ><p class="content-text">{{ selected.content }}</p>
      <div class="row">
        <el-image
          v-for="url in selected.imageUrls"
          :key="url"
          :src="url"
          :preview-src-list="selected.imageUrls"
          style="width: 140px; height: 120px"
          fit="cover"
        />
      </div>
      <div v-if="selected.buildConfig" class="panel">
        关联配置：{{ selected.buildConfig.title }}
      </div>
      <template v-if="kind === 'community'"
        ><h3>评论管理</h3>
        <el-empty v-if="!comments.length" description="暂无评论" />
        <div v-for="c in comments" :key="c.id" class="panel">
          <strong>{{ c.author.nickname || c.author.username }}</strong>
          <p class="content-text">{{ c.content }}</p>
          <el-button
            link
            type="danger"
            @click="
              confirm('删除该评论？', async () => {
                await api.request(
                  `/api/admin/community/comments/${c.id}`,
                  'DELETE',
                );
                await view(selected);
                await load();
              })
            "
            >删除评论</el-button
          >
        </div></template
      ></template
    ></el-dialog
  >
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { api } from "../lib/api";
import { useTask } from "../lib/task";
const { busy, error, perform, confirm } = useTask(),
  rows = ref<any[]>([]),
  query = ref(""),
  role = ref(""),
  page = ref(1),
  ban = ref(false),
  form = reactive({ id: 0, name: "", until: "", permanent: true });
const filtered = computed(() =>
  rows.value.filter(
    (r) =>
      (!role.value || r.role === role.value) &&
      `${r.username} ${r.nickname} ${r.email}`
        .toLowerCase()
        .includes(query.value.toLowerCase()),
  ),
);
async function load() {
  rows.value = await api.request("/api/admin/users");
}
async function changeRole(row: any, value: unknown) {
  await confirm(
    `将 ${row.username} 的权限改为 ${value === "ADMIN" ? "管理员" : "普通用户"}？`,
    async () => {
      await api.request(`/api/admin/users/${row.id}/role`, "PUT", {
        role: value,
      });
      await load();
    },
  );
}
function openBan(row: any) {
  Object.assign(form, {
    id: row.id,
    name: row.username,
    until: "",
    permanent: true,
  });
  ban.value = true;
}
async function saveBan() {
  if (
    !form.permanent &&
    (!form.until || new Date(form.until).getTime() <= Date.now())
  )
    throw Error("请选择未来的解封时间");
  await api.request(`/api/admin/users/${form.id}/enabled`, "PATCH", {
    enabled: false,
    banExpiresAt: form.permanent ? null : form.until,
  });
  ban.value = false;
  await load();
}
onMounted(() => perform(load));
</script>
<template>
  <div class="page-heading">
    <div>
      <h1>用户管理</h1>
      <p>管理账号权限与访问状态。</p>
    </div>
    <el-button :loading="busy" @click="perform(load)">刷新</el-button>
  </div>
  <el-alert v-if="error" :title="error" type="error" />
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="query"
        clearable
        placeholder="搜索账号、昵称或邮箱"
        @input="page = 1"
      /><el-select
        v-model="role"
        clearable
        placeholder="全部角色"
        @change="page = 1"
        ><el-option label="管理员" value="ADMIN" /><el-option
          label="普通用户"
          value="USER"
      /></el-select>
    </div>
    <el-table
      :data="filtered.slice((page - 1) * 12, page * 12)"
      v-loading="busy"
      ><el-table-column label="用户" min-width="180"
        ><template #default="{ row }"
          ><strong>{{ row.nickname || row.username }}</strong>
          <div class="muted">@{{ row.username }}</div></template
        ></el-table-column
      ><el-table-column
        prop="email"
        label="邮箱"
        min-width="220"
      /><el-table-column label="角色" width="150"
        ><template #default="{ row }"
          ><el-select
            :model-value="row.role"
            @change="(value: unknown) => changeRole(row, value)"
            ><el-option label="用户" value="USER" /><el-option
              label="管理员"
              value="ADMIN" /></el-select></template></el-table-column
      ><el-table-column label="状态" min-width="180"
        ><template #default="{ row }"
          ><el-tag :type="row.enabled ? 'success' : 'danger'">{{
            row.enabled
              ? "正常"
              : row.banExpiresAt
                ? `封禁至 ${row.banExpiresAt.replace("T", " ")}`
                : "永久封禁"
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="操作" width="110" fixed="right"
        ><template #default="{ row }"
          ><el-button
            v-if="row.enabled"
            link
            type="danger"
            @click="openBan(row)"
            >封禁</el-button
          ><el-button
            v-else
            link
            type="primary"
            @click="
              confirm('解除该账号封禁？', async () => {
                await api.request(
                  `/api/admin/users/${row.id}/enabled`,
                  'PATCH',
                  { enabled: true, banExpiresAt: null },
                );
                await load();
              })
            "
            >解除封禁</el-button
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
  <el-dialog v-model="ban" :title="`封禁账号 ${form.name}`" width="480px"
    ><el-form label-position="top"
      ><el-form-item label="封禁方式"
        ><el-switch
          v-model="form.permanent"
          active-text="永久封禁"
          inactive-text="限时封禁" /></el-form-item
      ><el-form-item v-if="!form.permanent" label="自动解封时间"
        ><el-date-picker
          v-model="form.until"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-form
    ><template #footer
      ><el-button @click="ban = false">取消</el-button
      ><el-button
        type="danger"
        :loading="busy"
        @click="perform(saveBan, '已更新封禁状态')"
        >确认封禁</el-button
      ></template
    ></el-dialog
  >
</template>

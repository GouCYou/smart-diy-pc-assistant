<script setup lang="ts">
import UserAvatar from "../components/UserAvatar.vue";
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { COUNTRY_OPTIONS,CHINA_PROVINCES,CHINA_CITY_OPTIONS,TAIWAN_CITY_OPTIONS } from "../lib/regions";
import { api } from "../lib/api";
import { user } from "../lib/session";
import { useTask } from "../lib/task";
const router=useRouter();
function cancel(){if(window.history.state.back)router.back();else void router.push("/account");}
const provinces=computed(()=>form.countryOrRegion==="中国大陆" ? CHINA_PROVINCES : []);
const cities=computed(()=>form.countryOrRegion==="中国台湾" ? TAIWAN_CITY_OPTIONS : CHINA_CITY_OPTIONS[form.province] || []);
function changeCountry(){form.province=provinces.value[0] || "";form.city=cities.value[0] || "";}
function changeProvince(){form.city=cities.value[0] || "";}
const props = defineProps<{ section: string }>();
const { busy, error, perform } = useTask(),
  form = reactive<any>({}),
  preferences = ref("");
async function load() {
  if (!user.value) return;
  Object.assign(form, await api.request("/api/user/profile"));
  form.gender=({MALE:"男",FEMALE:"女",UNKNOWN:"不透露",SECRET:"不透露",OTHER:"不透露"} as Record<string,string>)[String(form.gender).toUpperCase()] || (["男","女","不透露"].includes(form.gender) ? form.gender : "不透露");
  try {
    preferences.value = (await api.request("/agent/preferences")).content;
  } catch {
    error.value = "个人资料已加载，装机偏好暂时无法连接";
  }
}
async function savePreferences() {
  await api.request("/agent/preferences", "PUT", {
    content: preferences.value,
  });
}
async function save() {
  if(form.countryOrRegion && !COUNTRY_OPTIONS.includes(form.countryOrRegion))throw Error("请从列表选择国家或地区");
  if(form.province && !provinces.value.includes(form.province))throw Error("请从列表重新选择省份或直辖市");
  if(form.city && !cities.value.includes(form.city))throw Error("请从列表重新选择城市");
  await api.request("/api/user/profile", "PUT", form);
  user.value = await api.request("/api/auth/me");
  cancel();
}
async function upload(event: Event, kind: string) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const d = await api.upload(file, kind === "avatar" ? "avatar" : "background");
  form[kind + "Key"] = d.key;
  form[kind + "Url"] = d.url;
}
onMounted(() => perform(load));
</script>
<template>
  <section v-if="!user" class="panel empty-auth">
    <h2>欢迎来到 Smart DIY</h2>
    <p>登录后保存配置，参与社区，继续你的装机计划。</p>
    <router-link to="/auth"
      ><el-button type="primary">登录 / 注册</el-button></router-link
    >
  </section>
  <template v-else
    ><Teleport to="#mobile-page-action"><button v-if="props.section === 'profile'" class="profile-save" :disabled="busy" @click="perform(save, '个人资料已保存')">保存</button></Teleport><el-alert v-if="error" :title="error" type="error" />
    <div class="account-editor">
      <section v-if="props.section === 'profile'" class="panel profile-editor">
        <h2 class="desktop-only">个人资料</h2>
        <div class="avatar-upload">
          <UserAvatar :size="64" :src="form.avatarUrl"/><div class="profile-avatar-copy"><strong>{{form.nickname || form.username}}</strong><label class="file-button"
            ><input
              hidden
              type="file"
              accept="image/*"
              @change="perform(() => upload($event, 'avatar'))"
            />更换头像</label></div>
        </div>
        <el-form label-position="top" class="form-grid"
          ><el-form-item label="账号"
            ><el-input :model-value="form.username" disabled /></el-form-item
          ><el-form-item label="邮箱"
            ><el-input :model-value="form.email" disabled /></el-form-item
          ><el-form-item label="昵称" class="wide"
            ><el-input v-model="form.nickname" maxlength="64" /></el-form-item
          ><el-form-item label="个人简介" class="wide"
            ><el-input
              v-model="form.bio"
              type="textarea"
              :rows="3"
              maxlength="300" /></el-form-item
          ><el-form-item label="性别"
            ><el-select v-model="form.gender" clearable
              ><el-option
                v-for="s in ['男', '女', '不透露']"
                :key="s"
                :label="s"
                :value="s" /></el-select></el-form-item
          ><el-form-item label="生日"
            ><el-date-picker
              v-model="form.birthday"
              type="date"
              value-format="YYYY-MM-DD"
              style="width: 100%" /></el-form-item
          ><el-form-item label="国家或地区"><el-select v-model="form.countryOrRegion" aria-label="国家或地区" @change="changeCountry"><el-option v-for="v in COUNTRY_OPTIONS" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          <el-form-item label="省份或直辖市"><el-select v-model="form.province" aria-label="省份或直辖市" :disabled="!provinces.length" placeholder="不适用" @change="changeProvince"><el-option v-for="v in provinces" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          <el-form-item label="城市"><el-select v-model="form.city" aria-label="城市" :disabled="!cities.length" placeholder="不适用"><el-option v-for="v in cities" :key="v" :value="v" :label="v" /></el-select></el-form-item>
          </el-form
        ><div class="profile-actions"><label class="file-button background-upload"><input hidden type="file" accept="image/*" @change="perform(() => upload($event, 'background'))" />更换个人空间背景</label><el-button :disabled="busy" @click="cancel">取消</el-button><el-button
          type="primary"
          :loading="busy"
          @click="perform(save, '个人资料已保存')"
          class="desktop-only">保存资料</el-button></div>

      </section>
      <div v-if="props.section === 'preferences'">
        <section class="panel">
          <h2 class="desktop-only">装机偏好</h2>
          <el-input
            v-model="preferences"
            type="textarea"
            :rows="8"
            maxlength="2000"
            show-word-limit
            placeholder="例如：主要玩 FPS 游戏，已有 2K 显示器，偏好黑色无灯机箱…"
          />
          <div class="action-row">
            <el-button
              type="primary"
              :loading="busy"
              @click="perform(savePreferences, '装机偏好已保存')"
              >保存偏好</el-button
            >
          </div>
        </section>

      </div>
    </div></template
  >
</template>

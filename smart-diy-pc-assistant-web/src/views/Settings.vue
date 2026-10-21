<script setup lang="ts">
import ThemeControl from "../components/ThemeControl.vue";
import { onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ArrowRight } from '@element-plus/icons-vue';
import { api } from '../lib/api';
import { user, logout } from '../lib/session';
import { useTask } from '../lib/task';
const router = useRouter(), {busy,error,perform} = useTask(), profile = reactive<any>({});
async function save() { await api.request('/api/user/profile','PUT',profile); }
onMounted(() => perform(async () => { if(user.value) Object.assign(profile,await api.request('/api/user/profile')); }));
</script>
<template>
  <div class="settings-page">
    <h1 class="desktop-only">设置</h1><el-alert v-if="error" :title="error" type="error" />
    <h2>外观</h2><section class="settings-group"><ThemeControl/></section>
    <template v-if="user">
      <h2>账号</h2><nav class="account-menu"><router-link to="/profile"><span>编辑个人资料</span><el-icon><ArrowRight /></el-icon></router-link><router-link to="/auth"><span>登录与密码</span><el-icon><ArrowRight /></el-icon></router-link></nav>
      <h2>个人隐私</h2><section class="settings-group">
        <div><label for="space-public">公开个人空间</label><el-switch id="space-public" v-model="profile.spacePublic" :disabled="busy" @change="perform(save)" /></div>
        <div><label for="profile-public">公开个人资料</label><el-switch id="profile-public" v-model="profile.profilePublic" :disabled="busy" @change="perform(save)" /></div>
      </section>
      <h2>偏好</h2><nav class="account-menu"><router-link to="/preferences"><span>装机偏好</span><el-icon><ArrowRight /></el-icon></router-link></nav>
      <button class="logout-button" @click="logout(); router.push('/account')">退出登录</button>
    </template>
    <section v-else class="panel empty-auth"><router-link to="/auth"><el-button type="primary">登录 / 注册</el-button></router-link></section>
  </div>
</template>

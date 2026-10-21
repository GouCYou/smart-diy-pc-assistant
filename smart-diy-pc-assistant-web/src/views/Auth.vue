<script setup lang="ts">
import { reactive, ref, watch, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ArrowLeft, View, Hide } from "@element-plus/icons-vue";
import { api } from "../lib/api";
import { user } from "../lib/session";
import { useTask } from "../lib/task";
const router = useRouter(),
  { busy, error, perform } = useTask(),
  mode = ref("login"),
  passwordVisible = ref(false),
  confirmPassword = ref(""),
  captcha = ref<any>(null),
  cooldown = ref(0),
  form = reactive({
    username: "",
    email: "",
    password: "",
    verificationCode: "",
    captchaCode: "",
  });
let timer: ReturnType<typeof setInterval> | undefined;
async function loadCaptcha() {
  captcha.value = await api.request("/api/auth/captcha");
  form.captchaCode = "";
}
async function sendCode() {
  if (!form.email || !form.captchaCode) throw Error("请填写邮箱与图形验证码");
  const data = await api.request(
    mode.value === "register"
      ? "/api/auth/verification-code"
      : "/api/auth/password-reset/verification-code",
    "POST",
    {
      email: form.email,
      captchaId: captcha.value.captchaId,
      captchaCode: form.captchaCode,
    },
  );
  cooldown.value = data.resendSeconds;
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    cooldown.value--;
    if (cooldown.value <= 0) clearInterval(timer);
  }, 1000);
  await loadCaptcha();
  ElMessage.success("验证码已发送");
}
async function submit() {
  if (mode.value !== "reset" && !form.username.trim()) throw Error("请填写用户名或邮箱");
  if (!form.password) throw Error("请填写密码");
  if (mode.value !== "login" && form.password !== confirmPassword.value) throw Error("两次输入的密码不一致");
  if (mode.value === "reset") {
    await api.request("/api/auth/password-reset", "POST", {
      email: form.email,
      verificationCode: form.verificationCode,
      newPassword: form.password,
    });
    mode.value = "login";
    ElMessage.success("密码已重置，请登录");
    return;
  }
  const path =
    mode.value === "login" ? "/api/auth/login" : "/api/auth/register";
  const d = await api.request(path, "POST", form);
  localStorage.setItem("smartdiy_token", d.token);
  user.value = await api.request("/api/auth/me");
  await router.push("/");
}
watch(mode, () => {
  error.value = "";
  if (mode.value !== "login") perform(loadCaptcha);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>
<template>
  <section class="auth-page">
    <header class="auth-nav">
      <button class="round-back" aria-label="返回" @click="router.push('/account')"><el-icon><ArrowLeft /></el-icon></button>
      <strong>{{ mode === 'login' ? '登录' : mode === 'register' ? '注册' : '找回密码' }}</strong><span />
    </header>
    <div class="auth-body">
      <div class="auth-hero"><img src="/logo.png" alt="Smart DIY" /><h1>{{ mode === 'login' ? '欢迎回来' : mode === 'register' ? '立即加入' : '重置密码' }}</h1></div>
      <div v-if="mode !== 'reset'" v-slider class="segmented auth-mode">
        <button :class="{selected:mode === 'login'}" @click="mode = 'login'">登录</button>
        <button :class="{selected:mode === 'register'}" @click="mode = 'register'">注册</button>
      </div>
      <button v-else class="auth-reset-back" @click="mode = 'login'">返回登录</button>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <Transition name="page-fade" mode="out-in"><form :key="mode" class="auth-form" @submit.prevent="perform(submit)">
        <input v-if="mode !== 'reset'" v-model="form.username" :placeholder="mode === 'login' ? '用户名或邮箱' : '英文用户名'" aria-label="用户名或邮箱" autocomplete="username" required />
        <input v-if="mode !== 'login'" v-model="form.email" type="email" placeholder="邮箱" aria-label="邮箱" autocomplete="email" required />
        <template v-if="mode !== 'login'">
          <div class="auth-input-row"><input v-model="form.captchaCode" maxlength="4" placeholder="图形验证码" aria-label="图形验证码" required /><button type="button" class="captcha-button" aria-label="刷新验证码" @click="perform(loadCaptcha)"><img v-if="captcha" :src="captcha.image" alt="图形验证码" /></button></div>
          <div class="auth-input-row"><input v-model="form.verificationCode" maxlength="6" placeholder="邮箱验证码" aria-label="邮箱验证码" required /><button type="button" :disabled="cooldown > 0 || busy" @click="perform(sendCode)">{{ cooldown > 0 ? cooldown+'s' : '发送验证码' }}</button></div>
        </template>
        <div class="auth-password"><input v-model="form.password" :type="passwordVisible ? 'text' : 'password'" :placeholder="mode === 'reset' ? '新密码' : '密码'" aria-label="密码" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required /><button v-if="form.password" type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" @click="passwordVisible = !passwordVisible"><el-icon><Hide v-if="passwordVisible" /><View v-else /></el-icon></button></div>
        <input v-if="mode !== 'login'" v-model="confirmPassword" :type="passwordVisible ? 'text' : 'password'" placeholder="确认密码" aria-label="确认密码" autocomplete="new-password" required />
        <button v-if="mode === 'login'" type="button" class="auth-forgot" @click="mode = 'reset'">忘记密码</button>
        <button type="submit" class="auth-submit" :disabled="busy">{{ busy ? '请稍候…' : mode === 'login' ? '登录' : mode === 'register' ? '注册并登录' : '重置密码' }}</button>
      </form></Transition>
    </div>
  </section>
</template>

<template>
	<view :class="pageLeaving ? 'auth-page auth-page-leave' : 'auth-page'">
		<view class="status-space"></view>
		<view class="auth-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">{{ authMode == 'login' ? '登录' : (authMode == 'register' ? '注册' : '找回密码') }}</text>
			<view class="nav-spacer"></view>
		</view>

		<view class="hero">
			<view class="ai-mark">
				<image class="ai-mark-logo" src="/static/logo.png" mode="aspectFill"></image>
			</view>
			<text class="hero-title">{{ authMode == 'login' ? '欢迎回来' : (authMode == 'register' ? '立即加入' : '重置密码') }}</text>
		</view>

		<view v-if="authMode != 'reset'" class="mode-switch">
			<view :class="authMode == 'login' ? 'mode-indicator mode-indicator-login' : 'mode-indicator mode-indicator-register'"></view>
			<view :class="authMode == 'login' ? 'mode-item mode-item-active' : 'mode-item'" @click="setAuthMode('login')">
				<text :class="authMode == 'login' ? 'mode-text mode-text-active' : 'mode-text'">登录</text>
			</view>
			<view :class="authMode == 'register' ? 'mode-item mode-item-active' : 'mode-item'" @click="setAuthMode('register')">
				<text :class="authMode == 'register' ? 'mode-text mode-text-active' : 'mode-text'">注册</text>
			</view>
		</view>
		<view v-else class="reset-switch">
			<text class="reset-switch-text" @click="setAuthMode('login')">返回登录</text>
		</view>

		<view :class="authMotionClass.length > 0 ? 'form ' + authMotionClass : 'form'">
			<input v-if="authMode != 'reset'" class="form-input" v-model="username" :placeholder="authMode == 'login' ? '用户名或邮箱' : '用户名'" placeholder-class="input-placeholder" />
			<input v-if="authMode == 'register' || authMode == 'reset'" class="form-input" v-model="email" placeholder="邮箱" placeholder-class="input-placeholder" />
			<view v-if="authMode == 'register' || authMode == 'reset'" class="captcha-row">
				<input class="form-input captcha-input" v-model="captchaCode" maxlength="4" placeholder="验证码" placeholder-class="input-placeholder" />
				<view class="captcha-image-box" @click="loadCaptcha">
					<image v-if="captchaImage.length > 0" class="captcha-image" :src="captchaImage" mode="aspectFit"></image>
					<text v-else class="captcha-refresh-text">刷新</text>
				</view>
			</view>
			<view v-if="authMode == 'register' || authMode == 'reset'" class="verification-row">
				<input class="form-input verification-input" v-model="verificationCode" maxlength="6" placeholder="邮箱验证码" placeholder-class="input-placeholder" />
				<view :class="canSendCode ? 'code-button' : 'code-button code-button-disabled'" @click="sendVerificationCode">
					<text class="code-button-text">{{ codeCountdown > 0 ? codeCountdown + 's' : (sendingCode ? '发送中' : '发送验证码') }}</text>
				</view>
			</view>
			<view class="password-field">
				<input class="form-input password-input" v-model="password" :password="!passwordVisible" :placeholder="authMode == 'reset' ? '新密码' : '密码'" placeholder-class="input-placeholder" />
				<view v-if="password.length > 0" class="password-eye-button" @click="passwordVisible = !passwordVisible">
					<view :class="passwordVisible ? 'eye-icon eye-icon-active' : 'eye-icon'">
						<view class="eye-line eye-line-top"></view>
						<view class="eye-line eye-line-bottom"></view>
						<view class="eye-pupil"></view>
						<view v-if="!passwordVisible" class="eye-slash"></view>
					</view>
				</view>
			</view>
			<view v-if="authMode == 'register' || authMode == 'reset'" class="password-field">
				<input class="form-input password-input" v-model="confirmPassword" :password="!confirmPasswordVisible" placeholder="确认密码" placeholder-class="input-placeholder" />
				<view v-if="confirmPassword.length > 0" class="password-eye-button" @click="confirmPasswordVisible = !confirmPasswordVisible">
					<view :class="confirmPasswordVisible ? 'eye-icon eye-icon-active' : 'eye-icon'">
						<view class="eye-line eye-line-top"></view>
						<view class="eye-line eye-line-bottom"></view>
						<view class="eye-pupil"></view>
						<view v-if="!confirmPasswordVisible" class="eye-slash"></view>
					</view>
				</view>
			</view>
			<view v-if="authMode == 'login'" class="forgot-row">
				<text class="forgot-link" @click="setAuthMode('reset')">忘记密码</text>
			</view>
			<view class="submit-button" @click="submitAuth">
				<text class="submit-text">{{ loading ? '请稍候...' : (authMode == 'login' ? '登录' : (authMode == 'register' ? '注册并登录' : '重置密码')) }}</text>
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
	import { API_BASE_URL } from '../../common/api'

	type AuthPayload = {
		userId : number
		username : string
		email : string
		nickname : string
		avatarKey : string
		avatarUrl : string
		bio : string
		role : string
		token : string
	}

	const apiBaseUrl = API_BASE_URL
	const authMode = ref('login')
	const username = ref('')
	const email = ref('')
	const password = ref('')
	const confirmPassword = ref('')
	const passwordVisible = ref(false)
	const confirmPasswordVisible = ref(false)
	const captchaId = ref('')
	const captchaImage = ref('')
	const captchaCode = ref('')
	const verificationCode = ref('')
	const loading = ref(false)
	const sendingCode = ref(false)
	const codeCountdown = ref(0)
	const authMotionClass = ref('')
	const pageLeaving = ref(false)
	const verificationCountdownSeconds = 120
	let codeTimer : ReturnType<typeof setInterval> | null = null

	const canSendCode = computed(() => {
		return (authMode.value == 'register' || authMode.value == 'reset') && !sendingCode.value && codeCountdown.value <= 0
	})

	function setAuthMode(mode : string) {
		if (mode == authMode.value) {
			return
		}
		authMode.value = mode
		confirmPassword.value = ''
		if (mode == 'register' || mode == 'reset') {
			refreshCaptchaState()
		}
		authMotionClass.value = mode == 'login' ? 'form-slide-left' : 'form-slide-right'
		setTimeout(() => {
			authMotionClass.value = ''
		}, 220)
	}

	function sendVerificationCode() {
		if (!canSendCode.value) {
			return
		}
		const cleanEmail = email.value.trim()
		if (cleanEmail.length == 0) {
			uni.showToast({
				title: '请先填写邮箱',
				icon: 'none'
			})
			return
		}
		const cleanCaptchaCode = captchaCode.value.trim().toUpperCase()
		if (captchaId.value.length == 0 || cleanCaptchaCode.length == 0) {
			uni.showToast({
				title: '请先输入验证码',
				icon: 'none'
			})
			return
		}
		sendingCode.value = true
		startCodeCountdown(verificationCountdownSeconds)
		uni.showToast({
			title: '验证码已发送',
			icon: 'none'
		})
		uni.request({
			url: apiBaseUrl + (authMode.value == 'reset' ? '/api/auth/password-reset/verification-code' : '/api/auth/verification-code'),
			method: 'POST',
			header: {
				'Content-Type': 'application/json'
			},
			data: {
				email: cleanEmail,
				captchaId: captchaId.value,
				captchaCode: cleanCaptchaCode
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					return
				}
				resetCodeCountdown()
				refreshCaptchaState()
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '发送失败',
					icon: 'none'
				})
			},
			fail: () => {
				resetCodeCountdown()
				refreshCaptchaState()
				uni.showToast({
					title: '暂时连接不到后端服务',
					icon: 'none'
				})
			},
			complete: () => {
				sendingCode.value = false
			}
		})
	}

	function loadCaptcha(silent = false) {
		if (authMode.value != 'register' && authMode.value != 'reset') {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/auth/captcha',
			method: 'GET',
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					captchaId.value = body.data.captchaId == null ? '' : String(body.data.captchaId)
					captchaImage.value = body.data.image == null ? '' : String(body.data.image)
					captchaCode.value = ''
					return
				}
				if (!silent) {
					uni.showToast({
						title: body != null && body.message != null ? body.message as string : '验证码加载失败',
						icon: 'none'
					})
				}
			},
			fail: () => {
				if (!silent) {
					uni.showToast({
						title: '暂时连接不到后端服务',
						icon: 'none'
					})
				}
			}
		})
	}

	function refreshCaptchaState(silent = false) {
		captchaId.value = ''
		captchaImage.value = ''
		captchaCode.value = ''
		loadCaptcha(silent)
	}

	function startCodeCountdown(seconds : number) {
		codeCountdown.value = seconds
		if (codeTimer != null) {
			clearInterval(codeTimer)
		}
		codeTimer = setInterval(() => {
			codeCountdown.value = Math.max(0, codeCountdown.value - 1)
			if (codeCountdown.value <= 0 && codeTimer != null) {
				clearInterval(codeTimer)
				codeTimer = null
			}
		}, 1000)
	}

	function resetCodeCountdown() {
		codeCountdown.value = 0
		if (codeTimer != null) {
			clearInterval(codeTimer)
			codeTimer = null
		}
	}

	function goBack() {
		if (pageLeaving.value) {
			return
		}
		pageLeaving.value = true
		setTimeout(() => {
			const pages = getCurrentPages()
			if (pages.length > 1) {
				uni.navigateBack({
					animationType: 'slide-out-right',
					animationDuration: 260,
					fail: () => {
						uni.reLaunch({ url: '/pages/index/index' })
					}
				})
				return
			}
			uni.reLaunch({ url: '/pages/index/index' })
		}, 250)
	}

	function submitAuth() {
		if (loading.value) {
			return
		}
		const cleanUsername = username.value.trim()
		const cleanEmail = email.value.trim()
		const cleanPassword = password.value
		const cleanConfirmPassword = confirmPassword.value
		const cleanVerificationCode = verificationCode.value.trim().toUpperCase()
		if (
			(authMode.value != 'reset' && cleanUsername.length == 0) ||
			cleanPassword.length == 0 ||
			((authMode.value == 'register' || authMode.value == 'reset') && (cleanEmail.length == 0 || cleanVerificationCode.length == 0 || cleanConfirmPassword.length == 0))
		) {
			uni.showToast({
				title: '请填写完整信息',
				icon: 'none'
			})
			return
		}
		if ((authMode.value == 'register' || authMode.value == 'reset') && !isStrongPassword(cleanPassword)) {
			uni.showToast({
				title: '密码需8-20位，包含字母和数字',
				icon: 'none'
			})
			return
		}
		if ((authMode.value == 'register' || authMode.value == 'reset') && cleanPassword != cleanConfirmPassword) {
			uni.showToast({
				title: '两次输入的密码不一致',
				icon: 'none'
			})
			return
		}
		if (authMode.value == 'reset') {
			resetPassword(cleanEmail, cleanVerificationCode, cleanPassword)
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + (authMode.value == 'register' ? '/api/auth/register' : '/api/auth/login'),
			method: 'POST',
			header: {
				'Content-Type': 'application/json'
			},
			data: authMode.value == 'register' ? {
				username: cleanUsername,
				email: cleanEmail,
				password: cleanPassword,
				verificationCode: cleanVerificationCode
			} : {
				username: cleanUsername,
				password: cleanPassword
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					saveAuth(body.data as AuthPayload)
					uni.showToast({
						title: authMode.value == 'register' ? '注册成功' : '已登录',
						icon: 'none'
					})
					pageLeaving.value = true
					setTimeout(() => {
						uni.reLaunch({
							url: '/pages/index/index'
						})
					}, 240)
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '登录失败',
					icon: 'none'
				})
			},
			fail: () => {
				uni.showToast({
					title: '暂时连接不到后端服务',
					icon: 'none'
				})
			},
			complete: () => {
				loading.value = false
			}
		})
	}

	function resetPassword(cleanEmail : string, cleanVerificationCode : string, cleanPassword : string) {
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/auth/password-reset',
			method: 'POST',
			header: {
				'Content-Type': 'application/json'
			},
			data: {
				email: cleanEmail,
				verificationCode: cleanVerificationCode,
				newPassword: cleanPassword
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					uni.showToast({
						title: '密码已重置',
						icon: 'none'
					})
					password.value = ''
					confirmPassword.value = ''
					verificationCode.value = ''
					captchaCode.value = ''
					setAuthMode('login')
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '重置失败',
					icon: 'none'
				})
			},
			fail: () => {
				uni.showToast({
					title: '暂时连接不到后端服务',
					icon: 'none'
				})
			},
			complete: () => {
				loading.value = false
			}
		})
	}

	function saveAuth(payload : AuthPayload) {
		const nickname = payload.nickname == null || payload.nickname.length == 0 ? payload.username : payload.nickname
		const avatarKey = payload.avatarKey == null ? '' : payload.avatarKey
		const bio = payload.bio == null || payload.bio.length == 0 ? '这个人还没有写简介。' : payload.bio
		uni.setStorageSync('app_token', payload.token)
		uni.setStorageSync('app_user_id', payload.userId)
		uni.setStorageSync('app_username', payload.username)
		uni.setStorageSync('app_email', payload.email)
		uni.setStorageSync('app_nickname', nickname)
		uni.setStorageSync('app_avatar_key', avatarKey)
		uni.setStorageSync('app_bio', bio)
	}

	function isStrongPassword(value : string) {
		return /^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(value)
	}

	onBeforeUnmount(() => {
		if (codeTimer != null) {
			clearInterval(codeTimer)
		}
	})

	onMounted(() => {
		if (authMode.value == 'register' || authMode.value == 'reset') {
			loadCaptcha()
		}
	})
</script>

<style>
	view,
	uni-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.auth-page {
		min-height: 100vh;
		width: 100vw;
		max-width: none;
		background-color: #f6f7fb;
		padding-left: 22px;
		padding-right: 22px;
		padding-bottom: constant(safe-area-inset-bottom);
		padding-bottom: env(safe-area-inset-bottom);
		box-sizing: border-box;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.auth-page-leave {
		animation-name: appPageOut;
		animation-duration: 260ms;
		animation-timing-function: ease-in;
		animation-fill-mode: forwards;
	}

	@keyframes appPageIn {
		from {
			opacity: 0;
			transform: translateX(28px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}

	@keyframes appPageOut {
		from {
			opacity: 1;
			transform: translateX(0);
		}
		to {
			opacity: 0;
			transform: translateX(28px);
		}
	}

	.status-space {
		height: 44px;
		height: calc(44px + constant(safe-area-inset-top));
		height: calc(44px + env(safe-area-inset-top));
		height: calc(20px + var(--status-bar-height));
	}

	.auth-nav {
		position: relative;
		height: 48px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.nav-button,
	.nav-spacer {
		width: 38px;
		height: 38px;
	}

	.nav-button {
		position: relative;
		z-index: 2;
		border-radius: 19px;
		background-color: #ffffff;
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: #eef2f7;
	}

	.back-chevron {
		position: absolute;
		width: 16px;
		height: 16px;
		left: 12px;
		top: 11px;
	}

	.back-chevron-line {
		position: absolute;
		left: 2px;
		width: 11px;
		height: 2px;
		border-radius: 2px;
		background-color: #111827;
	}

	.back-chevron-line-top {
		top: 4px;
		transform: rotate(-45deg);
	}

	.back-chevron-line-bottom {
		top: 10px;
		transform: rotate(45deg);
	}

	.nav-title {
		position: absolute;
		pointer-events: none;
		left: 0;
		right: 0;
		top: 12px;
		font-size: 17px;
		line-height: 24px;
		font-weight: 800;
		color: #111827;
		text-align: center;
	}

	.hero {
		align-items: center;
		padding-top: 78px;
		padding-bottom: 30px;
	}

	@media screen and (max-height: 700px) {
		.hero {
			padding-top: 58px;
			padding-bottom: 24px;
		}
	}

	.ai-mark {
		width: 58px;
		height: 58px;
		border-radius: 20px;
		align-items: center;
		justify-content: center;
		margin-bottom: 18px;
		overflow: hidden;
	}

	.ai-mark-logo {
		width: 58px;
		height: 58px;
		border-radius: 20px;
	}

	.hero-title {
		font-size: 30px;
		line-height: 38px;
		font-weight: 900;
		color: #111827;
	}

	.mode-switch {
		height: 46px;
		border-radius: 22px;
		background-color: #e9edf5;
		padding: 4px;
		flex-direction: row;
		margin-bottom: 16px;
		position: relative;
	}

	.mode-item {
		flex: 1;
		border-radius: 18px;
		align-items: center;
		justify-content: center;
		position: relative;
		z-index: 1;
	}

	.mode-indicator {
		position: absolute;
		left: 4px;
		top: 4px;
		width: calc((100% - 8px) / 2);
		height: 38px;
		border-radius: 18px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
		transition-property: transform;
		transition-duration: 240ms;
		transition-timing-function: ease-out;
		z-index: 0;
	}

	.mode-indicator-login {
		transform: translateX(0);
	}

	.mode-indicator-register {
		transform: translateX(100%);
	}

	.reset-switch {
		height: 46px;
		border-radius: 22px;
		background-color: #e9edf5;
		align-items: center;
		justify-content: center;
		margin-bottom: 16px;
	}

	.reset-switch-text {
		font-size: 14px;
		line-height: 20px;
		font-weight: 800;
		color: #111827;
	}

	.mode-text {
		font-size: 14px;
		font-weight: 800;
		color: #667085;
	}

	.mode-text-active {
		color: #111827;
	}

	.form {
		width: 100%;
	}

	.form-slide-right {
		animation-name: formSlideRight;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	.form-slide-left {
		animation-name: formSlideLeft;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	@keyframes formSlideRight {
		from {
			opacity: 0;
			transform: translateX(22px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}

	@keyframes formSlideLeft {
		from {
			opacity: 0;
			transform: translateX(-22px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}

	.form-input {
		height: 52px;
		border-radius: 20px;
		background-color: #ffffff;
		padding-left: 16px;
		padding-right: 16px;
		margin-bottom: 12px;
		font-size: 15px;
		color: #111827;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.password-field {
		position: relative;
		margin-bottom: 12px;
	}

	.password-field .form-input {
		margin-bottom: 0;
	}

	.password-input {
		padding-right: 56px;
	}

	.password-eye-button {
		position: absolute;
		right: 8px;
		top: 0;
		width: 44px;
		height: 52px;
		align-items: center;
		justify-content: center;
	}

	.eye-icon {
		position: relative;
		width: 26px;
		height: 17px;
		border-width: 2px;
		border-style: solid;
		border-color: #667085;
		border-radius: 999px;
		align-items: center;
		justify-content: center;
	}

	.eye-icon-active {
		border-color: #111827;
	}

	.eye-line {
		display: none;
	}

	.eye-pupil {
		width: 7px;
		height: 7px;
		border-radius: 999px;
		background-color: #667085;
		z-index: 1;
	}

	.eye-icon-active .eye-pupil {
		background-color: #111827;
	}

	.eye-slash {
		position: absolute;
		width: 30px;
		height: 3px;
		border-radius: 999px;
		background-color: #667085;
		transform: rotate(-42deg);
		z-index: 2;
	}

	.verification-row {
		flex-direction: row;
		align-items: center;
		margin-bottom: 12px;
	}

	.captcha-row {
		flex-direction: row;
		align-items: center;
		margin-bottom: 12px;
	}

	.captcha-input {
		flex: 1;
		margin-bottom: 0;
	}

	.captcha-image-box {
		width: 112px;
		height: 52px;
		border-radius: 20px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
		margin-left: 10px;
		overflow: hidden;
		border-width: 1px;
		border-color: #dce8fb;
	}

	.captcha-image {
		width: 112px;
		height: 52px;
	}

	.captcha-refresh-text {
		font-size: 13px;
		font-weight: 900;
		color: #667085;
	}

	.verification-input {
		flex: 1;
		margin-bottom: 0;
	}

	.code-button {
		width: 112px;
		height: 52px;
		border-radius: 20px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-left: 10px;
	}

	.code-button-disabled {
		background-color: #d7dce5;
	}

	.code-button-text {
		font-size: 13px;
		font-weight: 900;
		color: #ffffff;
	}

	.input-placeholder {
		color: #98a2b3;
	}

	.forgot-row {
		align-items: flex-end;
		margin-top: -2px;
		margin-bottom: 14px;
	}

	.forgot-link {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #667085;
	}

	.submit-button {
		height: 52px;
		border-radius: 26px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-top: 8px;
	}

	.submit-text {
		font-size: 15px;
		font-weight: 900;
		color: #ffffff;
	}
</style>

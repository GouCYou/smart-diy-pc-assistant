<template>
	<view :class="pageLeaving ? 'profile-page profile-page-leave' : 'profile-page'">
		<view class="status-space"></view>
		<view class="profile-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">个人资料</text>
			<view class="save-nav" @click="saveProfile">
				<text class="save-nav-text">保存</text>
			</view>
		</view>

		<view class="avatar-area">
			<view class="avatar-preview" @click="chooseAvatar">
				<image v-if="avatarPreviewUrl.length > 0" class="avatar-image" :src="avatarPreviewUrl" mode="aspectFill"></image>
				<view v-else class="avatar-placeholder">
					<view class="avatar-placeholder-head"></view>
					<view class="avatar-placeholder-body"></view>
				</view>
			</view>
			<view class="avatar-action" @click="chooseAvatar">
				<text class="avatar-action-text">{{ uploadingAvatar ? '上传中...' : '更换头像' }}</text>
			</view>
		</view>

		<view class="form">
			<view class="field">
				<text class="field-label">用户名</text>
				<text class="field-static">{{ username }}</text>
			</view>
			<view class="field">
				<text class="field-label">绑定邮箱</text>
				<text class="field-static">{{ email }}</text>
			</view>
			<view class="field">
				<text class="field-label">昵称</text>
				<input class="field-input" v-model="nickname" placeholder="填写昵称" placeholder-class="input-placeholder" />
			</view>
			<view class="field field-large">
				<text class="field-label">个人简介</text>
				<textarea class="field-textarea" v-model="bio" placeholder="介绍一下你的装机偏好" placeholder-class="input-placeholder" />
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'

	type UserProfilePayload = {
		userId : number
		username : string
		email : string
		nickname : string
		avatarKey : string
		avatarUrl : string
		bio : string
		role : string
	}

	type FileUploadPayload = {
		url : string
		key : string
		domain : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const userId = ref(0)
	const username = ref('')
	const email = ref('')
	const nickname = ref('')
	const avatarKey = ref('')
	const avatarUrl = ref('')
	const avatarPreviewUrl = ref('')
	const bio = ref('')
	const saving = ref(false)
	const uploadingAvatar = ref(false)
	const pageLeaving = ref(false)

	loadLocalProfile()
	loadRemoteProfile()

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

	function loadLocalProfile() {
		const savedToken = uni.getStorageSync('app_token')
		const savedUserId = uni.getStorageSync('app_user_id')
		const savedUsername = uni.getStorageSync('app_username')
		const savedEmail = uni.getStorageSync('app_email')
		const savedNickname = uni.getStorageSync('app_nickname')
		const savedAvatarKey = uni.getStorageSync('app_avatar_key')
		const savedBio = uni.getStorageSync('app_bio')
		if (savedToken != null) {
			token.value = savedToken as string
		}
		if (savedUserId != null) {
			userId.value = savedUserId as number
		}
		if (savedUsername != null) {
			username.value = savedUsername as string
		}
		if (savedEmail != null) {
			email.value = savedEmail as string
		}
		if (savedNickname != null) {
			nickname.value = savedNickname as string
		}
		if (savedAvatarKey != null) {
			avatarKey.value = savedAvatarKey as string
		}
		if (savedBio != null) {
			bio.value = savedBio as string
		}
		if (token.value.length == 0) {
			uni.redirectTo({
				url: '/pages/auth/auth'
			})
		}
	}

	function loadRemoteProfile() {
		if (token.value.length == 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					applyProfile(body.data as UserProfilePayload)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					uni.redirectTo({
						url: '/pages/auth/auth'
					})
				}
			}
		})
	}

	function chooseAvatar() {
		if (uploadingAvatar.value) {
			return
		}
		uni.chooseImage({
			count: 1,
			success: (res) => {
				if (res.tempFilePaths.length > 0) {
					const localPath = res.tempFilePaths[0] as string
					avatarPreviewUrl.value = localPath
					uploadAvatar(localPath)
				}
			}
		})
	}

	function uploadAvatar(localPath : string) {
		uploadingAvatar.value = true
		uni.uploadFile({
			url: apiBaseUrl + '/api/files/upload',
			filePath: localPath,
			name: 'file',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				if (res.statusCode == 200) {
					let body = null as any
					try {
						body = JSON.parse(res.data as string) as any
					} catch (error) {
						body = null
					}
					if (body != null && body.code == 0 && body.data != null) {
						const upload = body.data as FileUploadPayload
						avatarKey.value = upload.key
						avatarUrl.value = normalizeMediaUrl(upload.url)
						avatarPreviewUrl.value = avatarUrl.value
						uni.showToast({
							title: '头像已上传，请保存资料',
							icon: 'none'
						})
						return
					}
					avatarPreviewUrl.value = avatarUrl.value
					uni.showToast({
						title: body != null && body.message != null ? body.message as string : '头像上传失败',
						icon: 'none'
					})
					return
				}
				avatarPreviewUrl.value = avatarUrl.value
				uni.showToast({
					title: '头像上传失败',
					icon: 'none'
				})
			},
			fail: () => {
				avatarPreviewUrl.value = avatarUrl.value
				uni.showToast({
					title: '暂时连接不到后端服务',
					icon: 'none'
				})
			},
			complete: () => {
				uploadingAvatar.value = false
			}
		})
	}

	function saveProfile() {
		if (saving.value) {
			return
		}
		if (token.value.length == 0) {
			uni.redirectTo({
				url: '/pages/auth/auth'
			})
			return
		}
		saving.value = true
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'PUT',
			header: {
				'Content-Type': 'application/json',
				'Authorization': 'Bearer ' + token.value
			},
			data: {
				nickname: nickname.value,
				avatarKey: avatarKey.value,
				bio: bio.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					applyProfile(body.data as UserProfilePayload)
					uni.showToast({
						title: '已保存',
						icon: 'none'
					})
					pageLeaving.value = true
					setTimeout(() => {
						uni.navigateBack({
							animationType: 'slide-out-right',
							animationDuration: 260
						})
					}, 250)
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '保存失败',
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
				saving.value = false
			}
		})
	}

	function applyProfile(payload : UserProfilePayload) {
		username.value = payload.username
		userId.value = payload.userId
		email.value = payload.email
		nickname.value = payload.nickname == null || payload.nickname.length == 0 ? payload.username : payload.nickname
		avatarKey.value = payload.avatarKey == null ? '' : payload.avatarKey
		avatarUrl.value = normalizeMediaUrl(payload.avatarUrl == null ? '' : payload.avatarUrl)
		avatarPreviewUrl.value = avatarUrl.value
		bio.value = payload.bio == null ? '' : payload.bio
		uni.setStorageSync('app_user_id', userId.value)
		uni.setStorageSync('app_username', username.value)
		uni.setStorageSync('app_email', email.value)
		uni.setStorageSync('app_nickname', nickname.value)
		uni.setStorageSync('app_avatar_key', avatarKey.value)
		uni.setStorageSync('app_bio', bio.value)
	}

	function clearAuth() {
		userId.value = 0
		uni.removeStorageSync('app_token')
		uni.removeStorageSync('app_user_id')
		uni.removeStorageSync('app_username')
		uni.removeStorageSync('app_email')
		uni.removeStorageSync('app_nickname')
		uni.removeStorageSync('app_avatar_key')
		uni.removeStorageSync('app_bio')
	}
</script>

<style>
	view,
	uni-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.profile-page {
		min-height: 100vh;
		width: 100vw;
		max-width: none;
		background-color: #f6f7fb;
		padding-left: 20px;
		padding-right: 20px;
		padding-bottom: constant(safe-area-inset-bottom);
		padding-bottom: env(safe-area-inset-bottom);
		box-sizing: border-box;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.profile-page-leave {
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

	.profile-nav {
		position: relative;
		height: 48px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.nav-button {
		position: relative;
		z-index: 2;
		width: 38px;
		height: 38px;
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

	.save-nav {
		position: relative;
		z-index: 2;
		height: 38px;
		padding-left: 15px;
		padding-right: 15px;
		border-radius: 19px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.save-nav-text {
		font-size: 14px;
		font-weight: 800;
		color: #ffffff;
	}

	.avatar-area {
		align-items: center;
		padding-top: 34px;
		padding-bottom: 24px;
	}

	.avatar-preview {
		width: 92px;
		height: 92px;
		border-radius: 46px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}

	.avatar-image {
		width: 92px;
		height: 92px;
		border-radius: 46px;
	}

	.avatar-placeholder {
		width: 100%;
		height: 100%;
		border-radius: 999px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		overflow: hidden;
	}

	.avatar-placeholder-head {
		width: 32%;
		height: 32%;
		border-radius: 999px;
		background-color: #98a2b3;
		margin-bottom: 5px;
	}

	.avatar-placeholder-body {
		width: 54%;
		height: 28%;
		border-radius: 999px 999px 0 0;
		background-color: #98a2b3;
	}

	.avatar-action {
		height: 34px;
		padding-left: 14px;
		padding-right: 14px;
		border-radius: 17px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
		margin-top: 12px;
	}

	.avatar-action-text {
		font-size: 13px;
		font-weight: 800;
		color: #007aff;
	}

	.form {
		border-radius: 28px;
		background-color: #ffffff;
		padding: 8px 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.field {
		min-height: 58px;
		border-bottom-width: 1px;
		border-bottom-color: #edf0f5;
		justify-content: center;
	}

	.field-large {
		min-height: 116px;
	}

	.field-label {
		font-size: 12px;
		font-weight: 800;
		color: #98a2b3;
		margin-bottom: 6px;
	}

	.field-static {
		font-size: 15px;
		color: #344054;
	}

	.field-input {
		height: 30px;
		font-size: 15px;
		color: #111827;
	}

	.field-textarea {
		height: 72px;
		font-size: 15px;
		line-height: 22px;
		color: #111827;
	}

	.input-placeholder {
		color: #98a2b3;
	}
</style>

<template>
	<view :class="pageLeaving ? 'settings-page settings-page-leave' : 'settings-page'">
		<view class="status-space"></view>
		<view class="settings-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">设置</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="settings-scroll" scroll-y="true">
			<view class="section-title section-title-first">账号</view>
			<view class="setting-group">
				<view class="plain-row plain-row-last" @click="openProfileEditor">
					<text class="plain-label">编辑个人资料</text>
					<text class="plain-value">›</text>
				</view>
			</view>

			<view class="section-title">个人隐私</view>
			<view class="setting-group">
				<view class="setting-row">
					<view class="setting-copy">
						<text class="setting-label">公开个人空间</text>
						<text class="setting-desc">关闭后，其他用户只能看到基础账号信息。</text>
					</view>
					<switch :checked="spacePublic" color="#007aff" @change="onSpacePublicChange" />
				</view>
				<view class="setting-row setting-row-last">
					<view class="setting-copy">
						<text class="setting-label">公开个人资料</text>
						<text class="setting-desc">关闭后，生日、性别和所在地仅自己可见。</text>
					</view>
					<switch :checked="profilePublic" color="#007aff" @change="onProfilePublicChange" />
				</view>
			</view>

			<view class="section-title">偏好</view>
			<view class="setting-group">
				<view class="setting-row">
					<view class="setting-copy">
						<text class="setting-label">通知提醒</text>
						<text class="setting-desc">保留互动和系统消息提醒。</text>
					</view>
					<switch :checked="notificationEnabled" color="#007aff" @change="onNotificationChange" />
				</view>
				<view class="setting-row setting-row-last">
					<view class="setting-copy">
						<text class="setting-label">省流量模式</text>
						<text class="setting-desc">列表图片优先使用压缩预览。</text>
					</view>
					<switch :checked="dataSaverEnabled" color="#007aff" @change="onDataSaverChange" />
				</view>
			</view>

			<view class="section-title">应用</view>
			<view class="setting-group">
				<view class="plain-row" @click="clearCache">
					<text class="plain-label">清理缓存</text>
					<text class="plain-value">›</text>
				</view>
				<view class="plain-row plain-row-last">
					<text class="plain-label">版本</text>
					<text class="plain-meta">1.0.0</text>
				</view>
			</view>

			<view v-if="token.length > 0" class="logout-button" @click="logout">
				<text class="logout-text">退出登录</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
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
		backgroundKey : string
		backgroundUrl : string
		bio : string
		gender : string
		birthday : string
		countryOrRegion : string
		province : string
		city : string
		profilePublic : boolean
		spacePublic : boolean
		role : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const profileLoaded = ref(false)
	const pageLeaving = ref(false)
	const saving = ref(false)
	const username = ref('')
	const email = ref('')
	const nickname = ref('用户')
	const avatarKey = ref('')
	const avatarUrl = ref('')
	const backgroundKey = ref('')
	const bio = ref('')
	const gender = ref('')
	const birthday = ref('')
	const countryOrRegion = ref('')
	const province = ref('')
	const city = ref('')
	const profilePublic = ref(true)
	const spacePublic = ref(true)
	const notificationEnabled = ref(true)
	const dataSaverEnabled = ref(false)

	loadLocalSettings()
	loadProfile()

	function loadLocalSettings() {
		const savedToken = uni.getStorageSync('app_token')
		const savedNotification = uni.getStorageSync('settings_notification_enabled')
		const savedDataSaver = uni.getStorageSync('settings_data_saver_enabled')
		if (savedToken != null) {
			token.value = savedToken as string
		}
		if (savedNotification != null) {
			notificationEnabled.value = savedNotification as boolean
		}
		if (savedDataSaver != null) {
			dataSaverEnabled.value = savedDataSaver as boolean
		}
		if (token.value.length == 0) {
			uni.redirectTo({ url: '/pages/auth/auth' })
		}
	}

	function loadProfile() {
		if (token.value.length == 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'GET',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					applyProfile(body.data as UserProfilePayload)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					uni.redirectTo({ url: '/pages/auth/auth' })
				}
			}
		})
	}

	function applyProfile(payload : UserProfilePayload) {
		username.value = payload.username
		email.value = payload.email
		nickname.value = payload.nickname == null || payload.nickname.length == 0 ? payload.username : payload.nickname
		avatarKey.value = payload.avatarKey == null ? '' : payload.avatarKey
		avatarUrl.value = normalizeMediaUrl(payload.avatarUrl == null ? '' : payload.avatarUrl)
		backgroundKey.value = payload.backgroundKey == null ? '' : payload.backgroundKey
		bio.value = payload.bio == null ? '' : payload.bio
		gender.value = payload.gender == null ? '' : payload.gender
		birthday.value = payload.birthday == null ? '' : payload.birthday
		countryOrRegion.value = payload.countryOrRegion == null ? '' : payload.countryOrRegion
		province.value = payload.province == null ? '' : payload.province
		city.value = payload.city == null ? '' : payload.city
		profilePublic.value = payload.profilePublic == null ? true : payload.profilePublic
		spacePublic.value = payload.spacePublic == null ? true : payload.spacePublic
		profileLoaded.value = true
	}

	function onSpacePublicChange(event : any) {
		spacePublic.value = event.detail.value as boolean
		savePrivacy()
	}

	function onProfilePublicChange(event : any) {
		profilePublic.value = event.detail.value as boolean
		savePrivacy()
	}

	function savePrivacy() {
		if (!profileLoaded.value) {
			return
		}
		saving.value = true
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'PUT',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				nickname: nickname.value,
				avatarKey: avatarKey.value,
				backgroundKey: backgroundKey.value,
				bio: bio.value,
				gender: gender.value,
				birthday: birthday.value,
				countryOrRegion: countryOrRegion.value,
				province: province.value,
				city: city.value,
				profilePublic: profilePublic.value,
				spacePublic: spacePublic.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					applyProfile(body.data as UserProfilePayload)
					uni.showToast({ title: '已保存', icon: 'none' })
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '保存失败', icon: 'none' })
				loadProfile()
			},
			fail: () => {
				uni.showToast({ title: '暂时连接不到后端服务', icon: 'none' })
				loadProfile()
			},
			complete: () => {
				saving.value = false
			}
		})
	}

	function onNotificationChange(event : any) {
		notificationEnabled.value = event.detail.value as boolean
		uni.setStorageSync('settings_notification_enabled', notificationEnabled.value)
	}

	function onDataSaverChange(event : any) {
		dataSaverEnabled.value = event.detail.value as boolean
		uni.setStorageSync('settings_data_saver_enabled', dataSaverEnabled.value)
	}

	function clearCache() {
		uni.removeStorageSync('current_post_id')
		uni.removeStorageSync('current_news_id')
		uni.showToast({ title: '缓存已清理', icon: 'none' })
	}

	function openProfileEditor() {
		uni.navigateTo({
			url: '/pages/profile/profile',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function logout() {
		clearAuth()
		uni.showToast({ title: '已退出登录', icon: 'none' })
		setTimeout(() => {
			uni.reLaunch({ url: '/pages/index/index' })
		}, 180)
	}

	function clearAuth() {
		token.value = ''
		uni.removeStorageSync('app_token')
		uni.removeStorageSync('app_user_id')
		uni.removeStorageSync('app_username')
		uni.removeStorageSync('app_email')
		uni.removeStorageSync('app_nickname')
		uni.removeStorageSync('app_avatar_key')
		uni.removeStorageSync('app_bio')
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
</script>

<style>
	view,
	uni-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.settings-page {
		min-height: 100vh;
		width: 100vw;
		background-color: #f6f7fb;
		padding-left: 16px;
		padding-right: 16px;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.settings-page-leave {
		animation-name: appPageOut;
		animation-duration: 260ms;
		animation-timing-function: ease-in;
		animation-fill-mode: forwards;
	}

	@keyframes appPageIn {
		from { opacity: 0; transform: translateX(28px); }
		to { opacity: 1; transform: translateX(0); }
	}

	@keyframes appPageOut {
		from { opacity: 1; transform: translateX(0); }
		to { opacity: 0; transform: translateX(28px); }
	}

	.status-space {
		height: calc(20px + var(--status-bar-height));
	}

	.settings-nav {
		position: relative;
		height: 48px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12px;
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

	.back-chevron-line-top { top: 4px; transform: rotate(-45deg); }
	.back-chevron-line-bottom { top: 10px; transform: rotate(45deg); }

	.nav-title {
		position: absolute;
		pointer-events: none;
		left: 0;
		right: 0;
		top: 12px;
		text-align: center;
		font-size: 17px;
		line-height: 24px;
		font-weight: 800;
		color: #111827;
	}

	.settings-scroll {
		flex: 1;
	}

	.setting-group {
		border-radius: 26px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.section-title {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #8a94a6;
		margin-left: 14px;
		margin-bottom: 8px;
		margin-top: 2px;
	}

	.section-title-first {
		margin-top: 0;
	}

	.setting-group {
		margin-bottom: 18px;
		padding-left: 16px;
		padding-right: 12px;
	}

	.setting-row {
		min-height: 72px;
		flex-direction: row;
		align-items: center;
		border-bottom-width: 1px;
		border-bottom-color: #edf0f5;
	}

	.setting-row-last,
	.plain-row-last {
		border-bottom-width: 0;
	}

	.setting-copy {
		flex: 1;
		padding-right: 12px;
	}

	.setting-label,
	.plain-label {
		font-size: 15px;
		line-height: 21px;
		font-weight: 800;
		color: #111827;
	}

	.setting-desc {
		font-size: 12px;
		line-height: 17px;
		color: #8a94a6;
		margin-top: 3px;
	}

	.plain-row {
		min-height: 54px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		border-bottom-width: 1px;
		border-bottom-color: #edf0f5;
	}

	.plain-value {
		font-size: 24px;
		color: #98a2b3;
	}

	.plain-meta {
		font-size: 14px;
		color: #8a94a6;
	}

	.logout-button {
		height: 50px;
		border-radius: 25px;
		background-color: #fff1f0;
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: #ffd8d6;
	}

	.logout-text {
		font-size: 15px;
		font-weight: 900;
		color: #ff3b30;
	}

	.bottom-space {
		height: 34px;
		height: calc(34px + env(safe-area-inset-bottom));
	}
</style>

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

		<scroll-view class="profile-scroll" scroll-y="true">
		<view class="account-card">
			<view class="avatar-preview" @click="chooseAvatar">
				<image v-if="avatarPreviewUrl.length > 0" class="avatar-image" :src="avatarPreviewUrl" mode="aspectFill"></image>
				<view v-else class="avatar-placeholder">
					<view class="avatar-placeholder-head"></view>
					<view class="avatar-placeholder-body"></view>
				</view>
			</view>
			<view class="account-main">
				<text class="account-name">{{ nickname.length > 0 ? nickname : username }}</text>
				<view class="avatar-action" @click="chooseAvatar">
					<text class="avatar-action-text">{{ uploadingAvatar ? '上传中...' : '更换头像' }}</text>
				</view>
			</view>
		</view>

		<view class="form-section">
			<view class="setting-row">
				<text class="setting-label">用户名</text>
				<text class="setting-value">{{ username }}</text>
			</view>
			<view class="setting-row">
				<text class="setting-label">邮箱</text>
				<text class="setting-value">{{ email }}</text>
			</view>
			<view class="setting-row">
				<text class="setting-label">昵称</text>
				<input class="setting-input" v-model="nickname" placeholder="填写昵称" placeholder-class="input-placeholder" />
			</view>
		</view>

		<view class="form-section">
			<view class="bio-field">
				<text class="setting-label">个人简介</text>
				<textarea class="setting-textarea" v-model="bio" placeholder="介绍一下你的装机偏好" placeholder-class="input-placeholder" />
			</view>
		</view>

		<view class="form-section">
			<view class="setting-row">
				<text class="setting-label">性别</text>
				<picker :range="genderOptions" :value="genderIndex" @change="onGenderChange">
					<view class="picker-value">
						<text class="picker-text">{{ getGenderLabel() }}</text>
					</view>
				</picker>
			</view>
			<view class="setting-row">
				<text class="setting-label">生日</text>
				<picker mode="date" :value="birthday" @change="onBirthdayChange">
					<view class="picker-value">
						<text :class="birthday.length > 0 ? 'picker-text' : 'picker-placeholder'">{{ birthday.length > 0 ? birthday : '选择生日' }}</text>
					</view>
				</picker>
			</view>
			<view class="setting-row">
				<text class="setting-label">所在地</text>
				<picker mode="multiSelector" :range="regionRange" :value="regionValue" @columnchange="onRegionColumnChange" @change="onRegionChange">
					<view class="picker-value">
						<text class="picker-text">{{ getRegionText() }}</text>
					</view>
				</picker>
			</view>
		</view>
		<view class="profile-bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'
	import { CHINA_CITY_OPTIONS, CHINA_PROVINCES, COUNTRY_OPTIONS, TAIWAN_CITY_OPTIONS } from '../../common/regions'

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
	const gender = ref('')
	const genderIndex = ref(0)
	const birthday = ref('')
	const countryOrRegion = ref('中国大陆')
	const province = ref('北京')
	const city = ref('北京市')
	const profilePublic = ref(true)
	const spacePublic = ref(true)
	const countryIndex = ref(0)
	const provinceIndex = ref(0)
	const cityIndex = ref(0)
	const regionRange = ref([[] as string[], [] as string[], [] as string[]])
	const regionValue = ref([0, 0, 0])
	const saving = ref(false)
	const uploadingAvatar = ref(false)
	const pageLeaving = ref(false)

	const countryOptions = COUNTRY_OPTIONS
	const provinceOptions = CHINA_PROVINCES
	const cityOptionsMap = CHINA_CITY_OPTIONS as any
	const taiwanCityOptions = TAIWAN_CITY_OPTIONS
	const taiwanProvinceColumn = ['台湾地区']
	const genderOptions = ['保密', '男', '女']
	const emptyRegionColumn = ['']

	syncPickerIndexes()
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
				bio: bio.value,
				gender: gender.value,
				birthday: birthday.value,
				countryOrRegion: countryOrRegion.value,
				province: countryOrRegion.value == '中国大陆' ? province.value : '',
				city: countryOrRegion.value == '中国大陆' || countryOrRegion.value == '中国台湾' ? city.value : ''
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
		gender.value = payload.gender == null ? '' : payload.gender
		birthday.value = payload.birthday == null ? '' : payload.birthday
		countryOrRegion.value = payload.countryOrRegion == null || payload.countryOrRegion.length == 0 ? '中国大陆' : payload.countryOrRegion
		province.value = payload.province == null || payload.province.length == 0 ? '北京' : payload.province
		city.value = payload.city == null || payload.city.length == 0 ? getCityOptions()[0] : payload.city
		if (countryOrRegion.value == '中国台湾') {
			province.value = ''
			city.value = payload.city == null || payload.city.length == 0 ? taiwanCityOptions[0] : payload.city
		}
		profilePublic.value = payload.profilePublic == null ? true : payload.profilePublic
		spacePublic.value = payload.spacePublic == null ? true : payload.spacePublic
		syncGenderIndex()
		syncPickerIndexes()
		uni.setStorageSync('app_user_id', userId.value)
		uni.setStorageSync('app_username', username.value)
		uni.setStorageSync('app_email', email.value)
		uni.setStorageSync('app_nickname', nickname.value)
		uni.setStorageSync('app_avatar_key', avatarKey.value)
		uni.setStorageSync('app_bio', bio.value)
	}

	function onBirthdayChange(event : any) {
		birthday.value = event.detail.value as string
	}

	function onGenderChange(event : any) {
		genderIndex.value = Number(event.detail.value)
		if (genderIndex.value == 1) {
			gender.value = 'MALE'
			return
		}
		if (genderIndex.value == 2) {
			gender.value = 'FEMALE'
			return
		}
		gender.value = ''
	}

	function syncGenderIndex() {
		if (gender.value == 'MALE') {
			genderIndex.value = 1
			return
		}
		if (gender.value == 'FEMALE') {
			genderIndex.value = 2
			return
		}
		genderIndex.value = 0
	}

	function getGenderLabel() : string {
		return genderOptions[genderIndex.value]
	}

	function onCountryChange(event : any) {
		countryIndex.value = Number(event.detail.value)
		countryOrRegion.value = countryOptions[countryIndex.value]
		if (countryOrRegion.value == '中国大陆') {
			provinceIndex.value = 0
			province.value = provinceOptions[0]
			cityIndex.value = 0
			city.value = getCityOptions()[0]
		} else {
			province.value = ''
			city.value = ''
		}
	}

	function onProvinceChange(event : any) {
		provinceIndex.value = Number(event.detail.value)
		province.value = provinceOptions[provinceIndex.value]
		cityIndex.value = 0
		city.value = getCityOptions()[0]
	}

	function onCityChange(event : any) {
		cityIndex.value = Number(event.detail.value)
		const options = getCityOptions()
		city.value = options[cityIndex.value]
	}

	function getCityOptions() : string[] {
		if (countryOrRegion.value == '中国台湾') {
			return taiwanCityOptions
		}
		const options = cityOptionsMap[province.value]
		if (options == null || options.length == 0) {
			return ['请选择城市']
		}
		return options as string[]
	}

	function syncPickerIndexes() {
		countryIndex.value = Math.max(0, countryOptions.indexOf(countryOrRegion.value))
		provinceIndex.value = Math.max(0, provinceOptions.indexOf(province.value))
		const cities = getCityOptions()
		cityIndex.value = Math.max(0, cities.indexOf(city.value))
		if (cityIndex.value == 0 && city.value.length == 0 && cities.length > 0) {
			city.value = cities[0]
		}
		syncRegionPicker()
	}

	function syncRegionPicker() {
		let provinceColumn = emptyRegionColumn
		let cityColumn = emptyRegionColumn
		if (countryOrRegion.value == '中国大陆') {
			provinceColumn = provinceOptions
			cityColumn = getCityOptions()
		}
		if (countryOrRegion.value == '中国台湾') {
			provinceColumn = taiwanProvinceColumn
			cityColumn = taiwanCityOptions
		}
		regionRange.value = [countryOptions, provinceColumn, cityColumn]
		regionValue.value = [
			Math.max(0, countryOptions.indexOf(countryOrRegion.value)),
			countryOrRegion.value == '中国大陆' ? Math.max(0, provinceOptions.indexOf(province.value)) : 0,
			countryOrRegion.value == '中国大陆' || countryOrRegion.value == '中国台湾' ? Math.max(0, cityColumn.indexOf(city.value)) : 0
		]
	}

	function onRegionColumnChange(event : any) {
		const column = Number(event.detail.column)
		const selected = Number(event.detail.value)
		const next = regionValue.value.slice()
		next[column] = selected
		if (column == 0) {
			const selectedCountry = countryOptions[selected]
			if (selectedCountry == '中国大陆') {
				countryOrRegion.value = selectedCountry
				province.value = provinceOptions[0]
				city.value = cityOptionsMap[province.value][0]
			} else if (selectedCountry == '中国台湾') {
				countryOrRegion.value = selectedCountry
				province.value = ''
				city.value = taiwanCityOptions[0]
			} else {
				countryOrRegion.value = selectedCountry
				province.value = ''
				city.value = ''
			}
			syncRegionPicker()
			return
		}
		if (column == 1 && countryOrRegion.value == '中国大陆') {
			province.value = provinceOptions[selected]
			city.value = getCityOptions()[0]
			syncRegionPicker()
			return
		}
		if (column == 2 && countryOrRegion.value == '中国大陆') {
			const cities = getCityOptions()
			city.value = cities[selected]
			syncRegionPicker()
			return
		}
		if (column == 2 && countryOrRegion.value == '中国台湾') {
			city.value = taiwanCityOptions[selected]
			syncRegionPicker()
			return
		}
		regionValue.value = next
	}

	function onRegionChange(event : any) {
		const value = event.detail.value as number[]
		const country = countryOptions[value[0]]
		countryOrRegion.value = country
		if (country == '中国大陆') {
			province.value = provinceOptions[value[1]]
			const cities = cityOptionsMap[province.value] as string[]
			city.value = cities[Math.min(value[2], cities.length - 1)]
		} else if (country == '中国台湾') {
			province.value = ''
			city.value = taiwanCityOptions[Math.min(value[2], taiwanCityOptions.length - 1)]
		} else {
			province.value = ''
			city.value = ''
		}
		syncRegionPicker()
	}

	function getRegionText() : string {
		if (countryOrRegion.value != '中国大陆') {
			if (countryOrRegion.value == '中国台湾' && city.value.length > 0) {
				return countryOrRegion.value + ' · ' + city.value
			}
			return countryOrRegion.value
		}
		return countryOrRegion.value + ' · ' + province.value + ' · ' + city.value
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
		padding-left: 16px;
		padding-right: 16px;
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

	.profile-scroll {
		flex: 1;
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

	.account-card {
		flex-direction: row;
		align-items: center;
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding: 18px;
		margin-top: 18px;
		margin-bottom: 14px;
	}

	.avatar-preview {
		width: 76px;
		height: 76px;
		border-radius: 38px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		margin-right: 16px;
	}

	.avatar-image {
		width: 76px;
		height: 76px;
		border-radius: 38px;
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
		align-self: flex-start;
		height: 32px;
		padding-left: 13px;
		padding-right: 13px;
		border-radius: 17px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
		margin-top: 10px;
	}

	.avatar-action-text {
		font-size: 13px;
		font-weight: 800;
		color: #007aff;
	}

	.account-main {
		flex: 1;
	}

	.account-name {
		font-size: 20px;
		line-height: 28px;
		font-weight: 900;
		color: #111827;
	}

	.form-section {
		border-radius: 24px;
		background-color: #ffffff;
		padding-left: 16px;
		padding-right: 16px;
		margin-bottom: 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.setting-row {
		min-height: 58px;
		flex-direction: row;
		align-items: center;
		border-bottom-width: 1px;
		border-bottom-color: #edf0f5;
		justify-content: space-between;
	}

	.setting-row-large {
		min-height: 120px;
		flex-direction: column;
		align-items: stretch;
		justify-content: center;
	}

	.bio-field {
		padding-top: 16px;
		padding-bottom: 16px;
	}

	.setting-row-last {
		border-bottom-width: 0;
	}

	.setting-label {
		font-size: 14px;
		line-height: 17px;
		font-weight: 700;
		color: #344054;
		margin-right: 14px;
	}

	.setting-value {
		font-size: 15px;
		line-height: 21px;
		color: #667085;
		text-align: right;
		flex: 1;
	}

	.setting-input {
		flex: 1;
		height: 30px;
		font-size: 15px;
		color: #111827;
		text-align: right;
	}

	.setting-textarea {
		width: 100%;
		max-width: 100%;
		box-sizing: border-box;
		height: 94px;
		font-size: 15px;
		line-height: 22px;
		color: #111827;
		margin-top: 10px;
		padding: 12px;
		border-radius: 18px;
		background-color: #f6f8fc;
	}

	.profile-bottom-space {
		height: 34px;
		height: calc(34px + constant(safe-area-inset-bottom));
		height: calc(34px + env(safe-area-inset-bottom));
	}

	.picker-value {
		min-height: 34px;
		justify-content: center;
		align-items: flex-end;
		flex: 1;
	}

	.picker-text {
		font-size: 15px;
		color: #111827;
		text-align: right;
	}

	.picker-placeholder {
		font-size: 15px;
		color: #98a2b3;
	}

	.input-placeholder {
		color: #98a2b3;
	}
</style>

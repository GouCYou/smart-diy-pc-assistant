<template>
	<view :class="pageLeaving ? 'notice-detail-page notice-detail-page-leave' : 'notice-detail-page'">
		<view class="status-space"></view>
		<view class="notice-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">通知详情</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="notice-scroll" scroll-y="true">
			<view class="notice-card">
				<text class="notice-title">{{ notification.title }}</text>
				<text class="notice-time">{{ formatTime(notification.createdAt) }}</text>
				<text class="notice-content">{{ notification.content }}</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onLoad } from '@dcloudio/uni-app'
	import { API_BASE_URL } from '../../common/api'

	type NotificationItem = {
		id : number
		type : string
		title : string
		content : string
		createdAt : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const pageLeaving = ref(false)
	const notificationId = ref(0)
	const notification = ref({
		id: 0,
		type: 'SYSTEM',
		title: '',
		content: '',
		createdAt: ''
	} as NotificationItem)

	onLoad((query) => {
		loadAuth()
		notificationId.value = Number(query == null || query.id == null ? 0 : query.id)
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		loadDetail()
	})

	function loadAuth() {
		const savedToken = uni.getStorageSync('app_token')
		token.value = savedToken == null ? '' : savedToken as string
	}

	function getAuthHeader() : any {
		return token.value.length == 0 ? {} : { 'Authorization': 'Bearer ' + token.value }
	}

	function loadDetail() {
		if (notificationId.value <= 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/notifications/' + notificationId.value,
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					notification.value = {
						id: Number(body.data.id == null ? 0 : body.data.id),
						type: body.data.type == null ? 'SYSTEM' : body.data.type as string,
						title: body.data.title == null ? '' : body.data.title as string,
						content: body.data.content == null ? '' : body.data.content as string,
						createdAt: body.data.createdAt == null ? '' : body.data.createdAt as string
					}
					return
				}
				if (body != null && body.message != null) {
					uni.showToast({
						title: body.message as string,
						icon: 'none'
					})
				}
			}
		})
	}

	function openAuthPage() {
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function goBack() {
		pageLeaving.value = true
		setTimeout(() => {
			uni.navigateBack()
		}, 220)
	}

	function formatTime(value : string) : string {
		if (value.length == 0) {
			return ''
		}
		return value.replace('T', ' ').slice(0, 16)
	}
</script>

<style>
	view,
	uni-view,
	scroll-view,
	uni-scroll-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.notice-detail-page {
		width: 100vw;
		min-height: 100vh;
		background-color: #f2f2f7;
		padding-left: 18px;
		padding-right: 18px;
		animation-name: pageInRight;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.notice-detail-page-leave {
		animation-name: pageOutRight;
		animation-duration: 220ms;
		animation-fill-mode: forwards;
		animation-timing-function: ease-in;
	}

	@keyframes pageInRight {
		from {
			transform: translateX(100%);
		}
		to {
			transform: translateX(0);
		}
	}

	@keyframes pageOutRight {
		from {
			transform: translateX(0);
		}
		to {
			transform: translateX(100%);
		}
	}

	.status-space {
		height: 44px;
		height: calc(44px + constant(safe-area-inset-top));
		height: calc(44px + env(safe-area-inset-top));
		flex-shrink: 0;
	}

	.notice-nav {
		height: 48px;
		flex-direction: row;
		align-items: center;
		margin-bottom: 16px;
	}

	.nav-button,
	.nav-spacer {
		width: 40px;
		height: 40px;
	}

	.nav-button {
		border-radius: 20px;
		background-color: rgba(255, 255, 255, 0.82);
		align-items: center;
		justify-content: center;
	}

	.nav-title {
		flex: 1;
		text-align: center;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.back-chevron {
		position: relative;
		width: 16px;
		height: 16px;
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

	.notice-scroll {
		flex: 1;
		height: 0;
		min-height: 0;
	}

	.notice-card {
		border-radius: 30px;
		background-color: #ffffff;
		padding: 26px 22px;
		align-items: flex-start;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.notice-title {
		width: 100%;
		text-align: left;
		font-size: 25px;
		line-height: 34px;
		font-weight: 900;
		color: #111827;
	}

	.notice-time {
		margin-top: 8px;
		font-size: 13px;
		line-height: 19px;
		color: #98a2b3;
	}

	.notice-content {
		width: 100%;
		margin-top: 24px;
		font-size: 16px;
		line-height: 27px;
		color: #344054;
		white-space: pre-wrap;
	}

	.bottom-space {
		height: 24px;
		height: calc(24px + constant(safe-area-inset-bottom));
		height: calc(24px + env(safe-area-inset-bottom));
		flex-shrink: 0;
	}
</style>

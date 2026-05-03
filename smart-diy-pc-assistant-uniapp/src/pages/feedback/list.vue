<template>
	<view :class="pageLeaving ? 'feedback-page feedback-page-leave' : 'feedback-page'">
		<view class="status-space"></view>
		<view class="feedback-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">我的反馈</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="feedback-scroll" scroll-y="true" refresher-enabled="true" :refresher-triggered="refreshing" refresher-background="#f2f2f7" @refresherrefresh="refreshList">
			<view v-for="item in items" :key="item.id" class="feedback-card">
				<view class="feedback-card-head">
					<text :class="'status-pill status-' + item.status.toLowerCase()">{{ item.statusLabel }}</text>
					<text class="feedback-time">{{ formatTime(item.createdAt) }}</text>
				</view>
				<text class="feedback-content">{{ item.content }}</text>
				<view v-if="item.imageUrls.length > 0" class="image-grid">
					<image v-for="image in item.imageUrls" :key="image" class="feedback-image" :src="image" mode="aspectFill" @click="previewFeedbackImage(item, image)"></image>
				</view>
				<view class="status-steps">
					<view class="status-step">
						<view class="status-dot status-dot-active">
							<text class="status-dot-text status-dot-text-active">1</text>
						</view>
						<text class="status-step-label status-step-label-active">已提交</text>
					</view>
					<view :class="isFeedbackReceived(item) ? 'status-connector status-connector-active' : 'status-connector'"></view>
					<view class="status-step">
						<view :class="isFeedbackReceived(item) ? 'status-dot status-dot-active' : 'status-dot'">
							<text :class="isFeedbackReceived(item) ? 'status-dot-text status-dot-text-active' : 'status-dot-text'">2</text>
						</view>
						<text :class="isFeedbackReceived(item) ? 'status-step-label status-step-label-active' : 'status-step-label'">已收到</text>
					</view>
					<view :class="isFeedbackResolved(item) ? 'status-connector status-connector-active' : 'status-connector'"></view>
					<view class="status-step">
						<view :class="isFeedbackResolved(item) ? 'status-dot status-dot-active' : 'status-dot'">
							<text :class="isFeedbackResolved(item) ? 'status-dot-text status-dot-text-active' : 'status-dot-text'">3</text>
						</view>
						<text :class="isFeedbackResolved(item) ? 'status-step-label status-step-label-active' : 'status-step-label'">已解决</text>
					</view>
				</view>
				<text v-if="item.resolvedAt.length > 0" class="status-desc">解决时间 {{ formatTime(item.resolvedAt) }}</text>
				<text v-else-if="item.receivedAt.length > 0" class="status-desc">收到时间 {{ formatTime(item.receivedAt) }}</text>
			</view>

			<view v-if="items.length == 0 && !loading" class="empty-card">
				<view class="empty-icon">
					<view class="empty-dot"></view>
					<view class="empty-line"></view>
				</view>
				<text class="empty-title">暂无反馈</text>
				<text class="empty-desc">提交后的反馈会在这里显示处理状态。</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'

	type FeedbackItem = {
		id : number
		content : string
		imageUrls : string[]
		status : string
		statusLabel : string
		createdAt : string
		receivedAt : string
		resolvedAt : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const pageLeaving = ref(false)
	const loading = ref(false)
	const refreshing = ref(false)
	const items = ref([] as FeedbackItem[])

	onShow(() => {
		loadAuth()
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		loadFeedback()
	})

	function loadAuth() {
		const savedToken = uni.getStorageSync('app_token')
		token.value = savedToken == null ? '' : savedToken as string
	}

	function getAuthHeader() : any {
		return token.value.length == 0 ? {} : { 'Authorization': 'Bearer ' + token.value }
	}

	function loadFeedback() {
		if (token.value.length == 0) {
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/feedback/mine',
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && Array.isArray(body.data)) {
					items.value = body.data.map((item : any) : FeedbackItem => normalizeFeedback(item))
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					openAuthPage()
				}
			},
			complete: () => {
				loading.value = false
				refreshing.value = false
			}
		})
	}

	function refreshList() {
		refreshing.value = true
		loadFeedback()
	}

	function normalizeFeedback(raw : any) : FeedbackItem {
		const urls = Array.isArray(raw.imageUrls) ? raw.imageUrls as string[] : [] as string[]
		return {
			id: Number(raw.id == null ? 0 : raw.id),
			content: raw.content == null ? '' : raw.content as string,
			imageUrls: urls.map(normalizeMediaUrl).filter((item) : boolean => item.length > 0),
			status: raw.status == null ? 'SUBMITTED' : raw.status as string,
			statusLabel: raw.statusLabel == null ? '已提交' : raw.statusLabel as string,
			createdAt: raw.createdAt == null ? '' : raw.createdAt as string,
			receivedAt: raw.receivedAt == null ? '' : raw.receivedAt as string,
			resolvedAt: raw.resolvedAt == null ? '' : raw.resolvedAt as string
		}
	}

	function previewFeedbackImage(item : FeedbackItem, image : string) {
		uni.previewImage({
			urls: item.imageUrls,
			current: image
		})
	}

	function isFeedbackReceived(item : FeedbackItem) : boolean {
		return item.status == 'RECEIVED' || item.status == 'RESOLVED'
	}

	function isFeedbackResolved(item : FeedbackItem) : boolean {
		return item.status == 'RESOLVED'
	}

	function goBack() {
		pageLeaving.value = true
		setTimeout(() => {
			uni.navigateBack({
				animationType: 'slide-out-right',
				animationDuration: 260
			})
		}, 220)
	}

	function openAuthPage() {
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
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

	.feedback-page {
		width: 100vw;
		height: 100vh;
		min-height: 100vh;
		background-color: #f2f2f7;
		padding-left: 18px;
		padding-right: 18px;
		overflow: hidden;
		animation-name: pageInRight;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.feedback-page-leave {
		animation-name: pageOutRight;
		animation-duration: 220ms;
		animation-fill-mode: forwards;
		animation-timing-function: ease-in;
	}

	@keyframes pageInRight {
		from { transform: translateX(100%); }
		to { transform: translateX(0); }
	}

	@keyframes pageOutRight {
		from { transform: translateX(0); }
		to { transform: translateX(100%); }
	}

	.status-space {
		height: 44px;
		height: calc(44px + constant(safe-area-inset-top));
		height: calc(44px + env(safe-area-inset-top));
		flex-shrink: 0;
	}

	.feedback-nav {
		height: 48px;
		flex-direction: row;
		align-items: center;
		margin-bottom: 16px;
	}

	.nav-button,
	.nav-spacer {
		width: 40px;
		height: 40px;
		flex-shrink: 0;
	}

	.nav-button {
		border-radius: 20px;
		background-color: rgba(255, 255, 255, 0.86);
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

	.feedback-scroll {
		flex: 1;
		min-height: 0;
	}

	.feedback-card,
	.empty-card {
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding: 18px;
		margin-bottom: 14px;
	}

	.feedback-card-head {
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 10px;
	}

	.status-pill {
		height: 28px;
		border-radius: 14px;
		padding-left: 12px;
		padding-right: 12px;
		font-size: 12px;
		line-height: 28px;
		font-weight: 900;
		color: #667085;
		background-color: #f3f6fb;
	}

	.status-received {
		color: #0a6bcb;
		background-color: #eaf4ff;
	}

	.status-resolved {
		color: #157f3b;
		background-color: #eaf8ef;
	}

	.feedback-time,
	.status-desc {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #98a2b3;
	}

	.feedback-content {
		font-size: 15px;
		line-height: 23px;
		color: #111827;
	}

	.image-grid {
		margin-top: 12px;
		flex-direction: row;
		flex-wrap: wrap;
	}

	.feedback-image {
		width: 78px;
		height: 78px;
		border-radius: 18px;
		margin-right: 8px;
		margin-bottom: 8px;
		background-color: #edf2f8;
	}

	.status-steps {
		margin-top: 18px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.status-step {
		width: 54px;
		align-items: center;
		flex-shrink: 0;
	}

	.status-dot {
		width: 30px;
		height: 30px;
		border-radius: 15px;
		background-color: #eef2f8;
		border-width: 2px;
		border-color: #d9e1ec;
		align-items: center;
		justify-content: center;
	}

	.status-dot-active {
		background-color: #111827;
		border-color: #111827;
	}

	.status-dot-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #98a2b3;
	}

	.status-dot-text-active {
		color: #ffffff;
	}

	.status-step-label {
		margin-top: 7px;
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #98a2b3;
		text-align: center;
	}

	.status-step-label-active {
		color: #111827;
	}

	.status-connector {
		flex: 1;
		height: 4px;
		border-radius: 2px;
		background-color: #e7ebf2;
		margin-left: 2px;
		margin-right: 2px;
		margin-bottom: 24px;
	}

	.status-connector-active {
		background-color: #111827;
	}

	.status-desc {
		margin-top: 10px;
	}

	.empty-card {
		margin-top: 24px;
		align-items: center;
		padding-top: 34px;
		padding-bottom: 34px;
	}

	.empty-icon {
		width: 62px;
		height: 62px;
		border-radius: 20px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
	}

	.empty-dot {
		width: 10px;
		height: 10px;
		border-radius: 5px;
		background-color: #007aff;
		margin-bottom: 7px;
	}

	.empty-line {
		width: 28px;
		height: 5px;
		border-radius: 3px;
		background-color: #007aff;
	}

	.empty-title {
		margin-top: 16px;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.empty-desc {
		margin-top: 6px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
	}

	.bottom-space {
		height: 30px;
		flex-shrink: 0;
	}
</style>

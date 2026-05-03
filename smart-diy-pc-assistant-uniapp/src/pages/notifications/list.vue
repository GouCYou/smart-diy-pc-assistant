<template>
	<view :class="pageLeaving ? 'notification-page notification-page-leave' : 'notification-page'">
		<view class="status-space"></view>
		<view class="notification-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">消息通知</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="notification-scroll" scroll-y="true" refresher-enabled="true" :refresher-triggered="refreshing" refresher-background="#f2f2f7" @refresherrefresh="refreshList">
			<view v-if="items.length > 0" class="notification-group">
				<view v-for="item in items" :key="item.id" class="notification-row" @click="openNotification(item)">
					<view :class="item.type == 'SYSTEM' || item.type == 'FEEDBACK_RESOLVED' ? 'notification-avatar notification-avatar-system' : 'notification-avatar'">
						<text v-if="item.type == 'SYSTEM' || item.type == 'FEEDBACK_RESOLVED'" class="system-mark">i</text>
						<image v-else-if="item.actor != null && item.actor.avatarUrl.length > 0" class="notification-avatar-image" :src="item.actor.avatarUrl" mode="aspectFill"></image>
						<view v-else class="avatar-placeholder">
							<view class="avatar-placeholder-head"></view>
							<view class="avatar-placeholder-body"></view>
						</view>
					</view>
					<view class="notification-main">
						<view class="notification-title-row">
							<text class="notification-title">{{ item.title }}</text>
							<view v-if="item.unread" class="notification-unread-dot"></view>
						</view>
						<text class="notification-content">{{ item.content }}</text>
						<text class="notification-time">{{ formatTime(item.createdAt) }}</text>
					</view>
					<text class="notification-arrow">›</text>
				</view>
			</view>

			<view v-if="items.length == 0 && !loading" class="empty-card">
				<view class="empty-icon">
					<view class="empty-icon-dot"></view>
					<view class="empty-icon-line"></view>
				</view>
				<text class="empty-title">暂无消息</text>
				<text class="empty-desc">系统通知、关注和评论回复会显示在这里。</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'

	type NotificationActor = {
		id : number
		username : string
		nickname : string
		avatarUrl : string
	}

	type NotificationItem = {
		id : number
		type : string
		title : string
		content : string
		actor : NotificationActor | null
		relatedPostId : number
		relatedCommentId : number
		relatedNewsId : number
		unread : boolean
		createdAt : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const loading = ref(false)
	const refreshing = ref(false)
	const pageLeaving = ref(false)
	const items = ref([] as NotificationItem[])

	onShow(() => {
		loadAuth()
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		loadNotifications(true)
	})

	function loadAuth() {
		const savedToken = uni.getStorageSync('app_token')
		token.value = savedToken == null ? '' : savedToken as string
	}

	function getAuthHeader() : any {
		return token.value.length == 0 ? {} : { 'Authorization': 'Bearer ' + token.value }
	}

	function loadNotifications(markRead : boolean) {
		if (token.value.length == 0) {
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/notifications',
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && Array.isArray(body.data)) {
					items.value = body.data.map((item : any) : NotificationItem => normalizeNotification(item))
					if (markRead && items.value.length > 0) {
						markAllRead()
					}
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

	function markAllRead() {
		uni.request({
			url: apiBaseUrl + '/api/notifications/read-all',
			method: 'POST',
			header: getAuthHeader(),
			success: () => {
				items.value = items.value.map((item) : NotificationItem => ({
					...item,
					unread: false
				}))
			}
		})
	}

	function refreshList() {
		refreshing.value = true
		loadNotifications(true)
	}

	function normalizeNotification(raw : any) : NotificationItem {
		const actor = raw.actor == null ? null : {
			id: Number(raw.actor.id == null ? 0 : raw.actor.id),
			username: raw.actor.username == null ? '' : raw.actor.username as string,
			nickname: raw.actor.nickname == null ? '' : raw.actor.nickname as string,
			avatarUrl: normalizeMediaUrl(raw.actor.avatarUrl == null ? '' : raw.actor.avatarUrl as string)
		}
		return {
			id: Number(raw.id == null ? 0 : raw.id),
			type: raw.type == null ? '' : raw.type as string,
			title: raw.title == null ? '' : raw.title as string,
			content: raw.content == null ? '' : raw.content as string,
			actor: actor,
			relatedPostId: Number(raw.relatedPostId == null ? 0 : raw.relatedPostId),
			relatedCommentId: Number(raw.relatedCommentId == null ? 0 : raw.relatedCommentId),
			relatedNewsId: Number(raw.relatedNewsId == null ? 0 : raw.relatedNewsId),
			unread: raw.unread == true,
			createdAt: raw.createdAt == null ? '' : raw.createdAt as string
		}
	}

	function openNotification(item : NotificationItem) {
		if (item.type == 'FEEDBACK_RESOLVED') {
			uni.navigateTo({
				url: '/pages/feedback/list',
				animationType: 'slide-in-right',
				animationDuration: 260
			})
			return
		}
		if (item.type == 'SYSTEM') {
			uni.navigateTo({
				url: '/pages/notifications/detail?id=' + item.id,
				animationType: 'slide-in-right',
				animationDuration: 260
			})
			return
		}
		if ((item.type == 'COMMUNITY_POST_REPLY' || item.type == 'COMMUNITY_POST_MENTION') && item.relatedPostId > 0) {
			uni.setStorageSync('current_post_id', item.relatedPostId)
			uni.navigateTo({
				url: '/pages/post/detail?id=' + item.relatedPostId,
				animationType: 'slide-in-right',
				animationDuration: 260
			})
			return
		}
		if ((item.type == 'COMMUNITY_COMMENT_REPLY' || item.type == 'COMMUNITY_COMMENT_MENTION') && item.relatedPostId > 0) {
			uni.setStorageSync('current_post_id', item.relatedPostId)
			uni.navigateTo({
				url: '/pages/post/detail?id=' + item.relatedPostId + '&commentId=' + item.relatedCommentId,
				animationType: 'slide-in-right',
				animationDuration: 260
			})
			return
		}
		if ((item.type == 'NEWS_COMMENT_REPLY' || item.type == 'NEWS_COMMENT_MENTION') && item.relatedNewsId > 0) {
			uni.setStorageSync('current_news_id', item.relatedNewsId)
			uni.navigateTo({
				url: '/pages/news/detail?id=' + item.relatedNewsId + '&commentId=' + item.relatedCommentId,
				animationType: 'slide-in-right',
				animationDuration: 260
			})
			return
		}
		if (item.actor != null && item.actor.id > 0) {
			uni.navigateTo({
				url: '/pages/user/space?userId=' + item.actor.id,
				animationType: 'slide-in-right',
				animationDuration: 260
			})
		}
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

	.notification-page {
		width: 100vw;
		height: 100vh;
		min-height: 100vh;
		background-color: #f2f2f7;
		padding-left: 18px;
		padding-right: 18px;
		overflow: hidden;
		box-sizing: border-box;
		animation-name: pageInRight;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.notification-page-leave {
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

	.notification-nav {
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

	.notification-scroll {
		flex: 1;
		height: auto;
		min-height: 0;
	}

	.notification-group {
		border-radius: 24px;
		background-color: rgba(255, 255, 255, 0.92);
		overflow: hidden;
		border-width: 1px;
		border-color: rgba(255, 255, 255, 0.76);
	}

	.notification-row {
		min-height: 86px;
		padding: 14px;
		flex-direction: row;
		align-items: center;
		border-bottom-width: 1px;
		border-bottom-color: #edf0f5;
	}

	.notification-avatar {
		width: 52px;
		height: 52px;
		border-radius: 16px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		margin-right: 12px;
		flex-shrink: 0;
	}

	.notification-avatar-system {
		background-color: #007aff;
	}

	.system-mark {
		font-size: 24px;
		line-height: 30px;
		font-weight: 900;
		color: #ffffff;
	}

	.notification-avatar-image {
		width: 52px;
		height: 52px;
		border-radius: 16px;
	}

	.avatar-placeholder {
		width: 100%;
		height: 100%;
		border-radius: 16px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
	}

	.avatar-placeholder-head {
		width: 16px;
		height: 16px;
		border-radius: 8px;
		background-color: #98a2b3;
		margin-bottom: 3px;
	}

	.avatar-placeholder-body {
		width: 28px;
		height: 14px;
		border-radius: 12px 12px 0 0;
		background-color: #98a2b3;
	}

	.notification-main {
		flex: 1;
		min-width: 0;
		padding-right: 10px;
	}

	.notification-title-row {
		flex-direction: row;
		align-items: center;
	}

	.notification-title {
		flex: 1;
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.notification-unread-dot {
		width: 8px;
		height: 8px;
		border-radius: 4px;
		background-color: #ff3b30;
		margin-left: 8px;
		flex-shrink: 0;
	}

	.notification-content {
		margin-top: 4px;
		font-size: 13px;
		line-height: 19px;
		color: #667085;
		max-height: 38px;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.notification-time {
		margin-top: 5px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.notification-arrow {
		font-size: 25px;
		line-height: 28px;
		color: #c5ccda;
	}

	.empty-card {
		margin-top: 24px;
		border-radius: 28px;
		background-color: #ffffff;
		padding: 34px 22px;
		align-items: center;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.empty-icon {
		width: 62px;
		height: 62px;
		border-radius: 20px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
	}

	.empty-icon-dot {
		width: 10px;
		height: 10px;
		border-radius: 5px;
		background-color: #007aff;
		margin-bottom: 7px;
	}

	.empty-icon-line {
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
		text-align: center;
		color: #98a2b3;
	}

	.bottom-space {
		height: 24px;
		height: calc(24px + constant(safe-area-inset-bottom));
		height: calc(24px + env(safe-area-inset-bottom));
		flex-shrink: 0;
	}
</style>

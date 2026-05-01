<template>
	<view :class="pageLeaving ? 'follow-page follow-page-leave' : 'follow-page'">
		<view class="status-space"></view>
		<view class="follow-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">{{ activeType == 'following' ? '关注' : '粉丝' }}</text>
			<view class="nav-spacer"></view>
		</view>

		<view class="segmented discovery-segmented">
			<view :class="activeType == 'following' ? 'segment-indicator segment-indicator-0' : 'segment-indicator segment-indicator-1'"></view>
			<view :class="activeType == 'following' ? 'segment-active' : 'segment'" @click="setActiveType('following')">
				<text :class="activeType == 'following' ? 'segment-active-text' : 'segment-text'">关注</text>
			</view>
			<view :class="activeType == 'followers' ? 'segment-active' : 'segment'" @click="setActiveType('followers')">
				<text :class="activeType == 'followers' ? 'segment-active-text' : 'segment-text'">粉丝</text>
			</view>
		</view>

		<scroll-view class="follow-scroll" scroll-y="true" refresher-enabled="true" :refresher-triggered="refreshing" refresher-background="#f2f2f7" @refresherrefresh="refreshList">
			<view v-if="items.length > 0" class="follow-group">
				<view v-for="item in items" :key="item.userId" class="follow-row" @click="openUserSpace(item.userId)">
					<view class="follow-avatar">
						<image v-if="item.avatarUrl.length > 0" class="follow-avatar-image" :src="item.avatarUrl" mode="aspectFill"></image>
						<view v-else class="avatar-placeholder">
							<view class="avatar-placeholder-head"></view>
							<view class="avatar-placeholder-body"></view>
						</view>
					</view>
					<view class="follow-main">
						<view class="follow-name-row">
							<text class="follow-name">{{ item.nickname || item.username }}</text>
							<text v-if="item.mine" class="follow-chip">自己</text>
							<text v-else-if="item.following" class="follow-chip follow-chip-muted">已关注</text>
						</view>
						<text class="follow-username">@{{ item.username }}</text>
					</view>
					<view class="follow-side">
						<text class="follow-arrow">›</text>
					</view>
				</view>
			</view>

			<view v-if="items.length == 0 && !loading" class="empty-card">
				<text class="empty-title">{{ activeType == 'following' ? '暂无关注' : '暂无粉丝' }}</text>
				<text class="empty-desc">{{ activeType == 'following' ? '关注的人会按最新关注时间显示在这里。' : '粉丝会按最新关注时间显示在这里。' }}</text>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onLoad, onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'

	type FollowItem = {
		userId : number
		username : string
		nickname : string
		avatarUrl : string
		bio : string
		followedAt : string
		following : boolean
		mine : boolean
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const userId = ref(0)
	const activeType = ref('following')
	const items = ref([] as FollowItem[])
	const loading = ref(false)
	const refreshing = ref(false)
	const pageLeaving = ref(false)

	onLoad((query) => {
		const rawUserId = query == null ? '' : query.userId as string
		const rawType = query == null ? '' : query.type as string
		userId.value = Number(rawUserId)
		activeType.value = rawType == 'followers' ? 'followers' : 'following'
		loadLocalState()
		loadList()
	})

	onShow(() => {
		loadLocalState()
		if (userId.value > 0) {
			loadList()
		}
	})

	function loadLocalState() {
		const savedToken = uni.getStorageSync('app_token')
		token.value = savedToken == null ? '' : savedToken as string
	}

	function setActiveType(type : string) {
		if (activeType.value == type) {
			return
		}
		activeType.value = type == 'followers' ? 'followers' : 'following'
		items.value = []
		loadList()
	}

	function refreshList() {
		refreshing.value = true
		loadList(true)
	}

	function loadList(force = false) {
		if ((loading.value && !force) || userId.value <= 0) {
			refreshing.value = false
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/users/' + userId.value + '/' + activeType.value,
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const next = body.data as FollowItem[]
					for (let i = 0; i < next.length; i++) {
						next[i] = normalizeItem(next[i])
					}
					items.value = next
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '读取失败', icon: 'none' })
			},
			fail: () => {
				uni.showToast({ title: '暂时读取不到列表', icon: 'none' })
			},
			complete: () => {
				loading.value = false
				refreshing.value = false
			}
		})
	}

	function normalizeItem(item : FollowItem) : FollowItem {
		item.username = item.username == null ? '' : item.username
		item.nickname = item.nickname == null ? '' : item.nickname
		item.avatarUrl = normalizeMediaUrl(item.avatarUrl == null ? '' : item.avatarUrl)
		item.bio = item.bio == null ? '' : item.bio
		item.followedAt = item.followedAt == null ? '' : item.followedAt
		return item
	}

	function openUserSpace(id : number) {
		if (id <= 0) {
			return
		}
		uni.navigateTo({
			url: '/pages/user/space?userId=' + id,
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function getAuthHeader() : any {
		if (token.value.length == 0) {
			return {}
		}
		return { 'Authorization': 'Bearer ' + token.value }
	}

	function clearAuth() {
		token.value = ''
		uni.removeStorageSync('app_token')
		uni.removeStorageSync('app_user_id')
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

	.follow-page {
		min-height: 100vh;
		height: 100vh;
		width: 100vw;
		background-color: #f2f2f7;
		padding-left: 18px;
		padding-right: 18px;
		box-sizing: border-box;
		overflow: hidden;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.follow-page-leave {
		animation-name: appPageOut;
		animation-duration: 260ms;
		animation-timing-function: ease-in;
		animation-fill-mode: forwards;
	}

	.status-space {
		height: 44px;
		height: calc(44px + constant(safe-area-inset-top));
		height: calc(44px + env(safe-area-inset-top));
		height: calc(20px + var(--status-bar-height));
	}

	.follow-nav {
		position: relative;
		height: 58px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.nav-button,
	.nav-spacer {
		position: relative;
		width: 38px;
		height: 38px;
		border-radius: 19px;
		background-color: rgba(255, 255, 255, 0.9);
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: rgba(229, 229, 234, 0.9);
	}

	.nav-spacer {
		background-color: transparent;
		border-color: transparent;
	}

	.nav-title {
		position: absolute;
		left: 0;
		right: 0;
		top: 16px;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
		text-align: center;
		pointer-events: none;
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

	.segmented {
		flex-direction: row;
		background-color: #e9edf5;
		border-radius: 18px;
		padding: 4px;
		position: relative;
		margin-bottom: 18px;
	}

	.segment,
	.segment-active {
		flex: 1;
		height: 36px;
		border-radius: 14px;
		align-items: center;
		justify-content: center;
		position: relative;
		z-index: 1;
	}

	.segment-indicator {
		position: absolute;
		left: 4px;
		top: 4px;
		width: calc((100% - 8px) / 3);
		height: 36px;
		border-radius: 14px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
		transition-property: transform;
		transition-duration: 240ms;
		transition-timing-function: ease-out;
		z-index: 0;
	}

	.segment-indicator-0 {
		transform: translateX(0);
	}

	.segment-indicator-1 {
		transform: translateX(100%);
	}

	.segment-indicator-2 {
		transform: translateX(200%);
	}

	.discovery-segmented .segment-indicator {
		width: calc((100% - 8px) / 2);
	}

	.segment-text,
	.segment-active-text {
		font-size: 13px;
	}

	.segment-text {
		color: #667085;
	}

	.segment-active-text {
		color: #111827;
		font-weight: 700;
	}

	.follow-scroll {
		flex: 1;
		height: auto;
		min-height: 0;
	}

	.follow-group {
		border-radius: 18px;
		background-color: #ffffff;
		overflow: hidden;
		border-width: 1px;
		border-color: rgba(229, 229, 234, 0.9);
	}

	.follow-row {
		min-height: 84px;
		flex-direction: row;
		align-items: center;
		padding: 12px;
		border-bottom-width: 1px;
		border-bottom-color: #f1f2f4;
	}

	.follow-avatar {
		width: 52px;
		height: 52px;
		border-radius: 26px;
		background-color: #eef2f7;
		margin-right: 12px;
		overflow: hidden;
		align-items: center;
		justify-content: center;
	}

	.follow-avatar-image {
		width: 52px;
		height: 52px;
		border-radius: 26px;
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
		margin-bottom: 2px;
	}

	.avatar-placeholder-body {
		width: 54%;
		height: 28%;
		border-radius: 999px 999px 0 0;
		background-color: #98a2b3;
	}

	.follow-main {
		flex: 1;
		min-width: 0;
	}

	.follow-name-row {
		flex-direction: row;
		align-items: center;
		min-width: 0;
	}

	.follow-name {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.follow-chip {
		margin-left: 8px;
		border-radius: 999px;
		background-color: #eef5ff;
		padding-left: 8px;
		padding-right: 8px;
		font-size: 11px;
		line-height: 18px;
		font-weight: 800;
		color: #0a84ff;
	}

	.follow-chip-muted {
		background-color: #f2f4f7;
		color: #667085;
	}

	.follow-username {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #8e8e93;
	}

	.follow-side {
		width: 28px;
		align-items: flex-end;
		justify-content: center;
		margin-left: 10px;
	}

	.follow-arrow {
		font-size: 28px;
		line-height: 28px;
		color: #c7c7cc;
	}

	.empty-card {
		border-radius: 22px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: rgba(229, 229, 234, 0.9);
		padding: 34px 20px;
		align-items: center;
	}

	.empty-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.empty-desc {
		margin-top: 6px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
		text-align: center;
	}

	.bottom-space {
		height: 24px;
		height: calc(24px + constant(safe-area-inset-bottom));
		height: calc(24px + env(safe-area-inset-bottom));
	}

	@keyframes appPageIn {
		from { opacity: 0; transform: translateX(28px); }
		to { opacity: 1; transform: translateX(0); }
	}

	@keyframes appPageOut {
		from { opacity: 1; transform: translateX(0); }
		to { opacity: 0; transform: translateX(28px); }
	}
</style>

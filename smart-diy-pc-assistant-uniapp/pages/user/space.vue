<template>
	<view :class="pageLeaving ? 'space-page space-page-leave' : 'space-page'">
		<view class="status-space"></view>
		<view class="space-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">个人空间</text>
			<view v-if="space.mine" class="nav-action" @click="openProfileEditor">
				<text class="nav-action-text">编辑</text>
			</view>
			<view v-else class="nav-spacer"></view>
		</view>

		<scroll-view class="space-scroll" scroll-y="true">
			<view :class="space.backgroundUrl.length > 0 ? 'hero-card hero-card-image' : 'hero-card hero-card-empty-state'" @click="openBackgroundPreview">
				<view class="hero-bg">
					<image v-if="space.backgroundUrl.length > 0" class="hero-bg-image" :src="space.backgroundUrl" mode="aspectFill"></image>
					<view v-else class="hero-bg-empty"></view>
				</view>
				<view v-if="space.backgroundUrl.length > 0" class="hero-shade"></view>
				<view class="hero-content">
					<view class="avatar-wrap">
						<image v-if="space.avatarUrl.length > 0" class="avatar-image" :src="space.avatarUrl" mode="aspectFill"></image>
						<view v-else class="avatar-placeholder">
							<view class="avatar-placeholder-head"></view>
							<view class="avatar-placeholder-body"></view>
						</view>
					</view>
					<view class="hero-main">
						<view class="name-row">
							<text class="nickname">{{ space.nickname }}</text>
							<text v-if="!space.profilePublic && space.mine" class="private-chip">仅自己可见</text>
						</view>
						<text class="username">@{{ space.username }}</text>
						<text class="bio">{{ space.bio.length > 0 ? space.bio : '这个人还没有写简介。' }}</text>
					</view>
				</view>
				<view class="stat-row">
					<view class="stat-item">
						<text class="stat-number">{{ space.followingCount }}</text>
						<text class="stat-label">关注</text>
					</view>
					<view class="stat-item">
						<text class="stat-number">{{ space.followerCount }}</text>
						<text class="stat-label">粉丝</text>
					</view>
					<view class="stat-item">
						<text class="stat-number">{{ space.posts.length }}</text>
						<text class="stat-label">动态</text>
					</view>
				</view>
				<view class="profile-meta">
					<text v-if="getGenderText().length > 0" :class="getGenderMetaClass()">{{ getGenderText() }}</text>
					<text v-if="getAgeText().length > 0" class="meta-chip">{{ getAgeText() }}</text>
					<text v-if="getLocationText().length > 0" class="meta-chip">{{ getLocationText() }}</text>
				</view>
				<view v-if="!space.mine" class="action-row">
					<view :class="space.following ? 'follow-button follow-button-active' : 'follow-button'" @click.stop="toggleFollow">
						<text :class="space.following ? 'follow-button-text follow-button-text-active' : 'follow-button-text'">{{ space.following ? '已关注' : '关注' }}</text>
					</view>
				</view>
			</view>

			<view v-if="!isSpaceHidden()" class="segmented space-segmented">
				<view :class="'segment-indicator segment-indicator-' + activeSegment"></view>
				<view :class="activeSegment == 0 ? 'segment-active' : 'segment'" @click="activeSegment = 0">
					<text :class="activeSegment == 0 ? 'segment-active-text' : 'segment-text'">动态</text>
				</view>
				<view :class="activeSegment == 1 ? 'segment-active' : 'segment'" @click="activeSegment = 1">
					<text :class="activeSegment == 1 ? 'segment-active-text' : 'segment-text'">收藏</text>
				</view>
			</view>

			<view v-if="isSpaceHidden()" class="empty-card hidden-space-card">
				<text class="empty-title">个人空间已隐藏</text>
				<text class="empty-desc">对方当前没有公开个人空间内容。</text>
			</view>

			<view v-if="activeSegment == 0 && !isSpaceHidden()" class="content-list">
				<view v-for="post in space.posts" :key="post.id" class="post-card" @click="openPostDetail(post.id)">
					<text class="post-content">{{ post.content }}</text>
					<view v-if="post.imageUrls.length > 0" class="thumb-grid">
						<image v-for="image in post.imageUrls.slice(0, 3)" :key="image" class="thumb" :src="image" mode="aspectFill"></image>
					</view>
					<text class="item-meta">{{ formatTime(post.createdAt) }} · {{ post.likeCount }} 赞 · {{ post.commentCount }} 评论</text>
				</view>
				<view v-if="space.posts.length == 0" class="empty-card">
					<text class="empty-title">还没有动态</text>
					<text class="empty-desc">发布后的装机讨论会展示在这里。</text>
				</view>
			</view>

			<view v-if="activeSegment == 1 && !isSpaceHidden()" class="content-list">
				<view v-for="item in getFavoriteItems()" :key="item.type + '-' + item.id">
					<view v-if="item.type == 'build'" class="build-link-card-row-wrap" @click="openBuildDetail(item.build)">
						<view class="build-link-icon">
							<view class="build-link-card">
								<view class="build-link-card-head"></view>
								<view class="build-link-card-row"></view>
								<view class="build-link-card-row build-link-card-row-short"></view>
							</view>
						</view>
						<view class="build-link-main">
							<text class="build-link-title">{{ item.build.title }}</text>
							<text class="build-link-desc">¥{{ item.build.totalPrice }} · 性能 {{ item.build.performanceScore }} 分</text>
						</view>
						<text class="build-link-arrow">›</text>
					</view>
					<view v-if="item.type == 'post'" class="post-card" @click="openPostDetail(item.post.id)">
						<text class="post-content">{{ item.post.content }}</text>
						<text class="item-meta">收藏动态 · {{ item.post.author.nickname }} · {{ formatTime(item.time) }}</text>
					</view>
					<view v-if="item.type == 'news'" class="favorite-news-card" @click="openNewsDetail(item.news.id)">
						<view class="favorite-news-cover-wrap">
							<image v-if="item.news.coverUrl.length > 0" class="favorite-news-cover" :src="item.news.coverUrl" mode="aspectFill"></image>
							<view v-else class="favorite-news-cover favorite-news-cover-empty">
								<text class="favorite-news-cover-text">暂无</text>
							</view>
							<view class="favorite-news-badge">
								<text class="favorite-news-badge-text">{{ item.news.tag || '科技新闻' }}</text>
							</view>
						</view>
						<view class="favorite-news-body">
							<text class="favorite-news-title">{{ item.news.title }}</text>
							<text class="favorite-news-summary">{{ item.news.summary }}</text>
							<view class="favorite-news-meta">
								<text class="favorite-news-meta-text">{{ formatTime(item.time) }}</text>
								<text class="favorite-news-meta-dot">·</text>
								<text class="favorite-news-meta-text">{{ item.news.viewCount }} 浏览</text>
								<text class="favorite-news-meta-dot">·</text>
								<text class="favorite-news-meta-text">{{ item.news.commentCount }} 评论</text>
							</view>
						</view>
					</view>
				</view>
				<view v-if="getFavoriteItems().length == 0" class="empty-card">
					<text class="empty-title">暂无收藏</text>
					<text class="empty-desc">收藏的配置、动态和热点会按时间显示在这里。</text>
				</view>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>

		<view v-if="backgroundPreviewVisible" class="preview-mask" @click="closeBackgroundPreview">
			<view class="preview-panel" @click.stop>
				<view class="preview-image-wrap">
					<image v-if="space.backgroundUrl.length > 0" class="preview-image" :src="space.backgroundUrl" mode="aspectFill"></image>
					<view v-else class="preview-empty">
						<text class="preview-empty-text">还没有设置背景图</text>
					</view>
				</view>
				<view v-if="space.mine" class="preview-action" @click="chooseBackground">
					<text class="preview-action-text">{{ uploadingBackground ? '上传中...' : '更改背景' }}</text>
				</view>
			</view>
		</view>

		<view v-if="buildDetailVisible" class="preview-mask" @click="closeBuildDetail">
			<view class="build-detail" @click.stop>
				<view class="detail-head">
					<text class="detail-title">{{ selectedBuild.title }}</text>
					<view class="detail-close" @click="closeBuildDetail">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<view class="detail-metrics">
					<view class="detail-metric">
						<text class="detail-label">总价</text>
						<text class="detail-value">¥{{ selectedBuild.totalPrice }}</text>
					</view>
					<view class="detail-metric">
						<text class="detail-label">性能</text>
						<text class="detail-value">{{ selectedBuild.performanceScore }} 分</text>
					</view>
				</view>
				<scroll-view class="detail-scroll" scroll-y="true">
					<view v-for="part in selectedBuild.parts" :key="part.id" class="detail-part-row">
						<text class="detail-part-title">{{ getHardwareTypeLabel(part.type) }} · {{ part.brand }} {{ part.model }}</text>
						<text class="detail-part-meta">¥{{ part.price }} · 性能 {{ part.performanceScore }} · {{ getPartSpecText(part) }}</text>
					</view>
					<text v-if="selectedBuild.note.length > 0" class="detail-note">备注：{{ selectedBuild.note }}</text>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onLoad, onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'

	type AuthorItem = {
		id : number
		username : string
		nickname : string
		avatarUrl : string
	}

	type HardwarePartItem = {
		id : number
		type : string
		brand : string
		model : string
		price : number
		performanceScore : number
		socketType : string
		memoryType : string
		formFactor : string
		wattage : number
		gpuLengthMm : number
		coolerHeightMm : number
		supportedFormFactors : string
		specs : string
		enabled : boolean
	}

	type BuildConfigItem = {
		id : number
		title : string
		note : string
		totalPrice : number
		performanceScore : number
		parts : HardwarePartItem[]
		updatedAt : string
	}

	type CommunityPostItem = {
		id : number
		author : AuthorItem
		content : string
		imageUrls : string[]
		likeCount : number
		commentCount : number
		favoriteCount : number
		createdAt : string
	}

	type NewsItem = {
		id : number
		title : string
		summary : string
		tag : string
		coverUrl : string
		viewCount : number
		commentCount : number
		createdAt : string
	}

	type FavoriteItem = {
		type : string
		id : number
		time : string
		build : BuildConfigItem
		post : CommunityPostItem
		news : NewsItem
	}

	type UserSpacePayload = {
		userId : number
		username : string
		nickname : string
		avatarUrl : string
		backgroundUrl : string
		bio : string
		gender : string
		birthday : string
		countryOrRegion : string
		province : string
		city : string
		profilePublic : boolean
		spacePublic : boolean
		mine : boolean
		following : boolean
		followingCount : number
		followerCount : number
		posts : CommunityPostItem[]
		favoriteBuilds : BuildConfigItem[]
		favoritePosts : CommunityPostItem[]
		favoriteNews : NewsItem[]
	}

	type FileUploadPayload = {
		url : string
		key : string
		domain : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const userId = ref(0)
	const activeSegment = ref(0)
	const pageLeaving = ref(false)
	const loading = ref(false)
	const uploadingBackground = ref(false)
	const backgroundPreviewVisible = ref(false)
	const buildDetailVisible = ref(false)
	const selectedBuild = ref(createEmptyBuildConfig())
	const space = ref(createEmptySpace())

	onLoad((query) => {
		const raw = query == null ? '' : query.userId as string
		userId.value = Number(raw)
		if (query != null && query.tab == 'favorites') {
			activeSegment.value = 1
		}
		loadLocalState()
		loadSpace()
	})

	onShow(() => {
		loadLocalState()
		if (userId.value > 0) {
			loadSpace()
		}
	})

	function createEmptySpace() : UserSpacePayload {
		return {
			userId: 0,
			username: '',
			nickname: '用户',
			avatarUrl: '',
			backgroundUrl: '',
			bio: '',
			gender: '',
			birthday: '',
			countryOrRegion: '',
			province: '',
			city: '',
			profilePublic: true,
			spacePublic: true,
			mine: false,
			following: false,
			followingCount: 0,
			followerCount: 0,
			posts: [] as CommunityPostItem[],
			favoriteBuilds: [] as BuildConfigItem[],
			favoritePosts: [] as CommunityPostItem[],
			favoriteNews: [] as NewsItem[]
		} as UserSpacePayload
	}

	function createEmptyBuildConfig() : BuildConfigItem {
		return {
			id: 0,
			title: '',
			note: '',
			totalPrice: 0,
			performanceScore: 0,
			parts: [] as HardwarePartItem[],
			updatedAt: ''
		} as BuildConfigItem
	}

	function loadLocalState() {
		const savedToken = uni.getStorageSync('app_token')
		if (savedToken != null) {
			token.value = savedToken as string
		}
	}

	function loadSpace() {
		if (loading.value || userId.value <= 0) {
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/users/' + userId.value + '/space',
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					space.value = normalizeSpace(body.data as UserSpacePayload)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			fail: () => {
				uni.showToast({ title: '暂时读取不到个人空间', icon: 'none' })
			},
			complete: () => {
				loading.value = false
			}
		})
	}

	function normalizeSpace(value : UserSpacePayload) : UserSpacePayload {
		value.avatarUrl = normalizeMediaUrl(value.avatarUrl == null ? '' : value.avatarUrl)
		value.backgroundUrl = normalizeMediaUrl(value.backgroundUrl == null ? '' : value.backgroundUrl)
		value.bio = value.bio == null ? '' : value.bio
		value.gender = value.gender == null ? '' : value.gender
		value.birthday = value.birthday == null ? '' : value.birthday
		value.countryOrRegion = value.countryOrRegion == null ? '' : value.countryOrRegion
		value.province = value.province == null ? '' : value.province
		value.city = value.city == null ? '' : value.city
		value.spacePublic = value.spacePublic == null ? true : value.spacePublic
		value.posts = value.posts == null ? [] as CommunityPostItem[] : value.posts
		value.favoriteBuilds = value.favoriteBuilds == null ? [] as BuildConfigItem[] : value.favoriteBuilds
		value.favoritePosts = value.favoritePosts == null ? [] as CommunityPostItem[] : value.favoritePosts
		value.favoriteNews = value.favoriteNews == null ? [] as NewsItem[] : value.favoriteNews
		for (let i = 0; i < value.posts.length; i++) {
			value.posts[i] = normalizePost(value.posts[i])
		}
		for (let i = 0; i < value.favoritePosts.length; i++) {
			value.favoritePosts[i] = normalizePost(value.favoritePosts[i])
		}
		for (let i = 0; i < value.favoriteBuilds.length; i++) {
			value.favoriteBuilds[i].parts = value.favoriteBuilds[i].parts == null ? [] as HardwarePartItem[] : value.favoriteBuilds[i].parts
			value.favoriteBuilds[i].note = value.favoriteBuilds[i].note == null ? '' : value.favoriteBuilds[i].note
			value.favoriteBuilds[i].updatedAt = value.favoriteBuilds[i].updatedAt == null ? '' : value.favoriteBuilds[i].updatedAt
		}
		for (let i = 0; i < value.favoriteNews.length; i++) {
			value.favoriteNews[i].summary = value.favoriteNews[i].summary == null ? '' : value.favoriteNews[i].summary
			value.favoriteNews[i].tag = value.favoriteNews[i].tag == null ? '' : value.favoriteNews[i].tag
			value.favoriteNews[i].coverUrl = normalizeMediaUrl(value.favoriteNews[i].coverUrl == null ? '' : value.favoriteNews[i].coverUrl)
			value.favoriteNews[i].viewCount = value.favoriteNews[i].viewCount == null ? 0 : value.favoriteNews[i].viewCount
			value.favoriteNews[i].commentCount = value.favoriteNews[i].commentCount == null ? 0 : value.favoriteNews[i].commentCount
			value.favoriteNews[i].createdAt = value.favoriteNews[i].createdAt == null ? '' : value.favoriteNews[i].createdAt
		}
		return value
	}

	function normalizePost(post : CommunityPostItem) : CommunityPostItem {
		if (post.author == null) {
			post.author = { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem
		}
		if (post.author.avatarUrl != null) {
			post.author.avatarUrl = normalizeMediaUrl(post.author.avatarUrl)
		}
		post.imageUrls = post.imageUrls == null ? [] as string[] : post.imageUrls
		for (let i = 0; i < post.imageUrls.length; i++) {
			post.imageUrls[i] = normalizeMediaUrl(post.imageUrls[i])
		}
		return post
	}

	function toggleFollow() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/users/' + userId.value + '/follow',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					space.value = normalizeSpace(body.data as UserSpacePayload)
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '操作失败', icon: 'none' })
			}
		})
	}

	function chooseBackground() {
		if (!space.value.mine || uploadingBackground.value) {
			return
		}
		backgroundPreviewVisible.value = false
		uni.chooseImage({
			count: 1,
			success: (res) => {
				if (res.tempFilePaths.length > 0) {
					uploadBackground(res.tempFilePaths[0] as string)
				}
			}
		})
	}

	function openBackgroundPreview() {
		backgroundPreviewVisible.value = true
	}

	function closeBackgroundPreview() {
		backgroundPreviewVisible.value = false
	}

	function getFavoriteItems() : FavoriteItem[] {
		const items = [] as FavoriteItem[]
		for (let i = 0; i < space.value.favoriteBuilds.length; i++) {
			const build = space.value.favoriteBuilds[i]
			items.push({ type: 'build', id: build.id, time: build.updatedAt, build: build, post: createEmptyPost(), news: createEmptyNews() } as FavoriteItem)
		}
		for (let i = 0; i < space.value.favoritePosts.length; i++) {
			const post = space.value.favoritePosts[i]
			items.push({ type: 'post', id: post.id, time: post.createdAt, build: createEmptyBuildConfig(), post: post, news: createEmptyNews() } as FavoriteItem)
		}
		for (let i = 0; i < space.value.favoriteNews.length; i++) {
			const news = space.value.favoriteNews[i]
			items.push({ type: 'news', id: news.id, time: news.createdAt, build: createEmptyBuildConfig(), post: createEmptyPost(), news: news } as FavoriteItem)
		}
		return items.sort((a : FavoriteItem, b : FavoriteItem) : number => {
			return toTimeValue(b.time) - toTimeValue(a.time)
		})
	}

	function isSpaceHidden() : boolean {
		return !space.value.mine && !space.value.spacePublic
	}

	function createEmptyPost() : CommunityPostItem {
		return {
			id: 0,
			author: { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem,
			content: '',
			imageUrls: [] as string[],
			likeCount: 0,
			commentCount: 0,
			favoriteCount: 0,
			createdAt: ''
		} as CommunityPostItem
	}

	function createEmptyNews() : NewsItem {
		return { id: 0, title: '', summary: '', tag: '', coverUrl: '', viewCount: 0, commentCount: 0, createdAt: '' } as NewsItem
	}

	function toTimeValue(value : string) : number {
		if (value == null || value.length == 0) {
			return 0
		}
		const parsed = Date.parse(value)
		return Number.isNaN(parsed) ? 0 : parsed
	}

	function openBuildDetail(build : BuildConfigItem) {
		selectedBuild.value = build
		buildDetailVisible.value = true
	}

	function closeBuildDetail() {
		buildDetailVisible.value = false
	}

	function uploadBackground(localPath : string) {
		uploadingBackground.value = true
		uni.uploadFile({
			url: apiBaseUrl + '/api/files/upload',
			filePath: localPath,
			name: 'file',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				let body = null as any
				try {
					body = JSON.parse(res.data as string) as any
				} catch (error) {
					body = null
				}
				if (res.statusCode == 200 && body != null && body.code == 0 && body.data != null) {
					const upload = body.data as FileUploadPayload
					saveBackground(upload.key)
					return
				}
				uni.showToast({ title: '背景图上传失败', icon: 'none' })
			},
			fail: () => {
				uni.showToast({ title: '暂时连接不到后端服务', icon: 'none' })
			},
			complete: () => {
				uploadingBackground.value = false
			}
		})
	}

	function saveBackground(backgroundKey : string) {
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'PUT',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				nickname: space.value.nickname,
				backgroundKey: backgroundKey,
				bio: space.value.bio,
				gender: space.value.gender,
				birthday: space.value.birthday,
				countryOrRegion: space.value.countryOrRegion,
				province: space.value.province,
				city: space.value.city,
				profilePublic: space.value.profilePublic
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					uni.showToast({ title: '背景图已更新', icon: 'none' })
					loadSpace()
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '保存失败', icon: 'none' })
			}
		})
	}

	function getLocationText() : string {
		if (space.value.countryOrRegion.length == 0) {
			return ''
		}
		if (space.value.countryOrRegion == '中国大陆') {
			if (space.value.province.length == 0 && space.value.city.length == 0) {
				return '中国大陆'
			}
			return space.value.province + ' ' + space.value.city
		}
		if (space.value.countryOrRegion == '中国台湾' && space.value.city.length > 0) {
			return space.value.countryOrRegion + ' ' + space.value.city
		}
		return space.value.countryOrRegion
	}

	function getGenderText() : string {
		if (space.value.gender == 'MALE') {
			return '男'
		}
		if (space.value.gender == 'FEMALE') {
			return '女'
		}
		return ''
	}

	function getGenderMetaClass() : string {
		if (space.value.gender == 'MALE') {
			return 'meta-chip meta-chip-gender meta-chip-male'
		}
		if (space.value.gender == 'FEMALE') {
			return 'meta-chip meta-chip-gender meta-chip-female'
		}
		return 'meta-chip meta-chip-gender'
	}

	function getAgeText() : string {
		if (space.value.birthday.length == 0) {
			return ''
		}
		const parts = space.value.birthday.split('-')
		if (parts.length < 3) {
			return ''
		}
		const year = Number(parts[0])
		const month = Number(parts[1])
		const day = Number(parts[2])
		if (year <= 0 || month <= 0 || day <= 0) {
			return ''
		}
		const now = new Date()
		let age = now.getFullYear() - year
		const currentMonth = now.getMonth() + 1
		const currentDay = now.getDate()
		if (currentMonth < month || (currentMonth == month && currentDay < day)) {
			age--
		}
		if (age < 0) {
			return ''
		}
		return age + '岁'
	}

	function openPostDetail(id : number) {
		uni.setStorageSync('current_post_id', id)
		uni.navigateTo({
			url: '/pages/post/detail',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function openNewsDetail(id : number) {
		uni.setStorageSync('current_news_id', id)
		uni.navigateTo({
			url: '/pages/news/detail',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function openProfileEditor() {
		uni.navigateTo({
			url: '/pages/profile/profile',
			animationType: 'slide-in-right',
			animationDuration: 260
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

	function getHardwareTypeLabel(type : string) : string {
		if (type == 'CPU') {
			return 'CPU'
		}
		if (type == 'MOTHERBOARD') {
			return '主板'
		}
		if (type == 'GPU') {
			return '显卡'
		}
		if (type == 'MEMORY') {
			return '内存'
		}
		if (type == 'STORAGE') {
			return '硬盘'
		}
		if (type == 'PSU') {
			return '电源'
		}
		if (type == 'CASE') {
			return '机箱'
		}
		if (type == 'COOLER') {
			return '散热'
		}
		return type
	}

	function getPartSpecText(part : HardwarePartItem) : string {
		let text = ''
		if (part.socketType != null && part.socketType.length > 0) {
			text += part.socketType
		}
		if (part.memoryType != null && part.memoryType.length > 0) {
			text += text.length == 0 ? part.memoryType : ' · ' + part.memoryType
		}
		if (part.formFactor != null && part.formFactor.length > 0) {
			text += text.length == 0 ? part.formFactor : ' · ' + part.formFactor
		}
		if (part.specs != null && part.specs.length > 0) {
			text += text.length == 0 ? part.specs : ' · ' + part.specs
		}
		return text.length == 0 ? '暂无规格' : text
	}

	function formatTime(value : string) : string {
		if (value == null) {
			return ''
		}
		if (value.length >= 16) {
			return value.substring(5, 16).replace('T', ' ')
		}
		return value
	}
</script>

<style>
	view,
	uni-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.space-page {
		min-height: 100vh;
		width: 100vw;
		background-color: #f6f7fb;
		padding-left: 16px;
		padding-right: 16px;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.space-page-leave {
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

	.space-nav {
		position: relative;
		height: 48px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 10px;
	}

	.nav-button,
	.nav-spacer {
		width: 38px;
		height: 38px;
	}

	.nav-button {
		position: relative;
		z-index: 3;
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

	.nav-action {
		height: 38px;
		padding-left: 15px;
		padding-right: 15px;
		border-radius: 19px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		z-index: 2;
	}

	.nav-action-text {
		font-size: 14px;
		font-weight: 800;
		color: #ffffff;
	}

	.space-scroll {
		flex: 1;
	}

	.hero-card,
	.post-card,
	.simple-card,
	.empty-card {
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		overflow: hidden;
	}

	.hero-card {
		position: relative;
		margin-bottom: 14px;
		min-height: 204px;
		background-color: #ffffff;
	}

	.hero-bg,
	.hero-bg-image,
	.hero-bg-empty {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
	}

	.hero-bg-empty {
		background-color: #ffffff;
	}

	.hero-card-empty-state .nickname,
	.hero-card-empty-state .stat-number {
		color: #111827;
		text-shadow: none;
	}

	.hero-card-empty-state .username,
	.hero-card-empty-state .bio {
		color: #667085;
		text-shadow: none;
	}

	.hero-card-empty-state .stat-label {
		color: #98a2b3;
		text-shadow: none;
	}

	.hero-card-empty-state .private-chip {
		background-color: #eef5ff;
	}

	.hero-card-empty-state .meta-chip {
		background-color: #f2f4f7;
	}

	.hero-card-empty-state .meta-chip-gender {
		background-color: #f2f4f7;
	}

	.hero-card-empty-state .meta-chip-male {
		background-color: #eef5ff;
		color: #007aff;
	}

	.hero-card-empty-state .meta-chip-female {
		background-color: #fff0f5;
		color: #ff2d55;
	}

	.hero-shade {
		position: absolute;
		left: 0;
		top: 0;
		width: 100%;
		height: 100%;
		background-color: rgba(17, 24, 39, 0.34);
	}

	.hero-content {
		position: relative;
		flex-direction: row;
		padding-left: 18px;
		padding-right: 18px;
		padding-top: 18px;
		align-items: flex-end;
		z-index: 1;
	}

	.avatar-wrap {
		width: 78px;
		height: 78px;
		border-radius: 39px;
		background-color: #eef2f7;
		border-width: 4px;
		border-color: rgba(255, 255, 255, 0.88);
		overflow: hidden;
		margin-right: 14px;
	}

	.avatar-image {
		width: 78px;
		height: 78px;
		border-radius: 39px;
	}

	.avatar-placeholder {
		width: 100%;
		height: 100%;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
	}

	.avatar-placeholder-head {
		width: 30%;
		height: 30%;
		border-radius: 999px;
		background-color: #98a2b3;
		margin-bottom: 4px;
	}

	.avatar-placeholder-body {
		width: 54%;
		height: 26%;
		border-radius: 999px 999px 0 0;
		background-color: #98a2b3;
	}

	.hero-main {
		flex: 1;
		padding-top: 0;
		min-width: 0;
	}

	.name-row {
		flex-direction: row;
		align-items: center;
		padding-right: 6px;
	}

	.nickname {
		font-size: 22px;
		line-height: 30px;
		font-weight: 900;
		color: #ffffff;
		margin-right: 6px;
		max-width: 118px;
		overflow: hidden;
		text-shadow: 0 1px 7px rgba(0, 0, 0, 0.42);
	}

	.private-chip {
		flex-shrink: 0;
		height: 24px;
		padding-left: 10px;
		padding-right: 10px;
		border-radius: 12px;
		background-color: rgba(255, 255, 255, 0.82);
		font-size: 12px;
		line-height: 24px;
		font-weight: 800;
		color: #007aff;
	}

	.username,
	.bio {
		font-size: 13px;
		line-height: 19px;
		color: rgba(255, 255, 255, 0.9);
		text-shadow: 0 1px 6px rgba(0, 0, 0, 0.38);
	}

	.item-meta,
	.simple-desc,
	.simple-note,
	.empty-desc {
		font-size: 13px;
		line-height: 19px;
		color: #667085;
	}

	.bio {
		margin-top: 4px;
	}

	.stat-row {
		position: relative;
		flex-direction: row;
		padding: 6px 18px 2px;
		z-index: 1;
	}

	.stat-item {
		flex: 1;
		align-items: center;
	}

	.stat-number {
		font-size: 20px;
		line-height: 28px;
		font-weight: 900;
		color: #ffffff;
		text-shadow: 0 1px 7px rgba(0, 0, 0, 0.42);
	}

	.stat-label {
		font-size: 12px;
		color: rgba(255, 255, 255, 0.78);
		text-shadow: 0 1px 5px rgba(0, 0, 0, 0.36);
	}

	.profile-meta {
		position: relative;
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 12px;
		padding-left: 18px;
		padding-right: 18px;
		padding-bottom: 4px;
		z-index: 1;
	}

	.meta-chip {
		margin-right: 8px;
		margin-bottom: 8px;
		padding: 6px 10px;
		border-radius: 14px;
		background-color: rgba(255, 255, 255, 0.82);
		font-size: 12px;
		color: #344054;
	}

	.meta-chip-gender {
		background-color: rgba(255, 255, 255, 0.82);
		color: #344054;
	}

	.meta-chip-male {
		background-color: #eef5ff;
		color: #007aff;
	}

	.meta-chip-female {
		background-color: #fff0f5;
		color: #ff2d55;
	}

	.action-row {
		position: relative;
		padding: 0 18px 18px;
		z-index: 1;
	}

	.follow-button,
	.secondary-button {
		height: 44px;
		border-radius: 22px;
		align-items: center;
		justify-content: center;
		background-color: #111827;
	}

	.follow-button-active,
	.secondary-button {
		background-color: #eef5ff;
	}

	.follow-button-text,
	.secondary-button-text {
		font-size: 15px;
		font-weight: 900;
		color: #ffffff;
	}

	.follow-button-text-active,
	.secondary-button-text {
		color: #007aff;
	}

	.segmented {
		flex-direction: row;
		background-color: #e9edf5;
		border-radius: 18px;
		padding: 4px;
		margin-bottom: 14px;
		position: relative;
	}

	.segment,
	.segment-active {
		flex: 1;
		height: 36px;
		border-radius: 14px;
		align-items: center;
		justify-content: center;
		z-index: 1;
	}

	.segment-indicator {
		position: absolute;
		left: 4px;
		top: 4px;
		width: calc((100% - 8px) / 2);
		height: 36px;
		border-radius: 14px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
		transition-property: transform;
		transition-duration: 240ms;
		transition-timing-function: ease-out;
	}

	.segment-indicator-0 { transform: translateX(0); }
	.segment-indicator-1 { transform: translateX(100%); }

	.segment-text,
	.segment-active-text {
		font-size: 12px;
	}

	.segment-text {
		color: #667085;
	}

	.segment-active-text {
		color: #111827;
		font-weight: 800;
	}

	.content-list {
		flex-direction: column;
	}

	.post-card,
	.simple-card,
	.empty-card {
		padding: 16px;
		margin-bottom: 10px;
	}

	.post-content,
	.simple-title,
	.empty-title {
		font-size: 16px;
		line-height: 23px;
		font-weight: 800;
		color: #111827;
	}

	.thumb-grid {
		flex-direction: row;
		margin-top: 10px;
		margin-bottom: 10px;
	}

	.thumb {
		width: 88px;
		height: 88px;
		border-radius: 16px;
		margin-right: 8px;
		background-color: #eef2f7;
	}

	.simple-desc,
	.simple-note,
	.item-meta {
		margin-top: 6px;
	}

	.build-link-card-row-wrap {
		min-height: 70px;
		border-radius: 22px;
		background-color: #f6f9ff;
		border-width: 1px;
		border-color: #e8eef8;
		flex-direction: row;
		align-items: center;
		padding: 12px;
		margin-bottom: 10px;
	}

	.build-link-icon {
		width: 42px;
		height: 42px;
		border-radius: 16px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 12px;
	}

	.build-link-card {
		width: 20px;
		height: 24px;
		border-radius: 5px;
		background-color: #ffffff;
		padding: 4px;
	}

	.build-link-card-head {
		width: 12px;
		height: 3px;
		border-radius: 2px;
		background-color: #2f80ed;
		margin-bottom: 4px;
	}

	.build-link-card-row {
		width: 12px;
		height: 3px;
		border-radius: 2px;
		background-color: #d0d5dd;
		margin-bottom: 3px;
	}

	.build-link-card-row-short {
		width: 8px;
		margin-bottom: 0;
	}

	.build-link-main {
		flex: 1;
	}

	.build-link-title {
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
	}

	.build-link-desc {
		font-size: 12px;
		line-height: 17px;
		color: #667085;
		margin-top: 2px;
	}

	.build-link-arrow {
		font-size: 28px;
		line-height: 28px;
		color: #98a2b3;
		margin-left: 8px;
	}

	.favorite-news-card {
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		margin-bottom: 12px;
		overflow: hidden;
	}

	.favorite-news-cover-wrap {
		position: relative;
		height: 148px;
		background-color: #eef2f7;
	}

	.favorite-news-cover {
		width: 100%;
		height: 148px;
	}

	.favorite-news-cover-empty {
		align-items: center;
		justify-content: center;
		background-color: #eef2f7;
	}

	.favorite-news-cover-text {
		font-size: 15px;
		font-weight: 900;
		color: #98a2b3;
	}

	.favorite-news-badge {
		position: absolute;
		left: 14px;
		bottom: 12px;
		height: 28px;
		padding-left: 10px;
		padding-right: 10px;
		border-radius: 14px;
		background-color: rgba(17, 24, 39, 0.74);
		align-items: center;
		justify-content: center;
	}

	.favorite-news-badge-text {
		font-size: 12px;
		font-weight: 900;
		color: #ffffff;
	}

	.favorite-news-body {
		padding: 15px;
	}

	.favorite-news-title {
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.favorite-news-summary {
		margin-top: 7px;
		font-size: 14px;
		line-height: 22px;
		color: #667085;
		display: -webkit-box;
		-webkit-line-clamp: 4;
		-webkit-box-orient: vertical;
		max-height: 88px;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.favorite-news-meta {
		flex-direction: row;
		align-items: center;
		margin-top: 10px;
	}

	.favorite-news-meta-text,
	.favorite-news-meta-dot {
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.favorite-news-meta-dot {
		margin-left: 6px;
		margin-right: 6px;
	}

	.empty-card {
		align-items: center;
		padding-top: 34px;
		padding-bottom: 34px;
	}

	.empty-desc {
		margin-top: 6px;
	}

	.bottom-space {
		height: 28px;
	}

	.preview-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(17, 24, 39, 0.46);
		z-index: 90;
		justify-content: flex-end;
	}

	.preview-panel {
		background-color: #ffffff;
		border-top-left-radius: 28px;
		border-top-right-radius: 28px;
		padding: 16px;
		padding-bottom: calc(18px + env(safe-area-inset-bottom));
		animation-name: sheetUp;
		animation-duration: 240ms;
		animation-timing-function: ease-out;
	}

	.preview-image-wrap {
		height: 204px;
		border-radius: 22px;
		background-color: #ffffff;
		overflow: hidden;
		align-items: center;
		justify-content: center;
	}

	.preview-image {
		width: 100%;
		height: 204px;
	}

	.preview-empty {
		width: 100%;
		height: 204px;
		align-items: center;
		justify-content: center;
		background-color: #ffffff;
	}

	.preview-empty-text {
		font-size: 14px;
		font-weight: 800;
		color: #667085;
	}

	.preview-action {
		height: 48px;
		border-radius: 24px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-top: 12px;
	}

	.preview-action-text {
		font-size: 15px;
		font-weight: 900;
		color: #ffffff;
	}

	.build-detail {
		background-color: #ffffff;
		border-top-left-radius: 28px;
		border-top-right-radius: 28px;
		padding: 18px;
		padding-bottom: calc(20px + env(safe-area-inset-bottom));
		max-height: 78vh;
		animation-name: sheetUp;
		animation-duration: 240ms;
		animation-timing-function: ease-out;
	}

	@keyframes sheetUp {
		from {
			opacity: 0;
			transform: translateY(36px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.detail-head {
		position: relative;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		min-height: 42px;
	}

	.detail-title {
		font-size: 17px;
		line-height: 24px;
		font-weight: 900;
		color: #111827;
		text-align: center;
	}

	.detail-close {
		position: absolute;
		right: 0;
		top: 2px;
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #f2f4f7;
		align-items: center;
		justify-content: center;
	}

	.close-line {
		position: absolute;
		width: 15px;
		height: 2px;
		border-radius: 2px;
		background-color: #667085;
		transform: rotate(45deg);
	}

	.close-line-two {
		transform: rotate(-45deg);
	}

	.detail-metrics {
		flex-direction: row;
		background-color: #f6f9ff;
		border-radius: 20px;
		padding: 14px;
		margin-top: 12px;
		margin-bottom: 12px;
	}

	.detail-metric {
		flex: 1;
	}

	.detail-label {
		font-size: 12px;
		font-weight: 800;
		color: #98a2b3;
	}

	.detail-value {
		font-size: 20px;
		line-height: 28px;
		font-weight: 900;
		color: #111827;
		margin-top: 2px;
	}

	.detail-scroll {
		max-height: 430px;
	}

	.detail-part-row {
		border-radius: 18px;
		background-color: #f8fafc;
		padding: 12px;
		margin-bottom: 8px;
	}

	.detail-part-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.detail-part-meta,
	.detail-note {
		font-size: 12px;
		line-height: 18px;
		color: #667085;
		margin-top: 4px;
	}
</style>

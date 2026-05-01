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
					<view class="avatar-wrap" @click.stop="previewAvatar">
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
					<view class="stat-item" @click.stop="openFollowList('following')">
						<text class="stat-number">{{ space.followingCount }}</text>
						<text class="stat-label">关注</text>
					</view>
					<view class="stat-item" @click.stop="openFollowList('followers')">
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
					<view class="post-content mention-content">
						<text v-for="(token, index) in buildMentionTokens(post.content, post.mentions)" :key="'space-post-mention-' + post.id + '-' + index" :class="token.userId > 0 ? 'mention-token mention-token-active post-mention-token' : 'mention-token post-mention-token'" @click.stop="openMentionToken(token)">{{ token.text }}</text>
					</view>
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
					<view v-if="item.type == 'build'" class="favorite-build-card" @click="openBuildDetail(item.build)">
						<view class="favorite-build-icon">
							<view class="build-link-card">
								<view class="build-link-card-head"></view>
								<view class="build-link-card-row"></view>
								<view class="build-link-card-row build-link-card-row-short"></view>
							</view>
						</view>
						<view class="favorite-build-main">
							<view class="favorite-build-head">
								<text class="favorite-build-title">{{ item.build.title }}</text>
								<text class="favorite-build-chip">配置单</text>
							</view>
							<text v-if="item.build.serialNumber.length > 0" class="favorite-build-serial">{{ item.build.serialNumber }}</text>
						</view>
						<text class="favorite-build-arrow">›</text>
					</view>
					<view v-if="item.type == 'post'" class="favorite-post-card" @click="openPostDetail(item.post.id)">
						<view class="favorite-post-main">
							<view class="favorite-post-content mention-content">
								<text v-for="(token, index) in buildMentionTokens(item.post.content, item.post.mentions)" :key="'favorite-post-mention-' + item.post.id + '-' + index" :class="token.userId > 0 ? 'mention-token mention-token-active post-mention-token' : 'mention-token post-mention-token'" @click.stop="openMentionToken(token)">{{ token.text }}</text>
							</view>
							<text class="favorite-post-meta">收藏动态 · {{ item.post.author.nickname }} · {{ formatTime(item.time) }}</text>
						</view>
						<image v-if="item.post.imageUrls.length > 0" class="favorite-post-thumb" :src="item.post.imageUrls[0]" mode="aspectFill"></image>
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
				<text v-if="selectedBuild.serialNumber.length > 0" class="detail-serial">{{ selectedBuild.serialNumber }}</text>
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
						<image v-if="part.productImage.length > 0" class="detail-part-image" :src="part.productImage" mode="aspectFill"></image>
						<view v-else class="detail-part-image detail-part-fallback">
							<text class="detail-part-fallback-text">{{ getHardwareTypeShortLabel(part.type) }}</text>
						</view>
						<view class="detail-part-main">
							<text class="detail-part-title">{{ getCompactPartTitle(part, 46) }}</text>
							<text class="detail-part-meta">{{ getHardwareTypeLabel(part.type) }} · {{ part.matchedModel.length > 0 ? part.matchedModel : part.model }} · {{ getPartPriceLabel(part) }}</text>
							<text class="detail-part-product">{{ getPartSpecText(part) }}</text>
						</view>
					</view>
					<text v-if="selectedBuild.note.length > 0" class="detail-note">备注：{{ selectedBuild.note }}</text>
				</scroll-view>
			</view>
		</view>
		<view v-if="imagePreviewVisible" class="inline-image-preview" @click="closeImagePreview">
			<view class="inline-preview-top">
				<view class="inline-preview-back" @click.stop="closeImagePreview">
					<view class="back-chevron">
						<view class="back-chevron-line back-chevron-line-top inline-preview-chevron-line"></view>
						<view class="back-chevron-line back-chevron-line-bottom inline-preview-chevron-line"></view>
					</view>
				</view>
				<text class="inline-preview-count">{{ imagePreviewIndex + 1 }}/{{ imagePreviewUrls.length }}</text>
				<view class="inline-preview-back inline-preview-placeholder"></view>
			</view>
			<movable-area class="inline-preview-area" :scale-area="true" @click.stop @wheel.stop="handleImagePreviewWheel">
				<movable-view class="inline-preview-view" direction="all" :scale="true" :scale-min="0.5" :scale-max="4" :scale-value="imagePreviewScale">
					<image :class="imagePreviewAvatarMode ? 'inline-preview-image inline-preview-avatar-image' : 'inline-preview-image'" :src="imagePreviewCurrent" :mode="imagePreviewAvatarMode ? 'aspectFill' : 'aspectFit'"></image>
				</movable-view>
			</movable-area>
			<view v-if="imagePreviewUrls.length > 1" class="inline-preview-bottom">
				<view :class="imagePreviewIndex == 0 ? 'inline-preview-switch inline-preview-switch-disabled' : 'inline-preview-switch'" @click.stop="previousPreviewImage">
					<text class="inline-preview-switch-text">上一张</text>
				</view>
				<view :class="imagePreviewIndex == imagePreviewUrls.length - 1 ? 'inline-preview-switch inline-preview-switch-disabled' : 'inline-preview-switch'" @click.stop="nextPreviewImage">
					<text class="inline-preview-switch-text">下一张</text>
				</view>
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onLoad, onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl } from '../../common/api'
	import { MentionItem, MentionToken, buildMentionTokens, normalizeMentions } from '../../common/mention'

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
		productTitle : string
		productPrice : number
		productImage : string
		productLink : string
		skuId : string
		matchedModel : string
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
		serialNumber : string
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
		mentions : MentionItem[]
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
	const imagePreviewVisible = ref(false)
	const imagePreviewUrls = ref([] as string[])
	const imagePreviewIndex = ref(0)
	const imagePreviewCurrent = ref('')
	const imagePreviewScale = ref(1)
	const imagePreviewAvatarMode = ref(false)
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
			serialNumber: '',
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
			value.favoriteBuilds[i] = normalizeBuild(value.favoriteBuilds[i])
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

	function normalizeBuild(build : BuildConfigItem) : BuildConfigItem {
		build.parts = build.parts == null ? [] as HardwarePartItem[] : build.parts
		build.serialNumber = (build as any).serialNumber == null ? '' : (build as any).serialNumber as string
		build.note = build.note == null ? '' : build.note
		build.updatedAt = build.updatedAt == null ? '' : build.updatedAt
		for (let i = 0; i < build.parts.length; i++) {
			build.parts[i] = normalizeBuildPart(build.parts[i])
		}
		return build
	}

	function normalizeBuildPart(part : HardwarePartItem) : HardwarePartItem {
		part.brand = part.brand == null ? '' : part.brand
		part.model = part.model == null ? '' : part.model
		part.productTitle = (part as any).productTitle == null ? '' : (part as any).productTitle as string
		part.productPrice = (part as any).productPrice == null ? 0 : Number((part as any).productPrice)
		part.productImage = normalizeMediaUrl((part as any).productImage == null ? '' : (part as any).productImage as string)
		part.productLink = (part as any).productLink == null ? '' : (part as any).productLink as string
		part.skuId = (part as any).skuId == null ? '' : (part as any).skuId as string
		part.matchedModel = (part as any).matchedModel == null ? '' : (part as any).matchedModel as string
		part.socketType = part.socketType == null ? '' : part.socketType
		part.memoryType = part.memoryType == null ? '' : part.memoryType
		part.formFactor = part.formFactor == null ? '' : part.formFactor
		part.specs = part.specs == null ? '' : part.specs
		return part
	}

	function normalizePost(post : CommunityPostItem) : CommunityPostItem {
		if (post.author == null) {
			post.author = { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem
		}
		if (post.author.avatarUrl != null) {
			post.author.avatarUrl = normalizeMediaUrl(post.author.avatarUrl)
		}
		post.mentions = normalizeMentions((post as any).mentions)
		post.imageUrls = post.imageUrls == null ? [] as string[] : post.imageUrls
		for (let i = 0; i < post.imageUrls.length; i++) {
			post.imageUrls[i] = normalizeMediaUrl(post.imageUrls[i])
		}
		return post
	}

	function openMentionToken(token : MentionToken) {
		if (token.userId > 0) {
			openUserSpace(token.userId)
		}
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

	function previewAvatar() {
		if (space.value.avatarUrl.length == 0) {
			return
		}
		openImagePreview([space.value.avatarUrl], space.value.avatarUrl, true)
	}

	function openImagePreview(images : string[], current : string, avatarMode : boolean = false) {
		const normalized = [] as string[]
		for (let i = 0; i < images.length; i++) {
			normalized.push(normalizeMediaUrl(images[i]))
		}
		imagePreviewUrls.value = normalized
		imagePreviewIndex.value = 0
		for (let i = 0; i < normalized.length; i++) {
			if (normalized[i] == current) {
				imagePreviewIndex.value = i
				break
			}
		}
		imagePreviewCurrent.value = normalizeMediaUrl(current)
		imagePreviewScale.value = 1
		imagePreviewAvatarMode.value = avatarMode
		imagePreviewVisible.value = true
	}

	function closeImagePreview() {
		imagePreviewVisible.value = false
		imagePreviewScale.value = 1
		imagePreviewAvatarMode.value = false
	}

	function previousPreviewImage() {
		if (imagePreviewIndex.value <= 0) {
			return
		}
		imagePreviewIndex.value = imagePreviewIndex.value - 1
		imagePreviewCurrent.value = imagePreviewUrls.value[imagePreviewIndex.value]
		imagePreviewScale.value = 1
	}

	function nextPreviewImage() {
		if (imagePreviewIndex.value >= imagePreviewUrls.value.length - 1) {
			return
		}
		imagePreviewIndex.value = imagePreviewIndex.value + 1
		imagePreviewCurrent.value = imagePreviewUrls.value[imagePreviewIndex.value]
		imagePreviewScale.value = 1
	}

	function handleImagePreviewWheel(event : any) {
		const delta = event.deltaY as number
		if (delta == 0) {
			return
		}
		let nextScale = imagePreviewScale.value + (delta < 0 ? 0.16 : -0.16)
		if (nextScale < 0.5) {
			nextScale = 0.5
		}
		if (nextScale > 4) {
			nextScale = 4
		}
		imagePreviewScale.value = nextScale
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
		selectedBuild.value = normalizeBuild(build)
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

	function openFollowList(type : string) {
		if (isSpaceHidden() || space.value.userId <= 0) {
			return
		}
		uni.navigateTo({
			url: '/pages/user/follows?userId=' + space.value.userId + '&type=' + type,
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

	function getHardwareTypeShortLabel(type : string) : string {
		if (type == 'MOTHERBOARD') {
			return 'MB'
		}
		if (type == 'STORAGE') {
			return 'SSD'
		}
		if (type == 'COOLER') {
			return '散'
		}
		return getHardwareTypeLabel(type).slice(0, 2)
	}

	function getCompactPartTitle(part : HardwarePartItem, maxLength : number) : string {
		const title = part.productTitle != null && part.productTitle.length > 0 ? part.productTitle : part.model
		if (title.length <= maxLength) {
			return title
		}
		return title.slice(0, maxLength) + '...'
	}

	function getPartPriceLabel(part : HardwarePartItem) : string {
		const price = part.productPrice != null && part.productPrice > 0 ? part.productPrice : part.price
		return price > 0 ? '¥' + price : '暂无价格'
	}

	function getBuildCardDesc(build : BuildConfigItem) : string {
		const serial = build.serialNumber.length > 0 ? build.serialNumber + ' · ' : ''
		return serial + '¥' + build.totalPrice + ' · 性能 ' + build.performanceScore + ' 分'
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
		height: 100vh;
		width: 100vw;
		background-color: #f6f7fb;
		padding-left: 16px;
		padding-right: 16px;
		box-sizing: border-box;
		overflow: hidden;
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

	.space-nav .nav-button .back-chevron {
		position: relative;
		left: auto;
		top: auto;
	}

	.space-nav .nav-button .back-chevron-line {
		background-color: #111827;
	}

	.space-nav .nav-button .back-chevron-line-top {
		top: 4px;
		transform: rotate(-45deg);
	}

	.space-nav .nav-button .back-chevron-line-bottom {
		top: 10px;
		transform: rotate(45deg);
	}

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
		height: auto;
		min-height: 0;
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

	.space-page .space-segmented .segment-indicator {
		width: calc((100% - 8px) / 2);
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

	.mention-content {
		flex-direction: row;
		flex-wrap: wrap;
		align-items: flex-start;
	}

	.mention-token {
		white-space: pre-wrap;
		color: #111827;
	}

	.mention-token-active {
		color: #0a84ff;
		font-weight: 900;
	}

	.post-mention-token {
		font-size: 16px;
		line-height: 23px;
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

	.favorite-build-card {
		min-height: 76px;
		border-radius: 26px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #dbe8ff;
		flex-direction: row;
		align-items: center;
		padding: 14px;
		margin-bottom: 12px;
		box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
	}

	.favorite-build-icon {
		width: 48px;
		height: 48px;
		border-radius: 16px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 12px;
		flex-shrink: 0;
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

	.favorite-build-main {
		flex: 1;
		min-width: 0;
	}

	.favorite-build-head {
		width: 100%;
		flex-direction: row;
		align-items: center;
		min-width: 0;
	}

	.favorite-build-title {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.favorite-build-chip {
		height: 24px;
		border-radius: 12px;
		background-color: #e8f2ff;
		padding-left: 9px;
		padding-right: 9px;
		margin-left: 8px;
		font-size: 11px;
		line-height: 24px;
		font-weight: 900;
		color: #007aff;
		flex-shrink: 0;
	}

	.favorite-build-serial {
		font-size: 12px;
		line-height: 17px;
		color: #667085;
		margin-top: 4px;
	}

	.favorite-build-metrics {
		width: 100%;
		flex-direction: row;
		margin-top: 10px;
	}

	.favorite-build-metric {
		flex: 1;
		min-width: 0;
		border-radius: 16px;
		background-color: #f6f9ff;
		padding: 9px 10px;
		margin-right: 8px;
	}

	.favorite-build-metric:last-child {
		margin-right: 0;
	}

	.favorite-build-metric-label {
		font-size: 11px;
		line-height: 16px;
		font-weight: 800;
		color: #98a2b3;
	}

	.favorite-build-metric-value {
		margin-top: 2px;
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
	}

	.favorite-build-arrow {
		font-size: 28px;
		line-height: 28px;
		color: #98a2b3;
		margin-left: 8px;
		flex-shrink: 0;
	}

	.favorite-post-card {
		width: 100%;
		min-height: 92px;
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding: 14px 16px;
		margin-bottom: 10px;
		flex-direction: row;
		align-items: center;
		overflow: hidden;
	}

	.favorite-post-main {
		flex: 1;
		min-width: 0;
		padding-right: 12px;
	}

	.favorite-post-content {
		width: 100%;
		max-height: 48px;
		font-size: 16px;
		line-height: 24px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
	}

	.favorite-post-meta {
		margin-top: 6px;
		font-size: 13px;
		line-height: 18px;
		font-weight: 700;
		color: #667085;
	}

	.favorite-post-thumb {
		width: 72px;
		height: 72px;
		border-radius: 18px;
		background-color: #eef2f7;
		flex-shrink: 0;
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

	.detail-serial {
		margin-top: 2px;
		margin-bottom: 10px;
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #007aff;
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
		margin-top: 12px;
		margin-bottom: 12px;
	}

	.detail-metric {
		flex: 1;
		min-width: 0;
		border-radius: 18px;
		background-color: #f6f9ff;
		padding: 14px;
		margin-right: 10px;
	}

	.detail-metric:last-child {
		margin-right: 0;
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
		flex-direction: row;
		align-items: center;
		gap: 10px;
		border-radius: 18px;
		background-color: #f8fafc;
		padding: 12px;
		margin-bottom: 8px;
	}

	.detail-part-image {
		width: 54px;
		height: 54px;
		border-radius: 16px;
		background-color: #eef2f7;
	}

	.detail-part-fallback {
		align-items: center;
		justify-content: center;
	}

	.detail-part-fallback-text {
		font-size: 12px;
		font-weight: 900;
		color: #98a2b3;
	}

	.detail-part-main {
		flex: 1;
		min-width: 0;
	}

	.detail-part-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.detail-part-meta,
	.detail-part-product,
	.detail-note {
		font-size: 12px;
		line-height: 18px;
		color: #667085;
		margin-top: 4px;
	}

	.detail-part-product {
		color: #98a2b3;
	}

	.inline-image-preview {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(5, 7, 13, 0.84);
		z-index: 80;
	}

	.inline-preview-top {
		position: fixed;
		left: 18px;
		right: 18px;
		top: 44px;
		top: calc(44px + constant(safe-area-inset-top));
		top: calc(44px + env(safe-area-inset-top));
		top: calc(20px + var(--status-bar-height));
		width: auto;
		max-width: none;
		height: 42px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		z-index: 82;
	}

	.inline-preview-back {
		position: relative;
		width: 42px;
		height: 42px;
		border-radius: 21px;
		background-color: rgba(17, 24, 39, 0.86);
		align-items: center;
		justify-content: center;
	}

	.inline-preview-chevron-line {
		background-color: #ffffff;
	}

	.inline-preview-placeholder {
		background-color: transparent;
	}

	.inline-preview-count {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #ffffff;
		text-shadow: 0 1px 8px rgba(17, 24, 39, 0.42);
	}

	.inline-preview-area,
	.inline-preview-view {
		width: 100%;
		height: 100vh;
	}

	.inline-preview-view {
		align-items: center;
		justify-content: center;
	}

	.inline-preview-image {
		width: 100%;
		height: 100vh;
	}

	.inline-preview-avatar-image {
		width: 72vw;
		height: 72vw;
		max-width: 360px;
		max-height: 360px;
		border-radius: 28px;
	}

	.inline-preview-bottom {
		position: fixed;
		left: 24px;
		right: 24px;
		bottom: 28px;
		bottom: calc(28px + constant(safe-area-inset-bottom));
		bottom: calc(28px + env(safe-area-inset-bottom));
		width: auto;
		max-width: none;
		flex-direction: row;
		justify-content: space-between;
		z-index: 82;
	}

	.inline-preview-switch {
		height: 42px;
		border-radius: 21px;
		background-color: rgba(17, 24, 39, 0.78);
		padding-left: 18px;
		padding-right: 18px;
		align-items: center;
		justify-content: center;
	}

	.inline-preview-switch-disabled {
		opacity: 0.35;
	}

	.inline-preview-switch-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #ffffff;
	}
</style>

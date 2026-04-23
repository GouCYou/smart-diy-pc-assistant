<template>
	<view :class="pageLeaving ? 'post-page post-page-leave' : 'post-page'">
		<view class="status-space"></view>
		<view class="post-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">帖子详情</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="post-scroll" scroll-y="true">
			<view v-if="post.id > 0" class="post-card">
				<view class="post-head">
					<view class="post-avatar">
						<image v-if="post.author.avatarUrl.length > 0" class="post-avatar-image" :src="normalizeMediaUrl(post.author.avatarUrl)" mode="aspectFill"></image>
						<view v-else class="avatar-placeholder">
							<view class="avatar-placeholder-head"></view>
							<view class="avatar-placeholder-body"></view>
						</view>
					</view>
					<view class="post-author-main">
						<text class="post-author-name">{{ post.author.nickname }}</text>
						<text class="post-time">{{ formatTime(post.createdAt) }}</text>
					</view>
					<view v-if="isMine(post.author.id)" class="comment-delete post-head-delete" @click="confirmDeletePost">
						<text class="comment-delete-text">删除</text>
					</view>
				</view>

				<text class="post-content">{{ post.content }}</text>

				<view v-if="post.imageUrls.length > 0" class="image-grid">
					<image v-for="image in post.imageUrls" :key="image" class="post-image" :src="normalizeMediaUrl(image)" mode="aspectFill" @click="previewPostImage(image)"></image>
				</view>

				<view v-if="post.buildConfig != null && post.buildConfig.id > 0" class="build-card" @click="openBuildDetail(post.buildConfig)">
					<view class="build-icon">
						<view class="build-card-mark">
							<view class="build-card-head"></view>
							<view class="build-card-row"></view>
							<view class="build-card-row build-card-row-short"></view>
						</view>
					</view>
					<view class="build-main">
						<text class="build-title">{{ post.buildConfig.title }}</text>
						<text class="build-desc">¥{{ post.buildConfig.totalPrice }} · 性能 {{ post.buildConfig.performanceScore }} 分</text>
					</view>
					<text class="build-arrow">›</text>
				</view>

				<view v-if="false" class="build-detail">
					<view v-for="part in post.buildConfig.parts" :key="part.id" class="build-part">
						<text class="build-part-title">{{ getHardwareTypeLabel(part.type) }} · {{ part.brand }} {{ part.model }}</text>
						<text class="build-part-desc">¥{{ part.price }} · 性能 {{ part.performanceScore }} · {{ getPartSpecText(part) }}</text>
					</view>
					<text v-if="post.buildConfig.note.length > 0" class="build-note">备注：{{ post.buildConfig.note }}</text>
				</view>

				<view class="post-actions">
					<view class="post-action" @click="togglePostLike">
						<text :class="post.liked ? 'post-action-icon post-like-active' : 'post-action-icon'">♥</text>
						<text class="post-action-text">{{ post.likeCount }}</text>
					</view>
					<view class="post-action" @click="togglePostFavorite">
						<text :class="post.favorite ? 'post-action-icon post-favorite-active' : 'post-action-icon'">★</text>
						<text class="post-action-text">{{ post.favoriteCount }}</text>
					</view>
				</view>
			</view>

			<view v-if="post.id > 0" class="comment-section">
				<view class="comment-headline">
					<text class="comment-title">评论 {{ post.commentCount }}</text>
					<view class="sort-switch">
						<view :class="commentSort == 'latest' ? 'sort-item sort-item-active' : 'sort-item'" @click="setCommentSort('latest')">
							<text :class="commentSort == 'latest' ? 'sort-text sort-text-active' : 'sort-text'">最新</text>
						</view>
						<view :class="commentSort == 'likes' ? 'sort-item sort-item-active' : 'sort-item'" @click="setCommentSort('likes')">
							<text :class="commentSort == 'likes' ? 'sort-text sort-text-active' : 'sort-text'">点赞</text>
						</view>
					</view>
				</view>

				<view v-for="comment in getPagedComments()" :key="comment.id" class="comment-card">
					<view class="comment-top">
						<view class="comment-avatar">
							<image v-if="comment.author.avatarUrl.length > 0" class="comment-avatar-image" :src="normalizeMediaUrl(comment.author.avatarUrl)" mode="aspectFill"></image>
							<view v-else class="avatar-placeholder avatar-placeholder-small">
								<view class="avatar-placeholder-head"></view>
								<view class="avatar-placeholder-body"></view>
							</view>
						</view>
						<view class="comment-main">
							<text class="comment-author">{{ comment.author.nickname }}</text>
							<text class="comment-time">{{ formatTime(comment.createdAt) }}</text>
						</view>
						<view class="comment-like" @click="toggleCommentLike(comment.id)">
							<text :class="comment.liked ? 'comment-like-icon comment-like-active' : 'comment-like-icon'">♥</text>
							<text class="comment-like-text">{{ comment.likeCount }}</text>
						</view>
						<view v-if="isMine(comment.author.id)" class="comment-delete" @click="confirmDeleteComment(comment.id)">
							<text class="comment-delete-text">删除</text>
						</view>
					</view>
					<text class="comment-content">{{ comment.content }}</text>
					<view v-if="comment.imageUrls.length > 0" class="comment-image-grid">
						<image v-for="image in comment.imageUrls" :key="image" class="comment-image" :src="normalizeMediaUrl(image)" mode="aspectFill" @click="previewCommentImage(comment.imageUrls, image)"></image>
					</view>
					<view v-if="comment.buildConfig != null && comment.buildConfig.id > 0" class="comment-build-card" @click="openBuildDetail(comment.buildConfig)">
						<view class="comment-build-icon">
							<view class="comment-build-card-mark">
								<view class="comment-build-card-head"></view>
								<view class="comment-build-card-row"></view>
								<view class="comment-build-card-row comment-build-card-row-short"></view>
							</view>
						</view>
						<view class="comment-build-main">
							<text class="comment-build-title">{{ comment.buildConfig.title }}</text>
							<text class="comment-build-desc">¥{{ comment.buildConfig.totalPrice }} · 性能 {{ comment.buildConfig.performanceScore }} 分</text>
						</view>
						<text class="comment-build-arrow">›</text>
					</view>
				</view>

				<view v-if="getCommentPageCount() > 1" class="list-pager">
					<view :class="commentPage <= 1 ? 'pager-button pager-button-disabled' : 'pager-button'" @click="previousCommentPage">
						<text :class="commentPage <= 1 ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">上一页</text>
					</view>
					<text class="pager-count">{{ commentPage }} / {{ getCommentPageCount() }}</text>
					<view :class="commentPage >= getCommentPageCount() ? 'pager-button pager-button-disabled' : 'pager-button'" @click="nextCommentPage">
						<text :class="commentPage >= getCommentPageCount() ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">下一页</text>
					</view>
				</view>

				<view v-if="post.comments.length == 0" class="comment-empty">
					<text class="comment-empty-title">还没有评论</text>
					<text class="comment-empty-desc">说说你的看法，或者补充装机建议。</text>
				</view>
			</view>
			<view class="post-bottom-spacer"></view>
		</scroll-view>

		<view v-if="commentImageUrls.length > 0" class="comment-draft-images">
			<view v-for="(image, index) in commentImageUrls" :key="image" :class="getCommentDraftImageClass(index)" :style="getCommentDraftImageStyle(index)" @touchstart.stop="startCommentImageTouch(index, $event)" @touchmove.stop="moveCommentImageDrag($event)" @touchend.stop="endCommentImageDrag">
				<image class="draft-image" :src="image" mode="aspectFill" @click="previewCommentImage(commentImageUrls, image)"></image>
				<view v-if="false" class="draft-image-order">
					<text :class="index == 0 ? 'draft-order-text draft-order-disabled' : 'draft-order-text'" @click.stop="moveCommentImageLeft(index)">‹</text>
					<text :class="index == commentImageUrls.length - 1 ? 'draft-order-text draft-order-disabled' : 'draft-order-text'" @click.stop="moveCommentImageRight(index)">›</text>
				</view>
				<view class="draft-image-remove" @click="removeCommentImage(image)">
					<view class="close-line"></view>
					<view class="close-line close-line-two"></view>
				</view>
			</view>
		</view>

		<view v-if="commentBuildId > 0" :class="commentImageUrls.length > 0 ? 'comment-linked-build comment-linked-build-raised' : 'comment-linked-build'" @click="openBuildDetail(commentBuildDetail)">
			<view class="comment-build-icon">
				<view class="comment-build-card-mark">
					<view class="comment-build-card-head"></view>
					<view class="comment-build-card-row"></view>
					<view class="comment-build-card-row comment-build-card-row-short"></view>
				</view>
			</view>
			<view class="comment-build-main">
				<text class="comment-build-title">{{ commentBuildTitle }}</text>
				<text class="comment-build-desc">已关联配置，点击查看详情</text>
			</view>
			<view class="comment-build-remove" @click.stop="clearCommentBuild">
				<view class="close-line"></view>
				<view class="close-line close-line-two"></view>
			</view>
		</view>

		<view class="comment-composer">
			<view class="comment-image-button" @click="pickCommentImages">
				<view class="tool-image-icon">
					<view class="tool-image-sun"></view>
					<view class="tool-image-hill"></view>
				</view>
			</view>
			<input class="comment-input" v-model="commentText" placeholder="写评论" placeholder-class="input-placeholder" />
			<view :class="commentBuildId > 0 ? 'comment-attach-button comment-attach-button-active' : 'comment-attach-button'" @click="openCommentBuildPicker">
				<view class="comment-build-symbol">
					<view class="comment-build-symbol-head"></view>
					<view class="comment-build-symbol-row"></view>
					<view class="comment-build-symbol-row comment-build-symbol-row-short"></view>
				</view>
			</view>
			<view class="comment-send" @click="submitComment">
				<text class="comment-send-text">{{ commentSubmitting ? '...' : '发送' }}</text>
			</view>
		</view>
		<view v-if="commentBuildPickerVisible" class="build-mask" @click="closeCommentBuildPicker">
			<view class="build-picker" @click.stop>
				<view class="build-modal-head">
					<text class="build-modal-title">选择关联配置</text>
					<view class="build-modal-close" @click="closeCommentBuildPicker">
						<view class="close-line build-close-line"></view>
						<view class="close-line close-line-two build-close-line"></view>
					</view>
				</view>
				<scroll-view class="build-picker-scroll" scroll-y="true">
					<view v-for="item in commentBuildList" :key="item.id" class="build-picker-item" @click="toggleBuildForComment(item)">
						<view class="build-picker-main">
							<text class="build-picker-title">{{ item.title }}</text>
							<text class="build-picker-desc">¥{{ item.totalPrice }} · 性能 {{ item.performanceScore }} 分</text>
						</view>
						<view :class="commentBuildId == item.id ? 'build-picker-plus build-picker-plus-selected' : 'build-picker-plus'">
							<text :class="commentBuildId == item.id ? 'build-picker-plus-text build-picker-plus-text-selected' : 'build-picker-plus-text'">{{ commentBuildId == item.id ? '-' : '+' }}</text>
						</view>
					</view>
					<view v-if="commentBuildList.length == 0" class="build-picker-empty">
						<text class="build-picker-empty-text">暂无可关联配置</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<view v-if="buildDetailVisible" class="build-mask" @click="closeBuildDetail">
			<view class="build-modal" @click.stop>
				<view class="build-modal-head">
					<text class="build-modal-title">{{ selectedBuildDetail.title }}</text>
					<view class="build-modal-close" @click="closeBuildDetail">
						<view class="close-line build-close-line"></view>
						<view class="close-line close-line-two build-close-line"></view>
					</view>
				</view>
				<view class="build-modal-stats">
					<view class="build-modal-stat">
						<text class="build-modal-stat-label">总价</text>
						<text class="build-modal-stat-value">¥{{ selectedBuildDetail.totalPrice }}</text>
					</view>
					<view class="build-modal-stat">
						<text class="build-modal-stat-label">性能</text>
						<text class="build-modal-stat-value">{{ selectedBuildDetail.performanceScore }} 分</text>
					</view>
				</view>
				<scroll-view class="build-modal-parts" scroll-y="true">
					<view v-for="part in selectedBuildDetail.parts" :key="part.id" class="build-part">
						<text class="build-part-title">{{ getHardwareTypeLabel(part.type) }} · {{ part.brand }} {{ part.model }}</text>
						<text class="build-part-desc">¥{{ part.price }} · 性能 {{ part.performanceScore }} · {{ getPartSpecText(part) }}</text>
					</view>
					<text v-if="selectedBuildDetail.note.length > 0" class="build-note">备注：{{ selectedBuildDetail.note }}</text>
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
					<image class="inline-preview-image" :src="imagePreviewCurrent" mode="aspectFit"></image>
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
		<view v-if="uploadProgressVisible" class="upload-progress-mask">
			<view class="upload-progress-card">
				<text class="upload-progress-title">{{ uploadProgressTitle }}</text>
				<view class="upload-progress-track">
					<view class="upload-progress-fill" :style="'width: ' + uploadProgressPercent + '%'"></view>
				</view>
				<text class="upload-progress-text">{{ uploadProgressPercent }}%</text>
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onHide, onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl as normalizeRemoteMediaUrl } from '../../common/api'

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
		compatibilityStatus : string
		compatibilityMessages : string[]
		parts : HardwarePartItem[]
		summary : string
		content : string
		source : string
		favorite : boolean
		updatedAt : string
	}

	type CommunityCommentItem = {
		id : number
		postId : number
		author : AuthorItem
		content : string
		imageUrls : string[]
		buildConfig : BuildConfigItem
		likeCount : number
		liked : boolean
		createdAt : string
	}

	type CommunityPostItem = {
		id : number
		author : AuthorItem
		content : string
		imageUrls : string[]
		buildConfig : BuildConfigItem
		likeCount : number
		commentCount : number
		favoriteCount : number
		liked : boolean
		favorite : boolean
		createdAt : string
		comments : CommunityCommentItem[]
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const currentUserId = ref(0)
	const postId = ref(0)
	const post = ref(createEmptyPost())
	const pageLeaving = ref(false)
	const loading = ref(false)
	const commentSort = ref('latest')
	const commentPage = ref(1)
	const commentText = ref('')
	const commentImageUrls = ref([] as string[])
	const commentImageSortActive = ref(false)
	const draggingCommentImageIndex = ref(-1)
	const commentImageDragStartX = ref(0)
	const commentImageDragStartY = ref(0)
	const commentImageDragOffsetX = ref(0)
	const commentImageDragOffsetY = ref(0)
	const commentImageLongPressTimer = ref(0)
	const commentBuildId = ref(0)
	const commentBuildTitle = ref('')
	const commentBuildDetail = ref(createEmptyBuildConfig())
	const commentBuildList = ref([] as BuildConfigItem[])
	const commentBuildPickerVisible = ref(false)
	const commentSubmitting = ref(false)
	const buildDetailVisible = ref(false)
	const selectedBuildDetail = ref(createEmptyBuildConfig())
	const imagePreviewVisible = ref(false)
	const imagePreviewUrls = ref([] as string[])
	const imagePreviewIndex = ref(0)
	const imagePreviewCurrent = ref('')
	const imagePreviewScale = ref(1)
	const uploadProgressVisible = ref(false)
	const uploadProgressTitle = ref('')
	const uploadProgressPercent = ref(0)
	const backendReconnectPending = ref(false)
	let backendReconnectTimer : number = 0
	let backendProbeRunning = false

	loadLocalState()
	loadCurrentUserId()
	loadPostDetail()
	onShow(() => {
		loadLocalState()
		if (backendReconnectPending.value) {
			startBackendReconnectTimer()
		}
		loadCurrentUserId()
		loadPostDetail()
	})
	onHide(() => {
		stopBackendReconnectTimer()
	})

	function createEmptyAuthor() : AuthorItem {
		return { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem
	}

	function createEmptyBuildConfig() : BuildConfigItem {
		return {
			id: 0,
			title: '',
			note: '',
			totalPrice: 0,
			performanceScore: 0,
			compatibilityStatus: '',
			compatibilityMessages: [] as string[],
			parts: [] as HardwarePartItem[],
			summary: '',
			content: '',
			source: '',
			favorite: false,
			updatedAt: ''
		} as BuildConfigItem
	}

	function createEmptyPost() : CommunityPostItem {
		return {
			id: 0,
			author: createEmptyAuthor(),
			content: '',
			imageUrls: [] as string[],
			buildConfig: createEmptyBuildConfig(),
			likeCount: 0,
			commentCount: 0,
			favoriteCount: 0,
			liked: false,
			favorite: false,
			createdAt: '',
			comments: [] as CommunityCommentItem[]
		} as CommunityPostItem
	}

	function loadLocalState() {
		const savedToken = uni.getStorageSync('app_token')
		const savedUserId = uni.getStorageSync('app_user_id')
		const savedPostId = uni.getStorageSync('current_post_id')
		if (savedToken != null) {
			token.value = savedToken as string
		}
		if (savedUserId != null) {
			currentUserId.value = savedUserId as number
		}
		if (savedPostId != null) {
			postId.value = savedPostId as number
		}
	}

	function loadCurrentUserId() {
		if (token.value.length == 0 || currentUserId.value > 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'GET',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const data = body.data as any
					currentUserId.value = data.userId as number
					uni.setStorageSync('app_user_id', currentUserId.value)
				}
			}
		})
	}

	function loadPostDetail() {
		if (loading.value || postId.value == 0) {
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + postId.value,
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					backendReconnectPending.value = false
					post.value = normalizePost(body.data as CommunityPostItem)
					clampCommentPage()
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			fail: () => {
				scheduleBackendReconnect()
				uni.showToast({ title: '暂时连接不到后端服务', icon: 'none' })
			},
			complete: () => {
				loading.value = false
			}
		})
	}

	function normalizePost(value : CommunityPostItem) : CommunityPostItem {
		if (value.author.avatarUrl.length > 0) {
			value.author.avatarUrl = normalizeMediaUrl(value.author.avatarUrl)
		}
		for (let i = 0; i < value.imageUrls.length; i++) {
			value.imageUrls[i] = normalizeMediaUrl(value.imageUrls[i])
		}
		for (let i = 0; i < value.comments.length; i++) {
			if (value.comments[i].author.avatarUrl.length > 0) {
				value.comments[i].author.avatarUrl = normalizeMediaUrl(value.comments[i].author.avatarUrl)
			}
			if (value.comments[i].buildConfig == null) {
				value.comments[i].buildConfig = createEmptyBuildConfig()
			}
			for (let j = 0; j < value.comments[i].imageUrls.length; j++) {
				value.comments[i].imageUrls[j] = normalizeMediaUrl(value.comments[i].imageUrls[j])
			}
		}
		return value
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

	function openAuthPage() {
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function requireLogin() : boolean {
		if (token.value.length > 0) {
			return true
		}
		openAuthPage()
		return false
	}

	function getAuthHeader() : any {
		if (token.value.length == 0) {
			return {}
		}
		return { 'Authorization': 'Bearer ' + token.value }
	}

	function scheduleBackendReconnect() {
		backendReconnectPending.value = true
		startBackendReconnectTimer()
	}

	function startBackendReconnectTimer() {
		if (backendReconnectTimer != 0) {
			return
		}
		probeBackendAndReload()
		backendReconnectTimer = setInterval(() => {
			probeBackendAndReload()
		}, 3000)
	}

	function stopBackendReconnectTimer() {
		if (backendReconnectTimer == 0) {
			return
		}
		clearInterval(backendReconnectTimer)
		backendReconnectTimer = 0
	}

	function probeBackendAndReload() {
		if (!backendReconnectPending.value || backendProbeRunning) {
			return
		}
		backendProbeRunning = true
		uni.request({
			url: apiBaseUrl + '/api/community/posts',
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				if (res.statusCode >= 200 && res.statusCode < 500) {
					backendReconnectPending.value = false
					stopBackendReconnectTimer()
					loadLocalState()
					loadCurrentUserId()
					loadPostDetail()
				}
			},
			complete: () => {
				backendProbeRunning = false
			}
		})
	}

	function openBuildDetail(buildConfig : BuildConfigItem) {
		selectedBuildDetail.value = buildConfig
		buildDetailVisible.value = true
	}

	function closeBuildDetail() {
		buildDetailVisible.value = false
	}

	function togglePostLike() {
		if (!requireLogin()) {
			return
		}
		if (post.value.id == 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + post.value.id + '/like',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const updated = normalizePost(body.data as CommunityPostItem)
					const comments = post.value.comments
					post.value = updated
					post.value.comments = comments
				}
			}
		})
	}

	function togglePostFavorite() {
		if (!requireLogin()) {
			return
		}
		if (post.value.id == 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + post.value.id + '/favorite',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const updated = normalizePost(body.data as CommunityPostItem)
					const comments = post.value.comments
					post.value = updated
					post.value.comments = comments
				}
			}
		})
	}

	function confirmDeletePost() {
		if (post.value.id == 0) {
			return
		}
		uni.showModal({
			title: '删除帖子',
			content: '删除后无法恢复，确定删除吗？',
			confirmText: '删除',
			cancelText: '取消',
			confirmColor: '#ff3b30',
			success: (res) => {
				if (res.confirm) {
					deletePost()
				}
			}
		})
	}

	function deletePost() {
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + post.value.id,
			method: 'DELETE',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					uni.showToast({ title: '已删除', icon: 'none' })
					goBack()
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '删除失败', icon: 'none' })
			}
		})
	}

	function submitComment() {
		if (!requireLogin()) {
			return
		}
		if (commentSubmitting.value || post.value.id == 0) {
			return
		}
		const content = commentText.value.trim()
		if (content.length == 0) {
			uni.showToast({ title: '先写点评论', icon: 'none' })
			return
		}
		commentSubmitting.value = true
		uploadProgressVisible.value = commentImageUrls.value.length > 0
		uploadProgressTitle.value = commentImageUrls.value.length > 0 ? '正在发送' : ''
		uploadProgressPercent.value = 0
		uploadCommentImagesBeforeSubmit(commentImageUrls.value, (uploadedUrls : string[]) => {
			createComment(content, uploadedUrls)
		}, () => {
			commentSubmitting.value = false
			uploadProgressVisible.value = false
		})
	}

	function createComment(content : string, uploadedImageKeys : string[]) {
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + post.value.id + '/comments',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				content: content,
				imageKeys: uploadedImageKeys,
				buildConfigId: commentBuildId.value > 0 ? commentBuildId.value : null
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					if (uploadedImageKeys.length > 0) {
						uploadProgressTitle.value = '上传成功'
						uploadProgressPercent.value = 100
					}
					const comment = body.data as CommunityCommentItem
					if (comment.buildConfig == null) {
						comment.buildConfig = createEmptyBuildConfig()
					}
					for (let i = 0; i < comment.imageUrls.length; i++) {
						comment.imageUrls[i] = normalizeMediaUrl(comment.imageUrls[i])
					}
					post.value.comments.push(comment)
					post.value.commentCount = post.value.commentCount + 1
					commentPage.value = 1
					commentText.value = ''
					commentImageUrls.value = []
					clearCommentBuild()
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '评论失败', icon: 'none' })
			},
			fail: () => {
				uni.showToast({ title: '暂时连接不到后端服务', icon: 'none' })
			},
			complete: () => {
				commentSubmitting.value = false
				setTimeout(() => {
					uploadProgressVisible.value = false
				}, 500)
			}
		})
	}

	function toggleCommentLike(id : number) {
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/comments/' + id + '/like',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const updated = body.data as CommunityCommentItem
					for (let i = 0; i < post.value.comments.length; i++) {
						if (post.value.comments[i].id == updated.id) {
							for (let j = 0; j < updated.imageUrls.length; j++) {
								updated.imageUrls[j] = normalizeMediaUrl(updated.imageUrls[j])
							}
							post.value.comments.splice(i, 1, updated)
							break
						}
					}
				}
			}
		})
	}

	function confirmDeleteComment(id : number) {
		uni.showModal({
			title: '删除评论',
			content: '删除后无法恢复，确定删除吗？',
			confirmText: '删除',
			cancelText: '取消',
			confirmColor: '#ff3b30',
			success: (res) => {
				if (res.confirm) {
					deleteComment(id)
				}
			}
		})
	}

	function deleteComment(id : number) {
		uni.request({
			url: apiBaseUrl + '/api/community/comments/' + id,
			method: 'DELETE',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					post.value.comments = post.value.comments.filter((comment) : boolean => comment.id != id)
					if (post.value.commentCount > 0) {
						post.value.commentCount = post.value.commentCount - 1
					}
					clampCommentPage()
					uni.showToast({ title: '已删除', icon: 'none' })
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '删除失败', icon: 'none' })
			}
		})
	}

	function isMine(authorId : number) : boolean {
		return currentUserId.value > 0 && authorId == currentUserId.value
	}

	function setCommentSort(value : string) {
		commentSort.value = value
		commentPage.value = 1
	}

	function getSortedComments() : CommunityCommentItem[] {
		const next = post.value.comments.slice()
		if (commentSort.value == 'likes') {
			next.sort((a : CommunityCommentItem, b : CommunityCommentItem) : number => b.likeCount - a.likeCount)
		} else {
			next.sort((a : CommunityCommentItem, b : CommunityCommentItem) : number => {
				if (b.createdAt > a.createdAt) {
					return 1
				}
				if (b.createdAt < a.createdAt) {
					return -1
				}
				return 0
			})
		}
		return next
	}

	function getCommentPageCount() : number {
		const total = post.value.comments.length
		if (total == 0) {
			return 1
		}
		return Math.ceil(total / 10)
	}

	function clampCommentPage() {
		const pageCount = getCommentPageCount()
		if (commentPage.value > pageCount) {
			commentPage.value = pageCount
		}
		if (commentPage.value < 1) {
			commentPage.value = 1
		}
	}

	function previousCommentPage() {
		if (commentPage.value <= 1) {
			return
		}
		commentPage.value = commentPage.value - 1
	}

	function nextCommentPage() {
		const pageCount = getCommentPageCount()
		if (commentPage.value >= pageCount) {
			return
		}
		commentPage.value = commentPage.value + 1
	}

	function getPagedComments() : CommunityCommentItem[] {
		const comments = getSortedComments()
		clampCommentPage()
		const start = (commentPage.value - 1) * 10
		return comments.slice(start, start + 10)
	}

	function pickCommentImages() {
		if (!requireLogin()) {
			return
		}
		if (commentImageUrls.value.length >= 9) {
			uni.showToast({ title: '最多添加 9 张图片', icon: 'none' })
			return
		}
		uni.chooseImage({
			count: 9 - commentImageUrls.value.length,
			sizeType: ['compressed'],
			sourceType: ['album', 'camera'],
			success: (chooseRes) => {
				const paths = chooseRes.tempFilePaths as string[]
				for (let i = 0; i < paths.length; i++) {
					if (commentImageUrls.value.length < 9) {
						commentImageUrls.value.push(paths[i])
					}
				}
			}
		})
	}

	function uploadCommentImagesBeforeSubmit(images : string[], complete : (urls : string[]) => void, fail : () => void) {
		if (images.length == 0) {
			complete([] as string[])
			return
		}
		const uploaded = [] as string[]
		uploadCommentImageAt(images, 0, uploaded, complete, fail)
	}

	function uploadCommentImageAt(images : string[], index : number, uploaded : string[], complete : (urls : string[]) => void, fail : () => void) {
		if (index >= images.length) {
			uploadProgressTitle.value = '正在发送'
			uploadProgressPercent.value = 95
			complete(uploaded)
			return
		}
		uploadProgressTitle.value = '正在发送'
		uploadProgressPercent.value = Math.floor(index * 100 / images.length)
		uploadCommentImage(images[index], (url : string) => {
			uploaded.push(url)
			uploadProgressPercent.value = Math.floor((index + 1) * 100 / images.length)
			uploadCommentImageAt(images, index + 1, uploaded, complete, fail)
		}, fail)
	}

	function uploadCommentImage(filePath : string, complete : (key : string) => void, fail : () => void) {
		uni.uploadFile({
			url: apiBaseUrl + '/api/files/upload',
			filePath: filePath,
			name: 'file',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (uploadRes) => {
				let body = null as any
				try {
					body = JSON.parse(uploadRes.data as string) as any
				} catch (error) {
					body = null
				}
				if (body != null && body.code == 0 && body.data != null) {
					complete((body.data as any).key as string)
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '上传失败', icon: 'none' })
				fail()
			},
			fail: () => {
				fail()
				uni.showToast({ title: '图片上传失败', icon: 'none' })
			}
		})
	}

	function removeCommentImage(image : string) {
		commentImageUrls.value = commentImageUrls.value.filter((item) : boolean => item != image)
	}

	function moveCommentImageLeft(index : number) {
		if (index <= 0 || index >= commentImageUrls.value.length) {
			return
		}
		const next = commentImageUrls.value.slice()
		const current = next[index]
		next[index] = next[index - 1]
		next[index - 1] = current
		commentImageUrls.value = next
	}

	function moveCommentImageRight(index : number) {
		if (index < 0 || index >= commentImageUrls.value.length - 1) {
			return
		}
		const next = commentImageUrls.value.slice()
		const current = next[index]
		next[index] = next[index + 1]
		next[index + 1] = current
		commentImageUrls.value = next
	}

	function startCommentImageSort(index : number, event : TouchEvent) {
		commentImageSortActive.value = true
		draggingCommentImageIndex.value = index
		if (event.touches.length > 0) {
			commentImageDragStartX.value = event.touches[0].clientX
			commentImageDragStartY.value = event.touches[0].clientY
		}
	}

	function startCommentImageTouch(index : number, event : TouchEvent) {
		if (event.touches.length > 0) {
			if (commentImageLongPressTimer.value > 0) {
				clearTimeout(commentImageLongPressTimer.value)
			}
			commentImageSortActive.value = false
			draggingCommentImageIndex.value = index
			commentImageDragStartX.value = event.touches[0].clientX
			commentImageDragStartY.value = event.touches[0].clientY
			commentImageDragOffsetX.value = 0
			commentImageDragOffsetY.value = 0
			commentImageLongPressTimer.value = setTimeout(() => {
				if (draggingCommentImageIndex.value == index) {
					commentImageSortActive.value = true
				}
			}, 360)
		}
	}

	function moveCommentImageDrag(event : TouchEvent) {
		if (!commentImageSortActive.value || draggingCommentImageIndex.value < 0 || event.touches.length == 0) {
			return
		}
		const distanceX = event.touches[0].clientX - commentImageDragStartX.value
		const distanceY = event.touches[0].clientY - commentImageDragStartY.value
		commentImageDragOffsetX.value = distanceX
		commentImageDragOffsetY.value = distanceY
		const absX = Math.abs(distanceX)
		const absY = Math.abs(distanceY)
		if (absX < 32 && absY < 42) {
			return
		}
		const from = draggingCommentImageIndex.value
		let to = from
		if (absY > absX && absY >= 42) {
			to = from + (distanceY > 0 ? 4 : -4)
			if (to < 0) {
				to = 0
			}
			if (to > commentImageUrls.value.length - 1) {
				to = commentImageUrls.value.length - 1
			}
		} else {
			if (distanceX > 0 && from < commentImageUrls.value.length - 1) {
				to = from + 1
			}
			if (distanceX < 0 && from > 0) {
				to = from - 1
			}
		}
		if (to == from) {
			return
		}
		swapCommentImages(from, to)
		draggingCommentImageIndex.value = to
		commentImageDragStartX.value = event.touches[0].clientX
		commentImageDragStartY.value = event.touches[0].clientY
		commentImageDragOffsetX.value = 0
		commentImageDragOffsetY.value = 0
	}

	function endCommentImageDrag() {
		if (commentImageLongPressTimer.value > 0) {
			clearTimeout(commentImageLongPressTimer.value)
			commentImageLongPressTimer.value = 0
		}
		draggingCommentImageIndex.value = -1
		commentImageDragOffsetX.value = 0
		commentImageDragOffsetY.value = 0
		setTimeout(() => {
			commentImageSortActive.value = false
		}, 160)
	}

	function swapCommentImages(from : number, to : number) {
		const next = commentImageUrls.value.slice()
		const current = next[from]
		next[from] = next[to]
		next[to] = current
		commentImageUrls.value = next
	}

	function getCommentDraftImageClass(index : number) : string {
		if (!commentImageSortActive.value) {
			return 'draft-image-wrap'
		}
		if (draggingCommentImageIndex.value == index) {
			return 'draft-image-wrap draft-image-wrap-dragging'
		}
		return 'draft-image-wrap draft-image-wrap-sorting'
	}

	function getCommentDraftImageStyle(index : number) : string {
		if (!commentImageSortActive.value || draggingCommentImageIndex.value != index) {
			return ''
		}
		return 'transform: translate(' + commentImageDragOffsetX.value + 'px, ' + commentImageDragOffsetY.value + 'px) scale(1.16); z-index: 20;'
	}

	function openCommentBuildPicker() {
		if (!requireLogin()) {
			return
		}
		commentBuildPickerVisible.value = true
		uni.request({
			url: apiBaseUrl + '/api/build-configs?segment=all',
			method: 'GET',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					commentBuildList.value = body.data as BuildConfigItem[]
				}
			}
		})
	}

	function closeCommentBuildPicker() {
		commentBuildPickerVisible.value = false
	}

	function attachBuildToComment(item : BuildConfigItem) {
		commentBuildId.value = item.id
		commentBuildTitle.value = item.title
		commentBuildDetail.value = item
		commentBuildPickerVisible.value = false
	}

	function toggleBuildForComment(item : BuildConfigItem) {
		if (commentBuildId.value == item.id) {
			clearCommentBuild()
			commentBuildPickerVisible.value = false
			return
		}
		attachBuildToComment(item)
	}

	function clearCommentBuild() {
		commentBuildId.value = 0
		commentBuildTitle.value = ''
		commentBuildDetail.value = createEmptyBuildConfig()
	}

	function previewPostImage(image : string) {
		openImagePreview(post.value.imageUrls, normalizeMediaUrl(image))
	}

	function previewCommentImage(images : string[], image : string) {
		openImagePreview(images, normalizeMediaUrl(image))
	}

	function openImagePreview(images : string[], current : string) {
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
		imagePreviewCurrent.value = current
		imagePreviewScale.value = 1
		imagePreviewVisible.value = true
	}

	function closeImagePreview() {
		imagePreviewVisible.value = false
		imagePreviewScale.value = 1
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

	function normalizeMediaUrl(url : string) : string {
		return normalizeRemoteMediaUrl(url)
	}

	function formatTime(value : string) : string {
		if (value.length >= 16) {
			return value.substring(5, 16).replace('T', ' ')
		}
		return value
	}

	function getAuthorAvatarText(name : string) : string {
		if (name.length > 0) {
			return name.substring(0, 1)
		}
		return '用'
	}

	function getHardwareTypeLabel(type : string) : string {
		if (type == 'MOTHERBOARD') return '主板'
		if (type == 'GPU') return '显卡'
		if (type == 'MEMORY') return '内存'
		if (type == 'STORAGE') return '硬盘'
		if (type == 'PSU') return '电源'
		if (type == 'CASE') return '机箱'
		if (type == 'COOLER') return '散热'
		return type
	}

	function getPartSpecText(part : HardwarePartItem) : string {
		const specs = [] as string[]
		if (part.socketType.length > 0) specs.push(part.socketType)
		if (part.memoryType.length > 0) specs.push(part.memoryType)
		if (part.formFactor.length > 0) specs.push(part.formFactor)
		if (part.specs.length > 0) specs.push(part.specs)
		return specs.join(' · ')
	}

	function clearAuth() {
		currentUserId.value = 0
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
	uni-view,
	scroll-view,
	uni-scroll-view,
	movable-area,
	uni-movable-area,
	movable-view,
	uni-movable-view {
		display: flex;
		flex-direction: column;
		box-sizing: border-box;
	}

	.post-page {
		min-height: 100vh;
		width: 100vw;
		max-width: none;
		background-color: #f6f7fb;
		padding-left: 20px;
		padding-right: 20px;
		box-sizing: border-box;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.post-page-leave {
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
		height: 44px;
		height: calc(44px + constant(safe-area-inset-top));
		height: calc(44px + env(safe-area-inset-top));
		height: calc(20px + var(--status-bar-height));
	}

	.post-nav {
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
		background-color: #ffffff;
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: #eef2f7;
	}

	.nav-button {
		z-index: 2;
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

	.post-scroll {
		height: calc(100vh - 180px);
	}

	.post-bottom-spacer {
		height: 22px;
		height: calc(22px + constant(safe-area-inset-bottom));
		height: calc(22px + env(safe-area-inset-bottom));
	}

	.post-card,
	.comment-section {
		border-radius: 28px;
		background-color: #ffffff;
		padding: 18px;
		margin-top: 12px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.post-head,
	.comment-top {
		flex-direction: row;
		align-items: center;
	}

	.post-avatar,
	.comment-avatar {
		width: 42px;
		height: 42px;
		border-radius: 21px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		margin-right: 10px;
		overflow: hidden;
	}

	.comment-avatar {
		width: 34px;
		height: 34px;
		border-radius: 17px;
	}

	.post-avatar-image,
	.comment-avatar-image {
		width: 42px;
		height: 42px;
		border-radius: 21px;
	}

	.comment-avatar-image {
		width: 34px;
		height: 34px;
		border-radius: 17px;
	}

	.post-author-main,
	.comment-main {
		flex: 1;
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
		margin-bottom: 3px;
	}

	.avatar-placeholder-body {
		width: 54%;
		height: 28%;
		border-radius: 999px 999px 0 0;
		background-color: #98a2b3;
	}

	.avatar-placeholder-small .avatar-placeholder-head {
		margin-bottom: 2px;
	}

	.post-author-name,
	.comment-author {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.post-time,
	.comment-time {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.post-content {
		margin-top: 16px;
		font-size: 16px;
		line-height: 25px;
		color: #111827;
	}

	.image-grid,
	.comment-image-grid {
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 12px;
	}

	.post-image {
		width: 86px;
		height: 86px;
		border-radius: 16px;
		margin-right: 8px;
		margin-bottom: 8px;
		background-color: #f3f6fb;
	}

	.comment-image {
		width: 68px;
		height: 68px;
		border-radius: 15px;
		margin-right: 7px;
		margin-bottom: 7px;
		background-color: #f3f6fb;
	}

	.build-card,
	.build-detail {
		border-radius: 22px;
		background-color: #f6f9ff;
		border-width: 1px;
		border-color: #dbeafe;
		margin-top: 12px;
		padding: 12px;
	}

	.build-card {
		min-height: 70px;
		flex-direction: row;
		align-items: center;
	}

	.build-icon {
		width: 42px;
		height: 42px;
		border-radius: 16px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 10px;
	}

	.build-card-mark {
		width: 22px;
		height: 26px;
		border-radius: 7px;
		background-color: #ffffff;
		padding: 4px;
	}

	.build-card-head {
		width: 14px;
		height: 5px;
		border-radius: 3px;
		background-color: #007aff;
	}

	.build-card-row {
		width: 14px;
		height: 3px;
		border-radius: 2px;
		background-color: #111827;
		margin-top: 4px;
	}

	.build-card-row-short {
		width: 10px;
	}

	.build-main {
		flex: 1;
	}

	.build-title,
	.build-part-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.build-desc,
	.build-part-desc,
	.build-note {
		margin-top: 3px;
		font-size: 12px;
		line-height: 18px;
		color: #667085;
	}

	.build-arrow {
		font-size: 24px;
		color: #98a2b3;
	}

	.build-part {
		border-radius: 16px;
		background-color: #ffffff;
		padding: 10px;
		margin-bottom: 8px;
	}

	.comment-build-card,
	.comment-linked-build {
		border-radius: 20px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #e7edf7;
		margin-top: 10px;
		padding: 10px;
		flex-direction: row;
		align-items: center;
	}

	.comment-linked-build {
		position: fixed;
		left: 20px;
		right: 20px;
		bottom: 78px;
		bottom: calc(78px + constant(safe-area-inset-bottom));
		bottom: calc(78px + env(safe-area-inset-bottom));
		width: auto;
		max-width: none;
		margin-top: 0;
		z-index: 22;
	}

	.comment-linked-build-raised {
		bottom: 150px;
		bottom: calc(150px + constant(safe-area-inset-bottom));
		bottom: calc(150px + env(safe-area-inset-bottom));
	}

	.comment-build-icon {
		width: 34px;
		height: 34px;
		border-radius: 12px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 9px;
	}

	.comment-build-card-mark {
		width: 18px;
		height: 22px;
		border-radius: 6px;
		background-color: #ffffff;
		padding: 3px;
	}

	.comment-build-card-head {
		width: 10px;
		height: 4px;
		border-radius: 2px;
		background-color: #007aff;
	}

	.comment-build-card-row {
		width: 10px;
		height: 2px;
		border-radius: 1px;
		background-color: #111827;
		margin-top: 3px;
	}

	.comment-build-card-row-short {
		width: 7px;
	}

	.comment-build-main {
		flex: 1;
	}

	.comment-build-title {
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #111827;
	}

	.comment-build-desc {
		margin-top: 2px;
		font-size: 11px;
		line-height: 16px;
		color: #667085;
	}

	.comment-build-arrow {
		font-size: 20px;
		line-height: 22px;
		color: #98a2b3;
	}

	.comment-build-remove {
		width: 28px;
		height: 28px;
		border-radius: 14px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.post-actions {
		flex-direction: row;
		align-items: center;
		margin-top: 14px;
	}

	.post-action,
	.comment-like {
		width: 52px;
		height: 32px;
		border-radius: 16px;
		background-color: #f6f7fb;
		margin-right: 8px;
		flex-direction: row;
		align-items: center;
		justify-content: center;
	}

	.post-action-icon,
	.comment-like-icon {
		font-size: 13px;
		line-height: 18px;
		color: #98a2b3;
		margin-right: 4px;
	}

	.post-like-active,
	.comment-like-active {
		color: #ff3b30;
	}

	.post-favorite-active {
		color: #007aff;
	}

	.post-action-text,
	.comment-like-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.comment-delete {
		background-color: #fff1f0;
	}

	.comment-delete-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #ff3b30;
	}

	.comment-delete {
		width: 52px;
		height: 32px;
		border-radius: 16px;
		align-items: center;
		justify-content: center;
	}

	.post-head-delete {
		margin-left: 8px;
	}

	.comment-headline {
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12px;
	}

	.comment-title {
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.sort-switch {
		width: 132px;
		height: 36px;
		border-radius: 18px;
		background-color: #f3f6fb;
		flex-direction: row;
		padding: 4px;
	}

	.sort-item {
		flex: 1;
		border-radius: 14px;
		align-items: center;
		justify-content: center;
	}

	.sort-item-active {
		background-color: #ffffff;
	}

	.sort-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.sort-text-active {
		color: #007aff;
	}

	.comment-card {
		border-radius: 22px;
		background-color: #f8fafc;
		padding: 13px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.comment-like {
		margin-right: 0;
	}

	.comment-content {
		margin-top: 8px;
		font-size: 14px;
		line-height: 21px;
		color: #111827;
	}

	.comment-empty {
		align-items: center;
		padding: 36px 16px;
	}

	.comment-empty-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.comment-empty-desc {
		margin-top: 6px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
		text-align: center;
	}

	.list-pager {
		height: 46px;
		border-radius: 23px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		margin-top: 4px;
		margin-bottom: 4px;
	}

	.pager-button {
		height: 32px;
		border-radius: 16px;
		background-color: #eef5ff;
		padding-left: 14px;
		padding-right: 14px;
		align-items: center;
		justify-content: center;
	}

	.pager-button-disabled {
		background-color: #f3f6fb;
	}

	.pager-button-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #007aff;
	}

	.pager-button-text-disabled {
		color: #b9c1ce;
	}

	.pager-count {
		width: 72px;
		text-align: center;
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #667085;
	}

	.comment-draft-images {
		position: fixed;
		left: 20px;
		right: 20px;
		bottom: 78px;
		bottom: calc(78px + constant(safe-area-inset-bottom));
		bottom: calc(78px + env(safe-area-inset-bottom));
		width: auto;
		max-width: none;
		min-height: 66px;
		border-radius: 22px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding: 8px;
		flex-direction: row;
		z-index: 20;
	}

	.draft-image-wrap {
		position: relative;
		width: 58px;
		height: 58px;
		border-radius: 14px;
		margin-right: 8px;
		overflow: hidden;
	}

	.draft-image {
		position: absolute;
		left: 0;
		top: 0;
		width: 58px;
		height: 58px;
		border-radius: 14px;
		background-color: #f3f6fb;
	}

	.draft-image-wrap-sorting {
		animation-name: draftImageShake;
		animation-duration: 150ms;
		animation-iteration-count: infinite;
		animation-timing-function: ease-in-out;
	}

	.draft-image-wrap-dragging {
		opacity: 0.86;
		z-index: 20;
	}

	@keyframes draftImageShake {
		0% { transform: rotate(-1deg); }
		50% { transform: rotate(1deg); }
		100% { transform: rotate(-1deg); }
	}

	.draft-image-order {
		position: absolute;
		left: 4px;
		right: 4px;
		bottom: 4px;
		height: 20px;
		border-radius: 10px;
		background-color: rgba(17, 24, 39, 0.72);
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		padding-left: 7px;
		padding-right: 7px;
	}

	.draft-order-text {
		font-size: 17px;
		line-height: 19px;
		font-weight: 900;
		color: #ffffff;
	}

	.draft-order-disabled {
		color: rgba(255, 255, 255, 0.32);
	}

	.draft-image-remove {
		position: absolute;
		right: 4px;
		top: 4px;
		width: 22px;
		height: 22px;
		border-radius: 11px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		z-index: 3;
	}

	.close-line {
		position: absolute;
		width: 10px;
		height: 2px;
		border-radius: 2px;
		background-color: #ffffff;
		transform: rotate(45deg);
	}

	.close-line-two {
		transform: rotate(-45deg);
	}

	.comment-composer {
		position: fixed;
		left: 20px;
		right: 20px;
		bottom: 18px;
		bottom: calc(18px + constant(safe-area-inset-bottom));
		bottom: calc(18px + env(safe-area-inset-bottom));
		width: auto;
		max-width: none;
		min-height: 52px;
		border-radius: 26px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		flex-direction: row;
		align-items: center;
		padding: 6px;
		z-index: 21;
	}

	.comment-image-button {
		width: 40px;
		height: 40px;
		border-radius: 20px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
		margin-right: 4px;
	}

	.comment-image-button .tool-image-icon {
		left: 0.67px;
	}

	.comment-attach-button {
		position: relative;
		width: 40px;
		height: 40px;
		border-radius: 20px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
		margin-right: 4px;
	}

	.comment-attach-button-active {
		background-color: #eef5ff;
	}

	.comment-build-symbol {
		width: 18px;
		height: 22px;
		border-radius: 6px;
		border-width: 2px;
		border-color: #667085;
		padding: 3px;
	}

	.comment-build-symbol-head {
		width: 10px;
		height: 4px;
		border-radius: 2px;
		background-color: #667085;
	}

	.comment-build-symbol-row {
		width: 10px;
		height: 2px;
		border-radius: 1px;
		background-color: #667085;
		margin-top: 3px;
	}

	.comment-build-symbol-row-short {
		width: 7px;
	}

	.comment-attach-button-active .comment-build-symbol {
		border-color: #007aff;
	}

	.comment-attach-button-active .comment-build-symbol-head,
	.comment-attach-button-active .comment-build-symbol-row {
		background-color: #007aff;
	}

	.tool-image-icon {
		position: relative;
		width: 18px;
		height: 16px;
		border-radius: 5px;
		border-width: 2px;
		border-color: #007aff;
		overflow: hidden;
	}

	.tool-image-sun {
		position: absolute;
		right: 3px;
		top: 3px;
		width: 4px;
		height: 4px;
		border-radius: 2px;
		background-color: #007aff;
	}

	.tool-image-hill {
		position: absolute;
		left: 3px;
		bottom: 1px;
		width: 12px;
		height: 6px;
		border-radius: 6px;
		background-color: #007aff;
	}

	.tool-build-symbol {
		width: 15px;
		height: 18px;
		border-radius: 5px;
		border-width: 2px;
		border-color: #007aff;
		padding: 2px;
	}

	.tool-build-symbol-head {
		width: 7px;
		height: 3px;
		border-radius: 2px;
		background-color: #007aff;
	}

	.tool-build-symbol-row {
		width: 7px;
		height: 2px;
		border-radius: 1px;
		background-color: #007aff;
		margin-top: 2px;
	}

	.tool-build-symbol-row-short {
		width: 5px;
	}

	.comment-input {
		flex: 1;
		height: 40px;
		font-size: 14px;
		color: #111827;
		padding-left: 10px;
		padding-right: 10px;
	}

	.input-placeholder {
		color: #98a2b3;
	}

	.comment-send {
		height: 40px;
		border-radius: 20px;
		background-color: #111827;
		padding-left: 14px;
		padding-right: 14px;
		align-items: center;
		justify-content: center;
	}

	.comment-send-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #ffffff;
	}

	.build-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(15, 23, 42, 0.32);
		justify-content: flex-end;
		z-index: 40;
	}

	.build-modal,
	.build-picker {
		margin-left: 20px;
		margin-right: 20px;
		margin-bottom: 22px;
		border-radius: 28px;
		background-color: #ffffff;
		padding: 18px;
		border-width: 1px;
		border-color: #edf0f5;
		animation-name: buildModalIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	@keyframes buildModalIn {
		from { opacity: 0; transform: translateY(28px); }
		to { opacity: 1; transform: translateY(0); }
	}

	.build-modal-head {
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12px;
	}

	.build-modal-title {
		flex: 1;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.build-modal-close {
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-left: 10px;
	}

	.build-close-line {
		background-color: #ffffff;
	}

	.build-modal-stats {
		flex-direction: row;
		margin-bottom: 12px;
	}

	.build-modal-stat {
		flex: 1;
		border-radius: 18px;
		background-color: #f6f9ff;
		padding: 12px;
		margin-right: 8px;
	}

	.build-modal-stat-label {
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}

	.build-modal-stat-value {
		margin-top: 3px;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.build-modal-parts,
	.build-picker-scroll {
		max-height: 360px;
	}

	.build-picker-item {
		border-radius: 18px;
		background-color: #f6f7fb;
		padding: 12px;
		margin-bottom: 10px;
		flex-direction: row;
		align-items: center;
	}

	.build-picker-main {
		flex: 1;
	}

	.build-picker-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.build-picker-desc,
	.build-picker-empty-text {
		margin-top: 3px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}

	.build-picker-plus {
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.build-picker-plus-selected {
		background-color: #eff4ff;
		border-width: 1px;
		border-color: #d7e4ff;
	}

	.build-picker-plus-text {
		font-size: 22px;
		line-height: 28px;
		font-weight: 700;
		color: #ffffff;
	}

	.build-picker-plus-text-selected {
		color: #0b1220;
	}

	.build-picker-empty {
		padding: 34px;
		align-items: center;
	}

	.build-picker {
		margin-left: 8px;
		margin-right: 8px;
		margin-bottom: 8px;
		border-radius: 28px;
		padding: 18px;
		padding-bottom: 20px;
		border-width: 0;
	}

	.build-picker-scroll {
		height: 156px;
		max-height: 156px;
	}

	.build-picker-item {
		min-height: 62px;
		border-radius: 16px;
		background-color: #f6f7fb;
		border-width: 0;
		padding: 11px;
		margin-bottom: 10px;
	}

	.build-picker-desc {
		margin-top: 2px;
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

	.inline-preview-back .back-chevron-line {
		background-color: #ffffff;
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

	.upload-progress-mask {
		position: fixed;
		left: 24px;
		right: 24px;
		top: 58px;
		top: calc(58px + constant(safe-area-inset-top));
		top: calc(58px + env(safe-area-inset-top));
		top: calc(34px + var(--status-bar-height));
		width: auto;
		max-width: none;
		height: 104px;
		background-color: transparent;
		z-index: 90;
		align-items: center;
		pointer-events: none;
	}

	.upload-progress-card {
		width: 232px;
		border-radius: 0;
		background-color: transparent;
		padding: 0;
		border-width: 0;
	}

	.upload-progress-title {
		font-size: 15px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
		text-align: center;
	}

	.upload-progress-track {
		height: 5px;
		border-radius: 3px;
		background-color: #eef2f7;
		margin-top: 10px;
		overflow: hidden;
	}

	.upload-progress-fill {
		height: 5px;
		border-radius: 3px;
		background-color: #111827;
	}

	.upload-progress-text {
		margin-top: 7px;
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #667085;
		text-align: center;
	}
</style>

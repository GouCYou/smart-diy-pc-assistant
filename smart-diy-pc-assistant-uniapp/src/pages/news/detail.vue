<template>
	<view :class="pageLeaving ? 'news-page news-page-leave' : 'news-page'">
		<view class="status-space"></view>
		<view class="news-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">新闻详情</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="news-scroll" scroll-y="true" :scroll-into-view="scrollIntoViewId" scroll-with-animation="true">
			<view v-if="news.id > 0" class="article-card">
				<view class="article-body">
					<text class="article-title">{{ news.title }}</text>
					<view class="article-kicker">
						<text class="article-kicker-text">{{ news.tag || '科技新闻' }}</text>
					</view>
					<view class="article-meta">
						<text class="article-meta-text">{{ formatTime(news.createdAt) }}</text>
						<text class="article-meta-dot">·</text>
						<text class="article-meta-text">{{ news.viewCount }} 浏览</text>
					</view>
					<view class="article-content">
						<view v-for="(block, index) in getContentBlocks()" :key="index">
							<text v-if="block.type == 'text'" class="article-paragraph">{{ block.text }}</text>
							<image v-if="block.type == 'image'" class="article-image" :src="block.imageUrl" mode="aspectFill" @click="previewNewsImage(block.imageUrl)"></image>
						</view>
					</view>
					<view class="article-finished">
						<view class="article-finished-line"></view>
						<text class="article-finished-text">已读完这篇新闻</text>
						<view class="article-finished-line"></view>
					</view>
					<view class="article-actions article-actions-bottom">
						<view class="article-action" @click="toggleNewsLike">
							<text :class="news.liked ? 'article-action-icon article-like-active' : 'article-action-icon'">♥</text>
							<text class="article-action-text">{{ news.likeCount }}</text>
						</view>
						<view class="article-action" @click="toggleNewsFavorite">
							<text :class="news.favorite ? 'article-action-icon article-fav-active' : 'article-action-icon'">★</text>
							<text class="article-action-text">{{ news.favoriteCount }}</text>
						</view>
						<view class="article-action">
							<image class="comment-action-image" src="/static/icons/comment.svg" mode="aspectFit"></image>
							<text class="article-action-text">{{ news.commentCount }}</text>
						</view>
					</view>
				</view>
			</view>

			<view v-if="news.id > 0" class="comment-section">
				<view class="comment-headline">
					<text class="comment-title">评论 {{ news.commentCount }}</text>
					<view class="sort-switch">
						<view :class="commentSort == 'latest' ? 'sort-indicator sort-indicator-0' : 'sort-indicator sort-indicator-1'"></view>
						<view :class="commentSort == 'latest' ? 'sort-item sort-item-active' : 'sort-item'" @click="setCommentSort('latest')">
							<text :class="commentSort == 'latest' ? 'sort-text sort-text-active' : 'sort-text'">最新</text>
						</view>
						<view :class="commentSort == 'likes' ? 'sort-item sort-item-active' : 'sort-item'" @click="setCommentSort('likes')">
							<text :class="commentSort == 'likes' ? 'sort-text sort-text-active' : 'sort-text'">点赞</text>
						</view>
					</view>
				</view>

				<view v-for="comment in news.comments" :id="'comment-' + comment.id" :key="comment.id" :class="highlightCommentId == comment.id ? 'comment-card comment-card-highlight' : 'comment-card'" @click="replyToComment(comment)">
					<view class="comment-top">
						<view class="comment-avatar" @click.stop="openUserSpace(comment.author.id)">
							<image v-if="comment.author.avatarUrl.length > 0" class="comment-avatar-image" :src="comment.author.avatarUrl" mode="aspectFill"></image>
							<view v-else class="avatar-placeholder">
								<view class="avatar-placeholder-head"></view>
								<view class="avatar-placeholder-body"></view>
							</view>
						</view>
						<view class="comment-main" @click.stop="openUserSpace(comment.author.id)">
							<text class="comment-author">{{ comment.author.nickname || comment.author.username }}</text>
							<text class="comment-time">{{ formatTime(comment.createdAt) }}</text>
						</view>
						<view class="comment-like" @click.stop="toggleCommentLike(comment.id)">
							<text :class="comment.liked ? 'comment-like-icon comment-like-active' : 'comment-like-icon'">♥</text>
							<text class="comment-like-text">{{ comment.likeCount }}</text>
						</view>
					</view>
					<view v-if="comment.replyTo != null && comment.replyTo.id > 0" class="comment-reply-context">
						<text class="comment-reply-context-text">回复 {{ getAuthorDisplayName(comment.replyTo) }}</text>
					</view>
					<view v-if="comment.content.length > 0" class="comment-content mention-content">
						<text v-for="(token, index) in buildMentionTokens(comment.content, comment.mentions)" :key="'comment-mention-' + comment.id + '-' + index" :class="token.userId > 0 ? 'mention-token mention-token-active comment-mention-token' : 'mention-token comment-mention-token'" @click.stop="openMentionToken(token)">{{ token.text }}</text>
					</view>
					<view v-if="comment.imageUrls.length > 0" class="comment-image-grid">
						<image v-for="image in comment.imageUrls" :key="image" class="comment-image" :src="normalizeMediaUrl(image)" mode="aspectFill" @click.stop="previewCommentImage(comment.imageUrls, image)"></image>
					</view>
				</view>

				<view v-if="news.comments.length == 0" class="comment-empty">
					<text class="comment-empty-title">还没有评论</text>
					<text class="comment-empty-desc">说说你对这条硬件资讯的看法。</text>
				</view>
			</view>

			<view v-if="news.id == 0 && !loading" class="empty-state">
				<text class="empty-title">新闻不存在</text>
				<text class="empty-desc">可能已下线或暂时无法读取。</text>
			</view>
			<view class="news-bottom-spacer"></view>
		</scroll-view>

		<view class="comment-dock">
			<view v-if="commentImageUrls.length > 0" class="comment-draft-images">
				<view v-for="image in commentImageUrls" :key="image" class="draft-image-wrap">
					<image class="draft-image" :src="image" mode="aspectFill" @click="previewCommentImage(commentImageUrls, image)"></image>
					<view class="draft-image-remove" @click="removeCommentImage(image)">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
			</view>
			<view v-if="mentionDrawerVisible" class="mention-drawer">
				<view class="mention-drawer-handle"></view>
				<view class="mention-search-row">
					<text class="mention-search-mark">@</text>
					<text class="mention-search-text">{{ mentionQuery.length > 0 ? mentionQuery : '搜索昵称' }}</text>
				</view>
				<scroll-view class="mention-user-scroll" scroll-y="true">
					<view v-for="user in mentionUsers" :key="user.userId" class="mention-user-row" @click="chooseCommentMention(user)">
						<view class="mention-user-avatar">
							<image v-if="user.avatarUrl.length > 0" class="mention-user-avatar-image" :src="user.avatarUrl" mode="aspectFill"></image>
							<view v-else class="avatar-placeholder">
								<view class="avatar-placeholder-head"></view>
								<view class="avatar-placeholder-body"></view>
							</view>
						</view>
						<text class="mention-user-name">{{ user.nickname }}</text>
						<text class="mention-user-plus">添加</text>
					</view>
					<view v-if="mentionUsers.length == 0" class="mention-empty">
						<text class="mention-empty-text">{{ mentionLoading ? '搜索中...' : '没有找到用户' }}</text>
					</view>
				</scroll-view>
			</view>
			<view class="comment-composer">
				<view class="comment-image-button" @click="pickCommentImages">
					<view class="tool-image-icon">
						<view class="tool-image-sun"></view>
						<view class="tool-image-hill"></view>
					</view>
				</view>
				<view v-if="replyTargetCommentId > 0" class="comment-reply-pill" @click="clearCommentReply">
					<text class="comment-reply-pill-text">回复 {{ replyTargetName }}</text>
					<view class="comment-reply-pill-close">
						<view class="close-line reply-close-line"></view>
						<view class="close-line close-line-two reply-close-line"></view>
					</view>
				</view>
				<input class="comment-input" v-model="commentText" :placeholder="getCommentPlaceholder()" placeholder-class="input-placeholder" @input="handleCommentInputChange" @focus="refreshMentionDrawer" />
				<view class="comment-send" @click="submitComment">
					<text class="comment-send-text">{{ commentSubmitting ? '...' : '发送' }}</text>
				</view>
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
	</view>
</template>

<script setup lang="ts">
	import { nextTick, ref } from 'vue'
	import { onLoad, onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL, normalizeMediaUrl as normalizeRemoteMediaUrl } from '../../common/api'
	import { MentionItem, MentionSearchUser, MentionToken, buildMentionTokens, getActiveMentionQuery, insertMention, normalizeMentionSearchUsers, normalizeMentions } from '../../common/mention'

	type AuthorItem = {
		id : number
		username : string
		nickname : string
		avatarUrl : string
	}

	type NewsCommentItem = {
		id : number
		newsId : number
		author : AuthorItem
		parentCommentId : number
		replyTo : AuthorItem
		content : string
		mentions : MentionItem[]
		imageUrls : string[]
		likeCount : number
		liked : boolean
		createdAt : string
	}

	type NewsItem = {
		id : number
		title : string
		summary : string
		tag : string
		content : string
		coverKey : string
		coverUrl : string
		viewCount : number
		likeCount : number
		commentCount : number
		favoriteCount : number
		liked : boolean
		favorite : boolean
		status : string
		createdAt : string
		updatedAt : string
		comments : NewsCommentItem[]
	}

	type ContentBlock = {
		type : string
		text : string
		imageUrl : string
	}

	const apiBaseUrl = API_BASE_URL
	const token = ref('')
	const newsId = ref(0)
	const news = ref(createEmptyNews())
	const pageLeaving = ref(false)
	const loading = ref(false)
	const commentSort = ref('latest')
	const pendingCommentId = ref(0)
	const highlightCommentId = ref(0)
	const scrollIntoViewId = ref('')
	const commentText = ref('')
	const replyTargetCommentId = ref(0)
	const replyTargetName = ref('')
	const commentImageUrls = ref([] as string[])
	const commentSubmitting = ref(false)
	const imagePreviewVisible = ref(false)
	const imagePreviewUrls = ref([] as string[])
	const imagePreviewIndex = ref(0)
	const imagePreviewCurrent = ref('')
	const imagePreviewScale = ref(1)
	const mentionDrawerVisible = ref(false)
	const mentionLoading = ref(false)
	const mentionQuery = ref('')
	const mentionUsers = ref([] as MentionSearchUser[])
	let mentionSearchTimer : number = 0

	loadLocalState()

	onLoad((options : any) => {
		const routeNewsId = Number(options == null || options.id == null ? 0 : options.id)
		const routeCommentId = Number(options == null || options.commentId == null ? 0 : options.commentId)
		if (routeNewsId > 0) {
			newsId.value = routeNewsId
			uni.setStorageSync('current_news_id', routeNewsId)
		}
		if (routeCommentId > 0) {
			pendingCommentId.value = routeCommentId
			highlightCommentId.value = routeCommentId
		}
		loadNewsDetail()
	})

	onShow(() => {
		loadLocalState()
		loadNewsDetail()
	})

	function createEmptyAuthor() : AuthorItem {
		return { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem
	}

	function createEmptyNews() : NewsItem {
		return {
			id: 0,
			title: '',
			summary: '',
			tag: '科技新闻',
			content: '',
			coverKey: '',
			coverUrl: '',
			viewCount: 0,
			likeCount: 0,
			commentCount: 0,
			favoriteCount: 0,
			liked: false,
			favorite: false,
			status: '',
			createdAt: '',
			updatedAt: '',
			comments: [] as NewsCommentItem[]
		} as NewsItem
	}

	function loadLocalState() {
		const savedToken = uni.getStorageSync('app_token')
		const savedNewsId = uni.getStorageSync('current_news_id')
		if (savedToken != null) {
			token.value = savedToken as string
		}
		if (savedNewsId != null) {
			newsId.value = savedNewsId as number
		}
	}

	function loadNewsDetail() {
		if (newsId.value <= 0 || loading.value) {
			return
		}
		loading.value = true
		uni.request({
			url: apiBaseUrl + '/api/news/' + newsId.value,
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					news.value = normalizeNewsItem(body.data as NewsItem)
					focusPendingComment()
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			fail: () => {
				uni.showToast({ title: '暂时读取不到新闻', icon: 'none' })
			},
			complete: () => {
				loading.value = false
			}
		})
	}

	function loadComments() {
		if (news.value.id <= 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/news/' + news.value.id + '/comments?sort=' + commentSort.value,
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const comments = body.data as NewsCommentItem[]
					for (let i = 0; i < comments.length; i++) {
						comments[i] = normalizeComment(comments[i])
					}
					news.value.comments = comments
					focusPendingComment()
				}
			}
		})
	}

	function submitComment() {
		if (!requireLogin() || commentSubmitting.value) {
			return
		}
		const content = commentText.value.trim()
		if (content.length == 0 && commentImageUrls.value.length == 0) {
			uni.showToast({ title: '先写点评论或添加图片', icon: 'none' })
			return
		}
		commentSubmitting.value = true
		uploadCommentImagesBeforeSubmit(commentImageUrls.value, (uploadedImageKeys : string[]) => {
			createComment(content, uploadedImageKeys)
		}, () => {
			commentSubmitting.value = false
		})
	}

	function createComment(content : string, uploadedImageKeys : string[]) {
		uni.request({
			url: apiBaseUrl + '/api/news/' + news.value.id + '/comments',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				content: content,
				imageKeys: uploadedImageKeys,
				parentCommentId: replyTargetCommentId.value > 0 ? replyTargetCommentId.value : null
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					commentText.value = ''
					commentImageUrls.value = []
					clearCommentReply()
					news.value.commentCount = news.value.commentCount + 1
					loadComments()
					return
				}
				uni.showToast({ title: body != null && body.message != null ? body.message as string : '评论失败', icon: 'none' })
			},
			fail: () => {
				uni.showToast({ title: '暂时连接不到后端服务', icon: 'none' })
			},
			complete: () => {
				commentSubmitting.value = false
			}
		})
	}

	function toggleNewsLike() {
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/news/' + news.value.id + '/like',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const comments = news.value.comments
					news.value = normalizeNewsItem(body.data as NewsItem)
					news.value.comments = comments
				}
			}
		})
	}

	function toggleNewsFavorite() {
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/news/' + news.value.id + '/favorite',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const comments = news.value.comments
					news.value = normalizeNewsItem(body.data as NewsItem)
					news.value.comments = comments
				}
			}
		})
	}

	function toggleCommentLike(id : number) {
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/news/comments/' + id + '/like',
			method: 'POST',
			header: { 'Authorization': 'Bearer ' + token.value },
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const updated = normalizeComment(body.data as NewsCommentItem)
					for (let i = 0; i < news.value.comments.length; i++) {
						if (news.value.comments[i].id == updated.id) {
							news.value.comments.splice(i, 1, updated)
							break
						}
					}
				}
			}
		})
	}

	function setCommentSort(value : string) {
		if (commentSort.value == value) {
			return
		}
		commentSort.value = value
		loadComments()
	}

	function getContentBlocks() : ContentBlock[] {
		const blocks = [] as ContentBlock[]
		const source = news.value.content == null ? '' : news.value.content
		const pattern = /!\[[^\]]*]\(([^)]+)\)/g
		let cursor = 0
		let match = pattern.exec(source)
		while (match != null) {
			appendTextBlocks(blocks, source.substring(cursor, match.index))
			blocks.push({
				type: 'image',
				text: '',
				imageUrl: normalizeMediaUrl(match[1])
			} as ContentBlock)
			cursor = match.index + match[0].length
			match = pattern.exec(source)
		}
		appendTextBlocks(blocks, source.substring(cursor))
		if (blocks.length == 0 && news.value.summary.length > 0) {
			blocks.push({ type: 'text', text: news.value.summary, imageUrl: '' } as ContentBlock)
		}
		return blocks
	}

	function appendTextBlocks(blocks : ContentBlock[], text : string) {
		const pieces = text.split(/\n{2,}/)
		for (let i = 0; i < pieces.length; i++) {
			const value = pieces[i].trim()
			if (value.length > 0) {
				blocks.push({ type: 'text', text: value, imageUrl: '' } as ContentBlock)
			}
		}
	}

	function normalizeNewsItem(item : NewsItem) : NewsItem {
		if (item.tag == null || item.tag.length == 0) {
			item.tag = '科技新闻'
		}
		if (item.coverUrl != null && item.coverUrl.length > 0) {
			item.coverUrl = normalizeMediaUrl(item.coverUrl)
		}
		if (item.comments == null) {
			item.comments = [] as NewsCommentItem[]
		}
		for (let i = 0; i < item.comments.length; i++) {
			item.comments[i] = normalizeComment(item.comments[i])
		}
		return item
	}

	function normalizeComment(comment : NewsCommentItem) : NewsCommentItem {
		if (comment.author == null) {
			comment.author = { id: 0, username: '', nickname: '用户', avatarUrl: '' } as AuthorItem
		}
		if (comment.author.avatarUrl != null && comment.author.avatarUrl.length > 0) {
			comment.author.avatarUrl = normalizeMediaUrl(comment.author.avatarUrl)
		}
		if (comment.replyTo == null) {
			comment.replyTo = createEmptyAuthor()
		}
		if (comment.replyTo.avatarUrl != null && comment.replyTo.avatarUrl.length > 0) {
			comment.replyTo.avatarUrl = normalizeMediaUrl(comment.replyTo.avatarUrl)
		}
		comment.mentions = normalizeMentions((comment as any).mentions)
		if (comment.imageUrls == null) {
			comment.imageUrls = [] as string[]
		}
		for (let i = 0; i < comment.imageUrls.length; i++) {
			comment.imageUrls[i] = normalizeMediaUrl(comment.imageUrls[i])
		}
		return comment
	}

	function normalizeMediaUrl(url : string) : string {
		return normalizeRemoteMediaUrl(url)
	}

	function replyToComment(comment : NewsCommentItem) {
		if (!requireLogin() || comment.id <= 0) {
			return
		}
		replyTargetCommentId.value = comment.id
		replyTargetName.value = getAuthorDisplayName(comment.author)
	}

	function clearCommentReply() {
		replyTargetCommentId.value = 0
		replyTargetName.value = ''
	}

	function getCommentPlaceholder() : string {
		return replyTargetCommentId.value > 0 ? '回复 ' + replyTargetName.value + '，输入 @ 艾特用户' : '写评论，输入 @ 艾特用户'
	}

	function openMentionToken(token : MentionToken) {
		if (token.userId > 0) {
			openUserSpace(token.userId)
		}
	}

	function handleCommentInputChange(event : any) {
		const value = event == null || event.detail == null ? '' : event.detail.value as string
		commentText.value = value
		refreshMentionDrawer()
	}

	function refreshMentionDrawer() {
		const query = getActiveMentionQuery(commentText.value)
		if (query == null) {
			mentionDrawerVisible.value = false
			mentionUsers.value = [] as MentionSearchUser[]
			return
		}
		mentionQuery.value = query
		mentionDrawerVisible.value = true
		if (mentionSearchTimer > 0) {
			clearTimeout(mentionSearchTimer)
		}
		mentionSearchTimer = setTimeout(() => {
			loadMentionUsers(query)
		}, 140)
	}

	function loadMentionUsers(query : string) {
		mentionLoading.value = true
		uni.request({
			url: apiBaseUrl + '/api/users/mentions/search?q=' + encodeURIComponent(query),
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					mentionUsers.value = normalizeMentionSearchUsers(body.data)
				}
			},
			complete: () => {
				mentionLoading.value = false
			}
		})
	}

	function chooseCommentMention(user : MentionSearchUser) {
		commentText.value = insertMention(commentText.value, user.nickname)
		mentionDrawerVisible.value = false
		mentionUsers.value = [] as MentionSearchUser[]
	}

	function focusPendingComment() {
		if (pendingCommentId.value <= 0 || news.value.comments.length == 0) {
			return
		}
		for (let i = 0; i < news.value.comments.length; i++) {
			if (news.value.comments[i].id == pendingCommentId.value) {
				highlightCommentId.value = pendingCommentId.value
				nextTick(() => {
					scrollIntoViewId.value = ''
					setTimeout(() => {
						scrollIntoViewId.value = 'comment-' + pendingCommentId.value
					}, 80)
				})
				return
			}
		}
	}

	function getAuthorDisplayName(author : AuthorItem) : string {
		if (author == null) {
			return '用户'
		}
		if (author.nickname != null && author.nickname.length > 0) {
			return author.nickname
		}
		if (author.username != null && author.username.length > 0) {
			return author.username
		}
		return '用户'
	}

	function previewNewsImage(image : string) {
		const urls = [] as string[]
		const blocks = getContentBlocks()
		for (let i = 0; i < blocks.length; i++) {
			if (blocks[i].type == 'image') {
				urls.push(blocks[i].imageUrl)
			}
		}
		openImagePreview(urls.length > 0 ? urls : [image], normalizeMediaUrl(image))
	}

	function previewCommentImage(images : string[], image : string) {
		const urls = images.map((item) : string => normalizeMediaUrl(item))
		openImagePreview(urls, normalizeMediaUrl(image))
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

	function uploadCommentImagesBeforeSubmit(images : string[], complete : (keys : string[]) => void, fail : () => void) {
		if (images.length == 0) {
			complete([] as string[])
			return
		}
		const uploaded = [] as string[]
		uploadCommentImageAt(images, 0, uploaded, complete, fail)
	}

	function uploadCommentImageAt(images : string[], index : number, uploaded : string[], complete : (keys : string[]) => void, fail : () => void) {
		if (index >= images.length) {
			complete(uploaded)
			return
		}
		uploadCommentImage(images[index], (key : string) => {
			uploaded.push(key)
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
				uni.showToast({ title: '图片上传失败', icon: 'none' })
				fail()
			}
		})
	}

	function removeCommentImage(image : string) {
		commentImageUrls.value = commentImageUrls.value.filter((item) : boolean => item != image)
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
			} else {
				uni.reLaunch({ url: '/pages/index/index' })
			}
		}, 250)
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

	function requireLogin() : boolean {
		if (token.value.length > 0) {
			return true
		}
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
		return false
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

	function formatTime(value : string) : string {
		if (value.length >= 16) {
			return value.substring(5, 16).replace('T', ' ')
		}
		return value
	}
</script>

<style>
	.news-page {
		min-height: 100vh;
		height: 100vh;
		width: 100vw;
		max-width: none;
		background-color: #f6f7fb;
		padding-left: 20px;
		padding-right: 20px;
		box-sizing: border-box;
		overflow: hidden;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.news-page-leave {
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

	.news-nav {
		display: flex;
		position: relative;
		height: 58px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.nav-button,
	.nav-spacer {
		display: flex;
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

	.news-scroll {
		width: 100%;
		flex: 1;
		height: auto;
		min-height: 0;
		box-sizing: border-box;
	}

	.news-bottom-spacer {
		height: 118px;
		height: calc(118px + constant(safe-area-inset-bottom));
		height: calc(118px + env(safe-area-inset-bottom));
		flex-shrink: 0;
	}

	.article-card,
	.comment-section {
		border-radius: 28px;
		background-color: #ffffff;
		margin-top: 12px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.article-card {
		overflow: visible;
	}

	.comment-section {
		overflow: hidden;
	}

	.article-body,
	.comment-section {
		padding: 18px;
		box-sizing: border-box;
	}

	.article-body {
		display: flex;
		flex-direction: column;
	}

	.article-kicker {
		display: flex;
		align-self: flex-start;
		height: 30px;
		border-radius: 15px;
		background-color: #eef5ff;
		padding-left: 12px;
		padding-right: 12px;
		align-items: center;
		justify-content: center;
		margin-top: 12px;
	}

	.article-kicker-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #0a84ff;
	}

	.article-title {
		font-size: 26px;
		line-height: 36px;
		font-weight: 900;
		color: #101828;
	}

	.article-meta,
	.article-actions,
	.comment-top {
		display: flex;
		flex-direction: row;
		align-items: center;
	}

	.article-meta {
		margin-top: 12px;
	}

	.article-meta-text,
	.article-meta-dot {
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.article-meta-dot {
		margin-left: 6px;
		margin-right: 6px;
	}

	.article-actions {
		margin-top: 16px;
		flex-wrap: nowrap;
		justify-content: flex-start;
	}

	.article-actions-bottom {
		margin-top: 16px;
		justify-content: space-between;
		padding-bottom: 2px;
	}

	.article-actions-bottom .article-action {
		flex: 1 1 0;
		margin-right: 8px;
		min-width: 0;
	}

	.article-actions-bottom .article-action:last-child {
		margin-right: 0;
	}

	.article-action,
	.comment-like {
		display: flex;
		min-width: 56px;
		height: 34px;
		box-sizing: border-box;
		border-radius: 17px;
		background-color: #f6f7fb;
		margin-right: 8px;
		padding-left: 12px;
		padding-right: 12px;
		flex-direction: row;
		align-items: center;
		justify-content: center;
	}

	.comment-like {
		flex: 0 0 auto;
	}

	.article-action-icon,
	.comment-like-icon {
		font-size: 13px;
		line-height: 18px;
		color: #98a2b3;
		margin-right: 5px;
	}

	.comment-action-icon {
		display: flex;
		position: relative;
		flex: 0 0 auto;
		width: 18px;
		height: 14px;
		border-radius: 8px;
		background-color: #8b95a5;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		margin-right: 6px;
		overflow: visible;
	}

	.comment-action-dot {
		display: block;
		width: 3px;
		height: 3px;
		border-radius: 2px;
		background-color: #ffffff;
		margin-left: 1px;
		margin-right: 1px;
	}

	.comment-action-tail {
		display: block;
		position: absolute;
		width: 6px;
		height: 6px;
		border-radius: 2px;
		background-color: #8b95a5;
		left: 3px;
		bottom: -3px;
		transform: rotate(45deg);
	}

	.comment-action-image {
		width: 18px;
		height: 18px;
		margin-right: 5px;
		flex-shrink: 0;
	}

	.article-like-active,
	.comment-like-active {
		color: #ff3b30;
	}

	.article-fav-active {
		color: #ffcc00;
	}

	.article-action-text,
	.comment-like-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #667085;
	}

	.article-content {
		margin-top: 22px;
		padding-top: 2px;
	}

	.article-paragraph {
		display: block;
		margin-bottom: 16px;
		font-size: 16px;
		line-height: 28px;
		color: #111827;
	}

	.article-image {
		display: block;
		width: 100%;
		height: 188px;
		border-radius: 20px;
		margin-top: 2px;
		margin-bottom: 16px;
		background-color: #eef2f7;
	}

	.article-finished {
		display: flex;
		flex-direction: row;
		align-items: center;
		margin-top: 10px;
		margin-bottom: 2px;
	}

	.article-finished-line {
		flex: 1;
		height: 1px;
		background-color: #eef2f7;
	}

	.article-finished-text {
		padding-left: 10px;
		padding-right: 10px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.comment-headline {
		display: flex;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		flex-wrap: nowrap;
		margin-bottom: 12px;
	}

	.comment-title {
		flex: 1;
		min-width: 0;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.sort-switch {
		display: flex;
		flex: 0 0 auto;
		width: 132px;
		height: 36px;
		border-radius: 18px;
		background-color: #f3f6fb;
		flex-direction: row;
		padding: 4px;
		position: relative;
	}

	.sort-item {
		display: flex;
		flex: 1;
		border-radius: 14px;
		align-items: center;
		justify-content: center;
		position: relative;
		z-index: 1;
	}

	.sort-item-active {
		background-color: transparent;
	}

	.sort-indicator {
		position: absolute;
		left: 4px;
		top: 4px;
		width: 62px;
		height: 28px;
		border-radius: 14px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
		box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
		transition-property: transform;
		transition-duration: 240ms;
		transition-timing-function: ease-out;
		z-index: 0;
	}

	.sort-indicator-0 {
		transform: translateX(0);
	}

	.sort-indicator-1 {
		transform: translateX(62px);
	}

	.sort-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.sort-text-active {
		color: #111827;
	}

	.comment-card {
		border-radius: 22px;
		background-color: #f8fafc;
		padding: 13px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
		transition-property: background-color, border-color, transform;
		transition-duration: 260ms;
	}

	.comment-card-highlight {
		background-color: #eef5ff;
		border-color: rgba(10, 132, 255, 0.28);
		transform: scale(1.01);
	}

	.comment-avatar {
		display: flex;
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		margin-right: 8px;
		overflow: hidden;
	}

	.comment-avatar-image {
		width: 34px;
		height: 34px;
		border-radius: 17px;
	}

	.avatar-placeholder {
		display: flex;
		flex-direction: column;
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

	.comment-main {
		display: flex;
		flex-direction: column;
		flex: 1;
		min-width: 0;
	}

	.comment-author {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.comment-time {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.comment-like {
		margin-right: 0;
	}

	.comment-section {
		margin-bottom: 24px;
	}

	.comment-content {
		display: block;
		margin-top: 8px;
		font-size: 14px;
		line-height: 22px;
		color: #111827;
	}

	.mention-content {
		display: flex;
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

	.comment-mention-token {
		font-size: 14px;
		line-height: 22px;
	}

	.comment-reply-context {
		display: flex;
		align-self: flex-start;
		margin-top: 8px;
		border-radius: 12px;
		background-color: #eef2f7;
		padding: 4px 8px;
	}

	.comment-reply-context-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.comment-image-grid {
		display: flex;
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 10px;
	}

	.comment-image {
		width: 70px;
		height: 70px;
		border-radius: 16px;
		margin-right: 8px;
		margin-bottom: 8px;
		background-color: #eef2f7;
	}

	.comment-empty,
	.empty-state {
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 36px 16px;
	}

	.comment-empty-title,
	.empty-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.comment-empty-desc,
	.empty-desc {
		margin-top: 6px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
		text-align: center;
	}

	.comment-dock {
		display: flex;
		position: fixed;
		left: 20px;
		right: 20px;
		bottom: 18px;
		bottom: calc(18px + constant(safe-area-inset-bottom));
		bottom: calc(18px + env(safe-area-inset-bottom));
		width: auto;
		max-width: none;
		flex-direction: column;
		z-index: 21;
	}

	.comment-draft-images {
		display: flex;
		flex-direction: row;
		width: auto;
		min-height: 70px;
		border-radius: 24px;
		background-color: rgba(255, 255, 255, 0.96);
		border-width: 1px;
		border-color: #edf0f5;
		padding: 8px;
		margin-bottom: 8px;
		box-shadow: 0 14px 36px rgba(16, 24, 40, 0.08);
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

	.draft-image-remove {
		display: flex;
		position: absolute;
		right: 4px;
		top: 4px;
		width: 22px;
		height: 22px;
		border-radius: 11px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
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
		display: flex;
		min-height: 52px;
		border-radius: 26px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		flex-direction: row;
		align-items: center;
		padding: 6px;
		box-shadow: 0 14px 36px rgba(16, 24, 40, 0.08);
	}

	.mention-drawer {
		max-height: 245px;
		border-radius: 26px;
		background-color: rgba(255, 255, 255, 0.96);
		border-width: 1px;
		border-color: rgba(237, 240, 245, 0.96);
		box-shadow: 0 18px 48px rgba(16, 24, 40, 0.14);
		padding: 8px;
		margin-bottom: 8px;
		animation-name: mentionDrawerIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	@keyframes mentionDrawerIn {
		from {
			opacity: 0;
			transform: translateY(14px) scale(0.98);
		}
		to {
			opacity: 1;
			transform: translateY(0) scale(1);
		}
	}

	.mention-drawer-handle {
		width: 36px;
		height: 4px;
		border-radius: 2px;
		background-color: #d9e0ea;
		align-self: center;
		margin-top: 2px;
		margin-bottom: 8px;
	}

	.mention-search-row {
		height: 38px;
		border-radius: 19px;
		background-color: #f6f7fb;
		flex-direction: row;
		align-items: center;
		padding-left: 12px;
		padding-right: 12px;
		margin-bottom: 8px;
	}

	.mention-search-mark {
		font-size: 17px;
		line-height: 22px;
		font-weight: 900;
		color: #0a84ff;
		margin-right: 6px;
	}

	.mention-search-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #667085;
	}

	.mention-user-scroll {
		max-height: 180px;
	}

	.mention-user-row {
		height: 50px;
		border-radius: 18px;
		flex-direction: row;
		align-items: center;
		padding-left: 8px;
		padding-right: 8px;
	}

	.mention-user-row:active {
		background-color: #f6f7fb;
	}

	.mention-user-avatar {
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		overflow: hidden;
		margin-right: 10px;
	}

	.mention-user-avatar-image {
		width: 34px;
		height: 34px;
		border-radius: 17px;
	}

	.mention-user-name {
		flex: 1;
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.mention-user-plus {
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #0a84ff;
	}

	.mention-empty {
		height: 54px;
		align-items: center;
		justify-content: center;
	}

	.mention-empty-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #98a2b3;
	}

	.comment-reply-pill {
		display: flex;
		flex: 0 0 auto;
		height: 32px;
		max-width: 116px;
		border-radius: 16px;
		background-color: #eef5ff;
		flex-direction: row;
		align-items: center;
		padding-left: 10px;
		padding-right: 7px;
		margin-left: 4px;
		margin-right: 6px;
	}

	.comment-reply-pill-text {
		flex: 1;
		min-width: 0;
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #0a84ff;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.comment-reply-pill-close {
		display: flex;
		position: relative;
		width: 16px;
		height: 16px;
		border-radius: 8px;
		background-color: rgba(10, 132, 255, 0.16);
		margin-left: 4px;
		align-items: center;
		justify-content: center;
	}

	.reply-close-line {
		width: 8px;
		background-color: #0a84ff;
	}

	.comment-image-button {
		display: flex;
		flex: 0 0 auto;
		width: 40px;
		height: 40px;
		border-radius: 20px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
	}

	.tool-image-icon {
		position: relative;
		width: 18px;
		height: 16px;
		border-radius: 5px;
		border-width: 2px;
		border-color: #0a84ff;
		overflow: hidden;
	}

	.tool-image-sun {
		position: absolute;
		right: 3px;
		top: 3px;
		width: 4px;
		height: 4px;
		border-radius: 2px;
		background-color: #0a84ff;
	}

	.tool-image-hill {
		position: absolute;
		left: 3px;
		bottom: 1px;
		width: 12px;
		height: 6px;
		border-radius: 6px;
		background-color: #0a84ff;
	}

	.comment-input {
		flex: 1;
		height: 40px;
		font-size: 14px;
		color: #111827;
		padding-left: 10px;
		padding-right: 12px;
	}

	.input-placeholder {
		color: #98a2b3;
	}

	.comment-send {
		display: flex;
		flex: 0 0 auto;
		height: 40px;
		border-radius: 20px;
		background-color: #111827;
		padding-left: 16px;
		padding-right: 16px;
		align-items: center;
		justify-content: center;
	}

	.comment-send-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #ffffff;
	}

	.inline-image-preview {
		display: flex;
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(5, 7, 13, 0.84);
		z-index: 80;
	}

	.inline-preview-top {
		display: flex;
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
		display: flex;
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
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.inline-preview-image {
		width: 100%;
		height: 100vh;
	}

	.inline-preview-bottom {
		display: flex;
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
		display: flex;
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

	@keyframes appPageIn {
		from { opacity: 0; transform: translateX(28px); }
		to { opacity: 1; transform: translateX(0); }
	}

	@keyframes appPageOut {
		from { opacity: 1; transform: translateX(0); }
		to { opacity: 0; transform: translateX(28px); }
	}
</style>

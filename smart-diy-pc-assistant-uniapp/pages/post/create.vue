<template>
	<view :class="pageLeaving ? 'create-page create-page-leave' : 'create-page'">
		<view class="status-space"></view>
		<view class="create-nav">
			<view class="nav-button" @click="goBack">
				<view class="back-chevron">
					<view class="back-chevron-line back-chevron-line-top"></view>
					<view class="back-chevron-line back-chevron-line-bottom"></view>
				</view>
			</view>
			<text class="nav-title">发布动态</text>
			<view class="nav-spacer"></view>
		</view>

		<scroll-view class="create-scroll" scroll-y="true">
			<view class="composer-card">
				<textarea class="composer-textarea" v-model="content" placeholder="分享装机想法，输入 @ 艾特用户" placeholder-class="input-placeholder" maxlength="-1" @input="handleContentInputChange" @focus="refreshMentionDrawer" />
			</view>

			<view v-if="mentionDrawerVisible" class="mention-drawer">
				<view class="mention-drawer-handle"></view>
				<view class="mention-search-row">
					<text class="mention-search-mark">@</text>
					<text class="mention-search-text">{{ mentionQuery.length > 0 ? mentionQuery : '搜索昵称' }}</text>
				</view>
				<scroll-view class="mention-user-scroll" scroll-y="true">
					<view v-for="user in mentionUsers" :key="user.userId" class="mention-user-row" @click="chooseContentMention(user)">
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

			<view v-if="imageUrls.length > 0" class="image-grid">
				<view v-for="(image, index) in imageUrls" :key="image" :class="getImageWrapClass(index)" :style="getImageDragStyle(index)" @touchstart.stop="startImageTouch(index, $event)" @touchmove.stop="moveImageDrag($event)" @touchend.stop="endImageDrag">
					<image class="draft-image" :src="image" mode="aspectFill" @click="previewImage(image)"></image>
					<view class="image-remove" @click.stop="removeImage(image)">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
			</view>

			<view v-if="selectedBuildId > 0" class="selected-build" @click="openBuildPreview">
				<view class="build-icon">
					<view class="build-icon-card">
						<view class="build-icon-head"></view>
						<view class="build-icon-row"></view>
						<view class="build-icon-row build-icon-row-short"></view>
					</view>
				</view>
				<view class="selected-build-main">
					<text class="selected-build-title">{{ selectedBuildTitle }}</text>
					<text class="selected-build-desc">已关联配置，点击可预览</text>
				</view>
				<view class="build-clear" @click.stop="clearBuild">
					<view class="close-line"></view>
					<view class="close-line close-line-two"></view>
				</view>
			</view>

			<view class="tool-grid">
				<view class="tool-card" @click="pickImages">
					<view class="tool-symbol tool-symbol-blue">
						<view class="image-icon-sun"></view>
						<view class="image-icon-hill"></view>
					</view>
					<text class="tool-card-title">图片</text>
					<text class="tool-card-meta">{{ imageUrls.length }}/9</text>
				</view>
				<view :class="selectedBuildId > 0 ? 'tool-card tool-card-right tool-card-active' : 'tool-card tool-card-right'" @click="toggleBuildPicker">
					<view class="tool-symbol tool-symbol-green">
						<view class="config-icon-head"></view>
						<view class="config-icon-row"></view>
						<view class="config-icon-row config-icon-row-short"></view>
					</view>
					<text class="tool-card-title">配置</text>
					<text class="tool-card-meta">{{ selectedBuildId > 0 ? '已关联' : '可选' }}</text>
				</view>
			</view>

			<view v-if="buildPickerVisible" class="build-list-section">
				<view class="section-head">
					<text class="section-title">选择关联配置</text>
					<view class="section-refresh" @click="loadBuilds">
						<text class="section-refresh-text">{{ buildLoading ? '读取中' : '刷新' }}</text>
					</view>
				</view>
				<view v-for="item in buildList" :key="item.id" class="build-item" @click="toggleBuild(item)">
					<view class="build-item-main">
						<text class="build-item-title">{{ item.title }}</text>
						<text class="build-item-desc">¥{{ item.totalPrice }} · 性能 {{ item.performanceScore }} 分</text>
					</view>
					<view :class="selectedBuildId == item.id ? 'build-check build-check-active' : 'build-check'">
						<text :class="selectedBuildId == item.id ? 'build-check-text build-check-text-active' : 'build-check-text'">{{ selectedBuildId == item.id ? '✓' : '+' }}</text>
					</view>
				</view>
				<view v-if="buildList.length == 0 && !buildLoading" class="empty-builds">
					<text class="empty-builds-title">暂无可关联配置</text>
					<text class="empty-builds-desc">先在配置页保存一套方案后，就可以关联到动态里。</text>
				</view>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>

		<view class="bottom-submit-wrap">
			<view :class="canSubmit() ? 'bottom-submit bottom-submit-ready' : 'bottom-submit'" @click="submitPost">
				<text :class="canSubmit() ? 'bottom-submit-text bottom-submit-text-ready' : 'bottom-submit-text'">{{ publishing ? '正在发布...' : '发布动态' }}</text>
			</view>
		</view>

		<view v-if="progressVisible" class="progress-mask">
			<view class="progress-card">
				<text class="progress-title">{{ progressTitle }}</text>
				<view class="progress-track">
					<view class="progress-fill" :style="'width: ' + progressPercent + '%'"></view>
				</view>
				<text class="progress-text">{{ progressPercent }}%</text>
			</view>
		</view>
	</view>
</template>

<script setup lang="ts">
	import { ref } from 'vue'
	import { onShow } from '@dcloudio/uni-app'
	import { API_BASE_URL } from '../../common/api'
	import { MentionSearchUser, getActiveMentionQuery, insertMention, normalizeMentionSearchUsers } from '../../common/mention'

	type BuildConfigItem = {
		id : number
		title : string
		totalPrice : number
		performanceScore : number
	}

	const apiBaseUrl = API_BASE_URL
	const pageLeaving = ref(false)
	const token = ref('')
	const content = ref('')
	const imageUrls = ref([] as string[])
	const imageSortActive = ref(false)
	const draggingImageIndex = ref(-1)
	const imageDragStartX = ref(0)
	const imageDragStartY = ref(0)
	const imageDragOffsetX = ref(0)
	const imageDragOffsetY = ref(0)
	const imageLongPressTimer = ref(0)
	const buildPickerVisible = ref(false)
	const buildLoading = ref(false)
	const buildList = ref([] as BuildConfigItem[])
	const selectedBuildId = ref(0)
	const selectedBuildTitle = ref('')
	const publishing = ref(false)
	const progressVisible = ref(false)
	const progressTitle = ref('正在发布')
	const progressPercent = ref(0)
	const mentionDrawerVisible = ref(false)
	const mentionLoading = ref(false)
	const mentionQuery = ref('')
	const mentionUsers = ref([] as MentionSearchUser[])
	let mentionSearchTimer : number = 0

	onShow(() => {
		loadAuth()
		if (token.value.length == 0) {
			openAuthPage()
		}
	})

	function loadAuth() {
		const savedToken = uni.getStorageSync('app_token')
		token.value = savedToken == null ? '' : savedToken as string
	}

	function openAuthPage() {
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function goBack() {
		if (publishing.value) {
			return
		}
		pageLeaving.value = true
		setTimeout(() => {
			const pages = getCurrentPages()
			if (pages.length > 1) {
				uni.navigateBack({
					animationType: 'slide-out-right',
					animationDuration: 260
				})
				return
			}
			uni.reLaunch({
				url: '/pages/index/index'
			})
		}, 260)
	}

	function canSubmit() : boolean {
		return !publishing.value && content.value.trim().length > 0
	}

	function handleContentInputChange(event : any) {
		const value = event == null || event.detail == null ? '' : event.detail.value as string
		content.value = value
		refreshMentionDrawer()
	}

	function refreshMentionDrawer() {
		const query = getActiveMentionQuery(content.value)
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
			header: token.value.length == 0 ? {} : { 'Authorization': 'Bearer ' + token.value },
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

	function chooseContentMention(user : MentionSearchUser) {
		content.value = insertMention(content.value, user.nickname)
		mentionDrawerVisible.value = false
		mentionUsers.value = [] as MentionSearchUser[]
	}

	function pickImages() {
		if (imageUrls.value.length >= 9) {
			uni.showToast({
				title: '最多添加 9 张图片',
				icon: 'none'
			})
			return
		}
		uni.chooseImage({
			count: 9 - imageUrls.value.length,
			sizeType: ['compressed'],
			sourceType: ['album', 'camera'],
			success: (res) => {
				const paths = res.tempFilePaths as string[]
				for (let i = 0; i < paths.length; i++) {
					if (imageUrls.value.length < 9) {
						imageUrls.value.push(paths[i])
					}
				}
			}
		})
	}

	function removeImage(image : string) {
		imageUrls.value = imageUrls.value.filter((item) : boolean => item != image)
	}

	function startImageTouch(index : number, event : TouchEvent) {
		if (event.touches.length == 0) {
			return
		}
		if (imageLongPressTimer.value > 0) {
			clearTimeout(imageLongPressTimer.value)
		}
		imageSortActive.value = false
		draggingImageIndex.value = index
		imageDragStartX.value = event.touches[0].clientX
		imageDragStartY.value = event.touches[0].clientY
		imageDragOffsetX.value = 0
		imageDragOffsetY.value = 0
		imageLongPressTimer.value = setTimeout(() => {
			if (draggingImageIndex.value == index) {
				imageSortActive.value = true
			}
		}, 360)
	}

	function moveImageDrag(event : TouchEvent) {
		if (!imageSortActive.value || draggingImageIndex.value < 0 || event.touches.length == 0) {
			return
		}
		const distanceX = event.touches[0].clientX - imageDragStartX.value
		const distanceY = event.touches[0].clientY - imageDragStartY.value
		imageDragOffsetX.value = distanceX
		imageDragOffsetY.value = distanceY
		const absX = Math.abs(distanceX)
		const absY = Math.abs(distanceY)
		if (absX < 36 && absY < 48) {
			return
		}
		const from = draggingImageIndex.value
		let to = from
		if (absY > absX && absY >= 48) {
			to = from + (distanceY > 0 ? 3 : -3)
			if (to < 0) {
				to = 0
			}
			if (to > imageUrls.value.length - 1) {
				to = imageUrls.value.length - 1
			}
		} else {
			if (distanceX > 0 && from < imageUrls.value.length - 1) {
				to = from + 1
			}
			if (distanceX < 0 && from > 0) {
				to = from - 1
			}
		}
		if (to == from) {
			return
		}
		swapImages(from, to)
		draggingImageIndex.value = to
		imageDragStartX.value = event.touches[0].clientX
		imageDragStartY.value = event.touches[0].clientY
		imageDragOffsetX.value = 0
		imageDragOffsetY.value = 0
	}

	function endImageDrag() {
		if (imageLongPressTimer.value > 0) {
			clearTimeout(imageLongPressTimer.value)
			imageLongPressTimer.value = 0
		}
		draggingImageIndex.value = -1
		imageDragOffsetX.value = 0
		imageDragOffsetY.value = 0
		setTimeout(() => {
			imageSortActive.value = false
		}, 160)
	}

	function swapImages(from : number, to : number) {
		const next = imageUrls.value.slice()
		const current = next[from]
		next[from] = next[to]
		next[to] = current
		imageUrls.value = next
	}

	function getImageWrapClass(index : number) : string {
		if (!imageSortActive.value) {
			return 'image-cell'
		}
		if (draggingImageIndex.value == index) {
			return 'image-cell image-cell-dragging'
		}
		return 'image-cell image-cell-sorting'
	}

	function getImageDragStyle(index : number) : string {
		if (!imageSortActive.value || draggingImageIndex.value != index) {
			return ''
		}
		return 'transform: translate(' + imageDragOffsetX.value + 'px, ' + imageDragOffsetY.value + 'px) scale(1.12); z-index: 20;'
	}

	function previewImage(image : string) {
		uni.previewImage({
			urls: imageUrls.value,
			current: image
		})
	}

	function toggleBuildPicker() {
		buildPickerVisible.value = !buildPickerVisible.value
		if (buildPickerVisible.value && buildList.value.length == 0) {
			loadBuilds()
		}
	}

	function loadBuilds() {
		if (token.value.length == 0 || buildLoading.value) {
			return
		}
		buildLoading.value = true
		uni.request({
			url: apiBaseUrl + '/api/build-configs?segment=all',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					buildList.value = (body.data as any[]).map((item : any) : BuildConfigItem => normalizeBuild(item))
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					openAuthPage()
				}
			},
			fail: () => {
				uni.showToast({
					title: '暂时读取不到配置单',
					icon: 'none'
				})
			},
			complete: () => {
				buildLoading.value = false
			}
		})
	}

	function normalizeBuild(item : any) : BuildConfigItem {
		return {
			id: Number(item == null || item.id == null ? 0 : item.id),
			title: item == null || item.title == null ? '' : item.title as string,
			totalPrice: Number(item == null || item.totalPrice == null ? 0 : item.totalPrice),
			performanceScore: Number(item == null || item.performanceScore == null ? 0 : item.performanceScore)
		}
	}

	function toggleBuild(item : BuildConfigItem) {
		if (selectedBuildId.value == item.id) {
			clearBuild()
			return
		}
		selectedBuildId.value = item.id
		selectedBuildTitle.value = item.title
		buildPickerVisible.value = false
	}

	function clearBuild() {
		selectedBuildId.value = 0
		selectedBuildTitle.value = ''
	}

	function openBuildPreview() {
		if (selectedBuildId.value <= 0) {
			return
		}
		uni.setStorageSync('current_build_id', selectedBuildId.value)
		uni.showToast({
			title: '可在配置页查看详情',
			icon: 'none'
		})
	}

	function submitPost() {
		if (!canSubmit()) {
			if (content.value.trim().length == 0) {
				uni.showToast({
					title: '先写点内容',
					icon: 'none'
				})
			}
			return
		}
		publishing.value = true
		progressVisible.value = true
		progressTitle.value = '正在发布'
		progressPercent.value = 0
		const draftContent = content.value.trim()
		const draftImages = imageUrls.value.slice()
		const draftBuildId = selectedBuildId.value
		uploadImages(draftImages, (uploadedKeys : string[]) => {
			createPost(draftContent, uploadedKeys, draftBuildId)
		}, () => {
			publishing.value = false
			progressVisible.value = false
		})
	}

	function uploadImages(images : string[], complete : (keys : string[]) => void, fail : () => void) {
		if (images.length == 0) {
			complete([] as string[])
			return
		}
		uploadImageAt(images, 0, [] as string[], complete, fail)
	}

	function uploadImageAt(images : string[], index : number, uploaded : string[], complete : (keys : string[]) => void, fail : () => void) {
		if (index >= images.length) {
			progressTitle.value = '正在发布'
			progressPercent.value = 95
			complete(uploaded)
			return
		}
		progressTitle.value = '正在上传图片'
		progressPercent.value = Math.floor(index * 100 / images.length)
		uni.uploadFile({
			url: apiBaseUrl + '/api/files/upload',
			filePath: images[index],
			name: 'file',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				let body = null as any
				try {
					body = JSON.parse(res.data as string) as any
				} catch (error) {
					body = null
				}
				if (body != null && body.code == 0 && body.data != null) {
					uploaded.push((body.data as any).key as string)
					progressPercent.value = Math.floor((index + 1) * 100 / images.length)
					uploadImageAt(images, index + 1, uploaded, complete, fail)
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '图片上传失败',
					icon: 'none'
				})
				fail()
			},
			fail: () => {
				uni.showToast({
					title: '图片上传失败',
					icon: 'none'
				})
				fail()
			}
		})
	}

	function createPost(draftContent : string, imageKeys : string[], buildId : number) {
		uni.request({
			url: apiBaseUrl + '/api/community/posts',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				content: draftContent,
				imageKeys: imageKeys,
				buildConfigId: buildId > 0 ? buildId : null
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					progressTitle.value = '发布成功'
					progressPercent.value = 100
					uni.showToast({
						title: '已发布',
						icon: 'none'
					})
					setTimeout(() => {
						uni.navigateBack()
					}, 360)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					openAuthPage()
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '发布失败',
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
				publishing.value = false
				setTimeout(() => {
					progressVisible.value = false
				}, 500)
			}
		})
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
</script>

<style>
	.create-page {
		min-height: 100vh;
		background-color: #f6f7fb;
		color: #111827;
		display: flex;
		flex-direction: column;
		animation-name: appPageIn;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.create-page-leave {
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
		height: 28px;
		height: calc(28px + constant(safe-area-inset-top));
		height: calc(28px + env(safe-area-inset-top));
	}

	.create-nav {
		position: relative;
		height: 56px;
		padding-left: 20px;
		padding-right: 20px;
		display: flex;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		flex-shrink: 0;
	}

	.nav-button {
		width: 40px;
		height: 40px;
		border-radius: 20px;
		background-color: #ffffff;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.back-chevron {
		position: relative;
		width: 16px;
		height: 16px;
	}

	.back-chevron-line {
		position: absolute;
		left: 3px;
		width: 12px;
		height: 3px;
		border-radius: 2px;
		background-color: #111827;
	}

	.back-chevron-line-top {
		top: 3px;
		transform: rotate(-45deg);
	}

	.back-chevron-line-bottom {
		top: 10px;
		transform: rotate(45deg);
	}

	.nav-title {
		position: absolute;
		left: 80px;
		right: 80px;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #020817;
		text-align: center;
		pointer-events: none;
	}

	.nav-submit {
		height: 40px;
		min-width: 58px;
		border-radius: 20px;
		padding-left: 16px;
		padding-right: 16px;
		background-color: #e9edf5;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.nav-submit-ready {
		background-color: #111827;
	}

	.nav-spacer {
		width: 40px;
		height: 40px;
		flex-shrink: 0;
	}

	.nav-submit-text {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #667085;
	}

	.nav-submit-text-ready {
		color: #ffffff;
	}

	.create-scroll {
		flex: 1;
		height: 0;
		padding-left: 20px;
		padding-right: 20px;
		box-sizing: border-box;
	}

	.composer-card,
	.tool-section,
	.selected-build,
	.build-list-section {
		width: 100%;
		border-radius: 28px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		box-sizing: border-box;
	}

	.composer-card {
		min-height: 210px;
		margin-top: 8px;
		padding: 18px;
		display: flex;
		flex-direction: column;
	}

	.composer-textarea {
		width: 100%;
		min-height: 174px;
		font-size: 16px;
		line-height: 24px;
		color: #111827;
		box-sizing: border-box;
	}

	.mention-drawer {
		width: 100%;
		max-height: 245px;
		border-radius: 26px;
		background-color: rgba(255, 255, 255, 0.96);
		border-width: 1px;
		border-color: rgba(237, 240, 245, 0.96);
		box-shadow: 0 18px 48px rgba(16, 24, 40, 0.12);
		padding: 8px;
		margin-top: 10px;
		box-sizing: border-box;
		animation-name: mentionDrawerIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	@keyframes mentionDrawerIn {
		from {
			opacity: 0;
			transform: translateY(-8px) scale(0.98);
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

	.avatar-placeholder {
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

	.input-placeholder {
		color: #98a2b3;
	}

	.image-grid {
		width: 100%;
		display: flex;
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 14px;
	}

	.image-cell {
		position: relative;
		width: 96px;
		height: 96px;
		border-radius: 20px;
		margin-right: 10px;
		margin-bottom: 10px;
		overflow: hidden;
		background-color: #edf2f8;
	}

	.image-cell-sorting {
		animation-name: imageShake;
		animation-duration: 150ms;
		animation-iteration-count: infinite;
		animation-timing-function: ease-in-out;
	}

	.image-cell-dragging {
		opacity: 0.88;
		z-index: 20;
	}

	@keyframes imageShake {
		0% { transform: rotate(-1deg); }
		50% { transform: rotate(1deg); }
		100% { transform: rotate(-1deg); }
	}

	.draft-image {
		width: 100%;
		height: 100%;
		border-radius: 20px;
	}

	.image-remove,
	.build-clear {
		position: absolute;
		width: 26px;
		height: 26px;
		border-radius: 13px;
		background-color: rgba(255, 255, 255, 0.92);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.image-remove {
		right: 6px;
		top: 6px;
	}

	.close-line {
		position: absolute;
		width: 12px;
		height: 2px;
		border-radius: 1px;
		background-color: #667085;
		transform: rotate(45deg);
	}

	.close-line-two {
		transform: rotate(-45deg);
	}

	.selected-build {
		position: relative;
		margin-top: 14px;
		padding: 14px;
		display: flex;
		flex-direction: row;
		align-items: center;
	}

	.build-icon {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		background-color: #111827;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 12px;
		flex-shrink: 0;
	}

	.build-icon-card {
		width: 18px;
		height: 22px;
		border-radius: 5px;
		border-width: 2px;
		border-color: #ffffff;
		padding: 2px;
		display: flex;
		flex-direction: column;
	}

	.build-icon-head,
	.build-icon-row {
		width: 9px;
		height: 3px;
		border-radius: 2px;
		background-color: #ffffff;
	}

	.build-icon-row {
		margin-top: 3px;
	}

	.build-icon-row-short {
		width: 6px;
	}

	.selected-build-main {
		flex: 1;
		min-width: 0;
		padding-right: 32px;
	}

	.selected-build-title {
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.selected-build-desc {
		margin-top: 3px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}

	.build-clear {
		right: 14px;
		top: 23px;
		background-color: #f3f6fb;
	}

	.tool-grid {
		width: 100%;
		margin-top: 14px;
		display: flex;
		flex-direction: row;
	}

	.tool-card {
		flex: 1;
		min-width: 0;
		height: 58px;
		border-radius: 22px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding-left: 12px;
		padding-right: 12px;
		margin-right: 10px;
		box-sizing: border-box;
		display: flex;
		flex-direction: row;
		align-items: center;
	}

	.tool-card-right {
		margin-right: 0;
	}

	.tool-card-active {
		border-color: #d8e7ff;
		background-color: #fbfdff;
	}

	.tool-symbol {
		position: relative;
		width: 32px;
		height: 32px;
		border-radius: 12px;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
		margin-right: 9px;
	}

	.tool-symbol-blue {
		background-color: #eef5ff;
	}

	.tool-symbol-green {
		background-color: #effaf3;
		flex-direction: column;
	}

	.tool-card-meta {
		font-size: 11px;
		line-height: 16px;
		font-weight: 800;
		color: #8a96a8;
		background-color: #f5f7fb;
		border-radius: 10px;
		padding-left: 8px;
		padding-right: 8px;
		padding-top: 2px;
		padding-bottom: 2px;
		flex-shrink: 0;
	}

	.tool-card-title {
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

	.tool-card-desc {
		width: 100%;
		margin-top: 4px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.tool-section {
		margin-top: 14px;
		padding-left: 14px;
		padding-right: 14px;
		display: none;
	}

	.tool-row {
		height: 72px;
		display: flex;
		flex-direction: row;
		align-items: center;
		border-bottom-width: 1px;
		border-bottom-color: #f1f4f8;
	}

	.tool-row:last-child {
		border-bottom-width: 0;
	}

	.tool-icon {
		position: relative;
		width: 42px;
		height: 42px;
		border-radius: 16px;
		display: flex;
		align-items: center;
		justify-content: center;
		margin-right: 12px;
		flex-shrink: 0;
	}

	.tool-icon-blue {
		background-color: #eef5ff;
	}

	.tool-icon-green {
		background-color: #effaf3;
		flex-direction: column;
	}

	.image-icon-sun {
		position: absolute;
		right: 12px;
		top: 12px;
		width: 5px;
		height: 5px;
		border-radius: 3px;
		background-color: #007aff;
	}

	.image-icon-hill {
		width: 20px;
		height: 12px;
		border-radius: 10px;
		background-color: #007aff;
		margin-top: 10px;
	}

	.config-icon-head,
	.config-icon-row {
		width: 16px;
		height: 4px;
		border-radius: 2px;
		background-color: #34c759;
	}

	.config-icon-row {
		margin-top: 4px;
	}

	.config-icon-row-short {
		width: 10px;
	}

	.tool-copy {
		flex: 1;
		min-width: 0;
	}

	.tool-title {
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #111827;
	}

	.tool-desc {
		margin-top: 3px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.tool-arrow {
		font-size: 26px;
		line-height: 30px;
		color: #98a2b3;
		margin-left: 8px;
	}

	.build-list-section {
		margin-top: 14px;
		padding: 16px;
	}

	.section-head {
		display: flex;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12px;
	}

	.section-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.section-refresh {
		height: 30px;
		border-radius: 15px;
		background-color: #f3f6fb;
		padding-left: 12px;
		padding-right: 12px;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.section-refresh-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #007aff;
	}

	.build-item {
		min-height: 64px;
		border-radius: 20px;
		background-color: #f7f9fc;
		padding: 12px;
		margin-bottom: 10px;
		display: flex;
		flex-direction: row;
		align-items: center;
	}

	.build-item-main {
		flex: 1;
		min-width: 0;
		padding-right: 12px;
	}

	.build-item-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.build-item-desc {
		margin-top: 4px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}

	.build-check {
		width: 32px;
		height: 32px;
		border-radius: 16px;
		background-color: #e9edf5;
		display: flex;
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.build-check-active {
		background-color: #111827;
	}

	.build-check-text {
		font-size: 18px;
		line-height: 24px;
		font-weight: 900;
		color: #667085;
	}

	.build-check-text-active {
		color: #ffffff;
	}

	.empty-builds {
		min-height: 150px;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}

	.empty-builds-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.empty-builds-desc {
		width: 240px;
		margin-top: 8px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
		text-align: center;
	}

	.bottom-space {
		height: 104px;
		height: calc(104px + constant(safe-area-inset-bottom));
		height: calc(104px + env(safe-area-inset-bottom));
	}

	.bottom-submit-wrap {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 14px 20px;
		padding-bottom: calc(14px + constant(safe-area-inset-bottom));
		padding-bottom: calc(14px + env(safe-area-inset-bottom));
		background-color: rgba(246, 247, 251, 0.92);
	}

	.bottom-submit {
		height: 52px;
		border-radius: 26px;
		background-color: #e9edf5;
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.bottom-submit-ready {
		background-color: #111827;
	}

	.bottom-submit-text {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #667085;
	}

	.bottom-submit-text-ready {
		color: #ffffff;
	}

	.progress-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(15, 23, 42, 0.24);
		display: flex;
		align-items: center;
		justify-content: center;
	}

	.progress-card {
		width: 260px;
		border-radius: 26px;
		background-color: #ffffff;
		padding: 22px;
		display: flex;
		flex-direction: column;
		align-items: center;
	}

	.progress-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.progress-track {
		width: 100%;
		height: 8px;
		border-radius: 4px;
		background-color: #edf2f8;
		margin-top: 16px;
		overflow: hidden;
	}

	.progress-fill {
		height: 8px;
		border-radius: 4px;
		background-color: #007aff;
	}

	.progress-text {
		margin-top: 10px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}
</style>

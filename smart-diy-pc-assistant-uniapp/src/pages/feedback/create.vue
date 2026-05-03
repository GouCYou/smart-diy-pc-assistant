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
			<text class="nav-title">反馈</text>
			<view class="nav-action" @click="openMine">
				<text class="nav-action-text">我的反馈</text>
			</view>
		</view>

		<scroll-view class="feedback-scroll" scroll-y="true">
			<view class="feedback-card">
				<text class="card-title">反馈内容</text>
				<textarea class="feedback-textarea" v-model="content" placeholder="写下遇到的问题、建议或想补充的功能" placeholder-class="input-placeholder" maxlength="1200" />
				<view class="counter-row">
					<text class="counter-text">{{ content.length }}/1200</text>
				</view>
			</view>

			<view class="feedback-card">
				<view class="card-head">
					<text class="card-title">截图</text>
					<text class="card-meta">{{ imageUrls.length }}/9</text>
				</view>
				<view class="image-grid">
					<view v-for="image in imageUrls" :key="image" class="image-cell">
						<image class="draft-image" :src="image" mode="aspectFill" @click="previewImage(image)"></image>
						<view class="image-remove" @click.stop="removeImage(image)">
							<view class="close-line"></view>
							<view class="close-line close-line-two"></view>
						</view>
					</view>
					<view v-if="imageUrls.length < 9" class="image-add" @click="pickImages">
						<view class="image-add-plus">
							<view class="plus-line"></view>
							<view class="plus-line plus-line-vertical"></view>
						</view>
						<text class="image-add-text">添加图片</text>
					</view>
				</view>
			</view>
			<view class="bottom-space"></view>
		</scroll-view>

		<view class="bottom-submit-wrap">
			<view :class="canSubmit() ? 'bottom-submit bottom-submit-ready' : 'bottom-submit'" @click="submitFeedback">
				<text :class="canSubmit() ? 'bottom-submit-text bottom-submit-text-ready' : 'bottom-submit-text'">{{ submitting ? '正在提交...' : '提交反馈' }}</text>
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

	const apiBaseUrl = API_BASE_URL
	const pageLeaving = ref(false)
	const token = ref('')
	const content = ref('')
	const imageUrls = ref([] as string[])
	const submitting = ref(false)
	const progressVisible = ref(false)
	const progressTitle = ref('正在提交')
	const progressPercent = ref(0)

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
		if (submitting.value) {
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
		}, 240)
	}

	function openMine() {
		uni.navigateTo({
			url: '/pages/feedback/list',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function canSubmit() : boolean {
		return !submitting.value && content.value.trim().length > 0
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

	function previewImage(image : string) {
		uni.previewImage({
			urls: imageUrls.value,
			current: image
		})
	}

	function submitFeedback() {
		if (!canSubmit()) {
			uni.showToast({
				title: '先写下反馈内容',
				icon: 'none'
			})
			return
		}
		submitting.value = true
		progressVisible.value = true
		progressTitle.value = '正在提交'
		progressPercent.value = 0
		const draftContent = content.value.trim()
		const draftImages = imageUrls.value.slice()
		uploadImages(draftImages, (uploadedKeys : string[]) => {
			createFeedback(draftContent, uploadedKeys)
		}, () => {
			submitting.value = false
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
			progressTitle.value = '正在提交'
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

	function createFeedback(draftContent : string, imageKeys : string[]) {
		uni.request({
			url: apiBaseUrl + '/api/feedback',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: {
				content: draftContent,
				imageKeys: imageKeys
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					progressTitle.value = '提交成功'
					progressPercent.value = 100
					uni.showToast({
						title: '已提交',
						icon: 'none'
					})
					setTimeout(() => {
						uni.redirectTo({
							url: '/pages/feedback/list',
							animationType: 'slide-in-right',
							animationDuration: 260
						})
					}, 360)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					openAuthPage()
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '提交失败',
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
				submitting.value = false
				setTimeout(() => {
					progressVisible.value = false
				}, 500)
			}
		})
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

	.nav-button {
		width: 40px;
		height: 40px;
		border-radius: 20px;
		background-color: rgba(255, 255, 255, 0.86);
		align-items: center;
		justify-content: center;
		flex-shrink: 0;
	}

	.nav-title {
		flex: 1;
		text-align: center;
		font-size: 18px;
		line-height: 25px;
		font-weight: 900;
		color: #111827;
	}

	.nav-action {
		height: 40px;
		min-width: 82px;
		border-radius: 20px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
		padding-left: 14px;
		padding-right: 14px;
		flex-shrink: 0;
	}

	.nav-action-text {
		font-size: 13px;
		line-height: 18px;
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

	.feedback-card {
		width: 100%;
		border-radius: 28px;
		background-color: #ffffff;
		padding: 18px;
		margin-bottom: 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.card-head {
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 12px;
	}

	.card-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
	}

	.card-meta,
	.counter-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #98a2b3;
	}

	.feedback-textarea {
		width: 100%;
		min-height: 190px;
		margin-top: 12px;
		font-size: 16px;
		line-height: 24px;
		color: #111827;
	}

	.input-placeholder {
		color: #98a2b3;
	}

	.counter-row {
		margin-top: 8px;
		align-items: flex-end;
	}

	.image-grid {
		flex-direction: row;
		flex-wrap: wrap;
	}

	.image-cell,
	.image-add {
		position: relative;
		width: 96px;
		height: 96px;
		border-radius: 20px;
		margin-right: 10px;
		margin-bottom: 10px;
		overflow: hidden;
		background-color: #edf2f8;
	}

	.draft-image {
		width: 100%;
		height: 100%;
		border-radius: 20px;
	}

	.image-add {
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: #d9e2ef;
		border-style: dashed;
	}

	.image-add-plus {
		position: relative;
		width: 28px;
		height: 28px;
	}

	.plus-line {
		position: absolute;
		left: 5px;
		top: 13px;
		width: 18px;
		height: 3px;
		border-radius: 2px;
		background-color: #667085;
	}

	.plus-line-vertical {
		transform: rotate(90deg);
	}

	.image-add-text {
		margin-top: 4px;
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.image-remove {
		position: absolute;
		right: 6px;
		top: 6px;
		width: 26px;
		height: 26px;
		border-radius: 13px;
		background-color: rgba(255, 255, 255, 0.92);
		align-items: center;
		justify-content: center;
	}

	.close-line {
		position: absolute;
		width: 12px;
		height: 2px;
		border-radius: 2px;
		background-color: #111827;
		transform: rotate(45deg);
	}

	.close-line-two {
		transform: rotate(-45deg);
	}

	.bottom-space {
		height: 120px;
		height: calc(120px + constant(safe-area-inset-bottom));
		height: calc(120px + env(safe-area-inset-bottom));
		flex-shrink: 0;
	}

	.bottom-submit-wrap {
		position: fixed;
		left: 18px;
		right: 18px;
		bottom: 18px;
		bottom: calc(18px + constant(safe-area-inset-bottom));
		bottom: calc(18px + env(safe-area-inset-bottom));
	}

	.bottom-submit {
		width: 100%;
		height: 58px;
		border-radius: 29px;
		background-color: #e5e7eb;
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
		color: #98a2b3;
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
		background-color: rgba(15, 23, 42, 0.26);
		align-items: center;
		justify-content: center;
		z-index: 60;
	}

	.progress-card {
		width: 260px;
		border-radius: 28px;
		background-color: #ffffff;
		padding: 22px;
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
		height: 10px;
		border-radius: 5px;
		background-color: #edf0f5;
		margin-top: 16px;
		overflow: hidden;
	}

	.progress-fill {
		height: 10px;
		border-radius: 5px;
		background-color: #111827;
	}

	.progress-text {
		margin-top: 10px;
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #667085;
	}
</style>

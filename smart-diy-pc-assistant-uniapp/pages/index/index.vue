<template>
	<view class="app-shell" @touchstart="handleTouchStart" @touchend="handleTouchEnd">
		<view v-if="activeTab == 0" :class="getPageClass('chat-page')">
			<view class="status-space"></view>
			<view class="chat-nav">
				<view class="nav-button" @click="toggleHistory">
					<view class="menu-line"></view>
					<view class="menu-line menu-line-short"></view>
				</view>
				<text class="nav-title">对话</text>
				<view :class="newConversationPressed ? 'nav-button nav-button-pressed nav-button-plus-active' : 'nav-button'" @click="startNewConversation">
					<view class="plus-line"></view>
					<view class="plus-line plus-line-vertical"></view>
				</view>
			</view>

			<scroll-view class="chat-scroll" scroll-y="true">
				<view v-if="chatMessages.length == 0" class="chat-empty">
					<view class="ai-orbit">
						<view class="ai-core">
							<text class="ai-core-text">AI</text>
						</view>
						<view class="ai-dot ai-dot-one"></view>
						<view class="ai-dot ai-dot-two"></view>
						<view class="ai-dot ai-dot-three"></view>
					</view>
					<text class="chat-empty-title">有什么装机问题？</text>
					<text class="chat-empty-desc">告诉我预算、用途、屏幕分辨率、外观偏好，我会帮你整理成清晰的配置思路。</text>
					<view class="prompt-grid">
						<view v-for="item in promptChips" :key="item" class="prompt-card" @click="choosePrompt(item)">
							<text class="prompt-text">{{ item }}</text>
						</view>
					</view>
				</view>

				<view v-else class="message-list">
					<view v-for="(message, index) in chatMessages" :key="index" :class="message.role == 'user' ? 'message-row message-row-user' : 'message-row'">
						<view :class="message.role == 'user' ? 'message-bubble message-bubble-user' : 'message-bubble message-bubble-ai'">
							<text :class="message.role == 'user' ? 'message-text message-text-user' : 'message-text'">{{ message.content }}</text>
						</view>
						<view v-if="message.buildConfigId > 0" class="chat-build-card" @click="openChatBuildDetail(message.buildConfigId)">
							<view class="chat-build-icon">
								<text class="chat-build-icon-text">AI</text>
							</view>
							<view class="chat-build-main">
								<text class="chat-build-title">{{ message.buildConfigTitle }}</text>
								<text class="chat-build-desc">点击查看配置详情</text>
							</view>
							<text class="chat-build-arrow">›</text>
						</view>
					</view>
					<view v-if="chatLoading" class="message-row">
						<view class="message-bubble message-bubble-ai message-bubble-breathing">
							<view class="thinking-mark">
								<text class="thinking-mark-text">AI</text>
							</view>
							<text class="breathing-title">正在思考</text>
						</view>
					</view>
				</view>
			</scroll-view>

			<view v-if="attachedBuildId > 0" class="composer-attachment">
				<text class="composer-attachment-text">已附加：{{ attachedBuildTitle }}</text>
				<view class="composer-attachment-close" @click="clearAttachedBuild">
					<view class="close-line"></view>
					<view class="close-line close-line-two"></view>
				</view>
			</view>
			<view class="composer">
				<view :class="attachedBuildId > 0 ? 'attach-button attach-button-active' : 'attach-button'" @click="openChatBuildPicker">
					<view class="attach-icon-line"></view>
					<view class="attach-icon-dot"></view>
				</view>
				<input class="composer-input" v-model="draft" confirm-type="send" placeholder="询问任何装机问题" placeholder-class="composer-placeholder" @confirm="submitMessage" />
				<view :class="draft.length > 0 || attachedBuildId > 0 ? 'send-button send-button-ready' : 'send-button'" @click="submitMessage">
					<view class="send-arrow-line"></view>
					<view class="send-arrow-head send-arrow-head-top"></view>
					<view class="send-arrow-head send-arrow-head-bottom"></view>
				</view>
			</view>

		</view>

		<view v-if="activeTab == 1" :class="getPageClass('normal-page')">
			<view class="status-space"></view>
			<view class="page-head page-head-row">
				<view class="page-head-main">
					<text class="page-title page-title-inset">配置</text>
				</view>
				<view class="small-primary" @click="createBuild">
					<text class="small-primary-text">新建</text>
				</view>
			</view>

			<view class="segmented">
				<view :class="buildSegment == 0 ? 'segment-active' : 'segment'" @click="selectBuildSegment(0)">
					<text :class="buildSegment == 0 ? 'segment-active-text' : 'segment-text'">自定义</text>
				</view>
				<view :class="buildSegment == 1 ? 'segment-active' : 'segment'" @click="selectBuildSegment(1)">
					<text :class="buildSegment == 1 ? 'segment-active-text' : 'segment-text'">AI 生成</text>
				</view>
				<view :class="buildSegment == 2 ? 'segment-active' : 'segment'" @click="selectBuildSegment(2)">
					<text :class="buildSegment == 2 ? 'segment-active-text' : 'segment-text'">收藏</text>
				</view>
			</view>

			<view class="section-search">
				<view class="section-search-icon">
					<image class="section-search-image" src="/static/icons/search.svg" mode="aspectFit"></image>
				</view>
				<input class="section-search-input" v-model="buildSearchKeyword" placeholder="搜索配置名称、备注或配件" placeholder-class="section-search-placeholder" @input="resetBuildPage()" />
			</view>

			<scroll-view class="build-scroll" scroll-y="true">
				<view v-if="getFilteredBuildList().length > 0" class="build-list">
					<view v-for="item in getPagedBuildList()" :key="item.id" class="build-card" @click="openBuildDetail(item)">
						<view class="build-card-head">
							<view class="build-card-main">
								<text class="build-title">{{ item.title }}</text>
								<text class="build-meta">{{ getBuildSourceLabel(item.source) }} · {{ formatHistoryTime(item.updatedAt) }}</text>
							</view>
							<view class="build-card-delete" @click.stop="deleteBuild(item.id)">
								<text class="build-card-delete-text">删除</text>
							</view>
						</view>
						<view class="build-score-row">
							<view class="build-score-pill">
								<text class="build-score-label">总价</text>
								<text class="build-score-value">¥{{ item.totalPrice }}</text>
							</view>
							<view class="build-score-pill">
								<text class="build-score-label">性能</text>
								<text class="build-score-value">{{ item.performanceScore }} 分</text>
							</view>
						</view>
						<text class="build-summary">{{ item.summary }}</text>
						<view class="build-part-preview">
							<text v-for="part in item.parts" :key="part.id" class="build-part-chip">{{ getHardwareTypeLabel(part.type) }} · {{ part.model }}</text>
						</view>
						<view class="build-actions">
							<view class="build-action build-action-soft" @click.stop="editBuild(item)">
								<text class="build-action-icon">✎</text>
								<text class="build-action-text">编辑</text>
							</view>
							<view class="build-action build-action-soft" @click.stop="copyBuild(item.content)">
								<text class="build-action-icon">⧉</text>
								<text class="build-action-text">复制</text>
							</view>
							<view :class="item.favorite ? 'build-action build-action-favorite build-action-favorite-active' : 'build-action build-action-favorite'" @click.stop="toggleBuildFavorite(item)">
								<text :class="item.favorite ? 'build-action-icon favorite-icon-active' : 'build-action-icon'">{{ item.favorite ? '★' : '☆' }}</text>
								<text :class="item.favorite ? 'favorite-text favorite-text-active' : 'favorite-text'">{{ item.favorite ? '已收藏' : '收藏' }}</text>
							</view>
						</view>
					</view>
					<view v-if="getBuildPageCount() > 1" class="list-pager">
						<view :class="getBuildPage() <= 1 ? 'pager-button pager-button-disabled' : 'pager-button'" @click="previousBuildPage">
							<text :class="getBuildPage() <= 1 ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">上一页</text>
						</view>
						<text class="pager-count">{{ getBuildPage() }} / {{ getBuildPageCount() }}</text>
						<view :class="getBuildPage() >= getBuildPageCount() ? 'pager-button pager-button-disabled' : 'pager-button'" @click="nextBuildPage">
							<text :class="getBuildPage() >= getBuildPageCount() ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">下一页</text>
						</view>
					</view>
				</view>

				<view v-if="getFilteredBuildList().length == 0" class="empty-state">
					<view class="empty-illustration">
						<view class="empty-grid-row">
							<view class="empty-grid-cell"></view>
							<view class="empty-grid-cell"></view>
						</view>
						<view class="empty-grid-row">
							<view class="empty-grid-cell"></view>
							<view class="empty-grid-cell empty-grid-cell-accent"></view>
						</view>
					</view>
					<text class="empty-title">{{ getBuildEmptyTitle() }}</text>
					<text class="empty-desc">{{ getBuildEmptyDesc() }}</text>
					<view v-if="buildSegment != 2" class="action-stack">
						<view class="primary-action" @click="createBuild">
							<text class="primary-action-text">新建配置</text>
						</view>
						<view class="secondary-action" @click="askAiForBuild">
							<text class="secondary-action-text">让 AI 生成</text>
						</view>
					</view>
				</view>
			</scroll-view>
		</view>

		<view v-if="activeTab == 2" :class="getPageClass('normal-page')">
			<view class="status-space"></view>
			<view class="page-head page-head-row">
				<view class="page-head-main">
					<text class="page-title page-title-inset">发现</text>
				</view>
				<view class="small-primary" @click="publishPost">
					<text class="small-primary-text">发帖</text>
				</view>
			</view>

			<view class="section-search">
				<view class="section-search-icon">
					<image class="section-search-image" src="/static/icons/search.svg" mode="aspectFit"></image>
				</view>
				<input class="section-search-input" v-model="communitySearchKeyword" placeholder="搜索帖子内容、作者或配置" placeholder-class="section-search-placeholder" @input="resetCommunityPage()" />
			</view>

			<scroll-view class="community-scroll" scroll-y="true" refresher-enabled="true" :refresher-triggered="communityRefreshing" refresher-background="#f6f7fb" @refresherrefresh="refreshCommunityPosts">
				<view v-if="getFilteredCommunityPosts().length > 0" class="community-list">
					<view v-for="post in getPagedCommunityPosts()" :key="post.id" class="post-card" @click="openPostDetail(post.id)">
						<view class="post-head">
							<view class="post-avatar">
								<image v-if="post.author.avatarUrl.length > 0" class="post-avatar-image" :src="post.author.avatarUrl" mode="aspectFill"></image>
								<view v-else class="avatar-placeholder">
									<view class="avatar-placeholder-head"></view>
									<view class="avatar-placeholder-body"></view>
								</view>
							</view>
							<view class="post-author-main">
								<text class="post-author-name">{{ post.author.nickname }}</text>
								<text class="post-time">{{ formatHistoryTime(post.createdAt) }}</text>
							</view>
							<view v-if="isMine(post.author.id)" class="post-head-delete" @click.stop="confirmDeletePost(post.id)">
								<text class="post-head-delete-text">删除</text>
							</view>
						</view>
						<text class="post-content">{{ post.content }}</text>
						<view v-if="post.imageUrls.length > 0" class="post-image-grid">
							<image v-for="image in post.imageUrls" :key="image" class="post-image" :src="image" mode="aspectFill" @click.stop="previewPostImages(post.imageUrls, image)"></image>
						</view>
						<view v-if="post.buildConfig != null && post.buildConfig.id > 0" class="post-build-card" @click.stop="openPostBuildDetail(post.buildConfig.id)">
							<view class="chat-build-icon">
								<text class="chat-build-icon-text">DIY</text>
							</view>
							<view class="chat-build-main">
								<text class="chat-build-title">{{ post.buildConfig.title }}</text>
								<text class="chat-build-desc">¥{{ post.buildConfig.totalPrice }} · 性能 {{ post.buildConfig.performanceScore }} 分</text>
							</view>
							<text class="chat-build-arrow">›</text>
						</view>
						<view class="post-actions">
							<view class="post-action" @click.stop="togglePostLike(post.id)">
								<text :class="post.liked ? 'post-action-icon post-action-icon-active' : 'post-action-icon'">♥</text>
								<text class="post-action-text">{{ post.likeCount }}</text>
							</view>
							<view class="post-action" @click.stop="togglePostFavorite(post.id)">
								<text :class="post.favorite ? 'post-action-icon post-favorite-icon-active' : 'post-action-icon'">★</text>
								<text class="post-action-text">{{ post.favoriteCount }}</text>
							</view>
							<view class="post-action">
								<view class="comment-action-icon">
									<view class="comment-action-dot"></view>
									<view class="comment-action-dot"></view>
									<view class="comment-action-dot"></view>
									<view class="comment-action-tail"></view>
								</view>
								<text class="post-action-text">{{ post.commentCount }}</text>
							</view>
						</view>
					</view>
					<view v-if="getCommunityPageCount() > 1" class="list-pager">
						<view :class="communityPage <= 1 ? 'pager-button pager-button-disabled' : 'pager-button'" @click="previousCommunityPage">
							<text :class="communityPage <= 1 ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">上一页</text>
						</view>
						<text class="pager-count">{{ communityPage }} / {{ getCommunityPageCount() }}</text>
						<view :class="communityPage >= getCommunityPageCount() ? 'pager-button pager-button-disabled' : 'pager-button'" @click="nextCommunityPage">
							<text :class="communityPage >= getCommunityPageCount() ? 'pager-button-text pager-button-text-disabled' : 'pager-button-text'">下一页</text>
						</view>
					</view>
				</view>

				<view v-if="getFilteredCommunityPosts().length == 0" class="empty-state empty-state-soft">
					<view class="empty-illustration empty-forum">
						<view class="forum-bubble"></view>
						<view class="forum-bubble forum-bubble-small"></view>
					</view>
					<text class="empty-title">{{ getCommunityEmptyTitle() }}</text>
					<text class="empty-desc">{{ getCommunityEmptyDesc() }}</text>
				</view>
			</scroll-view>
		</view>

		<view v-if="activeTab == 3" :class="getPageClass('normal-page')">
			<view class="status-space"></view>
			<view class="page-head">
				<view class="page-head-main">
					<text class="page-title page-title-inset">热点</text>
				</view>
			</view>

			<view class="empty-state">
				<view class="empty-illustration empty-news">
					<view class="news-line news-line-long"></view>
					<view class="news-line"></view>
					<view class="news-line news-line-short"></view>
				</view>
				<text class="empty-title">暂无新闻热点</text>
				<text class="empty-desc">有新的硬件资讯时，会在这里集中展示。</text>
			</view>
		</view>

		<view v-if="activeTab == 4" :class="getPageClass('normal-page')">
			<view class="status-space"></view>
			<view class="profile-card" @click="handleProfileCardClick">
				<view class="profile-avatar">
					<image v-if="currentAvatarUrl.length > 0" class="profile-avatar-image" :src="currentAvatarUrl" mode="aspectFill"></image>
					<view v-else class="avatar-placeholder avatar-placeholder-large">
						<view class="avatar-placeholder-head"></view>
						<view class="avatar-placeholder-body"></view>
					</view>
				</view>
				<view class="profile-copy">
					<text class="profile-title">{{ token.length > 0 ? currentNickname : '登录 / 注册' }}</text>
					<text class="profile-desc">{{ token.length > 0 ? currentBio : '登录后同步配置、收藏和社区内容。' }}</text>
					<text v-if="token.length > 0" class="profile-email">{{ currentEmail }}</text>
				</view>
			</view>

			<view class="zero-stats">
				<view class="zero-stat">
					<text class="zero-number">0</text>
					<text class="zero-label">配置</text>
				</view>
				<view class="zero-stat">
					<text class="zero-number">0</text>
					<text class="zero-label">收藏</text>
				</view>
				<view class="zero-stat zero-stat-last">
					<text class="zero-number">0</text>
					<text class="zero-label">帖子</text>
				</view>
			</view>

			<view class="menu-list">
				<view class="menu-item" @click="openSettings">
					<text class="menu-text">设置</text>
					<text class="menu-arrow">›</text>
				</view>
				<view v-if="token.length > 0" :class="logoutPressed ? 'menu-item menu-item-pressed' : 'menu-item'" @click="logout">
					<text class="menu-text menu-text-danger">退出登录</text>
					<text class="menu-arrow">›</text>
				</view>
			</view>
		</view>

		<view v-if="!historyOpen" class="tabbar">
			<view :class="activeTab == 0 ? 'tab-item tab-item-active' : 'tab-item'" @click="selectTab(0)">
				<view class="tab-glyph ai-tab-glyph">
					<text :class="activeTab == 0 ? 'ai-tab-text ai-tab-text-active' : 'ai-tab-text'">AI</text>
				</view>
				<text :class="activeTab == 0 ? 'tab-label tab-label-active' : 'tab-label'">对话</text>
			</view>
			<view :class="activeTab == 1 ? 'tab-item tab-item-active' : 'tab-item'" @click="selectTab(1)">
				<view class="tab-glyph grid-glyph">
					<view class="grid-icon-row">
						<view :class="activeTab == 1 ? 'grid-icon-cell grid-icon-cell-active' : 'grid-icon-cell'"></view>
						<view :class="activeTab == 1 ? 'grid-icon-cell grid-icon-cell-active' : 'grid-icon-cell'"></view>
					</view>
					<view class="grid-icon-row">
						<view :class="activeTab == 1 ? 'grid-icon-cell grid-icon-cell-active' : 'grid-icon-cell'"></view>
						<view :class="activeTab == 1 ? 'grid-icon-cell grid-icon-cell-active' : 'grid-icon-cell'"></view>
					</view>
				</view>
				<text :class="activeTab == 1 ? 'tab-label tab-label-active' : 'tab-label'">配置</text>
			</view>
			<view :class="activeTab == 2 ? 'tab-item tab-item-active' : 'tab-item'" @click="selectTab(2)">
				<view class="tab-glyph discover-glyph">
					<view :class="activeTab == 2 ? 'discover-circle discover-circle-active' : 'discover-circle'"></view>
					<view :class="activeTab == 2 ? 'discover-needle discover-needle-active' : 'discover-needle'"></view>
				</view>
				<text :class="activeTab == 2 ? 'tab-label tab-label-active' : 'tab-label'">发现</text>
			</view>
			<view :class="activeTab == 3 ? 'tab-item tab-item-active' : 'tab-item'" @click="selectTab(3)">
				<view class="tab-glyph news-glyph">
					<view :class="activeTab == 3 ? 'news-icon-paper news-icon-paper-active' : 'news-icon-paper'">
						<view :class="activeTab == 3 ? 'news-icon-line news-icon-line-long news-icon-line-active' : 'news-icon-line news-icon-line-long'"></view>
						<view :class="activeTab == 3 ? 'news-icon-line news-icon-line-active' : 'news-icon-line'"></view>
					</view>
				</view>
				<text :class="activeTab == 3 ? 'tab-label tab-label-active' : 'tab-label'">热点</text>
			</view>
			<view :class="activeTab == 4 ? 'tab-item tab-item-active' : 'tab-item'" @click="selectTab(4)">
				<view class="tab-glyph mine-glyph">
					<view :class="activeTab == 4 ? 'mine-icon-head mine-icon-head-active' : 'mine-icon-head'"></view>
					<view :class="activeTab == 4 ? 'mine-icon-body mine-icon-body-active' : 'mine-icon-body'"></view>
				</view>
				<text :class="activeTab == 4 ? 'tab-label tab-label-active' : 'tab-label'">我的</text>
			</view>
		</view>

		<view v-if="historyOpen" class="history-mask" @click="closeHistory"></view>
		<view :class="historyOpen ? 'history-panel history-panel-open' : 'history-panel'">
			<view class="history-top">
				<text class="history-title">对话记录</text>
				<view class="history-close" @click="closeHistory">
					<view class="close-line"></view>
					<view class="close-line close-line-two"></view>
				</view>
			</view>
			<scroll-view class="history-list" scroll-y="true" :refresher-enabled="token.length > 0" :refresher-triggered="historyRefreshing" @refresherrefresh="refreshConversations">
				<view v-if="conversationList.length > 0">
					<view v-for="item in conversationList" :key="item.id" class="history-row">
						<view :class="getHistoryItemClass(item.id)" @click="openConversation(item.id)" @longpress="showConversationDeleteAction(item.id)">
							<text :class="currentConversationId == item.id ? 'history-item-title history-item-title-active' : 'history-item-title'">{{ item.title }}</text>
							<text class="history-item-time">{{ formatHistoryTime(item.updatedAt) }}</text>
						</view>
						<view :class="deleteActionConversationId == item.id ? 'history-delete-action history-delete-action-show' : 'history-delete-action'" @click.stop="openDeleteDialog(item.id, item.title)">
							<view class="trash-handle"></view>
							<view class="trash-lid"></view>
							<view class="trash-can">
								<view class="trash-line"></view>
								<view class="trash-line"></view>
								<view class="trash-line"></view>
							</view>
						</view>
					</view>
				</view>
				<view v-if="conversationList.length == 0" class="history-empty">
					<view class="empty-dot">
						<view class="empty-dot-inner"></view>
					</view>
					<text class="history-empty-title">暂无对话记录</text>
					<text class="history-empty-desc">{{ token.length > 0 ? '开始第一次对话后，会在这里继续查看和切换。' : '登录后可以同步查看历史对话。' }}</text>
				</view>
			</scroll-view>
		</view>

		<view v-if="deleteDialogVisible" class="delete-dialog-mask" @click="cancelDeleteDialog">
			<view class="delete-dialog" @click.stop>
				<text class="delete-dialog-title">删除这条对话？</text>
				<text class="delete-dialog-desc">“{{ pendingDeleteTitle }}” 删除后无法恢复。</text>
				<view class="delete-dialog-actions">
					<view class="delete-dialog-cancel" @click="cancelDeleteDialog">
						<text class="delete-dialog-cancel-text">取消</text>
					</view>
					<view class="delete-dialog-confirm" @click="confirmDeleteDialog">
						<text class="delete-dialog-confirm-text">{{ deleteSubmitting ? '删除中...' : '删除' }}</text>
					</view>
				</view>
			</view>
		</view>

		<view v-if="buildFormVisible" class="build-form-mask" @click="closeBuildForm">
			<view class="build-form" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title">{{ buildEditingId > 0 ? '编辑配置' : '新建配置' }}</text>
					<view class="build-form-close" @click="closeBuildForm">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<view class="build-live-panel">
					<view class="build-live-item">
						<text class="build-live-label">实时总价</text>
						<text class="build-live-value">¥{{ getCurrentBuildTotalPrice() }}</text>
					</view>
					<view class="build-live-item">
						<text class="build-live-label">性能评分</text>
						<text class="build-live-value">{{ getCurrentBuildPerformanceScore() }} 分</text>
					</view>
				</view>
				<scroll-view class="build-form-body" scroll-y="true">
					<input class="build-form-input" v-model="buildFormTitle" placeholder="配置名称" placeholder-class="input-placeholder" />
					<textarea class="build-form-textarea build-note-textarea" v-model="buildFormNote" placeholder="备注，例如偏好白色、小机箱、安静一点" placeholder-class="input-placeholder" />
					<view class="part-selector-list">
						<view v-for="partType in hardwareTypeOptions" :key="partType.key" :class="getPartSelectorClass(partType.key)" @click="openPartPicker(partType.key)">
							<view class="part-selector-main">
								<text class="part-selector-label">{{ partType.label }}</text>
								<text :class="hasSelectedPart(partType.key) ? 'part-selector-value' : 'part-selector-placeholder'">{{ getSelectedPartName(partType.key) }}</text>
							</view>
							<text class="part-selector-price">{{ getSelectedPartPrice(partType.key) }}</text>
						</view>
					</view>
					<view class="compat-list">
						<text v-for="message in getCurrentCompatibilityMessages()" :key="message" class="compat-message">{{ message }}</text>
					</view>
				</scroll-view>
				<view class="build-form-submit" @click="saveBuild">
					<text class="build-form-submit-text">{{ buildSaving ? '保存中...' : '保存配置' }}</text>
				</view>
			</view>
		</view>

		<view v-if="partPickerVisible" class="build-form-mask" @click="closePartPicker">
			<view class="part-picker" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title">选择{{ getHardwareTypeLabel(activePickerType) }}</text>
					<view class="build-form-close" @click="closePartPicker">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<view class="part-search-row">
					<input class="part-search-input" v-model="hardwareKeyword" confirm-type="search" placeholder="搜索品牌或型号" placeholder-class="input-placeholder" @confirm="loadHardwareOptions" />
					<view class="part-search-button" @click="loadHardwareOptions">
						<text class="part-search-button-text">搜索</text>
					</view>
				</view>
				<scroll-view class="part-option-scroll" scroll-y="true">
					<view v-for="part in hardwareOptions" :key="part.id" class="part-option" @click="selectHardwarePart(part)">
						<view class="part-option-main">
							<text class="part-option-title">{{ part.brand }} {{ part.model }}</text>
							<text class="part-option-spec">{{ getPartSpecText(part) }}</text>
						</view>
						<view class="part-option-side">
							<text class="part-option-price">¥{{ part.price }}</text>
							<text class="part-option-score">{{ part.performanceScore }} 分</text>
						</view>
					</view>
					<view v-if="hardwareOptions.length == 0" class="part-empty">
						<text class="part-empty-text">暂无可选硬件</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<view v-if="buildDetailVisible" class="build-form-mask build-detail-mask" @click="closeBuildDetail">
			<view class="build-detail" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title">{{ selectedBuildDetail.title }}</text>
					<view class="build-form-close" @click="closeBuildDetail">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<view class="build-detail-metrics">
					<view class="build-detail-metric">
						<text class="build-live-label">总价</text>
						<text class="build-live-value">¥{{ selectedBuildDetail.totalPrice }}</text>
					</view>
					<view class="build-detail-metric">
						<text class="build-live-label">性能</text>
						<text class="build-live-value">{{ selectedBuildDetail.performanceScore }} 分</text>
					</view>
				</view>
				<scroll-view class="build-detail-scroll" scroll-y="true">
					<view v-for="part in selectedBuildDetail.parts" :key="part.id" class="detail-part-row">
						<text class="detail-part-title">{{ getHardwareTypeLabel(part.type) }} · {{ part.brand }} {{ part.model }}</text>
						<text class="detail-part-meta">¥{{ part.price }} · 性能 {{ part.performanceScore }} · {{ getPartSpecText(part) }}</text>
					</view>
					<view class="compat-list compat-list-detail">
						<text v-for="message in selectedBuildDetail.compatibilityMessages" :key="message" class="compat-message">{{ message }}</text>
					</view>
					<text v-if="selectedBuildDetail.note.length > 0" class="detail-note">备注：{{ selectedBuildDetail.note }}</text>
				</scroll-view>
				<view class="build-detail-actions">
					<view class="build-action build-action-soft" @click="editBuild(selectedBuildDetail)">
						<text class="build-action-icon">✎</text>
						<text class="build-action-text">编辑</text>
					</view>
					<view class="build-action build-action-soft" @click="toggleBuildFavorite(selectedBuildDetail)">
						<text :class="selectedBuildDetail.favorite ? 'favorite-icon favorite-icon-active' : 'favorite-icon'">{{ selectedBuildDetail.favorite ? '★' : '☆' }}</text>
						<text class="build-action-text">{{ selectedBuildDetail.favorite ? '已收藏' : '收藏' }}</text>
					</view>
				</view>
			</view>
		</view>

		<view v-if="chatBuildPickerVisible" class="build-form-mask compact-picker-mask" @click="closeChatBuildPicker">
			<view class="part-picker compact-build-picker" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title compact-picker-title">选择关联配置</text>
					<view class="build-form-close compact-picker-close" @click="closeChatBuildPicker">
						<view class="close-line compact-picker-close-line"></view>
						<view class="close-line close-line-two compact-picker-close-line"></view>
					</view>
				</view>
				<scroll-view class="part-option-scroll compact-picker-scroll" scroll-y="true">
					<view v-for="item in chatAttachBuildList" :key="item.id" class="part-option" @click="toggleBuildForChat(item)">
						<view class="part-option-main">
							<text class="part-option-title">{{ item.title }}</text>
							<text class="part-option-spec">¥{{ item.totalPrice }} · 性能 {{ item.performanceScore }} 分</text>
						</view>
						<view class="part-option-side">
							<view :class="attachedBuildId == item.id ? 'plus-circle-button plus-circle-button-selected' : 'plus-circle-button'">
								<text :class="attachedBuildId == item.id ? 'plus-circle-text plus-circle-text-selected' : 'plus-circle-text'">{{ attachedBuildId == item.id ? '-' : '+' }}</text>
							</view>
						</view>
					</view>
					<view v-if="chatAttachBuildList.length == 0" class="part-empty">
						<text class="part-empty-text">暂无可关联配置</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<view v-if="postFormVisible" class="build-form-mask" @click="closePostForm">
			<view class="post-form" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title">发布帖子</text>
					<view class="build-form-close" @click="closePostForm">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<textarea class="post-form-textarea" v-model="postContent" placeholder="分享装机想法、预算困惑或成品照片" placeholder-class="input-placeholder" />
				<view v-if="postBuildId > 0" class="post-linked-build" @click="openPostBuildDetail(postBuildId)">
					<view class="chat-build-icon">
						<text class="chat-build-icon-text">DIY</text>
					</view>
					<view class="chat-build-main">
						<text class="chat-build-title">{{ postBuildTitle }}</text>
						<text class="chat-build-desc">已关联配置，点击可查看详情</text>
					</view>
					<view class="composer-attachment-close" @click.stop="clearPostBuild">
						<view class="close-line close-line-small"></view>
						<view class="close-line close-line-two close-line-small"></view>
					</view>
				</view>
				<view v-if="postImageUrls.length > 0" class="form-image-grid">
					<view v-for="(image, index) in postImageUrls" :key="image" :class="getPostImageWrapClass(index)" :style="getPostImageDragStyle(index)" @touchstart.stop="startPostImageTouch(index, $event)" @touchmove.stop="movePostImageDrag($event)" @touchend.stop="endPostImageDrag">
						<image class="form-image" :src="image" mode="aspectFill" @click="previewPostDraftImage(image)"></image>
						<view v-if="false" class="form-image-order">
							<text :class="index == 0 ? 'form-image-order-text form-image-order-disabled' : 'form-image-order-text'" @click.stop="movePostImageLeft(index)">‹</text>
							<text :class="index == postImageUrls.length - 1 ? 'form-image-order-text form-image-order-disabled' : 'form-image-order-text'" @click.stop="movePostImageRight(index)">›</text>
						</view>
						<view class="form-image-remove" @click.stop="removePostImage(image)">
							<view class="close-line close-line-small"></view>
							<view class="close-line close-line-two close-line-small"></view>
						</view>
					</view>
				</view>
				<view class="post-form-tools">
					<view class="tool-pill" @click="pickPostImages">
						<view class="tool-image-icon">
							<view class="tool-image-sun"></view>
							<view class="tool-image-hill"></view>
						</view>
						<text class="tool-pill-text">图片 {{ postImageUrls.length }}/9</text>
					</view>
					<view class="tool-pill" @click="openPostBuildPicker">
						<view class="tool-build-icon">
							<view class="tool-build-dot"></view>
							<view class="tool-build-dot"></view>
						</view>
						<text class="tool-pill-text">配置</text>
					</view>
				</view>
				<view class="build-form-submit" @click="submitPost">
					<text class="build-form-submit-text">{{ postSubmitting ? '发布中...' : '发布' }}</text>
				</view>
			</view>
		</view>

		<view v-if="postBuildPickerVisible" class="build-form-mask compact-picker-mask" @click="closePostBuildPicker">
			<view class="part-picker compact-build-picker" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title compact-picker-title">选择关联配置</text>
					<view class="build-form-close compact-picker-close" @click="closePostBuildPicker">
						<view class="close-line compact-picker-close-line"></view>
						<view class="close-line close-line-two compact-picker-close-line"></view>
					</view>
				</view>
				<scroll-view class="part-option-scroll compact-picker-scroll" scroll-y="true">
					<view v-for="item in postBuildList" :key="item.id" class="part-option" @click="toggleBuildForPost(item)">
						<view class="part-option-main">
							<text class="part-option-title">{{ item.title }}</text>
							<text class="part-option-spec">¥{{ item.totalPrice }} · 性能 {{ item.performanceScore }} 分</text>
						</view>
						<view class="part-option-side">
							<view :class="postBuildId == item.id ? 'plus-circle-button plus-circle-button-selected' : 'plus-circle-button'">
								<text :class="postBuildId == item.id ? 'plus-circle-text plus-circle-text-selected' : 'plus-circle-text'">{{ postBuildId == item.id ? '-' : '+' }}</text>
							</view>
						</view>
					</view>
					<view v-if="postBuildList.length == 0" class="part-empty">
						<text class="part-empty-text">暂无可关联配置</text>
					</view>
				</scroll-view>
			</view>
		</view>

		<view v-if="false" class="build-form-mask" @click="closePostDetail">
			<view class="post-detail" @click.stop>
				<view class="build-form-head">
					<text class="build-form-title">帖子详情</text>
					<view class="build-form-close" @click="closePostDetail">
						<view class="close-line"></view>
						<view class="close-line close-line-two"></view>
					</view>
				</view>
				<scroll-view class="post-detail-scroll" scroll-y="true">
					<view class="post-head">
						<view class="post-avatar">
							<image v-if="selectedPost.author.avatarUrl.length > 0" class="post-avatar-image" :src="selectedPost.author.avatarUrl" mode="aspectFill"></image>
							<view v-else class="avatar-placeholder">
								<view class="avatar-placeholder-head"></view>
								<view class="avatar-placeholder-body"></view>
							</view>
						</view>
						<view class="post-author-main">
							<text class="post-author-name">{{ selectedPost.author.nickname }}</text>
							<text class="post-time">{{ formatHistoryTime(selectedPost.createdAt) }}</text>
						</view>
					</view>
					<text class="post-content post-detail-content">{{ selectedPost.content }}</text>
					<view v-if="selectedPost.imageUrls.length > 0" class="post-image-grid">
						<image v-for="image in selectedPost.imageUrls" :key="image" class="post-image" :src="image" mode="aspectFill" @click="previewImage(image)"></image>
					</view>
					<view v-if="selectedPost.buildConfig != null && selectedPost.buildConfig.id > 0" class="post-build-card" @click="openPostBuildDetail(selectedPost.buildConfig.id)">
						<view class="chat-build-icon">
							<text class="chat-build-icon-text">DIY</text>
						</view>
						<view class="chat-build-main">
							<text class="chat-build-title">{{ selectedPost.buildConfig.title }}</text>
							<text class="chat-build-desc">¥{{ selectedPost.buildConfig.totalPrice }} · 性能 {{ selectedPost.buildConfig.performanceScore }} 分</text>
						</view>
						<text class="chat-build-arrow">›</text>
					</view>
					<view class="post-actions post-detail-actions">
						<view class="post-action" @click="togglePostLike(selectedPost.id)">
							<text :class="selectedPost.liked ? 'post-action-icon post-action-icon-active' : 'post-action-icon'">♥</text>
							<text class="post-action-text">{{ selectedPost.likeCount }}</text>
						</view>
						<view class="post-action" @click="togglePostFavorite(selectedPost.id)">
							<text :class="selectedPost.favorite ? 'post-action-icon post-action-icon-active' : 'post-action-icon'">★</text>
							<text class="post-action-text">{{ selectedPost.favoriteCount }}</text>
						</view>
					</view>
					<view class="comment-list">
						<text class="comment-title">评论 {{ selectedPost.commentCount }}</text>
						<view v-for="comment in selectedPost.comments" :key="comment.id" class="comment-card">
							<view class="post-head comment-head">
								<view class="comment-avatar">
									<image v-if="comment.author.avatarUrl.length > 0" class="comment-avatar-image" :src="comment.author.avatarUrl" mode="aspectFill"></image>
									<view v-else class="avatar-placeholder avatar-placeholder-small">
										<view class="avatar-placeholder-head"></view>
										<view class="avatar-placeholder-body"></view>
									</view>
								</view>
								<view class="post-author-main">
									<text class="post-author-name">{{ comment.author.nickname }}</text>
									<text class="post-time">{{ formatHistoryTime(comment.createdAt) }}</text>
								</view>
								<view class="post-action comment-like" @click="toggleCommentLike(comment.id)">
									<text :class="comment.liked ? 'post-action-icon post-action-icon-active' : 'post-action-icon'">♥</text>
									<text class="post-action-text">{{ comment.likeCount }}</text>
								</view>
							</view>
							<text class="comment-content">{{ comment.content }}</text>
							<view v-if="comment.imageUrls.length > 0" class="comment-image-grid">
								<image v-for="image in comment.imageUrls" :key="image" class="comment-image" :src="image" mode="aspectFill" @click="previewImage(image)"></image>
							</view>
						</view>
					</view>
				</scroll-view>
				<view v-if="commentImageUrls.length > 0" class="comment-draft-images">
					<view v-for="image in commentImageUrls" :key="image" class="comment-draft-image-wrap">
						<image class="comment-draft-image" :src="image" mode="aspectFill"></image>
						<view class="form-image-remove comment-image-remove" @click="removeCommentImage(image)">
							<view class="close-line close-line-small"></view>
							<view class="close-line close-line-two close-line-small"></view>
						</view>
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
					<view class="comment-send" @click="submitComment">
						<text class="comment-send-text">{{ commentSubmitting ? '...' : '发送' }}</text>
					</view>
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

	type ChatMessage = {
		role : string
		content : string
		buildConfigId : number
		buildConfigTitle : string
	}

	type ConversationSummary = {
		id : number
		title : string
		updatedAt : string
	}

	type UserProfilePayload = {
		userId : number
		username : string
		email : string
		nickname : string
		avatarKey : string
		avatarUrl : string
		bio : string
		role : string
	}

	type HardwareTypeOption = {
		key : string
		label : string
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

	type AuthorItem = {
		id : number
		username : string
		nickname : string
		avatarUrl : string
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
	const activeTab = ref(0)
	const historyOpen = ref(false)
	const touchStartX = ref(0)
	const touchStartY = ref(0)
	const draft = ref('')
	const buildSegment = ref(0)
	const buildList = ref([] as BuildConfigItem[])
	const buildSearchKeyword = ref('')
	const buildCustomPage = ref(1)
	const buildAiPage = ref(1)
	const buildFavoritePage = ref(1)
	const buildLoading = ref(false)
	const buildFormVisible = ref(false)
	const buildSaving = ref(false)
	const buildEditingId = ref(0)
	const buildFormTitle = ref('')
	const buildFormNote = ref('')
	const selectedHardwareParts = ref([] as HardwarePartItem[])
	const partPickerVisible = ref(false)
	const activePickerType = ref('CPU')
	const hardwareKeyword = ref('')
	const hardwareOptions = ref([] as HardwarePartItem[])
	const buildDetailVisible = ref(false)
	const chatBuildPickerVisible = ref(false)
	const chatAttachBuildList = ref([] as BuildConfigItem[])
	const attachedBuildId = ref(0)
	const attachedBuildTitle = ref('')
	const selectedBuildDetail = ref({
		id: 0,
		title: '',
		note: '',
		totalPrice: 0,
		performanceScore: 0,
		compatibilityStatus: 'WARN',
		compatibilityMessages: [] as string[],
		parts: [] as HardwarePartItem[],
		summary: '',
		content: '',
		source: 'MANUAL',
		favorite: false,
		updatedAt: ''
	} as BuildConfigItem)
	const pageMotionClass = ref('')
	const chatMessages = ref([] as ChatMessage[])
	const conversationList = ref([] as ConversationSummary[])
	const currentConversationId = ref(0)
	const chatLoading = ref(false)
	const historyRefreshing = ref(false)
	const historyPressLocked = ref(false)
	const deleteActionConversationId = ref(0)
	const deleteDialogVisible = ref(false)
	const pendingDeleteConversationId = ref(0)
	const pendingDeleteTitle = ref('')
	const deleteSubmitting = ref(false)
	const logoutPressed = ref(false)
	const newConversationPressed = ref(false)
	const token = ref('')
	const currentUserId = ref(0)
	const currentUsername = ref('')
	const currentEmail = ref('')
	const currentNickname = ref('')
	const currentAvatarUrl = ref('')
	const currentBio = ref('')
	const communityPosts = ref([] as CommunityPostItem[])
	const communitySearchKeyword = ref('')
	const communityPage = ref(1)
	const communityLoading = ref(false)
	const communityRefreshing = ref(false)
	const postFormVisible = ref(false)
	const postSubmitting = ref(false)
	const postContent = ref('')
	const postImageUrls = ref([] as string[])
	const postImageSortActive = ref(false)
	const draggingPostImageIndex = ref(-1)
	const postImageDragStartX = ref(0)
	const postImageDragStartY = ref(0)
	const postImageDragOffsetX = ref(0)
	const postImageDragOffsetY = ref(0)
	const postImageLongPressTimer = ref(0)
	const postBuildId = ref(0)
	const postBuildTitle = ref('')
	const postBuildPickerVisible = ref(false)
	const postBuildList = ref([] as BuildConfigItem[])
	const postDetailVisible = ref(false)
	const selectedPost = ref(createEmptyPost())
	const commentText = ref('')
	const commentImageUrls = ref([] as string[])
	const commentSubmitting = ref(false)
	const imagePreviewVisible = ref(false)
	const imagePreviewUrls = ref([] as string[])
	const imagePreviewIndex = ref(0)
	const imagePreviewCurrent = ref('')
	const imagePreviewScale = ref(1)
	const uploadProgressVisible = ref(false)
	const uploadProgressTitle = ref('')
	const uploadProgressPercent = ref(0)

	const promptChips = [
		'预算 6000 元，主玩 2K 游戏',
		'白色小机箱，安静一点',
		'剪辑、建模和游戏都要兼顾',
		'已有显卡，帮我补齐其他配件'
	]

	const hardwareTypeOptions = [
		{ key: 'CPU', label: 'CPU' },
		{ key: 'MOTHERBOARD', label: '主板' },
		{ key: 'GPU', label: '显卡' },
		{ key: 'MEMORY', label: '内存' },
		{ key: 'STORAGE', label: '硬盘' },
		{ key: 'PSU', label: '电源' },
		{ key: 'CASE', label: '机箱' },
		{ key: 'COOLER', label: '散热' }
	] as HardwareTypeOption[]

	let historyRefreshTimer : number = 0

	loadAuth()
	if (token.value.length > 0) {
		loadConversations()
		startHistoryRefreshTimer()
	}
	onShow(() => {
		loadAuth()
		if (token.value.length > 0) {
			if (currentUserId.value == 0) {
				loadProfile()
			}
			loadConversations()
			startHistoryRefreshTimer()
		} else {
			stopHistoryRefreshTimer()
		}
		if (activeTab.value == 4) {
			loadProfile()
		}
		if (activeTab.value == 1) {
			loadBuildConfigs()
		}
		if (activeTab.value == 2) {
			loadCommunityPosts()
		}
	})
	onHide(() => {
		stopHistoryRefreshTimer()
	})

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

	function createEmptyAuthor() : AuthorItem {
		return {
			id: 0,
			username: '',
			nickname: '用户',
			avatarUrl: ''
		} as AuthorItem
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

	function normalizeSearchKeyword(value : string) : string {
		return value.trim().toLowerCase()
	}

	function textMatchesKeyword(value : string, keyword : string) : boolean {
		return value.toLowerCase().indexOf(keyword) >= 0
	}

	function resetBuildPage() {
		setBuildPage(1)
	}

	function getBuildPage() : number {
		let page = buildCustomPage.value
		if (buildSegment.value == 1) {
			page = buildAiPage.value
		}
		if (buildSegment.value == 2) {
			page = buildFavoritePage.value
		}
		const pageCount = getBuildPageCount()
		if (page > pageCount) {
			return pageCount
		}
		if (page < 1) {
			return 1
		}
		return page
	}

	function setBuildPage(page : number) {
		const pageCount = getBuildPageCount()
		let nextPage = page
		if (nextPage < 1) {
			nextPage = 1
		}
		if (nextPage > pageCount) {
			nextPage = pageCount
		}
		if (buildSegment.value == 1) {
			buildAiPage.value = nextPage
			return
		}
		if (buildSegment.value == 2) {
			buildFavoritePage.value = nextPage
			return
		}
		buildCustomPage.value = nextPage
	}

	function clampBuildPage() {
		setBuildPage(getBuildPage())
	}

	function previousBuildPage() {
		setBuildPage(getBuildPage() - 1)
	}

	function nextBuildPage() {
		setBuildPage(getBuildPage() + 1)
	}

	function getBuildPageCount() : number {
		const total = getFilteredBuildList().length
		if (total == 0) {
			return 1
		}
		return Math.ceil(total / 5)
	}

	function getPagedBuildList() : BuildConfigItem[] {
		const list = getFilteredBuildList()
		const page = getBuildPage()
		const start = (page - 1) * 5
		return list.slice(start, start + 5)
	}

	function getFilteredBuildList() : BuildConfigItem[] {
		const keyword = normalizeSearchKeyword(buildSearchKeyword.value)
		if (keyword.length == 0) {
			return buildList.value
		}
		return buildList.value.filter((item : BuildConfigItem) : boolean => {
			if (textMatchesKeyword(item.title, keyword) || textMatchesKeyword(item.note, keyword) || textMatchesKeyword(item.summary, keyword) || textMatchesKeyword(item.content, keyword) || textMatchesKeyword(getBuildSourceLabel(item.source), keyword)) {
				return true
			}
			for (let i = 0; i < item.parts.length; i++) {
				const part = item.parts[i]
				if (textMatchesKeyword(getHardwareTypeLabel(part.type), keyword) || textMatchesKeyword(part.brand, keyword) || textMatchesKeyword(part.model, keyword) || textMatchesKeyword(part.socketType, keyword) || textMatchesKeyword(part.memoryType, keyword) || textMatchesKeyword(part.formFactor, keyword) || textMatchesKeyword(part.specs, keyword)) {
					return true
				}
			}
			return false
		})
	}

	function resetCommunityPage() {
		communityPage.value = 1
	}

	function clampCommunityPage() {
		const pageCount = getCommunityPageCount()
		if (communityPage.value > pageCount) {
			communityPage.value = pageCount
		}
		if (communityPage.value < 1) {
			communityPage.value = 1
		}
	}

	function previousCommunityPage() {
		if (communityPage.value <= 1) {
			return
		}
		communityPage.value = communityPage.value - 1
	}

	function nextCommunityPage() {
		const pageCount = getCommunityPageCount()
		if (communityPage.value >= pageCount) {
			return
		}
		communityPage.value = communityPage.value + 1
	}

	function getCommunityPageCount() : number {
		const total = getFilteredCommunityPosts().length
		if (total == 0) {
			return 1
		}
		return Math.ceil(total / 5)
	}

	function getPagedCommunityPosts() : CommunityPostItem[] {
		const list = getFilteredCommunityPosts()
		clampCommunityPage()
		const start = (communityPage.value - 1) * 5
		return list.slice(start, start + 5)
	}

	function getFilteredCommunityPosts() : CommunityPostItem[] {
		const keyword = normalizeSearchKeyword(communitySearchKeyword.value)
		if (keyword.length == 0) {
			return communityPosts.value
		}
		return communityPosts.value.filter((post : CommunityPostItem) : boolean => {
			if (textMatchesKeyword(post.content, keyword) || textMatchesKeyword(post.author.nickname, keyword) || textMatchesKeyword(post.author.username, keyword)) {
				return true
			}
			if (post.buildConfig != null && post.buildConfig.id > 0) {
				if (textMatchesKeyword(post.buildConfig.title, keyword) || textMatchesKeyword(post.buildConfig.note, keyword) || textMatchesKeyword(post.buildConfig.summary, keyword) || textMatchesKeyword(post.buildConfig.content, keyword)) {
					return true
				}
			}
			return false
		})
	}

	function getBuildEmptyTitle() : string {
		if (normalizeSearchKeyword(buildSearchKeyword.value).length > 0) {
			return '没有找到配置'
		}
		if (buildSegment.value == 1) {
			return '还没有 AI 生成记录'
		}
		if (buildSegment.value == 2) {
			return '还没有收藏配置'
		}
		return '还没有配置'
	}

	function getBuildEmptyDesc() : string {
		if (normalizeSearchKeyword(buildSearchKeyword.value).length > 0) {
			return '换个关键词试试，配置名称、备注和配件型号都可以搜。'
		}
		if (buildSegment.value == 1) {
			return '从对话页描述你的预算和用途，生成后的方案会收纳在这里。'
		}
		if (buildSegment.value == 2) {
			return '看到喜欢的配置后可以收藏，方便之后对比和调整。'
		}
		return '你可以从零开始选择配件，也可以先让 AI 生成一份方案再调整。'
	}

	function getCommunityEmptyTitle() : string {
		if (normalizeSearchKeyword(communitySearchKeyword.value).length > 0) {
			return '没有找到帖子'
		}
		return '还没有社区内容'
	}

	function getCommunityEmptyDesc() : string {
		if (normalizeSearchKeyword(communitySearchKeyword.value).length > 0) {
			return '换个关键词试试，帖子内容、作者和关联配置都可以搜。'
		}
		return '发布第一条帖子后，这里会展示你的动态和其他用户的装机讨论。'
	}

	function getPageClass(baseClass : string) : string {
		if (pageMotionClass.value.length == 0) {
			return 'page ' + baseClass
		}
		return 'page ' + baseClass + ' ' + pageMotionClass.value
	}

	function selectTab(index : number) {
		if (index == activeTab.value) {
			historyOpen.value = false
			return
		}
		if (index > activeTab.value) {
			pageMotionClass.value = 'page-enter-right'
		} else {
			pageMotionClass.value = 'page-enter-left'
		}
		activeTab.value = index
		historyOpen.value = false
		if (index == 4) {
			loadAuth()
			loadProfile()
		}
		if (index == 1) {
			loadBuildConfigs()
		}
		if (index == 2) {
			loadCommunityPosts()
		}
		setTimeout(() => {
			pageMotionClass.value = ''
		}, 260)
	}

	function selectBuildSegment(index : number) {
		buildSegment.value = index
		loadBuildConfigs()
	}

	function toggleHistory() {
		historyOpen.value = !historyOpen.value
		if (historyOpen.value) {
			loadConversations()
		}
	}

	function closeHistory() {
		historyOpen.value = false
		deleteActionConversationId.value = 0
		deleteDialogVisible.value = false
	}

	function choosePrompt(text : string) {
		draft.value = text
	}

	function submitMessage() {
		let text = draft.value.trim()
		if (text.length == 0 && attachedBuildId.value > 0) {
			text = '帮我看看这套配置怎么样？'
		}
		if (text.length == 0) {
			return
		}
		if (token.value.length == 0) {
			uni.showToast({
				title: '请先登录后再使用 AI 对话',
				icon: 'none'
			})
			openAuthPage()
			return
		}
		if (chatLoading.value) {
			return
		}
		const sentBuildId = attachedBuildId.value
		const sentBuildTitle = attachedBuildTitle.value
		chatMessages.value.push({
			role: 'user',
			content: text,
			buildConfigId: sentBuildId,
			buildConfigTitle: sentBuildTitle
		})
		draft.value = ''
		clearAttachedBuild()
		chatLoading.value = true
		uni.request({
			url: apiBaseUrl + '/api/ai/chat',
			method: 'POST',
			header: {
				'Content-Type': 'application/json',
				'Authorization': 'Bearer ' + token.value
			},
			data: {
				conversationId: currentConversationId.value > 0 ? currentConversationId.value : null,
				buildConfigId: sentBuildId > 0 ? sentBuildId : null,
				message: text
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const data = body.data as any
					currentConversationId.value = data.conversationId as number
					let generatedBuildId = 0
					let generatedBuildTitle = ''
					if (data.generatedBuildConfigId != null) {
						generatedBuildId = data.generatedBuildConfigId as number
					}
					if (data.generatedBuildConfigTitle != null) {
						generatedBuildTitle = data.generatedBuildConfigTitle as string
					}
					chatMessages.value.push({
						role: 'assistant',
						content: formatAiContent(data.content as string),
						buildConfigId: generatedBuildId,
						buildConfigTitle: generatedBuildTitle
					})
					if (generatedBuildId > 0) {
						loadBuildConfigs()
					}
					loadConversations()
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					chatMessages.value.push({
						role: 'assistant',
						content: '登录状态已失效，请重新登录后继续对话。',
						buildConfigId: 0,
						buildConfigTitle: ''
					})
					openAuthPage()
					return
				}
				chatMessages.value.push({
					role: 'assistant',
					content: '后端暂时没有返回有效内容，请稍后再试。',
					buildConfigId: 0,
					buildConfigTitle: ''
				})
			},
			fail: () => {
				chatMessages.value.push({
					role: 'assistant',
					content: '暂时连接不到后端服务，请确认后端已经启动。',
					buildConfigId: 0,
					buildConfigTitle: ''
				})
			},
			complete: () => {
				chatLoading.value = false
			}
		})
	}

	function startNewConversation() {
		if (newConversationPressed.value) {
			return
		}
		newConversationPressed.value = true
		chatMessages.value = []
		currentConversationId.value = 0
		clearAttachedBuild()
		draft.value = ''
		setTimeout(() => {
			newConversationPressed.value = false
			uni.showToast({
				title: '已新建对话',
				icon: 'none'
			})
		}, 220)
	}

	function askAiForBuild() {
		selectTab(0)
		draft.value = '请帮我生成一套适合我的电脑配置'
	}

	function openChatBuildPicker() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		chatBuildPickerVisible.value = true
		loadChatAttachBuilds()
	}

	function closeChatBuildPicker() {
		chatBuildPickerVisible.value = false
	}

	function loadChatAttachBuilds() {
		uni.request({
			url: apiBaseUrl + '/api/build-configs?segment=all',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					chatAttachBuildList.value = body.data as BuildConfigItem[]
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
			}
		})
	}

	function attachBuildToChat(item : BuildConfigItem) {
		attachedBuildId.value = item.id
		attachedBuildTitle.value = item.title
		chatBuildPickerVisible.value = false
		uni.showToast({
			title: '已关联配置',
			icon: 'none'
		})
	}

	function toggleBuildForChat(item : BuildConfigItem) {
		if (attachedBuildId.value == item.id) {
			clearAttachedBuild()
			chatBuildPickerVisible.value = false
			return
		}
		attachBuildToChat(item)
	}

	function clearAttachedBuild() {
		attachedBuildId.value = 0
		attachedBuildTitle.value = ''
	}

	function createBuild() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		buildEditingId.value = 0
		buildFormTitle.value = ''
		buildFormNote.value = ''
		selectedHardwareParts.value = []
		buildFormVisible.value = true
		activePickerType.value = 'CPU'
		hardwareKeyword.value = ''
		loadHardwareOptions()
	}

	function editBuild(item : BuildConfigItem) {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		buildDetailVisible.value = false
		buildEditingId.value = item.id
		buildFormTitle.value = item.title
		buildFormNote.value = item.note
		selectedHardwareParts.value = item.parts
		buildFormVisible.value = true
		activePickerType.value = 'CPU'
		hardwareKeyword.value = ''
		loadHardwareOptions()
	}

	function closeBuildForm() {
		if (buildSaving.value) {
			return
		}
		buildFormVisible.value = false
		buildEditingId.value = 0
	}

	function saveBuild() {
		if (buildSaving.value) {
			return
		}
		if (buildFormTitle.value.trim().length == 0) {
			uni.showToast({
				title: '请填写配置名称',
				icon: 'none'
			})
			return
		}
		const partIds = getSelectedPartIds()
		if (partIds.length == 0) {
			uni.showToast({
				title: '请至少选择一个硬件',
				icon: 'none'
			})
			return
		}
		if (getCurrentCompatibilityStatus() == 'BLOCK') {
			uni.showToast({
				title: '存在兼容性问题',
				icon: 'none'
			})
			return
		}
		const wasEditing = buildEditingId.value > 0
		buildSaving.value = true
		uni.request({
			url: wasEditing ? apiBaseUrl + '/api/build-configs/' + buildEditingId.value : apiBaseUrl + '/api/build-configs',
			method: wasEditing ? 'PUT' : 'POST',
			header: {
				'Content-Type': 'application/json',
				'Authorization': 'Bearer ' + token.value
			},
			data: {
				title: buildFormTitle.value.trim(),
				note: buildFormNote.value.trim(),
				partIds: partIds,
				source: 'MANUAL'
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					buildFormVisible.value = false
					buildEditingId.value = 0
					buildSegment.value = 0
					loadBuildConfigs()
					uni.showToast({
						title: wasEditing ? '配置已更新' : '配置已保存',
						icon: 'none'
					})
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					openAuthPage()
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '保存失败',
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
				buildSaving.value = false
			}
		})
	}

	function publishPost() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		postContent.value = ''
		postImageUrls.value = []
		postBuildId.value = 0
		postBuildTitle.value = ''
		postFormVisible.value = true
	}

	function closePostForm() {
		if (postSubmitting.value) {
			return
		}
		postFormVisible.value = false
		postBuildPickerVisible.value = false
	}

	function loadCommunityPosts() {
		loadCommunityPostsWithForce(false)
	}

	function refreshCommunityPosts() {
		communityRefreshing.value = true
		loadCommunityPostsWithForce(true)
	}

	function loadCommunityPostsWithForce(force : boolean) {
		if (communityLoading.value && !force) {
			return
		}
		communityLoading.value = true
		uni.request({
			url: apiBaseUrl + '/api/community/posts',
			method: 'GET',
			header: getAuthHeader(),
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const posts = body.data as CommunityPostItem[]
					for (let i = 0; i < posts.length; i++) {
						posts[i] = normalizeCommunityPost(posts[i])
					}
					communityPosts.value = posts
					clampCommunityPage()
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					return
				}
			},
			complete: () => {
				communityLoading.value = false
				communityRefreshing.value = false
			}
		})
	}

	function submitPost() {
		if (postSubmitting.value) {
			return
		}
		const content = postContent.value.trim()
		const draftImages = postImageUrls.value.slice()
		const draftBuildId = postBuildId.value
		if (content.length == 0) {
			uni.showToast({
				title: '先写点内容',
				icon: 'none'
			})
			return
		}
		postSubmitting.value = true
		postFormVisible.value = false
		postBuildPickerVisible.value = false
		uploadProgressVisible.value = true
		uploadProgressTitle.value = '正在发布'
		uploadProgressPercent.value = 0
		uploadImagesBeforeSubmit(draftImages, 'post', (uploadedUrls : string[]) => {
			createCommunityPost(content, uploadedUrls, draftBuildId)
		}, () => {
			postSubmitting.value = false
			uploadProgressVisible.value = false
		})
	}

	function createCommunityPost(content : string, uploadedImageKeys : string[], buildConfigId : number) {
		const payload = {
			content: content,
			imageKeys: uploadedImageKeys,
			buildConfigId: buildConfigId > 0 ? buildConfigId : null
		}
		uni.request({
			url: apiBaseUrl + '/api/community/posts',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value,
				'Content-Type': 'application/json'
			},
			data: payload,
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					uploadProgressTitle.value = '发布成功'
					uploadProgressPercent.value = 100
					postFormVisible.value = false
					postContent.value = ''
					postImageUrls.value = []
					postBuildId.value = 0
					postBuildTitle.value = ''
					loadCommunityPosts()
					uni.showToast({
						title: '已发布',
						icon: 'none'
					})
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
				postSubmitting.value = false
				setTimeout(() => {
					uploadProgressVisible.value = false
				}, 500)
			}
		})
	}

	function pickPostImages() {
		if (postImageUrls.value.length >= 9) {
			uni.showToast({
				title: '最多添加 9 张图片',
				icon: 'none'
			})
			return
		}
		chooseDraftImages('post', 9 - postImageUrls.value.length)
	}

	function pickCommentImages() {
		if (commentImageUrls.value.length >= 9) {
			uni.showToast({
				title: '最多添加 9 张图片',
				icon: 'none'
			})
			return
		}
		chooseDraftImages('comment', 9 - commentImageUrls.value.length)
	}

	function chooseDraftImages(target : string, count : number) {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.chooseImage({
			count: count,
			sizeType: ['compressed'],
			sourceType: ['album', 'camera'],
			success: (chooseRes) => {
				const paths = chooseRes.tempFilePaths as string[]
				for (let i = 0; i < paths.length; i++) {
					if (target == 'post') {
						if (postImageUrls.value.length < 9) {
							postImageUrls.value.push(paths[i])
						}
					} else {
						if (commentImageUrls.value.length < 9) {
							commentImageUrls.value.push(paths[i])
						}
					}
				}
			}
		})
	}

	function uploadImagesBeforeSubmit(images : string[], target : string, complete : (urls : string[]) => void, fail : () => void) {
		if (images.length == 0) {
			complete([] as string[])
			return
		}
		const uploaded = [] as string[]
		uploadDraftImageAt(images, target, 0, uploaded, complete, fail)
	}

	function uploadDraftImageAt(images : string[], target : string, index : number, uploaded : string[], complete : (urls : string[]) => void, fail : () => void) {
		if (index >= images.length) {
			uploadProgressTitle.value = target == 'post' ? '正在发布' : '上传成功'
			uploadProgressPercent.value = target == 'post' ? 95 : 100
			complete(uploaded)
			return
		}
		uploadProgressTitle.value = target == 'post' ? '正在发布' : '正在发送'
		uploadProgressPercent.value = Math.floor(index * 100 / images.length)
		uploadCommunityImage(images[index], target, (url : string) => {
			uploaded.push(url)
			uploadProgressPercent.value = Math.floor((index + 1) * 100 / images.length)
			uploadDraftImageAt(images, target, index + 1, uploaded, complete, fail)
		}, fail)
	}

	function uploadCommunityImage(filePath : string, target : string, complete : (key : string) => void, fail : () => void) {
		uni.uploadFile({
			url: apiBaseUrl + '/api/files/upload',
			filePath: filePath,
			name: 'file',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (uploadRes) => {
				let body = null as any
				try {
					body = JSON.parse(uploadRes.data as string) as any
				} catch (error) {
					body = null
				}
				if (body != null && body.code == 0 && body.data != null) {
					const key = (body.data as any).key as string
					complete(key)
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '上传失败',
					icon: 'none'
				})
				fail()
			},
			fail: () => {
				fail()
				uni.showToast({
					title: '图片上传失败',
					icon: 'none'
				})
			}
		})
	}

	function removePostImage(image : string) {
		postImageUrls.value = postImageUrls.value.filter((item) : boolean => item != image)
	}

	function movePostImageLeft(index : number) {
		if (index <= 0 || index >= postImageUrls.value.length) {
			return
		}
		const next = postImageUrls.value.slice()
		const current = next[index]
		next[index] = next[index - 1]
		next[index - 1] = current
		postImageUrls.value = next
	}

	function movePostImageRight(index : number) {
		if (index < 0 || index >= postImageUrls.value.length - 1) {
			return
		}
		const next = postImageUrls.value.slice()
		const current = next[index]
		next[index] = next[index + 1]
		next[index + 1] = current
		postImageUrls.value = next
	}

	function startPostImageSort(index : number, event : TouchEvent) {
		postImageSortActive.value = true
		draggingPostImageIndex.value = index
		if (event.touches.length > 0) {
			postImageDragStartX.value = event.touches[0].clientX
			postImageDragStartY.value = event.touches[0].clientY
		}
	}

	function startPostImageTouch(index : number, event : TouchEvent) {
		if (event.touches.length > 0) {
			if (postImageLongPressTimer.value > 0) {
				clearTimeout(postImageLongPressTimer.value)
			}
			postImageSortActive.value = false
			draggingPostImageIndex.value = index
			postImageDragStartX.value = event.touches[0].clientX
			postImageDragStartY.value = event.touches[0].clientY
			postImageDragOffsetX.value = 0
			postImageDragOffsetY.value = 0
			postImageLongPressTimer.value = setTimeout(() => {
				if (draggingPostImageIndex.value == index) {
					postImageSortActive.value = true
				}
			}, 360)
		}
	}

	function movePostImageDrag(event : TouchEvent) {
		if (!postImageSortActive.value || draggingPostImageIndex.value < 0 || event.touches.length == 0) {
			return
		}
		const distanceX = event.touches[0].clientX - postImageDragStartX.value
		const distanceY = event.touches[0].clientY - postImageDragStartY.value
		postImageDragOffsetX.value = distanceX
		postImageDragOffsetY.value = distanceY
		const absX = Math.abs(distanceX)
		const absY = Math.abs(distanceY)
		if (absX < 36 && absY < 48) {
			return
		}
		const from = draggingPostImageIndex.value
		let to = from
		if (absY > absX && absY >= 48) {
			to = from + (distanceY > 0 ? 3 : -3)
			if (to < 0) {
				to = 0
			}
			if (to > postImageUrls.value.length - 1) {
				to = postImageUrls.value.length - 1
			}
		} else {
			if (distanceX > 0 && from < postImageUrls.value.length - 1) {
				to = from + 1
			}
			if (distanceX < 0 && from > 0) {
				to = from - 1
			}
		}
		if (to == from) {
			return
		}
		swapPostImages(from, to)
		draggingPostImageIndex.value = to
		postImageDragStartX.value = event.touches[0].clientX
		postImageDragStartY.value = event.touches[0].clientY
		postImageDragOffsetX.value = 0
		postImageDragOffsetY.value = 0
	}

	function endPostImageDrag() {
		if (postImageLongPressTimer.value > 0) {
			clearTimeout(postImageLongPressTimer.value)
			postImageLongPressTimer.value = 0
		}
		draggingPostImageIndex.value = -1
		postImageDragOffsetX.value = 0
		postImageDragOffsetY.value = 0
		setTimeout(() => {
			postImageSortActive.value = false
		}, 160)
	}

	function swapPostImages(from : number, to : number) {
		const next = postImageUrls.value.slice()
		const current = next[from]
		next[from] = next[to]
		next[to] = current
		postImageUrls.value = next
	}

	function getPostImageWrapClass(index : number) : string {
		if (!postImageSortActive.value) {
			return 'form-image-wrap'
		}
		if (draggingPostImageIndex.value == index) {
			return 'form-image-wrap form-image-wrap-dragging'
		}
		return 'form-image-wrap form-image-wrap-sorting'
	}

	function getPostImageDragStyle(index : number) : string {
		if (!postImageSortActive.value || draggingPostImageIndex.value != index) {
			return ''
		}
		return 'transform: translate(' + postImageDragOffsetX.value + 'px, ' + postImageDragOffsetY.value + 'px) scale(1.16); z-index: 20;'
	}

	function removeCommentImage(image : string) {
		commentImageUrls.value = commentImageUrls.value.filter((item) : boolean => item != image)
	}

	function normalizeMediaUrl(url : string) : string {
		return normalizeRemoteMediaUrl(url)
	}

	function previewPostDraftImage(image : string) {
		openImagePreview(postImageUrls.value, image)
	}

	function previewPostImages(images : string[], image : string) {
		openImagePreview(images, image)
	}

	function previewImage(image : string) {
		openImagePreview([image], image)
	}

	function openImagePreview(images : string[], current : string) {
		imagePreviewUrls.value = images
		imagePreviewIndex.value = 0
		for (let i = 0; i < images.length; i++) {
			if (images[i] == current) {
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

	function openPostBuildPicker() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		postBuildPickerVisible.value = true
		uni.request({
			url: apiBaseUrl + '/api/build-configs?segment=all',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					postBuildList.value = body.data as BuildConfigItem[]
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					openAuthPage()
				}
			}
		})
	}

	function closePostBuildPicker() {
		postBuildPickerVisible.value = false
	}

	function attachBuildToPost(item : BuildConfigItem) {
		postBuildId.value = item.id
		postBuildTitle.value = item.title
		postBuildPickerVisible.value = false
	}

	function toggleBuildForPost(item : BuildConfigItem) {
		if (postBuildId.value == item.id) {
			clearPostBuild()
			postBuildPickerVisible.value = false
			return
		}
		attachBuildToPost(item)
	}

	function clearPostBuild() {
		postBuildId.value = 0
		postBuildTitle.value = ''
	}

	function openPostDetail(id : number) {
		uni.setStorageSync('current_post_id', id)
		uni.navigateTo({
			url: '/pages/post/detail',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function closePostDetail() {
		postDetailVisible.value = false
		commentText.value = ''
		commentImageUrls.value = []
	}

	function openPostBuildDetail(buildConfigId : number) {
		openChatBuildDetail(buildConfigId)
	}

	function togglePostLike(id : number) {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + id + '/like',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					updateCommunityPost(normalizeCommunityPost(body.data as CommunityPostItem))
				}
			}
		})
	}

	function togglePostFavorite(id : number) {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + id + '/favorite',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					updateCommunityPost(normalizeCommunityPost(body.data as CommunityPostItem))
				}
			}
		})
	}

	function confirmDeletePost(id : number) {
		uni.showModal({
			title: '删除帖子',
			content: '删除后无法恢复，确定删除吗？',
			confirmText: '删除',
			cancelText: '取消',
			confirmColor: '#ff3b30',
			success: (res) => {
				if (res.confirm) {
					deleteCommunityPost(id)
				}
			}
		})
	}

	function deleteCommunityPost(id : number) {
		uni.request({
			url: apiBaseUrl + '/api/community/posts/' + id,
			method: 'DELETE',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					communityPosts.value = communityPosts.value.filter((item) : boolean => item.id != id)
					clampCommunityPage()
					if (selectedPost.value.id == id) {
						selectedPost.value = createEmptyPost()
						postDetailVisible.value = false
					}
					uni.showToast({
						title: '已删除',
						icon: 'none'
					})
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '删除失败',
					icon: 'none'
				})
			}
		})
	}

	function updateCommunityPost(post : CommunityPostItem) {
		for (let i = 0; i < communityPosts.value.length; i++) {
			if (communityPosts.value[i].id == post.id) {
				const current = communityPosts.value[i]
				post.comments = current.comments
				communityPosts.value.splice(i, 1, post)
				break
			}
		}
		if (selectedPost.value.id == post.id) {
			const comments = selectedPost.value.comments
			selectedPost.value = post
			selectedPost.value.comments = comments
		}
	}

	function isMine(authorId : number) : boolean {
		return currentUserId.value > 0 && authorId == currentUserId.value
	}

	function normalizeCommunityPost(post : CommunityPostItem) : CommunityPostItem {
		if (post.author.avatarUrl.length > 0) {
			post.author.avatarUrl = normalizeMediaUrl(post.author.avatarUrl)
		}
		for (let i = 0; i < post.imageUrls.length; i++) {
			post.imageUrls[i] = normalizeMediaUrl(post.imageUrls[i])
		}
		return post
	}

	function submitComment() {
		if (!requireLogin()) {
			return
		}
		if (commentSubmitting.value) {
			return
		}
		if (selectedPost.value.id == 0) {
			return
		}
		const content = commentText.value.trim()
		if (content.length == 0) {
			uni.showToast({
				title: '先写点评论',
				icon: 'none'
			})
			return
		}
		commentSubmitting.value = true
		uploadImagesBeforeSubmit(commentImageUrls.value, 'comment', (uploadedImageKeys : string[]) => {
			uni.request({
				url: apiBaseUrl + '/api/community/posts/' + selectedPost.value.id + '/comments',
				method: 'POST',
				header: {
					'Authorization': 'Bearer ' + token.value,
					'Content-Type': 'application/json'
				},
				data: {
					content: content,
					imageKeys: uploadedImageKeys
				},
				success: (res) => {
					const body = res.data as any
					if (body != null && body.code == 0 && body.data != null) {
						selectedPost.value.comments.push(body.data as CommunityCommentItem)
						selectedPost.value.commentCount = selectedPost.value.commentCount + 1
						commentText.value = ''
						commentImageUrls.value = []
						syncSelectedPostToList()
						return
					}
					if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
						clearAuth()
						openAuthPage()
						return
					}
					uni.showToast({
						title: body != null && body.message != null ? body.message as string : '评论失败',
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
					commentSubmitting.value = false
				}
			})
		}, () => {
			commentSubmitting.value = false
		})
	}

	function toggleCommentLike(id : number) {
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/community/comments/' + id + '/like',
			method: 'POST',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					const updated = body.data as CommunityCommentItem
					for (let i = 0; i < selectedPost.value.comments.length; i++) {
						if (selectedPost.value.comments[i].id == updated.id) {
							selectedPost.value.comments.splice(i, 1, updated)
							break
						}
					}
				}
			}
		})
	}

	function syncSelectedPostToList() {
		for (let i = 0; i < communityPosts.value.length; i++) {
			if (communityPosts.value[i].id == selectedPost.value.id) {
				communityPosts.value.splice(i, 1, selectedPost.value)
				break
			}
		}
	}

	function getAuthorAvatarText(name : string) : string {
		if (name.length > 0) {
			return name.substring(0, 1)
		}
		return '用'
	}

	function login() {
		openAuthPage()
	}

	function handleProfileCardClick() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		openProfileEditor()
	}

	function getProfileAvatarText() : string {
		if (token.value.length == 0) {
			return '我'
		}
		if (currentNickname.value.length > 0) {
			return currentNickname.value.substring(0, 1)
		}
		if (currentUsername.value.length > 0) {
			return currentUsername.value.substring(0, 1)
		}
		return '我'
	}

	function logout() {
		if (logoutPressed.value) {
			return
		}
		logoutPressed.value = true
		setTimeout(() => {
			clearAuth()
			stopHistoryRefreshTimer()
			logoutPressed.value = false
			uni.showToast({
				title: '您已退出登录！',
				icon: 'none'
			})
		}, 180)
	}

	function openAuthPage() {
		uni.navigateTo({
			url: '/pages/auth/auth',
			animationType: 'slide-in-bottom',
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

	function openProfileEditor() {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.navigateTo({
			url: '/pages/profile/profile',
			animationType: 'slide-in-right',
			animationDuration: 260
		})
	}

	function openSettings() {
		uni.showToast({
			title: '后续接入设置',
			icon: 'none'
		})
	}

	function loadAuth() {
		const savedToken = uni.getStorageSync('app_token')
		const savedUserId = uni.getStorageSync('app_user_id')
		const savedUsername = uni.getStorageSync('app_username')
		const savedEmail = uni.getStorageSync('app_email')
		const savedNickname = uni.getStorageSync('app_nickname')
		const savedAvatarKey = uni.getStorageSync('app_avatar_key')
		const savedBio = uni.getStorageSync('app_bio')
		if (savedToken != null) {
			token.value = savedToken as string
		}
		if (savedUserId != null) {
			currentUserId.value = savedUserId as number
		}
		if (savedUsername != null) {
			currentUsername.value = savedUsername as string
		}
		if (savedEmail != null) {
			currentEmail.value = savedEmail as string
		}
		if (savedNickname != null) {
			currentNickname.value = savedNickname as string
		}
		if (savedAvatarKey != null) {
			currentAvatarUrl.value = ''
		}
		if (savedBio != null) {
			currentBio.value = savedBio as string
		}
		if (currentNickname.value.length == 0 && currentUsername.value.length > 0) {
			currentNickname.value = currentUsername.value
		}
	}

	function saveProfile(payload : UserProfilePayload) {
		currentUserId.value = payload.userId
		currentUsername.value = payload.username
		currentEmail.value = payload.email
		currentNickname.value = payload.nickname == null || payload.nickname.length == 0 ? payload.username : payload.nickname
		currentAvatarUrl.value = normalizeAvatarUrl(payload.avatarUrl == null ? '' : payload.avatarUrl)
		currentBio.value = payload.bio == null || payload.bio.length == 0 ? '这个人还没有写简介。' : payload.bio
		uni.setStorageSync('app_user_id', currentUserId.value)
		uni.setStorageSync('app_username', currentUsername.value)
		uni.setStorageSync('app_email', currentEmail.value)
		uni.setStorageSync('app_nickname', currentNickname.value)
		uni.setStorageSync('app_avatar_key', payload.avatarKey == null ? '' : payload.avatarKey)
		uni.setStorageSync('app_bio', currentBio.value)
	}

	function normalizeAvatarUrl(url : string) : string {
		return normalizeRemoteMediaUrl(url)
	}

	function clearAuth() {
		token.value = ''
		currentConversationId.value = 0
		currentUserId.value = 0
		conversationList.value = []
		chatMessages.value = []
		currentUsername.value = ''
		currentEmail.value = ''
		currentNickname.value = ''
		currentAvatarUrl.value = ''
		currentBio.value = ''
		buildList.value = []
		selectedHardwareParts.value = []
		buildEditingId.value = 0
		buildFormVisible.value = false
		partPickerVisible.value = false
		buildDetailVisible.value = false
		chatBuildPickerVisible.value = false
		communityPosts.value = []
		postFormVisible.value = false
		postBuildPickerVisible.value = false
		postDetailVisible.value = false
		postImageUrls.value = []
		commentImageUrls.value = []
		selectedPost.value = createEmptyPost()
		clearAttachedBuild()
		uni.removeStorageSync('app_token')
		uni.removeStorageSync('app_user_id')
		uni.removeStorageSync('app_username')
		uni.removeStorageSync('app_email')
		uni.removeStorageSync('app_nickname')
		uni.removeStorageSync('app_avatar_key')
		uni.removeStorageSync('app_bio')
	}

	function loadProfile() {
		if (token.value.length == 0) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/user/profile',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					saveProfile(body.data as UserProfilePayload)
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			fail: () => {
			}
		})
	}

	function loadBuildConfigs() {
		if (token.value.length == 0) {
			buildList.value = []
			resetBuildPage()
			return
		}
		buildLoading.value = true
		uni.request({
			url: apiBaseUrl + '/api/build-configs?segment=' + getBuildSegmentKey(),
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					buildList.value = body.data as BuildConfigItem[]
					clampBuildPage()
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			complete: () => {
				buildLoading.value = false
			}
		})
	}

	function getBuildSegmentKey() : string {
		if (buildSegment.value == 1) {
			return 'ai'
		}
		if (buildSegment.value == 2) {
			return 'favorite'
		}
		return 'mine'
	}

	function getBuildSourceLabel(source : string) : string {
		return source == 'AI' ? 'AI 生成' : '手动配置'
	}

	function getHardwareTypeLabel(type : string) : string {
		for (let i = 0; i < hardwareTypeOptions.length; i++) {
			if (hardwareTypeOptions[i].key == type) {
				return hardwareTypeOptions[i].label
			}
		}
		return type
	}

	function getSelectedPart(type : string) : any {
		for (let i = 0; i < selectedHardwareParts.value.length; i++) {
			if (selectedHardwareParts.value[i].type == type) {
				return selectedHardwareParts.value[i]
			}
		}
		return null
	}

	function hasSelectedPart(type : string) : boolean {
		return getSelectedPart(type) != null
	}

	function getSelectedPartName(type : string) : string {
		const part = getSelectedPart(type)
		if (part == null) {
			return '请选择' + getHardwareTypeLabel(type)
		}
		return part.brand + ' ' + part.model
	}

	function getSelectedPartPrice(type : string) : string {
		const part = getSelectedPart(type)
		if (part == null) {
			return ''
		}
		return '¥' + part.price
	}

	function getPartSelectorClass(type : string) : string {
		if (hasPartCompatibilityIssue(type)) {
			return 'part-selector-row part-selector-row-danger'
		}
		return 'part-selector-row'
	}

	function hasPartCompatibilityIssue(type : string) : boolean {
		const cpu = getSelectedPart('CPU')
		const motherboard = getSelectedPart('MOTHERBOARD')
		const memory = getSelectedPart('MEMORY')
		const gpu = getSelectedPart('GPU')
		const psu = getSelectedPart('PSU')
		const pcCase = getSelectedPart('CASE')
		const cooler = getSelectedPart('COOLER')
		if ((type == 'CPU' || type == 'MOTHERBOARD') && cpu != null && motherboard != null && cpu.socketType.length > 0 && motherboard.socketType.length > 0 && cpu.socketType != motherboard.socketType) {
			return true
		}
		if ((type == 'MEMORY' || type == 'MOTHERBOARD') && memory != null && motherboard != null && memory.memoryType.length > 0 && motherboard.memoryType.length > 0 && memory.memoryType != motherboard.memoryType) {
			return true
		}
		if (type == 'PSU' && psu != null) {
			let estimatedWattage = 120
			for (let i = 0; i < selectedHardwareParts.value.length; i++) {
				if (selectedHardwareParts.value[i].type != 'PSU') {
					estimatedWattage += selectedHardwareParts.value[i].wattage
				}
			}
			if (psu.wattage > 0 && psu.wattage < estimatedWattage) {
				return true
			}
		}
		if ((type == 'GPU' || type == 'CASE') && gpu != null && pcCase != null && gpu.gpuLengthMm > 0 && pcCase.gpuLengthMm > 0 && gpu.gpuLengthMm > pcCase.gpuLengthMm) {
			return true
		}
		if ((type == 'COOLER' || type == 'CASE') && cooler != null && pcCase != null && cooler.coolerHeightMm > 0 && pcCase.coolerHeightMm > 0 && cooler.coolerHeightMm > pcCase.coolerHeightMm) {
			return true
		}
		if ((type == 'MOTHERBOARD' || type == 'CASE') && motherboard != null && pcCase != null && motherboard.formFactor.length > 0 && pcCase.supportedFormFactors.length > 0 && pcCase.supportedFormFactors.toUpperCase().indexOf(motherboard.formFactor.toUpperCase()) < 0) {
			return true
		}
		return false
	}

	function getSelectedPartIds() : number[] {
		const ids = [] as number[]
		for (let i = 0; i < selectedHardwareParts.value.length; i++) {
			ids.push(selectedHardwareParts.value[i].id)
		}
		return ids
	}

	function openPartPicker(type : string) {
		activePickerType.value = type
		hardwareKeyword.value = ''
		partPickerVisible.value = true
		loadHardwareOptions()
	}

	function closePartPicker() {
		partPickerVisible.value = false
	}

	function loadHardwareOptions() {
		if (token.value.length == 0) {
			hardwareOptions.value = []
			return
		}
		let url = apiBaseUrl + '/api/hardware-parts?type=' + activePickerType.value
		if (hardwareKeyword.value.trim().length > 0) {
			url += '&keyword=' + encodeURIComponent(hardwareKeyword.value.trim())
		}
		uni.request({
			url: url,
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					hardwareOptions.value = body.data as HardwarePartItem[]
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
					openAuthPage()
				}
			},
			fail: () => {
				uni.showToast({
					title: '暂时连接不到硬件库',
					icon: 'none'
				})
			}
		})
	}

	function selectHardwarePart(part : HardwarePartItem) {
		const next = [] as HardwarePartItem[]
		for (let i = 0; i < selectedHardwareParts.value.length; i++) {
			if (selectedHardwareParts.value[i].type != part.type) {
				next.push(selectedHardwareParts.value[i])
			}
		}
		next.push(part)
		selectedHardwareParts.value = next
		partPickerVisible.value = false
	}

	function getCurrentBuildTotalPrice() : number {
		let total = 0
		for (let i = 0; i < selectedHardwareParts.value.length; i++) {
			total += selectedHardwareParts.value[i].price
		}
		return total
	}

	function getCurrentBuildPerformanceScore() : number {
		let weighted = 0
		let weight = 0
		const cpu = getSelectedPart('CPU')
		const gpu = getSelectedPart('GPU')
		const memory = getSelectedPart('MEMORY')
		const storage = getSelectedPart('STORAGE')
		const motherboard = getSelectedPart('MOTHERBOARD')
		if (cpu != null) {
			weighted += cpu.performanceScore * 30
			weight += 30
		}
		if (gpu != null) {
			weighted += gpu.performanceScore * 40
			weight += 40
		}
		if (memory != null) {
			weighted += memory.performanceScore * 12
			weight += 12
		}
		if (storage != null) {
			weighted += storage.performanceScore * 10
			weight += 10
		}
		if (motherboard != null) {
			weighted += motherboard.performanceScore * 8
			weight += 8
		}
		if (weight == 0) {
			return 0
		}
		return Math.round(weighted / weight)
	}

	function getCurrentCompatibilityStatus() : string {
		const messages = getCurrentCompatibilityMessages()
		for (let i = 0; i < messages.length; i++) {
			const message = messages[i]
			if (message.indexOf('不匹配') >= 0 || message.indexOf('超出') >= 0 || message.indexOf('至少') >= 0) {
				return 'BLOCK'
			}
		}
		if (messages.length == 1 && messages[0].indexOf('未发现') >= 0) {
			return 'PASS'
		}
		return 'WARN'
	}

	function getCurrentCompatibilityMessages() : string[] {
		const messages = [] as string[]
		const cpu = getSelectedPart('CPU')
		const motherboard = getSelectedPart('MOTHERBOARD')
		const memory = getSelectedPart('MEMORY')
		const gpu = getSelectedPart('GPU')
		const psu = getSelectedPart('PSU')
		const pcCase = getSelectedPart('CASE')
		const cooler = getSelectedPart('COOLER')
		if (cpu == null) {
			messages.push('建议补充 CPU。')
		}
		if (motherboard == null) {
			messages.push('建议补充主板。')
		}
		if (memory == null) {
			messages.push('建议补充内存。')
		}
		if (psu == null) {
			messages.push('建议补充电源。')
		}
		if (pcCase == null) {
			messages.push('建议补充机箱。')
		}
		if (cpu != null && motherboard != null && cpu.socketType.length > 0 && motherboard.socketType.length > 0 && cpu.socketType != motherboard.socketType) {
			messages.push('CPU 接口 ' + cpu.socketType + ' 与主板接口 ' + motherboard.socketType + ' 不匹配。')
		}
		if (memory != null && motherboard != null && memory.memoryType.length > 0 && motherboard.memoryType.length > 0 && memory.memoryType != motherboard.memoryType) {
			messages.push('内存类型 ' + memory.memoryType + ' 与主板支持的 ' + motherboard.memoryType + ' 不匹配。')
		}
		if (psu != null) {
			let estimatedWattage = 120
			for (let i = 0; i < selectedHardwareParts.value.length; i++) {
				if (selectedHardwareParts.value[i].type != 'PSU') {
					estimatedWattage += selectedHardwareParts.value[i].wattage
				}
			}
			if (psu.wattage > 0 && psu.wattage < estimatedWattage) {
				messages.push('电源额定功率建议至少 ' + estimatedWattage + 'W，当前为 ' + psu.wattage + 'W。')
			}
		}
		if (gpu != null && pcCase != null && gpu.gpuLengthMm > 0 && pcCase.gpuLengthMm > 0 && gpu.gpuLengthMm > pcCase.gpuLengthMm) {
			messages.push('显卡长度 ' + gpu.gpuLengthMm + 'mm 超出机箱支持的 ' + pcCase.gpuLengthMm + 'mm。')
		}
		if (cooler != null && pcCase != null && cooler.coolerHeightMm > 0 && pcCase.coolerHeightMm > 0 && cooler.coolerHeightMm > pcCase.coolerHeightMm) {
			messages.push('散热器高度 ' + cooler.coolerHeightMm + 'mm 超出机箱支持的 ' + pcCase.coolerHeightMm + 'mm。')
		}
		if (motherboard != null && pcCase != null && motherboard.formFactor.length > 0 && pcCase.supportedFormFactors.length > 0 && pcCase.supportedFormFactors.toUpperCase().indexOf(motherboard.formFactor.toUpperCase()) < 0) {
			messages.push('主板版型 ' + motherboard.formFactor + ' 不在机箱支持范围内。')
		}
		if (messages.length == 0) {
			messages.push('当前选择未发现明显兼容性问题。')
		}
		return messages
	}

	function getCompatibilityLabel(status : string) : string {
		if (status == 'PASS') {
			return '兼容'
		}
		if (status == 'BLOCK') {
			return '需调整'
		}
		return '需确认'
	}

	function getCompatibilityClass(status : string) : string {
		if (status == 'PASS') {
			return 'build-status build-status-pass'
		}
		if (status == 'BLOCK') {
			return 'build-status build-status-block'
		}
		return 'build-status build-status-warn'
	}

	function getPartSpecText(part : HardwarePartItem) : string {
		let text = ''
		if (part.socketType.length > 0) {
			text += part.socketType
		}
		if (part.memoryType.length > 0) {
			text += text.length == 0 ? part.memoryType : ' · ' + part.memoryType
		}
		if (part.formFactor.length > 0) {
			text += text.length == 0 ? part.formFactor : ' · ' + part.formFactor
		}
		if (part.wattage > 0) {
			text += text.length == 0 ? part.wattage + 'W' : ' · ' + part.wattage + 'W'
		}
		if (part.gpuLengthMm > 0) {
			text += text.length == 0 ? part.gpuLengthMm + 'mm' : ' · ' + part.gpuLengthMm + 'mm'
		}
		if (part.coolerHeightMm > 0) {
			text += text.length == 0 ? part.coolerHeightMm + 'mm' : ' · ' + part.coolerHeightMm + 'mm'
		}
		if (text.length == 0) {
			text = part.specs.length > 0 ? part.specs : '通用规格'
		}
		return text
	}

	function openBuildDetail(item : BuildConfigItem) {
		selectedBuildDetail.value = item
		buildDetailVisible.value = true
	}

	function closeBuildDetail() {
		buildDetailVisible.value = false
	}

	function openChatBuildDetail(buildConfigId : number) {
		if (buildConfigId <= 0) {
			return
		}
		if (!requireLogin()) {
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/build-configs/' + buildConfigId,
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					selectedBuildDetail.value = body.data as BuildConfigItem
					buildDetailVisible.value = true
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '配置单不存在',
					icon: 'none'
				})
			},
			fail: () => {
				uni.showToast({
					title: '暂时读取不到配置详情',
					icon: 'none'
				})
			}
		})
	}

	function toggleBuildFavorite(item : BuildConfigItem) {
		uni.request({
			url: apiBaseUrl + '/api/build-configs/' + item.id + '/favorite',
			method: 'PATCH',
			header: {
				'Content-Type': 'application/json',
				'Authorization': 'Bearer ' + token.value
			},
			data: {
				favorite: !item.favorite
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					if (buildDetailVisible.value) {
						selectedBuildDetail.value.favorite = !selectedBuildDetail.value.favorite
					}
					loadBuildConfigs()
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '操作失败',
					icon: 'none'
				})
			}
		})
	}

	function deleteBuild(id : number) {
		uni.request({
			url: apiBaseUrl + '/api/build-configs/' + id,
			method: 'DELETE',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					buildList.value = buildList.value.filter((item) : boolean => item.id != id)
					clampBuildPage()
					uni.showToast({
						title: '配置已删除',
						icon: 'none'
					})
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '删除失败',
					icon: 'none'
				})
			}
		})
	}

	function copyBuild(content : string) {
		uni.setClipboardData({
			data: content,
			success: () => {
				uni.showToast({
					title: '已复制配置',
					icon: 'none'
				})
			}
		})
	}

	function loadConversations() {
		loadConversationsWithState(false)
	}

	function refreshConversations() {
		if (token.value.length == 0) {
			historyRefreshing.value = false
			return
		}
		historyRefreshing.value = true
		loadConversationsWithState(true)
	}

	function loadConversationsWithState(fromRefresh : boolean) {
		if (token.value.length == 0) {
			conversationList.value = []
			if (fromRefresh) {
				historyRefreshing.value = false
			}
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/ai/conversations',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					conversationList.value = body.data as ConversationSummary[]
					return
				}
				if (res.statusCode == 401 || res.statusCode == 403 || (body != null && body.code == 401)) {
					clearAuth()
				}
			},
			complete: () => {
				if (fromRefresh) {
					historyRefreshing.value = false
				}
			}
		})
	}

	function openConversation(conversationId : number) {
		if (historyPressLocked.value) {
			return
		}
		if (deleteActionConversationId.value != 0) {
			deleteActionConversationId.value = 0
			return
		}
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		uni.request({
			url: apiBaseUrl + '/api/ai/conversations/' + conversationId + '/messages',
			method: 'GET',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0 && body.data != null) {
					currentConversationId.value = conversationId
					chatMessages.value = normalizeChatMessages(body.data as ChatMessage[])
					historyOpen.value = false
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '读取对话失败',
					icon: 'none'
				})
			},
			fail: () => {
				uni.showToast({
					title: '暂时连接不到后端服务',
					icon: 'none'
				})
			}
		})
	}

	function normalizeChatMessages(messages : ChatMessage[]) : ChatMessage[] {
		const result = [] as ChatMessage[]
		for (let i = 0; i < messages.length; i++) {
			const item = messages[i]
			result.push({
				role: item.role,
				content: item.content,
				buildConfigId: item.buildConfigId == null ? 0 : item.buildConfigId,
				buildConfigTitle: item.buildConfigTitle == null ? '' : item.buildConfigTitle
			})
		}
		return result
	}

	function getHistoryItemClass(conversationId : number) : string {
		let className = currentConversationId.value == conversationId ? 'history-item history-item-active' : 'history-item'
		if (deleteActionConversationId.value == conversationId) {
			className += ' history-item-delete-open'
		}
		return className
	}

	function showConversationDeleteAction(conversationId : number) {
		if (token.value.length == 0) {
			openAuthPage()
			return
		}
		historyPressLocked.value = true
		setTimeout(() => {
			historyPressLocked.value = false
		}, 420)
		deleteActionConversationId.value = conversationId
	}

	function openDeleteDialog(conversationId : number, title : string) {
		pendingDeleteConversationId.value = conversationId
		pendingDeleteTitle.value = title
		deleteDialogVisible.value = true
	}

	function cancelDeleteDialog() {
		if (deleteSubmitting.value) {
			return
		}
		deleteDialogVisible.value = false
		pendingDeleteConversationId.value = 0
		pendingDeleteTitle.value = ''
	}

	function confirmDeleteDialog() {
		if (deleteSubmitting.value || pendingDeleteConversationId.value == 0) {
			return
		}
		deleteConversation(pendingDeleteConversationId.value)
	}

	function deleteConversation(conversationId : number) {
		deleteSubmitting.value = true
		uni.request({
			url: apiBaseUrl + '/api/ai/conversations/' + conversationId,
			method: 'DELETE',
			header: {
				'Authorization': 'Bearer ' + token.value
			},
			success: (res) => {
				const body = res.data as any
				if (body != null && body.code == 0) {
					conversationList.value = conversationList.value.filter((item) : boolean => item.id != conversationId)
					if (currentConversationId.value == conversationId) {
						currentConversationId.value = 0
						chatMessages.value = []
					}
					deleteActionConversationId.value = 0
					deleteDialogVisible.value = false
					pendingDeleteConversationId.value = 0
					pendingDeleteTitle.value = ''
					uni.showToast({
						title: '已删除对话',
						icon: 'none'
					})
					return
				}
				uni.showToast({
					title: body != null && body.message != null ? body.message as string : '删除失败',
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
				deleteSubmitting.value = false
			}
		})
	}

	function startHistoryRefreshTimer() {
		if (historyRefreshTimer != 0) {
			return
		}
		historyRefreshTimer = setInterval(() => {
			if (token.value.length > 0) {
				loadConversations()
			}
		}, 15000)
	}

	function stopHistoryRefreshTimer() {
		if (historyRefreshTimer == 0) {
			return
		}
		clearInterval(historyRefreshTimer)
		historyRefreshTimer = 0
	}

	function formatHistoryTime(value : string) : string {
		if (value.length >= 16) {
			return value.substring(5, 16).replace('T', ' ')
		}
		return value
	}

	function formatAiContent(content : string) : string {
		return content
			.replace(/\*\*/g, '')
			.replace(/###/g, '')
			.replace(/##/g, '')
			.replace(/#/g, '')
			.replace(/^\s*[-*]\s+/gm, '')
			.trim()
	}

	function handleTouchStart(event : TouchEvent) {
		if (imagePreviewVisible.value) {
			return
		}
		if (event.touches.length > 0) {
			touchStartX.value = event.touches[0].clientX
			touchStartY.value = event.touches[0].clientY
		}
	}

	function handleTouchEnd(event : TouchEvent) {
		if (imagePreviewVisible.value) {
			return
		}
		if (event.changedTouches.length == 0) {
			return
		}
		const distance = event.changedTouches[0].clientX - touchStartX.value
		const verticalDistance = event.changedTouches[0].clientY - touchStartY.value
		const absDistance = Math.abs(distance)
		const absVerticalDistance = Math.abs(verticalDistance)
		if (absDistance < 80 || absDistance < absVerticalDistance * 1.2) {
			return
		}
		if (historyOpen.value && distance < -70) {
			historyOpen.value = false
			return
		}
		if (activeTab.value == 0 && touchStartX.value < 170 && distance > 70) {
			historyOpen.value = true
			return
		}
		if (distance < -80 && activeTab.value < 4) {
			selectTab(activeTab.value + 1)
			return
		}
		if (distance > 80 && activeTab.value > 0) {
			selectTab(activeTab.value - 1)
		}
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

	.app-shell .chat-nav,
	.app-shell .composer,
	.app-shell .tabbar,
	.app-shell .post-head,
	.app-shell .page-head-row,
	.app-shell .post-actions,
	.app-shell .build-card-head,
	.app-shell .build-score-row,
	.app-shell .build-actions,
	.app-shell .menu-item,
	.app-shell .profile-card,
	.app-shell .zero-stats,
	.app-shell .segmented,
	.app-shell .section-search {
		display: flex;
		flex-direction: row;
	}

	.app-shell {
		min-height: 100vh;
		width: 100vw;
		max-width: none;
		background-color: #f6f7fb;
		position: relative;
		overflow: hidden;
	}

	.page {
		min-height: 100vh;
		box-sizing: border-box;
		padding-left: 18px;
		padding-right: 18px;
		padding-bottom: 112px;
		transform: translateX(0);
	}

	.page-enter-right {
		animation-name: pageInRight;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	.page-enter-left {
		animation-name: pageInLeft;
		animation-duration: 260ms;
		animation-timing-function: ease-out;
	}

	@keyframes pageInRight {
		from {
			opacity: 0;
			transform: translateX(34px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}

	@keyframes pageInLeft {
		from {
			opacity: 0;
			transform: translateX(-34px);
		}
		to {
			opacity: 1;
			transform: translateX(0);
		}
	}

	.status-space {
		height: 36px;
		min-height: 36px;
		flex-shrink: 0;
	}

	.chat-page {
		background-color: #f9fafc;
		padding-bottom: 0;
	}

	.chat-nav {
		display: flex;
		position: fixed;
		left: 18px;
		right: 18px;
		top: 36px;
		width: auto;
		max-width: none;
		height: 48px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		background-color: #f9fafc;
		z-index: 30;
	}

	.chat-nav .nav-button:first-child {
		position: absolute;
		left: 0;
		top: 5px;
	}

	.chat-nav .nav-button:last-child {
		position: absolute;
		right: 0;
		top: 5px;
	}

	.nav-title {
		position: absolute;
		left: 0;
		right: 0;
		top: 12px;
		font-size: 17px;
		font-weight: 700;
		color: #111827;
		text-align: center;
	}

	.nav-button {
		position: relative;
		width: 38px;
		height: 38px;
		border-radius: 19px;
		background-color: #ffffff;
		align-items: center;
		justify-content: center;
		border-width: 1px;
		border-color: #eef2f7;
		transition-property: transform, background-color;
		transition-duration: 180ms;
	}

	.nav-button-pressed {
		transform: scale(0.9);
		background-color: #eef5ff;
	}

	.nav-button-plus-active {
		animation-name: plusButtonSpin;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	.nav-button-plus-active .plus-line {
		background-color: #007aff;
	}

	.chat-nav .nav-button:last-child::before,
	.chat-nav .nav-button:last-child::after {
		content: '';
		position: absolute;
		left: 11px;
		top: 18px;
		width: 16px;
		height: 2px;
		border-radius: 2px;
		background-color: #1f2937;
	}

	.chat-nav .nav-button:last-child::after {
		transform: rotate(90deg);
	}

	@keyframes plusButtonSpin {
		from {
			transform: scale(0.92) rotate(0deg);
		}
		to {
			transform: scale(0.92) rotate(90deg);
		}
	}

	.menu-line {
		width: 17px;
		height: 2px;
		border-radius: 2px;
		background-color: #1f2937;
		margin-bottom: 5px;
	}

	.menu-line-short {
		width: 12px;
		margin-bottom: 0;
	}

	.plus-line {
		position: absolute;
		left: 11px;
		top: 18px;
		width: 16px;
		height: 2px;
		border-radius: 2px;
		background-color: #1f2937;
	}

	.plus-line-vertical {
		transform: rotate(90deg);
	}

	.chat-scroll {
		position: fixed;
		left: 18px;
		right: 18px;
		top: 92px;
		bottom: 160px;
		width: auto;
		max-width: none;
		margin-top: 0;
		z-index: 1;
	}

	.chat-empty {
		min-height: 500px;
		align-items: center;
		justify-content: center;
		padding-left: 8px;
		padding-right: 8px;
	}

	.ai-orbit {
		position: relative;
		width: 92px;
		height: 72px;
		align-items: center;
		justify-content: center;
		margin-bottom: 18px;
	}

	.ai-core {
		position: absolute;
		left: 29px;
		top: 19px;
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		z-index: 2;
	}

	.ai-core-text {
		font-size: 12px;
		line-height: 16px;
		font-weight: 900;
		color: #ffffff;
	}

	.ai-dot {
		position: absolute;
		width: 10px;
		height: 10px;
		border-radius: 5px;
		background-color: #007aff;
		left: 41px;
		top: 31px;
		z-index: 1;
	}

	.ai-dot-one {
		transform: translateX(-26px) translateY(-16px) scale(0.92);
		animation-name: aiDotOneOrbit3d;
		animation-duration: 3200ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: 1;
	}

	.ai-dot-two {
		background-color: #34c759;
		transform: translateX(27px) translateY(-10px) scale(1.04);
		z-index: 3;
		animation-name: aiDotTwoOrbit3d;
		animation-duration: 3200ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: 1;
	}

	.ai-dot-three {
		background-color: #ff9500;
		transform: translateX(0) translateY(27px) scale(0.98);
		animation-name: aiDotThreeOrbit3d;
		animation-duration: 3200ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: 1;
	}

	@keyframes aiDotOneOrbit3d {
		0% {
			transform: translateX(-26px) translateY(-16px) scale(0.92);
		}
		28% {
			transform: translateX(7px) translateY(-25px) scale(0.78);
		}
		56% {
			transform: translateX(29px) translateY(4px) scale(1.12);
		}
		78% {
			transform: translateX(-4px) translateY(27px) scale(1.02);
		}
		100% {
			transform: translateX(-26px) translateY(-16px) scale(0.92);
		}
	}

	@keyframes aiDotTwoOrbit3d {
		0% {
			transform: translateX(27px) translateY(-10px) scale(1.04);
		}
		28% {
			transform: translateX(4px) translateY(26px) scale(1.14);
		}
		56% {
			transform: translateX(-30px) translateY(4px) scale(0.9);
		}
		78% {
			transform: translateX(-3px) translateY(-26px) scale(0.78);
		}
		100% {
			transform: translateX(27px) translateY(-10px) scale(1.04);
		}
	}

	@keyframes aiDotThreeOrbit3d {
		0% {
			transform: translateX(0) translateY(27px) scale(0.98);
		}
		28% {
			transform: translateX(-29px) translateY(3px) scale(0.86);
		}
		56% {
			transform: translateX(-3px) translateY(-26px) scale(0.78);
		}
		78% {
			transform: translateX(30px) translateY(3px) scale(1.14);
		}
		100% {
			transform: translateX(0) translateY(27px) scale(0.98);
		}
	}

	.chat-empty-title {
		font-size: 25px;
		line-height: 34px;
		font-weight: 800;
		color: #111827;
		text-align: center;
	}

	.chat-empty-desc {
		width: 86%;
		margin-top: 10px;
		font-size: 14px;
		line-height: 22px;
		color: #667085;
		text-align: center;
	}

	.prompt-grid {
		width: 100%;
		margin-top: 28px;
	}

	.prompt-card {
		min-height: 48px;
		border-radius: 18px;
		background-color: #ffffff;
		justify-content: center;
		padding-left: 16px;
		padding-right: 16px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.prompt-text {
		font-size: 14px;
		line-height: 20px;
		color: #344054;
	}

	.message-list {
		padding-top: 12px;
		padding-bottom: 24px;
	}

	.message-row {
		flex-direction: column;
		align-items: flex-start;
		margin-bottom: 14px;
	}

	.message-row-user {
		align-items: flex-end;
	}

	.message-bubble {
		max-width: 82%;
		padding: 13px 15px;
		border-radius: 21px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
	}

	.message-bubble-ai {
		background-color: #ffffff;
	}

	.message-bubble-user {
		background-color: #ffffff;
	}

	.message-bubble-breathing {
		width: 122px;
		height: 44px;
		padding: 0 12px;
		flex-direction: row;
		align-items: center;
		border-color: #edf2f7;
		animation-name: thinkingBreathe;
		animation-duration: 1700ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: infinite;
	}

	.thinking-mark {
		width: 28px;
		height: 28px;
		border-radius: 14px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 9px;
		animation-name: coreBreathe;
		animation-duration: 1700ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: infinite;
	}

	.thinking-mark-text {
		font-size: 10px;
		line-height: 14px;
		font-weight: 900;
		color: #ffffff;
	}

	.breathing-title {
		font-size: 13px;
		line-height: 18px;
		font-weight: 800;
		color: #344054;
	}

	@keyframes thinkingBreathe {
		0% {
			transform: scale(0.99);
			opacity: 0.86;
		}
		50% {
			transform: scale(1.015);
			opacity: 1;
		}
		100% {
			transform: scale(0.99);
			opacity: 0.86;
		}
	}

	@keyframes coreBreathe {
		0% {
			transform: scale(0.9);
			background-color: #344054;
		}
		50% {
			transform: scale(1.08);
			background-color: #111827;
		}
		100% {
			transform: scale(0.9);
			background-color: #344054;
		}
	}

	.message-text {
		font-size: 15px;
		line-height: 22px;
		color: #111827;
	}

	.message-text-user {
		color: #111827;
	}

	.chat-build-card {
		width: 260px;
		min-height: 72px;
		border-radius: 22px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #dbeafe;
		margin-top: 8px;
		padding: 12px;
		flex-direction: row;
		align-items: center;
		box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
	}

	.chat-build-icon {
		width: 42px;
		height: 42px;
		border-radius: 16px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-right: 10px;
	}

	.chat-build-icon-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #ffffff;
	}

	.chat-build-main {
		flex: 1;
	}

	.chat-build-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.chat-build-desc {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #667085;
	}

	.chat-build-arrow {
		font-size: 24px;
		line-height: 28px;
		color: #98a2b3;
		margin-left: 8px;
	}

	.composer {
		display: flex;
		position: fixed;
		left: 18px;
		right: 18px;
		bottom: 98px;
		width: auto;
		max-width: none;
		min-height: 56px;
		border-radius: 28px;
		background-color: #ffffff;
		flex-direction: row;
		align-items: center;
		padding: 7px;
		margin-bottom: 0;
		box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
		border-width: 1px;
		border-color: rgba(226, 232, 240, 0.9);
		z-index: 30;
	}

	.composer-attachment {
		position: fixed;
		left: 30px;
		right: 30px;
		bottom: 162px;
		width: auto;
		max-width: none;
		min-height: 38px;
		border-radius: 19px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #dbeafe;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		padding-left: 14px;
		padding-right: 8px;
		z-index: 31;
		box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
	}

	.composer-attachment-text {
		flex: 1;
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #007aff;
	}

	.composer-attachment-close {
		position: relative;
		width: 28px;
		height: 28px;
		border-radius: 14px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
	}

	.attach-button {
		position: relative;
		width: 42px;
		height: 42px;
		border-radius: 21px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
		margin-right: 4px;
	}

	.attach-button-active {
		background-color: #eef5ff;
	}

	.attach-icon-line {
		width: 15px;
		height: 3px;
		border-radius: 3px;
		background-color: #667085;
	}

	.attach-icon-dot {
		position: absolute;
		width: 7px;
		height: 7px;
		border-radius: 4px;
		background-color: #007aff;
		right: 12px;
		top: 12px;
	}

	.composer-input {
		flex: 1;
		height: 42px;
		padding-left: 14px;
		padding-right: 10px;
		font-size: 15px;
		color: #111827;
	}

	.composer-placeholder {
		color: #98a2b3;
	}

	.send-button {
		position: relative;
		width: 42px;
		height: 42px;
		border-radius: 21px;
		background-color: #e5e7eb;
		align-items: center;
		justify-content: center;
	}

	.send-button-ready {
		background-color: #111827;
	}

	.send-arrow-line {
		width: 17px;
		height: 3px;
		border-radius: 3px;
		background-color: #ffffff;
	}

	.send-arrow-head {
		position: absolute;
		width: 9px;
		height: 3px;
		border-radius: 3px;
		background-color: #ffffff;
		right: 11px;
	}

	.send-arrow-head-top {
		transform: rotate(45deg);
		margin-top: -6px;
	}

	.send-arrow-head-bottom {
		transform: rotate(-45deg);
		margin-top: 6px;
	}

	.history-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(15, 23, 42, 0.18);
		z-index: 60;
	}

	.history-panel {
		position: fixed;
		left: -304px;
		top: 0;
		bottom: 0;
		width: 304px;
		background-color: #ffffff;
		z-index: 61;
		padding-top: 56px;
		padding-left: 18px;
		padding-right: 18px;
		box-sizing: border-box;
		box-shadow: none;
		transition-property: left;
		transition-duration: 240ms;
	}

	.history-panel-open {
		left: 0;
		box-shadow: 22px 0 45px rgba(15, 23, 42, 0.13);
	}

	.history-top {
		height: 44px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.history-list {
		margin-top: 18px;
		height: 620px;
	}

	.history-row {
		flex-direction: row;
		align-items: center;
		margin-bottom: 10px;
	}

	.history-item {
		flex: 1;
		border-radius: 18px;
		background-color: #f6f7fb;
		padding: 14px;
		border-width: 1px;
		border-color: #edf0f5;
		transition-property: transform, background-color, border-color;
		transition-duration: 180ms;
	}

	.history-item-active {
		background-color: #eef5ff;
		border-color: #d8e8ff;
	}

	.history-item-delete-open {
		transform: translateX(-3px);
		background-color: #f6f7fb;
		border-color: #ffccd0;
	}

	.history-delete-action {
		width: 0;
		height: 34px;
		border-radius: 17px;
		background-color: #ff3b30;
		align-items: center;
		justify-content: center;
		opacity: 0;
		overflow: hidden;
		margin-left: 0;
		transform: scale(0.88);
		transition-property: width, opacity, margin-left, transform;
		transition-duration: 180ms;
	}

	.history-delete-action-show {
		width: 34px;
		opacity: 1;
		margin-left: 6px;
		transform: scale(1);
		animation-name: trashShake;
		animation-duration: 520ms;
		animation-timing-function: ease-in-out;
		animation-iteration-count: infinite;
	}

	.trash-handle {
		width: 6px;
		height: 2px;
		border-radius: 3px;
		background-color: #ffffff;
		margin-bottom: 1px;
	}

	.trash-lid {
		width: 15px;
		height: 3px;
		border-radius: 4px;
		background-color: #ffffff;
		margin-bottom: 1px;
	}

	.trash-can {
		width: 14px;
		height: 13px;
		border-radius: 4px;
		background-color: #ffffff;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		padding-left: 2px;
		padding-right: 2px;
		box-sizing: border-box;
	}

	.trash-line {
		width: 1px;
		height: 8px;
		border-radius: 2px;
		background-color: #ff3b30;
		margin-left: 1px;
		margin-right: 1px;
	}

	@keyframes trashShake {
		0% {
			transform: rotate(0deg) scale(1);
		}
		25% {
			transform: rotate(-3deg) scale(1.01);
		}
		50% {
			transform: rotate(3deg) scale(1);
		}
		75% {
			transform: rotate(-2deg) scale(1.01);
		}
		100% {
			transform: rotate(0deg) scale(1);
		}
	}

	.history-item-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 800;
		color: #111827;
	}

	.history-item-title-active {
		color: #007aff;
	}

	.history-item-time {
		margin-top: 5px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.history-title {
		font-size: 22px;
		font-weight: 800;
		color: #111827;
		margin-left: 6px;
	}

	.history-close {
		position: relative;
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #f2f4f7;
		align-items: center;
		justify-content: center;
	}

	.close-line {
		position: absolute;
		width: 14px;
		height: 2px;
		border-radius: 2px;
		background-color: #667085;
		transform: rotate(45deg);
	}

	.close-line-two {
		transform: rotate(-45deg);
	}

	.history-empty {
		margin-top: 96px;
		align-items: center;
		padding-left: 18px;
		padding-right: 18px;
	}

	.empty-dot {
		width: 52px;
		height: 52px;
		border-radius: 26px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
		margin-bottom: 16px;
	}

	.empty-dot-inner {
		width: 18px;
		height: 18px;
		border-radius: 9px;
		background-color: #c7d2fe;
	}

	.history-empty-title {
		font-size: 17px;
		font-weight: 800;
		color: #111827;
	}

	.history-empty-desc {
		margin-top: 8px;
		font-size: 13px;
		line-height: 20px;
		text-align: center;
		color: #667085;
	}

	.delete-dialog-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(15, 23, 42, 0.28);
		z-index: 80;
		align-items: center;
		justify-content: center;
		padding-left: 28px;
		padding-right: 28px;
	}

	.delete-dialog {
		width: 100%;
		border-radius: 28px;
		background-color: #ffffff;
		padding: 26px 20px 18px;
		align-items: center;
		border-width: 1px;
		border-color: rgba(255, 255, 255, 0.9);
		box-shadow: 0 24px 60px rgba(15, 23, 42, 0.2);
		animation-name: dialogPop;
		animation-duration: 180ms;
		animation-timing-function: ease-out;
	}

	@keyframes dialogPop {
		from {
			opacity: 0;
			transform: scale(0.94);
		}
		to {
			opacity: 1;
			transform: scale(1);
		}
	}

	.delete-dialog-title {
		font-size: 20px;
		line-height: 28px;
		font-weight: 900;
		color: #111827;
		text-align: center;
	}

	.delete-dialog-desc {
		width: 88%;
		margin-top: 8px;
		font-size: 14px;
		line-height: 21px;
		color: #667085;
		text-align: center;
	}

	.delete-dialog-actions {
		width: 100%;
		flex-direction: row;
		margin-top: 22px;
	}

	.delete-dialog-cancel,
	.delete-dialog-confirm {
		flex: 1;
		height: 48px;
		border-radius: 20px;
		align-items: center;
		justify-content: center;
	}

	.delete-dialog-cancel {
		background-color: #f3f6fb;
		margin-right: 10px;
	}

	.delete-dialog-confirm {
		background-color: #ff3b30;
	}

	.delete-dialog-cancel-text,
	.delete-dialog-confirm-text {
		font-size: 15px;
		font-weight: 900;
	}

	.delete-dialog-cancel-text {
		color: #344054;
	}

	.delete-dialog-confirm-text {
		color: #ffffff;
	}

	.build-form-mask {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(15, 23, 42, 0.26);
		z-index: 82;
		justify-content: flex-end;
	}

	.build-detail-mask {
		z-index: 96;
	}

	.build-form {
		border-radius: 30px;
		background-color: #ffffff;
		padding: 20px;
		padding-bottom: 28px;
		margin-left: 10px;
		margin-right: 10px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
		animation-name: buildFormIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	@keyframes buildFormIn {
		from {
			opacity: 0;
			transform: translateY(30px);
		}
		to {
			opacity: 1;
			transform: translateY(0);
		}
	}

	.build-form-head {
		height: 40px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		margin-bottom: 14px;
	}

	.build-form-title {
		font-size: 20px;
		line-height: 28px;
		font-weight: 900;
		color: #111827;
	}

	.build-form-body {
		height: 430px;
	}

	.build-form-close {
		position: relative;
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #f3f6fb;
		align-items: center;
		justify-content: center;
	}

	.build-form-input {
		height: 48px;
		border-radius: 18px;
		background-color: #f6f7fb;
		padding-left: 14px;
		padding-right: 14px;
		margin-bottom: 10px;
		font-size: 14px;
		color: #111827;
	}

	.build-form-textarea {
		height: 126px;
		border-radius: 18px;
		background-color: #f6f7fb;
		padding: 14px;
		margin-bottom: 14px;
		font-size: 14px;
		line-height: 21px;
		color: #111827;
	}

	.build-note-textarea {
		height: 82px;
	}

	.build-live-panel {
		flex-direction: row;
		align-items: center;
		background-color: #f6f9ff;
		border-radius: 22px;
		padding: 12px;
		margin-bottom: 12px;
		border-width: 1px;
		border-color: #e8f1ff;
	}

	.build-live-item {
		flex: 1;
	}

	.build-live-label,
	.build-score-label {
		font-size: 11px;
		line-height: 16px;
		color: #98a2b3;
		font-weight: 800;
	}

	.build-live-value,
	.build-score-value {
		margin-top: 2px;
		font-size: 17px;
		line-height: 23px;
		color: #111827;
		font-weight: 900;
	}

	.part-selector-list {
		margin-bottom: 10px;
	}

	.part-selector-row {
		min-height: 58px;
		border-radius: 20px;
		background-color: #f6f7fb;
		margin-bottom: 8px;
		padding-left: 14px;
		padding-right: 14px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.part-selector-row-danger {
		background-color: #fff5f5;
		border-width: 1px;
		border-color: #ffb4ab;
	}

	.part-selector-main {
		flex: 1;
		padding-right: 12px;
	}

	.part-selector-label {
		font-size: 12px;
		line-height: 17px;
		font-weight: 900;
		color: #667085;
	}

	.part-selector-value,
	.part-selector-placeholder {
		margin-top: 2px;
		font-size: 14px;
		line-height: 20px;
	}

	.part-selector-value {
		color: #111827;
		font-weight: 800;
	}

	.part-selector-placeholder {
		color: #98a2b3;
	}

	.part-selector-price {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #007aff;
	}

	.compat-list {
		background-color: #f8fafc;
		border-radius: 18px;
		padding: 12px;
		margin-bottom: 12px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.compat-list-detail {
		margin-top: 12px;
	}

	.compat-message {
		font-size: 12px;
		line-height: 18px;
		color: #667085;
		margin-bottom: 4px;
	}

	.build-form-submit {
		height: 50px;
		border-radius: 25px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.build-form-submit-text {
		font-size: 15px;
		font-weight: 900;
		color: #ffffff;
	}

	.part-picker,
	.build-detail {
		border-radius: 30px;
		background-color: #ffffff;
		padding: 20px;
		padding-bottom: 24px;
		margin-left: 10px;
		margin-right: 10px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
		animation-name: buildFormIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	.part-search-row {
		flex-direction: row;
		align-items: center;
		margin-bottom: 12px;
	}

	.part-search-input {
		flex: 1;
		height: 46px;
		border-radius: 18px;
		background-color: #f6f7fb;
		padding-left: 14px;
		padding-right: 14px;
		font-size: 14px;
		color: #111827;
	}

	.part-search-button {
		width: 64px;
		height: 46px;
		border-radius: 18px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		margin-left: 8px;
	}

	.part-search-button-text {
		font-size: 13px;
		font-weight: 900;
		color: #ffffff;
	}

	.part-option-scroll {
		height: 430px;
	}

	.part-option {
		min-height: 72px;
		border-radius: 20px;
		background-color: #f8fafc;
		padding: 13px;
		margin-bottom: 8px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.part-option-main {
		flex: 1;
		padding-right: 12px;
	}

	.part-option-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.part-option-spec {
		margin-top: 3px;
		font-size: 12px;
		line-height: 18px;
		color: #667085;
	}

	.part-option-side {
		align-items: flex-end;
	}

	.part-option-price {
		font-size: 15px;
		line-height: 21px;
		font-weight: 900;
		color: #007aff;
	}

	.part-option-score {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.part-empty {
		height: 160px;
		align-items: center;
		justify-content: center;
	}

	.part-empty-text {
		font-size: 14px;
		color: #98a2b3;
	}

	.compact-picker-mask {
		background-color: rgba(15, 23, 42, 0.32);
	}

	.compact-build-picker {
		border-radius: 28px;
		padding: 18px;
		padding-bottom: 20px;
		margin-left: 8px;
		margin-right: 8px;
		margin-bottom: 8px;
		border-width: 0;
	}

	.compact-picker-title {
		font-size: 18px;
		line-height: 25px;
	}

	.compact-picker-close {
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #111827;
	}

	.compact-picker-close-line {
		background-color: #ffffff;
	}

	.compact-picker-scroll {
		height: 156px;
	}

	.compact-build-picker .part-option {
		min-height: 62px;
		border-radius: 16px;
		background-color: #f6f7fb;
		border-width: 0;
		padding: 11px;
		margin-bottom: 10px;
	}

	.compact-build-picker .part-option-title {
		font-size: 14px;
		line-height: 20px;
	}

	.compact-build-picker .part-option-spec {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
	}

	.compact-build-picker .part-option-side {
		align-items: center;
		justify-content: center;
	}

	.build-detail-metrics {
		flex-direction: row;
		align-items: center;
		background-color: #f6f9ff;
		border-radius: 22px;
		padding: 12px;
		margin-bottom: 12px;
	}

	.build-detail-metric {
		flex: 1;
	}

	.build-detail-scroll {
		height: 430px;
	}

	.build-detail-actions {
		flex-direction: row;
		margin-top: 12px;
	}

	.detail-part-row {
		border-radius: 20px;
		background-color: #f8fafc;
		padding: 13px;
		margin-bottom: 8px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.detail-part-title {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.detail-part-meta {
		margin-top: 4px;
		font-size: 12px;
		line-height: 18px;
		color: #667085;
	}

	.detail-note {
		font-size: 13px;
		line-height: 20px;
		color: #344054;
		margin-top: 6px;
	}

	.normal-page {
		background-color: #f6f7fb;
		height: 100vh;
		overflow: hidden;
	}

	.page-head {
		margin-bottom: 18px;
		flex-shrink: 0;
	}

	.page-head-row {
		flex-direction: row;
		align-items: flex-start;
		justify-content: space-between;
	}

	.page-head-main {
		flex: 1;
		padding-right: 12px;
	}

	.page-title {
		font-size: 30px;
		line-height: 38px;
		font-weight: 800;
		color: #111827;
	}

	.page-title-inset {
		margin-left: 4px;
	}

	.page-subtitle {
		margin-top: 5px;
		font-size: 14px;
		line-height: 21px;
		color: #667085;
	}

	.segmented {
		flex-direction: row;
		background-color: #e9edf5;
		border-radius: 18px;
		padding: 4px;
		margin-bottom: 18px;
	}

	.segment,
	.segment-active {
		flex: 1;
		height: 36px;
		border-radius: 14px;
		align-items: center;
		justify-content: center;
	}

	.segment-active {
		background-color: #ffffff;
		border-width: 1px;
		border-color: #eef2f7;
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

	.section-search {
		height: 46px;
		border-radius: 23px;
		background-color: #ffffff;
		border-width: 1px;
		border-color: #edf0f5;
		padding-left: 14px;
		padding-right: 14px;
		margin-bottom: 14px;
		flex-direction: row;
		align-items: center;
		flex-shrink: 0;
	}

	.section-search-icon {
		width: 20px;
		height: 20px;
		margin-right: 8px;
		align-items: center;
		justify-content: center;
	}

	.section-search-image {
		width: 20px;
		height: 20px;
	}

	.section-search-input {
		flex: 1;
		height: 44px;
		font-size: 14px;
		color: #111827;
	}

	.section-search-placeholder {
		color: #98a2b3;
	}

	.build-scroll {
		height: 465px;
	}

	.build-list {
		padding-bottom: 18px;
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
		margin-bottom: 12px;
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

	.build-card {
		border-radius: 24px;
		background-color: #ffffff;
		padding: 16px;
		margin-bottom: 12px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.build-card-head {
		flex-direction: row;
		align-items: flex-start;
		justify-content: space-between;
	}

	.build-card-main {
		flex: 1;
		padding-right: 12px;
	}

	.build-card-delete {
		height: 32px;
		border-radius: 16px;
		background-color: #fff1f0;
		padding-left: 12px;
		padding-right: 12px;
		align-items: center;
		justify-content: center;
	}

	.build-card-delete-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #ff3b30;
	}

	.build-title {
		font-size: 17px;
		line-height: 24px;
		font-weight: 900;
		color: #111827;
	}

	.build-meta {
		margin-top: 3px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.favorite-button {
		height: 32px;
		border-radius: 16px;
		background-color: #f3f6fb;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		padding-left: 12px;
		padding-right: 12px;
	}

	.favorite-button-active {
		background-color: #fff1f0;
	}

	.favorite-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #344054;
	}

	.favorite-icon {
		font-size: 13px;
		line-height: 17px;
		font-weight: 900;
		color: #98a2b3;
		margin-right: 4px;
	}

	.favorite-icon-active {
		color: #ffb800;
	}

	.build-action-icon.favorite-icon-active {
		color: #ffb800;
	}

	.favorite-text-active {
		color: #ffb800;
	}

	.build-score-row {
		flex-direction: row;
		align-items: center;
		margin-top: 12px;
	}

	.build-score-pill {
		flex: 1;
		min-height: 54px;
		border-radius: 18px;
		background-color: #f6f9ff;
		padding: 9px 12px;
		margin-right: 8px;
	}

	.build-status {
		height: 34px;
		border-radius: 17px;
		align-items: center;
		justify-content: center;
		padding-left: 12px;
		padding-right: 12px;
	}

	.build-status-pass {
		background-color: #eafaf0;
	}

	.build-status-warn {
		background-color: #fff7e8;
	}

	.build-status-block {
		background-color: #fff1f0;
	}

	.build-status-text {
		font-size: 12px;
		line-height: 16px;
		font-weight: 900;
		color: #111827;
	}

	.build-tags {
		flex-direction: row;
		margin-top: 12px;
	}

	.build-tag {
		height: 28px;
		border-radius: 14px;
		background-color: #eef5ff;
		align-items: center;
		justify-content: center;
		padding-left: 10px;
		padding-right: 10px;
		margin-right: 8px;
	}

	.build-tag-text {
		font-size: 12px;
		font-weight: 800;
		color: #007aff;
	}

	.build-summary {
		margin-top: 12px;
		font-size: 14px;
		line-height: 21px;
		font-weight: 800;
		color: #344054;
	}

	.build-content {
		margin-top: 8px;
		font-size: 13px;
		line-height: 21px;
		color: #667085;
	}

	.build-part-preview {
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 10px;
	}

	.build-part-chip {
		font-size: 12px;
		line-height: 18px;
		color: #667085;
		background-color: #f3f6fb;
		border-radius: 12px;
		padding: 4px 8px;
		margin-right: 6px;
		margin-bottom: 6px;
	}

	.build-actions {
		flex-direction: row;
		margin-top: 14px;
	}

	.build-action {
		width: 70px;
		height: 32px;
		border-radius: 16px;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		margin-right: 8px;
	}

	.build-action-soft {
		background-color: #f3f6fb;
	}

	.build-action-danger {
		background-color: #fff1f0;
	}

	.build-action-favorite {
		background-color: #f3f6fb;
	}

	.build-action-favorite-active {
		background-color: #fff8e6;
	}

	.build-action-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #344054;
	}

	.build-action-icon {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #344054;
		margin-right: 4px;
	}

	.build-action-danger-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #ff3b30;
	}

	.empty-state {
		min-height: 430px;
		border-radius: 30px;
		background-color: #ffffff;
		align-items: center;
		justify-content: center;
		padding-left: 24px;
		padding-right: 24px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.empty-state-soft {
		box-shadow: none;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.empty-illustration {
		width: 76px;
		height: 76px;
		border-radius: 26px;
		background-color: #f2f6ff;
		align-items: center;
		justify-content: center;
		margin-bottom: 20px;
	}

	.empty-grid-row {
		flex-direction: row;
	}

	.empty-grid-cell {
		width: 18px;
		height: 18px;
		border-radius: 7px;
		background-color: #d7e4ff;
		margin: 3px;
	}

	.empty-grid-cell-accent {
		background-color: #34c759;
	}

	.empty-title {
		font-size: 20px;
		line-height: 28px;
		font-weight: 800;
		color: #111827;
		text-align: center;
	}

	.empty-desc {
		margin-top: 8px;
		font-size: 14px;
		line-height: 22px;
		color: #667085;
		text-align: center;
	}

	.action-stack {
		width: 100%;
		margin-top: 24px;
	}

	.primary-action,
	.secondary-action {
		height: 48px;
		border-radius: 24px;
		align-items: center;
		justify-content: center;
		margin-bottom: 10px;
	}

	.primary-action {
		background-color: #111827;
	}

	.secondary-action {
		background-color: #eef5ff;
	}

	.primary-action-text {
		font-size: 15px;
		font-weight: 800;
		color: #ffffff;
	}

	.secondary-action-text {
		font-size: 15px;
		font-weight: 800;
		color: #007aff;
	}

	.small-primary {
		height: 38px;
		padding-left: 16px;
		padding-right: 16px;
		border-radius: 19px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.small-primary-text {
		font-size: 14px;
		font-weight: 800;
		color: #ffffff;
	}

	.compose-box {
		border-radius: 26px;
		background-color: #ffffff;
		padding: 16px;
		margin-bottom: 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.compose-placeholder {
		font-size: 15px;
		line-height: 22px;
		color: #98a2b3;
	}

	.compose-tools {
		flex-direction: row;
		margin-top: 14px;
	}

	.tool-pill {
		height: 34px;
		border-radius: 17px;
		background-color: #f3f6fb;
		flex-direction: row;
		align-items: center;
		padding-left: 12px;
		padding-right: 12px;
		margin-right: 10px;
	}

	.tool-image-icon {
		position: relative;
		width: 16px;
		height: 14px;
		border-radius: 4px;
		border-width: 2px;
		border-color: #007aff;
		margin-right: 6px;
		overflow: hidden;
	}

	.tool-image-sun {
		position: absolute;
		right: 2px;
		top: 2px;
		width: 4px;
		height: 4px;
		border-radius: 2px;
		background-color: #007aff;
	}

	.tool-image-hill {
		position: absolute;
		left: 2px;
		bottom: -1px;
		width: 12px;
		height: 7px;
		border-radius: 7px;
		background-color: #007aff;
	}

	.tool-build-icon {
		width: 15px;
		height: 15px;
		flex-direction: row;
		margin-right: 6px;
	}

	.tool-build-dot {
		width: 6px;
		height: 15px;
		border-radius: 3px;
		background-color: #34c759;
		margin-right: 3px;
	}

	.tool-pill-text {
		font-size: 13px;
		font-weight: 700;
		color: #344054;
	}

	.community-scroll {
		height: 590px;
		flex-shrink: 1;
	}

	.community-list {
		padding-bottom: 22px;
	}

	.post-card {
		border-radius: 28px;
		background-color: #ffffff;
		padding: 18px;
		margin-bottom: 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.post-head {
		flex-direction: row;
		align-items: center;
	}

	.post-avatar {
		width: 42px;
		height: 42px;
		border-radius: 21px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		margin-right: 10px;
		overflow: hidden;
	}

	.post-avatar-image {
		width: 42px;
		height: 42px;
		border-radius: 21px;
	}

	.post-author-main {
		flex: 1;
	}

	.post-author-name {
		font-size: 14px;
		line-height: 20px;
		font-weight: 900;
		color: #111827;
	}

	.post-time {
		margin-top: 2px;
		font-size: 12px;
		line-height: 17px;
		color: #98a2b3;
	}

	.post-content {
		margin-top: 14px;
		font-size: 15px;
		line-height: 23px;
		color: #111827;
	}

	.post-image-grid,
	.form-image-grid {
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

	.post-build-card,
	.post-linked-build {
		min-height: 70px;
		border-radius: 22px;
		background-color: #f6f9ff;
		border-width: 1px;
		border-color: #dbeafe;
		margin-top: 12px;
		padding: 12px;
		flex-direction: row;
		align-items: center;
	}

	.post-actions {
		flex-direction: row;
		align-items: center;
		margin-top: 14px;
	}

	.post-action {
		width: 52px;
		height: 32px;
		border-radius: 16px;
		background-color: #f6f7fb;
		margin-right: 8px;
		flex-direction: row;
		align-items: center;
		justify-content: center;
	}

	.post-action-icon {
		font-size: 13px;
		line-height: 18px;
		color: #98a2b3;
		margin-right: 4px;
	}

	.post-action-icon-active {
		color: #ff3b30;
	}

	.post-favorite-icon-active {
		color: #007aff;
	}

	.post-action-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #667085;
	}

	.post-head-delete {
		height: 34px;
		border-radius: 17px;
		background-color: #fff1f0;
		padding-left: 11px;
		padding-right: 11px;
		align-items: center;
		justify-content: center;
	}

	.post-head-delete-text {
		font-size: 12px;
		line-height: 17px;
		font-weight: 800;
		color: #ff3b30;
	}

	.post-form,
	.post-detail {
		border-radius: 30px;
		background-color: #ffffff;
		padding: 20px;
		padding-bottom: 22px;
		margin-left: 10px;
		margin-right: 10px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
		animation-name: buildFormIn;
		animation-duration: 220ms;
		animation-timing-function: ease-out;
	}

	.post-form-textarea {
		height: 150px;
		border-radius: 22px;
		background-color: #f6f7fb;
		padding: 16px;
		font-size: 15px;
		line-height: 22px;
		color: #111827;
	}

	.post-form-tools {
		flex-direction: row;
		margin-top: 14px;
		margin-bottom: 12px;
	}

	.form-image-wrap {
		position: relative;
		width: 88px;
		height: 88px;
		border-radius: 18px;
		margin-right: 8px;
		margin-bottom: 8px;
		overflow: hidden;
	}

	.form-image {
		position: absolute;
		left: 0;
		top: 0;
		width: 88px;
		height: 88px;
		border-radius: 18px;
		background-color: #f3f6fb;
	}

	.form-image-wrap-sorting {
		animation-name: imageShake;
		animation-duration: 150ms;
		animation-iteration-count: infinite;
		animation-timing-function: ease-in-out;
	}

	.form-image-wrap-dragging {
		opacity: 0.86;
		z-index: 20;
	}

	@keyframes imageShake {
		0% { transform: rotate(-1deg); }
		50% { transform: rotate(1deg); }
		100% { transform: rotate(-1deg); }
	}

	.form-image-remove {
		position: absolute;
		right: 5px;
		top: 5px;
		width: 24px;
		height: 24px;
		border-radius: 12px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
		z-index: 3;
	}

	.form-image-order {
		position: absolute;
		left: 6px;
		right: 6px;
		bottom: 6px;
		height: 24px;
		border-radius: 12px;
		background-color: rgba(17, 24, 39, 0.72);
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		padding-left: 9px;
		padding-right: 9px;
	}

	.form-image-order-text {
		font-size: 20px;
		line-height: 22px;
		font-weight: 900;
		color: #ffffff;
	}

	.form-image-order-disabled {
		color: rgba(255, 255, 255, 0.32);
	}

	.comment-action-icon {
		position: relative;
		width: 18px;
		height: 14px;
		border-radius: 8px;
		background-color: #8b95a5;
		flex-direction: row;
		align-items: center;
		justify-content: center;
		margin-right: 6px;
	}

	.comment-action-dot {
		width: 3px;
		height: 3px;
		border-radius: 2px;
		background-color: #ffffff;
		margin-left: 1px;
		margin-right: 1px;
	}

	.comment-action-tail {
		position: absolute;
		width: 6px;
		height: 6px;
		border-radius: 2px;
		background-color: #8b95a5;
		left: 3px;
		bottom: -3px;
		transform: rotate(45deg);
	}

	.close-line-small {
		width: 11px;
		background-color: #ffffff;
	}

	.plus-circle-button {
		width: 34px;
		height: 34px;
		border-radius: 17px;
		background-color: #111827;
		align-items: center;
		justify-content: center;
	}

	.plus-circle-button-selected {
		background-color: #eff4ff;
		border-width: 1px;
		border-color: #d7e4ff;
	}

	.plus-circle-text {
		font-size: 22px;
		line-height: 26px;
		font-weight: 600;
		color: #ffffff;
	}

	.plus-circle-text-selected {
		color: #0b1220;
	}

	.inline-image-preview {
		position: fixed;
		left: 0;
		right: 0;
		top: 0;
		bottom: 0;
		background-color: rgba(5, 7, 13, 0.84);
		z-index: 140;
	}

	.inline-preview-top {
		position: fixed;
		left: 18px;
		right: 18px;
		top: 32px;
		width: auto;
		max-width: none;
		height: 42px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		z-index: 142;
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

	.inline-preview-placeholder {
		background-color: transparent;
	}

	.back-chevron {
		position: relative;
		width: 16px;
		height: 16px;
	}

	.back-chevron-line {
		position: absolute;
		left: 2px;
		width: 10px;
		height: 2px;
		border-radius: 2px;
		background-color: #ffffff;
	}

	.inline-preview-chevron-line {
		background-color: #ffffff;
	}

	.back-chevron-line-top {
		top: 4px;
		transform: rotate(-45deg);
	}

	.back-chevron-line-bottom {
		bottom: 4px;
		transform: rotate(45deg);
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
		width: auto;
		max-width: none;
		flex-direction: row;
		justify-content: space-between;
		z-index: 142;
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
		top: 46px;
		width: auto;
		max-width: none;
		height: 104px;
		background-color: transparent;
		z-index: 150;
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

	.post-detail-scroll {
		height: 470px;
	}

	.post-detail-content {
		margin-top: 16px;
	}

	.post-detail-actions {
		margin-bottom: 16px;
	}

	.comment-list {
		margin-top: 10px;
	}

	.comment-title {
		font-size: 16px;
		line-height: 22px;
		font-weight: 900;
		color: #111827;
		margin-bottom: 10px;
	}

	.comment-card {
		border-radius: 22px;
		background-color: #f8fafc;
		padding: 13px;
		margin-bottom: 10px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.comment-head {
		margin-bottom: 8px;
	}

	.comment-avatar {
		width: 32px;
		height: 32px;
		border-radius: 16px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		margin-right: 8px;
		overflow: hidden;
	}

	.comment-avatar-image {
		width: 32px;
		height: 32px;
		border-radius: 16px;
	}

	.comment-like {
		margin-right: 0;
	}

	.comment-content {
		font-size: 14px;
		line-height: 21px;
		color: #111827;
	}

	.comment-image-grid {
		flex-direction: row;
		flex-wrap: wrap;
		margin-top: 8px;
	}

	.comment-image {
		width: 74px;
		height: 74px;
		border-radius: 16px;
		margin-right: 7px;
		margin-bottom: 7px;
		background-color: #f3f6fb;
	}

	.comment-draft-images {
		flex-direction: row;
		padding-top: 8px;
		padding-bottom: 8px;
	}

	.comment-draft-image-wrap {
		position: relative;
		width: 52px;
		height: 52px;
		margin-right: 8px;
	}

	.comment-draft-image {
		width: 52px;
		height: 52px;
		border-radius: 14px;
		background-color: #f3f6fb;
	}

	.comment-image-remove {
		width: 20px;
		height: 20px;
		border-radius: 10px;
	}

	.comment-composer {
		min-height: 48px;
		border-radius: 24px;
		background-color: #f6f7fb;
		flex-direction: row;
		align-items: center;
		padding: 5px;
		margin-top: 8px;
	}

	.comment-image-button {
		width: 38px;
		height: 38px;
		border-radius: 19px;
		background-color: #ffffff;
		align-items: center;
		justify-content: center;
		margin-right: 4px;
	}

	.comment-input {
		flex: 1;
		height: 38px;
		font-size: 14px;
		color: #111827;
		padding-left: 10px;
		padding-right: 10px;
	}

	.comment-send {
		height: 38px;
		border-radius: 19px;
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

	.empty-forum {
		background-color: #f0fdf4;
	}

	.forum-bubble {
		width: 38px;
		height: 24px;
		border-radius: 13px;
		background-color: #34c759;
	}

	.forum-bubble-small {
		width: 24px;
		height: 16px;
		background-color: #9ae6b4;
		margin-top: -5px;
		margin-left: 20px;
	}

	.empty-news {
		background-color: #fff7ed;
	}

	.news-line {
		width: 34px;
		height: 5px;
		border-radius: 3px;
		background-color: #ff9500;
		margin-bottom: 6px;
	}

	.news-line-long {
		width: 42px;
	}

	.news-line-short {
		width: 24px;
		margin-bottom: 0;
	}

	.profile-card {
		border-radius: 30px;
		background-color: #ffffff;
		padding: 20px;
		flex-direction: row;
		align-items: center;
		margin-bottom: 14px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.profile-avatar {
		position: relative;
		width: 64px;
		height: 64px;
		border-radius: 32px;
		background-color: #eef2f7;
		align-items: center;
		justify-content: center;
		margin-right: 14px;
		overflow: hidden;
	}

	.profile-avatar-image {
		position: absolute;
		width: 64px;
		height: 64px;
		border-radius: 32px;
		left: 0;
		top: 0;
		z-index: 2;
	}

	.profile-copy {
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

	.avatar-placeholder-large .avatar-placeholder-head {
		margin-bottom: 4px;
	}

	.avatar-placeholder-small .avatar-placeholder-head {
		margin-bottom: 2px;
	}

	.profile-title {
		font-size: 22px;
		line-height: 29px;
		font-weight: 800;
		color: #111827;
	}

	.profile-desc {
		margin-top: 4px;
		font-size: 13px;
		line-height: 20px;
		color: #667085;
	}

	.profile-email {
		margin-top: 4px;
		font-size: 12px;
		line-height: 18px;
		color: #98a2b3;
	}

	.zero-stats {
		flex-direction: row;
		margin-bottom: 14px;
	}

	.zero-stat {
		flex: 1;
		border-radius: 22px;
		background-color: #ffffff;
		padding-top: 16px;
		padding-bottom: 16px;
		align-items: center;
		margin-right: 9px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.zero-stat-last {
		margin-right: 0;
	}

	.zero-number {
		font-size: 22px;
		font-weight: 800;
		color: #111827;
	}

	.zero-label {
		margin-top: 4px;
		font-size: 12px;
		color: #667085;
	}

	.menu-list {
		border-radius: 26px;
		background-color: #ffffff;
		padding: 6px;
		border-width: 1px;
		border-color: #edf0f5;
	}

	.menu-item {
		height: 54px;
		border-radius: 20px;
		padding-left: 14px;
		padding-right: 12px;
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
	}

	.menu-item-pressed {
		background-color: #fff1f0;
		transform: scale(0.98);
		transition-property: background-color, transform;
		transition-duration: 160ms;
	}

	.menu-text {
		font-size: 15px;
		font-weight: 700;
		color: #111827;
	}

	.menu-text-danger {
		color: #ff3b30;
	}

	.menu-arrow {
		font-size: 25px;
		color: #98a2b3;
	}

	.tabbar {
		display: flex;
		position: fixed;
		left: 18px;
		right: 18px;
		bottom: 14px;
		width: auto;
		max-width: none;
		height: 72px;
		border-radius: 27px;
		background-color: rgba(255, 255, 255, 0.96);
		flex-direction: row;
		align-items: center;
		justify-content: space-between;
		padding-left: 8px;
		padding-right: 8px;
		box-shadow: 0 10px 26px rgba(15, 23, 42, 0.1);
		border-width: 1px;
		border-color: rgba(255, 255, 255, 0.9);
		z-index: 20;
	}

	.tab-item {
		flex: 1;
		height: 58px;
		border-radius: 22px;
		align-items: center;
		justify-content: center;
		transition-property: background-color, transform;
		transition-duration: 180ms;
	}

	.tab-item-active {
		background-color: #eef5ff;
		transform: scale(1.03);
	}

	.tab-glyph {
		position: relative;
		width: 28px;
		height: 26px;
		align-items: center;
		justify-content: center;
		margin-bottom: 2px;
	}

	.tab-label {
		font-size: 11px;
		line-height: 14px;
		font-weight: 700;
		color: #8a94a6;
	}

	.tab-label-active {
		color: #007aff;
	}

	.ai-tab-glyph {
		border-radius: 10px;
	}

	.ai-tab-text {
		font-size: 13px;
		line-height: 18px;
		font-weight: 900;
		color: #8a94a6;
	}

	.ai-tab-text-active {
		color: #007aff;
	}

	.grid-icon-row {
		flex-direction: row;
	}

	.grid-icon-cell {
		width: 8px;
		height: 8px;
		border-radius: 3px;
		background-color: #8a94a6;
		margin: 2px;
	}

	.grid-icon-cell-active {
		background-color: #007aff;
	}

	.discover-circle {
		width: 19px;
		height: 19px;
		border-radius: 10px;
		border-width: 2px;
		border-color: #8a94a6;
	}

	.discover-circle-active {
		border-color: #007aff;
	}

	.discover-needle {
		position: absolute;
		width: 2px;
		height: 12px;
		border-radius: 2px;
		background-color: #8a94a6;
		transform: rotate(40deg);
	}

	.discover-needle-active {
		background-color: #007aff;
	}

	.news-icon-paper {
		width: 20px;
		height: 21px;
		border-radius: 6px;
		border-width: 2px;
		border-color: #8a94a6;
		align-items: center;
		justify-content: center;
	}

	.news-icon-paper-active {
		border-color: #007aff;
	}

	.news-icon-line {
		width: 10px;
		height: 2px;
		border-radius: 2px;
		background-color: #8a94a6;
		margin-top: 3px;
	}

	.news-icon-line-active {
		background-color: #007aff;
	}

	.news-icon-line-long {
		width: 13px;
		margin-top: 0;
	}

	.mine-icon-head {
		width: 10px;
		height: 10px;
		border-radius: 5px;
		background-color: #8a94a6;
		margin-bottom: 3px;
	}

	.mine-icon-head-active {
		background-color: #007aff;
	}

	.mine-icon-body {
		width: 19px;
		height: 9px;
		border-radius: 8px;
		background-color: #8a94a6;
	}

	.mine-icon-body-active {
		background-color: #007aff;
	}

	.tab-item-active .discover-circle,
	.tab-item-active .news-icon-paper {
		border-color: #007aff;
	}

	.tab-item-active .grid-icon-cell,
	.tab-item-active .discover-needle,
	.tab-item-active .news-icon-line,
	.tab-item-active .mine-icon-head,
	.tab-item-active .mine-icon-body {
		background-color: #007aff;
	}
</style>

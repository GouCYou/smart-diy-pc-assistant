<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

type ApiResponse<T> = {
  code: number
  message: string
  data: T
}

type LoginResponse = {
  userId: number
  username: string
  role: string
  token: string
}

type NewsItem = {
  id?: number
  title: string
  summary: string
  content: string
  coverUrl: string
  status: 'DRAFT' | 'PUBLISHED'
}

type HardwarePartType = 'CPU' | 'MOTHERBOARD' | 'GPU' | 'MEMORY' | 'STORAGE' | 'PSU' | 'CASE' | 'COOLER'

type HardwarePart = {
  id?: number
  type: HardwarePartType
  brand: string
  model: string
  price: number
  performanceScore: number
  socketType: string
  memoryType: string
  formFactor: string
  wattage: number
  gpuLengthMm: number
  coolerHeightMm: number
  supportedFormFactors: string
  specs: string
  enabled: boolean
}

type AuthorItem = {
  id: number
  username: string
  nickname: string
  avatarUrl: string
}

type CommunityPostItem = {
  id: number
  author: AuthorItem
  content: string
  imageUrls: string[]
  buildConfig: { id: number; title: string; totalPrice: number; performanceScore: number } | null
  likeCount: number
  commentCount: number
  favoriteCount: number
  liked: boolean
  favorite: boolean
  createdAt: string
  comments: CommunityCommentItem[]
}

type CommunityCommentItem = {
  id: number
  postId: number
  author: AuthorItem
  content: string
  imageUrls: string[]
  likeCount: number
  liked: boolean
  createdAt: string
}

const menuItems = [
  { key: 'dashboard', label: '仪表盘', icon: '⌘' },
  { key: 'components', label: '配件库', icon: '▦' },
  { key: 'news', label: '新闻热点', icon: '≡' },
  { key: 'community', label: '社区内容', icon: '✦' },
  { key: 'ai', label: 'AI 设置', icon: 'AI' },
  { key: 'settings', label: '系统设置', icon: '⚙' },
]

const loggedIn = ref(false)
const activeMenu = ref('dashboard')
const loading = ref(false)
const savingNews = ref(false)
const savingHardware = ref(false)
const errorMessage = ref('')
const username = ref('')
const password = ref('')
const currentUser = ref('')
const token = ref('')
const loginPanelRef = ref<HTMLElement | null>(null)
const isLoginPanelHovered = ref(false)
const isLoginPanelFocused = ref(false)
const newsList = ref<NewsItem[]>([])
const hardwareList = ref<HardwarePart[]>([])
const hardwareKeyword = ref('')
const hardwareTypeFilter = ref('ALL')
const hardwareStatusFilter = ref('ALL')
const hardwarePage = ref(1)
const hardwarePageSize = ref(12)
const communityPosts = ref<CommunityPostItem[]>([])
const communityComments = ref<CommunityCommentItem[]>([])
const selectedCommunityPostId = ref<number | null>(null)
const newsForm = reactive<NewsItem>({
  title: '',
  summary: '',
  content: '',
  coverUrl: '',
  status: 'DRAFT',
})
const hardwareForm = reactive<HardwarePart>({
  type: 'CPU',
  brand: '',
  model: '',
  price: 0,
  performanceScore: 0,
  socketType: '',
  memoryType: '',
  formFactor: '',
  wattage: 0,
  gpuLengthMm: 0,
  coolerHeightMm: 0,
  supportedFormFactors: '',
  specs: '',
  enabled: true,
})
const editingHardwareId = ref<number | null>(null)
const hardwareTypeOptions: Array<{ label: string; value: HardwarePartType }> = [
  { label: 'CPU', value: 'CPU' },
  { label: '主板', value: 'MOTHERBOARD' },
  { label: '显卡', value: 'GPU' },
  { label: '内存', value: 'MEMORY' },
  { label: '硬盘', value: 'STORAGE' },
  { label: '电源', value: 'PSU' },
  { label: '机箱', value: 'CASE' },
  { label: '散热', value: 'COOLER' },
]

const currentMenuTitle = computed(() => {
  return menuItems.find((item) => item.key === activeMenu.value)?.label ?? '仪表盘'
})

const isLoginEngaged = computed(() => isLoginPanelHovered.value || isLoginPanelFocused.value)

const dashboardCards = computed(() => [
  { label: '配件总数', value: hardwareList.value.length, hint: '已接入硬件库' },
  { label: '启用配件', value: hardwareList.value.filter((item) => item.enabled).length, hint: '当前可被用户选择' },
  { label: '社区帖子', value: communityPosts.value.length, hint: '发现页内容管理' },
  { label: '新闻热点', value: newsList.value.length, hint: '已接入基础接口' },
])

const hardwareTypeStats = computed(() => {
  const total = Math.max(hardwareList.value.length, 1)
  return hardwareTypeOptions.map((option) => {
    const count = hardwareList.value.filter((item) => item.type === option.value).length
    return {
      label: option.label,
      count,
      percent: Math.round((count / total) * 100),
    }
  })
})

const activityBars = computed(() => [
  { label: '帖子', value: communityPosts.value.length },
  { label: '评论', value: communityPosts.value.reduce((sum, item) => sum + item.commentCount, 0) },
  { label: '收藏', value: communityPosts.value.reduce((sum, item) => sum + item.favoriteCount, 0) },
  { label: '新闻', value: newsList.value.length },
])

const maxActivityValue = computed(() => {
  return Math.max(...activityBars.value.map((item) => item.value), 1)
})

const filteredHardwareList = computed(() => {
  const keyword = hardwareKeyword.value.trim().toLowerCase()
  return hardwareList.value.filter((item) => {
    const matchType = hardwareTypeFilter.value === 'ALL' || item.type === hardwareTypeFilter.value
    const matchStatus =
      hardwareStatusFilter.value === 'ALL' ||
      (hardwareStatusFilter.value === 'ENABLED' && item.enabled) ||
      (hardwareStatusFilter.value === 'DISABLED' && !item.enabled)
    const searchable = `${hardwareTypeLabel(item.type)} ${item.brand} ${item.model} ${item.socketType} ${item.memoryType} ${item.formFactor} ${item.specs}`.toLowerCase()
    return matchType && matchStatus && (keyword.length === 0 || searchable.includes(keyword))
  })
})

const hardwareTotalPages = computed(() => {
  return Math.max(1, Math.ceil(filteredHardwareList.value.length / hardwarePageSize.value))
})

const pagedHardwareList = computed(() => {
  const current = Math.min(hardwarePage.value, hardwareTotalPages.value)
  const start = (current - 1) * hardwarePageSize.value
  return filteredHardwareList.value.slice(start, start + hardwarePageSize.value)
})

function authHeaders() {
  return {
    Authorization: `Bearer ${token.value}`,
    'Content-Type': 'application/json',
  }
}

async function request<T>(url: string, options: RequestInit = {}) {
  const response = await fetch(url, options)
  const payload = (await response.json()) as ApiResponse<T>
  if (!response.ok || payload.code !== 0) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data
}

async function handleLogin() {
  errorMessage.value = ''
  loading.value = true
  try {
    const data = await request<LoginResponse>('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: username.value,
        password: password.value,
      }),
    })
    token.value = data.token
    currentUser.value = data.username
    localStorage.setItem('admin_token', data.token)
    localStorage.setItem('admin_user', data.username)
    loggedIn.value = true
    await Promise.all([loadNews(), loadHardware(), loadCommunityPosts()])
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败'
  } finally {
    loading.value = false
  }
}

function logout() {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_user')
  token.value = ''
  loggedIn.value = false
  activeMenu.value = 'dashboard'
}

async function loadNews() {
  if (!token.value) {
    return
  }
  newsList.value = await request<NewsItem[]>('/api/admin/news', {
    headers: authHeaders(),
  })
}

async function loadHardware() {
  if (!token.value) {
    return
  }
  hardwareList.value = await request<HardwarePart[]>('/api/admin/hardware-parts', {
    headers: authHeaders(),
  })
}

async function loadCommunityPosts() {
  if (!token.value) {
    return
  }
  communityPosts.value = await request<CommunityPostItem[]>('/api/admin/community/posts', {
    headers: authHeaders(),
  })
  if (selectedCommunityPostId.value) {
    const exists = communityPosts.value.some((item) => item.id === selectedCommunityPostId.value)
    if (exists) {
      await loadCommunityComments(selectedCommunityPostId.value)
    } else {
      selectedCommunityPostId.value = null
      communityComments.value = []
    }
  }
}

async function loadCommunityComments(postId: number) {
  selectedCommunityPostId.value = postId
  communityComments.value = await request<CommunityCommentItem[]>(`/api/admin/community/posts/${postId}/comments`, {
    headers: authHeaders(),
  })
}

function resetHardwareForm() {
  editingHardwareId.value = null
  hardwareForm.type = 'CPU'
  hardwareForm.brand = ''
  hardwareForm.model = ''
  hardwareForm.price = 0
  hardwareForm.performanceScore = 0
  hardwareForm.socketType = ''
  hardwareForm.memoryType = ''
  hardwareForm.formFactor = ''
  hardwareForm.wattage = 0
  hardwareForm.gpuLengthMm = 0
  hardwareForm.coolerHeightMm = 0
  hardwareForm.supportedFormFactors = ''
  hardwareForm.specs = ''
  hardwareForm.enabled = true
}

function editHardware(item: HardwarePart) {
  editingHardwareId.value = item.id ?? null
  hardwareForm.type = item.type
  hardwareForm.brand = item.brand
  hardwareForm.model = item.model
  hardwareForm.price = item.price
  hardwareForm.performanceScore = item.performanceScore
  hardwareForm.socketType = item.socketType
  hardwareForm.memoryType = item.memoryType
  hardwareForm.formFactor = item.formFactor
  hardwareForm.wattage = item.wattage
  hardwareForm.gpuLengthMm = item.gpuLengthMm
  hardwareForm.coolerHeightMm = item.coolerHeightMm
  hardwareForm.supportedFormFactors = item.supportedFormFactors
  hardwareForm.specs = item.specs
  hardwareForm.enabled = item.enabled
}

async function saveHardware() {
  errorMessage.value = ''
  savingHardware.value = true
  try {
    const url = editingHardwareId.value ? `/api/admin/hardware-parts/${editingHardwareId.value}` : '/api/admin/hardware-parts'
    await request<HardwarePart>(url, {
      method: editingHardwareId.value ? 'PUT' : 'POST',
      headers: authHeaders(),
      body: JSON.stringify(hardwareForm),
    })
    resetHardwareForm()
    await loadHardware()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    savingHardware.value = false
  }
}

async function deleteHardware(id?: number) {
  if (!id || !window.confirm('确定删除这个配件？')) {
    return
  }
  try {
    await request<void>(`/api/admin/hardware-parts/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    await loadHardware()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败'
  }
}

async function deleteCommunityPost(id: number) {
  if (!window.confirm('确定删除这篇帖子？相关评论也会一起删除。')) {
    return
  }
  try {
    await request<void>(`/api/admin/community/posts/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    if (selectedCommunityPostId.value === id) {
      selectedCommunityPostId.value = null
      communityComments.value = []
    }
    await loadCommunityPosts()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败'
  }
}

async function deleteCommunityComment(id: number) {
  if (!window.confirm('确定删除这条评论？')) {
    return
  }
  try {
    await request<void>(`/api/admin/community/comments/${id}`, {
      method: 'DELETE',
      headers: authHeaders(),
    })
    if (selectedCommunityPostId.value) {
      await loadCommunityComments(selectedCommunityPostId.value)
      await loadCommunityPosts()
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '删除失败'
  }
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function handleLoginPanelFocusOut() {
  requestAnimationFrame(() => {
    const activeElement = document.activeElement
    isLoginPanelFocused.value = !!(
      loginPanelRef.value &&
      activeElement &&
      loginPanelRef.value.contains(activeElement)
    )
  })
}

function hardwareTypeLabel(type: HardwarePartType) {
  return hardwareTypeOptions.find((item) => item.value === type)?.label ?? type
}

function resetHardwarePage() {
  hardwarePage.value = 1
}

function previousHardwarePage() {
  hardwarePage.value = Math.max(1, hardwarePage.value - 1)
}

function nextHardwarePage() {
  hardwarePage.value = Math.min(hardwareTotalPages.value, hardwarePage.value + 1)
}

async function saveNews() {
  errorMessage.value = ''
  savingNews.value = true
  try {
    await request<NewsItem>('/api/admin/news', {
      method: 'POST',
      headers: authHeaders(),
      body: JSON.stringify(newsForm),
    })
    newsForm.title = ''
    newsForm.summary = ''
    newsForm.content = ''
    newsForm.coverUrl = ''
    newsForm.status = 'DRAFT'
    await loadNews()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    savingNews.value = false
  }
}

function switchMenu(key: string) {
  activeMenu.value = key
  if (key === 'news') {
    loadNews()
  }
  if (key === 'components') {
    loadHardware()
  }
  if (key === 'community') {
    loadCommunityPosts()
  }
}

onMounted(() => {
  const savedToken = localStorage.getItem('admin_token')
  const savedUser = localStorage.getItem('admin_user')
  if (savedToken) {
    token.value = savedToken
    currentUser.value = savedUser || 'root'
    loggedIn.value = true
    Promise.all([loadNews(), loadHardware(), loadCommunityPosts()]).catch(() => logout())
  }
})
</script>

<template>
  <main v-if="!loggedIn" :class="isLoginEngaged ? 'login-page engaged' : 'login-page'">
    <section class="login-hero">
      <div class="hero-card">
        <div class="hero-badge">Smart DIY Admin</div>
        <h1>智能DIY助手</h1>
        <p>统一管理硬件资讯、配置方案、社区内容和 AI 能力。</p>
        <div class="hero-metrics">
          <span>Vue3</span>
          <span>Spring Boot</span>
          <span>MySQL / Redis</span>
        </div>
      </div>
    </section>

    <section
      ref="loginPanelRef"
      class="login-panel"
      @mouseenter="isLoginPanelHovered = true"
      @mouseleave="isLoginPanelHovered = false"
      @focusin="isLoginPanelFocused = true"
      @focusout="handleLoginPanelFocusOut"
    >
      <form class="login-form" autocomplete="off" @submit.prevent="handleLogin">
        <div>
          <p class="eyebrow">管理后台</p>
          <h2>登录系统</h2>
          <span>请输入管理员账号与密码。</span>
        </div>

        <label>
          管理员账号
          <input v-model="username" autocomplete="off" placeholder="请输入账号" />
        </label>

        <label>
          登录密码
          <input v-model="password" autocomplete="off" placeholder="请输入密码" type="password" />
        </label>

        <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
        <button class="primary-button" :disabled="loading" type="submit">
          {{ loading ? '登录中...' : '进入后台' }}
        </button>
      </form>
    </section>
  </main>

  <main v-else class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">DIY</div>
        <div>
          <h1>智能DIY助手</h1>
          <p>后台管理系统</p>
        </div>
      </div>

      <nav class="menu">
        <button
          v-for="item in menuItems"
          :key="item.key"
          :class="activeMenu === item.key ? 'menu-item active' : 'menu-item'"
          @click="switchMenu(item.key)"
        >
          <span class="menu-icon">{{ item.icon }}</span>
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <div>
          <h2>{{ currentMenuTitle }}</h2>
        </div>
        <div class="user-pill">
          <span>{{ currentUser }}</span>
          <button @click="logout">退出</button>
        </div>
      </header>

      <section v-if="activeMenu === 'dashboard'" class="content-area">
        <div class="stats-grid">
          <article v-for="card in dashboardCards" :key="card.label" class="stat-card">
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <p>{{ card.hint }}</p>
          </article>
        </div>
        <div class="wide-card">
          <h3>建设进度</h3>
          <div class="progress-list">
            <p>后端基础：JWT、MySQL、Redis、七牛云、LangChain4j 接口边界</p>
            <p>用户端：五栏主界面和对话交互原型</p>
            <p>管理端：后台壳、登录页、新闻热点管理入口</p>
          </div>
        </div>
        <div class="dashboard-charts">
          <section class="chart-card">
            <div class="chart-head">
              <h3>配件类型分布</h3>
              <span>{{ hardwareList.length }} 个配件</span>
            </div>
            <div class="bar-list">
              <div v-for="item in hardwareTypeStats" :key="item.label" class="bar-row">
                <div class="bar-meta">
                  <span>{{ item.label }}</span>
                  <strong>{{ item.count }}</strong>
                </div>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: `${item.percent}%` }"></div>
                </div>
              </div>
            </div>
          </section>
          <section class="chart-card">
            <div class="chart-head">
              <h3>内容活跃概览</h3>
              <span>实时统计</span>
            </div>
            <div class="column-chart">
              <div v-for="item in activityBars" :key="item.label" class="column-item">
                <div class="column-shell">
                  <div class="column-fill" :style="{ height: `${Math.max(8, Math.round((item.value / maxActivityValue) * 100))}%` }"></div>
                </div>
                <strong>{{ item.value }}</strong>
                <span>{{ item.label }}</span>
              </div>
            </div>
          </section>
        </div>
      </section>

      <section v-else-if="activeMenu === 'components'" class="content-area two-column">
        <form class="editor-card" @submit.prevent="saveHardware">
          <h3>{{ editingHardwareId ? '编辑配件' : '新增配件' }}</h3>
          <label>
            类型
            <select v-model="hardwareForm.type">
              <option v-for="item in hardwareTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
          </label>
          <div class="form-grid">
            <label>
              品牌
              <input v-model="hardwareForm.brand" required placeholder="例如 AMD / 华硕" />
            </label>
            <label>
              型号
              <input v-model="hardwareForm.model" required placeholder="例如 Ryzen 5 7500F" />
            </label>
          </div>
          <div class="form-grid">
            <label>
              价格
              <input v-model.number="hardwareForm.price" min="0" required type="number" />
            </label>
            <label>
              性能评分
              <input v-model.number="hardwareForm.performanceScore" min="0" max="100" required type="number" />
            </label>
          </div>
          <div class="form-grid">
            <label>
              CPU 接口
              <input v-model="hardwareForm.socketType" placeholder="AM5 / LGA1700" />
            </label>
            <label>
              内存类型
              <input v-model="hardwareForm.memoryType" placeholder="DDR4 / DDR5" />
            </label>
          </div>
          <div class="form-grid">
            <label>
              版型
              <input v-model="hardwareForm.formFactor" placeholder="ATX / M-ATX / ITX" />
            </label>
            <label>
              支持版型
              <input v-model="hardwareForm.supportedFormFactors" placeholder="ATX,M-ATX,ITX" />
            </label>
          </div>
          <div class="form-grid">
            <label>
              功耗 / 额定功率
              <input v-model.number="hardwareForm.wattage" min="0" type="number" />
            </label>
            <label>
              显卡限长
              <input v-model.number="hardwareForm.gpuLengthMm" min="0" type="number" />
            </label>
          </div>
          <label>
            散热限高
            <input v-model.number="hardwareForm.coolerHeightMm" min="0" type="number" />
          </label>
          <label>
            规格备注
            <textarea v-model="hardwareForm.specs" placeholder="接口、容量、频率、适用场景等"></textarea>
          </label>
          <label class="inline-check">
            <input v-model="hardwareForm.enabled" type="checkbox" />
            启用
          </label>
          <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
          <div class="button-row">
            <button class="primary-button" :disabled="savingHardware" type="submit">
              {{ savingHardware ? '保存中...' : '保存配件' }}
            </button>
            <button class="ghost-button" type="button" @click="resetHardwareForm">清空</button>
          </div>
        </form>

        <div class="list-card">
          <div class="section-head">
            <div>
              <h3>配件列表</h3>
              <p class="section-subtitle">共 {{ filteredHardwareList.length }} 个结果</p>
            </div>
          </div>
          <div class="hardware-toolbar">
            <input v-model="hardwareKeyword" class="hardware-search" placeholder="搜索品牌、型号、接口或规格" @input="resetHardwarePage" />
            <select v-model="hardwareTypeFilter" class="hardware-filter" @change="resetHardwarePage">
              <option value="ALL">全部类型</option>
              <option v-for="item in hardwareTypeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <select v-model="hardwareStatusFilter" class="hardware-filter hardware-status-filter" @change="resetHardwarePage">
              <option value="ALL">全部状态</option>
              <option value="ENABLED">启用</option>
              <option value="DISABLED">停用</option>
            </select>
            <select v-model.number="hardwarePageSize" class="hardware-filter hardware-page-size" @change="resetHardwarePage">
              <option :value="12">每页 12 条</option>
              <option :value="20">每页 20 条</option>
              <option :value="50">每页 50 条</option>
            </select>
          </div>
          <article v-for="item in pagedHardwareList" :key="item.id" class="hardware-row">
            <div class="hardware-row-main">
              <strong>{{ hardwareTypeLabel(item.type) }} · {{ item.brand }} {{ item.model }}</strong>
              <p>¥{{ item.price }} · 性能 {{ item.performanceScore }} · {{ item.socketType || item.memoryType || item.formFactor || '通用规格' }}</p>
            </div>
            <div class="hardware-actions">
              <span :class="item.enabled ? 'status-chip published' : 'status-chip'">{{ item.enabled ? '启用' : '停用' }}</span>
              <button type="button" @click="editHardware(item)">编辑</button>
              <button class="danger-link" type="button" @click="deleteHardware(item.id)">删除</button>
            </div>
          </article>
          <div v-if="filteredHardwareList.length === 0" class="empty-box">暂无符合条件的配件</div>
          <div v-if="filteredHardwareList.length > 0" class="pagination-row">
            <button type="button" :disabled="hardwarePage <= 1" @click="previousHardwarePage">上一页</button>
            <span>第 {{ Math.min(hardwarePage, hardwareTotalPages) }} / {{ hardwareTotalPages }} 页 · 每页 {{ hardwarePageSize }} 条</span>
            <button type="button" :disabled="hardwarePage >= hardwareTotalPages" @click="nextHardwarePage">下一页</button>
          </div>
        </div>
      </section>

      <section v-else-if="activeMenu === 'news'" class="content-area two-column">
        <form class="editor-card" @submit.prevent="saveNews">
          <h3>发布新闻热点</h3>
          <label>
            标题
            <input v-model="newsForm.title" required placeholder="请输入标题" />
          </label>
          <label>
            摘要
            <input v-model="newsForm.summary" required placeholder="请输入摘要" />
          </label>
          <label>
            封面地址
            <input v-model="newsForm.coverUrl" placeholder="七牛云图片地址" />
          </label>
          <label>
            状态
            <select v-model="newsForm.status">
              <option value="DRAFT">草稿</option>
              <option value="PUBLISHED">发布</option>
            </select>
          </label>
          <label>
            正文
            <textarea v-model="newsForm.content" placeholder="请输入正文内容"></textarea>
          </label>
          <p v-if="errorMessage" class="error-text">{{ errorMessage }}</p>
          <button class="primary-button" :disabled="savingNews" type="submit">
            {{ savingNews ? '保存中...' : '保存新闻' }}
          </button>
        </form>

        <div class="list-card">
          <h3>新闻列表</h3>
          <article v-for="item in newsList" :key="item.id" class="news-row">
            <div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.summary }}</p>
            </div>
            <span>{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span>
          </article>
          <div v-if="newsList.length === 0" class="empty-box">暂无新闻热点</div>
        </div>
      </section>

      <section v-else-if="activeMenu === 'community'" class="content-area two-column">
        <div class="list-card">
          <div class="section-head">
            <h3>帖子管理</h3>
            <button class="ghost-button" type="button" @click="loadCommunityPosts">刷新</button>
          </div>
          <article
            v-for="post in communityPosts"
            :key="post.id"
            :class="selectedCommunityPostId === post.id ? 'community-row active' : 'community-row'"
          >
            <div class="community-row-main" @click="loadCommunityComments(post.id)">
              <div class="community-meta">
                <strong>{{ post.author.nickname || post.author.username }}</strong>
                <span>{{ formatTime(post.createdAt) }}</span>
              </div>
              <p>{{ post.content }}</p>
              <div v-if="post.buildConfig" class="admin-build-chip">
                配置：{{ post.buildConfig.title }} · ¥{{ post.buildConfig.totalPrice }} · {{ post.buildConfig.performanceScore }} 分
              </div>
              <div v-if="post.imageUrls.length" class="admin-image-strip">
                <img v-for="image in post.imageUrls" :key="image" :src="image" alt="" />
              </div>
              <div class="community-counts">
                <span>点赞 {{ post.likeCount }}</span>
                <span>评论 {{ post.commentCount }}</span>
                <span>收藏 {{ post.favoriteCount }}</span>
              </div>
            </div>
            <button class="danger-link" type="button" @click="deleteCommunityPost(post.id)">删除</button>
          </article>
          <div v-if="communityPosts.length === 0" class="empty-box">暂无社区帖子</div>
        </div>

        <div class="list-card">
          <h3>评论管理</h3>
          <div v-if="!selectedCommunityPostId" class="empty-box">选择左侧帖子后查看评论</div>
          <article v-for="comment in communityComments" :key="comment.id" class="comment-row">
            <div class="comment-row-main">
              <div class="community-meta">
                <strong>{{ comment.author.nickname || comment.author.username }}</strong>
                <span>{{ formatTime(comment.createdAt) }}</span>
              </div>
              <p>{{ comment.content }}</p>
              <div v-if="comment.imageUrls.length" class="admin-image-strip">
                <img v-for="image in comment.imageUrls" :key="image" :src="image" alt="" />
              </div>
              <div class="community-counts">
                <span>点赞 {{ comment.likeCount }}</span>
              </div>
            </div>
            <button class="danger-link" type="button" @click="deleteCommunityComment(comment.id)">删除</button>
          </article>
          <div v-if="selectedCommunityPostId && communityComments.length === 0" class="empty-box">暂无评论</div>
        </div>
      </section>

      <section v-else class="content-area">
        <div class="wide-card empty-module">
          <h3>{{ currentMenuTitle }}</h3>
          <p>该模块的接口和页面将在下一步继续细化。</p>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #eef5ff;
  transition: background 420ms ease;
}

.login-hero {
  flex: 1.1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 16% 18%, rgba(255, 255, 255, 0.38), transparent 25%),
    linear-gradient(145deg, #2459e3 0%, #377dff 54%, #77d3ff 100%);
  transition:
    flex 520ms cubic-bezier(0.22, 1, 0.36, 1),
    padding 520ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.hero-card {
  width: min(620px, 100%);
  min-height: 500px;
  padding: 60px;
  border-radius: 36px;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
  box-shadow: 0 28px 70px rgba(17, 24, 39, 0.14);
  backdrop-filter: blur(28px) saturate(150%);
  transition:
    transform 520ms cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 520ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.hero-badge,
.eyebrow {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-card h1 {
  margin: 22px 0 14px;
  font-size: 56px;
  line-height: 1.08;
}

.hero-card p {
  max-width: 420px;
  color: rgba(255, 255, 255, 0.84);
  line-height: 1.8;
}

.hero-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 32px;
}

.hero-metrics span {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
}

.login-panel {
  flex: 0.9;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 24% 18%, rgba(255, 255, 255, 0.96), transparent 24%),
    linear-gradient(180deg, #f8fbff 0%, #eef5ff 100%);
  transition:
    flex 520ms cubic-bezier(0.22, 1, 0.36, 1),
    padding 520ms cubic-bezier(0.22, 1, 0.36, 1),
    background 520ms ease;
}

.login-form {
  width: 430px;
  padding: 36px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.9);
  box-shadow: var(--shadow);
  backdrop-filter: blur(24px) saturate(150%);
  transition:
    transform 520ms cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 520ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.login-page.engaged .login-hero {
  flex: 0.94;
  padding: 22px;
}

.login-page.engaged .hero-card {
  transform: translateX(-10px) scale(0.985);
  border-radius: 42px;
  box-shadow: 0 18px 44px rgba(17, 24, 39, 0.12);
}

.login-page.engaged .login-panel {
  flex: 1.06;
  padding: 22px 28px;
}

.login-page.engaged .login-form {
  transform: scale(1.03) translateX(-4px);
  border-radius: 34px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.78),
    0 34px 84px rgba(37, 99, 235, 0.16);
}

.login-form h2 {
  margin: 6px 0 8px;
  font-size: 32px;
}

.login-form span,
.login-form label,
.wide-card p,
.news-row p {
  color: var(--muted);
}

label {
  display: grid;
  gap: 8px;
  margin-top: 18px;
  font-weight: 700;
}

input,
textarea,
select {
  width: 100%;
  border: 1px solid rgba(150, 179, 230, 0.42);
  border-radius: 16px;
  padding: 13px 16px;
  outline: none;
  color: var(--text);
  background: rgba(248, 251, 255, 0.78);
}

select {
  appearance: none;
  padding-right: 52px;
  background:
    linear-gradient(45deg, transparent 50%, #475467 50%) right 28px center / 7px 7px no-repeat,
    linear-gradient(135deg, #475467 50%, transparent 50%) right 22px center / 7px 7px no-repeat,
    rgba(248, 251, 255, 0.78);
}

textarea {
  min-height: 150px;
  resize: vertical;
}

.primary-button {
  width: 100%;
  height: 48px;
  margin-top: 22px;
  border: none;
  border-radius: 16px;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(135deg, var(--blue), #65a3ff);
  box-shadow: 0 12px 24px rgba(55, 125, 255, 0.22);
}

.primary-button:disabled {
  opacity: 0.7;
}

.error-text {
  color: #dc2626;
  margin: 12px 0 0;
}

.admin-shell {
  display: flex;
  gap: 18px;
  width: 100%;
  height: 100%;
  padding: 18px;
  background: #fff;
}

.sidebar {
  width: 268px;
  min-width: 268px;
  padding: 22px 14px;
  border-radius: 34px;
  color: #fff;
  background: linear-gradient(180deg, rgba(43, 92, 215, 0.96), rgba(98, 152, 238, 0.86));
  box-shadow: 0 30px 72px rgba(56, 87, 145, 0.2);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 22px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  font-weight: 900;
  background: rgba(255, 255, 255, 0.18);
}

.brand h1 {
  margin: 0;
  font-size: 20px;
}

.brand p {
  margin: 4px 0 0;
  color: rgba(255, 255, 255, 0.78);
}

.menu {
  display: grid;
  gap: 10px;
}

.menu-item {
  height: 54px;
  border: none;
  border-radius: 18px;
  color: rgba(255, 255, 255, 0.94);
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  font-weight: 800;
  background: rgba(255, 255, 255, 0.08);
}

.menu-item.active,
.menu-item:hover {
  background: rgba(255, 255, 255, 0.2);
}

.menu-icon {
  width: 26px;
  text-align: center;
}

.main-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 16px;
}

.topbar {
  height: 74px;
  border-radius: 28px;
  padding: 0 22px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(226, 233, 244, 0.86);
  box-shadow: 0 12px 30px rgba(92, 122, 176, 0.08);
}

.topbar h2 {
  margin: 0;
  font-size: 24px;
}

.user-pill {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 8px 16px;
  border-radius: 999px;
  background: #f6f9ff;
}

.user-pill button {
  border: none;
  border-radius: 999px;
  padding: 8px 12px;
  color: var(--blue-dark);
  background: rgba(55, 125, 255, 0.12);
}

.content-area {
  flex: 1;
  overflow: auto;
  padding: 4px 2px 22px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.stat-card,
.wide-card,
.editor-card,
.list-card {
  border: 1px solid rgba(226, 233, 244, 0.9);
  border-radius: 30px;
  background: var(--card);
  box-shadow: 0 10px 24px rgba(24, 39, 75, 0.04);
}

.stat-card {
  padding: 22px;
}

.stat-card span {
  color: var(--muted);
  font-weight: 700;
}

.stat-card strong {
  display: block;
  margin-top: 12px;
  font-size: 34px;
}

.stat-card p {
  margin: 4px 0 0;
  color: var(--muted);
}

.wide-card,
.editor-card,
.list-card {
  padding: 24px;
  margin-top: 18px;
}

.wide-card h3,
.editor-card h3,
.list-card h3 {
  margin: 0 0 16px;
  font-size: 20px;
}

.progress-list {
  display: grid;
  gap: 10px;
}

.dashboard-charts {
  display: grid;
  grid-template-columns: minmax(420px, 1fr) minmax(420px, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.chart-card {
  min-height: 320px;
  padding: 24px;
  border: 1px solid rgba(226, 233, 244, 0.9);
  border-radius: 30px;
  background: var(--card);
  box-shadow: 0 10px 24px rgba(24, 39, 75, 0.04);
}

.chart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.chart-head h3 {
  margin: 0;
  font-size: 20px;
}

.chart-head span {
  color: var(--muted);
  font-weight: 800;
}

.bar-list {
  display: grid;
  gap: 14px;
}

.bar-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--muted);
  font-weight: 800;
}

.bar-meta strong {
  color: var(--text);
}

.bar-track {
  height: 12px;
  overflow: hidden;
  border-radius: 999px;
  background: #eef3fb;
}

.bar-fill {
  height: 100%;
  min-width: 8px;
  border-radius: 999px;
  background: linear-gradient(90deg, #377dff, #74c7ff);
}

.column-chart {
  height: 235px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: end;
  gap: 18px;
  padding-top: 8px;
}

.column-item {
  min-width: 0;
  display: grid;
  justify-items: center;
  gap: 8px;
}

.column-shell {
  width: 58px;
  height: 150px;
  display: flex;
  align-items: end;
  overflow: hidden;
  border-radius: 999px;
  background: #eef3fb;
}

.column-fill {
  width: 100%;
  border-radius: 999px;
  background: linear-gradient(180deg, #74c7ff, #377dff);
}

.column-item strong {
  font-size: 20px;
  color: var(--text);
}

.column-item span {
  color: var(--muted);
  font-weight: 800;
}

.two-column {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(420px, 1.1fr);
  gap: 18px;
  align-items: stretch;
}

.two-column .editor-card,
.two-column .list-card {
  margin-top: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.inline-check {
  display: flex;
  align-items: center;
  flex-direction: row;
  gap: 10px;
  font-weight: 800;
}

.inline-check input {
  width: 18px;
  height: 18px;
}

.button-row,
.hardware-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.button-row {
  margin-top: 22px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  align-items: center;
  gap: 12px;
}

.ghost-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: auto;
  min-width: 82px;
  height: 46px;
  white-space: nowrap;
  border: none;
  border-radius: 999px;
  padding: 0 18px;
  font-weight: 900;
  color: var(--text);
  background: #f4f7fb;
}

.button-row .primary-button {
  width: 100%;
  height: 48px;
  margin-top: 0;
  border-radius: 24px;
}

.button-row .ghost-button {
  width: 96px;
  height: 48px;
  min-width: 96px;
  border-radius: 24px;
}

.hardware-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.hardware-row-main {
  min-width: 0;
}

.hardware-row-main strong {
  display: block;
}

.hardware-row-main p {
  margin: 6px 0 0;
  color: var(--muted);
}

.hardware-actions {
  flex-shrink: 0;
}

.hardware-actions button {
  border: none;
  border-radius: 999px;
  padding: 8px 12px;
  font-weight: 800;
  color: var(--blue-dark);
  background: rgba(55, 125, 255, 0.1);
}

.hardware-actions .danger-link {
  color: #ff3b30;
  background: rgba(255, 59, 48, 0.1);
}

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding-top: 18px;
  margin-top: auto;
}

.pagination-row button {
  height: 38px;
  min-width: 84px;
  border: none;
  border-radius: 999px;
  color: var(--blue-dark);
  font-weight: 900;
  background: rgba(55, 125, 255, 0.1);
}

.pagination-row button:disabled {
  color: #98a2b3;
  background: #f3f6fb;
}

.pagination-row span {
  color: var(--muted);
  font-size: 13px;
  font-weight: 800;
}

.status-chip {
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  color: var(--muted);
  background: #f3f6fb;
}

.status-chip.published {
  color: #0a7a3d;
  background: rgba(52, 199, 89, 0.12);
}

.news-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}

.news-row strong {
  display: block;
}

.news-row p {
  margin: 6px 0 0;
}

.news-row span {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--blue-dark);
  background: rgba(55, 125, 255, 0.1);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.section-head h3 {
  margin: 0;
}

.section-subtitle {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.hardware-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 150px 132px 132px;
  gap: 10px;
  margin-bottom: 12px;
}

.hardware-search,
.hardware-filter {
  height: 44px;
  border-radius: 999px;
}

.hardware-filter {
  padding-top: 0;
  padding-bottom: 0;
}

.community-row,
.comment-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  border-radius: 22px;
  background: #f8fbff;
}

.community-row.active {
  border-color: rgba(55, 125, 255, 0.34);
  background: #eef5ff;
}

.community-row-main,
.comment-row-main {
  min-width: 0;
  flex: 1;
  cursor: pointer;
}

.community-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.community-meta span {
  flex-shrink: 0;
  color: var(--muted);
  font-size: 13px;
}

.community-row p,
.comment-row p {
  margin: 8px 0 0;
  color: var(--text);
  line-height: 1.7;
  white-space: pre-wrap;
}

.admin-build-chip {
  display: inline-flex;
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 999px;
  color: var(--blue-dark);
  font-weight: 800;
  background: rgba(55, 125, 255, 0.1);
}

.admin-image-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.admin-image-strip img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 16px;
  border: 1px solid var(--line);
}

.community-counts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.community-counts span {
  padding: 6px 10px;
  border-radius: 999px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
  background: #fff;
}

.community-row > .danger-link,
.comment-row > .danger-link {
  flex-shrink: 0;
  border: none;
  border-radius: 999px;
  padding: 8px 12px;
  color: #ff3b30;
  font-weight: 900;
  background: rgba(255, 59, 48, 0.1);
}

.empty-box {
  padding: 40px;
  text-align: center;
  color: var(--muted);
}

.empty-module {
  min-height: 420px;
  display: grid;
  place-content: center;
  text-align: center;
}

@media (max-width: 1100px) {
  .login-page {
    flex-direction: column;
  }

  .login-hero,
  .login-panel,
  .login-page.engaged .login-hero,
  .login-page.engaged .login-panel {
    flex: none;
  }

  .login-hero {
    min-height: 36vh;
  }

  .hero-card {
    min-height: auto;
    padding: 34px;
  }

  .hero-card h1 {
    font-size: 40px;
  }

  .stats-grid,
  .two-column,
  .dashboard-charts,
  .hardware-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>

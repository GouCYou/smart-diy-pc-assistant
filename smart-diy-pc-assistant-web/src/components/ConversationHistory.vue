<script setup lang="ts">
import { Fold, MoreFilled, ChatDotRound, Edit, Top, Share, Box, Delete } from '@element-plus/icons-vue';
defineProps<{items:any[];current:string;loggedIn:boolean;archived?:boolean}>();
const emit=defineEmits<{select:[id:string];delete:[id:string];toggle:[];action:[command:string,item:any];archiveView:[]}>();
</script>
<template>
  <div class="conversation-history">
    <header><button aria-label="收起历史侧栏" title="收起历史侧栏" @click="emit('toggle')"><el-icon><Fold /></el-icon></button><strong>{{archived ? '已归档对话' : '对话历史'}}</strong><button class="history-archive-view" :aria-label="archived ? '返回对话历史' : '查看归档对话'" @click="emit('archiveView')">{{archived ? '返回' : '归档'}}</button></header>
    <nav v-if="items.length" v-slider class="conversation-history-list" aria-label="历史对话">
      <div v-for="h in items" :key="h.id" class="conversation-history-row" :class="{active:current===h.id}">
        <button class="conversation-select" :title="h.title" :aria-current="current===h.id ? 'true' : undefined" @click="emit('select',h.id)"><span>{{h.title}}</span></button>
        <span v-if="h.pinned" class="conversation-pin" title="已置顶"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="M9 3h6l-1 6 4 4v2h-5v6l-1 1-1-1v-6H6v-2l4-4-1-6Z" /></svg></span>
        <el-dropdown popper-class="conversation-menu-popper" trigger="click" placement="bottom-start" @command="(command: string)=>command==='delete' ? emit('delete',h.id) : emit('action',String(command),h)">
          <button class="conversation-more" :aria-label="`对话选项：${h.title}`" title="对话选项" @click.stop><el-icon><MoreFilled /></el-icon></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="rename"><el-icon><Edit /></el-icon>重命名</el-dropdown-item><el-dropdown-item command="pin"><el-icon><Top /></el-icon>{{h.pinned ? '取消置顶' : '置顶'}}</el-dropdown-item><el-dropdown-item divided command="share"><el-icon><Share /></el-icon>分享</el-dropdown-item><el-dropdown-item divided command="archive"><el-icon><Box /></el-icon>{{h.archived ? '取消归档' : '归档'}}</el-dropdown-item><el-dropdown-item command="delete" class="danger-menu-item"><el-icon><Delete /></el-icon>删除</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </nav>
    <div v-else class="conversation-history-empty"><el-icon><ChatDotRound /></el-icon><span>{{loggedIn ? (archived ? '暂无归档对话' : '暂无对话记录') : '登录后查看对话历史'}}</span><router-link v-if="!loggedIn" to="/auth">登录</router-link></div>
  </div>
</template>

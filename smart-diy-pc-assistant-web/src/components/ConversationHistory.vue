<script setup lang="ts">
import { Fold, Delete, ChatDotRound } from '@element-plus/icons-vue';
defineProps<{items:any[]; current:string; loggedIn:boolean}>();
const emit=defineEmits<{select:[id:string];delete:[id:string];toggle:[]}>();
</script>
<template>
  <div class="conversation-history">
    <header><button aria-label="收起历史侧栏" title="收起历史侧栏" @click="emit('toggle')"><el-icon><Fold /></el-icon></button><strong>对话历史</strong></header>
    <nav v-if="items.length" v-slider class="conversation-history-list" aria-label="历史对话">
      <div v-for="h in items" :key="h.id" class="conversation-history-row" :class="{active:current === h.id}">
        <button class="conversation-select" :title="h.title" :aria-current="current === h.id ? 'true' : undefined" @click="emit('select',h.id)"><span>{{h.title}}</span><time>{{new Date(h.updated_at).toLocaleDateString()}}</time></button>
        <button class="conversation-delete" :aria-label="'删除对话：'+h.title" @click="emit('delete',h.id)"><el-icon><Delete /></el-icon></button>
      </div>
    </nav>
    <div v-else class="conversation-history-empty"><el-icon><ChatDotRound /></el-icon><span>{{loggedIn ? '暂无对话记录' : '登录后查看对话历史'}}</span><router-link v-if="!loggedIn" to="/auth">登录</router-link></div>
  </div>
</template>

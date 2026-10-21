<script setup lang="ts">
import { DocumentCopy, Refresh, Share } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
const props=defineProps<{content:string;disabled?:boolean}>();
const emit=defineEmits<{retry:[];share:[]}>();
async function copy(){try{await navigator.clipboard.writeText(props.content);ElMessage.success('已复制');}catch{ElMessage.error('复制失败，请手动选择文本复制');}}
</script>
<template><div class="message-actions"><button title="复制" aria-label="复制回答" @click="copy"><el-icon><DocumentCopy /></el-icon></button><button title="重新生成" aria-label="重新生成回答" :disabled="disabled" @click="emit('retry')"><el-icon><Refresh /></el-icon></button><button title="分享" aria-label="分享回答" :disabled="disabled" @click="emit('share')"><el-icon><Share /></el-icon></button></div></template>

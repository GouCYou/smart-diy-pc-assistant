<script setup lang="ts">
import { ArrowLeft } from '@element-plus/icons-vue';
defineProps<{title:string}>();
const emit = defineEmits<{close:[]}>();
</script>
<template><div class="dialog-heading"><button type="button" aria-label="返回" @click="emit('close')"><el-icon><ArrowLeft /></el-icon></button><strong>{{title}}</strong><span /></div></template>

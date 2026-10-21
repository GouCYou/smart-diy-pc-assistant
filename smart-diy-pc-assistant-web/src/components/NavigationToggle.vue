<script setup lang="ts">
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue';
import { sidebarCollapsed } from '../lib/navigation';
</script>
<template><button class="main-navigation-toggle" :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'" :title="sidebarCollapsed ? '展开导航' : '收起导航'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed"><el-icon><DArrowRight v-if="sidebarCollapsed" /><DArrowLeft v-else /></el-icon></button></template>

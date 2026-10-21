<script setup lang="ts">
import { sidebarCollapsed } from '../lib/navigation';
</script>
<template><button class="main-navigation-toggle" :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'" :title="sidebarCollapsed ? '展开导航' : '收起导航'" :aria-expanded="!sidebarCollapsed" @click="sidebarCollapsed = !sidebarCollapsed"><svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="3"/><path d="M8 4v16"/><path v-if="sidebarCollapsed" d="m12 9 3 3-3 3"/><path v-else d="m16 9-3 3 3 3"/></svg></button></template>

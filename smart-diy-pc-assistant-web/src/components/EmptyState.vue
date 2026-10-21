<script setup lang="ts">
import { Collection, ChatDotRound, Document, Bell } from '@element-plus/icons-vue';
withDefaults(defineProps<{description?: string; kind?: string}>(),{description:'暂无内容',kind:'collection'});
const icons: Record<string, typeof Collection> = {collection:Collection,chat:ChatDotRound,news:Document,bell:Bell};
</script>
<template><div class="empty-state" role="status"><el-icon class="empty-state-icon"><component :is="icons[kind] || Collection" /></el-icon><p>{{description}}</p></div></template>

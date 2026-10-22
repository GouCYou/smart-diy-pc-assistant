<script setup lang="ts">
import {computed} from 'vue';
import {UserFilled} from '@element-plus/icons-vue';
const props=withDefaults(defineProps<{src?:string|null;size?:number|'large'|'default'|'small'}>(),{size:40});
const source=computed(()=>props.src?.trim() || undefined);
</script>
<template><el-avatar class="user-avatar" :src="source" :size="size" :icon="UserFilled" alt="用户头像"/></template>

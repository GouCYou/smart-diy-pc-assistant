<script setup lang="ts">
import { onMounted,ref } from 'vue';
import {useRoute} from 'vue-router';
import {api} from '../lib/api';
import {markdown} from '../lib/markdown';
import MessageAttachments from '../components/MessageAttachments.vue';
const route=useRoute(),conversation=ref<any>(null),error=ref('');
onMounted(async()=>{try{conversation.value=await api.request(`/agent/shared/${route.params.token}`);}catch(e){error.value=(e as Error).message;}});
</script>
<template><section class="article"><el-alert v-if="error" :title="error" type="error" :closable="false" /><template v-if="conversation"><h1>{{conversation.title}}</h1><p class="muted">分享的对话 · 只读</p><article v-for="(m,i) in conversation.messages" :key="i" class="message" :class="m.role"><time class="message-date">{{new Date(m.created_at).toLocaleString('zh-CN')}}</time><div v-if="m.role==='user'" class="message-body">{{m.content}}</div><div v-else class="message-body markdown" v-html="markdown(m.content)" /><MessageAttachments :attachments="m.attachments" /></article></template></section></template>

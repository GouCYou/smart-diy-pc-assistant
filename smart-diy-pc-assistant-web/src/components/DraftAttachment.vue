<script setup lang="ts">
import {computed} from 'vue';
import type {Draft} from '../../../shared/types';
import {quotedDraft} from '../lib/chatPresentation';
import {previewDraft} from '../lib/previews';
const props=defineProps<{draft:Draft;readOnly?:boolean}>();
const images=computed(()=>props.draft.parts.map(p=>props.draft.partSelections?.find(q=>q.hardwarePartId===p.id)?.image || p.productImage).filter((v):v is string=>!!v&&/^https:\/\//i.test(v)).slice(0,4));
const summary=computed(()=>props.draft.parts.filter(p=>['CPU','GPU','MEMORY'].includes(p.type)).map(p=>p.model).join(' · '));
</script>
<template><button v-if="quotedDraft(draft)" class="draft-attachment" @click="previewDraft={draft,readOnly:!!readOnly}"><span class="draft-attachment-images"><img v-for="url in images" :key="url" :src="url" alt="配置商品图" loading="lazy"/><span v-if="!images.length" class="draft-attachment-fallback">配置</span></span><span class="draft-attachment-main"><strong>{{draft.title}}</strong><span>{{summary}}</span><small>{{draft.parts.length}} 件配件 · 已报价 ¥{{draft.validation.quotedTotal.toLocaleString()}}</small></span><span class="draft-attachment-arrow">查看配置 ↗</span></button></template>

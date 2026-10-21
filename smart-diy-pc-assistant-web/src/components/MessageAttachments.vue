<script setup lang="ts">
import {previewBuildId,previewNewsId} from "../lib/previews";
import DraftCard from "./DraftCard.vue";
import type {Draft} from "../../../shared/types";
defineProps<{readOnly?:boolean;attachments?:{draft?:Draft;build?:{id:number;title:string};news?:any[]}}>();
</script>
<template>
  <div v-if="attachments?.build || attachments?.news?.length || attachments?.draft" class="message-attachments">
    <DraftCard v-if="attachments.draft" :draft="attachments.draft" :read-only="readOnly" />
    <button v-if="attachments.build" @click="previewBuildId=attachments.build.id" class="linked-build"><span class="attachment-kind">关联配置</span><strong>{{attachments.build.title}}</strong><span>查看配置 →</span></button>
    <button v-for="news in attachments.news || []" :key="news.id" @click="previewNewsId=news.id" class="linked-news"><img v-if="news.coverUrl" :src="news.coverUrl" alt="" loading="lazy" /><div><span class="attachment-kind">引用资讯</span><strong>{{news.title || '查看新闻'}}</strong><p v-if="news.summary">{{news.summary}}</p></div><span>→</span></button>
  </div>
</template>

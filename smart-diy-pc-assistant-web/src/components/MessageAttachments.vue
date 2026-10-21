<script setup lang="ts">
import DraftCard from "./DraftCard.vue";
import type {Draft} from "../../../shared/types";
defineProps<{readOnly?:boolean;attachments?:{draft?:Draft;build?:{id:number;title:string};news?:any[]}}>();
</script>
<template>
  <div v-if="attachments?.build || attachments?.news?.length || attachments?.draft" class="message-attachments">
    <DraftCard v-if="attachments.draft" :draft="attachments.draft" :read-only="readOnly" />
    <router-link v-if="attachments.build" :to="`/builds?id=${attachments.build.id}`" class="linked-build"><span class="attachment-kind">关联配置</span><strong>{{attachments.build.title}}</strong><span>查看配置 →</span></router-link>
    <router-link v-for="news in attachments.news || []" :key="news.id" :to="`/news/${news.id}`" class="linked-news"><img v-if="news.coverUrl" :src="news.coverUrl" alt="" loading="lazy" /><div><span class="attachment-kind">引用资讯</span><strong>{{news.title || '查看新闻'}}</strong><p v-if="news.summary">{{news.summary}}</p></div><span>→</span></router-link>
  </div>
</template>

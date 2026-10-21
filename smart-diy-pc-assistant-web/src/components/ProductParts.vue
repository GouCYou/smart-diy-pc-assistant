<script setup lang="ts">
import { Cpu, TopRight } from '@element-plus/icons-vue';
import { hardwareTypes, type Hardware } from '../../../shared/types';
defineProps<{parts:(Hardware & {selectedPrice?:number;shopName?:string;priceLabel?:string;priceConditions?:string;priceType?:string})[];compact?:boolean}>();
const safe=(value?:string)=>value?.startsWith('//') ? 'https:'+value : /^https:\/\//i.test(value || '') ? value : '';
</script>
<template>
  <div class="product-parts" :class="{'is-compact':compact}">
    <article v-for="p in parts" :key="p.id" class="product-part">
      <a v-if="safe(p.productLink)" :href="safe(p.productLink)" target="_blank" rel="noopener noreferrer" class="product-picture" :aria-label="`查看${p.productTitle || p.model}商品`"><el-image v-if="safe(p.productImage)" :src="safe(p.productImage)" fit="contain" loading="lazy"><template #error><el-icon><Cpu /></el-icon></template></el-image><el-icon v-else><Cpu /></el-icon></a>
      <span v-else class="product-picture"><el-image v-if="safe(p.productImage)" :src="safe(p.productImage)" fit="contain" loading="lazy"><template #error><el-icon><Cpu /></el-icon></template></el-image><el-icon v-else><Cpu /></el-icon></span>
      <div class="product-part-main"><span class="product-category">{{hardwareTypes[p.type] || p.type}}</span><a v-if="safe(p.productLink)" :href="safe(p.productLink)" target="_blank" rel="noopener noreferrer" class="product-title">{{p.productTitle || p.model}}</a><strong v-else class="product-title">{{p.productTitle || p.model}}</strong><p class="product-model">{{p.model}}</p><p v-if="!compact && p.specs" class="product-specs">{{p.specs}}</p><span v-if="!compact && p.shopName" class="product-shop">{{p.shopName}}</span></div>
      <div class="product-part-end"><strong class="product-price">{{(p.selectedPrice ?? p.productPrice) ? '¥'+(p.selectedPrice ?? p.productPrice)!.toLocaleString() : '待询价'}}</strong><span class="product-price-kind" :title="p.priceConditions">{{p.priceLabel || ((p.selectedPrice ?? p.productPrice) ? '页面价' : '')}}</span><a v-if="safe(p.productLink)" :href="safe(p.productLink)" target="_blank" rel="noopener noreferrer" class="product-link">京东<el-icon><TopRight /></el-icon></a><span v-else class="product-unmatched">未匹配商品</span></div>
    </article>
  </div>
</template>

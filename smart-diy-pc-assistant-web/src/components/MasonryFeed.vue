<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
const props=defineProps<{items:any[];columns:number;cardWidth:number;gap:number}>();
const root=ref<HTMLElement>(),heights=ref<Record<string,number>>({});
let observer:ResizeObserver|undefined;
function measure(){if(!root.value)return;const next:Record<string,number>={};for(const node of root.value.children){const el=node as HTMLElement;next[el.dataset.id!]=el.getBoundingClientRect().height;}heights.value=next;}
async function observe(){await nextTick();observer?.disconnect();if(root.value)for(const el of root.value.children)observer?.observe(el);measure();}
onMounted(()=>{observer=new ResizeObserver(measure);void observe();});
watch(()=>props.items.map(i=>i.id).join(','),observe);
onUnmounted(()=>observer?.disconnect());
const layout=computed(()=>{const levels=Array(props.columns).fill(0);const positions=props.items.map(item=>{const column=levels.indexOf(Math.min(...levels)),top=levels[column];levels[column]+= (heights.value[String(item.id)] || 220)+props.gap;return {item,left:column*(props.cardWidth+props.gap),top};});return {positions,height:Math.max(0,...levels)-props.gap};});
</script>
<template><div ref="root" class="stable-masonry" :style="{height:Math.max(0,layout.height)+'px'}"><article v-for="p in layout.positions" :key="p.item.id" :data-id="p.item.id" class="feed-card" :style="{width:cardWidth+'px',transform:`translate3d(${p.left}px,${p.top}px,0)`}"><slot :item="p.item" /></article></div></template>
<style scoped>
.stable-masonry{position:relative;min-width:0;transition:height .42s cubic-bezier(.22,.8,.24,1);}
.stable-masonry>.feed-card{position:absolute;left:0;top:0;margin:0;transition:transform .42s cubic-bezier(.22,.8,.24,1),box-shadow .2s;will-change:transform;}
@media(prefers-reduced-motion:reduce){.stable-masonry,.stable-masonry>.feed-card{transition:none;}}
</style>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { Check, Tools, Clock, Close, Document, ChatDotRound } from '@element-plus/icons-vue';
import { runStatuses } from '../../../shared/types';
export interface ActivityEvent {id:number;run_id:string;type:string;data:Record<string,any>;created_at:string}
const props=defineProps<{events:ActivityEvent[]; status?:string}>(), emit=defineEmits<{close:[]}>(), scroller=ref<HTMLElement|null>(null), following=ref(true);
const toolNames:Record<string,string>={record_plan:'整理任务计划',ask_user:'询问补充信息',search_hardware:'筛选硬件型号',search_knowledge:'检索知识库',search_products:'查询京东商品',get_build:'读取配置单',propose_build:'检查兼容性并生成草稿'};
function title(e:ActivityEvent) {
  const names:Record<string,string>={plan:'任务计划',products:'京东查询结果',references:'检索到参考资料',build_updated:'配置草稿已更新',needs_input:'等待补充信息',run_completed:'本轮任务完成',run_failed:'任务失败',run_interrupted:'任务已中断'};
  if(e.type === 'tool_started' || e.type === 'tool_completed') return toolNames[e.data.name] || e.data.name || '工具调用';
  if(e.type === 'status') return runStatuses[e.data.status] || '任务状态更新';
  return names[e.type] || '';
}
const visible=computed(() => props.events.filter(e => title(e)));
function icon(e:ActivityEvent) { return e.type === 'tool_started' ? Tools : e.type === 'tool_completed' || e.type === 'run_completed' ? Check : e.type === 'needs_input' ? ChatDotRound : e.type === 'plan' ? Document : Clock; }
watch(() => props.events[0]?.run_id, () => {following.value=true;});
watch(() => props.events.length,async () => {await nextTick();if(following.value)scroller.value?.scrollTo({top:scroller.value.scrollHeight,behavior:'smooth'});});
function trackScroll() {const el=scroller.value;if(el && el.scrollHeight-el.clientHeight-el.scrollTop<28)following.value=true;}
</script>
<template>
  <div class="tool-timeline">
    <header><strong>执行记录</strong><span v-if="status" class="activity-status">{{runStatuses[status]}}</span><button aria-label="收起执行记录" title="收起执行记录" @click="emit('close')"><el-icon><Close /></el-icon></button></header>
    <div ref="scroller" class="tool-timeline-scroll" @wheel="following = false" @touchmove="following = false" @scroll="trackScroll">
      <TransitionGroup v-if="visible.length" name="activity-entry" tag="ol" class="tool-timeline-list">
        <li v-for="e in visible" :key="e.id" :class="['activity-event',e.type]"><span class="activity-node"><el-icon><component :is="icon(e)" /></el-icon></span><div class="activity-event-body"><div class="activity-event-title"><strong>{{title(e)}}</strong><time :datetime="e.created_at">{{new Date(e.created_at).toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit'})}}</time></div><span v-if="e.type.startsWith('tool_')" class="activity-tool-name">{{e.data.name}} · {{e.type === 'tool_started' ? '开始调用' : '调用完成'}}</span><ol v-if="e.type === 'plan'" class="activity-plan"><li v-for="step in e.data.steps" :key="step">{{step}}</li></ol><p v-if="e.type === 'products'">{{e.data.products?.length || 0}} 个匹配商品<span v-if="e.data.warning"> · {{e.data.warning}}</span></p><p v-if="e.type === 'references'">{{e.data.items?.length || 0}} 条参考资料</p><p v-if="e.type === 'needs_input'">{{e.data.question}}</p><p v-if="e.type === 'run_failed'">{{e.data.message}}</p></div></li>
      </TransitionGroup>
      <div v-else class="activity-empty"><el-icon><Tools /></el-icon><strong>等待开始</strong><p>Agent 的工具调用与执行结果会记录在这里。</p></div>
    </div>
  </div>
</template>

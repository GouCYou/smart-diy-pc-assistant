<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Fold,
  Plus,
  ArrowRight,
  Setting,
  Monitor,
  Connection,
  Cpu,
  Collection,
  Clock,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import MessageAttachments from "../components/MessageAttachments.vue";
import { api } from "../lib/api";
import { user } from "../lib/session";
import { markdown } from "../lib/markdown";
import { useTask } from "../lib/task";
import ConversationHistory from "../components/ConversationHistory.vue";
import ToolTimeline, { type ActivityEvent } from "../components/ToolTimeline.vue";
import Requirements from "../components/Requirements.vue";
import DraftCard from "../components/DraftCard.vue";
import { runStatuses, type Run } from "../../../shared/types";
const route = useRoute(),
  router = useRouter(),
  { busy, error, perform, confirm } = useTask(),
  messages = ref<any[]>([]),
  run = ref<Run | null>(null),
  draft = ref(""),
  live = ref(""),
  plan = ref<string[]>([]),
  step = ref(""),
  question = ref(""),
  conversation = ref(""),
  history = ref<any[]>([]),
  showArchived = ref(false),
  conversationTitle = ref("新对话"),
  shareOpen = ref(false),
  shareUrl = ref(""),
  sharingId = ref(""),
  historyOpen = ref(true),
  activityOpen = ref(true),
  showActivityMobile = ref(false),
  activity = ref<ActivityEvent[]>([]),
  showRequirements = ref(false),
  scroll = ref<HTMLElement | null>(null);
const active = computed(
    () => run.value && ["queued", "running"].includes(run.value.status),
  ),
  toolNames: Record<string, string> = {
    record_plan: "整理任务计划",
    search_hardware: "筛选硬件型号",
    search_knowledge: "查阅知识资料",
    search_products: "查询商品报价",
    get_build: "读取配置单",
    propose_build: "校验配置方案",
  };
const visibleMessages = computed(() => {
  const last = messages.value.at(-1);
  return run.value?.answer && last?.role === 'assistant' && last.content === run.value.answer ? messages.value.slice(0,-1) : messages.value;
});
const currentEvents = computed(() => {
  const events = activity.value.filter(e => e.run_id === run.value?.id);
  let start = 0;
  events.forEach((e,i) => { if(e.type === 'status' && e.data.status === 'running') start = i; });
  return events.slice(start);
});
const completedCalls = computed(() => currentEvents.value.filter(e => e.type === 'tool_completed').length);
const totalCalls = computed(() => currentEvents.value.filter(e => e.type === 'tool_started').length);
const progress = computed(() => run.value?.status === 'completed' ? 100 : totalCalls.value ? Math.min(100, Math.round(completedCalls.value / totalCalls.value * 100)) : 0);
const workSummaries = computed(() => currentEvents.value.filter(e => e.type === 'work_summary').map(e => e.data.text));
const executionTitle = computed(() => active.value ? (step.value || '正在理解需求') : run.value?.status === 'completed' ? '处理完成' : runStatuses[run.value?.status || ''] || '执行过程');
let controller: AbortController | undefined,
  cursor = 0,
  selectionVersion = 0;
async function bottom() {
  await nextTick();
  scroll.value?.scrollTo({
    top: scroll.value.scrollHeight,
    behavior: "smooth",
  });
}
async function deleteHistory(id: string) {
  await api.request(`/agent/conversations/${id}`, "DELETE");
  if (conversation.value === id) fresh();
  await loadHistory();
}
async function historyAction(command:string,item:any) {
  if(command === 'rename') {
    let value:string;
    try { ({value}=await ElMessageBox.prompt('输入新的对话标题','重命名',{inputValue:item.title,inputValidator:(v:string)=>!!v.trim() && v.trim().length<=80 || '请输入 1–80 个字符'})); } catch { return; }
    await perform(async()=>{await api.request(`/agent/conversations/${item.id}`,'PATCH',{title:value.trim()});if(conversation.value===item.id)conversationTitle.value=value.trim();await loadHistory();});
  } else if(command === 'share') {
    sharingId.value=item.id;shareUrl.value='';shareOpen.value=true;
  } else {
    await perform(async()=>{await api.request(`/agent/conversations/${item.id}`,'PATCH',command==='pin' ? {pinned:!item.pinned} : {archived:!item.archived});await loadHistory();});
  }
}
async function createShare() {
  await perform(async()=>{const result=await api.request(`/agent/conversations/${sharingId.value}/share`,'POST');shareUrl.value=`${location.origin}/shared/${result.token}`;});
}
async function copyShare() {try{await navigator.clipboard.writeText(shareUrl.value);ElMessage.success('分享链接已复制');}catch{ElMessage.info('请手动复制分享链接');}}
async function revokeShare() {await perform(async()=>{await api.request(`/agent/conversations/${sharingId.value}/share`,'DELETE');shareUrl.value='';shareOpen.value=false;},'已停止分享');}
function messageDate(value:string) {return new Date(value).toLocaleString('zh-CN',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'});}
function showDate(index:number) {return index===0 || new Date(visibleMessages.value[index].created_at).getTime()-new Date(visibleMessages.value[index-1].created_at).getTime()>15*60*1000;}
async function loadHistory() {
  if (user.value) {history.value = await api.request(`/agent/conversations?archived=${showArchived.value}`);const selected=history.value.find(h=>h.id===conversation.value);if(selected)conversationTitle.value=selected.title;}
}
async function loadConversation(id: string) {
  const version = ++selectionVersion;
  controller?.abort();
  const c = await api.request(`/agent/conversations/${id}`);
  if (version !== selectionVersion) return;
  conversation.value = id;
  conversationTitle.value = c.title;
  messages.value = c.messages;
  run.value = c.runs.at(-1) || null;
  plan.value = [];
  question.value = "";
  live.value = "";
  cursor = 0;
  const logs = await Promise.all(c.runs.map((r:Run) => api.request<ActivityEvent[]>(`/agent/runs/${r.id}/activity`)));
  if (version !== selectionVersion) return;
  activity.value = logs.flat().sort((a:ActivityEvent,b:ActivityEvent) => a.id-b.id);
  await router.replace({ path: "/", query: { conversation: id } });
  if (run.value) await connect();
  await bottom();
}
async function connect() {
  if (!run.value) return;
  controller?.abort();
  controller = new AbortController();
  const current = run.value.id;
  const historical = !["queued", "running"].includes(run.value.status);
  try {
    await api.stream(
      `/agent/runs/${current}/events?after=${cursor}`,
      (type, data, id) => {
        cursor = id;
        if (!run.value || run.value.id !== current) return;
        if (!['text_delta','usage','model_turn_started','answer_ready'].includes(type) && !activity.value.some(e => e.id === id)) {
          activity.value.push({id,run_id:current,type,data,created_at:data._event_time || run.value.created_at});
        }
        if (type === "model_turn_started") live.value = "";
        if (type === "answer_ready") { run.value.answer = data.answer; live.value = ""; }
        if (type === "work_summary") live.value = "";
        if (type === "text_delta" && !run.value.answer) live.value += data.text;
        if (type === "conversation_title") {conversationTitle.value=data.title;void loadHistory();}
        if (type === "plan") plan.value = data.steps;
        if (type === "tool_started")
          step.value = toolNames[data.name] || "处理任务";
        if (type === "build_updated") run.value.draft = data;
        if (type === "references") run.value.references = data.items;
        if (
          type === "needs_input" &&
          (!historical || run.value.status === "needs_input")
        ) {
          question.value = data.question;
          if (!historical) run.value.status = "needs_input";
        }
        if (type === "status" && !historical) run.value.status = data.status;
        if (type === "run_failed" && !historical) {
          run.value.status = "failed";
          run.value.error = data.message;
        }
        if (type === "run_completed" && !historical) {
          run.value.status = "completed";
          run.value.answer = data.answer;
          if(data.conversation_title) conversationTitle.value=data.conversation_title;
        }
        void bottom();
      },
      controller.signal,
    );
    if (run.value?.id === current) {
      run.value = await api.request(`/agent/runs/${current}`);
      if (run.value?.status === "completed") {
        const c = await api.request(
          `/agent/conversations/${conversation.value}`,
        );
        messages.value = c.messages;
        live.value = "";
      }
      await loadHistory();
    }
  } catch (e) {
    if ((e as Error).name !== "AbortError") error.value = (e as Error).message;
  }
}
async function submit(text = draft.value) {
  if (!user.value) {
    await router.push("/auth");
    return;
  }
  if (!text.trim() || active.value) return;
  showRequirements.value = false;
  await perform(async () => {
    if (run.value?.status === "needs_input") {
      run.value = await api.request(
        `/agent/runs/${run.value.id}/resume`,
        "POST",
        { answer: text },
      );
      question.value = "";
    } else {
      run.value = await api.request("/agent/runs", "POST", {
        message: text,
        conversation_id: conversation.value || undefined,
        build_id: route.query.build ? Number(route.query.build) : undefined,
      });
      conversation.value = run.value!.conversation_id;
      await router.replace({
        path: "/",
        query: { conversation: conversation.value },
      });
      cursor = 0;
      plan.value = [];
    }
    messages.value.push({
      id: crypto.randomUUID(),
      role: "user",
      content: text,
    });
    draft.value = "";
    live.value = "";
    await bottom();
    void connect();
    void loadHistory();
  });
}
function fresh() {
  selectionVersion++;
  controller?.abort();
  conversation.value = "";
  conversationTitle.value = "新对话";
  messages.value = [];
  run.value = null;
  activity.value = [];
  plan.value = [];
  step.value = "";
  live.value = "";
  question.value = "";
  error.value = "";
  cursor = 0;
  router.replace("/");
}
async function cancel() {
  if (!run.value) return;
  await perform(async () => {
    run.value = await api.request(
      `/agent/runs/${run.value!.id}/cancel`,
      "POST",
    );
    question.value = "";
    controller?.abort();
  });
}
async function retry() {
  if (!run.value) return;
  await perform(async () => {
    run.value = await api.request(`/agent/runs/${run.value!.id}/retry`, "POST");
    live.value = "";
    void connect();
  });
}
onMounted(() => {
  if (user.value) {
    perform(async () => {
      await loadHistory();
      if (route.query.conversation)
        await loadConversation(String(route.query.conversation));
      else if (route.query.build)
        draft.value = "请评审这套配置，指出问题和升级建议。";
    });
  }
});
onUnmounted(() => controller?.abort());
</script>
<template>
  <div class="chat-layout agent-workspace" :class="{ 'has-conversation': messages.length, 'history-collapsed':!historyOpen, 'activity-collapsed':!activityOpen }">
    <aside class="chat-history desktop-only" :aria-hidden="!historyOpen" :inert="!historyOpen"><ConversationHistory :items="history" :current="conversation" :logged-in="!!user" :archived="showArchived" @action="historyAction" @archive-view="showArchived = !showArchived; perform(loadHistory)" @toggle="historyOpen = !historyOpen" @select="id => perform(() => loadConversation(id))" @delete="id => confirm('删除该对话和任务记录？', () => deleteHistory(id))" /></aside>
    <section class="chat-main">
      <header class="chat-toolbar">
        <button
          v-if="!historyOpen" class="chat-icon-button history-toggle"
          :aria-label="historyOpen ? '收起历史侧栏' : '展开历史侧栏'"
          :aria-expanded="historyOpen"
          @click="historyOpen = !historyOpen; if(historyOpen) perform(loadHistory)"
        >
          <el-icon><Fold /></el-icon>
        </button>
        <div class="chat-heading">
          <h1>
            <span class="conversation-title" :title="conversationTitle">{{conversationTitle}}</span>
          </h1>

        </div>
        <button class="chat-icon-button activity-toggle" aria-label="执行记录" :aria-expanded="activityOpen" @click="activityOpen = !activityOpen"><el-icon><Clock /></el-icon></button>
        <button class="chat-icon-button activity-toggle-mobile" :aria-expanded="showActivityMobile" aria-label="执行记录" @click="showActivityMobile = true"><el-icon><Clock /></el-icon></button>
        <button
          class="chat-icon-button new-chat"
          aria-label="新对话"
          @click="fresh"
        >
          <el-icon><Plus /></el-icon><span>新对话</span>
        </button>
      </header>
      <div class="mobile-history-wrap" :class="{'is-hidden':!historyOpen}" :aria-hidden="!historyOpen" :inert="!historyOpen"><ConversationHistory :items="history" :current="conversation" :logged-in="!!user" :archived="showArchived" @action="historyAction" @archive-view="showArchived = !showArchived; perform(loadHistory)" @toggle="historyOpen = !historyOpen" @select="id => perform(() => loadConversation(id))" @delete="id => confirm('删除该对话和任务记录？', () => deleteHistory(id))" /></div>
      <div ref="scroll" class="chat-scroll">
        <el-alert v-if="error" :title="error" type="error" show-icon />
        <section v-if="!messages.length" class="welcome">
          <div class="welcome-orbit">
            <span class="orbit-dot orbit-blue" /><span class="orbit-core"
              >AI</span
            ><span class="orbit-dot orbit-green" /><span
              class="orbit-dot orbit-orange"
            />
          </div>
          <h2>
            <span class="mobile-welcome-title">您好，别来无恙啊！</span
            ><span class="desktop-welcome-title"
              >今天，想装一台怎样的电脑？</span
            >
          </h2>
          <p>
            <span class="mobile-welcome-subtitle"
              >AI 也可能会犯错。请核查重要信息。</span
            >
          </p>
          <div class="prompt-grid">
            <button
              class="prompt-card prompt-card-setup"
              @click="showRequirements = true"
            >
              <img class="prompt-setup-image" src="/icons/budget-need.svg" alt="" />
              <div>
                <strong>按预算设定需求</strong
                >
              </div>
              <el-icon class="prompt-arrow"><ArrowRight /></el-icon></button
            ><button
              class="prompt-card"
              @click="draft = '帮我规划白色小机箱配置，尽量安静。'"
            >
              <el-icon class="desktop-prompt-icon"><Monitor /></el-icon>
              <div>
                <strong>白色小机箱，安静一点</strong
                >
              </div>
              <el-icon class="prompt-arrow"><ArrowRight /></el-icon></button
            ><button
              class="prompt-card"
              @click="draft = '剪辑、建模和游戏我都要兼顾，请帮我规划配置。'"
            >
              <el-icon class="desktop-prompt-icon"><Cpu /></el-icon>
              <div>
                <strong>剪辑、建模和游戏都要兼顾</strong
                >
              </div>
              <el-icon class="prompt-arrow"><ArrowRight /></el-icon></button
            ><button
              class="prompt-card"
              @click="
                draft = '我已经有显卡，请先问我显卡型号，再帮我补齐其他配件。'
              "
            >
              <el-icon class="desktop-prompt-icon"><Connection /></el-icon>
              <div>
                <strong>已有显卡，帮我补齐其他配件</strong
                >
              </div>
              <el-icon class="prompt-arrow"><ArrowRight /></el-icon>
            </button>
          </div>
        </section>
        <div class="messages">
          <article
            v-for="(m,index) in visibleMessages"
            :key="m.id"
            class="message"
            :class="m.role"
          >
            <time v-if="showDate(index)" class="message-date" :datetime="m.created_at">{{messageDate(m.created_at)}}</time>
            <div v-if="m.role === 'user'" class="message-body">
              {{ m.content }}
            </div>
            <div
              v-else
              class="message-body markdown"
              v-html="markdown(m.content)"
            /><MessageAttachments :attachments="m.attachments" />
          </article>
          <section v-if="run" class="execution-panel" :class="{ 'is-active': active }" aria-live="polite">
            <details :key="run.id" class="execution-details">
              <summary><span v-if="active" class="execution-spinner" /><strong>{{ executionTitle }}</strong><span class="execution-count" v-if="totalCalls">{{ completedCalls }} / {{ totalCalls }} 项工具调用完成</span><span class="execution-disclosure">查看过程</span></summary>
              <div class="execution-detail-body">
                <ol v-if="plan.length"><li v-for="p in plan" :key="p">{{p}}</li></ol>
                <div v-for="(summary,i) in workSummaries" :key="i" class="markdown execution-summary" v-html="markdown(summary)" />
                <div v-if="live && !run.answer && active" class="markdown execution-summary" v-html="markdown(live)" />
                <p v-if="!plan.length && !live && !workSummaries.length" class="muted">正在准备处理步骤。</p>
              </div>
            </details>
            <div v-if="active || totalCalls" class="execution-progress" role="progressbar" aria-label="工具执行进度" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100"><span :style="{width: progress + '%'}" /></div>
            <p v-if="active" class="execution-hint">正在查询与校验，完成后会在下方给出方案。</p>
            <p v-if="run.error" class="execution-error">{{ run.error }}</p>
            <p v-if="question" class="content-text">{{ question }}</p>
            <div class="execution-actions"><el-button v-if="active || run.status === 'needs_input'" text @click="cancel">停止任务</el-button><el-button v-if="['failed','interrupted','cancelled'].includes(run.status)" @click="retry">继续任务</el-button></div>
          </section>
          <article v-if="run?.answer" class="message assistant"><div class="message-body markdown" v-html="markdown(run.answer)" /></article>
          <DraftCard
            v-if="run?.draft"
            :key="run.id + JSON.stringify(run.draft.partIds)"
            :draft="run.draft"
          /><el-collapse v-if="run?.references?.length"
            ><el-collapse-item title="参考资料"
              ><article
                v-for="r in run.references"
                :key="r.id"
                class="reference"
              >
                <strong>{{ r.title }}</strong>
                <p>{{ r.excerpt }}</p>
                <a
                  v-if="/^https:\/\//.test(r.source)"
                  :href="r.source"
                  target="_blank"
                  rel="noopener noreferrer"
                  >查看原文 ↗</a
                ><small>{{ new Date(r.updatedAt).toLocaleDateString() }}</small>
              </article></el-collapse-item
            ></el-collapse
          >
        </div>
      </div>
      <footer class="composer-wrap">
        <form class="composer" @submit.prevent="submit()">
          <el-popover popper-class="composer-tools-popover" :show-arrow="false" placement="top-start" :width="230" trigger="click"
            ><template #reference
              ><button
                type="button"
                class="composer-options"
                aria-label="对话工具"
              >
                <img src="/icons/chat-actions.svg" alt="" /></button
            ></template>
            <div class="composer-tools">
              <button type="button" @click="showRequirements = true">
                <el-icon><Setting /></el-icon
                ><span>按预算设定需求</span></button
              ><button type="button" @click="router.push('/builds')">
                <el-icon><Collection /></el-icon><span>选择配置进行评审</span>
              </button>
            </div></el-popover
          ><textarea
            v-model="draft"
            :placeholder="
              question ? '补充信息，继续当前任务…' : '询问任何装机问题'
            "
            rows="1"
            maxlength="8000"
            aria-label="输入装机需求"
            @keydown.enter.exact.prevent="!$event.isComposing && submit()"
          /><button
            class="composer-send"
            :class="{ ready: draft.trim() && !active }"
            type="submit"
            :disabled="!draft.trim() || !!active || busy"
            aria-label="发送消息"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </form>
      </footer>
    </section>
    <aside class="chat-activity desktop-only" :aria-hidden="!activityOpen" :inert="!activityOpen"><ToolTimeline :events="activity" :status="run?.status" @close="activityOpen = false" /></aside>
  <el-dialog v-model="shareOpen" title="分享对话" width="480px"><p>创建链接后，任何获得链接的人都可以查看当前对话快照；后续消息不会自动加入。你可以随时停止分享。</p><template v-if="shareUrl"><el-input :model-value="shareUrl" readonly /><div class="action-row"><el-button type="primary" @click="copyShare">复制链接</el-button><el-button @click="revokeShare">停止分享</el-button></div></template><el-button v-else type="primary" :loading="busy" @click="createShare">创建分享链接</el-button><el-button v-if="!shareUrl" text @click="revokeShare">停止已有分享</el-button></el-dialog>
</div>
  <el-drawer v-model="showActivityMobile" title="执行记录" direction="rtl" size="min(360px, 94vw)" :with-header="false" class="activity-drawer"><ToolTimeline :events="activity" :status="run?.status" @close="showActivityMobile = false" /></el-drawer
  ><el-dialog
    v-model="showRequirements"
    title="快速设定需求"
    width="560px"
    class="requirements-dialog"
    :show-close="true"
    ><Requirements @submit="submit"
  /></el-dialog>
</template>

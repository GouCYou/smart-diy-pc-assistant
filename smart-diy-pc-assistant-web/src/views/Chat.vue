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
async function loadHistory() {
  if (user.value) history.value = await api.request("/agent/conversations");
}
async function loadConversation(id: string) {
  const version = ++selectionVersion;
  controller?.abort();
  const c = await api.request(`/agent/conversations/${id}`);
  if (version !== selectionVersion) return;
  conversation.value = id;
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
        if (!['text_delta','usage'].includes(type) && !activity.value.some(e => e.id === id)) {
          activity.value.push({id,run_id:current,type,data,created_at:data._event_time || run.value.created_at});
        }
        if (type === "text_delta" && !run.value.answer) live.value += data.text;
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
    <aside class="chat-history desktop-only" :aria-hidden="!historyOpen" :inert="!historyOpen"><ConversationHistory :items="history" :current="conversation" :logged-in="!!user" @toggle="historyOpen = !historyOpen" @select="id => perform(() => loadConversation(id))" @delete="id => confirm('删除该对话和任务记录？', () => deleteHistory(id))" /></aside>
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
            <span class="mobile-chat-title">对话</span
            ><span class="desktop-chat-title">装机对话</span>
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
      <div class="mobile-history-wrap" :class="{'is-hidden':!historyOpen}" :aria-hidden="!historyOpen" :inert="!historyOpen"><ConversationHistory :items="history" :current="conversation" :logged-in="!!user" @toggle="historyOpen = !historyOpen" @select="id => perform(() => loadConversation(id))" @delete="id => confirm('删除该对话和任务记录？', () => deleteHistory(id))" /></div>
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
            v-for="m in messages"
            :key="m.id"
            class="message"
            :class="m.role"
          >
            <div class="message-label">
              {{ m.role === "user" ? "你" : "Smart DIY" }}
            </div>
            <div v-if="m.role === 'user'" class="message-body">
              {{ m.content }}
            </div>
            <div
              v-else
              class="message-body markdown"
              v-html="markdown(m.content)"
            />
          </article>
          <div v-if="run && (active || question || run.error || ['failed','cancelled','interrupted'].includes(run.status))" class="run-panel">
            <div class="row" style="justify-content: space-between">
              <strong>{{ runStatuses[run.status] }}</strong
              ><el-button
                v-if="active || run.status === 'needs_input'"
                text
                @click="cancel"
                >取消任务</el-button
              >
            </div>
            <ol v-if="plan.length">
              <li v-for="p in plan" :key="p">{{ p }}</li>
            </ol>
            <p v-if="active" class="muted">{{ step || "正在理解你的需求" }}…</p>
            <p v-if="run.error" class="content-text">{{ run.error }}</p>
            <el-button
              v-if="['failed', 'interrupted', 'cancelled'].includes(run.status)"
              @click="retry"
              >重试任务</el-button
            >
            <p v-if="question" class="content-text">{{ question }}</p>
          </div>
          <div
            v-if="live && !run?.answer"
            class="message-body markdown"
            v-html="markdown(live)"
          />
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
          <el-popover placement="top-start" :width="230" trigger="click"
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

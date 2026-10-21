<script setup lang="ts">
import { reactive } from "vue";
const emit = defineEmits<{ submit: [message: string] }>(),
  form = reactive({
    budget: 6000,
    use: "游戏",
    resolution: "2K",
    cpu: "不限制",
    gpu: "不限制",
    look: "不限制",
    extra: "",
  });
function submit() {
  emit(
    "submit",
    `帮我规划一套装机方案。预算 ${form.budget} 元，用途：${form.use}，分辨率：${form.resolution}，CPU 偏好：${form.cpu}，显卡偏好：${form.gpu}，外观：${form.look}。${form.extra}`,
  );
}
</script>
<template>
  <form class="requirements-form" @submit.prevent="submit">
    <div class="requirement-section budget-section">
      <div class="budget-heading">
        <strong>预算</strong><b>¥{{ form.budget.toLocaleString() }}</b>
      </div>
      <el-slider v-model="form.budget" :min="1000" :max="50000" :step="500" />
      <div class="budget-range"><span>¥1000</span><span>¥50000</span></div>
      <label class="budget-custom"
        >自定义预算
        <div>
          <span>¥</span
          ><input
            v-model.number="form.budget"
            type="number"
            min="1000"
            max="100000"
            aria-label="自定义预算"
          /></div
      ></label>
    </div>
    <div
      v-for="group in [
        {
          key: 'use' as const,
          title: '使用场景',
          items: ['游戏', '剪辑', '办公', '编程', 'AI', '其他'],
        },
        {
          key: 'cpu' as const,
          title: 'CPU 倾向',
          items: ['不限制', 'AMD', 'Intel'],
        },
        {
          key: 'gpu' as const,
          title: '显卡倾向',
          items: ['不限制', 'NVIDIA', 'AMD', 'Intel'],
        },
        {
          key: 'resolution' as const,
          title: '目标分辨率',
          items: ['1080P', '2K', '4K'],
        },
        {
          key: 'look' as const,
          title: '外观偏好',
          items: ['不限制', '白色简约', '黑色低调', '小巧机箱'],
        },
      ]"
      :key="group.key"
      class="requirement-section"
    >
      <strong>{{ group.title }}</strong>
      <div v-slider class="choice-chips">
        <button
          v-for="s in group.items"
          :key="s"
          type="button"
          :class="{ selected: form[group.key] === s }"
          @click="form[group.key] = s"
        >
          {{ s }}
        </button>
      </div>
    </div>
    <div class="requirement-section">
      <strong>补充需求</strong
      ><textarea
        v-model="form.extra"
        rows="3"
        placeholder="具体游戏、已有硬件、静音需求…"
      />
    </div>
    <div class="requirement-submit">
      <button type="submit">发送给 AI</button>
    </div>
  </form>
</template>

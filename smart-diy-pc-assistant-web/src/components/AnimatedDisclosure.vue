<script setup lang="ts">
import {useId} from 'vue';
const open=defineModel<boolean>({default:false}),id=useId();
</script>
<template><section class="animated-disclosure" :class="{'is-open':open}"><button type="button" class="disclosure-trigger" :aria-expanded="open" :aria-controls="id" @click="open=!open"><slot name="summary"/></button><div :id="id" class="disclosure-grid" :inert="!open" :aria-hidden="!open"><div class="disclosure-content"><slot/></div></div></section></template>

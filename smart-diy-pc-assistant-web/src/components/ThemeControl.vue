<script setup lang="ts">
import {Sunny,Moon} from '@element-plus/icons-vue';
import {darkTheme,setTheme} from '../lib/theme';
</script>
<template><div class="theme-control"><button class="theme-switch" :class="{'is-dark':darkTheme}" type="button" role="switch" :aria-checked="darkTheme" :aria-label="darkTheme?'切换为白天模式':'切换为黑夜模式'" @click="setTheme(darkTheme?'light':'dark')"><span class="theme-thumb"/><el-icon><Sunny/></el-icon><el-icon><Moon/></el-icon></button></div></template>

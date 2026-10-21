<script setup lang="ts">
import {Sunny,Moon,Monitor} from '@element-plus/icons-vue';
import {themeMode,darkTheme,setTheme} from '../lib/theme';
</script>
<template><div class="theme-control"><span class="theme-label">外观</span><button class="theme-switch" :class="{'is-dark':darkTheme}" type="button" role="switch" :aria-checked="darkTheme" :aria-label="darkTheme?'切换为白天模式':'切换为黑夜模式'" @click="setTheme(darkTheme?'light':'dark')"><span class="theme-thumb"/><el-icon><Sunny/></el-icon><el-icon><Moon/></el-icon></button><button class="theme-system" :class="{'is-selected':themeMode==='system'}" :aria-pressed="themeMode==='system'" aria-label="跟随系统外观" title="跟随系统外观" @click="setTheme('system')"><el-icon><Monitor/></el-icon></button></div></template>

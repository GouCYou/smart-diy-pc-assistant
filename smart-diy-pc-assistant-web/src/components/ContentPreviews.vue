<script setup lang="ts">
import {ref,watch} from 'vue';
import {previewBuildId,previewNewsId,previewImages,previewImageIndex} from '../lib/previews';
import {api} from '../lib/api';
import ProductParts from './ProductParts.vue';
import Detail from '../views/Detail.vue';
const build=ref<any>(),error=ref(''),loading=ref(false);
watch(previewBuildId,async id=>{build.value=null;error.value='';if(!id)return;loading.value=true;try{const result=await api.request('/api/build-configs/public/'+id);if(previewBuildId.value===id){if(!result)throw Error("配置不存在或已删除");build.value=result;}}catch(e){error.value=(e as Error).message;}finally{loading.value=false;}});
</script>
<template>
<el-dialog :model-value="!!previewBuildId" title="配置预览" width="800px" class="content-preview-dialog" destroy-on-close @close="previewBuildId=null"><div v-loading="loading"><el-alert v-if="error" :title="error" type="error"/><template v-if="build"><h2>{{build.title}}</h2><ProductParts :parts="build.parts"/><details class="compatibility-details"><summary>兼容性与待核实事项</summary><ul><li v-for="text in build.compatibilityMessages" :key="text">{{text}}</li></ul></details><strong class="preview-total">已报价合计 ¥{{build.totalPrice?.toLocaleString()}}</strong></template></div></el-dialog>
<el-dialog :model-value="!!previewNewsId" title="硬件资讯" width="860px" class="content-preview-dialog" destroy-on-close @close="previewNewsId=null"><Detail v-if="previewNewsId" :id="previewNewsId" kind="news" embedded @close="previewNewsId=null"/></el-dialog>
<el-image-viewer v-if="previewImages.length" :url-list="previewImages" :initial-index="previewImageIndex" teleported :z-index="10000" @close="previewImages=[]"/>
</template>

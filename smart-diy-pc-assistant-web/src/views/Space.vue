<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { UserFilled } from '@element-plus/icons-vue';
import { api } from '../lib/api';
import { authenticated } from '../lib/session';
import { useTask } from '../lib/task';
import EmptyState from '../components/EmptyState.vue';
import DialogHeading from '../components/DialogHeading.vue';
const route=useRoute(), {busy,error,perform}=useTask(), space=ref<any>(null), section=ref(route.query.tab ? 'favorites':'posts'), tab=ref(String(route.query.tab || 'builds')), people=ref<any[]>([]), showPeople=ref(false), peopleKind=ref('following');
async function load() {space.value=await api.request(`/api/users/${route.params.id}/space`);}
async function follow() {authenticated();space.value=await api.request(`/api/users/${route.params.id}/follow`,'POST');}
async function follows(kind:string) {authenticated();peopleKind.value=kind;people.value=await api.request(`/api/users/${route.params.id}/${kind}`);showPeople.value=true;}
watch(() => route.params.id, () => perform(load),{immediate:true});
</script>
<template>
  <div class="space-page"><el-alert v-if="error" :title="error" type="error" />
    <template v-if="space">
      <section class="space-hero" :style="space.backgroundUrl ? {backgroundImage:`linear-gradient(#ffffffbb,#ffffffed),url(${space.backgroundUrl})`} : {}">
        <div class="space-identity"><el-avatar :size="64" :src="space.avatarUrl"><el-icon><UserFilled /></el-icon></el-avatar><div><h1>{{space.nickname || space.username}}</h1><span>@{{space.username}}</span></div></div>
        <p class="space-bio">{{space.bio || '这个人还没有写简介。'}}</p>
        <div class="space-stats"><button @click="perform(() => follows('following'))"><strong>{{space.followingCount}}</strong>关注</button><button @click="perform(() => follows('followers'))"><strong>{{space.followerCount}}</strong>粉丝</button><span><strong>{{space.posts.length}}</strong>动态</span></div>
        <p v-if="space.profilePublic" class="muted space-location">{{[space.countryOrRegion,space.province,space.city].filter(Boolean).join(' · ')}}</p>
        <router-link v-if="space.mine" to="/profile" class="space-edit"><el-button round>编辑资料</el-button></router-link><el-button v-else round :loading="busy" @click="perform(follow)">{{space.following ? '已关注' : '关注'}}</el-button>
      </section>
      <el-alert v-if="!space.spacePublic && !space.mine" title="个人空间已隐藏" type="info" />
      <template v-else>
        <div v-slider class="segmented space-segments"><button :class="{selected:section === 'posts'}" @click="section = 'posts'">动态</button><button :class="{selected:section === 'favorites'}" @click="section = 'favorites'">收藏</button></div>
        <template v-if="section === 'posts'"><article v-for="p in space.posts" :key="p.id" class="panel"><router-link :to="`/community/${p.id}`"><p class="content-text">{{p.content}}</p><div v-if="p.imageUrls?.length" class="image-grid"><el-image v-for="img in p.imageUrls.slice(0,3)" :key="img" :src="img" fit="cover" /></div><span class="muted">{{p.createdAt?.slice(0,10)}} · {{p.likeCount}} 赞 · {{p.commentCount}} 评论</span></router-link></article><EmptyState v-if="!space.posts.length" description="还没有动态" kind="chat" /></template>
        <template v-else><div v-slider class="favorite-tabs"><button v-for="[key,label] in [['builds','配置'],['favorites','动态'],['news','热点']]" :key="key" :class="{selected:tab === key}" @click="tab = key!">{{label}}</button></div>
          <template v-if="tab === 'builds'"><router-link v-for="b in space.favoriteBuilds" :key="b.id" :to="`/builds?public=${b.id}`" class="panel favorite-item">{{b.title}}<span>¥{{b.totalPrice}}</span></router-link><EmptyState v-if="!space.favoriteBuilds.length" description="还没有收藏配置" /></template>
          <template v-if="tab === 'favorites'"><router-link v-for="p in space.favoritePosts" :key="p.id" :to="`/community/${p.id}`" class="panel favorite-item">{{p.content.slice(0,150)}}</router-link><EmptyState v-if="!space.favoritePosts.length" description="还没有收藏动态" kind="chat" /></template>
          <template v-if="tab === 'news'"><router-link v-for="n in space.favoriteNews" :key="n.id" :to="`/news/${n.id}`" class="panel favorite-item">{{n.title}}</router-link><EmptyState v-if="!space.favoriteNews.length" description="还没有收藏热点" kind="news" /></template>
        </template>
      </template>
    </template>
    <el-dialog v-model="showPeople" width="500px" class="mobile-full-dialog" :show-close="false"><template #header><DialogHeading :title="peopleKind === 'following' ? '关注' : '粉丝'" @close="showPeople = false" /></template><div v-slider class="segmented"><button :class="{selected:peopleKind === 'following'}" @click="perform(() => follows('following'))">关注</button><button :class="{selected:peopleKind === 'followers'}" @click="perform(() => follows('followers'))">粉丝</button></div><router-link v-for="p in people" :key="p.userId" :to="`/users/${p.userId}`" class="reference" @click="showPeople = false"><div class="post-author"><el-avatar :src="p.avatarUrl"><el-icon><UserFilled /></el-icon></el-avatar><strong>{{p.nickname || p.username}}</strong></div></router-link><EmptyState v-if="!people.length" :description="peopleKind === 'following' ? '暂无关注' : '暂无粉丝'" /></el-dialog>
  </div>
</template>

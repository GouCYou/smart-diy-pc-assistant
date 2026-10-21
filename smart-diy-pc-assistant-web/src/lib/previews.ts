import type {Draft} from '../../../shared/types';
import {ref} from 'vue';
export const previewBuildId=ref<number|null>(null),previewNewsId=ref<number|null>(null);
export const previewImages=ref<string[]>([]),previewImageIndex=ref(0);
export function openImages(urls:string[],index=0){previewImages.value=urls.filter(u=>/^https?:\/\//.test(u));previewImageIndex.value=index;}

export const previewDraft=ref<{draft:Draft;readOnly:boolean}|null>(null);

export const draftSaveIds=new WeakMap<Draft,number>();

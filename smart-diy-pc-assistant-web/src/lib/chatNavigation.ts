import { ref } from 'vue';
export const newChatVersion=ref(0);
export const lastChatConversation=ref('');
export function runningConversation(items:{id:string;status?:string;updated_at?:string}[],last:string){
 const active=items.filter(item=>['queued','running'].includes(item.status || ''));
 return active.find(item=>item.id===last) || active.sort((a,b)=>Date.parse(b.updated_at || '')-Date.parse(a.updated_at || ''))[0];
}

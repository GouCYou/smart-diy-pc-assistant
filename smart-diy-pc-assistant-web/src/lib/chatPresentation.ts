import type {Draft} from '../../../shared/types.ts';
export function quotedDraft(draft?:Draft|null):draft is Draft {
 return !!draft && Array.isArray(draft.parts) && draft.parts.length>0 && draft.validation?.unpricedParts?.length===0 && draft.validation.quotedTotal>0 && draft.parts.every(p=>Number(draft.partSelections?.find(q=>q.hardwarePartId===p.id)?.price ?? p.selectedPrice ?? p.productPrice)>0);
}
export function elapsedLabel(seconds:number){
 const value=Math.max(0,Math.floor(seconds));
 if(value>=3600)return `${Math.floor(value/3600)}h ${Math.floor(value%3600/60)}m`;
 if(value>=60)return `${Math.floor(value/60)}m ${value%60}s`;
 return `${value}s`;
}

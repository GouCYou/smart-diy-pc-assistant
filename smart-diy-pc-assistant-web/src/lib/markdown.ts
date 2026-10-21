import { marked } from "marked";
import DOMPurify from "dompurify";
export function markdown(text: string) {
  // Some legacy replies escaped formatting twice. Preserve code while repairing prose.
  const normalized = (text || "").split(/(```[\s\S]*?```|`[^`\n]+`)/g).map((part,index) => index % 2 ? part : part.replace(/\\\*\\\*([\s\S]+?)\\\*\\\*/g,"**$1**")).join("");
  return DOMPurify.sanitize(marked.parse(normalized,{async:false,gfm:true,breaks:true}) as string);
}

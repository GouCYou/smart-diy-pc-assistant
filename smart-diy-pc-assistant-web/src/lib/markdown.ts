import DOMPurify from 'dompurify';
import { parseMarkdown } from './markdownParser';
export function markdown(text:string){return DOMPurify.sanitize(parseMarkdown(text));}

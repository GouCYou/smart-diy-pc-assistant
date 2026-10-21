import { marked } from "marked";
import DOMPurify from "dompurify";
export const markdown = (text: string) =>
  DOMPurify.sanitize(marked.parse(text || "", { async: false }) as string);

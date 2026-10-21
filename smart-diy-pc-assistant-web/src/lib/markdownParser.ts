import { Marked } from 'marked';
const parser=new Marked({gfm:true,breaks:true});
parser.use({extensions:[{
  name:'relaxedStrong',level:'inline',
  start(src:string){return src.indexOf('**');},
  tokenizer(src:string){
    if(src.startsWith('***'))return;
    const match=/^\*\*([^\n]+?)\*\*/.exec(src);
    if(!match || !match[1]!.trim())return;
    return {type:'relaxedStrong',raw:match[0],text:match[1]!.trim(),tokens:this.lexer.inlineTokens(match[1]!.trim())};
  },
  renderer(token){return `<strong>${this.parser.parseInline(token.tokens!)}</strong>`;}
}]});
export function parseMarkdown(text:string){
  const normalized=(text || '').split(/(```[\s\S]*?```|~~~[\s\S]*?~~~|`[^`\n]+`)/g).map((part,index)=>index%2 ? part : part.replace(/\\\*\\\*([\s\S]+?)\\\*\\\*/g,'**$1**').replace(/爬取|抓取/g,'获取')).join('');
  return parser.parse(normalized,{async:false}) as string;
}

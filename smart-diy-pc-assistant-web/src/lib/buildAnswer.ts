// Preserve stored Markdown; only hide explicit duplicate configuration sections.
export function buildAnswer(text:string,hasConfiguration:boolean){
  if(!hasConfiguration)return text;
  let omittedLevel=0, fence='';
  return text.split('\n').filter(line=>{
    const codeFence=line.match(/^\s{0,3}(`{3,}|~{3,})/);
    if(codeFence){if(!fence)fence=codeFence[1]![0]!;else if(codeFence[1]![0]===fence)fence='';return !omittedLevel;}
    if(fence)return !omittedLevel;
    const heading=line.match(/^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/);
    if(heading){
      const level=heading[1]!.length;
      if(omittedLevel && level>omittedLevel)return false;
      omittedLevel=/^(?:\d+[.、]\s*)?(详细配置|商品链接|配置清单|配件清单|具体配置)[：:]?$/.test(heading[2]!.replace(/\*\*/g,'')) ? level : 0;
    }
    return !omittedLevel;
  }).join('\n').trim();
}

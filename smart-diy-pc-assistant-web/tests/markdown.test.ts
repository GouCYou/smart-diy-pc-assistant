import test from 'node:test';
import assert from 'node:assert/strict';
import { parseMarkdown } from '../src/lib/markdownParser.ts';
test('Chinese quoted strong text and surrounding spaces render without literal delimiters',()=>{
 const result=parseMarkdown('如果你愿意，下一步给你出一版** “2 万内更适合你的白色简约最终配置” **。');
 assert.match(result,/<strong>“2 万内更适合你的白色简约最终配置”<\/strong>/);
 assert.ok(!result.includes('**'));
});
test('Code keeps literal stars while ordinary Markdown and public wording remain intact',()=>{
 const result=parseMarkdown('**CPU**、[商品](https://item.jd.com/123.html)\n\n`**code**`\n\n```txt\n**literal**\n```\n\n从京东爬取的价格');
 assert.match(result,/<strong>CPU<\/strong>/);assert.match(result,/<code>\*\*code\*\*<\/code>/);assert.match(result,/\*\*literal\*\*/);assert.match(result,/从京东获取的价格/);
});

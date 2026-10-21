import test from 'node:test';
import assert from 'node:assert/strict';
import {buildAnswer} from '../src/lib/buildAnswer.ts';
test('configuration cards replace only their duplicate sections',()=>{
 const source='## 详细配置\n|型号|价格|\n|---|---|\n|CPU|1000|\n### 内存补充\n规格\n## 商品链接\n- https://example.com\n## 为什么这样配\n预算优先投入显卡。';
 assert.equal(buildAnswer(source,true),'## 为什么这样配\n预算优先投入显卡。');
 assert.equal(buildAnswer(source,false),source);
});
test('general Markdown tables, prose and code fences remain intact',()=>{
 const source='## 兼容性对比\n|平台|差异|\n|---|---|\n|AM5|DDR5|\n\n```md\n## 配置清单\n示例文本\n```\n\n## 建议\n**核对规格**。';
 assert.equal(buildAnswer(source,true),source);
});

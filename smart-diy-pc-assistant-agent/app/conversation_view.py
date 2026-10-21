"""Compatibility view for preserved Java conversation messages and compact titles."""
import json
import re


def short_title(text):
    text = re.sub(r'\s+', ' ', text).strip()
    amount = re.search(r'预算\s*[¥￥]?\s*(\d{3,6})', text)
    use = next((label for key,label in [('游戏','游戏装机'),('剪辑','剪辑装机'),('办公','办公装机'),('升级','电脑升级'),('新闻','硬件新闻'),('故障','故障排查')] if key in text), '')
    if amount:
        use = use or "装机方案"
        return f'{amount[1]} 元{use}'
    if use and len(text)>24:
        return use
    text = re.sub(r'^(帮我|请|你好[，,！!]?)', '', text).strip(' ，,。')
    return text[:20] or '新对话'


def message_view(row):
    row = dict(row)
    content = row['content']
    attachments = {}
    build = re.search(r'\n\n关联配置[：:]\s*(.*?)（ID\s*(\d+)）', content)
    if build:
        attachments['build'] = {'id':int(build[2]),'title':build[1].strip() or '关联配置'}
        content = content[:build.start()] + content[build.end():]
    news = re.search(r'\n\n历史新闻引用[：:]\s*(\[.*\])\s*$',content,re.S)
    if news:
        try:
            parsed=json.loads(news[1])
            if isinstance(parsed,list):
                attachments['news']=[{k:item.get(k) for k in ('id','title','summary','coverUrl','tag')} for item in parsed if isinstance(item,dict) and isinstance(item.get('id'),int)]
                content=content[:news.start()]
        except (ValueError,TypeError):
            pass  # Preserve unreadable source data rather than silently discarding it.
    row['content'],row['attachments']=content.strip(),attachments
    return row

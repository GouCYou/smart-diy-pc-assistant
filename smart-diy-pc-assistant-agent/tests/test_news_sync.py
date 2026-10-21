from app.news_sync import parse_list,parse_article

def test_news_list_dedicated_article_links_and_dates():
    html='''<ul class="bl"><li><a class="title" href="https://www.ithome.com/1/016/053.htm">硬件新闻</a><div data-ot="2026-10-21T18:19:01.2230000+08:00"></div><img data-original="https://img.ithome.com/a.jpg"></li><li><a class="title" href="https://evil.example/a">坏链接</a></li></ul>'''
    rows=parse_list(html)
    assert len(rows)==1 and rows[0]['publishedAt'].startswith('2026-10-21T18:19:01')

def test_article_preserves_layout_and_images_but_strips_executable_content():
    html='<div id="paragraph"><h2>测试标题</h2><p>'+('硬件内容。'*20)+'<strong>重点</strong></p><img src="https://img.ithome.com/test.jpg"><script>alert(1)</script><iframe src="https://evil.test"></iframe><ul><li>项目一</li></ul></div>'
    text,images=parse_article(html,'https://www.ithome.com/1/016/053.htm')
    assert '## 测试标题' in text and '**重点**' in text and '- 项目一' in text
    assert images==['https://img.ithome.com/test.jpg'] and 'alert(1)' not in text and 'evil.test' not in text
    assert '来源：[IT之家]' in text

import pytest,httpx
from app.news_sync import NewsSync

@pytest.mark.asyncio
async def test_sync_uses_java_for_import_and_cleanup_then_skips_known(monkeypatch):
    import app.news_sync as module
    list_html='<ul class="bl"><li><a class="title" href="https://www.ithome.com/1/016/053.htm">新闻</a><div data-ot="2026-10-21T18:19:01+08:00"></div></li></ul>'
    async def respond(request):
        return httpx.Response(200,text=list_html if request.url.host=='digi.ithome.com' else '<div id="paragraph"><p>'+('正文内容'*30)+'</p></div>')
    original=httpx.AsyncClient
    monkeypatch.setattr(module.httpx,'AsyncClient',lambda **kwargs:original(transport=httpx.MockTransport(respond)))
    class Store:
        async def execute(self,*args):pass
    class Sync(NewsSync):
        calls=[];known=[]
        async def call(self,path,payload=None):
            self.calls.append(path)
            if path=='/state':return {'urls':self.known,'cutoff':'2026-09-21T00:00:00'}
            if path=='/article':self.known.append(payload['sourceUrl']);return {'id':1}
            return {'deleted':0,'failed':0}
    sync=Sync(Store(),None,'private-test-key')
    first=await sync.run();second=await sync.run()
    assert first['added']==1 and second['added']==0 and second['skipped']==1
    assert sync.calls.count('/article')==1 and sync.calls.count('/cleanup')==2

"""Daily IT Home hardware import. Java remains owner of news and media storage."""
import asyncio,base64,calendar,json,re
from datetime import datetime,timedelta,timezone
from urllib.parse import urljoin,urlparse
import httpx
from bs4 import BeautifulSoup, NavigableString
UTC8=timezone(timedelta(hours=8))
LIST_URL='https://digi.ithome.com/acc'
ARTICLE=re.compile(r'^https://www\.ithome\.com/\d+/\d+/\d+\.htm$')
def parse_list(html):
    soup=BeautifulSoup(html,'html.parser');items=[]
    for node in soup.select('ul.bl li'):
        a=node.select_one('a.title');date=node.select_one('[data-ot]');img=node.select_one('img')
        if not a or not date or not ARTICLE.fullmatch(a.get('href','')):continue
        try: published=datetime.fromisoformat(date['data-ot'])
        except ValueError:continue
        items.append({'sourceUrl':a['href'],'title':a.get_text(' ',strip=True)[:120],'publishedAt':published.astimezone(UTC8).replace(tzinfo=None).isoformat(),'cover':urljoin(LIST_URL,img.get('data-original',img.get('src',''))) if img else ''})
    return items

def parse_article(html,url):
    soup=BeautifulSoup(html,'html.parser');body=soup.select_one('#paragraph')
    if not body:raise ValueError('article body missing')
    for el in body.select('script,style,iframe,.tougao-user,.ad,.adblock,form'):el.decompose()
    images=[]
    def convert(node):
        if isinstance(node,NavigableString):return str(node)
        name=node.name
        if name=='img':
            src=urljoin(url,node.get('data-original') or node.get('data-src') or node.get('src',''))
            if urlparse(src).hostname not in {'img.ithome.com','img.ithome.net'}:return ''
            if src not in images:images.append(src)
            return '\n\n!['+node.get('alt','').replace(']','')[:100]+']('+src+')\n\n'
        text=''.join(convert(c) for c in node.children)
        if name in ('strong','b'):return '**'+text.strip()+'**'
        if name in ('em','i'):return '*'+text.strip()+'*'
        if name in ('h2','h3','h4'):return '\n\n'+('#'*int(name[1]))+' '+text.strip()+'\n\n'
        if name=='li':return '\n- '+text.strip()+'\n'
        if name=='a':
            link=urljoin(url,node.get('href',''))
            return '['+text.strip()+']('+link+')' if link.startswith('https://') else text
        if name=='br':return '\n'
        if name=='table':
            rows=[[c.get_text(' ',strip=True).replace('|','\\|') for c in row.find_all(['th','td'],recursive=False)] for row in node.find_all('tr')]
            rows=[r for r in rows if r]
            return '\n\n'+'\n'.join('| '+' | '.join(r)+' |'+ ('\n| '+' | '.join(['---']*len(r))+' |' if i==0 else '') for i,r in enumerate(rows))+'\n\n'
        if name=='tr':return '\n| '+' | '.join(c.get_text(' ',strip=True) for c in node.find_all(['th','td'],recursive=False))+' |\n'
        if name in ('p','div','section','ul','ol','table'):return '\n\n'+text.strip()+'\n\n'
        return text
    content=re.sub(r'\n{3,}','\n\n',convert(body)).strip()
    if len(content)<50:raise ValueError('article body too short')
    return content+'\n\n---\n来源：[IT之家]('+url+')',images

class NewsSync:
    def __init__(self,store,client,token):self.store,self.client,self.token=store,client,token;self.lock=asyncio.Lock()
    async def call(self,path,payload=None):
        r=await self.client.request('POST' if payload is not None else 'GET','/api/internal/news-sync'+path,headers={'X-News-Sync-Key':self.token},json=payload,timeout=180);r.raise_for_status();return r.json()['data']
    async def run(self):
        if not self.token:return {'enabled':False}
        if self.lock.locked():return {'running':True}
        async with self.lock:
            report={'added':0,'skipped':0,'failed':0,'startedAt':datetime.now(UTC8).isoformat()}
            try:
                state=await self.call('/state');known=set(state['urls']);cutoff=datetime.fromisoformat(state['cutoff'])
                async with httpx.AsyncClient(timeout=30,follow_redirects=False,headers={'User-Agent':'SmartDIY-News/1.0 (+https://smartdiy.cctstudio.cn)'}) as web:
                    r=await web.get(LIST_URL);r.raise_for_status()
                    for item in parse_list(r.text)[:40]:
                        if item['sourceUrl'] in known or datetime.fromisoformat(item['publishedAt'])<cutoff:report['skipped']+=1;continue
                        try:
                            response=await web.get(item['sourceUrl']);response.raise_for_status();content,images=parse_article(response.text,item['sourceUrl'])
                            if item['cover'] and item['cover'] not in images:images.insert(0,item['cover'])
                            if len(images)>24:raise ValueError('too many images')
                            uploads=[]
                            for src in images:
                                if urlparse(src).hostname not in {'img.ithome.com','img.ithome.net'}:continue
                                image=await web.get(src);image.raise_for_status();mime=image.headers.get('content-type','').split(';')[0]
                                if not mime.startswith('image/') or len(image.content)>5_000_000:raise ValueError('invalid article image')
                                uploads.append({'url':src,'mime':mime,'data':base64.b64encode(image.content).decode()})
                            await self.call('/article',{**item,'content':content[:100000],'images':uploads});report['added']+=1
                        except Exception as exc:
                            report['failed']+=1
                            report.setdefault('failureReasons',[]).append({'url':item['sourceUrl'],'type':type(exc).__name__})
                        await asyncio.sleep(1)
                report['cleanup']=await self.call('/cleanup',{})
            except Exception:report['error']='资讯同步失败，请检查来源站点或业务服务连接'
            report['finishedAt']=datetime.now(UTC8).isoformat()
            await self.store.execute("INSERT INTO settings(key,value) VALUES('news_sync_report',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",(json.dumps(report,ensure_ascii=False),))
            return report
    async def loop(self):
        while True:
            try:
                row=await self.store.one("SELECT value FROM settings WHERE key='news_sync_report'")
                last=json.loads(row['value']) if row else {}
                today=datetime.now(UTC8).date().isoformat()
                if last.get('error') or not last.get('finishedAt','').startswith(today):await self.run()
            except asyncio.CancelledError:raise
            except Exception:pass
            await asyncio.sleep(3600)

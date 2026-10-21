import asyncio
import json
from contextlib import asynccontextmanager
import httpx
import pytest
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult
from app.compatibility import check
from app.config import Settings
from app.crawler import Crawler, public_url
from app.knowledge import Knowledge
from app.runtime import Runtime
from app.store import Store, now, uid
from app.main import app


PARTS = [
    dict(id=1,type='CPU',model='CPU 7600',socketType='AM5',wattage=65,enabled=True),
    dict(id=2,type='MOTHERBOARD',model='B650',socketType='AM5',memoryType='DDR5',formFactor='ATX',enabled=True),
    dict(id=3,type='MEMORY',model='DDR5 32GB',memoryType='DDR5',enabled=True),
    dict(id=4,type='STORAGE',model='SSD 1TB',enabled=True),
    dict(id=5,type='PSU',model='PSU 650W',wattage=650,enabled=True),
    dict(id=6,type='CASE',model='CASE ATX',supportedFormFactors='ATX,MATX',coolerHeightMm=170,enabled=True),
    dict(id=7,type='COOLER',model='COOLER',coolerHeightMm=155,enabled=True),
]


class ScriptedModel(BaseChatModel):
    responses: list
    index: int = 0
    @property
    def _llm_type(self): return 'test-scripted-tool-model'
    def bind_tools(self, tools, **kwargs): return self
    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        answer = self.responses[min(self.index, len(self.responses)-1)]
        self.index += 1
        return ChatResult(generations=[ChatGeneration(message=answer)])


def call(name, args, id='tool-1'):
    return AIMessage(content='', tool_calls=[dict(name=name,args=args,id=id,type='tool_call')])


@pytest.fixture
async def env(tmp_path, monkeypatch):
    cfg=Settings(data_dir=tmp_path,api_key='test-key',model='test-model')
    store=Store(tmp_path/'agent.sqlite');await store.connect()
    def business(request):
        token=request.headers.get('authorization','')
        if token not in ('Bearer admin','Bearer user','Bearer other'):
            return httpx.Response(401,json={'message':'登录失效'})
        if request.url.path=='/api/auth/me':
            return httpx.Response(200,json={'code':0,'data':{'userId':2 if token=='Bearer other' else 1,'username':'test','role':'ADMIN' if token=='Bearer admin' else 'USER'}})
        if request.url.path=='/api/hardware-parts':
            return httpx.Response(200,json={'code':0,'data':PARTS})
        return httpx.Response(404,json={})
    client=httpx.AsyncClient(transport=httpx.MockTransport(business),base_url='http://java.test')
    crawler=Crawler();knowledge=Knowledge(store,cfg);runtime=Runtime(store,knowledge,crawler,client,cfg);await runtime.start()
    app.state.store,app.state.client,app.state.runtime,app.state.knowledge,app.state.crawler=store,client,runtime,knowledge,crawler
    web=httpx.AsyncClient(transport=httpx.ASGITransport(app=app),base_url='http://agent.test',headers={'Authorization':'Bearer user'})
    yield store,runtime,knowledge,crawler,web,monkeypatch
    await web.aclose();await runtime.close();await crawler.close();await client.aclose();await store.close()


async def done(store, run_id):
    for _ in range(100):
        row=await store.run(run_id)
        if row['status'] not in ('queued','running'): return row
        await asyncio.sleep(.02)
    pytest.fail('Agent did not reach a terminal/pause state')


async def test_actual_langgraph_tool_loop_and_draft(env):
    store,runtime,k,crawler,web,mp=env
    model=ScriptedModel(responses=[call('record_plan',{'steps':['筛选硬件','校验配置']}),call('search_hardware',{'query':'','limit':20}),call('propose_build',{'title':'测试配置','selections':[{'hardware_id':p['id']} for p in PARTS],'budget':6000}),AIMessage(content='方案已准备，所有报价待核实。')])
    mp.setattr('app.runtime.ChatOpenAI',lambda **kwargs:model)
    response=await web.post('/agent/runs',json={'message':'6000 元办公配置'})
    assert response.status_code==201
    run=await done(store,response.json()['id'])
    assert run['status']=='completed',run
    assert run['draft']['validation']['quotedTotal']==0
    assert len(run['draft']['validation']['unpricedParts'])==7
    assert run['answer']=='方案已准备，所有报价待核实。'
    events=await web.get(f"/agent/runs/{run['id']}/events")
    assert 'event: build_updated' in events.text and 'event: run_completed' in events.text
    conversation=await web.get('/agent/conversations/'+run['conversation_id'])
    assert len(conversation.json()['messages'])==2


async def test_interrupt_resume_and_authorization(env):
    store,runtime,k,crawler,web,mp=env
    model=ScriptedModel(responses=[call('ask_user',{'question':'预算是多少？'}),AIMessage(content='已记录预算。')])
    mp.setattr('app.runtime.ChatOpenAI',lambda **kwargs:model)
    run=(await web.post('/agent/runs',json={'message':'帮我配电脑'})).json()
    paused=await done(store,run['id']);assert paused['status']=='needs_input',paused
    assert '预算是多少' in (await web.get(f"/agent/runs/{run['id']}/events")).text
    conflict=await web.post('/agent/runs',json={'message':'another','conversation_id':run['conversation_id']})
    assert conflict.status_code==409
    forbidden=await web.get('/agent/runs/'+run['id'],headers={'Authorization':'Bearer other'})
    assert forbidden.status_code==404
    assert (await web.get('/agent/admin/settings')).status_code==403
    assert (await web.get('/agent/conversations',headers={'Authorization':'Bearer invalid'})).status_code==401
    resumed=await web.post(f"/agent/runs/{run['id']}/resume",json={'answer':'6000 元'})
    assert resumed.status_code==200
    final=await done(store,run['id']);assert final['status']=='completed',final


async def test_rag_reindex_delete_and_no_secret_response(env):
    store,runtime,k,crawler,web,mp=env
    headers={'Authorization':'Bearer admin'}
    doc=(await web.post('/agent/admin/documents',headers=headers,json={'title':'AM5 手册','content':'AM5 平台搭配 DDR5 内存。CPU 插槽必须一致。','source':'厂商资料'})).json()
    results=(await web.get('/agent/admin/search?q=AM5%20DDR5',headers=headers)).json()
    assert results[0]['documentId']==doc['id']
    assert not await k.search('unrelated xyz9000')
    await web.post(f"/agent/admin/documents/{doc['id']}/reindex",headers=headers)
    assert len(await store.all('SELECT * FROM chunks'))==1
    await web.delete('/agent/admin/documents/'+doc['id'],headers=headers)
    assert not await k.search('AM5')
    cfg=(await web.get('/agent/admin/settings',headers=headers)).json()
    assert 'api_key' not in cfg


async def test_tool_rejects_invented_quote_and_wrong_socket(env):
    store,runtime,k,crawler,web,mp=env
    run={'id':'fake','references':[]}
    # Tools rely on persisted run for event FK.
    cid=uid();await store.execute('INSERT INTO conversations VALUES(?,?,?,?)',(cid,1,'test',now()))
    await store.execute('INSERT INTO runs(id,user_id,conversation_id,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?)',('fake',1,cid,'running','{}',now(),now()))
    tools={t.name:t for t in await runtime.tools(run,'Bearer user',runtime.config.model_dump())}
    result=await tools['propose_build'].ainvoke({'title':'bad','selections':[{'hardware_id':1,'sku_id':'invented'}]})
    assert 'error' in result
    result=await tools['propose_build'].ainvoke({'title':'bad','selections':[{'hardware_id':999}]})
    assert 'error' in result
    assert (await store.run('fake'))['draft'] is None


def test_compatibility_exact_form_factor_and_budget():
    parts=[dict(p) for p in PARTS];parts[5]['supportedFormFactors']='MATX'
    assert '主板版型不在机箱支持范围内' in check(parts)['errors']
    parts[0]['socketType']='LGA1700';parts[0]['selectedPrice']=6500
    result=check(parts,6000)
    assert result['status']=='BLOCK' and any('插槽' in x for x in result['errors']) and any('预算' in x for x in result['errors'])


def test_crawler_no_fabricated_prices_or_mismatched_models():
    html='<li class="gl-item" data-sku="123"><div class="p-name"><em>CPU 7600 处理器</em></div><div class="p-price"><i></i></div></li><li class="gl-item" data-sku="456"><div class="p-name"><em>CPU 7500</em></div><div class="p-price"><i>100</i></div></li>'
    result=Crawler.parse_products(html,'CPU 7600')
    assert len(result)==1 and result[0]['price'] is None and result[0]['skuId']=='123'


async def test_crawler_blocks_private_document_url():
    with pytest.raises(ValueError): await public_url('https://127.0.0.1/private')
    with pytest.raises(ValueError): await public_url('http://example.com')


async def test_missing_model_is_explicit_failure_and_retry_preserves_history(env):
    store,runtime,k,crawler,web,mp=env
    runtime.config.api_key=''
    run=(await web.post('/agent/runs',json={'message':'帮我配电脑'})).json()
    failed=await done(store,run['id']);assert failed['status']=='failed' and '配置模型' in failed['error']
    runtime.config.api_key='test'
    mp.setattr('app.runtime.ChatOpenAI',lambda **kwargs:ScriptedModel(responses=[AIMessage(content='已恢复。')]))
    assert (await web.post(f"/agent/runs/{run['id']}/retry")).status_code==200
    assert (await done(store,run['id']))['status']=='completed'
    messages=await store.all('SELECT * FROM messages WHERE conversation_id=?',(run['conversation_id'],))
    assert len(messages)==2


async def test_cancel_waiting_task_and_delete_conversation(env):
    store,runtime,k,crawler,web,mp=env
    mp.setattr('app.runtime.ChatOpenAI',lambda **kwargs:ScriptedModel(responses=[call('ask_user',{'question':'预算？'})]))
    run=(await web.post('/agent/runs',json={'message':'配电脑'})).json()
    assert (await done(store,run['id']))['status']=='needs_input'
    assert (await web.post(f"/agent/runs/{run['id']}/cancel")).json()['status']=='cancelled'
    assert (await web.delete('/agent/conversations/'+run['conversation_id'])).status_code==200
    assert await store.run(run['id']) is None
    assert not await store.all('SELECT * FROM events WHERE run_id=?',(run['id'],))


def test_model_suffixes_and_accessory_titles_are_not_quotes():
    assert not Crawler.matches('RTX4060Ti 显卡','RTX4060','GPU')
    assert not Crawler.matches('7600 CPU 主板套装','7600','CPU')
    assert Crawler.matches('AMD 锐龙 CPU 7600 处理器','7600','CPU')


async def test_persistent_state_survives_restart(tmp_path):
    path=tmp_path/'restart.sqlite';store=Store(path);await store.connect()
    await store.execute('INSERT INTO conversations VALUES(?,?,?,?)',('c',1,'test',now()))
    await store.execute('INSERT INTO runs(id,user_id,conversation_id,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?)',('r',1,'c','running','{}',now(),now()))
    await store.event('r','plan',{'steps':['test']});await store.close()
    restored=Store(path);await restored.connect()
    assert (await restored.run('r'))['status']=='interrupted'
    assert len(await restored.all('SELECT * FROM events WHERE run_id=?',('r',)))==1
    await restored.close()


async def test_semantic_retrieval_and_embedding_model_change(env, monkeypatch):
    store,runtime,k,crawler,web,mp=env
    class Embedder:
        model='test-vectors-v1'
        async def aembed_documents(self, texts): return [[1.0,0.0] for _ in texts]
        async def aembed_query(self, text): return [1.0,0.0]
    embedder=Embedder()
    async def embeddings(): return embedder
    monkeypatch.setattr(k,'embeddings',embeddings)
    result=await k.ingest('处理器资料','处理器影响编译速度与游戏性能。','test source')
    assert result['mode']=='hybrid'
    assert (await k.search('processor'))[0]['documentId']==result['id']
    embedder.model='test-vectors-v2'
    assert not await k.search('processor')


def test_hot_search_parser_filters_wrong_suffixes():
    items={'moduleInfo':[{'id':'goods-pc-search','data':[{'id':'1','title':'RTX4060 显卡','jdPrice':'2199','imageUrl':'//img.example/x.jpg'},{'id':'2','title':'RTX4060Ti 显卡','jdPrice':'2999'}]}]}
    products=Crawler.parse_hot_products('<script>var configs = '+json.dumps(items)+';</script>','RTX4060','GPU')
    assert len(products)==1 and products[0]['price']==2199


async def test_catalog_discovery_returns_real_alternatives_for_unknown_model(env):
    store,runtime,k,crawler,web,mp=env
    cid=uid();await store.execute('INSERT INTO conversations VALUES(?,?,?,?)',(cid,1,'test',now()))
    await store.execute('INSERT INTO runs(id,user_id,conversation_id,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?)',('catalog-test',1,cid,'running','{}',now(),now()))
    tools={t.name:t for t in await runtime.tools({'id':'catalog-test','references':[]},'Bearer user',runtime.config.model_dump())}
    result=await tools['search_hardware'].ainvoke({'part_type':'CPU','query':'unknown-generation'})
    assert result['total']==0 and result['available_candidates'][0]['id']==1
    result=await tools['search_hardware'].ainvoke({'part_type':'CPU','query':'AM5'})
    assert result['items'][0]['id']==1


def test_crawler_ignores_family_digits_and_preserves_word_boundaries():
    assert Crawler.matches('AMD Ryzen 5 7600 处理器','Ryzen 5 7600','CPU')
    assert Crawler.matches('Intel Core Ultra 7 270K Plus 处理器','Core Ultra 7 270K Plus','CPU')
    assert Crawler.matches('RTX 4060 显卡','RTX4060','GPU')
    assert not Crawler.matches('RTX 4060 Ti 显卡','RTX4060','GPU')


async def test_admin_product_search_authorization_and_validation(env):
    store,runtime,k,crawler,web,mp=env
    async def search(keyword, part_type):
        return {'products':[{'skuId':'123','price':1289}], 'warning':'', 'cached':False}
    mp.setattr(crawler,'search',search)
    assert (await web.get('/agent/admin/products/search?keyword=9600X&part_type=CPU')).status_code==403
    response=await web.get('/agent/admin/products/search?keyword=9600X&part_type=CPU',headers={'Authorization':'Bearer admin'})
    assert response.status_code==200 and response.json()['products'][0]['price']==1289
    assert (await web.get('/agent/admin/products/search?keyword=x',headers={'Authorization':'Bearer admin'})).status_code==422


async def test_activity_replay_is_owned_and_has_original_timestamps(env):
    store,runtime,k,crawler,web,mp=env
    cid,rid=uid(),uid()
    await store.execute('INSERT INTO conversations VALUES(?,?,?,?)',(cid,1,'activity test',now()))
    await store.execute('INSERT INTO runs(id,user_id,conversation_id,status,payload,created_at,updated_at) VALUES(?,?,?,?,?,?,?)',(rid,1,cid,'completed','{}',now(),now()))
    await store.event(rid,'tool_started',{'name':'search_hardware'})
    await store.event(rid,'text_delta',{'text':'stream token'})
    await store.event(rid,'tool_completed',{'name':'search_hardware'})
    response=await web.get(f'/agent/runs/{rid}/activity')
    assert response.status_code==200
    assert [e['type'] for e in response.json()]==['tool_started','tool_completed']
    assert all(e['created_at'] and e['run_id']==rid for e in response.json())
    assert (await web.get(f'/agent/runs/{rid}/activity',headers={'Authorization':'Bearer other'})).status_code==404
    streamed=await web.get(f'/agent/runs/{rid}/events')
    frames=[json.loads(line[6:]) for line in streamed.text.splitlines() if line.startswith('data: ')]
    assert all(frame['_event_time'] for frame in frames)
    assert frames[0]['_event_time']==response.json()[0]['created_at']


async def test_rag_configuration_controls_chunking_and_result_limit(env):
    store, runtime, knowledge, crawler, web, mp = env
    settings = (await web.get('/agent/admin/settings', headers={'Authorization':'Bearer admin'})).json()
    settings.update(base_url='https://models.example/v1', model='test-model', rag_chunk_size=200, rag_chunk_overlap=30, rag_top_k=1)
    saved = await web.put('/agent/admin/settings', json=settings, headers={'Authorization':'Bearer admin'})
    assert saved.status_code == 200
    assert saved.json()['rag_top_k'] == 1
    settings['rag_chunk_overlap'] = 200
    assert (await web.put('/agent/admin/settings', json=settings, headers={'Authorization':'Bearer admin'})).status_code == 422
    document = await knowledge.ingest('AM5 主板知识', ('AM5 主板 DDR5 内存需要匹配。\n' * 50))
    assert document['chunks'] > 1
    found = await knowledge.search('AM5 主板')
    assert len(found) == 1
    chunks = await store.all('SELECT text FROM chunks WHERE document_id=?', (document['id'],))
    assert all(len(chunk['text']) <= 200 for chunk in chunks)

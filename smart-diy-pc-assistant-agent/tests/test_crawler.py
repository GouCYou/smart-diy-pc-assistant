"""Regression coverage for JD's upgraded product modules and misleading bundles."""
import json
import httpx
from app.crawler import Crawler


def page(items, module='goods-pc-filter-upgrade'):
    return '<script>var configs = '+json.dumps({'moduleInfo':[{'id':module,'data':items}]})+';</script>'


def test_upgraded_module_reads_real_price_and_relative_image():
    html = page([{'id':'123','title':'AMD 锐龙 R5 9600X CPU 处理器','cid3':'678','price':'1289.00','imageUrl':'jfs/t1/photo.jpg'}])
    products = Crawler.parse_hot_products(html,'Ryzen 5 9600X','CPU')
    assert len(products)==1
    assert products[0]['price']==1289
    assert products[0]['availability']=='unknown' and products[0]['priceType']=='page'
    assert products[0]['image']=='https://img10.360buyimg.com/n1/jfs/t1/photo.jpg'
    assert products[0]['link']=='https://item.jd.com/123.html'


def test_jd_category_excludes_bundles_even_without_type_in_title():
    products = Crawler.parse_hot_products(page([
        {'id':'1','title':'七彩虹 B650M D5 WIFI 9600X【盒装】','cid3':'17466','price':'2309'},
        {'id':'2','title':'AMD 9600X CPU','cid3':'678','price':'1289'},
        {'id':'3','title':'AMD 9600X 全家桶组装机','price':'7999'},
    ]),'9600X','CPU')
    assert [p['skuId'] for p in products]==['2']
    products = Crawler.parse_hot_products(page([
        {'id':'4','title':'5600+华硕RTX 5060 16G内存+1T硬盘','cid3':'11762','price':'5898'},
        {'id':'5','title':'RTX 5060 显卡','cid3':'679','price':'2499'},
        {'id':'6','title':'RTX 5060 Ti 显卡','cid3':'679','price':'3499'},
    ]),'RTX 5060','GPU')
    assert [p['skuId'] for p in products]==['5']


def test_missing_and_nonfinite_prices_remain_unquoted():
    products = Crawler.parse_hot_products(page([
        {'id':str(i),'title':'RTX 5060 显卡','price':p}
        for i,p in enumerate(['','NaN','Infinity','0','-1'],start=1)
    ]),'RTX 5060','GPU')
    assert len(products)==5 and all(p['price'] is None for p in products)


def test_parser_tolerates_metadata_modules_and_deduplicates():
    item = {'id':'123','title':'RTX 5060 显卡','price':'2499'}
    assert len(Crawler.parse_hot_products(page([item,item,None]),'RTX 5060','GPU'))==1
    assert not Crawler.parse_hot_products(page({'title':'metadata'}),'RTX 5060','GPU')
    assert not Crawler.parse_hot_products('<script>var configs = [];</script>','RTX 5060','GPU')
    assert not Crawler.parse_hot_products('<script>var configs = {"moduleInfo":[null]};</script>','RTX 5060','GPU')


async def test_search_uses_upgrade_results_and_caches_success():
    calls=[]
    async def handler(request):
        calls.append(request)
        return httpx.Response(200,text=page([{'id':'123','title':'RTX 5060 显卡','cid3':'679','price':'2499'}]))
    crawler=Crawler()
    await crawler.client.aclose()
    crawler.client=httpx.AsyncClient(transport=httpx.MockTransport(handler))
    try:
        first=await crawler.search('RTX 5060','GPU')
        second=await crawler.search('RTX 5060','GPU')
        assert first['products'][0]['price']==2499 and not first['warning']
        assert first['diagnostics'][0]['status']=='ok'
        assert second['cached'] and len(calls)==1
    finally:
        await crawler.close()


async def test_risk_redirect_is_reported_without_following_or_inventing_price():
    async def handler(request):
        return httpx.Response(302,headers={'location':'https://cfe.m.jd.com/risk_handler/'})
    crawler=Crawler()
    await crawler.client.aclose()
    crawler.client=httpx.AsyncClient(transport=httpx.MockTransport(handler))
    try:
        result=await crawler.search('RTX 5060','GPU')
        assert not result['products'] and result['warning']
        assert all(d['status']=='blocked' for d in result['diagnostics'])
    finally:
        await crawler.close()

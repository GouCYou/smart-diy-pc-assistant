import test from 'node:test';
import assert from 'node:assert/strict';
import { runningConversation } from '../src/lib/chatNavigation.ts';
test('navigation returns the last viewed running chat, otherwise the latest active chat',()=>{
 const rows=[{id:'pinned-old',status:'running',updated_at:'2026-10-20T01:00:00Z'},{id:'latest',status:'queued',updated_at:'2026-10-21T01:00:00Z'},{id:'done',status:'completed',updated_at:'2026-10-21T02:00:00Z'}];
 assert.equal(runningConversation(rows,'pinned-old')?.id,'pinned-old');
 assert.equal(runningConversation(rows,'done')?.id,'latest');
 assert.equal(runningConversation(rows.filter(r=>r.status==='completed'),'done'),undefined);
});

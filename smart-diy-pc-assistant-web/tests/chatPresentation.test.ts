import test from 'node:test';
import assert from 'node:assert/strict';
import {quotedDraft,elapsedLabel} from '../src/lib/chatPresentation.ts';
import type {Draft} from '../../shared/types.ts';
test('unfinished quotes never become message configuration links',()=>{
 const d={parts:[{id:1,selectedPrice:100}],partSelections:[],validation:{unpricedParts:[],quotedTotal:100}} as unknown as Draft;
 assert.equal(quotedDraft(d),true);
 assert.equal(quotedDraft({...d,validation:{...d.validation,unpricedParts:['GPU']}}),false);
 assert.equal(quotedDraft({...d,parts:[{...d.parts[0]!,selectedPrice:0}]}),false);
 assert.equal(quotedDraft({...d,partSelections:[{hardwarePartId:1,price:0}] as Draft['partSelections']}),false);
 assert.equal(quotedDraft(undefined),false);
});
test('elapsed time carries seconds into minutes and minutes into hours',()=>{
 assert.equal(elapsedLabel(59),'59s');assert.equal(elapsedLabel(60),'1m 0s');
 assert.equal(elapsedLabel(3599),'59m 59s');assert.equal(elapsedLabel(3600),'1h 0m');
 assert.equal(elapsedLabel(7260),'2h 1m');
});

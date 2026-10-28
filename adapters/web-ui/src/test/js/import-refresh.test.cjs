const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const script = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/dashboard-actions.js'), 'utf8');
const modulePromise = import('data:text/javascript;base64,'+Buffer.from(script).toString('base64'));
const response = status => ({ok:true,json:async()=>({status})});
test('waits for every file, including running jobs', async()=>{
 const {waitForImportRefresh} = await modulePromise;let calls=0;
 await waitForImportRefresh(['/a','/b'],{wait:async()=>{},fetchStatus:async()=>response(++calls<3?'RUNNING':'COMPLETED')});
 assert.equal(calls,4);
});
test('failed refresh reports that saved import is retained',async()=>{
 const {waitForImportRefresh}=await modulePromise;
 await assert.rejects(waitForImportRefresh(['/a'],{wait:async()=>{},fetchStatus:async()=>response('FAILED')}),/Import saved, but background refresh failed/);
});
test('HTTP error is not treated as completed refresh',async()=>{
 const {waitForImportRefresh}=await modulePromise;
 await assert.rejects(waitForImportRefresh(['/a'],{wait:async()=>{},fetchStatus:async()=>({ok:false})}),/status unavailable/);
});
test('UI wait expiry does not claim the background job failed',async()=>{
 const {waitForImportRefresh}=await modulePromise;let time=0;
 await assert.rejects(waitForImportRefresh(['/a'],{now:()=>time++,timeoutMs:2,wait:async()=>{},fetchStatus:async()=>response('RUNNING')}),/still running/);
});

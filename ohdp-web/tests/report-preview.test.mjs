import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import ts from 'typescript'
import {parse,compileScript} from '@vue/compiler-sfc'
import {ref,shallowRef,reactive,computed,watch,nextTick,effectScope,createRenderer} from 'vue'

const dir=new URL('../src/components/radiology/',import.meta.url)
const transpile=s=>ts.transpileModule(s,{compilerOptions:{module:ts.ModuleKind.ESNext,target:ts.ScriptTarget.ES2022}}).outputText
const support=await import('data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('reportPreviewSupport.ts',dir),'utf8'))).toString('base64'))
const source=fs.readFileSync(new URL('ReportPreviewPager.vue',dir),'utf8')
const settle=async()=>{await new Promise(setImmediate);await nextTick()}
function pager({pages=[],docx,render=async()=>{}}={}){
 const props=reactive({pages,docx,loading:false}),events=[],unmount=[]
 const bindings={ref,shallowRef,computed,watch,nextTick,onMounted(){},onBeforeUnmount:fn=>unmount.push(fn),defineProps:()=>props,withDefaults:x=>x,defineEmits:()=>((...x)=>events.push(x)),renderAsync:render,...support}
 let setup=parse(source).descriptor.scriptSetup.content
 const ast=ts.createSourceFile('preview.ts',setup,ts.ScriptTarget.Latest,true)
 for(const node of [...ast.statements].reverse())if(ts.isImportDeclaration(node))setup=setup.slice(0,node.pos)+setup.slice(node.end)
 const scope=effectScope();const c=scope.run(()=>new Function(...Object.keys(bindings),transpile(setup)+'\nreturn {currentPage,pageCount,pageLabel,changePage,docxHost,viewport,docxPages,rendering,renderError,renderDocument,zoomPercent,changeZoom,restoreZoom,canvasStyle,contentStyle,naturalSize,imageLoaded,measureViewport} ')(...Object.values(bindings)))
 const viewport={scrollTop:350,scrollLeft:100};c.viewport.value=viewport
 return {...c,props,events,scope,unmount,dispose(){unmount.forEach(f=>f());scope.stop()}}
}
test('six-page reports traverse cover, notice, every body page and back without crossing bounds',async()=>{
 const c=pager({pages:Array.from({length:6},(_,i)=>'page-'+i)})
 await nextTick();assert.equal(c.pageCount.value,6);c.changePage(-1);assert.equal(c.currentPage.value,0)
 const visited=[]
 for(let i=0;i<6;i++){
  await nextTick();visited.push(c.currentPage.value);assert.equal(c.pageLabel.value,i===0?'封面':i===1?'封二':`正文第${i-1}页`)
  assert.equal(c.events.at(-1)[0],'page-change');assert.deepEqual(c.events.at(-1)[1],{index:i,count:6,label:c.pageLabel.value})
  c.changePage(1)
 }
 assert.deepEqual(visited,[0,1,2,3,4,5]);assert.equal(c.currentPage.value,5)
 for(let i=5;i>=0;i--){assert.equal(c.currentPage.value,i);c.changePage(-1);await nextTick()}
 assert.equal(c.currentPage.value,0);assert.equal(c.viewport.value.scrollTop,0);assert.equal(c.viewport.value.scrollLeft,0);c.dispose()
})
test('empty, one-page and shortened previews always clamp the current page',async()=>{
 const c=pager();await nextTick();c.changePage(1);assert.equal(c.currentPage.value,0);assert.equal(c.pageCount.value,0)
 c.props.pages=['only'];await nextTick();c.changePage(1);assert.equal(c.currentPage.value,0)
 c.props.pages=['cover','notice','body'];await nextTick();c.changePage(2);await nextTick();assert.equal(c.currentPage.value,2)
 c.props.pages=['new'];await nextTick();assert.equal(c.currentPage.value,0);assert.equal(c.pageCount.value,1);c.dispose()
})
test('Word fallback registers every rendered page and makes exactly one visible while navigating',async()=>{
 const sections=Array.from({length:5},()=>({hidden:false}));const docx=new Blob(['docx'])
 let calls=0
 const c=pager({docx,render:async(blob,host,styles,options)=>{calls++;assert.equal(blob,docx);assert.equal(options.breakPages,true);assert.equal(options.ignoreLastRenderedPageBreak,false);assert.equal(host.innerHTML,'')}})
 c.docxHost.value={innerHTML:'old',querySelectorAll:selector=>{assert.equal(selector,'.docx-wrapper > section.docx');return sections}}
 await settle();assert.equal(calls,1);assert.equal(c.pageCount.value,5)
 for(let i=0;i<5;i++){await nextTick();assert.deepEqual(sections.map(s=>s.hidden),sections.map((_,n)=>i!==n));c.changePage(1)}
 c.changePage(-4);await nextTick();assert.deepEqual(sections.map(s=>s.hidden),[false,true,true,true,true]);c.dispose()
})
test('replacing a Word document discards old pages and restarts on the new cover',async()=>{
 let sections=Array.from({length:4},()=>({hidden:false}))
 const c=pager({docx:new Blob(['one'])});c.docxHost.value={innerHTML:'',querySelectorAll:()=>sections}
 await settle();c.changePage(3);await nextTick();assert.equal(c.currentPage.value,3)
 sections=Array.from({length:3},()=>({hidden:false}));c.props.docx=new Blob(['two']);await settle()
 assert.equal(c.currentPage.value,0);assert.equal(c.pageCount.value,3);assert.deepEqual(sections.map(s=>s.hidden),[false,true,true]);c.dispose()
})
test('a failed Word renderer exposes an error without reporting a usable page',async()=>{
 const c=pager({docx:new Blob(['broken']),render:async()=>{throw new Error('invalid zip')}});c.docxHost.value={innerHTML:'',querySelectorAll:()=>[]}
 await settle();assert.match(c.renderError.value,/预览失败/);assert.equal(c.pageCount.value,0);assert.equal(c.rendering.value,false);c.dispose()
})
test('closing a preview during Word rendering suppresses stale pages and events',async()=>{
 let release;const pending=new Promise(resolve=>release=resolve)
 const c=pager({docx:new Blob(['delayed']),render:()=>pending});c.docxHost.value={innerHTML:'',querySelectorAll:()=>[{hidden:false}]}
 await nextTick();const previous=c.events.length;c.dispose();release();await settle()
 assert.equal(c.pageCount.value,0);assert.equal(c.events.length,previous)
})
test('pager template connects real next/previous controls, keyboard navigation and one image at a time',()=>{
 const {descriptor}=parse(source);assert.doesNotThrow(()=>compileScript(descriptor,{id:'report-pager-test',inlineTemplate:true}))
 assert.match(descriptor.template.content,/@click="changePage\(-1\)"/);assert.match(descriptor.template.content,/@click="changePage\(1\)"/)
 assert.match(descriptor.template.content,/@keydown.left.prevent="changePage\(-1\)"/);assert.match(descriptor.template.content,/@keydown.right.prevent="changePage\(1\)"/)
 assert.match(descriptor.template.content,/:src="pages\[currentPage\]"/);assert.doesNotMatch(descriptor.template.content,/v-for/)
 assert.match(descriptor.template.content,/:disabled="!pageCount \|\| currentPage === 0"/);assert.match(descriptor.template.content,/:disabled="!pageCount \|\| currentPage >= pageCount - 1"/)
})
test('mounted compiled pager buttons change the displayed image, alt text and title event through all six pages',async()=>{
 const descriptor=parse(source).descriptor
 let code=compileScript(descriptor,{id:'mounted-report-pager',inlineTemplate:true,templateOptions:{compilerOptions:{hoistStatic:false}}}).content
 const supportUrl='data:text/javascript;base64,'+Buffer.from(transpile(fs.readFileSync(new URL('reportPreviewSupport.ts',dir),'utf8'))).toString('base64')
 const docxUrl='data:text/javascript;base64,'+Buffer.from('export async function renderAsync(){throw new Error("unexpected fallback")}').toString('base64')
 code=code.replace(/from ['"]\.\/reportPreviewSupport['"]/g,`from '${supportUrl}'`).replace(/from ['"]docx-preview['"]/g,`from '${docxUrl}'`)
 const file=new URL(`.mounted-pager-${process.pid}.mjs`,import.meta.url);fs.writeFileSync(file,transpile(code))
 let Component
 try{Component=(await import(file.href)).default}finally{fs.unlinkSync(file)}
 const node=(type,text='')=>({type,text,children:[],props:{},parent:null})
 function insert(child,parent,anchor){if(child.parent){const i=child.parent.children.indexOf(child);if(i>=0)child.parent.children.splice(i,1)}const i=anchor?parent.children.indexOf(anchor):-1;parent.children.splice(i<0?parent.children.length:i,0,child);child.parent=parent}
 const renderer=createRenderer({insert,remove(n){if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1)},createElement:type=>node(type),createText:text=>node('#text',text),createComment:text=>node('#comment',text),setText(n,text){n.text=text},setElementText(n,text){n.text=text;n.children=[]},parentNode:n=>n.parent,nextSibling:n=>n.parent?.children[n.parent.children.indexOf(n)+1]||null,patchProp(n,k,old,v){n.props[k]=v}})
 const events=[];const root=node('root');const app=renderer.createApp(Component,{pages:['p0','p1','p2','p3','p4','p5'],'onPage-change':x=>events.push(x)});app.mount(root)
 function find(type){const found=[];function walk(n){if(n.type===type)found.push(n);n.children.forEach(walk)}walk(root);return found}
 await settle()
 for(let i=0;i<6;i++){
  const previous=find('button').find(b=>b.props['aria-label']==='上一页'),next=find('button').find(b=>b.props['aria-label']==='下一页');assert.equal(find('img').length,1);assert.equal(find('img')[0].props.src,'p'+i);assert.match(find('img')[0].props.alt,new RegExp('第'+(i+1)+'页'))
  assert.equal(Boolean(previous.props.disabled),i===0);assert.equal(Boolean(next.props.disabled),i===5);assert.equal(events.at(-1).index,i)
  if(i<5){next.props.onClick();await settle()}
 }
 for(let i=4;i>=0;i--){find('button').find(b=>b.props['aria-label']==='上一页').props.onClick();await settle();assert.equal(find('img')[0].props.src,'p'+i)}
 assert.equal(events.at(-1).label,'封面')
 const view=find('div').find(n=>n.props.class==='report-page-viewport');view.clientWidth=800;view.clientHeight=600
 find('img')[0].props.onLoad({target:{naturalWidth:1000,naturalHeight:1400}});await settle()
 const canvas=()=>find('div').find(n=>n.props.class==='report-zoom-canvas')
 const initialWidth=parseFloat(canvas().props.style.width),initialHeight=parseFloat(canvas().props.style.height)
 find('button').find(b=>b.props['aria-label']==='放大').props.onClick();await settle()
 assert.equal(find('output')[0].text,'125%');assert.ok(parseFloat(canvas().props.style.width)>initialWidth);assert.ok(parseFloat(canvas().props.style.height)>initialHeight)
 find('button').find(b=>b.props['aria-label']==='缩小').props.onClick();await settle();assert.equal(find('output')[0].text,'100%')
 find('button').find(b=>b.props['aria-label']==='放大').props.onClick();await settle();view.scrollTop=300;view.scrollLeft=100
 find('button').find(b=>b.props['aria-label']==='还原').props.onClick();await settle();assert.equal(find('output')[0].text,'100%');assert.equal(view.scrollTop,0);assert.equal(view.scrollLeft,0);assert.equal(parseFloat(canvas().props.style.width),initialWidth)
 for(let i=0;i<8;i++){find('button').find(b=>b.props['aria-label']==='放大').props.onClick();await settle()}
 assert.equal(find('output')[0].text,'300%');assert.equal(find('button').find(b=>b.props['aria-label']==='放大').props.disabled,true)
 for(let i=0;i<10;i++){find('button').find(b=>b.props['aria-label']==='缩小').props.onClick();await settle()}
 assert.equal(find('output')[0].text,'50%');assert.equal(find('button').find(b=>b.props['aria-label']==='缩小').props.disabled,true)
 find('button').find(b=>b.props['aria-label']==='还原').props.onClick();await settle();assert.equal(find('output')[0].text,'100%')
 app.unmount()
})

test('zoom preserves the selected page and input data, persists across pages, clamps at both limits and resets for a new report',async()=>{
 const pages=['cover','notice','body'];const c=pager({pages});await settle();c.changePage(1);await settle()
 for(let i=0;i<20;i++)c.changeZoom(25)
 assert.equal(c.zoomPercent.value,300);assert.equal(c.currentPage.value,1);assert.deepEqual([...c.props.pages],pages)
 c.changePage(1);await settle();assert.equal(c.zoomPercent.value,300)
 for(let i=0;i<20;i++)c.changeZoom(-25)
 assert.equal(c.zoomPercent.value,50);assert.equal(c.currentPage.value,2)
 c.viewport.value.scrollTop=250;c.viewport.value.scrollLeft=120;c.restoreZoom();assert.equal(c.zoomPercent.value,100);assert.equal(c.viewport.value.scrollTop,0);assert.equal(c.viewport.value.scrollLeft,0)
 c.changeZoom(25);c.props.pages=['new cover','new notice'];await settle();assert.equal(c.zoomPercent.value,100);assert.equal(c.currentPage.value,0);c.dispose()
})

test('image and Word zoom use scrollable scaled canvas sizes without changing page content',async()=>{
 const c=pager({pages:['image']});c.viewport.value={clientWidth:800,clientHeight:600,offsetWidth:817,offsetHeight:617,scrollTop:0,scrollLeft:0}
 c.imageLoaded({target:{naturalWidth:1000,naturalHeight:1400}});await settle();assert.equal(c.canvasStyle.value.height,'600px')
 const base=+c.contentStyle.value.transform.match(/scale\((.+)\)/)[1];c.changeZoom(200);await settle();assert.equal(c.canvasStyle.value.height,'1800px');assert.equal(+c.contentStyle.value.transform.match(/scale\((.+)\)/)[1],base*3)
 c.viewport.value.clientHeight=583;c.measureViewport();await settle();assert.equal(c.canvasStyle.value.height,'1800px','scrollbar visibility must not change the fit baseline');c.dispose()
 const section={hidden:false,offsetWidth:800,offsetHeight:1100};const word=pager({docx:new Blob(['word'])});word.docxHost.value={innerHTML:'',querySelectorAll:()=>[section]};word.viewport.value={clientWidth:800,clientHeight:600,scrollTop:0,scrollLeft:0};await settle()
 const normal=parseFloat(word.canvasStyle.value.height);word.changeZoom(25);await settle();assert.ok(parseFloat(word.canvasStyle.value.height)>normal);assert.equal(word.docxPages.value[0],section);word.dispose()
})
test('both workbenches share the pager, dynamic title and top exit; archive alone uses nowrap row actions',()=>{
 const radiology=fs.readFileSync(new URL('RadiologyWorkbench.vue',dir),'utf8'),dose=fs.readFileSync(new URL('PersonalDoseWorkbench.vue',dir),'utf8')
 for(const s of [radiology,dose]){assert.match(s,/<ReportPreviewPager/);assert.match(s,/@page-change="previewPageTitle=\$event.label"/);assert.match(s,/<h3 :id="titleId">报告预览-{{ previewPageTitle }}/);assert.match(s,/<el-button type="primary" @click="previewVisible=false">退出/);assert.doesNotMatch(s,/previewSection/)}
 assert.match(radiology,/prop="reportNo" label="报告编号" width="200"/);assert.match(radiology,/fixed="right"[^\n]+class-name="table-action-cell report-archive-actions"[^\n]+width="160"/)
 assert.match(radiology,/class-name="table-action-cell table-action-stack"[^\n]+下载[^\n]+更改[^\n]+删除/)
 const css=fs.readFileSync(new URL('../src/styles/ppt-workspace.css',import.meta.url),'utf8');assert.match(css,/\.report-archive-actions>\.cell\{[^}]+flex-direction:row;[^}]+flex-wrap:nowrap;[^}]+white-space:nowrap/)
})

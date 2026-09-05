let simulationPageController = null;
export function initSimulationPage() {
destroySimulationPage();
simulationPageController = new AbortController();
const {signal} = simulationPageController;
(()=>{const root=document.querySelector('.iv-plan-timeline'),selector=root?.querySelector('[data-plan-year-selector]'),snapshots=[...(root?.querySelectorAll('[data-plan-snapshot]')||[])],markers=[...(root?.querySelectorAll('[data-plan-marker]')||[])],prev=root?.querySelector('[data-plan-prev]'),next=root?.querySelector('[data-plan-next]');if(!root||!selector||!snapshots.length)return;const select=year=>{const value=String(year),index=snapshots.findIndex(item=>item.dataset.year===value);if(index<0)return;selector.value=value;snapshots.forEach((item,itemIndex)=>{const active=itemIndex===index;item.hidden=!active;item.setAttribute('aria-hidden',String(!active))});markers.forEach(marker=>{const active=marker.dataset.year===value;marker.classList.toggle('is-active',active);marker.setAttribute('aria-selected',String(active));marker.setAttribute('tabindex',active?'0':'-1')});prev.disabled=index===0;next.disabled=index===snapshots.length-1};const move=step=>{const index=snapshots.findIndex(item=>item.dataset.year===selector.value);select(snapshots[Math.max(0,Math.min(snapshots.length-1,index+step))].dataset.year)};selector.addEventListener('change',()=>select(selector.value),{signal});selector.addEventListener('keydown',event=>{if(event.key==='ArrowLeft'){event.preventDefault();move(-1)}if(event.key==='ArrowRight'){event.preventDefault();move(1)}},{signal});prev?.addEventListener('click',()=>move(-1),{signal});next?.addEventListener('click',()=>move(1),{signal});markers.forEach(marker=>marker.addEventListener('click',()=>select(marker.dataset.year),{signal}));select(selector.value)})();

(()=>{document.querySelectorAll('.iv-simulation-projection-row--current td:first-child span').forEach(span=>{const year=span.textContent.trim(),link=document.createElement('a');link.className='iv-historical-year-link';link.title='View live year review';link.href=window.location.pathname.replace(/\/simulation(?:\/.*)?$/,'/simulation/timeline/'+encodeURIComponent(year))+window.location.search;link.textContent=year;span.replaceWith(link)})})();
}
export function destroySimulationPage() {
simulationPageController?.abort();
simulationPageController = null;
}


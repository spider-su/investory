(() => {
  'use strict';
  // Photo coordinates belong to the artwork; all rendered geometry uses scene pixels.
  const ART = {
    normal: {width:1672,height:941,glassSeat:{x:.445,y:.625},
      liquidSource:{x:.105,y:.575},mountainSource:{x:.9,y:.27}},
    wide: {width:1916,height:821,glassSeat:{x:.46,y:.665},
      liquidSource:{x:.20,y:.60},mountainSource:{x:.80,y:.315}}
  };
  const GEOMETRY = {desktopWidth:980,wideAspect:2.05,labelWidth:160,labelGap:18,
    hoverMin:18,hoverMax:36,hoverRatio:.025,
    // Native image coordinates: rim, visible base, waist and lower sand crest.
    assetHeight:1693,entryY:36,foot:1626,neckY:895,sandSurfaceY:1310,
    destinationInset:9};
  const scene = document.querySelector('.fl-scene.fl-clean-plate');
  const glass = scene?.querySelector('.fl-hourglass');
  const vapor = scene?.querySelector('.fl-income-vapor');
  if (scene && glass && vapor && typeof ResizeObserver !== 'undefined') {
    const header = document.querySelector('.fl-header');
    const guides = {liquid:scene.querySelector('.fl-asset-guide--dune'),
      mountain:scene.querySelector('.fl-asset-guide--mountain')};
    const nodes = [...scene.querySelectorAll('.fl-spend-node')];
    const paths = selector => [...vapor.querySelectorAll(selector)];
    const flowPaths = {
      cash:paths('.fl-vapor--cash path'),
      market:paths('.fl-vapor--market path')
    };
    const curvePath = ([a,c1,c2,b]) => `M ${a.x} ${a.y} C ${c1.x} ${c1.y} ${c2.x} ${c2.y} ${b.x} ${b.y}`;
    const publish = layout => {
      scene.financialLandscapeLayout = layout;
      scene.dispatchEvent(new CustomEvent('fl:layout', {detail:layout}));
    };
    const updateLayout = () => {
      if (window.innerWidth <= GEOMETRY.desktopWidth) {
        Object.values(guides).forEach(el => ['left','right','top','bottom','height'].forEach(k => el.style.removeProperty(k)));
        publish({desktop:false});
        return;
      }
      const sr = scene.getBoundingClientRect();
      const {width,height} = sr;
      if (!width || !height) return;
      const artKey = width / height >= GEOMETRY.wideAspect ? 'wide' : 'normal';
      const art = ART[artKey];
      const scale = Math.max(width / art.width, height / art.height);
      const offsetX = (width - art.width * scale) / 2;
      const offsetY = (height - art.height * scale) / 2;
      const photoPoint = p => ({x:offsetX+p.x*art.width*scale,y:offsetY+p.y*art.height*scale});
      const seat = photoPoint(art.glassSeat);
      const source = photoPoint(art.liquidSource);
      const summit = photoPoint(art.mountainSource);
      scene.style.setProperty('--fl-glass-seat-x',seat.x+'px');
      const hoverGap = Math.max(GEOMETRY.hoverMin,Math.min(GEOMETRY.hoverMax,height*GEOMETRY.hoverRatio));
      scene.style.setProperty('--fl-glass-seat-y',(seat.y-hoverGap)+'px');
      // Follow the actual header, including shorter desktop windows and font changes.
      scene.style.setProperty('--fl-panel-top',(header.getBoundingClientRect().bottom-sr.top+4)+'px');
      const gr = glass.getBoundingClientRect();
      const centerX = gr.left-sr.left+gr.width/2;
      const entry = {x:centerX,y:gr.top-sr.top+gr.height*GEOMETRY.entryY/GEOMETRY.assetHeight};
      const foot = {x:centerX,y:gr.top-sr.top+gr.height*GEOMETRY.foot/GEOMETRY.assetHeight};
      const neck = {x:centerX,y:gr.top-sr.top+gr.height*GEOMETRY.neckY/GEOMETRY.assetHeight};
      const sandSurface = {x:centerX,y:gr.top-sr.top+gr.height*GEOMETRY.sandSurfaceY/GEOMETRY.assetHeight};
      const internal = {points:[neck,{x:centerX,y:(neck.y+sandSurface.y)/2},sandSurface],
        share:.25,accent:'#D9B982',particleCount:4};
      // One gentle arc per source; cloud artwork does not dictate the route.
      const sourceArc = from => {
        const dx = entry.x-from.x, dy = entry.y-from.y;
        const rise = Math.min(height*.14,Math.abs(dx)*.12);
        return [from,{x:from.x+dx/3,y:from.y+dy/3-rise},
          {x:from.x+dx*2/3,y:from.y+dy*2/3-rise},entry];
      };
      vapor.setAttribute('viewBox',`0 0 ${width} ${height}`);
      const curves = {cash:sourceArc(source),market:sourceArc(summit)};
      Object.entries(curves).forEach(([key,d]) => flowPaths[key].forEach(path => path.setAttribute('d',curvePath(d))));
      const placeLabel = (el,point) => {
        const half = GEOMETRY.labelWidth/2;
        Object.assign(el.style,{top:(point.y-GEOMETRY.labelGap)+'px',bottom:'auto',
          height:'auto',left:Math.max(half,Math.min(width-half,point.x))+'px',right:'auto'});
      };
      placeLabel(guides.liquid,source);
      placeLabel(guides.mountain,summit);
      const spending = nodes.map(node => {
        const r = node.getBoundingClientRect();
        return {x:r.left-sr.left+r.width/2,y:r.top-sr.top+GEOMETRY.destinationInset,
          share:Number(node.dataset.share)||0,accent:node.dataset.accent||'#f1bc84'};
      });
      const cashShare = Math.max(0,Math.min(1,parseFloat(scene.style.getPropertyValue('--fl-cash-share'))/100||0));
      const upstream = Object.entries(curves).map(([key,points]) => ({points,
        share:key.startsWith('cash')?cashShare:1-cashShare,
        accent:key.startsWith('cash')?'#E8BE67':'#98BFDE',opacity:.65}));
      publish({desktop:true,width,height,foot,spending,upstream,internal,
        speed:Math.max(.25,Math.min(1.7,parseFloat(getComputedStyle(scene).getPropertyValue('--fl-stream-opacity'))||.7))});
    };
    let pending = 0;
    const scheduleLayout = () => {
      if (pending) return;
      pending = requestAnimationFrame(() => {pending=0;updateLayout();});
    };
    const observer = new ResizeObserver(scheduleLayout);
    [scene,glass,header,...nodes].forEach(el => observer.observe(el));
    document.addEventListener('fullscreenchange',scheduleLayout);
    window.addEventListener('resize',scheduleLayout);
    document.fonts?.ready.then(scheduleLayout);
    scheduleLayout();
  }
  const full = document.getElementById('fl-fullscreen');
  full?.addEventListener('click', () => {
    const app = document.querySelector('.fl-app');
    if (!document.fullscreenElement) { const result = app?.requestFullscreen?.(); result?.catch?.(() => undefined); }
    else { const result = document.exitFullscreen?.(); result?.catch?.(() => undefined); }
  });
})();

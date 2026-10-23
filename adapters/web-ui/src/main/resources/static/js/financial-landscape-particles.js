(() => {
  'use strict';
  const scene = document.querySelector('.fl-scene');
  if (!scene || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const canvas = document.createElement('canvas');
  canvas.className = 'fl-particles';
  canvas.setAttribute('aria-hidden', 'true');
  scene.prepend(canvas);
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  let w = 0, h = 0, frame = 0, lastTime = null;
  const FLOW_CYCLE_MS = 28000;
  let phase = 0;
  let flows = [];
  // Shared rendering keeps particle timing independent of each trail's visual weight.
  const makeFlow = (points,share,accent,particleCount,opacity=1,trailScale=1,trailOpacity=1) => {
    const path = new Path2D();
    const [a,b,c,d] = points;
    path.moveTo(a.x,a.y);
    if (d) path.bezierCurveTo(b.x,b.y,c.x,c.y,d.x,d.y);
    else path.quadraticCurveTo(b.x,b.y,c.x,c.y);
    const pointAt = t => {
      const u = 1-t;
      return d ? {x:u*u*u*a.x+3*u*u*t*b.x+3*u*t*t*c.x+t*t*t*d.x,
        y:u*u*u*a.y+3*u*u*t*b.y+3*u*t*t*c.y+t*t*t*d.y}
        : {x:u*u*a.x+2*u*t*b.x+t*t*c.x,y:u*u*a.y+2*u*t*b.y+t*t*c.y};
    };
    return {path,pointAt,share:Math.max(0,Math.min(1,share)),accent,particleCount,opacity,trailScale,trailOpacity};
  };
  // Geometry is measured once by financial-landscape-scene.js, never inside the draw loop.
  let layout = scene.financialLandscapeLayout;
  const applyLayout = event => {
    layout = event?.detail || scene.financialLandscapeLayout;
    if (!layout?.desktop) {
      ctx.clearRect(0,0,w,h);
      syncAnimation();
      return;
    }
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = layout.width; h = layout.height;
    canvas.width = Math.round(w*dpr); canvas.height = Math.round(h*dpr);
    ctx.setTransform(dpr,0,0,dpr,0,0);
    flows = layout.upstream.map(flow => makeFlow(flow.points,flow.share,flow.accent,flow.particleCount,flow.opacity,flow.trailScale,flow.trailOpacity));
    for (const destination of layout.spending) {
      const from = layout.foot;
      if (destination.y<=from.y+12) continue;
      const control = {x:from.x+(destination.x-from.x)*.62,y:from.y+(destination.y-from.y)*.3};
      flows.push(makeFlow([from,control,destination],destination.share,destination.accent));
    }
    const internal = layout.internal;
    flows.push(makeFlow(internal.points,internal.share,internal.accent,internal.particleCount));
    // Keep the SVG as a static fallback for reduced motion or unavailable Canvas.
    scene.classList.add('fl-canvas-flows');
    syncAnimation();
  };
  scene.addEventListener('fl:layout',applyLayout);
  const draw = time => {
    frame = 0;
    if (!layout?.desktop || document.hidden) return;
    const elapsed = lastTime === null ? 0 : Math.min(50,time-lastTime);
    lastTime = time;
    ctx.clearRect(0,0,w,h);
    phase = (phase+elapsed/FLOW_CYCLE_MS*layout.speed)%1;
    for (const [index,flow] of flows.entries()) {
      const {share,accent,path,pointAt,opacity,trailScale,trailOpacity} = flow;
      const lineWidth = (1.5+Math.sqrt(share)*3.4)*trailScale;
      ctx.save();
      ctx.globalAlpha = opacity*trailOpacity;
      ctx.lineWidth = lineWidth+2*trailScale;
      ctx.strokeStyle = 'rgba(8, 18, 28, .32)';
      ctx.stroke(path);
      ctx.lineWidth = lineWidth;
      ctx.strokeStyle = accent;
      ctx.globalAlpha = .34*opacity*trailOpacity;
      ctx.shadowColor = accent;
      ctx.shadowBlur = 8;
      ctx.stroke(path);
      const count = flow.particleCount ?? 9+Math.round(share*22);
      for (let i=0;i<count;i++) {
        const t = (phase+i/count+index*.07)%1;
        const point = pointAt(t);
        ctx.beginPath();
        ctx.fillStyle = accent;
        ctx.globalAlpha = (.42+.48*Math.sin(Math.PI*t))*opacity;
        ctx.arc(point.x,point.y,1.3+Math.sqrt(share)*2.4,0,Math.PI*2);
        ctx.fill();
      }
      ctx.restore();
    }
    frame = requestAnimationFrame(draw);
  };
  const syncAnimation = () => {
    if (!layout?.desktop || document.hidden) {
      cancelAnimationFrame(frame);
      frame = 0;
      lastTime = null;
    } else if (!frame) {
      frame = requestAnimationFrame(draw);
    }
  };
  document.addEventListener('visibilitychange',syncAnimation);
  applyLayout();
})();

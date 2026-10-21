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
  let w = 0, h = 0, frame = 0;
  let spendPhase = 0;
  const reserveParticles = Array.from({length: 40}, (_, i) => ({t: i / 40, size: .8 + (i % 3) * .35}));
  const resize = () => {
    const rect = scene.getBoundingClientRect();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    w = rect.width; h = rect.height;
    canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  };
  const observer = new ResizeObserver(resize);
  observer.observe(scene);
  resize();
  const draw = () => {
    ctx.clearRect(0, 0, w, h);
    const glass = scene.querySelector('.fl-hourglass');
    const rect = glass?.getBoundingClientRect();
    const parent = scene.getBoundingClientRect();
    if (rect && rect.width > 0) {
      const cx = rect.left - parent.left + rect.width * .5;
      const bottom = rect.top - parent.top + rect.height * .94;
      const speed = Math.max(.25, Math.min(1.7, Number.parseFloat(getComputedStyle(scene).getPropertyValue('--fl-stream-opacity')) || .7));
      spendPhase = (spendPhase + .003 * speed) % 1;
      const nodes = [...scene.querySelectorAll('.fl-spend-node')];
      // Paths share a common glass source and land at the category's sand pile.
      // They are illustrative averages, not individual transactions.
      if (w > 980 && nodes.length) {
        for (const [index, node] of nodes.entries()) {
          const destination = node.getBoundingClientRect();
          const x2 = destination.left - parent.left + destination.width * .5;
          const y2 = destination.top - parent.top + 9;
          if (y2 <= bottom + 12) continue;
          const controlX = cx + (x2 - cx) * .62;
          const controlY = bottom + (y2 - bottom) * .3;
          const share = Math.max(0, Math.min(1, Number(node.dataset.share) || 0));
          const accent = node.dataset.accent || '#f1bc84';
          ctx.save();
          ctx.beginPath();
          ctx.moveTo(cx, bottom);
          ctx.quadraticCurveTo(controlX, controlY, x2, y2);
          ctx.lineWidth = .8 + Math.sqrt(share) * 2.7;
          ctx.strokeStyle = 'rgba(246, 191, 119, .13)';
          ctx.shadowColor = accent;
          ctx.shadowBlur = 8;
          ctx.stroke();
          const count = 9 + Math.round(share * 22);
          for (let i = 0; i < count; i++) {
            const t = (spendPhase + i / count + index * .07) % 1;
            const u = 1 - t;
            const x = u * u * cx + 2 * u * t * controlX + t * t * x2;
            const y = u * u * bottom + 2 * u * t * controlY + t * t * y2;
            ctx.beginPath();
            ctx.fillStyle = accent;
            ctx.globalAlpha = .20 + .42 * Math.sin(Math.PI * t);
            ctx.arc(x, y, .65 + Math.sqrt(share) * 1.5, 0, Math.PI * 2);
            ctx.fill();
          }
          ctx.restore();
        }
      }
    }
    const direction = scene.dataset.reserveDirection;
    const glassRect = scene.querySelector('.fl-hourglass')?.getBoundingClientRect();
    // The photograph replaces the hidden CSS dune. Use the visible left highland
    // as the reserve source; hidden elements have a zero rect and cause long,
    // spurious diagonal streaks from the page origin.
    if ((direction === 'draw' || direction === 'save') && glassRect && w > 980) {
      const parentRect = scene.getBoundingClientRect();
      const glassX = glassRect.left - parentRect.left + glassRect.width * .5;
      const glassY = glassRect.top - parentRect.top + glassRect.height * .95;
      const duneX = w * .105;
      const duneY = h * .595;
      // Positive saving moves outward to the dune; a funding deficit draws inward.
      const fromX = direction === 'draw' ? duneX : glassX;
      const fromY = direction === 'draw' ? duneY : glassY;
      const toX = direction === 'draw' ? glassX : duneX;
      const toY = direction === 'draw' ? glassY : duneY;
      const midX = (glassX + duneX) / 2 - Math.min(35, w * .025);
      const midY = Math.min(glassY, duneY) - 25;
      ctx.save();
      ctx.beginPath();
      ctx.moveTo(fromX, fromY);
      ctx.quadraticCurveTo(midX, midY, toX, toY);
      ctx.strokeStyle = direction === 'draw' ? 'rgba(251,184,131,.34)' : 'rgba(163,222,190,.32)';
      ctx.lineWidth = 1.5;
      ctx.shadowColor = direction === 'draw' ? '#f7bb86' : '#a5dfc4';
      ctx.shadowBlur = 9;
      ctx.stroke();
      for (const p of reserveParticles) {
        p.t = (p.t + .0032) % 1;
        const t = p.t;
        const u = 1 - t;
        const x = u * u * fromX + 2 * u * t * midX + t * t * toX;
        const y = u * u * fromY + 2 * u * t * midY + t * t * toY;
        ctx.beginPath();
        ctx.fillStyle = direction === 'draw' ? 'rgba(252,185,129,.88)' : 'rgba(168,229,191,.85)';
        ctx.shadowBlur = 8;
        ctx.arc(x, y, p.size * 1.35, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    }
    frame = requestAnimationFrame(draw);
  };
  const onVisibility = () => {
    if (document.hidden) cancelAnimationFrame(frame);
    else { cancelAnimationFrame(frame); draw(); }
  };
  document.addEventListener('visibilitychange', onVisibility);
  draw();
})();

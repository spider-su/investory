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
  const particles = Array.from({length: 115}, (_, i) => ({
    t: (i * 0.61803398875) % 1,
    lane: (i % 7) - 3,
    size: 0.7 + (i % 4) * .4,
    blue: i % 5 === 0
  }));
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
      const cy = rect.top - parent.top;
      const gh = rect.height;
      const bottom = cy + gh * .9;
      const speed = Math.max(.25, Math.min(1.7, Number.parseFloat(getComputedStyle(scene).getPropertyValue('--fl-stream-opacity')) || .7));
      for (const p of particles) {
        p.t = (p.t + .0028 * speed * (1 + p.size / 5)) % 1;
        // Flow spreads from the hourglass base towards the foreground expense streams.
        const t = p.t;
        const spread = Math.min(w * .46, 60 + t * w * .42);
        const x = cx + p.lane / 3 * spread * t;
        const y = bottom + t * Math.max(24, h - bottom - 32);
        if (y > h - 18) continue;
        ctx.beginPath();
        ctx.fillStyle = p.blue ? 'rgba(113,192,243,.55)' : 'rgba(255,192,97,.78)';
        ctx.shadowColor = p.blue ? '#67b8ff' : '#ffbe63';
        ctx.shadowBlur = 5;
        ctx.arc(x, y, p.size, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    const direction = scene.dataset.reserveDirection;
    const duneRect = scene.querySelector('.fl-dune')?.getBoundingClientRect();
    const glassRect = scene.querySelector('.fl-hourglass')?.getBoundingClientRect();
    // The strand is illustrative, never a real transfer or account balance.
    if ((direction === 'draw' || direction === 'save') && duneRect && glassRect && w > 980) {
      const parentRect = scene.getBoundingClientRect();
      const glassX = glassRect.left - parentRect.left + glassRect.width * .5;
      const glassY = glassRect.top - parentRect.top + glassRect.height * .95;
      const duneX = duneRect.left - parentRect.left + duneRect.width * .53;
      const duneY = duneRect.top - parentRect.top + duneRect.height * .12;
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

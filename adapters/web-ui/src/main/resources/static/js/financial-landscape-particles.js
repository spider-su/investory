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
    frame = requestAnimationFrame(draw);
  };
  const onVisibility = () => {
    if (document.hidden) cancelAnimationFrame(frame);
    else { cancelAnimationFrame(frame); draw(); }
  };
  document.addEventListener('visibilitychange', onVisibility);
  draw();
})();

// Simulation board only owns presentation interactions. Projection values come from the server.
(function () {
  document.querySelectorAll('[data-simulation-disclosure]').forEach((item) => {
    item.addEventListener('toggle', () => item.classList.toggle('is-open', item.open));
  });
})();

import type { ObjectDirective } from 'vue';

// One measured indicator for horizontal tabs, wrapped chips and vertical navigation.
// Measurements come from the selected control, so labels and responsive widths can vary.
const cleanup = new WeakMap<HTMLElement, () => void>();
export const slider: ObjectDirective<HTMLElement> = {
  mounted(root) {
    let frame = 0, disposed = false;
    const measure = () => {
      frame = 0;
      const selected = Array.from(root.querySelectorAll<HTMLElement>(':scope > .selected, :scope > .active, :scope > [aria-selected="true"]')).find(el => el.getClientRects().length && getComputedStyle(el).display !== 'none');
      if (!selected) { root.dataset.sliderReady = 'false'; return; }
      const box = root.getBoundingClientRect(), item = selected.getBoundingClientRect();
      root.style.setProperty('--slider-x', `${item.left - box.left - root.clientLeft + root.scrollLeft}px`);
      root.style.setProperty('--slider-y', `${item.top - box.top - root.clientTop + root.scrollTop}px`);
      root.style.setProperty('--slider-width', `${item.width}px`);
      root.style.setProperty('--slider-height', `${item.height}px`);
      root.dataset.sliderReady = 'true';
      if (!root.dataset.sliderAnimated) requestAnimationFrame(() => { if (!disposed) root.dataset.sliderAnimated = 'true'; });
    };
    const schedule = () => { if (!disposed && !frame) frame = requestAnimationFrame(measure); };
    const resize = new ResizeObserver(schedule);
    resize.observe(root);
    const observeChildren = () => { for (const child of root.children) resize.observe(child); };
    observeChildren();
    const mutation = new MutationObserver(() => { observeChildren(); schedule(); });
    mutation.observe(root, {subtree:true, childList:true, attributes:true, attributeFilter:['class','aria-selected']});
    window.addEventListener('resize', schedule);
    document.fonts.ready.then(schedule);
    schedule();
    cleanup.set(root, () => { disposed = true; cancelAnimationFrame(frame); resize.disconnect(); mutation.disconnect(); window.removeEventListener('resize',schedule); });
  },
  beforeUnmount(root) { cleanup.get(root)?.(); cleanup.delete(root); },
};

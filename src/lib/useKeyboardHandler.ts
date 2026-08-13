import { useEffect } from 'react';

/**
 * Handles iOS keyboard behavior to prevent the page from being pushed up.
 * Uses the VisualViewport API to detect keyboard and adjust only the input area,
 * keeping the page body fixed.
 */
export function useKeyboardHandler() {
  useEffect(() => {
    const viewport = window.visualViewport;
    if (!viewport) return;

    const updateKeyboardOffset = () => {
      const layoutHeight = window.innerHeight;
      const visualHeight = viewport.height;
      const keyboardHeight = Math.max(0, layoutHeight - visualHeight - viewport.offsetTop);

      // Set a CSS variable that the input area uses for padding
      document.documentElement.style.setProperty('--keyboard-offset', `${keyboardHeight}px`);

      // Keep the body fixed - prevent scroll
      window.scrollTo(0, 0);
    };

    updateKeyboardOffset();
    viewport.addEventListener('resize', updateKeyboardOffset);
    viewport.addEventListener('scroll', updateKeyboardOffset);

    return () => {
      viewport.removeEventListener('resize', updateKeyboardOffset);
      viewport.removeEventListener('scroll', updateKeyboardOffset);
      document.documentElement.style.setProperty('--keyboard-offset', '0px');
    };
  }, []);
}

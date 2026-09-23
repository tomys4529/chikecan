import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// globals:trueを使わず明示importで統一しているため、
// @testing-library/reactの自動クリーンアップが働かない。ここで明示的に登録する。
afterEach(() => {
  cleanup();
});

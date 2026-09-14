const BASE = '/api/usage';

export function getWindowSummary() {
  return fetch(`${BASE}/window`).then((r) => r.json());
}

export function getWindowSvgUrl() {
  return `${BASE}/window/svg`;
}

export function regenerateNow() {
  return fetch(`${BASE}/regenerate`, { method: 'POST' }).then((r) => r.json());
}

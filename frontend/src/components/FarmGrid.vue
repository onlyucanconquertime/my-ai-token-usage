<script setup>
import { ref, computed, onMounted } from 'vue';
import { getWindowSvgUrl, getWindowSummary, regenerateNow } from '../api/usage.js';

const refreshKey = ref(Date.now());
const summary = ref(null);
const status = ref('');
const loading = ref(false);

const svgUrl = computed(() => `${getWindowSvgUrl()}?t=${refreshKey.value}`);

async function loadSummary() {
  summary.value = await getWindowSummary();
}

async function onRegenerate() {
  loading.value = true;
  status.value = '';
  try {
    const result = await regenerateNow();
    status.value = result.message;
    refreshKey.value = Date.now();
    await loadSummary();
  } catch (err) {
    status.value = `Failed: ${err}`;
  } finally {
    loading.value = false;
  }
}

onMounted(loadSummary);
</script>

<template>
  <div class="farm-grid">
    <p v-if="summary" class="range">{{ summary.start }} &rarr; {{ summary.end }} (today)</p>
    <img :src="svgUrl" alt="daily token usage chart" class="farm-image" />
    <div class="controls">
      <button :disabled="loading" @click="onRegenerate">
        {{ loading ? 'Regenerating…' : 'Regenerate now' }}
      </button>
      <span v-if="status" class="status">{{ status }}</span>
    </div>
    <table v-if="summary" class="summary">
      <thead>
        <tr><th>Date</th><th>Tokens</th><th>Est. cost</th><th>Tier</th></tr>
      </thead>
      <tbody>
        <tr v-for="day in summary.days" :key="day.date">
          <td>{{ day.date }}</td>
          <td>{{ day.totalTokens.toLocaleString() }}</td>
          <td>${{ day.estimatedCost.toFixed(2) }}</td>
          <td>{{ day.tier }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.farm-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
  font-family: monospace;
}
.range {
  margin: 0;
  color: #7a6a5a;
  font-size: 0.85rem;
}
.farm-image {
  border: 1px solid #ddd;
  border-radius: 4px;
  max-width: 100%;
  background: #000;
}
.controls {
  display: flex;
  align-items: center;
  gap: 10px;
}
button {
  padding: 6px 14px;
  cursor: pointer;
}
.status {
  color: #555;
}
.summary {
  border-collapse: collapse;
  font-size: 0.85rem;
}
.summary th, .summary td {
  border: 1px solid #ddd;
  padding: 4px 8px;
  text-align: right;
}
.summary th:first-child, .summary td:first-child {
  text-align: left;
}
</style>

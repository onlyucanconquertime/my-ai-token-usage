<script setup>
import FarmGrid from './components/FarmGrid.vue';
</script>

<template>
  <main>
    <h1>🌾 Token Farm</h1>
    <p class="subtitle">Local preview — this never publishes anywhere. The real GitHub profile update runs on a schedule.</p>
    <FarmGrid />
  </main>
</template>

<style>
body {
  background: #fffaf0;
  color: #3a2a1a;
}
main {
  max-width: 720px;
  margin: 2rem auto;
  padding: 0 1rem;
  font-family: system-ui, sans-serif;
}
.subtitle {
  color: #7a6a5a;
  font-size: 0.9rem;
}
</style>
